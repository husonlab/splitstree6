/*
 * EmbedderForOrderPrescribedNetwork.java Copyright (C) 2023 Daniel H. Huson
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
package splitstree6.view.trees.tanglegram.optimize;


import jloda.graph.Edge;
import jloda.graph.Node;
import jloda.graph.NodeArray;
import jloda.phylo.PhyloTree;
import jloda.util.BitSetUtils;

import java.io.IOException;
import java.util.*;

/**
 * computes an embedding for an order-prescribed network
 * Daniel Huson, 6.2011
 */
public class EmbedderForOrderPrescribedNetwork {
	private static final boolean verbose = false;

	/**
	 * given a mapping of each leaf to a different position, computes an embedding of the network that respects the ordering
	 *
	 */
	public static void apply(PhyloTree tree, Map<Node, Float> node2pos) throws IOException {
		if (tree.getRoot() != null) {
			if (verbose)
				System.err.println("Original network: " + tree.toBracketString(true) + ";");
			// recompute the lsa layout
			(new LayoutUnoptimized()).apply(tree);

			// get ordering of labeled leaves
			var orderedLabeledLeaves = computeOrderedLabeledLeaves(node2pos);

			// assign numbers to all subtrees obtained by disregarding all reticulate edges:
			try (var node2SubTreeId = tree.newNodeIntArray()) {
				var subTreeId2Root = new HashMap<Integer, Node>();

				var numberOfSubTrees = 0;
				for (var v : tree.nodes()) {
					if (node2SubTreeId.get(v) == null && v.getInDegree() != 1) {
						computeNode2SubTreeIdRec(v, ++numberOfSubTrees, node2SubTreeId);
						subTreeId2Root.put(numberOfSubTrees, v);
					}
				}
				if (verbose) {
					System.err.println("Leaf to subtree:");
					for (var v : tree.nodes()) {
						if (v.getOutDegree() == 0) {
							System.err.println("Leaf " + tree.getLabel(v) + " contained in subtree: " + node2SubTreeId.get(v));
						}
					}

					System.err.println("Position to subtree:");
					for (var p = 0; p < orderedLabeledLeaves.length; p++) {
						var v = orderedLabeledLeaves[p];
						System.err.println(" Position=" + p + " has label: " + tree.getLabel(v) + ", is subtree: " + node2SubTreeId.get(v));
					}
				}

				// map each reticulate node to its lsa parent
				var node2lsaParent = new HashMap<Node, Node>();
				for (var v : tree.nodes()) {
					if (tree.getLSAChildrenMap().get(v) != null) {
						for (var w : tree.getLSAChildrenMap().get(v)) {
							if (w.getInDegree() > 1)
								node2lsaParent.put(w, v);
						}
					}
				}

				// find nested subtrees and redirect their lsa edges to aim to lca of adjacent nodes of nesting subtree

				processNestedSubTrees(node2SubTreeId, numberOfSubTrees, subTreeId2Root, node2lsaParent, orderedLabeledLeaves, tree);

			}
			// extend node 2 pos mapping from labeled leaves to all nodes
			extendNode2PosRec(tree.getRoot(), node2pos);

			// reorder the children of each node so that they reflect computed ordering
			for (var v : tree.nodes()) {
				reorderChildren(v, node2pos);
			}

			if (verbose)
				System.err.println("Reordered network: " + tree.toBracketString(true) + ";");
		}
	}

