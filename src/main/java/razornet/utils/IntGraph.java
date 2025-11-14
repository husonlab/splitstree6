/*
 * IntGraph.java Copyright (C) 2025 Daniel H. Huson
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

import java.util.*;
import java.util.function.ToDoubleFunction;

/**
 * Minimal undirected graph with stable integer vertex IDs.
 * Supports optional integer edge weights.
 */
public final class IntGraph {
	private final Map<Integer, Set<Integer>> nodeNeighborsMap = new TreeMap<>();
	private final Map<Edge, Integer> edgeWeightMap = new HashMap<>();

	public IntGraph() {
	}

	public IntGraph(Collection<Integer> vertices) {
		vertices.forEach(this::ensureNode);
	}

	public IntGraph(int n) {
		for (int i = 0; i < n; i++)
			ensureNode(i);
	}

	/**
	 * Deep copy that preserves vertex IDs, edges, and weights.
	 */
	public IntGraph copy() {
		final var g = new IntGraph();
		// copy nodes
		for (var u : nodeNeighborsMap.keySet()) g.ensureNode(u);
		// copy adjacencies
		for (var u : nodeNeighborsMap.keySet()) {
			g.nodeNeighborsMap.get(u).addAll(nodeNeighborsMap.get(u));
		}
		// copy weights
		g.edgeWeightMap.putAll(this.edgeWeightMap);
		return g;
	}

	public int getNumberOfNodes() {
		return nodeNeighborsMap.size();
	}

	public int countNumberOfEdges() {
		return nodeNeighborsMap.values().stream().mapToInt(Set::size).sum() / 2;
	}

	public void ensureNode(int v) {
		nodeNeighborsMap.computeIfAbsent(v, k -> new HashSet<>());
	}

	public int newNode() {
		var v = 0;
		while (nodeNeighborsMap.containsKey(v))
			v++;
		ensureNode(v);
		return v;
	}

	public void addEdge(int u, int v) {
		addEdge(u, v, 1);
	}

	public Edge addEdge(int u, int v, int weight) {
		if (u == v) throw new IllegalArgumentException("No self loops");
		ensureNode(u);
		ensureNode(v);
		nodeNeighborsMap.get(u).add(v);
		nodeNeighborsMap.get(v).add(u);
		var e = new Edge(u, v);
		edgeWeightMap.put(e, weight);
		return e;
	}

	/**
	 * Throws if no weight is present for this edge.
	 */
	public int getWeight(Edge e) {
		var w = edgeWeightMap.get(e);
		if (w == null)
			throw new IllegalStateException("No weight stored for edge " + e);
		return w;
	}

	/**
	 * Throws if edge doesn't exist or has no stored weight.
	 */
	public int getWeight(int u, int v) {
		var w = edgeWeightMap.get(new Edge(u, v));
		if (w == null)
			throw new IllegalStateException("No weight stored for edge (" + u + "," + v + ")");
		return w;
	}

	/**
	 * Only allowed for existing edges.
	 */
	public void setWeight(Edge e, int weight) {
		if (!hasEdge(e.u, e.v))
			throw new IllegalArgumentException("No such edge: " + e);
		edgeWeightMap.put(e, weight);
	}

	/**
	 * Only allowed for existing edges.
	 */
	public void setWeight(int u, int v, int weight) {
		if (!hasEdge(u, v))
			throw new IllegalArgumentException("No such edge: (" + u + "," + v + ")");
		edgeWeightMap.put(new Edge(u, v), weight);
	}

	public boolean hasEdge(int u, int v) {
		var s = nodeNeighborsMap.get(u);
		return s != null && s.contains(v);
	}

	/**
	 * Returns the normalized edge if present, else null.
	 */
	public Edge getEdge(int u, int v) {
		return hasEdge(u, v) ? new Edge(u, v) : null;
	}

	/**
	 * Removes the edge (if present) and its weight mapping. Keeps isolated nodes.
	 */
	public void removeEdge(int u, int v) {
		var su = nodeNeighborsMap.get(u);
		if (su != null) su.remove(v);
		var sv = nodeNeighborsMap.get(v);
		if (sv != null) sv.remove(u);
		edgeWeightMap.remove(new Edge(u, v));
	}

	public void removeEdge(Edge e) {
		removeEdge(e.u(), e.v());
	}

	public Set<Integer> nodes() {
		// Unmodifiable live view (same as before)
		return Collections.unmodifiableSet(nodeNeighborsMap.keySet());
	}

	public boolean containsNode(int v) {
		return nodeNeighborsMap.containsKey(v);
	}

	/**
	 * Returns subset ∩ nodes() as an unmodifiable set.
	 */
	public Set<Integer> nodes(Collection<Integer> subset) {
		var set = new HashSet<Integer>();
		for (var v : nodeNeighborsMap.keySet()) {
			if (subset.contains(v)) set.add(v);
		}
		return Collections.unmodifiableSet(set);
	}

	public List<Integer> getAdjacentNodes(int u) {
		var s = nodeNeighborsMap.get(u);
		return (s == null) ? List.of() : new ArrayList<>(s);
	}

	public List<Edge> getAdjacentEdges(int u) {
		var list = new ArrayList<Edge>();
		for (var v : getAdjacentNodes(u)) {
			list.add(getEdge(u, v));
		}
		return list;
	}

	public void clear() {
		nodeNeighborsMap.clear();
		edgeWeightMap.clear();
	}

