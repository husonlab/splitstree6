/*
 * RerootingUtils.java Copyright (C) 2021. Daniel H. Huson
 *
 * (Some code written by other authors, as named in code.)
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
 *
 */

package splitstree6.algorithms.utils;

import jloda.fx.window.NotificationManager;
import jloda.graph.*;
import jloda.phylo.PhyloTree;
import jloda.util.Pair;
import jloda.util.Triplet;

import java.util.BitSet;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * rerooting methods
 * Daniel Huson, 4.2008
 */
public class RerootingUtils {
	/**
	 * reroot tree by edge
	 */
	public static void rerootByEdge(boolean internalNodeLabelsAreEdgeLabels, PhyloTree tree, Edge e) {
		final EdgeArray<String> edgeLabels;
		if (internalNodeLabelsAreEdgeLabels)
			edgeLabels = SupportValueUtils.setEdgeLabelsFromInternalNodeLabels(tree);
		else
			edgeLabels = null;

		// not under a special node, reroot in simple way
		tree.setRoot(e, edgeLabels);

		tree.redirectEdgesAwayFromRoot();

		if (internalNodeLabelsAreEdgeLabels)
			SupportValueUtils.setInternalNodeLabelsFromEdgeLabels(tree, edgeLabels);

		var root = tree.getRoot();

		if (root.getDegree() == 2 && tree.getLabel(root) == null) {
			final var ea = root.getFirstAdjacentEdge();
			final var eb = root.getLastAdjacentEdge();
			final var weight = tree.getWeight(ea) + tree.getWeight(eb);
			final var a = computeAverageDistanceToALeaf(tree, ea.getOpposite(root));
			final var b = computeAverageDistanceToALeaf(tree, eb.getOpposite(root));
			var na = 0.5 * (b - a + weight);
			if (na >= weight)
				na = 0.95 * weight;
			else if (na <= 0)
				na = 0.05 * weight;
			final var nb = weight - na;
			tree.setWeight(ea, na);
			tree.setWeight(eb, nb);
		}
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
	 * reroot a tree by outgroup. Find the node or edge middle point so that tree is optimally rooted for
	 * the given outgroup  labels
	 */
	public static void rerootByOutGroup(boolean internalNodeLabelsAreEdgeLabels, PhyloTree tree, BitSet outgroupTaxa) {
		if (tree.getRoot() == null)
			return;

		var totalOutgroupTaxa = 0;
		var totalIngroupNodes = 0;
		var totalNodes = tree.getNumberOfNodes();

		// compute number of outgroup taxa for each node
		NodeIntArray node2NumberOutgroup = new NodeIntArray(tree);
		for (var v : tree.nodes()) {
			if (tree.getNumberOfTaxa(v) > 0) {
				var isOutgroupNode = false;
				for (var t : tree.getTaxa(v)) {
					if (outgroupTaxa.get(t)) {
						isOutgroupNode = true;
						node2NumberOutgroup.set(v, node2NumberOutgroup.getInt(v) + 1);
						totalOutgroupTaxa++;
						break;
					}
				}
				if (!isOutgroupNode)
					totalIngroupNodes++;
			}
		}

		if (totalOutgroupTaxa == 0 || totalIngroupNodes == 0) {
			NotificationManager.showError("Internal error: no taxa found in rerootByOutGroup()");
			return;
		}

		var edge2OutgroupBelow = new EdgeIntArray(tree); // how many outgroup taxa below this edge?
		var edge2NodesBelow = new EdgeIntArray(tree);  // how many nodes below this edge?
		var node2OutgroupBelow = new NodeIntArray(tree); // how many outgroup taxa below this multifurcation?
		var node2NodesBelow = new NodeIntArray(tree);     // how many nodes below this multifurcation (including this?)

		rerootByOutgroupRec(tree.getRoot(), null, node2NumberOutgroup, edge2OutgroupBelow, edge2NodesBelow, node2OutgroupBelow, node2NodesBelow, totalNodes, totalOutgroupTaxa);

		// find best edge for rooting

		Edge bestEdge = null;
		var outgroupBelowBestEdge = 0;
		var nodesBelowBestEdge = 0;

		for (var e : tree.edges()) {
			var outgroupBelowE = edge2OutgroupBelow.getInt(e);
			var nodesBelowE = edge2NodesBelow.getInt(e);
			if (outgroupBelowE < 0.5 * totalOutgroupTaxa) {
				outgroupBelowE = totalOutgroupTaxa - outgroupBelowE;
				nodesBelowE = totalNodes - nodesBelowE;
			}
			if (bestEdge == null || outgroupBelowE > outgroupBelowBestEdge || (outgroupBelowE == outgroupBelowBestEdge && nodesBelowE < nodesBelowBestEdge)) {
				bestEdge = e;
				outgroupBelowBestEdge = outgroupBelowE;
				nodesBelowBestEdge = nodesBelowE;
			}
			//tree.setLabel(e,""+outgroupBelowE+" "+nodesBelowE);
		}

		// try to find better node for rooting:

		Node bestNode = null;
		var outgroupBelowBestNode = outgroupBelowBestEdge;
		var nodesBelowBestNode = nodesBelowBestEdge;

		for (var v : tree.nodes()) {
			var outgroupBelowV = node2OutgroupBelow.getInt(v);
			int nodesBelowV = node2NodesBelow.getInt(v);
			if (outgroupBelowV > 0 && (outgroupBelowV > outgroupBelowBestNode || (outgroupBelowV == outgroupBelowBestNode && nodesBelowV < nodesBelowBestNode))) {
				bestNode = v;
				outgroupBelowBestNode = outgroupBelowV;
				nodesBelowBestNode = nodesBelowV;
				// System.err.println("node score: "+outgroupBelowV+" "+nodesBelowV);
			}
		}
		if (bestNode != null && bestNode != tree.getRoot()) {
			rerootByNode(internalNodeLabelsAreEdgeLabels, tree, bestNode);
		} else if (bestEdge != null) {
			rerootByEdge(internalNodeLabelsAreEdgeLabels, tree, bestEdge);
		}
	}

	/**
	 * recursively determine the best place to root the tree for the given outgroup
	 */
	private static void rerootByOutgroupRec(Node v, Edge e, NodeIntArray node2NumberOutgroup, EdgeIntArray edge2OutgroupBelow,
											EdgeIntArray edge2NodesBelow, NodeIntArray node2OutgroupBelow, NodeIntArray node2NodesBelow, int totalNodes, int totalOutgroup) {
		var outgroupBelowE = node2NumberOutgroup.getInt(v);
		var nodesBelowE = 1; // including v

		for (var f : v.outEdges()) {
			rerootByOutgroupRec(f.getTarget(), f, node2NumberOutgroup, edge2OutgroupBelow, edge2NodesBelow, node2OutgroupBelow, node2NodesBelow, totalNodes, totalOutgroup);
			outgroupBelowE += edge2OutgroupBelow.getInt(f);
			nodesBelowE += edge2NodesBelow.getInt(f);
		}
		if (e != null) {
			edge2NodesBelow.set(e, nodesBelowE);
			edge2OutgroupBelow.set(e, outgroupBelowE);
		}

		// if v is a multifurcation then we may need to use it as root
		if (v.getOutDegree() > 2) // multifurcation
		{
			final var outgroupBelowV = outgroupBelowE + node2NumberOutgroup.getInt(v);

			if (outgroupBelowV == totalOutgroup) // all outgroup taxa lie below here
			{
				// count nodes below in straight-forward way
				node2OutgroupBelow.set(v, outgroupBelowV);

				var nodesBelowV = 1;
				for (var f : v.outEdges()) {
					if (edge2OutgroupBelow.getInt(f) > 0)
						nodesBelowV += edge2NodesBelow.getInt(f);
				}
				node2NodesBelow.set(v, nodesBelowV);
			} else // outgroupBelowE<totalOutgroup, i.e. some outgroup nodes lie above e
			{
				// count nodes below in parts not containing outgroup taxa and then subtract appropriately

				var keep = false;
				var nodesBelowV = 0;
				for (var f : v.outEdges()) {
					if (edge2OutgroupBelow.getInt(f) > 0)
						keep = true;   // need to have at least one node below that contains outgroup taxa
					else
						nodesBelowV += edge2NodesBelow.getInt(f);
				}
				if (keep) {
					node2OutgroupBelow.set(v, totalOutgroup);
					node2NodesBelow.set(v, totalNodes - nodesBelowV);
				}
			}
		}
	}

	/**
	 * re-root tree using midpoint rooting
	 */
	public static void rerootByMidpoint(boolean internalNodeLabelsAreEdgeLabels, PhyloTree tree) {
		final SortedSet<Triplet<Edge, Float, Float>> rankedMidpointRootings = getRankedMidpointRootings(tree);

		final var best = rankedMidpointRootings.first();
		final var e = best.getFirst();
		final var v = e.getSource();
		final var w = e.getTarget();
		final var a = best.getSecond();
		final var b = best.getThird();
		final var weight = (float) tree.getWeight(e);
		final var halfOfTotal = (a + b + weight) / 2;

		if (halfOfTotal <= a) {
			if (tree.getRoot() == v)
				return;
			rerootByNode(internalNodeLabelsAreEdgeLabels, tree, v);
		} else if (halfOfTotal >= a + weight) {
			if (tree.getRoot() == w)
				return;
			rerootByNode(internalNodeLabelsAreEdgeLabels, tree, w);
		} else {
			rerootByEdge(internalNodeLabelsAreEdgeLabels, tree, e);
		}
	}

	/**
	 * gets all mid-point rootings edges ranked by increasing level of imbalance (absolute difference of distances of
	 * source and target to furtherest leaf without through e)
	 *
	 * @return collection of triplets: edge,
	 */
	public static SortedSet<Triplet<Edge, Float, Float>> getRankedMidpointRootings(final PhyloTree tree) {
		final var maxBottomUpDistance = new EdgeFloatArray(tree);
		final var maxTopDownDistance = new EdgeFloatArray(tree);

		for (var e : tree.getRoot().outEdges())
			computeMaxBottomUpDistance(tree, e, maxBottomUpDistance);
		computeMaxTopDownDistanceRec(tree, tree.getRoot(), maxBottomUpDistance, maxTopDownDistance);

		var result = new TreeSet<Triplet<Edge, Float, Float>>((a, b) -> {
			float compare = Math.abs(a.getSecond() - a.getThird()) - Math.abs(b.getSecond() - b.getThird());
			if (compare < 0)
				return -1;
			else if (compare > 0)
				return 1;
			else if (a.getFirst().getId() < b.getFirst().getId())
				return -1;
			else if (a.getFirst().getId() > b.getFirst().getId())
				return 1;
			else
				return 0;
		});
		for (var e : tree.edges()) {
			Triplet<Edge, Float, Float> triplet = new Triplet<>(e, maxTopDownDistance.getFloat(e), maxBottomUpDistance.getFloat(e));
			result.add(triplet);
		}
		if (false) {
			System.err.println("Ranking:");
			for (var triplet : result) {
				System.err.println(triplet);
			}
		}
		return result;
	}

	/**
	 * compute the midpoint score for all edges
	 *
	 * @return midpoint scores
	 */
	public static EdgeFloatArray getMidpointScores(PhyloTree tree) {
		final var maxBottomUpDistance = new EdgeFloatArray(tree);
		final var maxTopDownDistance = new EdgeFloatArray(tree);
		for (var e : tree.getRoot().outEdges()) {
			computeMaxBottomUpDistance(tree, e, maxBottomUpDistance);
		}
		computeMaxTopDownDistanceRec(tree, tree.getRoot(), maxBottomUpDistance, maxTopDownDistance);

		final var scores = new EdgeFloatArray(tree);
		for (var e : tree.getRoot().outEdges()) {
			scores.put(e, Math.abs(maxBottomUpDistance.getFloat(e) - maxTopDownDistance.getFloat(e)));
		}
		return scores;
	}

	/**
	 * compute the midpoint score a given root node
	 *
	 * @return midpoint score
	 */
	public static float getMidpointScore(PhyloTree tree, Node root) {
		SortedSet<Double> distances = new TreeSet<>();

		for (var e : tree.getRoot().outEdges()) {
			distances.add(computeMaxDistanceRec(tree, e.getTarget(), e) + tree.getWeight(e));
		}
		var first = distances.last();
		distances.remove(distances.last());
		var second = distances.last();
		return (float) Math.abs(first - second);
	}

	/**
	 * compute the maximum distance from v to a leaf in a tree, avoiding edge f
	 *
	 * @return max distance
	 */
	private static float computeMaxDistanceRec(PhyloTree tree, Node v, Edge f) {
		var dist = 0.0f;
		for (var e : v.adjacentEdges()) {
			if (e != f) {
				dist = Math.max(dist, computeMaxDistanceRec(tree, e.getOpposite(v), e) + (float) tree.getWeight(e));
			}
		}
		return dist;
	}

	/**
	 * bottom up calculation of max down distance
	 *
	 * @return distance down (including length of e)
	 */
	private static float computeMaxBottomUpDistance(PhyloTree tree, Edge e, EdgeFloatArray maxDownDistance) {
		var w = e.getTarget();
		var depth = 0f;
		for (var f : w.outEdges()) {
			depth = Math.max(computeMaxBottomUpDistance(tree, f, maxDownDistance), depth);
		}
		maxDownDistance.put(e, depth);
		return depth + (float) tree.getWeight(e);
	}

	/**
	 * recursively compute best topdown distance
	 */
	private static void computeMaxTopDownDistanceRec(PhyloTree tree, Node v, EdgeFloatArray maxDownDistance, EdgeFloatArray maxUpDistance) {
		float bestUp;
		var inEdge = v.getFirstInEdge();
		if (inEdge != null)
			bestUp = maxUpDistance.getFloat(inEdge) + (float) tree.getWeight(inEdge);
		else
			bestUp = 0;

		for (var e : v.outEdges()) {
			float best = bestUp;
			for (var f : v.outEdges()) {
				if (f != e) {
					best = Math.max(best, maxDownDistance.getFloat(f) + (float) tree.getWeight(f));
				}
			}
			maxUpDistance.put(e, best);
		}
		for (var e : v.outEdges()) {
			computeMaxTopDownDistanceRec(tree, e.getTarget(), maxDownDistance, maxUpDistance);
		}
	}

	/**
	 * gets the average distance from this node to a leaf.
	 */
	public static double computeAverageDistanceToALeaf(PhyloTree tree, Node v) {
		// assumes that all edges are oriented away from the root
		var seen = new NodeSet(tree);
		var pair = new Pair<>(0.0, 0);
		computeAverageDistanceToLeafRec(tree, v, null, 0, seen, pair);
		var sum = pair.getFirst();
		var leaves = pair.getSecond();
		if (leaves > 0)
			return sum / leaves;
		else
			return 0;
	}

	/**
	 * recursively does the work
	 */
	private static void computeAverageDistanceToLeafRec(PhyloTree tree, Node v, Edge e, double distance, NodeSet seen, Pair<Double, Integer> pair) {
		if (!seen.contains(v)) {
			seen.add(v);
			if (v.getOutDegree() > 0) {
				for (Edge f : v.adjacentEdges()) {
					if (f != e)
						computeAverageDistanceToLeafRec(tree, f.getOpposite(v), f, distance + tree.getWeight(f), seen, pair);
				}
			} else {
				pair.setFirst(pair.getFirst() + distance);
				pair.setSecond(pair.getSecond() + 1);
			}
		}
	}
}