	/**
	 * process all nested subtrees (nested means that in the ordering of taxa there is some other subtree with leaves
	 * both before and after the one considered)
	 */
	private static void processNestedSubTrees(NodeArray<Integer> node2SubTreeId, int numberOfSubTrees,
											  Map<Integer, Node> subTreeId2Root,
											  Map<Node, Node> node2lsaParent,
											  Node[] orderedLabeledLeaves, PhyloTree tree) throws IOException {
		var first = new Integer[numberOfSubTrees + 1];
		var last = new Integer[numberOfSubTrees + 1];

		var pos = 0;
		for (Node v : orderedLabeledLeaves) {
			if (v != null) {
				var id = node2SubTreeId.get(v);
				// System.err.println("Leaf: " + tree.getLabel(v) + " subtreeId: " + id);
				if (first[id] == null)
					first[id] = pos;
				last[id] = pos;
				pos++;
			}
		}

		int count = 0;

		// process each subtree:
		for (var id = 1; id <= numberOfSubTrees; id++) {
			var subTreeRoot = subTreeId2Root.get(id);
			var lsaParent = node2lsaParent.get(subTreeRoot);

			if (lsaParent != null) {
				var before = new BitSet();
				var between = new BitSet();
				var after = new BitSet();

				var firstPos = first[id];
				var lastPos = last[id];

				for (var p = 0; p < orderedLabeledLeaves.length; p++) {
					var v = orderedLabeledLeaves[p];
					if (v != null) {
						var vId = node2SubTreeId.get(v);
						if (p < firstPos)
							before.set(vId);
						else if (p > firstPos && p < lastPos)
							between.set(vId);
						else if (p > lastPos)
							after.set(vId);
					}
				}

				if (between.intersects(before) || between.intersects(after)) {
					System.err.println("WARNING: not nested");
					continue;
				}

				before.andNot(between);
				after.andNot(between);
				var spans = BitSetUtils.intersection(before, after);
				if (spans.cardinality() > 0) {
					Node leftNode = null;
					Node rightNode = null;
					int spanId = 0;

					for (var p = firstPos - 1; p >= 0; p--) {
						var v = orderedLabeledLeaves[p];
						if (v != null) {
							var vId = node2SubTreeId.get(v);
							if (spans.get(vId)) {
								spanId = vId;
								leftNode = v;
								break;
							}
						}
					}
					for (var p = lastPos + 1; p < orderedLabeledLeaves.length; p++) {
						var v = orderedLabeledLeaves[p];
						if (v != null) {
							var vId = node2SubTreeId.get(v);
							if (vId == spanId) {
								rightNode = v;
								break;
							}
						}
					}
					if (leftNode != null && rightNode != null) {
						// if (++count < 19)
						moveLSAParentToEnclosingSubTreeNode(lsaParent, leftNode, rightNode, subTreeRoot);
					}
				} else //is not nested inside another tree
				{
					moveLSAParentToRoot(lsaParent, tree.getRoot(), subTreeRoot);
				}
			}
		}
	}

	/**
	 * remove the lsa edge from lsaParent to v and reattach it so as to lead from the lca of leftNode and rightNode to v
	 *
	 */
	private static void moveLSAParentToEnclosingSubTreeNode(Node lsaParent, Node leftNode, Node rightNode, Node v) throws IOException {
		if (v.getInDegree() == 1)
			throw new IOException("Not subtree root");

		var tree = (PhyloTree) lsaParent.getOwner();

		if (true) {
            Queue<Node> queue = new LinkedList<>();
			queue.add(v);
			String label = null;
			while (label == null && queue.size() > 0) {
				var z = queue.poll();
				if (tree.getLabel(z) != null)
					label = tree.getLabel(z);
				else {
					for (var u : z.children()) {
						if (u.getInDegree() <= 1)
							queue.add(u);
					}
				}
			}
			if (verbose)
				System.err.println("Moving parent of subtree containing '" + label + "' to LCA(" + tree.getLabel(leftNode) + "," + tree.getLabel(rightNode) + ")");
		}

		if (!tree.getLSAChildrenMap().get(lsaParent).contains(v))
			throw new IOException("Not an LSA child");

		tree.getLSAChildrenMap().get(lsaParent).remove(v);

		var lca = getLCA(leftNode, rightNode);
		tree.getLSAChildrenMap().get(lca).add(v);
	}


	/**
	 * remove the lsa edge from lsaParent to v and reattach it so as to lead from the root to v
	 *
	 */
	private static void moveLSAParentToRoot(Node lsaParent, Node root, Node v) throws IOException {
		var tree = (PhyloTree) lsaParent.getOwner();

		if (true) {
			var queue = new LinkedList<Node>();
			queue.add(v);
			String label = null;
			while (label == null && queue.size() > 0) {
				Node z = queue.poll();
				if (tree.getLabel(z) != null)
					label = tree.getLabel(z);
				else {
					for (var u : z.children()) {
						if (u.getInDegree() <= 1)
							queue.add(u);
					}
				}
			}
			if (verbose)
				System.err.println("Moving parent of subtree containing '" + label + "' to root");
		}

		if (!tree.getLSAChildrenMap().get(lsaParent).contains(v))
			throw new IOException("Not an LSA child");

		tree.getLSAChildrenMap().get(lsaParent).remove(v);

		tree.getLSAChildrenMap().get(root).add(v);
	}


	/**
	 * gets the lca of two nodes
	 *
	 * @return lca(a, b)
	 */
	private static Node getLCA(Node a, Node b) throws IOException {
		var aboveA = new HashSet<Node>();
		while (true) {
			aboveA.add(a);
			if (a.getInDegree() != 1)
				break;
			a = a.getFirstInEdge().getSource();
		}

		while (true) {
			if (aboveA.contains(b))
				return b;
			if (b.getInDegree() != 1)
				break;
			b = b.getFirstInEdge().getSource();
		}
		if (!aboveA.contains(b))
			throw new IOException("Failed to determine LCA in tree");
		return ((PhyloTree) b.getOwner()).getRoot();
	}

