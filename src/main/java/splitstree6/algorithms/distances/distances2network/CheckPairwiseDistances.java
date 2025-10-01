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
import jloda.graph.Node;
import jloda.graph.algorithms.Dijkstra;
import jloda.phylo.PhyloGraph;
import jloda.util.StringUtils;
import splitstree6.data.DistancesBlock;

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
		for (var i = 0; i < taxonNodePairs.size(); i++) {
			var a = taxonNodePairs.get(i);
			for (var j = i + 1; j < taxonNodePairs.size(); j++) {
				var b = taxonNodePairs.get(j);
				var inputDistance = D[a.getKey() - 1][b.getKey() - 1];
				var outputDistance = graphDistance(graph, a.getValue(), b.getValue());
				var diff = Math.abs(inputDistance - outputDistance);
				if (diff > epsilon) {
					System.err.println("'" + graph.getLabel(a.getValue()) + "' - '" + graph.getLabel(b.getValue()) + "': in=%s, out=%s, diff=%s".formatted(
							StringUtils.removeTrailingZerosAfterDot(inputDistance),
							StringUtils.removeTrailingZerosAfterDot(outputDistance),
							StringUtils.removeTrailingZerosAfterDot(diff)));
					differences++;
				}
			}
		}
		if (differences == 0)
			System.err.println("All path distances correct");
		else
			System.err.println("Incorrect path distances: " + differences);
		System.err.println("Total length: " + graph.edgeStream().mapToDouble(graph::getWeight).sum());
	}

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
