/*
 * MatrixCleaner.java Copyright (C) 2025 Daniel H. Huson
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
import java.util.function.IntPredicate;

import static splitstree6.algorithms.distances.distances2network.razor1.RazorExpand.EPS;

/**
 * MatrixCleaner: removes unlabeled degree-1 nodes and smooths unlabeled degree-2 nodes
 * on a given graph, then returns a new distance matrix D' (APSP on the modified graph).
 * <p>
 * Use when you want to clean a realized network but continue the pipeline on matrices.
 */
public final class MatrixCleaner {
	private MatrixCleaner() {
	}

	/* ========================= Public API ========================= */


	/**
	 * We will mutate a copy of the graph structure and build D' from it.
	 */
	public static Result cleanAndSmooth(double[][] D, UG graph, IntPredicate isLabeled) {
		final var n = D.length;
		final var G = graph.copy();                  // work on a copy
		final var W = new EdgeWeights(D);           // dynamic weights: defaults from D; new edges added here

		// Initialize weights for every existing edge from D
		for (var u = 0; u < n; u++) {
			for (var v : G.neighbors(u)) {
				if (u < v) W.put(u, v, D[u][v]);
			}
		}

		// Alive set
		final var alive = new boolean[n];
		Arrays.fill(alive, true);

		boolean changed;
		do {
			changed = false;

			// 1) Collect unlabeled leaves (deg 0 or 1) and degree-2 nodes
			final var leaves = new ArrayList<Integer>();
			final var deg2 = new ArrayList<Integer>();
			for (var v = 0; v < n; v++) {
				if (!alive[v]) continue;
				if (isLabeled.test(v)) continue; // only touch unlabeled
				final var d = G.degree(v);
				if (d <= 1) leaves.add(v);
				else if (d == 2) deg2.add(v);
			}

			// 2) Remove unlabeled leaves
			for (var v : leaves) {
				if (!alive[v]) continue;
				// delete node: remove its adjacency
				for (var u : new ArrayList<>(G.neighbors(v))) {
					G.removeEdge(u, v); // weights left in W are harmless
				}
				alive[v] = false;
				changed = true;
			}

			// 3) Smooth unlabeled degree-2 nodes
			for (var v : deg2) {
				if (!alive[v]) continue;
				if (G.degree(v) != 2) continue; // degree may have changed after leaf removal
				final var it = G.neighbors(v).iterator();
				final var a = it.next();
				final var b = it.next();
				if (!alive[a] || !alive[b]) continue;

				// edge length replacement: D[a][v] + D[v][b] (use W for safety if edges changed)
				final var av = W.get(a, v, D);
				final var vb = W.get(v, b, D);
				final var newLen = av + vb;

				// add or relax a-b
				if (!G.hasEdge(a, b)) {
					G.addEdge(a, b);
					W.put(a, b, newLen);
				} else {
					// keep shorter
					W.put(a, b, Math.min(W.get(a, b, D), newLen));
				}

				// remove v
				G.removeEdge(a, v);
				G.removeEdge(b, v);
				alive[v] = false;
				changed = true;
			}
		} while (changed);

		// 4) Re-index survivors compactly
		final var oldToNew = new int[n];
		Arrays.fill(oldToNew, -1);
		final var newToOldList = new ArrayList<Integer>();
		for (var i = 0; i < n; i++)
			if (alive[i]) {
				oldToNew[i] = newToOldList.size();
				newToOldList.add(i);
			}
		final var m = newToOldList.size();
		final var newToOld = newToOldList.stream().mapToInt(Integer::intValue).toArray();

		// 5) Build D' = APSP on pruned/smoothed G with weights W (restricted to alive nodes)
		final var Dprime = apsp(G, W, D, alive, oldToNew, m);

		return new Result(Dprime, newToOld, oldToNew);
	}

	/**
	 * Convenience overload: if you don't have the graph, we build a pre-graph from D
	 * (keep edge i-j iff no k with D[i][k]+D[k][j] <= D[i][j] + EPS).
	 */
	public static Result cleanAndSmooth(double[][] D, IntPredicate isLabeled) {
		final var G = buildPreGraphFromD(D);
		return cleanAndSmooth(D, G, isLabeled);
	}

	public record Result(double[][] D, int[] newToOld, int[] oldToNew) {
	}

	/* ========================= Internals ========================= */

	private static double[][] apsp(UG G, EdgeWeights W, double[][] D, boolean[] alive, int[] oldToNew, int m) {
		final var R = new double[m][m];
		for (var i = 0; i < m; i++) Arrays.fill(R[i], Double.POSITIVE_INFINITY);

		record Q(double d, int v) {
		}

		for (var sOld = 0; sOld < alive.length; sOld++) {
			if (!alive[sOld]) continue;
			final var s = oldToNew[sOld];
			final var dist = new double[m];
			Arrays.fill(dist, Double.POSITIVE_INFINITY);
			dist[s] = 0.0;

			final var pq = new PriorityQueue<Q>(Comparator.comparingDouble(q -> q.d));
			pq.add(new Q(0.0, s));

			while (!pq.isEmpty()) {
				final var cur = pq.poll();
				if (cur.d > dist[cur.v] + EPS) continue;

				final var uOld = oldIndex(oldToNew, cur.v);
				for (var wOld : G.neighbors(uOld)) {
					if (!alive[wOld]) continue;
					final var wNew = oldToNew[wOld];
					final var nd = cur.d + W.get(uOld, wOld, D);
					if (nd + EPS < dist[wNew]) {
						dist[wNew] = nd;
						pq.add(new Q(nd, wNew));
					}
				}
			}
			System.arraycopy(dist, 0, R[s], 0, m);
		}

		// finalize matrix
		for (var i = 0; i < m; i++) {
			R[i][i] = 0.0;
			for (var j = i + 1; j < m; j++) {
				final var v = Math.min(R[i][j], R[j][i]);
				R[i][j] = R[j][i] = v;
			}
		}
		return R;
	}

	private static int oldIndex(int[] oldToNew, int newIdx) {
		for (var i = 0; i < oldToNew.length; i++) if (oldToNew[i] == newIdx) return i;
		return -1; // shouldn't happen
	}

	/* Build pre-graph from D using the usual “essential edge” rule. */
	private static UG buildPreGraphFromD(double[][] D) {
		final var n = D.length;
		final var G = new UG(n);
		for (var i = 0; i < n; i++) {
			for (var j = i + 1; j < n; j++) {
				if (!isDominatedByTwoHop(D, i, j)) G.addEdge(i, j);
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

	/* Small holder for edge weights with add/relax support. */
	private static final class EdgeWeights {
		private final Map<Long, Double> map = new HashMap<>();
		private final double[][] D;

		EdgeWeights(double[][] D) {
			this.D = D;
		}

		double get(int u, int v, double[][] fallback) {
			if (u == v) return 0.0;
			if (v < u) {
				int t = u;
				u = v;
				v = t;
			}
			final var key = (((long) u) << 32) | (v & 0xffffffffL);
			final var w = map.get(key);
			return (w != null ? w : fallback[u][v]);
		}

		void put(int u, int v, double w) {
			if (u == v) return;
			if (v < u) {
				int t = u;
				u = v;
				v = t;
			}
			final var key = (((long) u) << 32) | (v & 0xffffffffL);
			map.put(key, w);
		}
	}
}