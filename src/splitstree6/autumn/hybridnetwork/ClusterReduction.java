/*
 * ClusterReduction.java Copyright (C) 2022 Daniel H. Huson
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
import jloda.graph.Graph;
import jloda.graph.Node;
import jloda.phylo.PhyloTree;
import jloda.util.Pair;
import jloda.util.Single;
import jloda.util.StringUtils;
import splitstree6.autumn.PostProcess;
import splitstree6.autumn.PreProcess;
import splitstree6.autumn.Root;
import splitstree6.data.TaxaBlock;

import java.io.IOException;
import java.util.*;

/**
 * given two multifurcating, refined, subtree-reduce trees, performs a mininum cluster reduction
 * Daniel Huson, 4.2011
 */
public class ClusterReduction {
	public static boolean checking = false;

	/**
	 * cluster-reduce two trees, if possible
	 *
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

		// run the algorithm
		var pair = apply(root1, root2, new Single<>());

		if (pair != null) {
			var results = new LinkedList<Root>();
			results.add(root1);
			results.add(root2);
			results.add(pair.getFirst());
			results.add(pair.getSecond());


			if (merge) {// for debugging purposes, we then merge the common subtrees back into the main tree
				var newRoot1 = root1.copySubNetwork();
				var newRoot2 = root2.copySubNetwork();

				var merged1 = MergeNetworks.apply(List.of(newRoot1), List.of(pair.getFirst()));
				var merged2 = MergeNetworks.apply(List.of(newRoot2), List.of(pair.getSecond()));
				results.addAll(merged1);
				results.addAll(merged2);
			}

			// convert data-structures to final trees
			var result = PostProcess.apply(results.toArray(new Root[0]), allTaxa, true);
			return result.toArray(new PhyloTree[0]);
		} else
			return null;
	}

	/**
	 * finds a pair nodes for minimal cluster reduction, if one exists
	 *
	 * @return two reduced clusters or null
	 */
	public static Pair<Root, Root> apply(Root v1, Root v2, Single<Integer> placeHolder) {
        var pair = applyRec(v1.getTaxa(), v1, v2, new HashSet<>(), placeHolder);
		if (!v1.getTaxa().equals(v2.getTaxa()))
			throw new RuntimeException("Unequal taxon sets: " + StringUtils.toString(v1.getTaxa()) + " vs " + StringUtils.toString(v2.getTaxa()));

		// reorder should not be necessary
		// v1.reorderSubTree();
		// v2.reorderSubTree();

		if (pair != null && !pair.getFirst().getTaxa().equals(pair.getSecond().getTaxa()))
			throw new RuntimeException("Unequal taxon sets: " + StringUtils.toString(pair.getFirst().getTaxa()) + " vs " + StringUtils.toString(pair.getSecond().getTaxa()));

		return pair;
	}

