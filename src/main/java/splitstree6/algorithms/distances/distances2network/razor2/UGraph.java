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

package splitstree6.algorithms.distances.distances2network.razor2;

import java.util.*;

/**
 * Minimal undirected graph with stable integer vertex IDs.
 * Supports optional integer edge weights.
 */
public final class UGraph {
	private final Map<Integer, Set<Integer>> adjacencies = new HashMap<>();
	private final Map<Edge, Integer> weights = new HashMap<>();

	public UGraph() {
	}

	public UGraph(Collection<Integer> vertices) {
		vertices.forEach(v -> adjacencies.put(v, new HashSet<>()));
	}

	public UGraph(int n) {
		for (int i = 0; i < n; i++) adjacencies.put(i, new HashSet<>());
	}

	/**
	 * Deep copy that preserves vertex IDs, edges, and weights.
	 */
	public UGraph copy() {
		final var g = new UGraph();
		// copy vertices
		for (var u : adjacencies.keySet()) g.ensure(u);
		// copy adjacencies
		for (var u : adjacencies.keySet()) {
			g.adjacencies.get(u).addAll(adjacencies.get(u));
		}
		// copy weights
		g.weights.putAll(this.weights);
		return g;
	}

	public int n() {
		return adjacencies.size();
	}

	public void ensure(int v) {
		adjacencies.computeIfAbsent(v, k -> new HashSet<>());
	}

	public void addEdge(int u, int v) {
		if (u == v) return; // no self-loops
		ensure(u);
		ensure(v);
		adjacencies.get(u).add(v);
		adjacencies.get(v).add(u);
		// leave weight absent unless explicitly set
	}

	public void addEdge(int u, int v, int weight) {
		if (u == v) return; // no self-loops
		ensure(u);
		ensure(v);
		adjacencies.get(u).add(v);
		adjacencies.get(v).add(u);
		weights.put(new Edge(u, v), weight);
	}

	/**
	 * Throws if no weight is present for this edge.
	 */
	public int getWeight(Edge e) {
		var w = weights.get(e);
		if (w == null)
			throw new IllegalStateException("No weight stored for edge " + e);
		return w;
	}

	/**
	 * Throws if edge doesn't exist or has no stored weight.
	 */
	public int getWeight(int u, int v) {
		var w = weights.get(new Edge(u, v));
		if (w == null)
			throw new IllegalStateException("No weight stored for edge (" + u + "," + v + ")");
		return w;
	}

	/**
	 * Only allowed for existing edges.
	 */
	public void setWeight(Edge e, int weight) {
		if (!hasEdge(e.u, e.v)) throw new IllegalArgumentException("No such edge: " + e);
		weights.put(e, weight);
	}

	/**
	 * Only allowed for existing edges.
	 */
	public void setWeight(int u, int v, int weight) {
		if (!hasEdge(u, v)) throw new IllegalArgumentException("No such edge: (" + u + "," + v + ")");
		weights.put(new Edge(u, v), weight);
	}

	boolean hasEdge(int u, int v) {
		var s = adjacencies.get(u);
		return s != null && s.contains(v);
	}

	/**
	 * Returns the normalized edge if present, else null.
	 */
	Edge getEdge(int u, int v) {
		return hasEdge(u, v) ? new Edge(u, v) : null;
	}

	/**
	 * Removes the edge (if present) and its weight mapping. Keeps isolated vertices.
	 */
	public void removeEdge(int u, int v) {
		var su = adjacencies.get(u);
		if (su != null) su.remove(v);
		var sv = adjacencies.get(v);
		if (sv != null) sv.remove(u);
		weights.remove(new Edge(u, v));
	}

	public Set<Integer> nodes() {
		// Unmodifiable live view (same as before)
		return Collections.unmodifiableSet(adjacencies.keySet());
	}

	/**
	 * Returns subset ∩ nodes() as an unmodifiable set.
	 */
	public Set<Integer> nodes(Collection<Integer> subset) {
		var set = new HashSet<Integer>();
		for (var v : adjacencies.keySet()) {
			if (subset.contains(v)) set.add(v);
		}
		return Collections.unmodifiableSet(set);
	}

