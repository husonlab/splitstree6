/*
 * RazorLocalPruner.java Copyright (C) 2025 Daniel H. Huson
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
/*
 * RazorLocalPruner.java  — matrix-in/matrix-out local pruning for RazorNet
 * Modeled on: RazorLocalPrunerOnGraph (PhyloGraph-based) structure and intent
 *
 * Input:  symmetric distance matrix D (metric) for indices 0..n-1
 *         IntPredicate isLabeled (true = original taxon; false = unlabeled/internal)
 * Output: pruned distance matrix D' (APSP on pruned pre-graph, weights from D)
 *
 * Daniel Huson, 9.2025
 */

import jloda.util.CanceledException;
import jloda.util.progress.ProgressListener;

import java.util.*;
import java.util.function.IntPredicate;

import static splitstree6.algorithms.distances.distances2network.razor1.RazorExpand.EPS;

public final class RazorLocalPruner {

	/**
	 * Local neighbor-based pruning on a distance matrix.
	 * For each internal edge e=(s,t):
	 * N := neighbors(s) ∪ neighbors(t) \ {s,t}
	 * localNodes := N ∪ {x ≠ s,t : x has ≥2 distinct neighbors in N}
	 * Build local subgraph on localNodes with all UG edges except (s,t)
	 * For every unordered (u,v) ⊂ N with u and v attached to different endpoints:
	 * D_via_e(u,v) = min( D[u,s] + D[s,t] + D[t,v],  D[u,t] + D[t,s] + D[s,v] )
	 * Let dAlt = shortest path(u,v) in the local subgraph (never using (s,t))
	 * If |dAlt - D_via_e(u,v)| ≤ EPS for all such pairs, remove (s,t)
	 * Returns D' as APSP on the pruned pre-graph (weights from original D).
	 */
	public static double[][] prune(final double[][] D, final IntPredicate isLabeled, ProgressListener progress) throws CanceledException {
		final var n = D.length;

		// 1) Build pre-graph from D using the essential-edge test (like your other passes)
		final var G = buildPreGraph(D);

		var changed = false;

		// 2) Candidates: both endpoints unlabeled
		final var candidates = new ArrayList<UG.Edge>();
		for (var s = 0; s < n; s++) {
			for (var t = s + 1; t < n; t++) {
				if (G.hasEdge(s, t) && !isLabeled.test(s) && !isLabeled.test(t)) {
					candidates.add(G.getEdge(s, t));
				}
			}
		}

		candidates.sort(Comparator.comparingDouble(f -> -D[f.u()][f.v()]));

		progress.setMaximum(candidates.size());
		progress.setProgress(0);
		// 3) Local redundancy test per edge
		for (var e : candidates) {
			progress.incrementProgress();
			final var s = e.u();
			final var t = e.v();
			if (G.hasEdge(s, t)) { // not been removed
				// neighbors(s) ∪ neighbors(t) \ {s,t}
				final var neighborNodes = new LinkedHashSet<Integer>();
				neighborNodes.addAll(G.neighbors(s));
				neighborNodes.addAll(G.neighbors(t));
				neighborNodes.remove(s);
				neighborNodes.remove(t);
				if (neighborNodes.size() < 2) continue;

				// localNodes: neighbors + nodes touching ≥2 distinct neighbors
				final var localNodes = new LinkedHashSet<>(neighborNodes);
				for (var x : neighborNodes) {
					for (var u : G.neighbors(x)) {
						for (var v : G.neighbors(u)) {
							if (v != x && neighborNodes.contains(v)) {
								localNodes.add(u);
								break;
							}
						}
					}
				}

				// local subgraph edges (exclude the center edge (s,t))
				var localEdges = new HashSet<UG.Edge>();
				for (var u : localNodes) {
					for (var v : G.neighbors(u)) {
						if (localNodes.contains(v)) {
							var f = G.getEdge(u, v);
							if (f != null && !e.equals(f))
								localEdges.add(f);
						}
					}
				}

				final var list = new ArrayList<>(neighborNodes);
				var allPairsSatisfied = true;

				outer:
				for (var i = 0; i < list.size(); i++) {
					final var u = list.get(i);
					var uc = (G.hasEdge(u, s) ? s : t);
					for (var j = i + 1; j < list.size(); j++) {
						final var v = list.get(j);
						var vc = (G.hasEdge(v, s) ? s : t);
						if (uc != vc) {
							var dist = D[u][uc] + D[s][t] + D[v][vc];
							// Shortest u—v in local subgraph, never using (s,t)
							final var alt = dijkstraLocal(D, G, localNodes, localEdges, u, v);
							if (Double.isInfinite(alt) || alt > dist + EPS) {
								allPairsSatisfied = false;
								break outer;
							}
						}
					}
				}

				if (allPairsSatisfied) {
					G.removeEdge(e.u(), e.v());
					changed = true;
				}
			}
		}

		if (changed) {
			// 5) Return D' = APSP on pruned G (weights from original D)
			var Dp = apspOnUG(G, D);
			return MatrixCleaner.cleanAndSmooth(Dp, G, isLabeled).D();
		} else return D;
	}