	/**
	 * recursively does the work
	 *
	 * @return two reduced clusters or null
	 */
	private static Pair<Root, Root> applyRec(BitSet taxa, Root root1, Root root2, Set<Pair<Node, Node>> compared, Single<Integer> placeHolder) {
		var nodePair = new Pair<Node, Node>(root1, root2);
		if (compared.contains(nodePair))
			return null;
		else
			compared.add(nodePair);

		// System.err.println("reduceClusterRec v1=" + Basic.toString((v1.getTaxa()) + " v2=" + Basic.toString(v2.getTaxa());

		var X = root1.getTaxa();
		var Y = root2.getTaxa();

		// recursively process all children
		for (var e1 : root1.outEdges()) {
			var u1 = (Root) e1.getTarget();
			if (u1.getTaxa().intersects(Y)) {
				var pair = applyRec(taxa, u1, root2, compared, placeHolder);
				if (pair != null)
					return pair;
			}
		}
		for (var e2 : root2.outEdges()) {
			var u2 = (Root) e2.getTarget();
			if (u2.getTaxa().intersects(X)) {
				var pair = applyRec(taxa, root1, u2, compared, placeHolder);
				if (pair != null)
					return pair;
			}
		}

		// in the code above we did not find a pair below any of the children, now see if v1 and v2 have a pair of clusters below them:

		// don't want either cluster to contain all taxa in the tree:
		if (root1.getTaxa().equals(taxa) || root2.getTaxa().equals(taxa) || root1.getInDegree() == 0 || root2.getInDegree() == 0)
			return null;

		if (!isBranchingNode(root1) || !isBranchingNode(root2))  // should both be branching nodes
			return null;

		var pairOfConnectedComponents = getPairOfSeparatableConnectedComponents(root1, root2);

		if (pairOfConnectedComponents != null) // has pair of connected components, one below each root
		{
			var component1 = pairOfConnectedComponents.getFirst();
			var subTreeRoot1 = root1.newNode();
			var taxa1 = new BitSet();
			var removedTaxa1 = new BitSet();
			var allChildren1 = (component1.size() == root1.getOutDegree());

			for (var a1 : component1) {
				var f = subTreeRoot1.newEdge(subTreeRoot1, a1);
				f.setInfo(a1.getFirstInEdge().getInfo());
				subTreeRoot1.deleteEdge(a1.getFirstInEdge());
				taxa1.or(((Root) a1).getTaxa());
				removedTaxa1.or(((Root) a1).getRemovedTaxa());
			}
			subTreeRoot1.setTaxa(taxa1);
			subTreeRoot1.setRemovedTaxa(removedTaxa1);
			subTreeRoot1.reorderChildren();

			if (allChildren1) {  // cluster is below one node, add extra root edge
				var tmp = subTreeRoot1.newNode();
				tmp.setTaxa(subTreeRoot1.getTaxa());
				tmp.setRemovedTaxa(subTreeRoot1.getRemovedTaxa());
				var f = tmp.newEdge(tmp, subTreeRoot1);
				f.setInfo(1);
				subTreeRoot1 = tmp;
			}

			var taxon = taxa1.nextSetBit(0);

			Root placeHolder1;
			if (allChildren1)
				placeHolder1 = root1;
			else {
				placeHolder1 = root1.newNode();
				var f = root1.newEdge(root1, placeHolder1);
				f.setInfo(1);
			}
			placeHolder1.getTaxa().set(taxon);
			// placeHolder1.getRemovedTaxa().or(removedTaxa1);
			placeHolder.set(taxon);

			root1.reorderChildren();

			// remove all taxa of cluster, except first, from all nodes above
			var up1 = root1;
			while (up1 != null) {
				up1.getTaxa().andNot(taxa1);
				up1.getTaxa().set(taxon);
				up1.getRemovedTaxa().andNot(removedTaxa1);
				//up1.reorderChildren();
				if (up1.getInDegree() > 0)
					up1 = (Root) up1.getFirstInEdge().getSource();
				else
					up1 = null;
			}

			var component2 = pairOfConnectedComponents.getSecond();
			var subTreeRoot2 = root2.newNode();
			var taxa2 = new BitSet();
			var removedTaxa2 = new BitSet();
			var allChildren2 = (component2.size() == root2.getOutDegree());

			for (var a2 : component2) {
				root2.deleteEdge(a2.getFirstInEdge());
				var f = root2.newEdge(subTreeRoot2, a2);
				f.setInfo(2);
				taxa2.or(((Root) a2).getTaxa());
				removedTaxa2.or(((Root) a2).getRemovedTaxa());
			}
			subTreeRoot2.setTaxa(taxa2);
			subTreeRoot2.setRemovedTaxa(removedTaxa2);
			subTreeRoot2.reorderChildren();

			if (allChildren2) { // cluster is below one node, add extra root edge
				var tmp = subTreeRoot2.newNode();
				tmp.setTaxa(subTreeRoot2.getTaxa());
				tmp.setRemovedTaxa(subTreeRoot2.getRemovedTaxa());
				var f = tmp.newEdge(tmp, subTreeRoot2);
				f.setInfo(2);
				subTreeRoot2 = tmp;
			}

			Root placeHolder2;
			if (allChildren2)
				placeHolder2 = root2;
			else {
				placeHolder2 = root2.newNode();
				var f = root2.newEdge(root2, placeHolder2);
				f.setInfo(2);
			}
			placeHolder2.getTaxa().set(taxon);

			// placeHolder2.getRemovedTaxa().or(removedTaxa2);
			root2.reorderChildren();

			// remove all taxa of cluster, except first, from all nodes above
			var up2 = root2;
			while (up2 != null) {
				up2.getTaxa().andNot(taxa2);
				up2.getTaxa().set(taxon);
				up2.getRemovedTaxa().andNot(removedTaxa2);
				//up2.reorderChildren();
				if (up2.getInDegree() > 0)
					up2 = (Root) up2.getFirstInEdge().getSource();
				else
					up2 = null;
			}

			if (!taxa1.equals(taxa2))
				throw new RuntimeException("Unequal taxon sets: " + StringUtils.toString(X) + " vs " + StringUtils.toString(Y));

            return new Pair<>(subTreeRoot1, subTreeRoot2);
		} else if (X.equals(Y)) // no pair of connected components, but perhaps both nodes give us a component
		{
			if (root1.getInDegree() == 0 || root2.getInDegree() == 0)
				throw new RuntimeException("Indegree should not be zero");

			var subTreeRoot1 = root1.newNode();
			var taxa1 = root1.getTaxa();
			var removedTaxa1 = root1.getRemovedTaxa();
			subTreeRoot1.setTaxa(taxa1);
			subTreeRoot1.setRemovedTaxa(root1.getRemovedTaxa());
			var toDelete = new LinkedList<Edge>();
			for (var e1 : root1.outEdges()) {
				var f = subTreeRoot1.newEdge(subTreeRoot1, e1.getTarget());
				f.setInfo(1);
				toDelete.add(e1);
			}
			for (var e1 : toDelete) {
				root1.deleteEdge(e1);
			}

			root1.getRemovedTaxa().clear();

			// remove all taxa of cluster, except first, from all nodes above
			var t1 = taxa1.nextSetBit(0);
			var up1 = root1;
			while (up1 != null) {
				up1.getTaxa().andNot(taxa1);
				up1.getTaxa().set(t1);
				up1.getRemovedTaxa().andNot(removedTaxa1);
				//up1.reorderChildren();
				if (up1.getInDegree() > 0)
					up1 = (Root) up1.getFirstInEdge().getSource();
				else
					up1 = null;
			}
			placeHolder.set(t1);

			var subTreeRoot2 = root2.newNode();
			var taxa2 = root2.getTaxa();
			var removedTaxa2 = root2.getRemovedTaxa();
			subTreeRoot2.setTaxa(taxa2);
			subTreeRoot2.setRemovedTaxa(root2.getRemovedTaxa());
			toDelete.clear();
			for (var e2 : root2.outEdges()) {
				var f = subTreeRoot2.newEdge(subTreeRoot2, e2.getTarget());
				f.setInfo(2);
				toDelete.add(e2);
			}
			for (var e2 : toDelete) {
				root2.deleteEdge(e2);
			}

			root2.getRemovedTaxa().clear();

			// remove all taxa of cluster, except first, from all nodes above
			var t2 = taxa2.nextSetBit(0);
			var up2 = root2;
			while (up2 != null) {
				up2.getTaxa().andNot(taxa2);
				up2.getTaxa().set(t2);
				up2.getRemovedTaxa().andNot(removedTaxa2);
				//up2.reorderChildren();
				if (up2.getInDegree() > 0)
					up2 = (Root) up2.getFirstInEdge().getSource();
				else
					up2 = null;
			}

			{  // cluster is below one node, add extra root edge
				var tmp = subTreeRoot1.newNode();
				tmp.setTaxa(subTreeRoot1.getTaxa());
				tmp.setRemovedTaxa(subTreeRoot1.getRemovedTaxa());
				var f = tmp.newEdge(tmp, subTreeRoot1);
				f.setInfo(1);
				subTreeRoot1 = tmp;
			}

			{  // cluster is below one node, add extra root edge
				var tmp = subTreeRoot2.newNode();
				tmp.setTaxa(subTreeRoot2.getTaxa());
				tmp.setRemovedTaxa(subTreeRoot2.getRemovedTaxa());
				var f = tmp.newEdge(tmp, subTreeRoot2);
				f.setInfo(2);
				subTreeRoot2 = tmp;
			}

			return new Pair<>(subTreeRoot1, subTreeRoot2);
		}

		return null;
	}

