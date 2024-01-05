/*
 * SubtreeReduction.java Copyright (C) 2024 Daniel H. Huson
 *
 * (Some files contain contributions from other authors, who are then mentioned separately.)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package splitstree6.autumn.hybridnetwork;

import jloda.graph.Edge;
import jloda.phylo.PhyloTree;
import jloda.util.Pair;
import jloda.util.Single;
import splitstree6.autumn.Cluster;
import splitstree6.autumn.PostProcess;
import splitstree6.autumn.PreProcess;
import splitstree6.autumn.Root;
import splitstree6.data.TaxaBlock;

import java.io.IOException;
import java.util.*;

/**
 * applys all possible subtree reductions to two trees
 * Daniel Huson, 5.2011
 */
public class SubtreeReduction {
	public enum ReturnValue {
		ISOMORPHIC, REDUCED, IRREDUCIBLE
	}

	/**
	 * sub-tree reduce two trees
	 *
	 * @param merge merge merged-subtrees back into trees, for debugging purposes
	 * @return subtree-reduced trees followed by all reduced subtrees
	 */
	public static PhyloTree[] apply(PhyloTree tree1, PhyloTree tree2, Set<String> selectedLabels, boolean merge) throws IOException {
		// create rooted trees with nodes labeled by taxa ids
		var allTaxa = new TaxaBlock();
		var roots = PreProcess.apply(tree1, tree2, allTaxa);

		var root1 = roots.getFirst();
		var root2 = roots.getSecond();

		if (selectedLabels != null) {
			for (var label : selectedLabels) {
				var id = allTaxa.indexOf(label);
				if (id != -1) {
					RemoveTaxon.apply(root1, 1, id);
					RemoveTaxon.apply(root2, 2, id);
				}
			}
		}

		// reorder should not be necessary
		// root1.reorderSubTree();
		// root2.reorderSubTree();

		var subTrees = new LinkedList<Pair<Root, Root>>();

		// run the algorithm
		var value = apply(root1, root2, subTrees, new Single<>());
		if (value == ReturnValue.ISOMORPHIC) {
			System.err.println("Trees are isomorphic");
			var taxa = root1.getTaxa();
			var newRoot1 = root1.newNode();
			newRoot1.setTaxa(taxa);
			root1 = newRoot1;
			Root newRoot2 = root2.newNode();
			newRoot2.setTaxa(taxa);
			root2 = newRoot2;
		} else if (value == ReturnValue.IRREDUCIBLE) {
			System.err.println("Trees are not subtree reducible");
		}

		var results = new LinkedList<Root>();
		results.add(root1);
		results.add(root2);
		for (var pair : subTrees) {
			results.add(pair.getFirst());
			results.add(pair.getSecond());
		}

		if (merge) {// for debugging purposes, we then merge the common subtrees back into the main tree
			var newRoot1 = root1.copySubNetwork();
			var newRoot2 = root2.copySubNetwork();
			var mergedSubtrees = new LinkedList<Root>();
			for (var pair : subTrees) {
				mergedSubtrees.add(MergeIsomorphicInducedTrees.apply(pair.getFirst(), pair.getSecond()));
			}
			results.addAll(mergedSubtrees);
			var merged1 = MergeNetworks.apply(List.of(newRoot1), mergedSubtrees);
			var merged2 = MergeNetworks.apply(List.of(newRoot2), mergedSubtrees);
			results.addAll(merged1);
			results.addAll(merged2);
		}

		// convert data-structures to final trees
		var result = PostProcess.apply(results.toArray(new Root[0]), allTaxa, true);
		return result.toArray(new PhyloTree[0]);
	}


	/**
	 * check whether two induced trees are isomorphic or not
	 *
	 * @return true iff isomorphic
	 */
	public static ReturnValue apply(Root root1, Root root2, List<Pair<Root, Root>> subTrees, Single<Integer> placeHolderTaxon) {
		var isomorphic = new HashSet<Pair<Root, Root>>();
		applyRec(nextBranchingNode(root1), nextBranchingNode(root2), subTrees, isomorphic, new HashSet<>(), new HashSet<>(), new HashSet<>(), placeHolderTaxon);
		if (isomorphic.contains(new Pair<>(nextBranchingNode(root1), nextBranchingNode(root2))))
			return ReturnValue.ISOMORPHIC;
		else if (subTrees.size() == 0)
			return ReturnValue.IRREDUCIBLE;
		else {
			while (root1.getOutDegree() == 1 && root1.getFirstOutEdge().getTarget().getOutDegree() == 1) {
				var v = (Root) root1.getFirstOutEdge().getTarget();
				var w = (Root) v.getFirstOutEdge().getTarget();
				root1.newEdge(root1, w);
				v.deleteNode();
			}
			while (root2.getOutDegree() == 1 && root2.getFirstOutEdge().getTarget().getOutDegree() == 1) {
				var v = (Root) root2.getFirstOutEdge().getTarget();
				var w = (Root) v.getFirstOutEdge().getTarget();
				root2.newEdge(root2, w);
				v.deleteNode();
			}
			return ReturnValue.REDUCED;
		}
	}

