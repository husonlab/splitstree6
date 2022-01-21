/*
 * SparseRowMatrix.java Copyright (C) 2022 Daniel H. Huson
 *
 * (Some files contain contributions from other authors, who are then mentioned separately.)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package splitstree6.algorithms.distances.distances2splits.neighbornet.NeighborNetPCG;

import Jama.Matrix;

/**
 * Implements a data structure for matrices where the number of nonzero entries in each row is bounded by a constant.
 */
public class SparseRowMatrix {
	public int m, n, N;
	public int[][] ind; //Indices of nonzero entries in each row
	public double[][] val; //Corresponding values

	/**
	 * Construct an all-zero matrix
	 *
	 * @param m number of rows
	 * @param n number of columns
	 * @param N max number of non-zero entries in a row
	 */
	public SparseRowMatrix(int m, int n, int N) {
		this.m = m;
		this.n = n;
		this.N = N;
		ind = new int[m + 1][N + 1];
		val = new double[m + 1][N + 1];
	}

	/**
	 * Computes this matrix times a vector x
	 *
	 * @param x n dim vector
	 * @return m dim vector
	 */
	public double[] multiply(double[] x) {
		double[] y = new double[m + 1];
		for (int i = 1; i <= m; i++) {
			for (int k = 1; k <= N; k++) {
				int j = ind[i][k];
				if (j != 0)
					y[i] += val[i][k] * x[j];
			}
		}
		return y;
	}

	/**
	 * Computes the difference between two SparseRowMatrices. Only computes the first N entries in each row.
	 * Computation is made a bit tricky by the fact that each matrix will have non-zero entries in different positions.
	 *
	 * @param S1 SparseRowMatrix
	 * @param S2 SparseRowMatrix
	 * @param N  Max number of non-zero entries in each row of the resulting matrix (will truncate to achieve this)
	 * @return S1-S2  SparseRowMatrix
	 */
	public static SparseRowMatrix minus(SparseRowMatrix S1, SparseRowMatrix S2, int N) {
		assert S1.m == S2.m && S1.n == S2.n : "Trying to subtract sparse matrices with different dimensions";

		SparseRowMatrix S = new SparseRowMatrix(S1.m, S1.n, N);

		for (int i = 1; i <= S1.m; i++) {
			// We have sparse rows for S1 and S2. These need to be merged.

			//Count the number of non-zero entries in each row.
			int N1 = 0;
			while (N1 < S1.N && S1.ind[i][N1 + 1] != 0)
				N1++;

			int N2 = 0;
			while (N2 < S2.N && S2.ind[i][N2 + 1] != 0)
				N2++;

			int nnz = 0;
			int k1 = 1;
			int k2 = 1;

			while (nnz < N && k1 <= N1 && k2 <= N2) {
				int j1 = S1.ind[i][k1];
				int j2 = S2.ind[i][k2];
				nnz = nnz + 1;
				if (j1 < j2) {
					S.ind[i][nnz] = j1;
					S.val[i][nnz] = S1.val[i][k1];
					k1++;
				} else if (j1 == j2) {
					S.ind[i][nnz] = j1;
					S.val[i][nnz] = S1.val[i][k1] - S2.val[i][k2];
					k1++;
					k2++;
				} else {
					S.ind[i][nnz] = j2;
					S.val[i][nnz] = -S2.val[i][k2];
					k2++;
				}
			}

			while (nnz < N && k1 <= N1) {
				nnz++;
				S.ind[i][nnz] = S1.ind[i][k1];
				S.val[i][nnz] = S1.val[i][k1];
				k1++;
			}
			while (nnz < N && k2 <= N2) {
				nnz++;
				S.ind[i][nnz] = S2.ind[i][k2];
				S.val[i][nnz] = -S2.val[i][k2];
				k2++;
			}
		}
		return S;
	}


	/**
	 * Multiple a vector by the transpose of this matrix
	 *
	 * @param x m dimensional vector
	 * @return y = M^T x, and n-dimensional vector
	 */
	public double[] multiplyTranspose(double[] x) {
		double[] y = new double[n + 1];
		for (int i = 1; i <= m; i++) {
			double xi = x[i];
			for (int k = 1; k <= N; k++) {
				int j = ind[i][k];
				if (j != 0)
					y[j] += val[i][k] * xi;
			}
		}
		return y;
	}

	/**
	 * Returns the induced submatrix of this matrix, with rows and columns restricted to those in I and J.
	 *
	 * @param I array of boolean - rows to select
	 * @param J array of boolean - columns to select
	 * @return SparseRowMatrix   the |I|x|J| submatrix.
	 */
	public SparseRowMatrix submatrix(boolean[] I, boolean[] J) {

		//Construct a map from full column indices to restricted column indices.
		int[] mapJ = new int[n + 1];
		int newj = 0;
		for (int j = 1; j < J.length; j++) {
			if (J[j]) {
				newj = newj + 1;
				mapJ[j] = newj;
			}
		}

		int sumJ = newj;
		int sumI = 0;
		for (int i = 1; i < I.length; i++)
			if (I[i])
				sumI++;

		SparseRowMatrix S2 = new SparseRowMatrix(sumI, sumJ, N);

		int newi = 0;
		for (int i = 1; i <= m; i++) {
			if (I[i]) {
				newi = newi + 1;

				int newk = 0;
				for (int k = 1; k <= N; k++) {
					int j = ind[i][k];
					if (j > 0 && J[j]) {
						newk = newk + 1;
						S2.ind[newi][newk] = mapJ[j];
						S2.val[newi][newk] = val[i][k];
					}
				}
			}
		}
		return S2;
	}

	/**
	 * Convert sparse row matrix into a dense JAMA matrix
	 *
	 * @return Matrix
	 */
	public Matrix toMatrix() {
		Matrix M = new Matrix(m, n);
		for (int i = 1; i <= m; i++) {
			for (int k = 1; k <= N; k++) {
				if (ind[i][k] != 0) {
					int j = ind[i][k];
					M.set(i - 1, j - 1, val[i][k]);
				}
			}
		}
		return M;
	}


}
