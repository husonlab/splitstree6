/*
 * ClusterReduction.java Copyright (C) 2024 Daniel H. Huson
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
package splitstree6.autumn.hybridnumber;


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
	public static final boolean checking = false;

	/**
	 * cluster reduce two trees, if possible
	 *
	 * @return subtree-reduced trees followed by all reduced subtrees
	 */
	public static PhyloTree[] apply(PhyloTree tree1, PhyloTree tree2) throws IOException {
		// create rooted trees with nodes labeled by taxa ids
		var allTaxa = new TaxaBlock();
		var roots = PreProcess.apply(tree1, tree2, allTaxa);

		var v1 = roots.getFirst();
		var v2 = roots.getSecond();

		// run the algorithm
		var pair = apply(v1, v2, new Single<>());

		if (pair != null) {
			var results = new LinkedList<Root>();
			results.add(v1);
			results.add(v2);
			results.add(pair.getFirst());
			results.add(pair.getSecond());
			// convert data-structures to final trees
			var result = PostProcess.apply(results.toArray(new Root[0]), allTaxa, false);
			return result.toArray(new PhyloTree[0]);

		} else
			return null;
	}


	/**
	 * finds a pair nodes for minimal cluster reduction, if one exists
	 *
	 * @return two reduced clusters or null
	 */
	public static Pair<Root, Root> apply(Root v1, Root v2, Single<Integer> placeHolderTaxa) {
		String string1 = null;
		String string2 = null;

		if (checking) {
			string1 = v1.toStringFullTreeX();
			string2 = v2.toStringFullTreeX();
		}

		var pair = applyRec(v1, v2, new HashSet<>(), placeHolderTaxa);
		if (!v1.getTaxa().equals(v2.getTaxa()))
			throw new RuntimeException("Unequal taxon sets: " + StringUtils.toString(v1.getTaxa()) + " vs " + StringUtils.toString(v2.getTaxa()));

		// reorder should not be necessary
		// v1.reorderSubTree();
		// v2.reorderSubTree();

		if (checking) {
			try {
				v1.checkTree();
			} catch (RuntimeException ex) {
				System.err.println("DATA A");
				System.err.println(string1 + ";");
				System.err.println(v1.toStringFullTreeX() + ";");
				if (pair != null)
					System.err.println(pair.getFirst().toStringFullTreeX() + ";");
				throw ex;
			}

			try {
				v2.checkTree();
			} catch (RuntimeException ex) {
				System.err.println("DATA B");
				System.err.println(string2 + ";");
				System.err.println(v2.toStringFullTreeX() + ";");
				if (pair != null)
					System.err.println(pair.getSecond().toStringFullTreeX() + ";");
				throw ex;
			}
		}

		if (pair != null && !pair.getFirst().getTaxa().equals(pair.getSecond().getTaxa()))
			throw new RuntimeException("Unequal taxon sets: " + StringUtils.toString(pair.getFirst().getTaxa()) + " vs " + StringUtils.toString(pair.getSecond().getTaxa()));

		if (pair != null) {
			// reorder should not be necessary
			// pair.getFirst().reorderSubTree();
			// pair.getSecond().reorderSubTree();

			if (checking) {
				try {
					pair.getFirst().checkTree();
				} catch (RuntimeException ex) {
					System.err.println("DATA 1");
					System.err.println(string1 + ";");
					System.err.println(v1.toStringFullTreeX() + ";");
					System.err.println(pair.getFirst().toStringFullTreeX() + ";");
					throw ex;
				}

				try {
					pair.getSecond().checkTree();
				} catch (RuntimeException ex) {
					System.err.println("DATA 2");
					System.err.println(string2 + ";");
					System.err.println(v2.toStringFullTreeX() + ";");
					System.err.println(pair.getSecond().toStringFullTreeX() + ";");
					throw ex;
				}
			}
		}
		return pair;
	}

	/**
	 * recursively does the work
	 *
	 * @return two reduced clusters or null
	 */
	private static Pair<Root, Root> applyRec(Root v1, Root v2, Set<Pair<Node, Node>> compared, Single<Integer> placeHolderTaxa) {
		var nodePair = new Pair<Node, Node>(v1, v2);
		if (compared.contains(nodePair))
			return null;
		else
			compared.add(nodePair);

		// System.err.println("reduceClusterRec v1=" + Basic.toString((v1.getTaxa()) + " v2=" + Basic.toString(v2.getTaxa());

		var X = v1.getTaxa();
		var Y = v2.getTaxa();

		// recursively process all children
		for (var e1 : v1.outEdges()) {
			var u1 = (Root) e1.getTarget();
			if (u1.getTaxa().intersects(Y)) {
				var pair = applyRec(u1, v2, compared, placeHolderTaxa);
				if (pair != null)
					return pair;
			}
		}
		for (var e2 : v2.outEdges()) {
			var u2 = (Root) e2.getTarget();
			if (u2.getTaxa().intersects(X)) {
				var pair = applyRec(v1, u2, compared, placeHolderTaxa);
				if (pair != null)
					return pair;
			}
		}

		if (v1.getInDegree() > 0 && v2.getInDegree() > 0) {
			var pairOfConnectedComponents = getPairOfSeparatableConnectedComponents(v1, v2);

			if (pairOfConnectedComponents != null) {
				var component1 = pairOfConnectedComponents.getFirst();
				var u1 = v1.newNode();
				var taxa1 = new BitSet();
				for (var p1 : component1) {
					u1.deleteEdge(p1.getFirstInEdge());
					u1.newEdge(u1, p1);
					taxa1.or(((Root) p1).getTaxa());
				}
				u1.setTaxa(taxa1);
				placeHolderTaxa.set(u1.getTaxa().nextSetBit(0));
				u1.reorderChildren();

				var component2 = pairOfConnectedComponents.getSecond();
				var u2 = v2.newNode();
				var taxa2 = new BitSet();
				for (var p2 : component2) {
					v2.deleteEdge(p2.getFirstInEdge());
					v2.newEdge(u2, p2);
					taxa2.or(((Root) p2).getTaxa());
				}
				u2.setTaxa(taxa2);
				u2.reorderChildren();

				if (!taxa1.equals(taxa2))
					throw new RuntimeException("Unequal taxon sets: " + StringUtils.toString(X) + " vs " + StringUtils.toString(Y));
				return new Pair<>(u1, u2);
			} else if ((v1.getOutDegree() > 1 || v2.getOutDegree() > 1) && X.equals(Y)) // no pair of connected components, but perhaps both nodes give us a component
			{
				if (v1.getInDegree() == 0 || v2.getInDegree() == 0)
					throw new RuntimeException("Indegree should not be zero");
				var u1 = v1.newNode();
				u1.setTaxa((BitSet) v1.getTaxa().clone());
				placeHolderTaxa.set(u1.getTaxa().nextSetBit(0));

				var toDelete = new LinkedList<Edge>();
				for (var e1 : v1.outEdges()) {
					u1.newEdge(u1, e1.getTarget());
					toDelete.add(e1);
				}
				for (var e1 : toDelete) {
					v1.deleteEdge(e1);
				}

				var u2 = v2.newNode();
				u2.setTaxa((BitSet) v2.getTaxa().clone());
				toDelete.clear();
				for (var e2 : v2.outEdges()) {
					u2.newEdge(u2, e2.getTarget());
					toDelete.add(e2);
				}
				for (var e2 : toDelete) {
					v2.deleteEdge(e2);
				}

				return new Pair<>(u1, u2);
			}
		}
		return null;
	}

	/**
	 * find a pair of separatable bunches of subtrees in both trees.
	 */
	private static Pair<Set<Node>, Set<Node>> getPairOfSeparatableConnectedComponents(Node v1, Node v2) {

		// compute intersection graph:
		var intersectionGraph = new Graph();
		var sets1 = new Node[v1.getOutDegree()];
		var i = 0;
		for (var e1 : v1.outEdges()) {
			sets1[i++] = intersectionGraph.newNode(e1.getTarget());
		}
		Node[] sets2 = new Node[v2.getOutDegree()];
		i = 0;
		for (var e2 : v2.outEdges()) {
			sets2[i++] = intersectionGraph.newNode(e2.getTarget());
		}

		{
			var i1 = 0;
			for (var e1 : v1.outEdges()) {
				var A1 = ((Root) e1.getTarget()).getTaxa();
				var i2 = 0;
				for (var e2 : v2.outEdges()) {
					var A2 = ((Root) e2.getTarget()).getTaxa();

					if (A1.intersects(A2))
						intersectionGraph.newEdge(sets1[i1], sets2[i2]);
					i2++;
				}
				i1++;
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
				for (Node x : nodesInComponent.getSecond())
					H.or(((Root) x).getTaxa());
				if (G.equals(H))      // have same taxa in both trees, return it!
					return nodesInComponent;
			}
		}
		return null;
	}

	/**
	 * get all tree nodes in a connected component of the  intersection graph
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
