/*
 * DebugPrint.java Copyright (C) 2025 Daniel H. Huson
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

package splitstree6.algorithms.distances.distances2network.razor1;

import java.util.ArrayList;
import java.util.Collections;

public class DebugPrint {
	/**
	 * Pretty-print a UG: nodes, degrees, edges, and connected components.
	 */
	public static void printUG(UG G, String title) {
		System.err.println("=== UG: " + title + " ===");

		// Nodes (sorted)
		var nodes = new ArrayList<>(G.vertices());
		Collections.sort(nodes);
		System.err.print("Nodes: ");
		System.err.println(nodes);

		// Degrees
		System.err.print("Degrees: ");
		System.err.println(nodes.stream()
				.map(v -> v + ":" + G.degree(v))
				.collect(java.util.stream.Collectors.joining("  ")));

		// Edges (sorted, as u<v)
		var edges = G.edges();
		edges.sort((a, b) -> (a.u() == b.u() ? Integer.compare(a.v(), b.v()) : Integer.compare(a.u(), b.u())));
		System.err.println("Edges (" + edges.size() + "): " +
						   edges.stream().map(e -> "(" + e.u() + "," + e.v() + ")")
								   .collect(java.util.stream.Collectors.joining(" ")));

		// Components
		var comps = G.components();
		System.err.println("Components (" + comps.size() + "):");
		int k = 1;
		for (var cc : comps.stream().sorted((a, b) -> Integer.compare(a.size(), b.size())).toList()) {
			var lst = new ArrayList<>(cc);
			Collections.sort(lst);
			System.err.println("  C" + (k++) + " size=" + lst.size() + " : " + lst);
		}
		System.err.println();
	}

	/**
	 * Adjacency list view (neighbors sorted).
	 */
	public static void printUGAdjacency(UG G, String title) {
		System.err.println("=== UG Adjacency: " + title + " ===");
		var nodes = new ArrayList<>(G.vertices());
		Collections.sort(nodes);
		for (int u : nodes) {
			var nb = new ArrayList<>(G.neighbors(u));
			Collections.sort(nb);
			System.err.println(u + " -> " + nb);
		}
		System.err.println();
	}

	/**
	 * print matrix
	 *
	 * @param D     matrix
	 * @param title title
	 */
	public static void printMatrix(double[][] D, String title) {
		System.err.println("=== " + title + " ===");
		int n = D.length;
		// Header row
		System.err.print("      ");
		for (int j = 0; j < n; j++) {
			System.err.printf("%8d", j);
		}
		System.err.println();
		// Rows
		for (int i = 0; i < n; i++) {
			System.err.printf("%4d ", i);
			for (int j = 0; j < n; j++) {
				System.err.printf("%8.3f", D[i][j]);
			}
			System.err.println();
		}
		System.err.println();
	}
}
