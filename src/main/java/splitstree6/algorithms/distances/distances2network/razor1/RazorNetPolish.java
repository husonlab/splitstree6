/*
 * RazorNetPolish.java Copyright (C) 2025 Daniel H. Huson
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

import jloda.util.CanceledException;
import jloda.util.progress.ProgressListener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import static java.lang.Math.abs;
import static java.lang.Math.max;
import static splitstree6.algorithms.distances.distances2network.razor1.RazorExpand.EPS;

/**
 * Polishing pass for RazorNet using a stateful MutableD (growable distance matrix).
 * - Adds auxiliary vertices around hubs (deg >= 4) using one local compactification round.
 * - Does NOT remove edges here; you can rebuild/prune the final graph from the returned matrix.
 * <p>
 * Dependencies you should place in your core package:
 * - MutableD (growable symmetric matrix)
 * - UG (undirected unweighted graph)  ← included here as a minimal inner class if you don't have it
 */
public final class RazorNetPolish {

	/**
	 * Entry point: one polishing pass; returns possibly-expanded matrix.
	 */
	public static double[][] polishUsingUG(double[][] Din, ProgressListener progress) throws CanceledException {
		var M = new MutableD(Din);

		// 1) Build pre-graph: keep only essential edges wrt current M
		var G = buildPreGraph(M);

		// 2) Process high-degree centers first (optional but often stabilizes results)
		var order = new ArrayList<Integer>();

		for (var v = 0; v < M.size(); v++) order.add(v);
		order.sort((a, b) -> Integer.compare(G.degree(b), G.degree(a)));

		progress.setMaximum(5);
		progress.setProgress(order.size());

		var oldN = M.size();
		for (var v : order) {
			progress.incrementProgress();
			if (v >= M.size()) continue;          // M may have grown; guard stale ids
			if (G.degree(v) < 3) continue;

			// S = {v} ∪ N(v)
			var S = new HashSet<Integer>();
			S.add(v);
			S.addAll(G.neighbors(v));
			if (S.size() < 5) continue;           // deg>=4 ⇒ |S|>=5; safety guard

			// --- Step 1 (local compactification): compute slacks c(x) for x∈S

			var slackMap = new HashMap<Integer, Slack>();
			var anySlack = false;
			for (var x : S) {
				var sx = slackWithArgmin(M, S, x);
				if (sx.s > EPS) {
					slackMap.put(x, sx);
					anySlack = true;
				}
			}
			if (!anySlack) continue;

			// --- Append aux nodes globally for each slack x (max3), unless coinciding
			for (var x : slackMap.keySet()) {
				var sx = slackMap.get(x);
				var cand = buildAuxColumn(M, x, sx);          // distances to ALL current vertices
				var coincide = findCoincidingVertex(M, cand); // exact-row match, distance ~0
				if (coincide == null) {
					if (true) {
						var willCreateDegree2Node = false;
						var neighbors = G.neighbors(x);
						for (var u : neighbors) {
							if (G.degree(u) == 2) {
								var su = slackWithArgmin(M, neighbors, u);
								if (su.s > EPS) {
									willCreateDegree2Node = true;
									break;
								}
							}
						}
						System.err.println("willCreateDegree2Node: " + willCreateDegree2Node);
					}

					M.appendVertex(cand);                      // grows M; new index is old size
					return M.toArray();
				}
			}
			if (M.size() > oldN) {
				var oldNodes = G.vertices().size();
				var oldEdges = G.edges().size();
				addEssentialEdgesForNewVertices(M, G, oldN);
				var newNodes = G.vertices().size();
				var newEdges = G.edges().size();
				System.err.println("G: " + oldNodes + " " + oldEdges + " -> " + newNodes + " " + newEdges);
			}
			// Note: we don't update G for new vertices; you rebuild your final graph later anyway.
		}
		return M.toArray();
	}

	/* --------------------------- Pre-graph (UG) --------------------------- */

	private static UG buildPreGraph(MutableD M) {
		var n = M.size();
		var G = new UG(n);
		for (var i = 0; i < n; i++) {
			for (var j = i + 1; j < n; j++) {
				if (isEssentialEdge(M, i, j, EPS, false)) G.addEdge(i, j);
			}
		}
		return G;
	}

	/**
	 * Keep (x,y) iff NO z with d(x,y) >= d(x,z) + d(z,y) - eps (optionally require positive legs).
	 */
	private static boolean isEssentialEdge(MutableD M, int x, int y, double eps, boolean requirePositiveLegs) {
		if (x == y) return false;
		var dxy = M.get(x, y);
		for (var z = 0; z < M.size(); z++) {
			if (z == x || z == y) continue;
			var dxz = M.get(x, z);
			var dzy = M.get(z, y);
			if (requirePositiveLegs && (dxz <= eps || dzy <= eps)) continue;
			if (dxy >= dxz + dzy - eps) return false;
		}
		return true;
	}

