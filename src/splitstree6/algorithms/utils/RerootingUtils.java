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

import jloda.fx.window.NotificationManager;
import jloda.graph.Edge;
import jloda.graph.EdgeArray;
import jloda.graph.EdgeDoubleArray;
import jloda.graph.Node;
import jloda.phylo.PhyloTree;
import jloda.util.BitSetUtils;

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
	 * reroot a tree by midpoint
	 */
	public static void rerootByMidpoint(boolean internalNodeLabelsAreEdgeLabels, PhyloTree tree) {
		if (tree.isReticulated()) {
			NotificationManager.showWarning(tree.getName() + ": reticulated, re-root not implemented");
			return;
		}

		var list = computeRootingRecords(tree);
		if (list.size() > 0) {
			list.sort(RootingRecord.comparatorForMidpointRooting());
			reroot(internalNodeLabelsAreEdgeLabels, tree, list.get(0), false);
		}
	}


	private static void reroot(boolean internalNodeLabelsAreEdgeLabels, PhyloTree tree, RootingRecord rootingRecord, boolean forceOnEdge) {
		var half = 0.5 * rootingRecord.total();

		if (rootingRecord.sourceToLeafMaxDistance() - rootingRecord.weight() >= half) {
			if (forceOnEdge) {
				var weight2source = 0.9 * rootingRecord.weight();
				var weight2target = 0.1 * rootingRecord.weight();
				rerootByEdge(internalNodeLabelsAreEdgeLabels, tree, rootingRecord.edge(), weight2source, weight2target);
			} else {
				var root = rootingRecord.edge().getTarget(); // yes, target!
				if (root != tree.getRoot())
					rerootByNode(internalNodeLabelsAreEdgeLabels, tree, root);
			}
		} else if (rootingRecord.targetToLeafMaxDistance() - rootingRecord.weight() >= half) {
			if (forceOnEdge) {
				var weight2source = 0.1 * rootingRecord.weight();
				var weight2target = 0.9 * rootingRecord.weight();
				rerootByEdge(internalNodeLabelsAreEdgeLabels, tree, rootingRecord.edge(), weight2source, weight2target);
			} else {
				var root = rootingRecord.edge().getSource(); // yes, source!
				if (root != tree.getRoot())
					rerootByNode(internalNodeLabelsAreEdgeLabels, tree, root);
			}
		} else {
			var weight2source = half - (rootingRecord.targetToLeafMaxDistance() - rootingRecord.weight());
			var weight2target = half - (rootingRecord.sourceToLeafMaxDistance() - rootingRecord.weight());
			rerootByEdge(internalNodeLabelsAreEdgeLabels, tree, rootingRecord.edge(), weight2source, weight2target);
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
				result.add(new RootingRecord(e, tree.getWeight(e), sourceMap.get(e), targetMap.get(e)));
			}
			return result;
		}
	}

	private static double computeRootingRecordsRec(PhyloTree tree, Node v, Edge inEdge, EdgeDoubleArray sourceMap, EdgeDoubleArray targetMap) {
		var maxDistance = 0.0;
		for (var f : v.adjacentEdges()) {
			if (f != inEdge) {
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

	/**
	 * reroot a tree by outgroup. Use tightest edge that separates all outgroup taxa
	 */
	public static void rerootByOutGroup(boolean internalNodeLabelsAreEdgeLabels, PhyloTree tree, BitSet outgroupTaxa) {
		if (tree.isReticulated()) {
			NotificationManager.showWarning(tree.getName() + ": reticulated, re-root not implemented");
			return;
		}

		try (EdgeArray<BitSet> sourceMap = tree.newEdgeArray(); EdgeArray<BitSet> targetMap = tree.newEdgeArray()) {
			for (var start : tree.nodeStream().filter(v -> v.getDegree() == 1).collect(Collectors.toList())) {
				computeTaxaRec(tree, start, null, sourceMap, targetMap);
			}

			Edge best = null;
			BitSet bestSet = null;
			for (var e : tree.edges()) {
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
			if (best != null) {
				for (var rootingRecord : computeRootingRecords(tree)) {
					if (rootingRecord.edge() == best) {
						reroot(internalNodeLabelsAreEdgeLabels, tree, rootingRecord, true);
						return;
					}
				}
			}
		}
	}

	private static BitSet computeTaxaRec(PhyloTree tree, Node v, Edge inEdge, EdgeArray<BitSet> sourceMap, EdgeArray<BitSet> targetMap) {
		var taxa = new BitSet();
		for (var f : v.adjacentEdges()) {
			if (f != inEdge) {
				var map = (v == f.getSource() ? sourceMap : targetMap);
				if (f.getOpposite(v).isLeaf()) {
					taxa.or(map.computeIfAbsent(f, a -> BitSetUtils.asBitSet(tree.getTaxa(a.getOpposite(v)))));
				} else
					taxa.or(map.computeIfAbsent(f, a -> computeTaxaRec(tree, a.getOpposite(v), a, sourceMap, targetMap)));
			}
		}
		return taxa;
	}


	/**
	 * reroot tree by node
	 */
	public static void rerootByNode(boolean internalNodeLabelsAreEdgeLabels, PhyloTree tree, Node v) {
		if (v == tree.getRoot())
			return;

		final var root = tree.getRoot();
		if (root.getDegree() == 2 && tree.getLabel(root) == null) {
			tree.delDivertex(root);
		}

		final EdgeArray<String> edgeLabels;
		if (internalNodeLabelsAreEdgeLabels)
			edgeLabels = SupportValueUtils.setEdgeLabelsFromInternalNodeLabels(tree);
		else
			edgeLabels = null;

		tree.eraseRoot(edgeLabels);
		tree.setRoot(v);
		tree.redirectEdgesAwayFromRoot();

		if (internalNodeLabelsAreEdgeLabels)
			SupportValueUtils.setInternalNodeLabelsFromEdgeLabels(tree, edgeLabels);
	}

	/**
	 * reroot tree by edge
	 */
	public static void rerootByEdge(boolean internalNodeLabelsAreEdgeLabels, PhyloTree tree, Edge e, double weight2source, double weight2target) {
		final EdgeArray<String> edgeLabels;
		if (internalNodeLabelsAreEdgeLabels)
			edgeLabels = SupportValueUtils.setEdgeLabelsFromInternalNodeLabels(tree);
		else
			edgeLabels = null;

		var source = e.getSource();
		var target = e.getTarget();
		// not under a special node, reroot in simple way
		tree.setRoot(e, edgeLabels);

		tree.redirectEdgesAwayFromRoot();

		if (internalNodeLabelsAreEdgeLabels)
			SupportValueUtils.setInternalNodeLabelsFromEdgeLabels(tree, edgeLabels);

		var root = tree.getRoot();
		tree.setWeight(root.getCommonEdge(source), weight2source);
		tree.setWeight(root.getCommonEdge(target), weight2target);
	}
}
