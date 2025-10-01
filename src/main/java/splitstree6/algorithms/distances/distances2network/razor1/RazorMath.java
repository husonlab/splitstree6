/*
 * RazorMath.java Copyright (C) 2025 Daniel H. Huson
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

import java.util.HashSet;
import java.util.Set;

import static splitstree6.algorithms.distances.distances2network.razor1.RazorExpand.EPS;

public final class RazorMath {
	/**
	 * Compute c(x) (slack) and an attaining pair (y, z) over the given subset.
	 */
	static Slack slackWithArgmin(MutableD M, Set<Integer> subset, int x) {
		var others = subset.stream().filter(i -> i != x).sorted().toList();
		if (others.size() < 2) return new Slack(0.0, -1, -1);

		var best = Double.POSITIVE_INFINITY;
		var by = -1;
		var bz = -1;
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
				if (false) {
					if (val <= 0) {
						System.err.println("x,y=" + M.get(x, y));
						System.err.println("x,z=" + M.get(x, z));
						System.err.println("y,z=" + M.get(y, z));
						System.err.println("x=" + x + ", y=" + y + ", z=" + z + " value=" + val);
					}
				}
			}
		}
		return new Slack(Math.max(0.0, best), by, bz);
	}

	/**
	 * Keep (x,y) iff there is NO z with d(x,y) >= d(x,z) + d(y,z) (within eps).
	 */
	public static boolean isEssentialEdge(double[][] D, int x, int y, double eps, boolean requirePositiveLegs) {
		if (x == y) return false;
		final double dxy = D[x][y];
		for (int z = 0; z < D.length; z++) {
			if (z == x || z == y) continue;
			final double dxz = D[x][z];
			final double dyz = D[y][z];
			if (requirePositiveLegs && (dxz <= eps || dyz <= eps)) continue; // optional
			if (dxy >= dxz + dyz - eps) return false; // redundant: x–y explained via z
		}
		return true; // no witness z found → keep edge (x,y)
	}

	static Set<Integer> fullIndexSet(int n) {
		var s = new HashSet<Integer>();
		for (int i = 0; i < n; i++) s.add(i);
		return s;
	}

	/**
	 * Redundant (u,v) if ∃z with D[u,z] + D[z,v] <= D[u,v] + EPS (and both segments > 0 if you want).
	 */
	static boolean isRedundantEdge(MutableD M, int u, int v) {
		int n = M.size();
		for (int z = 0; z < n; z++) {
			if (z == u || z == v) continue;
			var a = M.get(u, z);
			var b = M.get(z, v);
			// If you want to forbid zero-length steps, uncomment the next line:
			// if (a <= EPS || b <= EPS) continue;
			if (a + b <= M.get(u, v) + EPS)
				return true;
		}
		return false;
	}

	record Slack(double s, int y, int z) {
	}

	/**
	 * Build the candidate “column” for x' using s=c(x) and the “max3” construction.
	 */
	static double[] buildAuxColumn(MutableD M, int x, Slack sx) {
		double s = sx.s;
		int y = sx.y, z = sx.z;

		double dy = Math.max(M.get(y, x) - s, 0.0);
		double dz = Math.max(M.get(z, x) - s, 0.0);

		int n = M.size();
		double[] cand = new double[n];
		for (int a = 0; a < n; a++) {
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
			double v = Math.max(M.get(a, x) - s, Math.max(M.get(a, y) - dy, M.get(a, z) - dz));
			cand[a] = Math.max(v, 0.0);
		}
		return cand;
	}

	/**
	 * If cand duplicates an existing vertex row within EPS (and is 0 to that vertex), return its index; else null.
	 */
	static Integer coincidesWithExisting(MutableD M, double[] cand) {
		int n = M.size();
		for (int a = 0; a < n; a++) {
			if (Math.abs(cand[a]) > EPS) continue;      // must be 0 to “stick” to a
			boolean same = true;
			for (int b = 0; b < n; b++) {
				if (Math.abs(M.get(a, b) - cand[b]) > EPS) {
					same = false;
					break;
				}
			}
			if (same) return a;
		}
		return null;
	}
}