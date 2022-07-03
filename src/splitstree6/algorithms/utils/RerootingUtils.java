/*
 * RerootingUtils.java Copyright (C) 2022 Daniel H. Huson
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

package splitstree6.algorithms.utils;

import jloda.graph.Edge;
import jloda.graph.EdgeArray;
import jloda.graph.EdgeDoubleArray;
import jloda.graph.Node;
import jloda.phylo.PhyloTree;
import jloda.util.BitSetUtils;
import jloda.util.IteratorUtils;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Comparator;
import java.util.stream.Collectors;

/**
 * rerooting methods
 * Daniel Huson, 4.2008
 */
public class RerootingUtils {
	/**
	 * reRoot a tree by midpoint
	 */
	public static void reRootByMidpoint(boolean internalNodeLabelsAreEdgeLabels, PhyloTree tree) {
		var list = computeRootingRecords(tree);
		if (list.size() > 0) {
			list.sort(RootingRecord.comparatorForMidpointRooting());
			reRoot(internalNodeLabelsAreEdgeLabels, tree, list.get(0), false);
		}
	}

	/**
	 * reRoot a tree by out group. Use tightest edge that separates all outgroup taxa
	 */
	public static void reRootByOutGroup(boolean internalNodeLabelsAreEdgeLabels, PhyloTree tree, BitSet outgroupTaxa) {
		try (EdgeArray<BitSet> sourceMap = tree.newEdgeArray(); EdgeArray<BitSet> targetMap = tree.newEdgeArray()) {
			for (var start : tree.nodeStream().filter(v -> v.getDegree() == 1).collect(Collectors.toList())) {
				try (var visited = tree.newNodeSet()) {
					computeTaxaRec(tree, start, sourceMap, targetMap);
				}
			}

			Edge best = null;
			BitSet bestSet = null;
			for (var e : tree.edges()) {
				if (!tree.isReticulateEdge(e)) {
					var set = sourceMap.get(e);
					if (BitSetUtils.intersection(set, outgroupTaxa).cardinality() < outgroupTaxa.cardinality())
						set = targetMap.get(e);
					if (BitSetUtils.contains(set, outgroupTaxa)) {
						if (bestSet == null || set.cardinality() < bestSet.cardinality()) {
							best = e;
							bestSet = set;
						}
					}
				}
			}
			if (best != null) {
				for (var rootingRecord : computeRootingRecords(tree)) {
					if (rootingRecord.edge() == best) {
						reRoot(internalNodeLabelsAreEdgeLabels, tree, rootingRecord, true);
						return;
					}
				}
			}
		}
	}

	/**
	 * reRoot tree by node
	 */
	public static void reRootByNode(boolean internalNodeLabelsAreEdgeLabels, PhyloTree tree, Node v) {
		v = getLastAncestorAboveAllReticulations(v);

		if (v == tree.getRoot())
			return;

		if (tree.getRoot().getOutDegree() == 1 && tree.getNumberOfTaxa(tree.getRoot()) == 0) {
			var root = tree.getRoot().getFirstOutEdge().getTarget();
			tree.deleteNode(tree.getRoot());
			tree.setRoot(root);
		}

		if (v == tree.getRoot())
			return;

		final var root = tree.getRoot();
		if (root.getDegree() == 2 && tree.getLabel(root) == null) {
			tree.delDivertex(root);
		}

		final EdgeArray<String> edgeLabels;
		if (internalNodeLabelsAreEdgeLabels && !PhyloTree.SUPPORT_RICH_NEWICK)
			edgeLabels = SupportValueUtils.setEdgeLabelsFromInternalNodeLabels(tree);
		else
			edgeLabels = null;

		tree.eraseRoot(edgeLabels);
		tree.setRoot(v);
		tree.redirectEdgesAwayFromRoot();

		if (edgeLabels != null)
			SupportValueUtils.setInternalNodeLabelsFromEdgeLabels(tree, edgeLabels);
	}

	/**
	 * reRoot tree by edge
	 */
	public static void reRootByEdge(boolean internalNodeLabelsAreEdgeLabels, PhyloTree tree, Edge e, double weight2source, double weight2target) {
		final EdgeArray<String> edgeLabels;
		if (internalNodeLabelsAreEdgeLabels && !PhyloTree.SUPPORT_RICH_NEWICK)
			edgeLabels = SupportValueUtils.setEdgeLabelsFromInternalNodeLabels(tree);
		else
			edgeLabels = null;

		var v = getLastAncestorAboveAllReticulations(e.getSource());

		if (v != e.getSource()) {
			reRootByNode(internalNodeLabelsAreEdgeLabels, tree, v);
		} else {
			if (tree.getRoot().getOutDegree() == 1 && tree.getNumberOfTaxa(tree.getRoot()) == 0) {
				var root = tree.getRoot().getFirstOutEdge().getTarget();
				tree.deleteNode(tree.getRoot());
				tree.setRoot(root);
			}

			// not under a special node, reRoot in simple way
			tree.setRoot(e, weight2source, weight2target, edgeLabels);

			tree.redirectEdgesAwayFromRoot();

			System.err.println("Rerooted:\n" + tree.toBracketString(true) + ";");

			if (edgeLabels != null)
				SupportValueUtils.setInternalNodeLabelsFromEdgeLabels(tree, edgeLabels);
		}
	}

