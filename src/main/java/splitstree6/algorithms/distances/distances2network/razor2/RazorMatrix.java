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
public class RazorMatrix {
	private int[][] matrix = new int[0][0];

	public RazorMatrix() {
		this(100);
	}

	public RazorMatrix(int n) {
		resize(n);
	}

	public RazorMatrix(int[][] D) {
		resize(D.length);
		for (var i = 0; i < D.length; i++) {
			for (int j = 0; j < D.length; j++) {
				matrix[i][j] = (D[i][j] + D[j][i]) / 2;
			}
		}
	}

	public int get(int i, int j) {
		return matrix[i][j];
	}

	public void set(int i, int j, int val) {
		matrix[i][j] = matrix[j][i] = val;
	}

	private void resize(int n) {
		var tmp = new int[n][n];
		for (var i = 0; i < matrix.length; i++) {
			for (int j = 0; j < matrix.length; j++) {
				tmp[i][j] = tmp[j][i] = matrix[i][j];
			}
		}
		matrix = tmp;
	}

	public int size() {
		return matrix.length;
	}

	public void setRow(int r, int[] newRow) {
		for (var i = 0; i < newRow.length; i++) {
			set(i, r, newRow[i]);
		}
	}

	public int newRow() {
		var r = matrix[0].length;
		resize(matrix.length + 1);
		return r;
	}


}
