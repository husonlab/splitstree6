/*
 * NetworkSequencesAnalyzer.java Copyright (C) 2026 Daniel H. Huson
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

package splitstree6.view.network;

import jloda.graph.Edge;
import jloda.graph.Node;
import jloda.graph.algorithms.Dijkstra;
import jloda.util.StringUtils;
import splitstree6.data.DistancesBlock;
import splitstree6.data.NetworkBlock;
import splitstree6.data.TaxaBlock;

import java.util.HashMap;

public record NetworkDistancesAnalyzer() {

	public double inputPairwiseDistances(NetworkBlock networkBlock) {
		var distancesBlock = findDistancesBlock(networkBlock);
		var sum = 0.0;
		for (var s = 1; s <= distancesBlock.getNtax(); s++) {
			for (var t = s + 1; t <= distancesBlock.getNtax(); t++) {
				sum += distancesBlock.get(s, t);
			}
		}
		return 2 * sum;
	}

	public double totalEdgeDistances(NetworkBlock networkBlock) {
		if (!isApplicable(networkBlock))
			throw new IllegalArgumentException("Distances required");
		var sum = 0.0;
		var graph = networkBlock.getGraph();
		for (var e : graph.edges()) {
			sum += graph.getWeight(e);
		}
		return sum;
	}

	public double realizedPairwiseDistances(NetworkBlock networkBlock) {
		if (!isApplicable(networkBlock))
			throw new IllegalArgumentException("Distances required");

		var graph = networkBlock.getGraph();
		var weights = new HashMap<Edge, Double>();
		for (var e : graph.edges()) {
			weights.put(e, graph.getWeight(e));
		}

		var sum = 0.0;
		for (var v : graph.nodes()) {
			if (graph.hasTaxa(v)) {
				for (var w : graph.nodes(v)) {
					if (graph.hasTaxa(w)) {
						var shortestPath = Dijkstra.compute(graph, v, w, weights::get, true);
						Node prev = null;
						var pathLength = 0.0; // double: edge weights are fractional (half-integer Steiner edges), an int accumulator truncates each step and undershoots
						for (var q : shortestPath) {
							if (prev != null) {
								var e = q.getCommonEdge(prev);
								pathLength += weights.get(e);
							}
							prev = q;
						}
						sum += 2 * pathLength; // from v to w and from w to v
					}
				}
			}
		}
		return sum;
	}

	/**
	 * Multiplicative distortion of the realization: max_{i&lt;j} r_ij / min_{i&lt;j} r_ij, where r_ij = d_G(i,j)/d(i,j)
	 * with d_G the shortest-path (graph) distance and d the input distance. Equals 1 for an exact realization
	 * (indeed for any scalar multiple of it); larger values mean more distortion.
	 */
	public double distortion(NetworkBlock networkBlock) {
		if (!isApplicable(networkBlock))
			throw new IllegalArgumentException("Distances required");
		var distancesBlock = findDistancesBlock(networkBlock);
		var graph = networkBlock.getGraph();
		var weights = new HashMap<Edge, Double>();
		for (var e : graph.edges())
			weights.put(e, graph.getWeight(e));

		var maxRatio = 0.0;
		var minRatio = Double.MAX_VALUE;
		for (var v : graph.nodes()) {
			if (graph.hasTaxa(v)) {
				for (var w : graph.nodes(v)) {
					if (w != v && graph.hasTaxa(w)) {
						var input = distancesBlock.get(graph.getTaxon(v), graph.getTaxon(w));
						if (input <= 0)
							continue;
						var shortestPath = Dijkstra.compute(graph, v, w, weights::get, true);
						Node prev = null;
						var pathLength = 0.0;
						for (var q : shortestPath) {
							if (prev != null)
								pathLength += weights.get(q.getCommonEdge(prev));
							prev = q;
						}
						if (pathLength > 0) {
							var r = pathLength / input;
							maxRatio = Math.max(maxRatio, r);
							minRatio = Math.min(minRatio, r);
						}
					}
				}
			}
		}
		return (minRatio <= maxRatio) ? maxRatio / minRatio : 1.0; // 1.0 when there is no comparable pair
	}

	public double reportDifferentDistances(int s, int t, TaxaBlock taxaBlock, NetworkBlock networkBlock) {
		var diff = 0.0;
		var distancesBlock = findDistancesBlock(networkBlock);
		var inputDistance = distancesBlock.get(s, t);
		var pathDistance = 0.0;
		var graph = networkBlock.getGraph();

		var v = graph.nodeStream().filter(u -> graph.getTaxon(u) == s).findAny().orElse(null);
		var w = graph.nodeStream().filter(u -> graph.getTaxon(u) == t).findAny().orElse(null);
		if (v != null && w != null) {
			var shortestPath = Dijkstra.compute(graph, v, w, graph::getWeight, true);
			Node prev = null;
			for (var q : shortestPath) {
				if (prev != null) {
					var e = q.getCommonEdge(prev);
					pathDistance += graph.getWeight(e);
				}
				prev = q;
			}
			System.err.printf("Input distance %s - %s: %s%n", taxaBlock.getLabel(s), taxaBlock.getLabel(t), StringUtils.trim(inputDistance));
			System.err.printf("Path distance  %s - %s: %s%n", taxaBlock.getLabel(s), taxaBlock.getLabel(t), StringUtils.trim(pathDistance));

			diff = pathDistance - inputDistance;
			if (diff > 0) {
				System.err.println("Path distance larger:  " + StringUtils.trim(pathDistance) + " > " + StringUtils.trim(inputDistance));
			} else if (diff < 0) {
				System.err.println("Path distance smaller: " + StringUtils.trim(pathDistance) + " < " + StringUtils.trim(inputDistance));
			}
		}
		return diff;
	}

	public static DistancesBlock findDistancesBlock(NetworkBlock networkBlock) {
		var distancesBlock = findAncestorDistancesBlock(networkBlock);
		if (distancesBlock == null)
			throw new IllegalArgumentException("Distances required");
		return distancesBlock;
	}

	public static boolean isApplicable(NetworkBlock networkBlock) {
		return findAncestorDistancesBlock(networkBlock) != null;
	}

	/**
	 * Walk up the workflow to the nearest ancestor {@link DistancesBlock}, stepping through any intermediate
	 * network-to-network algorithms (e.g. the Stretch Filter) so that a filtered network still reports its length
	 * and distortion against the original input distances. Each step goes DataNode -&gt; its producing
	 * AlgorithmNode -&gt; that algorithm's source DataNode. Returns null if there is no DistancesBlock ancestor.
	 */
	private static DistancesBlock findAncestorDistancesBlock(NetworkBlock networkBlock) {
		var dataNode = networkBlock.getNode();
		for (var i = 0; dataNode != null && i < 100; i++) {
			var algorithmNode = dataNode.getPreferredParent();
			if (algorithmNode == null)
				return null;
			var sourceNode = algorithmNode.getPreferredParent();
			if (sourceNode == null)
				return null;
			if (sourceNode.getDataBlock() instanceof DistancesBlock distancesBlock)
				return distancesBlock;
			dataNode = sourceNode;
		}
		return null;
	}
}

