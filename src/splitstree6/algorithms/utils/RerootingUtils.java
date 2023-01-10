/*
 * RerootingUtils.java Copyright (C) 2023 Daniel H. Huson
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
	 * reroot a tree by midpoint
	 */
	public static void rerootByMidpoint(PhyloTree tree) {
		var list = computeRootingRecords(tree);
		if (list.size() > 0) {
			list.sort(RootingRecord.comparatorForMidpointRooting());
			reroot(tree, list.get(0), false);
		}
	}

	/**
	 * reroot a tree by out group. Use tightest edge that separates all outgroup taxa
	 */
	public static void rerootByOutgroup(PhyloTree tree, BitSet outgroupTaxa) {
		try (EdgeArray<BitSet> clusters = tree.newEdgeArray()) {
			tree.postorderTraversal(v -> {
				var bits = new BitSet();
				bits.or(BitSetUtils.asBitSet(tree.getTaxa(v)));
				for (var e : v.outEdges()) {
					bits.or(clusters.get(e));
				}
				for (var e : v.inEdges()) {
					clusters.put(e, bits);
				}
			});
			var allTaxa = BitSetUtils.asBitSet(tree.getTaxa());

			Edge best = null;
			BitSet bestSet = null;
			for (var e : tree.edges()) {
				if (!tree.isReticulateEdge(e)) {
					var set = clusters.get(e);
					if (BitSetUtils.contains(set, outgroupTaxa)) {
						if (bestSet == null || set.cardinality() < bestSet.cardinality()) {
							best = e;
							bestSet = set;
						}
					} else if (!set.intersects(outgroupTaxa)) {
						set = BitSetUtils.minus(allTaxa, set);
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
						reroot(tree, rootingRecord, true);
						return;
					}
				}
			}
		}
	}

	/**
	 * reroot tree by node
	 */
	public static void rerootByNode(PhyloTree tree, Node v) {
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

		tree.eraseRoot(null);
		tree.setRoot(v);
		tree.redirectEdgesAwayFromRoot();
	}

	/**
	 * reroot tree by edge
	 */
	public static void rerootByEdge(PhyloTree tree, Edge e, double weight2source, double weight2target) {
		var v = getLastAncestorAboveAllReticulations(e.getSource());

		if (v != e.getSource()) {
			rerootByNode(tree, v);
		} else {
			if (tree.getRoot().getOutDegree() == 1 && tree.getNumberOfTaxa(tree.getRoot()) == 0) {
				var root = tree.getRoot().getFirstOutEdge().getTarget();
				tree.deleteNode(tree.getRoot());
				tree.setRoot(root);
			}

			// not under a special node, reroot in simple way
			tree.setRoot(e, weight2source, weight2target, null);

			tree.redirectEdgesAwayFromRoot();
			if (false)
				System.err.println("Rerooted:\n" + tree.toBracketString(true) + ";");
		}
	}

	private static void reroot(PhyloTree tree, RootingRecord rootingRecord, boolean forceOnEdge) {
		var half = 0.5 * rootingRecord.total();

		if (rootingRecord.sourceToLeafMaxDistance() - rootingRecord.weight() >= half) {
			if (forceOnEdge) {
				var weight2source = 0.9 * rootingRecord.weight();
				var weight2target = 0.1 * rootingRecord.weight();
				rerootByEdge(tree, rootingRecord.edge(), weight2source, weight2target);
			} else {
				var root = rootingRecord.edge().getTarget(); // yes, target!
				if (root != tree.getRoot())
					rerootByNode(tree, root);
			}
		} else if (rootingRecord.targetToLeafMaxDistance() - rootingRecord.weight() >= half) {
			if (forceOnEdge) {
				var weight2source = 0.1 * rootingRecord.weight();
				var weight2target = 0.9 * rootingRecord.weight();
				rerootByEdge(tree, rootingRecord.edge(), weight2source, weight2target);
			} else {
				var root = rootingRecord.edge().getSource(); // yes, source!
				if (root != tree.getRoot())
					rerootByNode(tree, root);
			}
		} else {
			var weight2source = half - (rootingRecord.targetToLeafMaxDistance() - rootingRecord.weight());
			var weight2target = half - (rootingRecord.sourceToLeafMaxDistance() - rootingRecord.weight());
			rerootByEdge(tree, rootingRecord.edge(), weight2source, weight2target);
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

	public static record RootingRecord(Edge edge, double weight, double sourceToLeafMaxDistance,
									   double targetToLeafMaxDistance) {

		public static Comparator<RootingRecord> comparatorForMidpointRooting() {
			return (a, b) -> {
				if (Math.abs(a.total() - b.total()) < 0.001)
					return Double.compare(Math.max(a.sourceToLeafMaxDistance() - a.weight(), a.targetToLeafMaxDistance() - a.weight()),
							Math.max(b.sourceToLeafMaxDistance() - b.weight(), b.targetToLeafMaxDistance() - b.weight()));
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
}
