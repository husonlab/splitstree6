/*
 * CheckPairwiseDistances.java Copyright (C) 2025 Daniel H. Huson
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
 *
 */

package splitstree6.algorithms.distances.distances2network;

import javafx.util.Pair;
import jloda.fx.control.RichTextLabel;
import jloda.graph.Node;
import jloda.graph.algorithms.Dijkstra;
import jloda.phylo.PhyloGraph;
import jloda.util.StringUtils;
import splitstree6.data.DistancesBlock;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.PriorityQueue;

/**
 * check all pairwise distances
 * Daniel Huson, 9.2025
 */
public class CheckPairwiseDistances {
	public static void apply(PhyloGraph graph, DistancesBlock distancesBlock, double epsilon) {
		apply(graph, distancesBlock.getDistances(), epsilon);
	}

	public static void apply(PhyloGraph graph, double[][] D, double epsilon) {
		System.err.println("Checking pairwise distances on graph (" + graph.getNumberOfNodes() + " nodes, " + graph.getNumberOfEdges() + " edges):");
		var taxonNodePairs = graph.nodeStream().filter(graph::hasTaxa)
				.map(v -> new Pair<>(graph.getTaxon(v), v)).toList();
		var differences = 0;
		var totalDifference = 0.0;
		var totalInput = 0.0;
		var totalOutput = 0.0;
		for (var i = 0; i < taxonNodePairs.size(); i++) {
			var a = taxonNodePairs.get(i);
			// ONE shortest-path solve per row, not one per pair: see graphDistances
			var distanceFromA = graphDistances(graph, a.getValue());
			for (var j = i + 1; j < taxonNodePairs.size(); j++) {
				var b = taxonNodePairs.get(j);
				var inputDistance = D[a.getKey() - 1][b.getKey() - 1];
				totalInput += inputDistance;
				var outputDistance = distanceFromA.get(b.getValue());
				if (Double.isInfinite(outputDistance))
					throw new RuntimeException("No path from source to sink");
				totalOutput += outputDistance;
				var diff = Math.abs(inputDistance - outputDistance);
				if (diff > epsilon) {
					totalDifference += diff;
					if (false) {
						System.err.println("'" + RichTextLabel.getRawText(graph.getLabel(a.getValue())) + "' - '" + RichTextLabel.getRawText(graph.getLabel(b.getValue())) + "': in=%s, out=%s, diff=%s".formatted(
								StringUtils.trim(inputDistance),
								StringUtils.trim(outputDistance),
								StringUtils.trim(diff)));
					}
					differences++;
				}
			}
		}

		System.err.printf("Total input length:  %s output length: %s %n", StringUtils.trim("%.8f", totalInput), StringUtils.trim("%.8f", totalOutput));
		if (differences == 0)
			System.err.println("All path distances correct");
		else {
			System.err.printf("Path distances incorrect, difference: %s%%%n", StringUtils.trim("%.1f", 100 * (totalDifference / totalInput)));
			// System.err.println("Incorrect path distances: " + differences);
		}
		System.err.printf("Total network length: %s%n", StringUtils.trim("%.8f", graph.edgeStream().mapToDouble(graph::getWeight).sum()));
	}

	/**
	 * Shortest-path distance from {@code source} to every node of the graph, computed in one pass.
	 * <p>
	 * {@link Dijkstra#compute} is itself a single-source algorithm: it relaxes the whole graph and then keeps
	 * only the path to the one sink it was asked about. Calling it once per pair therefore repeats the entire
	 * solve and discards all but one of its answers, turning an O(n) job into an O(n^2) one. Over the taxon
	 * pairs of the 517-taxon HIV reference alignment that is 133,386 solves where 517 suffice, which is the
	 * difference between this check taking minutes and taking seconds.
	 * <p>
	 * Edges are traversed undirected, matching {@code Dijkstra.compute(..., undirected = true)} as used here.
	 *
	 * @return the distance to every node; {@code Double.POSITIVE_INFINITY} for nodes the source cannot reach
	 */
	public static Map<Node, Double> graphDistances(PhyloGraph graph, Node source) {
		var distance = new HashMap<Node, Double>();
		for (var v : graph.nodes())
			distance.put(v, Double.POSITIVE_INFINITY);
		distance.put(source, 0.0);

		var queue = new PriorityQueue<NodeDistance>(Comparator.comparingDouble(NodeDistance::distance));
		queue.add(new NodeDistance(source, 0.0));
		var settled = new HashSet<Node>();

		while (!queue.isEmpty()) {
			var top = queue.poll();
			if (!settled.add(top.node()))
				continue;   // a stale queue entry, already settled at a shorter distance
			for (var e : top.node().adjacentEdges()) {
				var weight = graph.getWeight(e);
				if (weight < 0)
					throw new IllegalArgumentException("Dijkstra requires non-negative weights");
				var v = e.getOpposite(top.node());
				var candidate = top.distance() + weight;
				if (candidate < distance.get(v)) {
					distance.put(v, candidate);
					queue.add(new NodeDistance(v, candidate));
				}
			}
		}
		return distance;
	}

	private record NodeDistance(Node node, double distance) {
	}

	/**
	 * Distance between a single pair. Prefer {@link #graphDistances} whenever more than one pair from the same
	 * source is wanted -- this recomputes the whole single-source solve every time it is called.
	 */
	public static double graphDistance(PhyloGraph graph, Node v, Node w) {
		var list = Dijkstra.compute(graph, v, w, graph::getWeight, true);
		var sum = 0.0;
		for (var i = 1; i < list.size(); i++) {
			var a = list.get(i - 1);
			var b = list.get(i);
			var e = a.getCommonEdge(b);
			sum += graph.getWeight(e);
		}
		return sum;
	}
}
