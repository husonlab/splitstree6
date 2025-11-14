/*
 * TriangleInequalities.java Copyright (C) 2025 Daniel H. Huson
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

/**
 * check triangle inequalities and fix them, if necessary
 * Daniel Huson, 9.2025
 */
public class TriangleInequalities {
	/**
	 * Returns true iff the triangle inequalities hold:
	 * d(i,k) <= d(i,j) + d(j,k)  for all i,j,k (distinct)
	 * Empty or single/two-node matrices return true.
	 */
	public static boolean check(int[][] m, boolean throwException) {
		var n = m.length;
		for (var i = 0; i < n; i++) {
			for (var k = 0; k < n; k++) {
				if (k == i) continue;
				var dik = m[i][k];
				for (var j = 0; j < n; j++) {
					if (j == i || j == k) continue;
					var dij = m[i][j];
					var djk = m[j][k];
					if (dik > dij + djk) {
						if (throwException)
							throw new IllegalStateException("Triangle equality not fulfilled");
						return false;
					}
				}
			}
		}
		return true;
	}

	/**
	 * Enforces all triangle inequalities by computing the metric closure.
	 * This modifies m IN-PLACE, decreasing entries only when necessary.
	 *
	 * @param distances the matrix to enforce triangle inequalities on
	 *                  <p>
	 *                  Complexity: O(n^3) over the number of active nodes.
	 */
	public static void fix(int[][] distances) {
		int n = distances.length;
		if (n <= 2) return;

		// Floyd–Warshall in (min, +) semiring
		for (int k = 0; k < n; k++) {
			for (int i = 0; i < n; i++) {
				if (i == k) continue;
				var dik = distances[i][k];
				for (int j = 0; j < n; j++) {
					if (j == k || j == i) continue;
					var cand = dik + distances[k][j];
					if (cand < distances[i][j]) {
						distances[j][i] = distances[i][j] = cand;
					}
				}
			}
		}
	}

}