	public List<Integer> neighbors(int u) {
		var s = adjacencies.get(u);
		return (s == null) ? List.of() : new ArrayList<>(s);
	}

	/**
	 * Neighbors of u that lie in subset (does NOT require u ∈ subset).
	 */
	public List<Integer> neighbors(int u, Collection<Integer> subset) {
		var result = new ArrayList<Integer>();
		var s = adjacencies.get(u);
		if (s == null) return result;
		for (var v : s) if (subset.contains(v)) result.add(v);
		return result;
	}

	public int degree(int u) {
		var s = adjacencies.get(u);
		return (s == null) ? 0 : s.size();
	}

	public int degree(int u, Collection<Integer> subset) {
		var neighbors = neighbors(u, subset);
		return neighbors.size();
	}

	public List<Edge> edges() {
		var list = new ArrayList<Edge>();
		for (var u : adjacencies.keySet())
			for (var v : adjacencies.get(u))
				if (u < v) list.add(new Edge(u, v));
		list.sort(Comparator.<Edge>comparingInt(e -> e.u).thenComparingInt(e -> e.v));
		return list;
	}

	public List<Edge> edges(Collection<Integer> subset) {
		var list = new ArrayList<Edge>();
		for (var u : adjacencies.keySet()) {
			if (!subset.contains(u)) continue;
			for (var v : adjacencies.get(u)) {
				if (subset.contains(v) && u < v) list.add(new Edge(u, v));
			}
		}
		list.sort(Comparator.<Edge>comparingInt(e -> e.u).thenComparingInt(e -> e.v));
		return list;
	}

	/**
	 * Induced subgraph on given edges; copies weights where available.
	 */
	public UGraph inducedByEdges(Collection<Edge> Es) {
		var vs = new HashSet<Integer>();
		for (var e : Es) {
			vs.add(e.u);
			vs.add(e.v);
		}
		var H = new UGraph(vs);
		for (var e : Es) {
			H.addEdge(e.u, e.v);
			Integer w = weights.get(e);
			if (w != null) H.weights.put(e, w);
		}
		return H;
	}

	/**
	 * Connected components of G[subset ∩ V].
	 */
	public List<Set<Integer>> connectedComponents() {
		var res = new ArrayList<Set<Integer>>();
		var seen = new HashSet<Integer>();
		for (var s : nodes()) {
			if (seen.contains(s)) continue;
			var cc = new HashSet<Integer>();
			var dq = new ArrayDeque<Integer>();
			dq.add(s);
			seen.add(s);
			while (!dq.isEmpty()) {
				var u = dq.poll();
				cc.add(u);
				for (var v : neighbors(u))
					if (seen.add(v)) dq.add(v);
			}
			res.add(cc);
		}
		return res;
	}

	/**
	 * Removes a node if present, all its incident edges, and their weights.
	 */
	public void removeNode(Integer u) {
		var nbrs = adjacencies.get(u);
		if (nbrs == null) return;
		// copy to avoid concurrent modification
		for (var v : new ArrayList<>(nbrs)) {
			adjacencies.get(v).remove(u);
			weights.remove(new Edge(u, v));
		}
		adjacencies.remove(u);
	}

	/**
	 * Incident (undirected) edges of x.
	 */
	public List<Edge> outEdges(Integer x) {
		var list = new ArrayList<Edge>();
		for (var v : neighbors(x)) {
			list.add(new Edge(x, v));
		}
		return list;
	}

	public UGraph inducedByNodes(Set<Integer> subset) {
		var graph = new UGraph();
		for (var u : subset) {
			for (var v : neighbors(u, subset)) {
				graph.addEdge(u, v, getWeight(u, v));
			}
		}
		return graph;
	}

	public record Edge(int u, int v) {
		public Edge(int u, int v) {
			this.u = Math.min(u, v);
			this.v = Math.max(u, v);
		}

		/**
		 * Returns the opposite endpoint; throws if x is not incident.
		 */
		public int other(int x) {
			if (x == u) return v;
			if (x == v) return u;
			throw new IllegalArgumentException("Vertex " + x + " not incident to edge " + this);
		}
	}
}