	/**
	 * Neighbors of u that lie in subset (does NOT require u ∈ subset).
	 */
	public List<Integer> getAdjacentNodes(int u, Collection<Integer> subset) {
		var result = new ArrayList<Integer>();
		var s = nodeNeighborsMap.get(u);
		if (s == null) return result;
		for (var v : s) if (subset.contains(v)) result.add(v);
		return result;
	}

	public int getDegree(int u) {
		var s = nodeNeighborsMap.get(u);
		return (s == null) ? 0 : s.size();
	}

	public int getDegree(int u, Collection<Integer> subset) {
		var neighbors = getAdjacentNodes(u, subset);
		return neighbors.size();
	}

	public List<Edge> edges() {
		var list = new ArrayList<Edge>();
		for (var u : nodeNeighborsMap.keySet())
			for (var v : nodeNeighborsMap.get(u))
				if (u < v) list.add(new Edge(u, v));
		list.sort(Comparator.<Edge>comparingInt(e -> e.u).thenComparingInt(e -> e.v));
		return list;
	}

	public int getNumberOfEdges() {
		return edges().size();
	}

	public List<Edge> edges(Collection<Integer> subset) {
		var list = new ArrayList<Edge>();
		for (var u : nodeNeighborsMap.keySet()) {
			if (!subset.contains(u)) continue;
			for (var v : nodeNeighborsMap.get(u)) {
				if (subset.contains(v) && u < v) list.add(new Edge(u, v));
			}
		}
		list.sort(Comparator.<Edge>comparingInt(e -> e.u).thenComparingInt(e -> e.v));
		return list;
	}

	/**
	 * Connected connectedComponents of G[subset ∩ V].
	 */
	public List<Set<Integer>> connectedComponents() {
		var res = new ArrayList<Set<Integer>>();
		var seen = new HashSet<Integer>();
		for (var s : nodes()) {
			if (seen.contains(s)) continue;
			var component = new TreeSet<Integer>();
			var queue = new ArrayDeque<Integer>();
			queue.add(s);
			seen.add(s);
			while (!queue.isEmpty()) {
				var u = queue.poll();
				component.add(u);
				for (var v : getAdjacentNodes(u))
					if (seen.add(v)) queue.add(v);
			}
			res.add(component);
		}
		return res;
	}


	/**
	 * Incident (undirected) edges of x.
	 */
	public List<Edge> edges(Integer x) {
		var list = new ArrayList<Edge>();
		for (var v : getAdjacentNodes(x)) {
			list.add(new Edge(x, v));
		}
		return list;
	}


	/**
	 * Removes a node if present, all its incident edges, and their weights.
	 */
	public void removeNode(Integer u) {
		removeEdges(u);
		nodeNeighborsMap.remove(u);
	}

	public void removeEdges(Integer u) {
		var nbrs = nodeNeighborsMap.get(u);
		if (nbrs == null) return;
		// copy to avoid concurrent modification
		for (var v : new ArrayList<>(nbrs)) {
			edgeWeightMap.remove(new Edge(u, v));
			nodeNeighborsMap.get(v).remove(u);
		}
		nbrs.clear();
	}

	public boolean hasWeight(Edge e) {
		return edgeWeightMap.containsKey(e);
	}

	public void removeNeighbors(Integer x, HashSet<Integer> set) {
		for (var y : new ArrayList<>(set)) {
			var e = getEdge(x, y);
			if (e != null) {
				removeEdge(e);
			}
		}
	}

	public IntGraph inducedByEdges(Collection<Edge> Es) {
		var vs = new HashSet<Integer>();
		for (var e : Es) {
			vs.add(e.u);
			vs.add(e.v);
		}
		var H = new IntGraph(vs);
		for (var e : Es) H.addEdge(e.u, e.v);
		return H;
	}

	public void addNode(int v) {
		ensureNode(v);
	}

	public Edge getCommonEdge(int a, int b) {
		if (nodeNeighborsMap.get(a).contains(b))
			return new Edge(a, b);
		else return null;
	}

	/**
	 * renumber all nodes starting at 0
	 */
	public void compact() {
		var oldIdNewIdMap = new TreeMap<Integer, Integer>();

		var count = 0;
		for (var t : nodes()) {
			oldIdNewIdMap.put(t, count++);
		}

		var newWeightMap = new HashMap<Edge, Integer>();
		for (var e : edgeWeightMap.keySet()) {
			newWeightMap.put(new Edge(oldIdNewIdMap.get(e.u()), oldIdNewIdMap.get(e.v())), edgeWeightMap.get(e));
		}
		edgeWeightMap.clear();
		edgeWeightMap.putAll(newWeightMap);

		var newNeighborMap = new TreeMap<Integer, Set<Integer>>();
		for (var e : nodeNeighborsMap.entrySet()) {
			var u = e.getKey();
			var oldSet = e.getValue();
			var newSet = new TreeSet<Integer>();
			for (var v : oldSet) {
				newSet.add(oldIdNewIdMap.get(v));
			}
			newNeighborMap.put(oldIdNewIdMap.get(u), newSet);
		}
		nodeNeighborsMap.clear();
		nodeNeighborsMap.putAll(newNeighborMap);
	}

	public int getTotalLength() {
		var length = 0;
		for (var e : edges())
			length += edgeWeightMap.get(e);
		return length;
	}

	public double getTotalLength(ToDoubleFunction<Integer> mapToDouble) {
		var length = 0.0;
		for (var e : edges())
			length += mapToDouble.applyAsDouble(edgeWeightMap.get(e));
		return length;
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