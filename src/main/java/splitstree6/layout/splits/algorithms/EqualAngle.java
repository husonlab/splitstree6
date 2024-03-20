/*
 *  EqualAngle.java Copyright (C) 2024 Daniel H. Huson
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

package splitstree6.layout.splits.algorithms;

import javafx.geometry.Point2D;
import jloda.fx.util.GeometryUtilsFX;
import jloda.graph.*;
import jloda.phylo.PhyloSplitsGraph;
import jloda.util.CanceledException;
import jloda.util.Pair;
import jloda.util.progress.ProgressListener;
import splitstree6.algorithms.utils.SplitsBlockUtilities;
import splitstree6.data.SplitsBlock;
import splitstree6.data.TaxaBlock;
import splitstree6.splits.GraphUtils;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.TreeSet;

/**
 * the equals angle algorithm for computing a split network for circular splits
 * Daniel Huson, 11.2017
 */
public class EqualAngle {
	/**
	 * update the algorithm to build a new graph
	 */
	public static boolean apply(ProgressListener progress, boolean useWeights, TaxaBlock taxaBlock, SplitsBlock splits, PhyloSplitsGraph graph, BitSet forbiddenSplits, BitSet usedSplits) throws CanceledException {
		//System.err.println("Running equals angle algorithm");
		graph.clear();
		usedSplits.clear();

		progress.setTasks("Computing Splits Network", "Equal Angle algorithm");
		progress.setMaximum(100);    //initialize maximum progress
		progress.setProgress(-1);    //set progress to 0

		final int[] cycle = normalizeCycle(splits.getCycle());

		progress.setProgress(2);

		initGraph(taxaBlock, splits, cycle, graph);

		final var interiorSplits = getNonTrivialSplitsOrdered(splits);

		progress.setMaximum(interiorSplits.size());    //initialize maximum progress

		var allUsed = true;
		{
			var count = 0;
			for (var s : interiorSplits) {
				if (SplitsBlockUtilities.isCircular(taxaBlock, cycle, splits.get(s))) {
					wrapSplit(taxaBlock, splits, s, cycle, graph);
					usedSplits.set(s, true);
					progress.setProgress(++count);
				} else
					allUsed = false;
			}
		}

		progress.setProgress(-1);
		removeTemporaryTrivialEdges(graph);

		assignAnglesToEdges(taxaBlock.getNtax(), splits, cycle, graph, forbiddenSplits, 360);

		progress.setProgress(90);

		// rotateAbout so that edge leaving first taxon ist pointing at 9 o'clock
		if (graph.getNumberOfNodes() > 0 && graph.getNumberOfEdges() > 0) {
			var v = graph.getTaxon2Node(1);
			var angle = GeometryUtilsFX.modulo360(180 + graph.getAngle(v.getFirstAdjacentEdge())); // add 180 to be consist with Embed
			for (var e : graph.edges()) {
				graph.setAngle(e, GeometryUtilsFX.modulo360(graph.getAngle(e) - angle));
			}
		}

		GraphUtils.addLabels(taxaBlock::getLabel, graph);
		progress.setProgress(100);   //set progress to 100%

		if (false) {
			for (var v : graph.nodes()) {
				if (graph.getLabel(v) != null)
					System.err.println("Node " + v.getId() + " " + graph.getLabel(v));
			}
			for (var e : graph.edges()) {
				System.err.println("Edge " + e.getSource().getId() + " - " + e.getTarget().getId() + " split: " + graph.getSplit(e));
			}
		}
		progress.reportTaskCompleted();

		return allUsed;
	}


	/**
	 * initializes the graph
	 */
	private static void initGraph(TaxaBlock taxa, SplitsBlock splits, int[] posOfTaxonInCycle, PhyloSplitsGraph graph) {
		graph.clear();

		// map from each taxon to it's trivial split in splits
		final var taxon2TrivialSplit = new int[taxa.getNtax() + 1];

		for (var s = 1; s <= splits.getNsplits(); s++) {
			final var split = splits.get(s);
			if (split.size() == 1) {
				final var t = split.getSmallerPart().nextSetBit(1);
				taxon2TrivialSplit[t] = s;
			}
		}

		final Node center = graph.newNode();
		for (var i = 1; i <= taxa.getNtax(); i++) {
			var t = posOfTaxonInCycle[i];

			var v = graph.newNode();

			graph.addTaxon(v, t);

			var e = graph.newEdge(center, v);
			if (taxon2TrivialSplit[t] != 0) {
				int s = taxon2TrivialSplit[t];
				graph.setWeight(e, splits.get(s).getWeight());
				graph.setSplit(e, s);
			} else
				graph.setSplit(e, -1); // mark as temporary split
		}
	}

