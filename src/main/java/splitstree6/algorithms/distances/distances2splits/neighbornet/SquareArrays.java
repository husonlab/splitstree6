/*
 *  SquareArrays.java Copyright (C) 2024 Daniel H. Huson
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
 */

package splitstree6.algorithms.distances.distances2splits.neighbornet;

import java.util.Arrays;

import static java.lang.Math.min;

/**
 * A collection of utility routines for handling 2D symmetric arrays.
 * In all of these routines, rows and columns with index 0 are ignored (for consistency with Matlab)
 **/

public class SquareArrays {

	/**
	 * Fill the array with the same values
	 *
	 * @param x   square array, overwritten
	 * @param val value used to fill x.
	 */
	static public void fill(double[][] x, double val) {
		int n = x.length - 1;
		for (int i = 1; i <= n; i++) {
			Arrays.fill(x[i], 1, n, val);
			x[i][i] = 0;
		}
	}

	/**
	 * Set a subset of elements of an array to zero
	 *
	 * @param r square array of double  (ignore 0 indexed rows and columns)
	 * @param A square array of boolean (ditto)
	 *          <p>
	 *          set r[i][j] = 0 whenever A[i][j] is true.
	 */
	static public void maskElements(double[][] r, boolean[][] A) {
		int n = r.length - 1;
		for (int i = 1; i <= n; i++)
			for (int j = 1; j <= n; j++)
				if (A[i][j])
					r[i][j] = 0;
	}

	/**
	 * Checks if A is empty (all entries set to false).
	 *
	 * @param A square array of boolean
	 * @return true if A is empty, false otherwise
	 */
	static public boolean isEmpty(boolean[][] A) {
		int n = A.length - 1;
		for (int i = 1; i <= n; i++)
			for (int j = i + 1; j <= n; j++)
				if (A[i][j])
					return false;
		return true;
	}

	/**
	 * Fill a boolean array indicating the elements of an array which are identically zero.
	 *
	 * @param x square array of doubles
	 * @param A overwrites square array of boolean, with A[i][j] = (x[i][j]==0);
	 */
	static void getZeroElements(double[][] x, boolean[][] A) {
		int n = x.length - 1;
		for (int i = 1; i <= n; i++)
			for (int j = 1; j <= i; j++)
				A[i][j] = A[j][i] = (x[i][j] == 0);
	}

	/**
	 * Returns a boolean array indicating the elements of an array which are identically zero.
	 *
	 * @param x square array of doubles
	 * @return square array of boolean, with A[i][j] = (x[i][j]==0);
	 */
	static boolean[][] getZeroElements(double[][] x) {
		int n = x.length - 1;
		boolean[][] A = new boolean[n + 1][n + 1];
		getZeroElements(x, A);
		return A;
	}

	/**
	 * Make all negative entries zero.
	 *
	 * @param x square array
	 */
	static void makeNegElementsZero(double[][] x) {
		int n = x.length - 1;
		double minVal = Double.MAX_VALUE;

		for (int i = 1; i <= n; i++)
			for (int j = i + 1; j <= n; j++) {
				minVal = min(minVal, x[i][j]);
				x[i][j] = x[j][i] = Math.max(x[i][j], 0);
			}
	}

	/**
	 * Sum the squares of entries of an array
	 *
	 * @param x Square array of doubles
	 * @return double sum of the squares of entries in x
	 */
	static public double sumArraySquared(double[][] x) {
		double total = 0.0;
		int n = x.length - 1;
		double si;
		for (int i = 1; i <= n; i++) {
			si = 0.0;
			for (int j = 1; j <= n; j++) {
				double x_ij = x[i][j];
				si += x_ij * x_ij;
			}
			total += si;
		}
		return total;
	}

	/**
	 * Find the minimum value of an entry in a square array
	 *
	 * @param x square array of double
	 * @return double min_ij  x_ij
	 */
	static public double minArray(double[][] x) {
		double minx = 0.0;
		int n = x.length - 1;
		for (int i = 1; i <= n; i++)
			for (int j = i + 1; j <= n; j++)
				minx = min(minx, x[i][j]);
		return minx;
	}


	/**
	 * Copy elements from one square array to another of the same size.
	 *
	 * @param from square array
	 * @param to   square array (assumed to be allocated already)
	 */
	static public void copyArray(double[][] from, double[][] to) {
		int n = from.length - 1;
		for (int i = 1; i <= n; i++) {
			System.arraycopy(from[i], 1, to[i], 1, n);
		}
	}

	static public void copyArray(double[] from, double[] to) {
		System.arraycopy(from, 0, to, 0, to.length);
	}

	/**
	 * Return the sum of squares of entries in the matrix
	 *
	 * @param x square array of doubles
	 * @return double, sum of squares
	 */
	static public double sumSquares(double[][] x) {
		int n = x.length - 1;
		var total = 0.0;
		for (int i = 1; i <= n; i++) {
			for (int j = i + 1; j <= n; j++) {
				total += x[i][j] * x[i][j];
			}
		}
		return 2.0 * total;
	}

	/**
	 * Count the number of true entries in the top triangle of the array
	 *
	 * @param A square boolean array
	 * @return number of true entries in upper triangle (excluding diagonal).
	 */
	static public int countTrueEntries(boolean[][] A) {
		int n = A.length - 1;
		int count = 0;
		for (int i = 1; i <= n; i++)
			for (int j = i + 1; j <= n; j++)
				if (A[i][j])
					count++;
		return count;
	}

	/**
	 * Return the sum of squared differences between two arrays of the same size (using
	 * lower triangular parts only)
	 *
	 * @param x square array
	 * @param y square array
	 * @return sum_{i<j} (x[i][j] - y[i][j])^2
	 */
	static public double diff(double[][] x, double[][] y) {
		int n = x.length - 1;
		double d = 0.0;
		for (int i = 1; i <= n; i++)
			for (int j = i + 1; j <= n; j++)
				d += (x[i][j] - y[i][j]) * (x[i][j] - y[i][j]);
		return d;
	}

}