	/**
	 * recursively do the work
	 */
	private static void applyRec(Root root1, Root root2, List<Pair<Root, Root>> subTrees, Set<Pair<Root, Root>> isomorphic, Set<Pair<Root, Root>> visited, Set<Root> subTreeBelow1, Set<Root> subTreeBelow2, Single<Integer> placeHolderTaxon) {
		// check whether already visited:
		var pairOfRoots = new Pair<>(root1, root2);
		if (visited.contains(pairOfRoots))
			return;
		else
			visited.add(pairOfRoots);

		// System.err.println("Visiting: " + root1 + " and " + root2);

		// special case of two leaves:
		if (root1.getOutDegree() == 0 && root2.getOutDegree() == 0) {
			if (root1.getTaxa().equals(root2.getTaxa()))
				isomorphic.add(pairOfRoots);
			return;
		}

		// recursively try all pairs:
		int aliveChildren1 = 0;
		for (var e1 : root1.outEdges()) {
			var w1 = (Root) e1.getTarget();
			if (w1.getTaxa().cardinality() > 0) {
				w1 = nextBranchingNode(w1);
				aliveChildren1++;
				if (Cluster.intersection(w1.getTaxa(), root2.getTaxa()).cardinality() > 0)
					applyRec(w1, root2, subTrees, isomorphic, visited, subTreeBelow1, subTreeBelow2, placeHolderTaxon);
			}
		}

		int aliveChildren2 = 0;
		for (var e2 : root2.outEdges()) {
			var w2 = (Root) e2.getTarget();
			if (w2.getTaxa().cardinality() > 0) {
				w2 = nextBranchingNode(w2);
				aliveChildren2++;
				if (Cluster.intersection(root1.getTaxa(), w2.getTaxa()).cardinality() > 0)
					applyRec(root1, w2, subTrees, isomorphic, visited, subTreeBelow1, subTreeBelow2, placeHolderTaxon);
			}
		}

		// can assume that all children have been visited. Are root1 and root2 two nodes that some isomorphic children (but not all)
		// determine isomorphic pairs of children below v1 and v2:
		var isomorphicChildren = new HashSet<Pair<Root, Root>>();
		var taxa = new BitSet();


		for (var e1 : root1.outEdges()) {
			var u1 = (Root) e1.getTarget();
			if (u1.getTaxa().cardinality() > 0 && !subTreeBelow1.contains(u1)) {
				u1 = nextBranchingNode(u1);
				for (var e2 : root2.outEdges()) {
					var u2 = (Root) e2.getTarget();
					if (u2.getTaxa().cardinality() > 0 && !subTreeBelow2.contains(u2)) {
						u2 = nextBranchingNode(u2);
						var pairOfChildren = new Pair<>(u1, u2);
						if (isomorphic.contains(pairOfChildren)) {
							// System.err.println("Isomorphic: " + u1 + " and " + u2);
							taxa.or(u1.getTaxa());

							// must use the nodes directly below root1 and root2 rather than the next branching nodes:
							isomorphicChildren.add(new Pair<>((Root) e1.getTarget(), (Root) e2.getTarget()));
							break;
						}
					}
				}
			}
		}

		// all alive children are isomorphic, and thus so are root1 and root2
		if (isomorphicChildren.size() == aliveChildren1 && isomorphicChildren.size() == aliveChildren2) {
			// System.err.println("Roots isomorphic: " + root1 + " and " + root2);
			isomorphic.add(pairOfRoots);
		} else if (isomorphicChildren.size() > 0 && root1.getOutDegree() > 0 && root2.getOutDegree() > 0 && taxa.cardinality() > 1) {
			// at least one pair of alive children are isomorphic, detach as subtree

			if (true) {
				if (isomorphicChildren.size() == 1) { // only one child each, want to cut at next branching node, rather than current node
					var aPair = isomorphicChildren.iterator().next();
					isomorphicChildren.clear();
					aPair.setFirst(nextBranchingNode(aPair.getFirst()));
					aPair.setSecond(nextBranchingNode(aPair.getSecond()));
					isomorphicChildren.add(aPair);
					root1 = (Root) aPair.getFirst().getFirstInEdge().getSource();
					root2 = (Root) aPair.getSecond().getFirstInEdge().getSource();
				}
			}

			var taxon = taxa.nextSetBit(0);
			placeHolderTaxon.set(taxon);

			var n1 = root1.newNode();
			var f1 = root1.newEdge(root1, n1);
			f1.setInfo(1);
			n1.getTaxa().set(taxon);
			// n1.getRemovedTaxa().or(removedTaxa1);
			subTreeBelow1.add(n1);
			root1.reorderChildren();

			var n2 = root2.newNode();
			var f2 = root2.newEdge(root2, n2);
			f2.setInfo(1);
			n2.getTaxa().set(taxon);
			// n2.getRemovedTaxa().or(removedTaxa2);
			subTreeBelow2.add(n2);
			root2.reorderChildren();

			// each isomorphic subtree is detached from the original tree and placed below a new node:
			var subRoot1 = root1.newNode();
			subRoot1.getTaxa().or(taxa);
			var subRoot2 = root2.newNode();
			subRoot2.getTaxa().or(taxa);

			var removedTaxa1 = new BitSet();
			var removedTaxa2 = new BitSet();

			for (var aPair : isomorphicChildren) {
				var u1 = aPair.getFirst();
				u1.deleteEdge(u1.getFirstInEdge());
				f1 = subRoot1.newEdge(subRoot1, u1);
				f1.setInfo(1);
				var u2 = aPair.getSecond();
				u2.deleteEdge(u2.getFirstInEdge());
				f2 = n2.newEdge(subRoot2, u2);
				f2.setInfo(2);
				removedTaxa1.or(u1.getRemovedTaxa());
				removedTaxa2.or(u2.getRemovedTaxa());
			}

			subRoot1.getRemovedTaxa().or(removedTaxa1);
			subRoot2.getRemovedTaxa().or(removedTaxa2);

			if (subRoot1.getOutDegree() == aliveChildren1) {
				var tmp1 = subRoot1.newNode();
				tmp1.setTaxa(subRoot1.getTaxa());
				tmp1.setRemovedTaxa(subRoot1.getRemovedTaxa());
				f1 = tmp1.newEdge(tmp1, subRoot1);
				f1.setInfo(1);
				subRoot1 = tmp1;
			}
			if (subRoot2.getOutDegree() == aliveChildren2) {
				var tmp2 = subRoot2.newNode();
				tmp2.setTaxa(subRoot2.getTaxa());
				tmp2.setRemovedTaxa(subRoot2.getRemovedTaxa());
				f2 = tmp2.newEdge(tmp2, subRoot2);
				f2.setInfo(2);
				subRoot2 = tmp2;
			}

			while (subRoot1.getOutDegree() == 1 && subRoot1.getFirstOutEdge().getTarget().getOutDegree() == 1) {
				var v = (Root) subRoot1.getFirstOutEdge().getTarget();
				var w = (Root) v.getFirstOutEdge().getTarget();
				v.deleteNode();
				subRoot1.newEdge(subRoot1, w);
			}

			while (subRoot2.getOutDegree() == 1 && subRoot2.getFirstOutEdge().getTarget().getOutDegree() == 1) {
				var v = (Root) subRoot2.getFirstOutEdge().getTarget();
				var w = (Root) v.getFirstOutEdge().getTarget();
				v.deleteNode();
				subRoot2.newEdge(subRoot2, w);
			}

			subTrees.add(new Pair<>(subRoot1, subRoot2));

			// remove all but the first taxon from rest of tree:
			var up1 = root1;
			while (up1 != null) {
				up1.getTaxa().andNot(taxa);
				up1.getTaxa().set(taxon);
				up1.getRemovedTaxa().andNot(removedTaxa1);
				subTreeBelow1.add(up1);
				//up1.reorderChildren();
				if (up1.getInDegree() > 0)
					up1 = (Root) up1.getFirstInEdge().getSource();
				else
					up1 = null;
			}
			var up2 = root2;
			while (up2 != null) {
				up2.getTaxa().andNot(taxa);
				up2.getTaxa().set(taxon);
				up2.getRemovedTaxa().andNot(removedTaxa2);
				subTreeBelow2.add(up2);
				//up2.reorderChildren();
				if (up2.getInDegree() > 0)
					up2 = (Root) up2.getFirstInEdge().getSource();
				else
					up2 = null;
			}
		}
	}

	/**
	 * moves down to next branching node
	 *
	 * @return root or next branching node
	 */
	private static Root nextBranchingNode(Root root) {
		while (true) {
			Edge nextEdge = null;
			for (var e : root.outEdges()) {
				var w = (Root) e.getTarget();
				if (w.getTaxa().cardinality() > 0) {
					if (nextEdge == null)
						nextEdge = e; // has a child with taxa
					else
						return root; // has atleast two children with taxa, is a branching node
				}
			}
			if (nextEdge != null)
				root = (Root) nextEdge.getTarget(); // has a child with taxa, go there
			else
				return root; // leaf
		}
	}
}