	/**
	 * returns the list of all non-trivial splits, ordered by by increasing size
	 * of the split part containing taxon 1
	 *
	 * @return non-trivial splits
	 */
	private static ArrayList<Integer> getNonTrivialSplitsOrdered(SplitsBlock splits) {
		final var interiorSplits = new TreeSet<Pair<Integer, Integer>>(new Pair<>()); // first component is size, second is id

		for (var s = 1; s <= splits.getNsplits(); s++) {
			final var split = splits.get(s);
			if (split.size() > 1) {
				interiorSplits.add(new Pair<>(split.getPartContaining(1).cardinality(), s));
			}
		}
		final var interiorSplitIDs = new ArrayList<Integer>(interiorSplits.size());
		for (var interiorSplit : interiorSplits) {
			interiorSplitIDs.add(interiorSplit.getSecond());
		}
		return interiorSplitIDs;
	}

	/**
	 * normalizes cycle so that cycle[1]=1
	 *
	 * @return normalized cycle
	 */
	private static int[] normalizeCycle(int[] cycle) {
		var result = new int[cycle.length];

		var i = 1;
		while (cycle[i] != 1 && i < cycle.length - 1)
			i++;
		var j = 1;
		while (i < cycle.length) {
			result[j] = cycle[i];
			i++;
			j++;
		}
		i = 1;
		while (j < result.length) {
			result[j] = cycle[i];
			i++;
			j++;
		}
		return result;
	}

	/**
	 * adds an interior split using the wrapping algorithm
	 */
	private static void wrapSplit(TaxaBlock taxa, SplitsBlock splits, int s, int[] cycle, PhyloSplitsGraph graph) throws IllegalStateException {
		final var part = splits.get(s).getPartNotContaining(1);

		var xp = 0; // first member of split part not containing taxon 1
		var xq = 0; // last member of split part not containing taxon 1
		for (var i = 1; i <= taxa.getNtax(); i++) {
			var t = cycle[i];
			if (part.get(t)) {
				if (xp == 0)
					xp = t;
				xq = t;
			}
		}
		final var vp = graph.getTaxon2Node(xp);
		final var innerP = vp.getFirstAdjacentEdge().getOpposite(vp);
		final var vq = graph.getTaxon2Node(xq);
		final var innerQ = vq.getFirstAdjacentEdge().getOpposite(vq);
		final var targetLeafEdge = vq.getFirstAdjacentEdge();

		var e = vp.getFirstAdjacentEdge();
		var v = graph.getOpposite(vp, e);  // node on existing boundary path

		final var leafEdges = new ArrayList<Edge>(taxa.getNtax());
		leafEdges.add(e);

		final var nodesVisited = new NodeSet(graph);

		Node prevU = null; // previous node on newly created boundary path from vp.opposite to vq.opposite
		Edge nextE;
		do {
			if (nodesVisited.contains(v)) {
				System.err.println(graph);
				throw new IllegalStateException("Node already visited: " + v);
			}
			nodesVisited.add(v);

			final var f0 = e; // f0 is edge by which we enter the node
			{
				var f = v.getNextAdjacentEdgeCyclic(f0);
				while (isLeafEdge(f)) {
					leafEdges.add(f);
					if (f == targetLeafEdge) {
						break;
					}
					if (f == f0)
						throw new RuntimeException("Node wraparound: f=" + f + " f0=" + f0);
					f = v.getNextAdjacentEdgeCyclic(f);

				}
				if (f == targetLeafEdge)
					nextE = null; // at end of chain
				else
					nextE = f; // continue along boundary
			}

			final var u = graph.newNode(); // new node on new path
			{
				final var f = graph.newEdge(u, null, v, f0, Edge.AFTER, Edge.AFTER, null); // edge from new node on new path to old node on existing path
				// here we make sure that new edge is inserted after f0
				graph.setSplit(f, s);
				graph.setWeight(f, splits.get(s).getWeight());
			}

			if (prevU != null) {
				final var f = graph.newEdge(u, prevU, null); // edge from current node to previous node on new path
				graph.setSplit(f, graph.getSplit(e));
				graph.setWeight(f, graph.getWeight(e));
			}
			for (var f : leafEdges) { // copy leaf edges over to new path
				final var fCopy = graph.newEdge(u, graph.getOpposite(v, f));
				graph.setSplit(fCopy, graph.getSplit(f));
				graph.setWeight(fCopy, graph.getWeight(f));
				graph.deleteEdge(f);
			}
			leafEdges.clear();

			if (nextE != null) {
				v = graph.getOpposite(v, nextE);
				e = nextE;
				prevU = u;
			}
		} while (nextE != null);
	}

	/**
	 * does this edge lead to a leaf?
	 *
	 * @return is leaf edge
	 */
	private static boolean isLeafEdge(Edge f) {
		return f.getSource().getDegree() == 1 || f.getTarget().getDegree() == 1;
	}

	/**
	 * this removes all temporary trivial edges added to the graph
	 */
	private static void removeTemporaryTrivialEdges(PhyloSplitsGraph graph) {
		final var tempEdges = new EdgeSet(graph);
		for (var e : graph.edges()) {
			if (graph.getSplit(e) == -1) // temporary leaf edge
				tempEdges.add(e);
		}

		for (var e : tempEdges) {
			Node v, w;
			if (e.getSource().getDegree() == 1) {
				v = e.getSource();
				w = e.getTarget();
			} else {
				w = e.getSource();
				v = e.getTarget();
			}
			for (var t : graph.getTaxa(v)) {
				graph.addTaxon(w, t);
			}
			graph.clearTaxa(v);
			graph.deleteNode(v);
		}
	}