	/**
	 * Pre-graph: keep (i,j) iff NO k with D[i][k]+D[k][j] <= D[i][j] + EPS.
	 */
	private static UG buildPreGraph(double[][] D) {
		final var n = D.length;
		final var G = new UG(n); // swap with your shared UG if available
		for (var i = 0; i < n; i++) {
			for (var j = i + 1; j < n; j++) {
				if (!isDominatedByTwoHop(D, i, j)) {
					G.addEdge(i, j); // UG call
				}
			}
		}
		return G;
	}

	private static boolean isDominatedByTwoHop(double[][] D, int i, int j) {
		final var dij = D[i][j];
		for (var k = 0; k < D.length; k++) {
			if (k == i || k == j) continue;
			if (D[i][k] + D[k][j] <= dij + EPS) return true;
		}
		return false;
	}

	/**
	 * Dijkstra local edges
	 */
	private static double dijkstraLocal(double[][] D, UG G, Collection<Integer> localNodes, Set<UG.Edge> localEdges, int src, int dst) {
		final var n = D.length;
		final var dist = new double[n];
		for (var v : localNodes) {
			dist[v] = Double.POSITIVE_INFINITY;
		}
		dist[src] = 0.0;

		record PQ(double d, int v) {
		}
		final var pq = new PriorityQueue<PQ>(Comparator.comparingDouble(p -> p.d));
		pq.add(new PQ(0.0, src));

		while (!pq.isEmpty()) {
			final var cur = pq.poll();
			if (cur.d > dist[cur.v] + EPS) continue;
			if (cur.v == dst) return cur.d;

			for (var w : G.neighbors(cur.v)) {
				if (localEdges.contains(G.getEdge(cur.v, w))) {
					final var nd = cur.d + D[cur.v][w];
					if (nd + EPS < dist[w]) {
						dist[w] = nd;
						pq.add(new PQ(nd, w));
					}
				}
			}
		}
		return Double.POSITIVE_INFINITY;
	}

	/**
	 * APSP on UG with edge weights from D (no matrix growth; preserves order).
	 */
	private static double[][] apspOnUG(UG G, double[][] D) {
		final var n = D.length;
		final var R = new double[n][n];
		for (var i = 0; i < n; i++) Arrays.fill(R[i], Double.POSITIVE_INFINITY);

		record PQ(double d, int v) {
		}

		for (var s = 0; s < n; s++) {
			final var dist = new double[n];
			Arrays.fill(dist, Double.POSITIVE_INFINITY);
			dist[s] = 0.0;

			final var pq = new PriorityQueue<PQ>(Comparator.comparingDouble(p -> p.d));
			pq.add(new PQ(0.0, s));

			while (!pq.isEmpty()) {
				final var cur = pq.poll();
				if (cur.d > dist[cur.v] + EPS) continue;
				for (var w : G.neighbors(cur.v)) {
					final var nd = cur.d + D[cur.v][w];
					if (nd + EPS < dist[w]) {
						dist[w] = nd;
						pq.add(new PQ(nd, w));
					}
				}
			}
			System.arraycopy(dist, 0, R[s], 0, n);
		}

		// Symmetrize and set diagonals
		for (var i = 0; i < n; i++) {
			R[i][i] = 0.0;
			for (var j = i + 1; j < n; j++) {
				final var m = Math.min(R[i][j], R[j][i]);
				R[i][j] = R[j][i] = m;
			}
		}
		return R;
	}

	/**
	 * Local subgraph (for Dijkstra), edge set excludes the tested center edge.
	 */
	private static final class LocalUG {
		private final ArrayList<HashSet<Integer>> adj;

		LocalUG(int n) {
			adj = new ArrayList<>(n);
			for (var i = 0; i < n; i++) adj.add(new HashSet<>());
		}

		void add(int u, int v) {
			if (u != v) {
				adj.get(u).add(v);
				adj.get(v).add(u);
			}
		}

		Collection<Integer> neighbors(int u) {
			return adj.get(u);
		}

		boolean hasEdge(int u, int v) {
			return adj.get(u).contains(v);
		}
	}
}