	private static void reRoot(boolean internalNodeLabelsAreEdgeLabels, PhyloTree tree, RootingRecord rootingRecord, boolean forceOnEdge) {
		var half = 0.5 * rootingRecord.total();

		if (rootingRecord.sourceToLeafMaxDistance() - rootingRecord.weight() >= half) {
			if (forceOnEdge) {
				var weight2source = 0.9 * rootingRecord.weight();
				var weight2target = 0.1 * rootingRecord.weight();
				reRootByEdge(internalNodeLabelsAreEdgeLabels, tree, rootingRecord.edge(), weight2source, weight2target);
			} else {
				var root = rootingRecord.edge().getTarget(); // yes, target!
				if (root != tree.getRoot())
					reRootByNode(internalNodeLabelsAreEdgeLabels, tree, root);
			}
		} else if (rootingRecord.targetToLeafMaxDistance() - rootingRecord.weight() >= half) {
			if (forceOnEdge) {
				var weight2source = 0.1 * rootingRecord.weight();
				var weight2target = 0.9 * rootingRecord.weight();
				reRootByEdge(internalNodeLabelsAreEdgeLabels, tree, rootingRecord.edge(), weight2source, weight2target);
			} else {
				var root = rootingRecord.edge().getSource(); // yes, source!
				if (root != tree.getRoot())
					reRootByNode(internalNodeLabelsAreEdgeLabels, tree, root);
			}
		} else {
			var weight2source = half - (rootingRecord.targetToLeafMaxDistance() - rootingRecord.weight());
			var weight2target = half - (rootingRecord.sourceToLeafMaxDistance() - rootingRecord.weight());
			reRootByEdge(internalNodeLabelsAreEdgeLabels, tree, rootingRecord.edge(), weight2source, weight2target);
		}
	}

	/**
	 * compute edge max distances in decreasing length
	 *
	 * @param tree tree
	 * @return edge max distances
	 */
	public static ArrayList<RootingRecord> computeRootingRecords(PhyloTree tree) {
		try (var sourceMap = tree.newEdgeDoubleArray(); var targetMap = tree.newEdgeDoubleArray()) {
			for (var start : tree.nodeStream().filter(v -> v.getDegree() == 1).collect(Collectors.toList())) {
				computeRootingRecordsRec(tree, start, null, sourceMap, targetMap);
			}
			var result = new ArrayList<RootingRecord>();
			for (var e : tree.edges()) {
				if (!tree.isReticulateEdge(e))
					result.add(new RootingRecord(e, tree.getWeight(e), sourceMap.get(e), targetMap.get(e)));
			}
			return result;
		}
	}

	private static double computeRootingRecordsRec(PhyloTree tree, Node v, Edge inEdge, EdgeDoubleArray sourceMap, EdgeDoubleArray targetMap) {
		var maxDistance = 0.0;
		for (var f : v.adjacentEdges()) {
			if (f != inEdge && tree.okToDescendDownThisEdgeInTraversal(f)) {
				var map = (v == f.getSource() ? sourceMap : targetMap);
				maxDistance = Math.max(maxDistance, map.computeIfAbsent(f, a -> computeRootingRecordsRec(tree, a.getOpposite(v), a, sourceMap, targetMap) + tree.getWeight(f)));
			}
		}
		return maxDistance;
	}

	public static record RootingRecord(Edge edge, double weight, double sourceToLeafMaxDistance,
									   double targetToLeafMaxDistance) {

		public static Comparator<RootingRecord> comparatorForMidpointRooting() {
			return (a, b) -> {
				if (Math.abs(a.total() - b.total()) < 0.001)
					return Double.compare(Math.max(a.sourceToLeafMaxDistance() - a.weight(), a.targetToLeafMaxDistance() - a.weight()), Math.max(b.sourceToLeafMaxDistance() - b.weight(), b.targetToLeafMaxDistance() - b.weight()));
				else
					return -Double.compare(a.total(), b.total());
			};
		}

		public double total() {
			return sourceToLeafMaxDistance + targetToLeafMaxDistance - weight;
		}

		public String toString() {
			return "weight=%f sourceToLeaf=%f targetToLeaf=%f total=%f".formatted(weight, sourceToLeafMaxDistance, targetToLeafMaxDistance, total());
		}
	}


	private static BitSet computeTaxaRec(PhyloTree tree, Node v, EdgeArray<BitSet> sourceMap, EdgeArray<BitSet> targetMap) {
		var taxa = new BitSet();
		for (var f : v.adjacentEdges()) {
			var w = f.getOpposite(v);
			var map = (v == f.getSource() ? sourceMap : targetMap);
			if (!map.containsKey(f)) {
				var bits = new BitSet();
				map.put(f, bits);
				if (w.isLeaf()) {
					bits.or(BitSetUtils.asBitSet(tree.getTaxa(w)));
				} else {
					bits.or(computeTaxaRec(tree, w, sourceMap, targetMap));
				}
				taxa.or(bits);
			}
		}
		return taxa;
	}

	/**
	 * get lowest ancestor above all reticulate nodes
	 *
	 * @param v node
	 * @return node or root
	 */
	private static Node getLastAncestorAboveAllReticulations(Node v) {
		var path = new ArrayList<Node>();
		for (var w = v; w != null; w = w.getParent()) {
			path.add(w);
		}
		for (var w : IteratorUtils.reverseIterator(path)) {
			if (w.getInDegree() <= 1)
				v = w;
			else if (w.getInDegree() > 1)
				break;
		}
		return v;
	}
}
