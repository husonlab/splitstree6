/*
 *  TridiagonalMatrix.java Copyright (C) 2022 Daniel H. Huson
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

package splitstree6.algorithms.distances.distances2splits.neighbornet.NeighborNetPCG;

import Jama.Matrix;

import java.util.Arrays;
import java.util.Random;

public class TridiagonalMatrix {
	//a,b,c store the diagonal, lower diagonal and upper diagonal.
	//b or c equals null in the case that this is a bidigaonal matrix.
	public int n; //Number of rows = number of columns
	public double[] a;
	public double[] b;
	public double[] c;

	//additional arrays allowing constant time access to inverse elements
	//TODO These could be made private?
	public double[] d;
	public int[] s, t;
	public double[] v, w;

	public TridiagonalMatrix clone() {
		TridiagonalMatrix T = new TridiagonalMatrix();
		T.n = n;
		if (a != null)
			T.a = a.clone();
		if (b != null)
			T.b = b.clone();
		if (c != null)
			T.c = c.clone();

		if (d != null)
			T.d = d.clone();
		if (s != null)
			T.s = s.clone();
		if (t != null)
			T.t = t.clone();
		if (v != null)
			T.v = v.clone();
		if (w != null)
			T.w = w.clone();
		return T;
	}


	/**
	 * solveL
	 * <p>
	 * solves  Mx = y in the case that this matrix is lower bidiagonal (c = null)
	 *
	 * @param y vector
	 * @return x vector solving Mx = y, if M is this matrix.
	 */
	public double[] solveL(double[] y) {
		assert c == null : "Applying solveL to a matrix which is not bidiagonal";
		double[] x = new double[n + 1];

		x[1] = y[1] / a[1];
		for (int i = 2; i <= n; i++)
			x[i] = (y[i] - b[i - 1] * x[i - 1]) / a[i];

		return x;
	}

	/**
	 * solves  Mx = y in the case that this matrix is upper bidiagonal (b = null)
	 *
	 * @param y n dimensional vector with starting index 1
	 * @return n dimensional vector x solving Mx = y,
	 */
	public double[] solveU(double[] y) {
		assert b == null : "Applying solveU to a matrix which is not bidiagonal";
		double[] x = new double[n + 1];

		x[n] = y[n] / a[n];
		for (int i = n - 1; i >= 1; i--)
			x[i] = (y[i] - c[i] * x[i + 1]) / a[i];

		return x;
	}

	/**
	 * Returns the tridiagonal matrix formed by restricting this matrix to rows and columns in I.
	 * <p>
	 * This is trickier than it sounds.
	 *
	 * @param I boolean array, with true values marking out elements if I.
	 * @return new Tridiagonal matrix with |I| rows and columns
	 */
	public TridiagonalMatrix submatrix(boolean[] I) {

		//Count the cardinality of the set
		int nn = 0;
		for (int i = 1; i <= n; i++) {
			if (I[i])
				nn++;
		}

		//Allocate space for arrays in new matrix
		TridiagonalMatrix T2 = new TridiagonalMatrix();
		T2.a = new double[nn + 1];
		if (b != null)
			T2.b = new double[nn]; //1...nn-1.
		if (c != null)
			T2.c = new double[nn];
		T2.n = nn;

		int newi = 0;
		for (int i = 1; i < n; i++) {
			if (I[i]) {
				newi = newi + 1;
				T2.a[newi] = a[i];
				if (I[i + 1]) {
					if (b != null)
						T2.b[newi] = b[i];
					if (c != null)
						T2.c[newi] = c[i];
				}
			}
		}
		if (I[n])
			T2.a[newi + 1] = a[n];

		return T2;
	}

	/**
	 * Constructs an LU decomposition of this matrix.
	 *
	 * @return Array with first element L and second element U, so that this matrix = LU.
	 * L is lower bidiagonal and U is upper bidiagonal.
	 */
	public TridiagonalMatrix[] trilu() {
		TridiagonalMatrix L = new TridiagonalMatrix();
		L.n = n;
		L.a = new double[n + 1];
		Arrays.fill(L.a, 1.0);
		L.a[0] = 0;
		L.b = new double[n];

		TridiagonalMatrix U = new TridiagonalMatrix();
		U.n = n;
		U.a = new double[n + 1];
		U.c = c;

		U.a[1] = a[1];
		for (int i = 2; i <= n; i++) {
			L.b[i - 1] = b[i - 1] / U.a[i - 1];
			U.a[i] = a[i] - L.b[i - 1] * U.c[i - 1];
		}

		return new TridiagonalMatrix[]{L, U};
	}

	/**
	 * Returns T1 - T2.
	 * <p>
	 * Note: in this implementation we assume that a,b,c are non-null for both T1 and T2.
	 *
	 * @param T1 Tridiagonal matrix
	 * @param T2 Tridiagonal matrix with the same dimensions as T1
	 * @return T1-T2
	 */
	public static TridiagonalMatrix minus(TridiagonalMatrix T1, TridiagonalMatrix T2) {
		assert T1.n == T2.n : "Trying to subtract matrices with different sizes";
		int n = T1.n;
		TridiagonalMatrix T3 = new TridiagonalMatrix();
		T3.n = T1.n;
		T3.a = new double[n + 1];
		T3.b = new double[n];
		T3.c = new double[n];

		for (int i = 1; i < n; i++) {
			T3.a[i] = T1.a[i] - T2.a[i];
			T3.b[i] = T1.b[i] - T2.b[i];
			T3.c[i] = T1.c[i] - T2.c[i];
		}
		T3.a[n] = T1.a[n] - T2.a[n];
		return T3;
	}

	/**
	 * Computes product of this matrix and vector x
	 *
	 * @param x vector
	 * @return y, vector = this matrix * x.
	 */
	public double[] multiply(double[] x) {
		double[] y = new double[n + 1];
		for (int i = 1; i <= n; i++) {
			if (i > 1 && b != null)
				y[i] += b[i - 1] * x[i - 1];
			y[i] += a[i] * x[i];
			if (i < n && c != null)
				y[i] += c[i] * x[i + 1];
		}
		return y;
	}

	/**
	 * Multiple two bidigaonal matrices
	 *
	 * @param L Lower bidiagonal matrix
	 * @param U Upper bidigaonal matrix
	 * @return Product L*U
	 */
	public static TridiagonalMatrix multiplyLU(TridiagonalMatrix L, TridiagonalMatrix U) {
		assert L.c == null && U.b == null : "Applying multiplyLU to matrices that are not lower and upper bidiagonal";
		assert L.n == U.n : "Applying multiplyLU to matrices with different sizes";
		int n = L.n;

		TridiagonalMatrix T = new TridiagonalMatrix();
		T.n = n;
		T.a = new double[n + 1];
		T.b = new double[n];
		T.c = new double[n];

		T.a[1] = L.a[1] * U.a[1];
		for (int i = 2; i <= n; i++) {
			T.a[i] = L.b[i - 1] * U.c[i - 1] + L.a[i] * U.a[i];
			T.b[i - 1] = L.b[i - 1] * U.a[i - 1];
			T.c[i - 1] = L.a[i - 1] * U.c[i - 1];
		}
		return T;
	}


	/**
	 * Compute data structures which enable constant time querying of inverse matrix.
	 */
	public void preprocessInverse() {

		if (n == 1) {
			d = new double[2];
			d[1] = 1.0 / a[1];
			return;
		}

		//ttheta[i] = det(M(1:i,1:i))/det(M(1:i-1,1:i-1), with ttheta[1] = a[1]
		double[] ttheta = new double[n + 1];
		ttheta[1] = a[1];
		for (int i = 2; i <= n; i++)
			ttheta[i] = a[i] - b[i - 1] * c[i - 1] / ttheta[i - 1];

		//tphi[i] = det(M(i:n,i:n))/det(M(i+1:n,i+1:n)
		double[] tphi = new double[n + 1];
		tphi[n] = a[n];
		for (int i = n - 1; i >= 1; i--)
			tphi[i] = a[i] - b[i] * c[i] / tphi[i + 1];

		//s[j] gives min value of i<j such that (T^{-1})_{ij} is non-zero.
		//t[i] gives min value of j<i such that (T^{-1})_{ij} is non-zero.
		s = new int[n + 1];
		t = new int[n + 1];
		for (int k = 1; k <= n; k++) {
			if (k == 1 || c[k - 1] == 0)
				s[k] = k;
			else
				s[k] = s[k - 1];
			if (k == 1 || b[k - 1] == 0)
				t[k] = k;
			else
				t[k] = t[k - 1];
		}

		//Evaluate diagonal of T^{-1} directly from Usmani's formula.
		d = new double[n + 1];
		d[n] = 1.0 / ttheta[n];
		for (int i = (n - 1); i >= 1; i--)
			d[i] = tphi[i + 1] * d[i + 1] / ttheta[i];

		//v and w are used in the inverse formula.
		v = new double[n + 1];
		for (int k = 1; k <= n; k++) {
			if (k == 1 || c[k - 1] == 0)
				v[k] = 1;
			else
				v[k] = -c[k - 1] * v[k - 1] / tphi[k];
		}
		w = new double[n + 1];
		for (int k = 1; k <= n; k++) {
			if (k == 1 || b[k - 1] == 0)
				w[k] = 1;
			else
				w[k] = -b[k - 1] * w[k - 1] / tphi[k];
		}
	}

	public double getTinv(int i, int j) {
		if (i == j)
			return d[i];
		else if (i < j && i >= s[j])
			return d[i] * v[j] / v[i];
		else if (i > j && j >= t[i])
			return d[j] * w[i] / w[j];
		else
			return 0.0;
	}

	/**
	 * Convert Tridiagonal matrix to a Jama matrix (for debugging)
	 *
	 * @return Matrix
	 */
	public Matrix toMatrix() {
		Matrix M = new Matrix(n, n);
		for (int i = 1; i < n; i++) {
			M.set(i - 1, i - 1, a[i]);
			if (b != null)
				M.set(i, i - 1, b[i]);
			if (c != null)
				M.set(i - 1, i, c[i]);
		}
		M.set(n - 1, n - 1, a[n]);
		return M;
	}


	public static void test(int n) {
		//Run a collection of tests on randomly generated tridiagonal matrices with n rows and columns

		//Generate a random tridiagonal matrix. Note: test will fail if this happens to be non-singular
		Random rand = new Random();
		TridiagonalMatrix M = new TridiagonalMatrix();
		M.n = n;
		M.a = new double[n + 1];
		for (int i = 1; i <= n; i++)
			M.a[i] = rand.nextDouble();
		M.b = new double[n];
		M.c = new double[n];
		for (int i = 1; i <= n - 1; i++) {
			M.b[i] = rand.nextDouble();
			M.c[i] = rand.nextDouble();
		}
		//Print the matrix
		Matrix MM = M.toMatrix();
		System.err.println(MM.toString());


		//Generate a random vector
		double[] x = new double[n + 1];
		for (int i = 1; i <= n; i++)
			x[i] = rand.nextDouble();

		//Compute y = Mx
		double[] y = M.multiply(x);

		//Carry out LU decomposition
		TridiagonalMatrix[] LU = M.trilu();

		//Solve Lz = y and then U(x2) = z. Compare to x.
		double[] z = LU[0].solveL(y);
		double[] x2 = LU[1].solveU(z);

		double diff = 0.0;
		for (int i = 1; i <= n; i++)
			diff += (x[i] - x2[i]) * (x[i] - x2[i]);
		System.err.println("LU solve, err = " + diff + "\n");

		//Solve the same system M(x3)=y using inverse.
		double[] x3 = new double[n + 1];
		M.preprocessInverse();
		for (int i = 1; i <= n; i++) {
			for (int j = 1; j <= n; j++)
				x3[i] += M.getTinv(i, j) * y[j];
		}

		diff = 0.0;
		for (int i = 1; i <= n; i++)
			diff += (x[i] - x3[i]) * (x[i] - x3[i]);

		System.err.println("Tridiagonal inverse solve, err = " + diff + "\n");
	}


}
