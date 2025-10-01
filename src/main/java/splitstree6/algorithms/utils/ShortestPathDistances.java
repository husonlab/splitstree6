/*
 * ShortestPathDistances.java Copyright (C) 2025 Daniel H. Huson
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

package splitstree6.algorithms.utils;

import jloda.graph.Node;
import jloda.graph.algorithms.Dijkstra;
import jloda.phylo.PhyloGraph;
import jloda.util.Table;

/**
 * compute shortest paths between all taxa
 * Daniel Huson, 9.2025
 */
public class ShortestPathDistances {

	public static Table<Integer, Integer, Double> apply(PhyloGraph graph, boolean undirected, boolean addWeights) {
		var table = new Table<Integer, Integer, Double>();
		for (var a : graph.nodes()) {
			if (graph.hasTaxa(a)) {
				var s = graph.getTaxon(a);
				for (var b : graph.nodes(a)) {
					if (graph.hasTaxa(b)) {
						var t = graph.getTaxon(b);
						var value = apply(graph, a, b, undirected, addWeights);
						table.put(s, t, value);
						table.put(t, s, value);
					}
				}
			}
		}
		return table;
	}

	public static double apply(PhyloGraph graph, Node v, Node w, boolean undirected, boolean addWeights) {
		var list = Dijkstra.compute(graph, v, w, graph::getWeight, undirected);
		if (addWeights) {
			var sum = 0.0;
			for (var i = 1; i < list.size(); i++) {
				var a = list.get(i - 1);
				var b = list.get(i);
				var e = a.getCommonEdge(b);
				sum += graph.getWeight(e);
			}
			return sum;
		} else return list.size() - 1;
	}
}
