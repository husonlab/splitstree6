/*
 * RazorDebugPrint.java Copyright (C) 2025 Daniel H. Huson
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

package splitstree6.algorithms.distances.distances2network.razor2;

import java.util.*;

public class RazorDebugPrint {

	public static void showMatrix(String title, int[][] D) {
		showMatrix(title, D, null);
	}

	public static void showMatrix(String title, int[][] D, Set<Integer> subset) {
		if (title != null && !title.isBlank())
			System.err.println(title);
		System.err.print("    ");
		for (var i = 0; i < D.length; i++) {
			if (subset == null || subset.contains(i)) {
				System.err.printf("%3d ", i);
			}
		}
		System.err.println();
		System.err.print("    ");
		for (var i = 0; i < D.length; i++) {
			if (subset == null || subset.contains(i)) {
				System.err.print("----");
			}
		}
		System.err.println();
		for (var i = 0; i < D.length; i++) {
			if (subset == null || subset.contains(i)) {
				System.err.printf("%3d|", i);
				for (var j = 0; j < D.length; j++) {
					if (subset == null || subset.contains(j)) {
						System.err.printf("%3d ", D[i][j]);
					}
				}
				System.err.println();
			}
		}
		System.err.println();
	}

	// ---------- NEW: graph-as-matrix printing (weights; 0 if absent) ----------

	public static void showGraph(String title, UGraph graph) {
		showGraph(title, graph, null);
	}

	public static void showGraph(String title, UGraph graph, Set<Integer> subset) {
		if (graph == null) {
			System.err.println("(null graph)");
			return;
		}
		if (title != null && !title.isBlank()) System.err.println(title);

		int countNodes;
		int countEdges;
		if (subset == null) {
			countNodes = graph.nodes().size();
			countEdges = graph.edges().size();
		} else {
			countNodes = (int) graph.nodes().stream().filter(subset::contains).count();
			countEdges = (int) graph.edges().stream().filter(e -> subset.contains(e.u()) && subset.contains(e.v())).count();
		}
		System.err.println("|V|=" + countNodes + " |E|=" + countEdges);
		if (countNodes > 0) {


			// Build a sorted node list; if subset provided, intersect first
			List<Integer> nodes = new ArrayList<>();
			if (subset == null) {
				nodes.addAll(graph.nodes());
			} else {
				for (int v : graph.nodes()) if (subset.contains(v)) nodes.add(v);
			}
			Collections.sort(nodes);

			// Header (column indices)
			System.err.print("    ");
			for (int v : nodes) System.err.printf("%3d ", v);
			System.err.println();

			// Separator
			System.err.print("    ");
			for (int ignored : nodes) System.err.print("----");
			System.err.println();

			// Rows
			for (int u : nodes) {
				System.err.printf("%3d|", u);
				for (int v : nodes) {
					System.err.printf("%3d ", safeWeightOrZero(graph, u, v));
				}
				System.err.println();
			}
			System.err.println();
		}
	}

	/**
	 * Returns the stored weight if present; otherwise 0. Diagonal is 0.
	 */
	private static int safeWeightOrZero(UGraph g, int u, int v) {
		if (u == v) return 0;
		try {
			return g.getWeight(u, v);
		} catch (RuntimeException ex) {
			// No stored weight or no such edge → print 0
			return 0;
		}
	}

	public static void showOutputGraph(String title, Collection<RazorExpand.OutputEdge> edges) {
		var graph = new UGraph();
		for (var outputEdge : edges) {
			graph.addEdge(outputEdge.i(), outputEdge.j(), outputEdge.w());
		}
		showGraph(title, graph);
	}
}