	private static void addEssentialEdgesForNewVertices(MutableD M, UG G, int fromIdx) {
		final int newN = M.size();
		for (int u = fromIdx; u < newN; u++) {
			for (int v = 0; v < newN; v++) {
				if (u == v) continue;
				if (isEssentialEdge(M, u, v, EPS, false)) {
					G.addEdge(u, v);
				}
			}
		}
	}

	/* --------------------------- Slack & aux (use MutableD directly) --------------------------- */

	private static final class Slack {
		final double s;
		final int y, z;

		Slack(double s, int y, int z) {
			this.s = s;
			this.y = y;
			this.z = z;
		}
	}

	/**
	 * c(x) = min_{y,z∈S\{x}} (D[x,y]+D[x,z]-D[y,z])/2 with argmin pair, truncated to >=0.
	 */
	private static Slack slackWithArgmin(MutableD M, Set<Integer> S, int x) {
		var others = S.stream().filter(i -> i != x).sorted().toList();
		if (others.size() < 2) return new Slack(0.0, -1, -1);
		var best = Double.POSITIVE_INFINITY;
		int by = -1, bz = -1;
		for (var i = 0; i < others.size(); i++) {
			var y = others.get(i);
			for (var j = i + 1; j < others.size(); j++) {
				var z = others.get(j);
				var val = (M.get(x, y) + M.get(x, z) - M.get(y, z)) / 2.0;
				if (val < best) {
					best = val;
					by = y;
					bz = z;
				}
			}
		}
		return new Slack(max(0.0, best), by, bz);
	}

	/**
	 * Global "max3" aux construction for x':
	 * s = slack.s; y,z are argmin.
	 * d(a,x') = max( d(a,x)-s, d(a,y)-max(d(y,x)-s,0), d(a,z)-max(d(z,x)-s,0), 0 )  for all a in current matrix.
	 */
	private static double[] buildAuxColumn(MutableD M, int x, Slack slack) {
		var s = slack.s;
		var y = slack.y;
		var z = slack.z;
		var dy = max(M.get(y, x) - s, 0.0);
		var dz = max(M.get(z, x) - s, 0.0);

		var n = M.size();
		var cand = new double[n];
		for (var a = 0; a < n; a++) {
			if (a == x) {
				cand[a] = s;
				continue;
			}
			if (a == y) {
				cand[a] = dy;
				continue;
			}
			if (a == z) {
				cand[a] = dz;
				continue;
			}
			var v = max(M.get(a, x) - s, max(M.get(a, y) - dy, M.get(a, z) - dz));
			cand[a] = max(v, 0.0);
		}
		return cand;
	}

	/**
	 * If cand equals an existing row within EPS and is 0 to that vertex, return its index; else null.
	 */
	private static Integer findCoincidingVertex(MutableD M, double[] cand) {
		var n = M.size();
		for (var a = 0; a < n; a++) {
			if (abs(cand[a]) > EPS) continue; // must be 0 to "stick" to a
			var same = true;
			for (var b = 0; b < n; b++) {
				if (abs(M.get(a, b) - cand[b]) > EPS) {
					same = false;
					break;
				}
			}
			if (same) return a;
		}
		return null;
	}

	/* --------------------------- Minimal MutableD (drop in your core) --------------------------- */

	/**
	 * Growable, symmetric distance matrix with safe append and snapshot.
	 */
	public static final class MutableD {
		private double[][] D;

		public MutableD(double[][] initial) {
			D = copyOf(initial);
		}

		public int size() {
			return D.length;
		}

		public double get(int i, int j) {
			return D[i][j];
		}

		public void set(int i, int j, double v) {
			D[i][j] = v;
			D[j][i] = v;
		}

		public int appendVertex(double[] cand) {
			var n = D.length;
			var R = new double[n + 1][n + 1];
			for (var i = 0; i < n; i++) System.arraycopy(D[i], 0, R[i], 0, n);
			for (var i = 0; i < n; i++) {
				R[i][n] = cand[i];
				R[n][i] = cand[i];
			}
			R[n][n] = 0.0;
			D = R;
			return n;
		}

		public double[][] toArray() {
			return copyOf(D);
		}

		private static double[][] copyOf(double[][] A) {
			var n = A.length;
			var R = new double[n][n];
			for (var i = 0; i < n; i++) System.arraycopy(A[i], 0, R[i], 0, n);
			return R;
		}
	}
}