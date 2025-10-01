/*
 * RazorMatrix.java Copyright (C) 2025 Daniel H. Huson
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

/**
 * maintains a lower triangle and uses 1.1 factor when growing
 */
public class RazorMatrixNext {
	private int n; // number of rows/cols in use
	private int[][] matrix = new int[0][0];

	public RazorMatrixNext() {
		this(100);
	}

	public RazorMatrixNext(int n) {
		this.n = n;
		ensureRows(n);
	}

	public RazorMatrixNext(int[][] D) {
		n = D.length;
		ensureRows(n);
		for (var i = 0; i < n; i++) {
			for (int j = 0; j < i; j++) {
				matrix[i][j] = (D[i][j] + D[j][i]) / 2;
			}
		}
	}

	public int get(int i, int j) {
		if (i > j)
			return matrix[i][j];
		else if (i < j)
			return matrix[j][i];
		else return 0;
	}

	public void set(int i, int j, int val) {
		if (i > j)
			matrix[i][j] = val;
		else if (i < j)
			matrix[j][i] = val;
	}

	private void ensureRows(int n) {
		if (n >= matrix.length) {
			var size = (int) Math.ceil(1.1 * (n + 1));
			var tmp = new int[size][0];
			for (var i = 0; i < size; i++) {
				tmp[i] = new int[i];
				if (i < matrix.length) {
					System.arraycopy(matrix[i], 0, tmp[i], 0, i);
				}
			}
			matrix = tmp;
		}
	}

	public int size() {
		return n;
	}

	public void setRow(int r, int[] newRow) {
		for (var i = 0; i < newRow.length; i++) {
			set(i, r, newRow[i]);
		}
	}

	public int newRow() {
		var r = n;
		ensureRows(++n);
		return r;
	}
}
