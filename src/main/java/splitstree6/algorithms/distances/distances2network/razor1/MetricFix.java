/*
 * MetricFix.java Copyright (C) 2025 Daniel H. Huson
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

import static java.lang.Double.isFinite;
import static splitstree6.algorithms.distances.distances2network.razor1.RazorExpand.EPS;

/**
 * enforces the triangle inequalities
 * Daniel Huson, 9.2025
 */
public final class MetricFix {
	private MetricFix() {
	}

	/**
	 * Returns a copy of D that satisfies the triangle inequalities.
	 * Method: metric closure via Floyd–Warshall on the complete graph with edge weights D.
	 * Properties:
	 * - Dp <= D elementwise (never increases any distance)
	 * - symmetric (within EPS) and zero diagonal
	 * - triangle inequalities hold within EPS
	 *
	 * @param D square distance matrix
	 * @return corrected distance matrix Dp
	 */
	public static double[][] enforceTriangleInequalities(double[][] D) {
		final int n = D.length;
		final double INF = Double.POSITIVE_INFINITY;

		// 1) Copy & sanitize: zero diagonal, non-negative, symmetric "best" (min), finite
		double[][] M = new double[n][n];
		for (int i = 0; i < n; i++) {
			if (D[i].length != n) throw new IllegalArgumentException("Matrix not square at row " + i);
		}
		for (int i = 0; i < n; i++) {
			for (int j = 0; j < n; j++) {
				double a = D[i][j];
				double b = D[j][i];
				// choose the smaller of the two directions (more conservative)
				double v = (i == j) ? 0.0 : Math.min(safeNonNeg(a), safeNonNeg(b));
				M[i][j] = isFinite(v) ? v : INF;
			}
		}

		// 2) Floyd–Warshall APSP (metric closure): M[i][j] = min(M[i][j], M[i][k] + M[k][j])
		for (int k = 0; k < n; k++) {
			for (int i = 0; i < n; i++) {
				double dik = M[i][k];
				if (!isFinite(dik)) continue;
				for (int j = 0; j < n; j++) {
					double alt = dik + M[k][j];
					if (alt + EPS < M[i][j]) {
						M[i][j] = alt;
					}
				}
			}
		}

		// 3) Re-symmetrize (average to smooth tiny numeric asymmetries), zero diag, clip tiny negatives
		for (int i = 0; i < n; i++) {
			M[i][i] = 0.0;
			for (int j = i + 1; j < n; j++) {
				double v = 0.5 * (M[i][j] + M[j][i]);
				if (v < 0 && v > -EPS) v = 0.0; // numeric noise
				M[i][j] = M[j][i] = v;
			}
		}

		return M;
	}

	// Helpers
	private static double safeNonNeg(double x) {
		if (!isFinite(x)) return Double.POSITIVE_INFINITY;
		return (x < 0 && x > -EPS) ? 0.0 : Math.max(0.0, x);
	}
}