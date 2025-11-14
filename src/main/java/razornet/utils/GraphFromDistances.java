/*
 * GraphFromDistances.java Copyright (C) 2025 Daniel H. Huson
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

import razornet.razor_int.MutableD;

import java.util.Arrays;
import java.util.Comparator;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.function.ToIntBiFunction;

public class GraphFromDistances {

	public static IntGraph realizeFromDistances(MutableD distances) {
		try {
			return realizeFromDistances(distances::get, Utilities.fullIndexSet(distances.size()), null);
		} catch (CanceledException ignored) {
			throw new RuntimeException(); // can't happen
		}
	}

	public static IntGraph realizeFromDistances(int[][] distances) {
		return realizeFromDistances(distances, Utilities.fullIndexSet(distances.length));
	}

	public static IntGraph realizeFromDistances(int[][] distances, Set<Integer> subset) {
		try {
			return realizeFromDistances((a, b) -> distances[a][b], subset, null);
		} catch (CanceledException ignored) {
			throw new RuntimeException(); // can't happen
		}
	}

	public static IntGraph realizeFromDistances(ToIntBiFunction<Integer, Integer> distance, Set<Integer> subset, Progress progress) throws CanceledException {
		// Create graph with exactly these vertex IDs
		var graph = new IntGraph();
		for (int v : subset) graph.ensureNode(v);

		// Add weighted edges only among nodes in subset
		for (var u : subset) {
			for (var v : subset) {
				if (u < v)
					graph.addEdge(u, v, distance.applyAsInt(u, v));
			}
		}

		// Remove redundant edges within the induced subgraph on 'nodes'
		{
			boolean changed;
			do {
				changed = false;

				// Test each edge for redundancy using a shortest path that excludes it
				for (var e : graph.edges()) {
					if (subset.contains(e.u()) && subset.contains(e.v())) {
						if (!graph.hasEdge(e.u(), e.v())) continue; // may have been removed already
						int alt = shortestPathExcluding(graph, subset, e.u(), e.v(), e.u(), e.v());
						if (alt <= graph.getWeight(e)) {
							graph.removeEdge(e.u(), e.v());
							changed = true;
						}
						if (progress != null)
							progress.checkForCanceled();
					}
				}
			} while (changed);
		}
		return graph;
	}

	/**
	 * Dijkstra excluding one edge (banU,banV).
	 */
	private static int shortestPathExcluding(IntGraph g, Set<Integer> subset, int src, int dst, int banU, int banV) {
		final var n = g.nodes().stream().mapToInt(v -> v).max().orElse(0) + 1;
		var dist = new int[n];
		Arrays.fill(dist, Integer.MAX_VALUE);
		dist[src] = 0;

		record NodeDist(int u, int d) {
		}
		var pq = new PriorityQueue<NodeDist>(Comparator.comparingDouble(nd -> nd.d));
		pq.add(new NodeDist(src, 0));
		var done = new boolean[n];

		while (!pq.isEmpty()) {
			var cur = pq.poll();
			int u = cur.u;
			if (done[u]) continue;
			done[u] = true;
			if (u == dst) return dist[u];

			for (int v : g.getAdjacentNodes(u)) {
				if (subset.contains(v)) {
					if ((u == banU && v == banV) || (u == banV && v == banU)) continue;
					var alt = dist[u] + g.getWeight(u, v);
					if (alt < dist[v]) {
						dist[v] = alt;
						pq.add(new NodeDist(v, alt));
					}
				}
			}
		}
		return dist[dst];
	}
}
