/*
 * MutableD.java Copyright (C) 2025 Daniel H. Huson
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

/**
 * Growable, symmetric distance matrix with safe append and copy-out.
 */
final class MutableD {
	private double[][] D;

	public MutableD(double[][] initial) {
		this.D = copyOf(initial);
	}

	/**
	 * number of vertices (rows/cols)
	 */
	public int size() {
		return D.length;
	}

	/**
	 * get/set keep symmetry
	 */
	public double get(int i, int j) {
		return D[i][j];
	}

	public void set(int i, int j, double v) {
		D[i][j] = v;
		D[j][i] = v;
	}

	/**
	 * Append a new vertex x' with distances cand[0..n-1] to existing vertices (and 0 to itself).
	 *
	 * @return the index of the appended vertex (old size)
	 */
	public int appendVertex(double[] cand) {
		final int n = D.length;
		final double[][] R = new double[n + 1][n + 1];
		for (int i = 0; i < n; i++) System.arraycopy(D[i], 0, R[i], 0, n);
		for (int i = 0; i < n; i++) {
			R[i][n] = cand[i];
			R[n][i] = cand[i];
		}
		R[n][n] = 0.0;
		D = R;
		return n;
	}

	/**
	 * returns a deep copy snapshot of the current matrix
	 */
	public double[][] toArray() {
		return copyOf(D);
	}

	/* ---- small local copy util to keep this class standalone ---- */
	private static double[][] copyOf(double[][] A) {
		final int n = A.length;
		final double[][] R = new double[n][n];
		for (int i = 0; i < n; i++) System.arraycopy(A[i], 0, R[i], 0, n);
		return R;
	}

	public double[][] getD() {
		return D;
	}
}