	/**
	 * assigns angles to all edges in the graph
	 *
	 * @param forbiddenSplits : set of all the splits such as their edges won't have their angles changed
	 */
	public static void assignAnglesToEdges(int ntaxa, SplitsBlock splits, int[] cycle, PhyloSplitsGraph graph, BitSet forbiddenSplits, double totalAngle) {
		//We create the list of angles representing the positions on a circle.
		var angles = assignAnglesToSplits(ntaxa, splits, cycle, totalAngle);

		for (var e : graph.edges()) {
			if (!forbiddenSplits.get(graph.getSplit(e))) {
				graph.setAngle(e, angles[graph.getSplit(e)]);
			}
		}
	}

	/**
	 * assigns angles to all edges in the graph
	 */
	public static double[] assignAnglesToSplits(int ntaxa, SplitsBlock splits, int[] cycle, double totalAngle) {
		//We create the list of angles representing the positions on a circle.

		//We create the list of angles representing the taxas on a circle.
		var angles = new double[ntaxa + 1];

		for (var t = 1; t <= ntaxa; t++) {
			angles[t] = (totalAngle * (t - 1) / (double) ntaxa) + 270 - 0.5 * totalAngle;
		}

		var split2angle = new double[splits.getNsplits() + 1];

		assignAnglesToSplits(ntaxa, angles, split2angle, splits, cycle);
		return split2angle;
	}


	/**
	 * assigns angles to the splits in the graph, considering that they are located exactly "in the middle" of two taxa
	 * so we fill split2angle using TaxaAngles.
	 *
	 * @param angles      for each taxa, its angle
	 * @param split2angle for each split, its angle
	 */
	private static void assignAnglesToSplits(int ntaxa, double[] angles, double[] split2angle, SplitsBlock splits, int[] cycle) {
		for (var s = 1; s <= splits.getNsplits(); s++) {
			var xp = 0; // first position of split part not containing taxon cycle[1]
			var xq = 0; // last position of split part not containing taxon cycle[1]
			final var part = splits.get(s).getPartNotContaining(cycle[1]);
			for (var i = 2; i <= ntaxa; i++) {
				var t = cycle[i];
				if (part.get(t)) {
					if (xp == 0)
						xp = i;
					xq = i;
				}
			}

			split2angle[s] = GeometryUtilsFX.modulo360(0.5 * (angles[xp] + angles[xq]));

			//System.out.println("split from "+xp+","+xpneighbour+" ("+TaxaAngleP+") to "+xq+","+xqneighbour+" ("+TaxaAngleQ+") -> "+split2angle[s]+" $ "+(180 * (xp + xq)) / (double) ntaxa);s
		}
	}


	/**
	 * assigns coordinates to nodes
	 */
	public static void assignCoordinatesToNodes(boolean useWeights, PhyloSplitsGraph graph, NodeArray<Point2D> node2point, int startTaxonId, int rootSplit) {
		if (graph.getNumberOfNodes() == 0)
			return;
		final var v = graph.getTaxon2Node(startTaxonId);
		node2point.put(v, new Point2D(0, 0));

		final var splitsInPath = new BitSet();
		NodeSet nodesVisited = new NodeSet(graph);

		assignCoordinatesToNodesRec(useWeights, v, splitsInPath, nodesVisited, graph, node2point, rootSplit);
	}

	/**
	 * recursively assigns coordinates to all nodes
	 */
	private static void assignCoordinatesToNodesRec(boolean useWeights, Node v, BitSet splitsInPath, NodeSet nodesVisited, PhyloSplitsGraph graph, NodeArray<Point2D> node2point, int rootSplit) {
		if (!nodesVisited.contains(v)) {
			nodesVisited.add(v);
			for (var e : v.adjacentEdges()) {
				var s = graph.getSplit(e);
				if (!splitsInPath.get(s)) {
					var w = graph.getOpposite(v, e);
					var weight = (useWeights ? graph.getWeight(e) : graph.getSplit(e) == rootSplit ? 0.1 : 1);
					var p = GeometryUtilsFX.translateByAngle(node2point.get(v), graph.getAngle(e), weight);
					node2point.put(w, p);
					splitsInPath.set(s, true);
					assignCoordinatesToNodesRec(useWeights, w, splitsInPath, nodesVisited, graph, node2point, rootSplit);
					splitsInPath.set(s, false);
				}
			}
		}
	}

	public static String getCitation() {
		return "Dress & Huson 2004;AWM Dress and DH. Huson, Constructing splits graphs, " +
			   "IEEE/ACM Transactions on Computational Biology and Bioinformatics 1(3):109-115, 2004.";
	}
}