	/**
	 * is this a branching node, i.e. does it have at least two children with unremoved taxa?
	 *
	 * @return true, if branching node
	 */
	private static boolean isBranchingNode(Root v) {
		boolean foundOne = false;
		for (var e : v.outEdges()) {
			var w = (Root) e.getTarget();
			if (w.getTaxa().cardinality() > 0) {
				if (foundOne)
					return true;
				else foundOne = true;
			}
		}
		return false;
	}

	/**
	 * returns the next branching node
	 *
	 * @return next branching node
	 */
	private static Root nextBranchingNode(Root v) {
		while (v != null) {
			Root one = null;
			for (var e : v.outEdges()) {
				var w = (Root) e.getTarget();
				if (w.getTaxa().cardinality() > 0) {
					if (one != null)
						return v; // found a second child with taxa, v is a branching node
					else one = w;
				}
			}
			v = one; // found only one child with taxa, move to it.
		}
		return null;
	}


	/**
	 * find a pair of separable bunches of subtrees in both trees.
	 *
	 */
	private static Pair<Set<Node>, Set<Node>> getPairOfSeparatableConnectedComponents(Node v1, Node v2) {
		// compute intersection graph:
		var intersectionGraph = new Graph();
		var sets1 = new Node[v1.getOutDegree()];
		var i = 0;
		for (var e1 : v1.outEdges()) {
			var w1 = (Root) e1.getTarget();
			if (w1.getTaxa().cardinality() > 0)
				sets1[i++] = intersectionGraph.newNode(w1);
		}
		var sets2 = new Node[v2.getOutDegree()];
		i = 0;
		for (var e2 : v2.outEdges()) {
			var w2 = (Root) e2.getTarget();
			if (w2.getTaxa().cardinality() > 0)
				sets2[i++] = intersectionGraph.newNode(w2);
		}

		{
			var i1 = 0;
			for (var e1 : v1.outEdges()) {
				var w1 = (Root) e1.getTarget();
				var A1 = w1.getTaxa();
				if (A1.cardinality() > 0) {
					var i2 = 0;
					for (var e2 : v2.outEdges()) {
						var w2 = (Root) e2.getTarget();
						var A2 = w2.getTaxa();
						if (A2.cardinality() > 0) {
							if (A1.intersects(A2))
								intersectionGraph.newEdge(sets1[i1], sets2[i2]);
							i2++;
						}
					}
					i1++;
				}
			}
		}

		// System.err.println("----- Intersection graph:\n"+intersectionGraph.toString());

		// find a component that contains at least three nodes and has same taxa in both components
		for (var a : intersectionGraph.nodes()) {
			if (a.getDegree() > 1) {
				var nodesInComponent = getNodesInComponent(a, sets1, sets2);
				var G = new BitSet();
				var H = new BitSet();
				for (var x : nodesInComponent.getFirst())
					G.or(((Root) x).getTaxa());
				for (var x : nodesInComponent.getSecond())
					H.or(((Root) x).getTaxa());
				if (G.equals(H))      // have same taxa in both trees, return it!
					return nodesInComponent;
			}
		}
		return null;
	}

	/**
	 * get all tree nodes in a connected component of the  intersection graph
	 *
	 */
	private static Pair<Set<Node>, Set<Node>> getNodesInComponent(Node a, Node[] sets1, Node[] sets2) {
		var seen = new HashSet<Node>();
		var stack = new Stack<Node>();
		stack.push(a);
		seen.add(a);
		while (stack.size() > 0) {
			a = stack.pop();
			for (var e : a.adjacentEdges()) {
				var b = e.getOpposite(a);
				if (!seen.contains(b)) {
					seen.add(b);
					stack.add(b);
				}
			}
		}
		var result1 = new HashSet<Node>();
		for (var c : sets1) {
			if (seen.contains(c))
				result1.add((Node) c.getInfo());
		}
		var result2 = new HashSet<Node>();
		for (var c : sets2) {
			if (seen.contains(c))
				result2.add((Node) c.getInfo());
		}
		return new Pair<>(result1, result2);
	}
}
