/*
 * MatrixUtils.java Copyright (C) 2025 Daniel H. Huson
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

public class MatrixUtils {
	/**
	 * Verify that the given symmetric distance matrix satisfies the triangle inequality
	 *
	 * @return true iff triangle inequalities hold
	 */
	public static boolean verifyIntegerTriangleInequalities(int[][] D) {
		final int n = D.length;
		for (int i = 0; i < n; i++) {
			for (int j = 0; j < n; j++) {
				if (i == j) continue;
				final double dij = D[i][j];
				for (int k = 0; k < n; k++) {
					if (k == i || k == j) continue;
					if (dij > D[i][k] + D[k][j]) {
						return false;
					}
				}
			}
		}
		return true;
	}
}
