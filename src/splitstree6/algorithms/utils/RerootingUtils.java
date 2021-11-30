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
import java.util.Comparator;
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
		if ((e.getSource() == tree.getRoot() || e.getTarget() == tree.getRoot()) && tree.getRoot().getDegree() == 2)
			return; // no need to reroot

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

		Node root = tree.getRoot();

		if (root.getDegree() == 2 && tree.getLabel(root) == null) {
			final Edge ea = root.getFirstAdjacentEdge();
			final Edge eb = root.getLastAdjacentEdge();
			final double weight = tree.getWeight(ea) + tree.getWeight(eb);
			final double a = computeAverageDistanceToALeaf(tree, ea.getOpposite(root));
			final double b = computeAverageDistanceToALeaf(tree, eb.getOpposite(root));
			double na = 0.5 * (b - a + weight);
			if (na >= weight)
				na = 0.95 * weight;
			else if (na <= 0)
				na = 0.05 * weight;
			final double nb = weight - na;
			tree.setWeight(ea, na);
			tree.setWeight(eb, nb);
		}
	}

	/**
	 * reroot tree by node
	 *
	 * @return true, if rerooted
	 */
	public static void rerootByNode(boolean internalNodeLabelsAreEdgeLabels, PhyloTree tree, Node v) {
		if (v == tree.getRoot())
			return;

		final Node root = tree.getRoot();
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
	 *
	 * @param tree
	 * @param outgroupTaxa
	 */
	public static void rerootByOutGroup(boolean internalNodeLabelsAreEdgeLabels, PhyloTree tree, BitSet outgroupTaxa) {
		if (tree.getRoot() == null)
			return;

		int totalOutgroupTaxa = 0;
		int totalIngroupNodes = 0;
		int totalNodes = tree.getNumberOfNodes();

		// compute number of outgroup taxa for each node
		NodeIntArray node2NumberOutgroup = new NodeIntArray(tree);
		for (Node v = tree.getFirstNode(); v != null; v = v.getNext()) {
			if (tree.getNumberOfTaxa(v) > 0) {
				boolean isOutgroupNode = false;
				for (Integer t : tree.getTaxa(v)) {
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

		EdgeIntArray edge2OutgroupBelow = new EdgeIntArray(tree); // how many outgroup taxa below this edge?
		EdgeIntArray edge2NodesBelow = new EdgeIntArray(tree);  // how many nodes below this edge?
		NodeIntArray node2OutgroupBelow = new NodeIntArray(tree); // how many outgroup taxa below this multifurcation?
		NodeIntArray node2NodesBelow = new NodeIntArray(tree);     // how many nodes below this multifurcation (including this?)

		rerootByOutgroupRec(tree.getRoot(), null, node2NumberOutgroup, edge2OutgroupBelow, edge2NodesBelow, node2OutgroupBelow, node2NodesBelow, totalNodes, totalOutgroupTaxa);

		// find best edge for rooting

		Edge bestEdge = null;
		int outgroupBelowBestEdge = 0;
		int nodesBelowBestEdge = 0;

		for (Edge e : tree.edges()) {
			int outgroupBelowE = edge2OutgroupBelow.getInt(e);
			int nodesBelowE = edge2NodesBelow.getInt(e);
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
		int outgroupBelowBestNode = outgroupBelowBestEdge;
		int nodesBelowBestNode = nodesBelowBestEdge;

		for (Node v : tree.nodes()) {
			int outgroupBelowV = node2OutgroupBelow.getInt(v);
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
	 *
	 * @param v
	 * @param e
	 * @param node2NumberOutgroup
	 * @param edge2OutgroupBelow
	 * @param edge2NodesBelow
	 * @param node2OutgroupBelow
	 * @param node2NodesBelow
	 * @param totalNodes
	 * @param totalOutgroup
	 */
	private static void rerootByOutgroupRec(Node v, Edge e, NodeIntArray node2NumberOutgroup, EdgeIntArray edge2OutgroupBelow,
											EdgeIntArray edge2NodesBelow, NodeIntArray node2OutgroupBelow, NodeIntArray node2NodesBelow, int totalNodes, int totalOutgroup) {
		int outgroupBelowE = node2NumberOutgroup.getInt(v);
		int nodesBelowE = 1; // including v

		for (Edge f : v.outEdges()) {
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
			final int outgroupBelowV = outgroupBelowE + node2NumberOutgroup.getInt(v);

			if (outgroupBelowV == totalOutgroup) // all outgroup taxa lie below here
			{
				// count nodes below in straight-forward way
				node2OutgroupBelow.set(v, outgroupBelowV);

				int nodesBelowV = 1;
				for (Edge f = v.getFirstOutEdge(); f != null; f = v.getNextOutEdge(f)) {
					if (edge2OutgroupBelow.getInt(f) > 0)
						nodesBelowV += edge2NodesBelow.getInt(f);
				}
				node2NodesBelow.set(v, nodesBelowV);
			} else // outgroupBelowE<totalOutgroup, i.e. some outgroup nodes lie above e
			{
				// count nodes below in parts not containing outgroup taxa and then subtract appropriately

				boolean keep = false;
				int nodesBelowV = 0;
				for (Edge f = v.getFirstOutEdge(); f != null; f = v.getNextOutEdge(f)) {
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
	 *
	 * @param tree
	 * @return true, if tree rerooted
	 */
	public static void rerootByMidpoint(boolean internalNodeLabelsAreEdgeLabels, PhyloTree tree) {
		final SortedSet<Triplet<Edge, Float, Float>> rankedMidpointRootings = getRankedMidpointRootings(tree);

		final Triplet<Edge, Float, Float> best = rankedMidpointRootings.first();
		final Edge e = best.getFirst();
		final Node v = e.getSource();
		final Node w = e.getTarget();
		final float a = best.getSecond();
		final float b = best.getThird();
		final float weight = (float) tree.getWeight(e);
		final float halfOfTotal = (a + b + weight) / 2;

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
	 * source and target to furthest leaf without through e)
	 *
	 * @return collection of triplets: edge,
	 */
	public static SortedSet<Triplet<Edge, Float, Float>> getRankedMidpointRootings(final PhyloTree tree) {
		final EdgeFloatArray maxBottomUpDistance = new EdgeFloatArray(tree);
		final EdgeFloatArray maxTopDownDistance = new EdgeFloatArray(tree);

		for (Edge e : tree.getRoot().outEdges())
			computeMaxBottomUpDistance(tree, e, maxBottomUpDistance);
		computeMaxTopDownDistanceRec(tree, tree.getRoot(), maxBottomUpDistance, maxTopDownDistance);

		SortedSet<Triplet<Edge, Float, Float>> result = new TreeSet<Triplet<Edge, Float, Float>>(new Comparator<Triplet<Edge, Float, Float>>() {
			public int compare(Triplet<Edge, Float, Float> a, Triplet<Edge, Float, Float> b) {
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
			}
		});
		for (Edge e : tree.edges()) {
			Triplet<Edge, Float, Float> triplet = new Triplet<Edge, Float, Float>(e, maxTopDownDistance.getFloat(e), maxBottomUpDistance.getFloat(e));
			result.add(triplet);
		}
		if (false) {
			System.err.println("Ranking:");
			for (Triplet<Edge, Float, Float> triplet : result) {
				System.err.println(triplet);
			}
		}
		return result;
	}

	/**
	 * compute the midpoint score for all edges
	 *
	 * @param tree
	 * @return midpoint scores
	 */
	public static EdgeFloatArray getMidpointScores(PhyloTree tree) {
		final EdgeFloatArray maxBottomUpDistance = new EdgeFloatArray(tree);
		final EdgeFloatArray maxTopDownDistance = new EdgeFloatArray(tree);
		for (Edge e = tree.getRoot().getFirstOutEdge(); e != null; e = tree.getRoot().getNextOutEdge(e))
			computeMaxBottomUpDistance(tree, e, maxBottomUpDistance);
		computeMaxTopDownDistanceRec(tree, tree.getRoot(), maxBottomUpDistance, maxTopDownDistance);

		final EdgeFloatArray scores = new EdgeFloatArray(tree);
		for (Edge e = tree.getRoot().getFirstOutEdge(); e != null; e = tree.getRoot().getNextOutEdge(e)) {
			scores.put(e, Math.abs(maxBottomUpDistance.getFloat(e) - maxTopDownDistance.getFloat(e)));
		}
		return scores;
	}

	/**
	 * compute the midpoint score a given root node
	 *
	 * @param tree
	 * @param root
	 * @return midpoint score
	 */
	public static float getMidpointScore(PhyloTree tree, Node root) {
		SortedSet<Double> distances = new TreeSet<>();

		for (Edge e = root.getFirstOutEdge(); e != null; e = root.getNextOutEdge(e)) {
			distances.add(computeMaxDistanceRec(tree, e.getTarget(), e) + tree.getWeight(e));
		}
		double first = distances.last();
		distances.remove(distances.last());
		double second = distances.last();
		return (float) Math.abs(first - second);
	}

	/**
	 * compute the maximum distance from v to a leaf in a tree, avoiding edge f
	 *
	 * @param tree
	 * @param v
	 * @param f
	 * @return max distance
	 */
	private static float computeMaxDistanceRec(PhyloTree tree, Node v, Edge f) {
		float dist = 0;
		for (Edge e = v.getFirstAdjacentEdge(); e != null; e = v.getNextAdjacentEdge(e)) {
			if (e != f) {
				dist = Math.max(dist, computeMaxDistanceRec(tree, e.getOpposite(v), e) + (float) tree.getWeight(e));
			}
		}
		return dist;
	}


	/**
	 * bottom up calculation of max down distance
	 *
	 * @param tree
	 * @param e
	 * @param maxDownDistance
	 * @return distance down (including length of e)
	 */
	private static float computeMaxBottomUpDistance(PhyloTree tree, Edge e, EdgeFloatArray maxDownDistance) {
		Node w = e.getTarget();
		float depth = 0;
		for (Edge f = w.getFirstOutEdge(); f != null; f = w.getNextOutEdge(f)) {
			depth = Math.max(computeMaxBottomUpDistance(tree, f, maxDownDistance), depth);
		}
		maxDownDistance.put(e, depth);
		return depth + (float) tree.getWeight(e);
	}

	/**
	 * recursively compute best topdown distance
	 *
	 * @param tree
	 * @param v
	 * @param maxDownDistance
	 * @param maxUpDistance
	 */
	private static void computeMaxTopDownDistanceRec(PhyloTree tree, Node v, EdgeFloatArray maxDownDistance, EdgeFloatArray maxUpDistance) {
		float bestUp;
		Edge inEdge = v.getFirstInEdge();
		if (inEdge != null)
			bestUp = maxUpDistance.getFloat(inEdge) + (float) tree.getWeight(inEdge);
		else
			bestUp = 0;

		for (Edge e = v.getFirstOutEdge(); e != null; e = v.getNextOutEdge(e)) {
			float best = bestUp;
			for (Edge f = v.getFirstOutEdge(); f != null; f = v.getNextOutEdge(f)) {
				if (f != e) {
					best = Math.max(best, maxDownDistance.getFloat(f) + (float) tree.getWeight(f));
				}
			}
			maxUpDistance.put(e, best);
		}
		for (Edge e = v.getFirstOutEdge(); e != null; e = v.getNextOutEdge(e)) {
			computeMaxTopDownDistanceRec(tree, e.getTarget(), maxDownDistance, maxUpDistance);
		}
	}

	/**
	 * gets the average distance from this node to a leaf.
	 *
	 * @param v
	 * @return average distance to a leaf
	 */
	public static double computeAverageDistanceToALeaf(PhyloTree tree, Node v) {
		// assumes that all edges are oriented away from the root
		NodeSet seen = new NodeSet(tree);
		Pair<Double, Integer> pair = new Pair<>(0.0, 0);
		computeAverageDistanceToLeafRec(tree, v, null, 0, seen, pair);
		double sum = pair.getFirst();
		int leaves = pair.getSecond();
		if (leaves > 0)
			return sum / leaves;
		else
			return 0;
	}

	/**
	 * recursively does the work
	 *
	 * @param v
	 * @param distance from root
	 * @param seen
	 * @param pair
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
