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
		if (!isApplicable(networkBlock))
			throw new IllegalArgumentException("Distances required");

		var sum = 0.0;
		if (networkBlock.getNode().getPreferredParent().getPreferredParent().getDataBlock() instanceof DistancesBlock distancesBlock) {
			var graph = networkBlock.getGraph();
			for (var s = 1; s <= distancesBlock.getNtax(); s++) {
				for (var t = s + 1; t <= distancesBlock.getNtax(); t++) {
					sum += distancesBlock.get(s, t);
				}
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
						var pathLength = 0;
						for (var q : shortestPath) {
							if (prev != null) {
								var e = q.getCommonEdge(prev);
								pathLength += weights.get(e);
							}
							prev = q;
						}
						sum += 2 * pathLength; // from v to w and from w to v
						System.err.println(graph.getLabel(v) + "-" + graph.getLabel(w) + ": " + pathLength);
					}
				}
			}
		}
		return sum;
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
		if (networkBlock.getNode().getPreferredParent().getPreferredParent().getDataBlock() instanceof DistancesBlock distancesBlock) {
			return distancesBlock;
		}
		throw new IllegalArgumentException("Distances required");
	}

	public static boolean isApplicable(NetworkBlock networkBlock) {
		return networkBlock.getNode().getPreferredParent().getPreferredParent().getDataBlock() instanceof DistancesBlock;
	}
}

