/*
 * RazorUtils.java Copyright (C) 2025 Daniel H. Huson
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

public class RazorUtils {

	public static UGraph realizeFromDistances(int[][] distances, boolean removeRedundantEdges) {
		final int n = distances.length;
		var graph = new UGraph(n);

		for (int i = 0; i < n; i++) {
			for (int j = i + 1; j < n; j++) {
				graph.addEdge(i, j, distances[i][j]);
			}
		}

		// Remove redundant edges: (u,v) is redundant if a u–v path of equal/shorter length exists without (u,v).
		if (removeRedundantEdges) {
			boolean changed;
			do {
				changed = false;
				var edges = new ArrayList<EdgeTriplet>();
				for (var u = 0; u < n; u++) {
					for (var v : graph.neighbors(u)) {
						if (u < v) edges.add(new EdgeTriplet(u, v, graph.getWeight(u, v)));
					}
				}
				for (var e : edges) {
					if (!graph.hasEdge(e.u, e.v)) continue;
					var alt = shortestPathExcluding(graph, e.u, e.v, e.u, e.v);
					if (alt <= e.w) {
						graph.removeEdge(e.u, e.v);
						changed = true;
					}
				}
			} while (changed);
		}
		return graph;
	}

	public static UGraph realizeFromDistances(int[][] distances, Set<Integer> subset, boolean removeRedundantEdges) {
		final int n = distances.length;

		// Build a clean, sorted list of valid nodes from subset (ignore out-of-range)
		var nodes = new ArrayList<Integer>();
		for (int v : subset) if (0 <= v && v < n) nodes.add(v);
		Collections.sort(nodes);

		// Create graph with exactly these vertex IDs
		var graph = new UGraph();
		for (int v : nodes) graph.ensure(v);

		// Add weighted edges only among nodes in subset
		for (int i = 0; i < nodes.size(); i++) {
			int u = nodes.get(i);
			for (int j = i + 1; j < nodes.size(); j++) {
				int v = nodes.get(j);
				graph.addEdge(u, v, distances[u][v]);
			}
		}

		// Remove redundant edges within the induced subgraph on 'nodes'
		if (removeRedundantEdges) {
			boolean changed;
			do {
				changed = false;

				// Collect current edges among 'nodes'
				var edges = new ArrayList<EdgeTriplet>();
				for (int u : nodes) {
					for (int v : graph.neighbors(u)) {
						if (u < v) edges.add(new EdgeTriplet(u, v, graph.getWeight(u, v)));
					}
				}

				// Test each edge for redundancy using shortest path that excludes it
				for (var e : edges) {
					if (!graph.hasEdge(e.u, e.v)) continue; // may have been removed already
					int alt = shortestPathExcluding(graph, e.u, e.v, e.u, e.v);
					if (alt <= e.w) {
						graph.removeEdge(e.u, e.v);
						changed = true;
					}
				}
			} while (changed);
		}

		return graph;
	}

	/**
	 * Dijkstra excluding one edge (banU,banV).
	 */
	private static int shortestPathExcluding(UGraph g, int src, int dst, int banU, int banV) {
		final var n = g.n();
		var dist = new int[n];
		Arrays.fill(dist, Integer.MAX_VALUE);
		dist[src] = 0;

		PriorityQueue<NodeDist> pq = new PriorityQueue<>(Comparator.comparingDouble(nd -> nd.d));
		pq.add(new NodeDist(src, 0));
		boolean[] done = new boolean[n];

		while (!pq.isEmpty()) {
			var cur = pq.poll();
			int u = cur.u;
			if (done[u]) continue;
			done[u] = true;
			if (u == dst) return dist[u];

			for (int v : g.neighbors(u)) {
				if ((u == banU && v == banV) || (u == banV && v == banU)) continue;
				var alt = dist[u] + g.getWeight(u, v);
				if (alt < dist[v]) {
					dist[v] = alt;
					pq.add(new NodeDist(v, alt));
				}
			}
		}
		return dist[dst];
	}

	record EdgeTriplet(int u, int v, int w) {
	}

	record NodeDist(int u, int d) {
	}

	static Set<Integer> fullIndexSet(int n) {
		var s = new HashSet<Integer>();
		for (int i = 0; i < n; i++) s.add(i);
		return s;
	}

	public static void checkAllDistancesEven(int[][] D) {
		for (var row : D) {
			if (Arrays.stream(row).anyMatch(v -> (v % 2) == 1)) {
				throw new RuntimeException("All distances must be even");
			}
		}
	}

	/**
	 * Redundant (u,v) if ∃z with D[u,z] + D[z,v] == D[u,v]
	 */
	static boolean isRedundantEdge(int[][] D, int u, int v, Set<Integer> subset) {
		for (var z : subset) {
			if (z == u || z == v) continue;
			var a = D[u][z];
			var b = D[z][v];
			if (a + b == D[u][v])
				return true;
		}
		return false;
	}
}
