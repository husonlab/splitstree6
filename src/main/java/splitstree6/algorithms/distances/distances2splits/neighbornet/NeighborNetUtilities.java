/*
 *  NeighborNetUtilities.java Copyright (C) 2024 Daniel H. Huson
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

import jloda.util.CanceledException;

import java.util.Random;

import static java.lang.Math.*;
import static splitstree6.algorithms.distances.distances2splits.neighbornet.NeighborNetSplitWeightsClean.IPG;

public class NeighborNetUtilities {


	static private void vec2array(double[] x, double[][] X) {
		int n = X.length - 1;
		int index = 0;
		for (int i = 1; i <= n; i++)
			for (int j = i + 1; j <= n; j++) {
				X[i][j] = x[index];
				index++;
			}
	}

	static private void array2vec(double[][] X, double[] x) {
		int n = X.length - 1;
		int index = 0;
		for (int i = 1; i <= n; i++)
			for (int j = i + 1; j <= n; j++) {
				x[index] = X[i][j];
				index++;
			}
	}


	/**
	 * Computes circular distances from an array of split weights.
	 *
	 * @param x split weights. Symmetric array. For i<j, x(i,j)  is the weight of the
	 *          split {i,i+1,...,j-1} | rest.
	 * @param y square array, overwritten with circular metric corresponding to these split weights.
	 */
	static public void calcAx(double[][] x, double[][] y) {
		var n = x.length - 1;

		for (var i = 1; i <= (n - 1); i++) {
			var s = 0.0;
			for (var j = i + 2; j <= n; j++)
				s += x[i + 1][j];
			for (var j = 1; j <= i; j++)
				s += x[j][i + 1];
			y[i][i + 1] = s;
		}

		for (var i = 1; i <= (n - 2); i++) {
			y[i + 2][i] = y[i][i + 2] = y[i][i + 1] + y[i + 1][i + 2] - 2 * x[i + 1][i + 2];
		}

		for (var k = 3; k <= n - 1; k++) {
			for (var i = 1; i <= n - k; i++) {  //TODO. This loop can be threaded, but it is not worth it
				var j = i + k;
				y[i][j] = y[i][j - 1] + y[i + 1][j] - y[i + 1][j - 1] - 2 * x[i + 1][j];
			}
		}
	}


	static public void calcAx(double[] x, double[] y, int n) {
		//  double[][] X = new double[n + 1][n + 1];
		// double[][] Y = new double[n + 1][n + 1];
		// double[] y2 = new double[x.length];

		// vec2array(x,X);
		//  calcAx(X, Y);
		//  array2vec(Y,y2);

		//Converted code
		var s_index = 0;
		for (var i = 1; i < n; i++) {
			//y[i][i+1] = \sum_{j=i+2}^n x[i+1][j]   + \sum_{j=1}^i x[j][i+1]
			double y_s = 0.0;
			var d_index = (2 * n - i - 1) * i / 2;
			for (int j = i + 2; j <= n; j++) {
				y_s += x[d_index];
				d_index++;
			}
			d_index = i - 1;
			for (int j = 1; j <= i; j++) {
				y_s += x[d_index];
				d_index += n - j - 1;
			}
			y[s_index] = y_s;
			s_index += n - i;
		}

		s_index = 1; //(1,3)
		for (var i = 1; i <= n - 2; i++) {
			//y[i][i + 2] = y[i][i + 1] + y[i + 1][i + 2] - 2 * x[i + 1][i + 2];
			y[s_index] = y[s_index - 1] + y[s_index + n - i - 1] - 2 * x[s_index + n - i - 1];
			s_index += n - i;
		}

		for (var k = 3; k <= n - 1; k++) {
			s_index = (k - 1); //(1,k+1)
			for (var i = 1; i <= n - k; i++) {
				//y[i][j] = y[i][j - 1] + y[i + 1][j] - y[i + 1][j - 1] - 2 * x[i + 1][j];
				y[s_index] = y[s_index - 1] + y[s_index + n - i - 1] - y[s_index + n - i - 2] - 2 * x[s_index + n - i - 1];
				s_index += n - i;
			}
		}
		// System.err.println("Error = "+diff(y,y2));

	}


	/**
	 * Compute Atx, when x and the result are represented as square arrays
	 *
	 * @param x square array
	 * @param y square array. Overwritten with result
	 */
	static public void calcAtx(double[][] x, double[][] y) {
		var n = x.length - 1;
		//double[][] y = new double[n+1][n+1];

		for (var i = 1; i <= n - 1; i++) {
			var s = 0.0;
			for (var j = 1; j < i; j++)
				s += x[j][i];
			for (var j = i + 1; j <= n; j++)
				s += x[i][j];
			y[i][i + 1] = s;
		}

		for (var i = 1; i <= n - 2; i++) {  //TODO This can be threaded, but is not worth it
			y[i][i + 2] = y[i][i + 1] + y[i + 1][i + 2] - 2 * x[i][i + 1];
		}

		for (var k = 3; k <= n - 1; k++) {
			for (var i = 1; i <= n - k; i++) { //TODO. This inner loop can be threaded, but is not worth it
				y[i][i + k] = y[i][i + k - 1] + y[i + 1][i + k] - y[i + 1][i + k - 1] - 2 * x[i][i + k - 1];
			}
		}
	}

	static public void calcAtx(double[] x, double[] y, int n) {
       /* double[][] X = new double[n + 1][n + 1];
        double[][] Y = new double[n + 1][n + 1];
        double[] y2 = new double[n*(n-1)/2];
        vec2array(x,X);
        calcAtx(X, Y);
        array2vec(Y,y2);*/

		int s_index = 0;
		for (int i = 1; i < n; i++) {
			//y[i][i+1] = \sum_{j=1}^{i-1} x[j][i] + \sum_{j=i+1}^n x[i][j];
			int d_index = i - 2;  //(1,i)
			double y_s = 0.0;
			for (int j = 1; j < i; j++) {
				y_s += x[d_index];
				d_index += n - j - 1;
			}
			d_index = s_index; //(i,i+1)
			for (int j = i + 1; j <= n; j++) {
				y_s += x[d_index];
				d_index++;
			}
			y[s_index] = y_s;
			s_index += n - i;
		}

		s_index = 1;
		for (int i = 1; i <= n - 2; i++) {
			//y[i][i + 2] = y[i][i + 1] + y[i + 1][i + 2] - 2 * x[i][i + 1];
			y[s_index] = y[s_index - 1] + y[s_index + n - i - 1] - 2 * x[s_index - 1];
			s_index += n - i;
		}

		for (int k = 3; k <= n - 1; k++) {
			s_index = k - 1; //(1,1+k)
			for (int i = 1; i <= n - k; i++) {
				y[s_index] = y[s_index - 1] + y[s_index + n - i - 1] - y[s_index + n - i - 2] - 2 * x[s_index - 1];
				s_index += n - i;
			}
		}
		//System.err.println("Error = "+diff(y,y2));


	}


	/**
	 * calcAinv_y
	 * <p>
	 * Computes A^{-1}(y).
	 * <p>
	 * When y is circular, result will be corresponding weights. If y is not circular, some
	 * elements will be negative.
	 *
	 * @param y square array
	 * @param x square array, overwritten by A^{-1}y.
	 */
	static public void calcAinv_y(double[][] y, double[][] x) {
		var n = y.length - 1;
		x[1][2] = (y[1][n] + y[1][2] - y[2][n]) / 2.0;
		for (var j = 3; j <= n - 1; j++) {
			x[1][j] = (y[j - 1][n] + y[1][j] - y[1][j - 1] - y[j][n]) / 2.0;
		}
		x[1][n] = (y[1][n] + y[n - 1][n] - y[1][n - 1]) / 2.0;

		for (var i = 2; i <= (n - 1); i++) {
			x[i][i + 1] = (y[i - 1][i] + y[i][i + 1] - y[i - 1][i + 1]) / 2.0;
			for (var j = (i + 2); j <= n; j++)
				x[i][j] = (y[i - 1][j - 1] + y[i][j] - y[i][j - 1] - y[i - 1][j]) / 2.0;
		}
	}


	//Temporary wrapper
	static public void calcAinv_y(double[] y, double[] x, int n) {
        /*double[][] X = new double[n + 1][n + 1];
        double[][] Y = new double[n + 1][n + 1];
        double[] x2 = new double[n*(n-1)/2];

        vec2array(y,Y);
        calcAinv_y(Y, X);
        array2vec(X,x2);*/

		x[0] = (y[n - 2] + y[0] - y[2 * n - 4]) / 2.0; //x[1][2] = (y[1][n] + y[1][2] - y[2][n]) / 2.0;
		int s_index = 1;
		int d_index = 2 * n - 4; //(2,n)
		for (int j = 3; j <= n; j++) {
			//x[1][j] = (y[j - 1][n] + y[1][j] - y[1][j - 1] - y[j][n]) / 2.0;
			x[j - 2] = (y[d_index] + y[j - 2] - y[j - 3] - y[d_index + n - j]) / 2.0;
			d_index += n - j; //(j,n);
		}
		x[n - 2] = (y[n - 2] + y[y.length - 1] - y[n - 3]) / 2.0;  //x[1][n] =  (y[1][n] + y[n - 1][n] - y[1][n - 1]) / 2.0;


		for (int i = 2; i <= (n - 1); i++) {
			s_index = (2 * n - i) * (i - 1) / 2; //(i,i+1)
			x[s_index] = (y[s_index + i - n - 1] + y[s_index] - y[s_index + i - n]) / 2; //x[i][i + 1] = (y[i - 1][i] + y[i][i + 1] - y[i - 1][i + 1]) / 2.0;
			s_index++; //(i,i+2)
			for (int j = (i + 2); j <= n; j++) {
				//x[i][j] =  (y[i - 1][j - 1] + y[i][j] - y[i][j - 1] - y[i - 1][j]) / 2.0;
				x[s_index] = (y[s_index + i - n - 1] + y[s_index] - y[s_index - 1] - y[s_index + i - n]) / 2.0;
				s_index++;
			}
		}

		//System.err.println("Error = "+diff(x,x2));
	}

	/**
	 * size
	 * <p>
	 * Returns number of entries in lower triangular of matrix which are set true.
	 *
	 * @param s boolean square matrix
	 * @return number of entries
	 */
	static public int cardinality(boolean[][] s) {
		int n = s.length - 1;
		int count = 0;
		for (int i = 1; i <= n; i++)
			for (int j = i + 1; j <= n; j++)
				if (s[i][j])
					count++;
		return count;
	}

	static public int cardinality(boolean[] s) {
		int count = 0;
		for (int i = 0; i < s.length; i++)
			if (s[i])
				count++;
		return count;
	}


	/**
	 * Compute the gradient at x of 1/2 ||Ax - d||
	 *
	 * @param x        square array
	 * @param d        square array
	 * @param gradient square array, overwritten by the gradient.
	 */
	static public void evalGradient(double[][] x, double[][] d, double[][] gradient) {
		var n = x.length - 1;
		var res = new double[n + 1][n + 1];
		calcAx(x, res);
		for (var i = 1; i <= n; i++)
			for (var j = i + 1; j <= n; j++)
				res[i][j] -= d[i][j];
		calcAtx(res, gradient);
	}

	static public void evalGradient(double[] x, double[] d, double[] gradient, double[] residual, int n) {
		calcAx(x, residual, n);
		for (var i = 0; i < residual.length; i++)
			residual[i] -= d[i];
		calcAtx(residual, gradient, n);
	}

	/**
	 * Scale entries in x by lambda
	 *
	 * @param x      doubl array
	 * @param lambda double
	 *               Overwrite x with lambda*x
	 */
	static public void scale(double[][] x, double lambda) {
		var n = x.length - 1;
		for (var i = 1; i <= n; i++)
			for (var j = i + 1; j <= n; j++)
				x[i][j] *= lambda;
	}

	static public void scale(double[] x, double lambda) {
		for (var i = 0; i < x.length; i++)
			x[i] *= lambda;
	}


	/**
	 * Computes the square of the norm of the projected gradient. This is inefficient and should
	 * probably only be used for development and debugging.
	 *
	 * @param x square array
	 * @param d square array
	 * @return square of the norm of the projected gradient
	 */
	static public double evalProjectedGradientSquared(double[][] x, double[][] d) {
		int n = x.length - 1;
		double[][] grad = new double[n + 1][n + 1];

//        System.err.print("X=");
//        for(int i=1;i<=n;i++)
//            for(int j=i+1;j<=n;j++)
//                System.err.print(x[i][j]+", ");
//        System.err.println();


		evalGradient(x, d, grad);

//        System.err.print("Grad=");
//        for(int i=1;i<=n;i++)
//            for(int j=i+1;j<=n;j++)
//                System.err.print(grad[i][j]+", ");
//        System.err.println();


		double pg = 0.0;
		for (int i = 1; i <= n; i++) {
			double row_i = 0.0;
			for (int j = i + 1; j <= n; j++) {
				double grad_ij = grad[i][j];
				if (x[i][j] > 0.0 || grad_ij < 0.0)
					row_i += grad_ij * grad_ij;
			}
			pg += row_i;
		}
		return pg;
	}

	static public double evalProjectedGradientSquared(double[] x, double[] d, double[] grad, double[] residual, int n) {
		evalGradient(x, d, grad, residual, n);

		double pg = 0.0;
		int index = 0;
		for (int i = 1; i <= n; i++) {
			double row_i = 0.0;
			for (int j = i + 1; j <= n; j++) {
				double grad_i = grad[index];
				if (x[index] > 0.0 || grad_i < 0.0)
					row_i += grad_i * grad_i;
				index++;
			}
			pg += row_i;
		}
		return pg;
	}

	/**
	 * Fill a boolean array indicating the elements of an array which are not positive
	 *
	 * @param x square array of doubles. Negative entries replaced by zeros.
	 * @param A overwrites square array of boolean, with A[i][j] = (x[i][j] <=0);
	 */
	static void getActiveEntries(double[][] x, boolean[][] A) {
		int n = x.length - 1;
		for (int i = 1; i <= n; i++)
			for (int j = i + 1; j <= n; j++) {
				if (x[i][j] <= 0.0) {
					if (A != null)
						A[i][j] = true;
					x[i][j] = 0.0;
				} else if (A != null)
					A[i][j] = false;
			}
	}

	static void getActiveEntries(double[] x, boolean[] A) {
		for (int i = 0; i < x.length; i++) {
			if (x[i] <= 0.0) {
				if (A != null)
					A[i] = true;
				x[i] = 0.0;
			} else if (A != null)
				A[i] = false;
		}
	}

	/**
	 * Replaces negative entries of a square matrix with zeros
	 *
	 * @param x square array
	 */
	static void zeroNegativeEntries(double[][] x) {
		getActiveEntries(x, null);
	}

	static void zeroNegativeEntries(double[] x) {
		getActiveEntries(x, null);
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
			for (int j = i + 1; j <= n; j++)
				if (A[i][j])
					r[i][j] = 0;
	}

	static public void maskElements(double[] r, boolean[] A) {
		for (int i = 0; i < A.length; i++)
			if (A[i])
				r[i] = 0;
	}


	/**
	 * Replace values in v with absolute value less than val with zeros.
	 *
	 * @param v   square array, overwritten
	 * @param val cutoff value.
	 */
	static void threshold(double[][] v, double val) {
		int n = v.length - 1;
		for (var i = 1; i <= n; i++)
			for (var j = i + 1; j <= n; j++)
				if (abs(v[i][j]) < val)
					v[i][j] = 0.0;
	}

	static void threshold(double[] v, double val) {
		for (var i = 0; i < v.length; i++)
			if (abs(v[i]) < val)
				v[i] = 0.0;
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

	static public double minArray(double[] x) {
		//TODO There must be standard code for this.
		double minx = 0.0;
		for (int i = 0; i < x.length; i++)
			minx = min(minx, x[i]);
		return minx;
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
			for (int j = i + 1; j <= n; j++) {
				double x_ij = x[i][j];
				si += x_ij * x_ij;
			}
			total += si;
		}
		return total;
	}

	static public double sumArraySquared(double[] x, int n) {
		double total = 0.0;
		int index = 0;
		for (int i = 1; i <= n; i++) {
			double s_i = 0.0;
			for (int j = i + 1; j <= n; j++) {
				double x_ij = x[index];
				s_i += x_ij * x_ij;
				index++;
			}
			total += s_i; //More stable if we sum each row separately and then add row sums.
		}
		return total;
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
		double df = 0.0;
		for (int i = 1; i <= n; i++)
			for (int j = i + 1; j <= n; j++) {
				double res_ij = (x[i][j] - y[i][j]);
				df += res_ij * res_ij;
			}
		return df;
	}

	static public double diff(double[] x, double[] y) {
		double total = 0.0;
		for (int i = 0; i < x.length; i++) {
			double d_i = x[i] - y[i];
			total += d_i * d_i;
		}
		return total;
	}


	/**
	 * Count the number of non-zero entries.
	 *
	 * @param x square array
	 * @return Number of non-zero entries in the upper triangle of x.
	 */
	static public int numberNonzero(double[][] x) {
		int n = x.length - 1;
		int count = 0;
		for (int i = 1; i <= n; i++)
			for (int j = i + 1; j <= n; j++) {
				if (x[i][j] != 0.0)
					count++;
			}
		return count;
	}


	static public int numberNonzero(double[] x) {
		int count = 0;
		for (int i = 0; i < x.length; i++)
			if (x[i] != 0.0)
				count++;
		return count;
	}


	/**
	 * Compute the residual ||Ax-d||^2
	 *
	 * @param x square array
	 * @param d square array
	 * @return sum of squared residual
	 */
	static public double evalResidual(double[][] x, double[][] d) {
		int n = x.length - 1;
		double[][] Ax = new double[n + 1][n + 1];
		calcAx(x, Ax);
		return diff(Ax, d);
	}

	static public double evalResidual(double[] x, double[] d, double[] Ax, int n) {
		calcAx(x, Ax, n);
		return diff(Ax, d);
	}

	static public void rand(double[] x, Random generator) {
		for (int i = 0; i < x.length; i++)
			x[i] = generator.nextDouble();
	}

	public static void main(String[] args) {
		int n = 200;
		int npairs = n * (n - 1) / 2;
		Random generator = new Random(1000);
		for (int r = 0; r < 1; r++) {
			double[] x = new double[npairs];
			double[] d = new double[npairs];
			rand(x, generator);
			rand(d, generator);
			double[] Atd = new double[npairs];
			calcAtx(d, Atd, n);
			double normAtd = sqrt(sumArraySquared(Atd, n));


			double[][] X = new double[n + 1][n + 1];
			double[][] D = new double[n + 1][n + 1];
			vec2array(x, X);
			vec2array(d, D);
			boolean[] active = new boolean[npairs];
			for (int i = 0; i < active.length; i++)
				active[i] = (generator.nextDouble() < 0.6);
			int index = 0;
			boolean[][] Active = new boolean[n + 1][n + 1];
			for (int i = 1; i <= n; i++)
				for (int j = i + 1; j <= n; j++) {
					Active[i][j] = active[index];
					index++;
				}
			NeighborNetSplitWeightsClean.NNLSParams params = new NeighborNetSplitWeightsClean.NNLSParams();
			params.cgnrIterations = n * (n - 1) / 2;
			params.cgnrTolerance = (0.0001 * normAtd) * (0.0001 * normAtd) / 10;
			params.cgnrPrintResiduals = false;
			params.projGradBound = params.cgnrTolerance;

			params.maxIterations = 20000;
			params.gcp_ke = 0.1;
			params.gcp_ku = 0.2;
			params.gcp_kl = 0.8;
			params.cgnrIterations = max(50, n * (n - 1) / 2);
			params.activeSetRho = 0.4;

			params.APGDtheta = 0.5;
			double[] x2 = new double[npairs];

			long before, oldTime = 0, newTime = 0;
			try {
//                before = System.currentTimeMillis();
//                activeSetMethod(X,D,params,null);
//                oldTime = System.currentTimeMillis() - before;
//
//                before = System.currentTimeMillis();
//                activeSetMethod(x,d,n,params,null);
//                newTime = System.currentTimeMillis()-before;
//
//                array2vec(X,x2);


				before = System.currentTimeMillis();
				//activeSetMethod(X,D,params,null);
				//gradientProjection(X,D,params,null);
				//APGD(X,D,params,null);
				IPG(X, D, params, null);
				oldTime = System.currentTimeMillis() - before;

				before = System.currentTimeMillis();
				//gradientProjection(x,d,n,params,null);
				//APGD(x,d,n,params,null);
				//activeSetMethod(x,d,n,params,null);
				IPG(x, d, n, params, null);
				newTime = System.currentTimeMillis() - before;

				array2vec(X, x2);
				//System.err.println("GRAD PROJECTION Difference = "+diff(x,x2)+"\told time = "+oldTime+"\tnewTime = "+newTime);
				//System.err.println("APGD SET Difference = "+diff(x,x2)+"\told time = "+oldTime+"\tnewTime = "+newTime);
				System.err.println("IPG SET Difference = " + diff(x, x2) + "\told time = " + oldTime + "\tnewTime = " + newTime);


			} catch (CanceledException e) {
				System.err.println("Cancelled");
			}


		}
	}
}
