/*
 * SuperfluousEdge.java Copyright (C) 2025 Daniel H. Huson
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

import jloda.graph.Edge;
import jloda.graph.Node;

import java.util.*;
import java.util.function.ToIntFunction;


public final class SuperfluousEdge {

	/**
	 * Is e=(s,t) superfluous? i.e. is there an s->t path not using e with total weight == weight(e)?
	 * Runs a bounded Dijkstra that:
	 * - never traverses e
	 * - never explores paths with distance > w(e)
	 * - stops immediately if t is popped with distance == w(e)
	 */
	public static boolean isSuperfluous(Node s, Node t, Edge e, ToIntFunction<Edge> getWeight) {
		final var W = getWeight.applyAsInt(e);
		// Trivial quick reject: if s or t aren't endpoints, or W < 0 (shouldn't happen)
		if (!e.nodes().containsAll(List.of(s, t)) || W < 0)
			throw new IllegalArgumentException("Edge e must be exactly (s,t) and have non-negative weight");

		// Dijkstra (bounded by W)
		Map<Node, Integer> dist = new HashMap<>();
		PriorityQueue<NodeDist> pq = new PriorityQueue<>(Comparator.comparingInt(nd -> nd.d));
		dist.put(s, 0);
		pq.add(new NodeDist(s, 0));

		while (!pq.isEmpty()) {
			NodeDist cur = pq.poll();
			if (cur.d != dist.get(cur.n)) continue;     // stale
			if (cur.d > W) break;                       // past the limit -> cannot reach exactly W
			if (cur.n == t) {
				// First time we pop t is the shortest distance s->t without using e.
				return cur.d == W;                      // equal -> superfluous; smaller -> not (under simple paths)
			}
			for (var a : cur.n.adjacentEdges()) {
				// Forbid using e itself
				if (a == e) continue;
				var nb = a.getOpposite(cur.n);
				if (nb == null) continue;               // not incident
				int nd = cur.d + getWeight.applyAsInt(a);
				if (nd < 0) throw new ArithmeticException("Integer overflow or negative weight");
				if (nd > W) continue;                   // prune paths longer than W
				Integer best = dist.get(nb);
				if (best == null || nd < best) {
					dist.put(nb, nd);
					pq.add(new NodeDist(nb, nd));
				}
			}
		}
		return false;
	}

	private static final class NodeDist {
		final Node n;
		final int d;

		NodeDist(Node n, int d) {
			this.n = n;
			this.d = d;
		}
	}
}