	/**
	 * extend the node2pos ordering to all nodes of the tree
	 *
	 */
	public static void extendNode2PosRec(Node v, Map<Node, Float> node2pos) {
		if (node2pos.get(v) == null) {
			var pos = (float) Integer.MAX_VALUE;
			for (var w : getLSAChildren(v)) {
				extendNode2PosRec(w, node2pos);
				pos = Math.min(node2pos.get(w), pos);
			}
			// todo: if leaf without label, need to compute a better value using reticulate edges...
			node2pos.put(v, pos);
		}
	}

	/**
	 * get all children in tree, or LSA tree of network
	 *
	 * @return all children
	 */
	private static List<Node> getLSAChildren(Node v) {
		var list = new LinkedList<Node>();

		var tree = (PhyloTree) v.getOwner();

		if (tree != null) {
			List<Node> targetNodes = null;
			if (tree.getLSAChildrenMap().get(v) != null)
				targetNodes = tree.getLSAChildrenMap().get(v);

			if (targetNodes == null) {
				for (var e : v.outEdges()) {
					if (!tree.isReticulateEdge(e))
						list.add(e.getTarget());
				}
			} else {
				list.addAll(targetNodes);
			}
		}
		return list;
	}

	/**
	 * list labeled leafr nodes in order of appearance
	 *
	 */
	private static Node[] computeOrderedLabeledLeaves(Map<Node, Float> node2pos) {
		var pos2node = new TreeMap<Float, Node>();
		for (var v : node2pos.keySet()) {
			pos2node.put(node2pos.get(v), v);
		}
		var array = new Node[pos2node.size()];
		var i = 0;
		for (var f : pos2node.keySet()) {
			array[i++] = pos2node.get(f);
		}

		return array;
	}


	/**
	 * recursively number each subtree in the forest obtained by ignoring all reticulate edges
	 *
	 */
	private static void computeNode2SubTreeIdRec(Node v, int subTreeId, NodeArray<Integer> node2SubTreeId) {
		if (node2SubTreeId.get(v) == null) {
			node2SubTreeId.put(v, subTreeId);
			for (var w : v.children()) {
				if (w.getInDegree() == 1) // e not a reticulate edge
				{
					computeNode2SubTreeIdRec(w, subTreeId, node2SubTreeId);
				}
			}
		}
	}


	/**
	 * reorder the lsa children of a node
	 *
	 */
	private static void reorderChildren(Node v, final Map<Node, Float> node2pos) {
		var lsaChildren = ((PhyloTree) v.getOwner()).getLSAChildrenMap().get(v);
		if (lsaChildren != null) {
			var nodes = new TreeSet<Node>((w1, w2) -> {
				float pos1 = node2pos.get(w1);
				float pos2 = node2pos.get(w2);
				if (pos1 < pos2)
					return -1;
				else if (pos1 > pos2)
					return 1;
				else return Integer.compare(w1.getId(), w2.getId());
			});
			nodes.addAll(lsaChildren);
			lsaChildren.clear();
			lsaChildren.addAll(nodes);
		}

		var edges = new TreeSet<Edge>((e1, e2) -> {
			var w1 = e1.getTarget();
			var w2 = e2.getTarget();

			var pos1 = node2pos.get(w1);
			var pos2 = node2pos.get(w2);
			if (pos1 < pos2)
				return -1;
			else if (pos1 > pos2)
				return 1;
			else return Integer.compare(w1.getId(), w2.getId());
		});
		for (var e : v.adjacentEdges()) {
			edges.add(e);
		}
		v.rearrangeAdjacentEdges(edges);
	}


	/**
	 * return ordering of leaves to reflect list of names
	 *
	 * @return ordering of labeled leaves
	 */
	public static Map<Node, Float> setupOrderingFromNames(PhyloTree tree, List<String> orderedNames) throws IOException {
		var nodes = new Node[orderedNames.size()];
		var name2pos = new HashMap<String, Integer>();
		var pos = 0;
		for (var name : orderedNames) {
			name2pos.put(name, pos++);
		}
		for (var v : tree.nodes()) {
			if (v.isLeaf()) {
				var name = tree.getLabel(v);
				if (name == null || name.trim().length() == 0)
					throw new IOException("Unlabeled leaf encountered");
				var thePos = name2pos.get(name);
				if (thePos == null)
					throw new IOException("Leaf-label without position encountered: " + name);
				nodes[thePos] = v;
			}
		}
		var node2pos = new HashMap<Node, Float>();
		for (int i = 0; i < nodes.length; i++)
			node2pos.put(nodes[i], (float) i + 1);
		return node2pos;
	}
}
