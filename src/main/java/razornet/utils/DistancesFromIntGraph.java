/*
 * DistancesFromIntGraph.java Copyright (C) 2025 Daniel H. Huson
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

package razornet.utils;

import razornet.razor_int.DijkstraAllPairs;

import java.util.HashMap;
import java.util.TreeSet;

/**
 * extracts the shortest path distances from a graph
 * Daniel Huson, 10.2025
 */
public final class DistancesFromIntGraph {
	/**
	 * Computes the shortest path distance (undirected) matrix using Dijkstra.
	 *
	 * @param graph the graph
	 * @param remap if true, remap nodes to 0...n-1, otherwise maintain their current numbering
	 * @return symmetric distance matrix
	 */
	public static int[][] shortestPathMatrix(IntGraph graph, boolean remap) {
		var maxId = graph.nodes().stream().mapToInt(i -> i).max().orElse(0);

		var oldNewMap = new HashMap<Integer, Integer>();
		var n = 0;
		if (remap) {
			for (var v : new TreeSet<>(graph.nodes())) {
				oldNewMap.put(v, n);
				n++;
			}
		} else {
			n = maxId + 1;
		}
		var M = new int[n][n];

		var map = DijkstraAllPairs.allShortestPathLengths(graph);
		for (var entry : map.entrySet()) {
			var s = entry.getKey();
			var i = oldNewMap.getOrDefault(s, s);
			for (var t : entry.getValue().keySet()) {
				var j = oldNewMap.getOrDefault(t, t);
				if (s < t) {
					M[i][j] = M[j][i] = entry.getValue().get(t);
				}
			}
		}
		return M;
	}
}