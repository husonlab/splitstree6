/*
 * NeighborhoodPruner.java Copyright (C) 2025 Daniel H. Huson
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
import jloda.graph.Graph;
import jloda.graph.Node;
import jloda.util.Triplet;

import java.util.*;
import java.util.function.Function;

import static splitstree6.algorithms.distances.distances2network.razor1.RazorExpand.EPS;

public final class NeighborhoodPruner {

	/**
	 * Neighborhood-based pruning for an undirected graph.
	 * For each edge e=(s,t) with unlabeled endpoints:
	 * N := (neighbors(s) ∪ neighbors(t)) \ {s,t}
	 * For every unordered (u,v) ⊂ N:
	 * D_via_e(u,v) = min{ armS(u) + w(e) + armT(v), armT(u) + w(e) + armS(v) }
	 * If in G \ {e} the shortest path distance(u,v) == D_via_e(u,v) (within EPS) for all pairs,
	 * then e is redundant and is removed.
	 *
	 * @param graph     undirected graph
	 * @param isLabeled node predicate: true if node is labeled (then e is NOT a candidate)
	 * @param weights   edge weight accessor, e.g., graph::getWeight
	 * @return number of removed edges
	 */
	public static int pruneNeighborhoodRedundantEdges(Graph graph,
													  Function<Node, Boolean> isLabeled,
													  Function<Edge, Double> weights) {

		// Collect candidate edges (both endpoints unlabeled)
		var candidates = new ArrayList<Edge>();
		for (var e : graph.edges()) {
			var s = e.getSource();
			var t = e.getTarget();
			if (!isLabeled.apply(s) && !isLabeled.apply(t)) {
				candidates.add(e);
			}
		}

		var toRemove = new ArrayList<Edge>();

		for (var e : candidates) {
			var s = e.getSource();
			var t = e.getTarget();

			// N = adj(s) ∪ adj(t) \ {s,t}
			var N = new LinkedHashSet<Node>();
			addNeighborsExcept(s, s, t, N);
			addNeighborsExcept(t, s, t, N);

			if (N.size() < 2)
				continue;

			/*
			if(N.size()==5 && Math.round(weights.apply(e))==1L) {
				var ones=0;
				var threes=0;
				System.err.println("------Center edge: "+e.getId()+" ("+e.getSource().getId()+","+e.getTarget().getId()+") weight="+weights.apply(e));
				for(var n:N) {
					System.err.print("Neighbor: "+n+" ");
					if(n.isAdjacent(e.getSource())) {
						var f=n.getCommonEdge(e.getSource());
						System.err.println("edge: "+f+" weight="+weights.apply(f));
						if(weights.apply(f)==1.0) {
							ones++;
						}
						if(weights.apply(f)==3.0) {
							threes++;
						}
					}
					if(n.isAdjacent(e.getTarget())) {
						var f=n.getCommonEdge(e.getTarget());
						System.err.println("edge: "+f+" weight="+weights.apply(f));
						if(weights.apply(f)==1.0) {
							ones++;
						}
						if(weights.apply(f)==3.0) {
							threes++;
						}
					}
				}
				if(ones==3 && threes==2) {
					System.err.println("Found");
				}
			}
			 */

			var triplets = new ArrayList<Triplet<Node, Node, Double>>();
			var array = new ArrayList<>(N);
			for (var i = 0; i < array.size(); i++) {
				var v = array.get(i);
				var vc = (v.isAdjacent(e.getSource()) ? e.getSource() : e.getTarget());
				var ve = v.getCommonEdge(vc);
				for (var j = i + 1; j < array.size(); j++) {
					var w = array.get(j);
					var wc = (w.isAdjacent(e.getSource()) ? e.getSource() : e.getTarget());
					var we = w.getCommonEdge(wc);
					if (wc != vc) {
						triplets.add(new Triplet<>(v, w, weights.apply(ve) + weights.apply(e) + weights.apply(we)));
					}
				}
			}
			var allSatisfied = true;
			for (var triplet : triplets) {
				var v = triplet.getFirst();
				var w = triplet.getSecond();
				var dist = triplet.getThird();
				var dAlt = dijkstraDistanceIgnoringEdge(graph, v, w, weights, e);
				if (dAlt > dist + EPS) {
					allSatisfied = false;
					break;
				}
			}
			if (allSatisfied) {
				toRemove.add(e);
			}
		}

		// Apply removals
		for (var e : toRemove) {
			graph.deleteEdge(e);
		}
		return toRemove.size();
	}

	/* ------------------------ helpers ------------------------ */

	/**
	 * Add all neighbors of 'center' except s and t into set N (undirected: use adjacentEdges).
	 */
	private static void addNeighborsExcept(Node center, Node s, Node t, Set<Node> N) {
		for (var e : center.adjacentEdges()) {
			var w = e.getOpposite(center);
			if (w != s && w != t) N.add(w);
		}
	}

	/**
	 * If (a,b) is an edge, return its weight; else empty.
	 */
	private static Optional<Double> edgeWeightIfAdjacent(Node a, Node b, Function<Edge, Double> weights) {
		for (var e : a.adjacentEdges()) {
			if (e.getOpposite(a) == b) {
				return Optional.of(weights.apply(e));
			}
		}
		return Optional.empty();
	}

	/**
	 * Sum that propagates +∞ correctly.
	 */
	private static double safeSum(double x, double y, double z) {
		if (Double.isInfinite(x) || Double.isInfinite(y) || Double.isInfinite(z)) return Double.POSITIVE_INFINITY;
		return x + y + z;
	}

	/**
	 * Dijkstra shortest path in an undirected graph, ignoring a specific edge 'forbidden'.
	 * Uses adjacentEdges() only.
	 */
	private static double dijkstraDistanceIgnoringEdge(Graph graph, Node src, Node dst,
													   Function<Edge, Double> weights, Edge forbidden) {
		var dist = new HashMap<Node, Double>();
		var pq = new PriorityQueue<double[]>(Comparator.comparingDouble(a -> a[0]));

		for (var v : graph.nodes()) dist.put(v, Double.POSITIVE_INFINITY);
		dist.put(src, 0.0);
		pq.add(new double[]{0.0, src.getId()});

		while (!pq.isEmpty()) {
			var top = pq.poll();
			var d = top[0];
			var u = findNodeById(graph, (int) top[1]);
			if (u == null) continue;
			if (d > dist.get(u)) continue;
			if (u == dst) return d;

			for (var e : u.adjacentEdges()) {
				if (e == forbidden) continue;                 // do not use e
				var v = e.getOpposite(u);
				var w = weights.apply(e).doubleValue();
				if (w < 0) throw new IllegalArgumentException("Negative edge weight not supported");
				var nd = d + w;
				if (nd + EPS < dist.get(v)) {
					dist.put(v, nd);
					pq.add(new double[]{nd, v.getId()});
				}
			}
		}
		return Double.POSITIVE_INFINITY;
	}

	/**
	 * Linear search; swap for graph.getNodeById(id) if available.
	 */
	private static Node findNodeById(Graph graph, int id) {
		for (var v : graph.nodes()) if (v.getId() == id) return v;
		return null;
	}
}