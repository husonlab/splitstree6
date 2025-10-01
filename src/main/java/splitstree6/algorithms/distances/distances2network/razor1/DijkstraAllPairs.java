/*
 * DijkstraAllPairs.java Copyright (C) 2025 Daniel H. Huson
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

import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.ToDoubleFunction;

/**
 * Dijkstra all pairs
 * Daniel Huson, 9.2025, using ChatGPT
 */
public final class DijkstraAllPairs {
	/**
	 * Compute all shortest paths between every pair of nodes in {@code nodes}, using only
	 * nodes ∈ allowedNodes and edges ∈ allowedEdges. The graph is treated as UNDIRECTED.
	 *
	 * @param nodes        the nodes to consider as sources/targets (often all nodes in a subgraph)
	 * @param allowedNodes node filter (must include sources/targets you care about)
	 * @param allowedEdges edge filter (only these edges may be traversed)
	 * @param weights      edge -> nonnegative weight
	 * @return A map: source -> (target -> PathResult). If target is unreachable, distance = +Inf and edges = null.
	 */
	public static <Node, Edge> Map<Node, Map<Node, PathResult<Edge>>> allShortestPaths(
			Collection<Node> nodes,
			Set<Node> allowedNodes,
			Set<Edge> allowedEdges,
			BiFunction<Edge, Node, Node> getOtherEnd,
			Function<Node, Iterable<Edge>> getAdjacentEdges,
			ToDoubleFunction<Edge> weights
	) {
		// Work only with sources that are in the requested nodes AND allowedNodes
		var sources = new ArrayList<Node>();
		for (var n : nodes) {
			if (allowedNodes.contains(n)) {
				sources.add(n);
			}
		}

		var result = new HashMap<Node, Map<Node, PathResult<Edge>>>();
		for (var s : sources) {
			result.put(s, dijkstraRestricted(s, nodes, allowedNodes, allowedEdges, getOtherEnd, getAdjacentEdges, weights));
		}
		return result;
	}


	public record PathResult<Edge>(double distance, List<Edge> edges) {
		@Override
		public String toString() {
			return "dist=" + distance + ", edges=" + (edges == null ? "null" : edges.size());
		}
	}


	private static <Node, Edge> Map<Node, PathResult<Edge>> dijkstraRestricted(
			Node source,
			Collection<Node> nodes,
			Set<Node> allowedNodes,
			Set<Edge> allowedEdges,
			BiFunction<Edge, Node, Node> getOtherEnd,
			Function<Node, Iterable<Edge>> getAdjacentEdges,
			ToDoubleFunction<Edge> weights
	) {
		// Distance & predecessor edge maps
		Map<Node, Double> dist = new HashMap<>();
		Map<Node, Edge> prevEdge = new HashMap<>();

		// Initialize distances
		for (Node n : allowedNodes) {
			dist.put(n, Double.POSITIVE_INFINITY);
		}
		dist.put(source, 0.0);

		// Min-heap by tentative distance
		var pq = new PriorityQueue<PQEntry<Node>>(Comparator.comparingDouble(e -> e.dist));
		pq.add(new PQEntry<>(source, 0.0));

		// Standard Dijkstra with filtering
		while (!pq.isEmpty()) {
			var cur = pq.poll();
			var u = cur.node;

			if (cur.dist > dist.get(u)) continue; // stale entry

			// Iterate over *all* adjacent edges, then filter to allowed
			for (var e : getAdjacentEdges.apply(u)) {
				if (!allowedEdges.contains(e)) continue;

				var v = getOtherEnd.apply(e, u);  // <-- IMPORTANT: undirected neighbor
				if (v == null) continue; // edge not incident to u (defensive)
				if (!allowedNodes.contains(v)) continue;

				var w = weights.applyAsDouble(e);
				if (w < 0) {
					throw new IllegalArgumentException("Dijkstra requires nonnegative weights: " + w);
				}

				var alt = dist.get(u) + w;
				if (alt < dist.get(v)) {
					dist.put(v, alt);
					prevEdge.put(v, e);
					pq.add(new PQEntry<>(v, alt));
				}
			}
		}

		// Build per-target PathResult
		var out = new HashMap<Node, PathResult<Edge>>();
		for (var t : nodes) {
			var d = dist.getOrDefault(t, Double.POSITIVE_INFINITY);
			if (d.isInfinite()) {
				out.put(t, new PathResult<>(Double.POSITIVE_INFINITY, null));
			} else if (t.equals(source)) {
				out.put(t, new PathResult<>(0.0, Collections.emptyList()));
			} else {
				var path = rebuildEdgePath(source, t, prevEdge, getOtherEnd);
				out.put(t, new PathResult<>(d, path));
			}
		}
		return out;
	}

	private record PQEntry<Node>(Node node, double dist) {
	}

	private static <Node, Edge> List<Edge> rebuildEdgePath(Node source, Node target, Map<Node, Edge> prevEdge, BiFunction<Edge, Node, Node> getOtherEnd) {
		var rev = new ArrayList<Edge>();
		var cur = target;
		while (!cur.equals(source)) {
			var e = prevEdge.get(cur);
			if (e == null) break; // unreachable safeguard (shouldn't happen if dist finite)
			rev.add(e);
			cur = getOtherEnd.apply(e, cur);
		}
		Collections.reverse(rev);
		return rev;
	}
}