/*
 *  EmbedderForOrderPrescribedNetwork.java Copyright (C) 2022 Daniel H. Huson
 *
 *  (Some files contain contributions from other authors, who are then mentioned separately.)
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package splitstree6.view.trees.tanglegram.optimize;


import jloda.graph.Edge;
import jloda.graph.Node;
import jloda.graph.NodeArray;
import jloda.phylo.PhyloTree;
import jloda.util.BitSetUtils;
import jloda.util.progress.ProgressSilent;

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
	 * @param tree
	 * @param node2pos
	 */
	public static void apply(PhyloTree tree, Map<Node, Float> node2pos) throws IOException {
		if (tree.getRoot() != null) {
			if (verbose)
				System.err.println("Original network: " + tree.toBracketString(true) + ";");
			// recompute the lsa layout
			(new LayoutUnoptimized()).apply(tree, new ProgressSilent());

			// get ordering of labeled leaves
			Node[] orderedLabeledLeaves = computeOrderedLabeledLeaves(node2pos);

			// assign numbers to all subtrees obtained by disregarding all reticulate edges:
			NodeArray<Integer> node2SubTreeId = new NodeArray<Integer>(tree);
			Map<Integer, Node> subTreeId2Root = new HashMap<Integer, Node>();

			int numberOfSubTrees = 0;
			for (Node v = tree.getFirstNode(); v != null; v = v.getNext()) {
				if (node2SubTreeId.get(v) == null && v.getInDegree() != 1) {
					computeNode2SubTreeIdRec(v, ++numberOfSubTrees, node2SubTreeId);
					subTreeId2Root.put(numberOfSubTrees, v);
				}
			}
			if (verbose) {
				System.err.println("Leaf to subtree:");
				for (Node v = tree.getFirstNode(); v != null; v = v.getNext()) {
					if (v.getOutDegree() == 0) {
						System.err.println("Leaf " + tree.getLabel(v) + " contained in subtree: " + node2SubTreeId.get(v));
					}
				}

				System.err.println("Position to subtree:");
				for (int p = 0; p < orderedLabeledLeaves.length; p++) {
					Node v = orderedLabeledLeaves[p];
					System.err.println(" Position=" + p + " has label: " + tree.getLabel(v) + ", is subtree: " + node2SubTreeId.get(v));
				}
			}

			// map each reticulate node to its lsa parent
			Map<Node, Node> node2lsaParent = new HashMap<Node, Node>();
			for (Node v = tree.getFirstNode(); v != null; v = v.getNext()) {
				if (tree.getLSAChildrenMap().get(v) != null) {
					for (Node w : tree.getLSAChildrenMap().get(v)) {
						if (w.getInDegree() > 1)
							node2lsaParent.put(w, v);
					}
				}
			}


			// find nested subtrees and redirect their lsa edges to aim to lca of adjacent nodes of nesting subtree

			processNestedSubTrees(node2SubTreeId, numberOfSubTrees, subTreeId2Root, node2lsaParent, orderedLabeledLeaves, tree);

			// extend node 2 pos mapping from labeled leaves to all nodes
			extendNode2PosRec(tree.getRoot(), node2pos);

			// reorder the children of each node so that they reflect computed ordering
			for (Node v = tree.getFirstNode(); v != null; v = v.getNext()) {
				reorderChildren(v, node2pos);
			}

			if (verbose)
				System.err.println("Reordered network: " + tree.toBracketString(true) + ";");
		}
	}

	/**
	 * process all nested subtrees (nested means that in the ordering of taxa there is some other subtree with leaves
	 * boht before and after the one considered)
	 *
	 * @param node2SubTreeId
	 * @param numberOfSubTrees
	 * @param tree
	 */
	private static void processNestedSubTrees(NodeArray<Integer> node2SubTreeId, int numberOfSubTrees,
											  Map<Integer, Node> subTreeId2Root,
											  Map<Node, Node> node2lsaParent,
											  Node[] orderedLabeledLeaves, PhyloTree tree) throws IOException {
		Integer[] first = new Integer[numberOfSubTrees + 1];
		Integer[] last = new Integer[numberOfSubTrees + 1];

		int pos = 0;
		for (Node v : orderedLabeledLeaves) {
			if (v != null) {
				int id = node2SubTreeId.get(v);
				// System.err.println("Leaf: " + tree.getLabel(v) + " subtreeId: " + id);
				if (first[id] == null)
					first[id] = pos;
				last[id] = pos;
				pos++;
			}
		}

		int count = 0;

		// process each subtree:
		for (int id = 1; id <= numberOfSubTrees; id++) {
			Node subTreeRoot = subTreeId2Root.get(id);
			Node lsaParent = node2lsaParent.get(subTreeRoot);

			if (lsaParent != null) {
				BitSet before = new BitSet();
				BitSet between = new BitSet();
				BitSet after = new BitSet();

				int firstPos = first[id];
				int lastPos = last[id];

				for (int p = 0; p < orderedLabeledLeaves.length; p++) {
					Node v = orderedLabeledLeaves[p];
					if (v != null) {
						int vId = node2SubTreeId.get(v);
						if (p < firstPos)
							before.set(vId);
						else if (p > firstPos && p < lastPos)
							between.set(vId);
						else if (p > lastPos)
							after.set(vId);
					}
				}

				if (between.intersects(before) || between.intersects(after))
					System.err.println("WARNING: not nested");


				before.andNot(between);
				after.andNot(between);
				var spans = BitSetUtils.intersection(before, after);
				if (spans.cardinality() > 0) {
					Node leftNode = null;
					Node rightNode = null;
					int spanId = 0;

					for (int p = firstPos - 1; p >= 0; p--) {
						Node v = orderedLabeledLeaves[p];
						if (v != null) {
							int vId = node2SubTreeId.get(v);
							if (spans.get(vId)) {
								spanId = vId;
								leftNode = v;
								break;
							}
						}
					}
					for (int p = lastPos + 1; p < orderedLabeledLeaves.length; p++) {
						Node v = orderedLabeledLeaves[p];
						if (v != null) {
							int vId = node2SubTreeId.get(v);
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
	 * @param lsaParent
	 * @param leftNode
	 * @param rightNode
	 * @param v
	 */
	private static void moveLSAParentToEnclosingSubTreeNode(Node lsaParent, Node leftNode, Node rightNode, Node v) throws IOException {
		if (v.getInDegree() == 1)
			throw new IOException("Not subtree root");

		PhyloTree tree = (PhyloTree) lsaParent.getOwner();

		if (true) {
			Queue<Node> queue = new LinkedList<Node>();
			queue.add(v);
			String label = null;
			while (label == null && queue.size() > 0) {
				Node z = queue.poll();
				if (tree.getLabel(z) != null)
					label = tree.getLabel(z);
				else {
					for (Edge e = z.getFirstOutEdge(); e != null; e = z.getNextOutEdge(e)) {
						Node u = e.getTarget();
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

		Node lca = getLCA(leftNode, rightNode);
		tree.getLSAChildrenMap().get(lca).add(v);
	}


	/**
	 * remove the lsa edge from lsaParent to v and reattach it so as to lead from the root to v
	 *
	 * @param lsaParent
	 * @param root
	 * @param v
	 */
	private static void moveLSAParentToRoot(Node lsaParent, Node root, Node v) throws IOException {
		PhyloTree tree = (PhyloTree) lsaParent.getOwner();

		if (true) {
			Queue<Node> queue = new LinkedList<Node>();
			queue.add(v);
			String label = null;
			while (label == null && queue.size() > 0) {
				Node z = queue.poll();
				if (tree.getLabel(z) != null)
					label = tree.getLabel(z);
				else {
					for (Edge e = z.getFirstOutEdge(); e != null; e = z.getNextOutEdge(e)) {
						Node u = e.getTarget();
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
	 * @param a
	 * @param b
	 * @return lca(a, b)
	 */
	private static Node getLCA(Node a, Node b) throws IOException {
		Set<Node> aboveA = new HashSet<Node>();
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
	 * @param v
	 * @param node2pos
	 */
	public static void extendNode2PosRec(Node v, Map<Node, Float> node2pos) {
		if (node2pos.get(v) == null) {
			float pos = Integer.MAX_VALUE;
			for (Node w : getLSAChildren(v)) {
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
	 * @param v
	 * @return all children
	 */
	private static List<Node> getLSAChildren(Node v) {
		var tree = (PhyloTree) v.getOwner();

		List<Node> targetNodes = null;
		if (tree.getLSAChildrenMap().get(v) != null)
			targetNodes = tree.getLSAChildrenMap().get(v);
		List<Node> list = new LinkedList<Node>();

		if (targetNodes == null) {
			for (Edge e = v.getFirstOutEdge(); e != null; e = v.getNextOutEdge(e)) {
				if (!tree.isReticulatedEdge(e))
					list.add(e.getTarget());
			}
		} else {
			list.addAll(targetNodes);
		}
		return list;
	}

	/**
	 * list labeled leafr nodes in order of appearance
	 *
	 * @param node2pos
	 * @return
	 */
	private static Node[] computeOrderedLabeledLeaves(Map<Node, Float> node2pos) {
		SortedMap<Float, Node> pos2node = new TreeMap<Float, Node>();
		for (Node v : node2pos.keySet()) {
			pos2node.put(node2pos.get(v), v);
		}
		Node[] array = new Node[pos2node.size()];
		int i = 0;
		for (Float f : pos2node.keySet()) {
			array[i++] = pos2node.get(f);
		}

		return array;
	}


	/**
	 * recursively number each subtree in the forest obtained by ignoring all reticulate edges
	 *
	 * @param v
	 * @param subTreeId
	 */
	private static void computeNode2SubTreeIdRec(Node v, int subTreeId, NodeArray<Integer> node2SubTreeId) {
		if (node2SubTreeId.get(v) == null) {
			node2SubTreeId.put(v, subTreeId);
			for (Edge e = v.getFirstOutEdge(); e != null; e = v.getNextOutEdge(e)) {
				Node w = e.getTarget();
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
	 * @param v
	 * @param node2pos
	 */
	private static void reorderChildren(Node v, final Map<Node, Float> node2pos) {
		List<Node> lsaChildren = ((PhyloTree) v.getOwner()).getLSAChildrenMap().get(v);
		if (lsaChildren != null) {
			SortedSet<Node> nodes = new TreeSet<Node>(new Comparator<Node>() {
				public int compare(Node w1, Node w2) {
					float pos1 = node2pos.get(w1);
					float pos2 = node2pos.get(w2);
					if (pos1 < pos2)
						return -1;
					else if (pos1 > pos2)
						return 1;
					else if (w1.getId() < w2.getId())
						return -1;
					else if (w1.getId() > w2.getId())
						return 1;
					else
						return 0;
				}
			});
			nodes.addAll(lsaChildren);
			lsaChildren.clear();
			lsaChildren.addAll(nodes);
		}

		SortedSet<Edge> edges = new TreeSet<Edge>(new Comparator<Edge>() {
			public int compare(Edge e1, Edge e2) {
				Node w1 = e1.getTarget();
				Node w2 = e2.getTarget();

				float pos1 = node2pos.get(w1);
				float pos2 = node2pos.get(w2);
				if (pos1 < pos2)
					return -1;
				else if (pos1 > pos2)
					return 1;
				else if (w1.getId() < w2.getId())
					return -1;
				else if (w1.getId() > w2.getId())
					return 1;
				else
					return 0;
			}
		});
		for (Edge e : v.adjacentEdges()) {
			edges.add(e);
		}
		v.rearrangeAdjacentEdges(edges);
	}


	/**
	 * return ordering of leaves to reflect list of names
	 *
	 * @param tree
	 * @param names
	 * @return ordering of labeled leaves
	 */
	public static Map<Node, Float> setupOrderingFromNames(PhyloTree tree, List<String> names) throws IOException {
		Node[] nodes = new Node[names.size()];
		Map<String, Integer> name2pos = new HashMap<String, Integer>();
		int pos = 0;
		for (String name : names) {
			name2pos.put(name, pos++);
		}
		for (Node v = tree.getFirstNode(); v != null; v = v.getNext()) {
			if (v.getOutDegree() == 0) {
				String name = tree.getLabel(v);
				if (name == null || name.trim().length() == 0)
					throw new IOException("Unlabeled leaf encountered");
				Integer thePos = name2pos.get(name);
				if (thePos == null)
					throw new IOException("Leaf-label without position encountered: " + name);
				nodes[thePos] = v;
			}
		}
		Map<Node, Float> node2pos = new HashMap<Node, Float>();
		for (int i = 0; i < nodes.length; i++)
			node2pos.put(nodes[i], (float) i + 1);
		return node2pos;
	}
}
