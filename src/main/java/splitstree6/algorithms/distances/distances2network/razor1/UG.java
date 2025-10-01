/*
 * UG.java Copyright (C) 2025 Daniel H. Huson
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

/**
 * Minimal undirected, unweighted graph with stable integer vertex IDs.
 */
public final class UG {
	private final Map<Integer, Set<Integer>> adj = new HashMap<>();

	public UG() {
	}

	public UG(Collection<Integer> vertices) {
		vertices.forEach(v -> adj.put(v, new HashSet<>()));
	}

	public UG(int n) {
		for (int i = 0; i < n; i++) adj.put(i, new HashSet<>());
	}

	public UG copy() {
		final var g = new UG(adj.size());
		for (var u = 0; u < adj.size(); u++) g.adj.get(u).addAll(adj.get(u));
		return g;
	}

	public void ensure(int v) {
		adj.computeIfAbsent(v, k -> new HashSet<>());
	}

	public void addEdge(int u, int v) {
		if (u == v) return;
		ensure(u);
		ensure(v);
		adj.get(u).add(v);
		adj.get(v).add(u);
	}

	boolean hasEdge(int u, int v) {
		return adj.get(u).contains(v);
	}

	Edge getEdge(int u, int v) {
		if (adj.get(u).contains(v)) {
			return new Edge(Math.min(u, v), Math.max(u, v));
		} else return null;
	}

	public void removeEdge(int u, int v) {
		var s = adj.get(u);
		if (s != null) s.remove(v);
		s = adj.get(v);
		if (s != null) s.remove(u);
	}

	public Set<Integer> vertices() {
		return Collections.unmodifiableSet(adj.keySet());
	}

	public Set<Integer> neighbors(int u) {
		return adj.containsKey(u) ? Collections.unmodifiableSet(adj.get(u)) : Set.of();
	}

	public int degree(int u) {
		return adj.containsKey(u) ? adj.get(u).size() : 0;
	}

	public List<Edge> edges() {
		var list = new ArrayList<Edge>();
		for (var u : adj.keySet())
			for (var v : adj.get(u))
				if (u < v) list.add(new Edge(u, v));
		list.sort(Comparator.<Edge>comparingInt(e -> e.u).thenComparingInt(e -> e.v));
		return list;
	}

	public UG inducedByEdges(Collection<Edge> Es) {
		var vs = new HashSet<Integer>();
		for (var e : Es) {
			vs.add(e.u);
			vs.add(e.v);
		}
		var H = new UG(vs);
		for (var e : Es) H.addEdge(e.u, e.v);
		return H;
	}

	public List<Set<Integer>> components() {
		var res = new ArrayList<Set<Integer>>();
		var seen = new HashSet<Integer>();
		for (var s : adj.keySet()) {
			if (seen.contains(s)) continue;
			var cc = new HashSet<Integer>();
			var dq = new ArrayDeque<Integer>();
			dq.add(s);
			seen.add(s);
			while (!dq.isEmpty()) {
				var u = dq.poll();
				cc.add(u);
				for (var v : adj.getOrDefault(u, Set.of()))
					if (seen.add(v)) dq.add(v);
			}
			res.add(cc);
		}
		return res;
	}

	public record Edge(int u, int v) {
	}
}