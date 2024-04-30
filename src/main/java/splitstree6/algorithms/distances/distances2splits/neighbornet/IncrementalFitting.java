/*
 *  IncrementalFitting.java Copyright (C) 2024 Daniel H. Huson
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


import org.apache.commons.math4.legacy.analysis.UnivariateFunction;
import org.apache.commons.math4.legacy.optim.MaxEval;
import org.apache.commons.math4.legacy.optim.nonlinear.scalar.GoalType;
import org.apache.commons.math4.legacy.optim.univariate.BrentOptimizer;
import org.apache.commons.math4.legacy.optim.univariate.SearchInterval;
import org.apache.commons.math4.legacy.optim.univariate.UnivariateObjectiveFunction;

import static java.lang.Math.*;

public class IncrementalFitting {
	/**
	 * incrementalFitting
	 * <p>
	 * Implements a rough but rapid heuristic for estimating split weights for splits compatible with a given circular
	 * ordering (assumed 1,2,....,n).
	 * Algorithm works by sorting the taxa so that taxa coming earlier in the order are more different from each other
	 * (see maxDivergenceOrder). Taxa are then inserted one by one back into the order and the weights for the splits
	 * created after each insertion are optimized, subject to the condition that the distances in the network between
	 * taxa already inserted are unchanged. The algorithm takes advantage of the fact the the splits created by
	 * inserting a single taxon have a special structure, allowing O(n) time updates of the weights each insertion
	 * (with some heuristics used to enforce non-negativity).
	 * <p>
	 * The method is consistent: when the original metric d is circular with circular ordering 1,2,...,n the split
	 * algorithm returns (with exact arithmetic) the corresponding split weights. The algorithm runs in approx
	 * O(n^2 log n) time, the log n coming from the golden section search used for the projective gradient step.
	 * <p>
	 * TODO: Replace golden section search with an Apache implementation of Brent's method.
	 *
	 * @param d   Square (symmetric) array of distances
	 * @param x   Square array, overwritten with the solution
	 * @param tol Tolerance in split weight error. Used to determine convergence of the golden section method.
	 */
	static public void incrementalFitting(double[][] x, double[][] d, double tol, boolean search) {
		int n = d.length - 1; //ntax
		double[][] p = new double[n + 1][n + 1];

		double[][] grad = new double[n + 1][n + 1];
		double[][] res = new double[n + 1][n + 1];


		int[] s = maxDivergenceOrder(d);
		int[] cycle = new int[n + 1]; //Circular ordering for taxa already placed - in increasing order

		//At any iteration k, the only non-zero splits correspond to x[s_i]x[s_j] for i,j \leq and the
		//only non-zero distances are p[s_i][s_j].
		//

		//First two taxa
		int s1 = s[1], s2 = s[2];
		x[s1][s2] = x[s2][s1] = d[s1][s2];
		p[s1][s2] = p[s2][s1] = d[s1][s2];
		cycle[1] = min(s1, s2);
		cycle[2] = max(s1, s2);

		for (int k = 3; k <= n; k++) {

			//DEBUG
			if (false) {
				double maxdiff = 0.0;
				for (int i = 1; i < k; i++)
					for (int j = i + 1; j < k; j++)
						maxdiff = max(maxdiff, abs(p[cycle[i]][cycle[j]] - d[cycle[i]][cycle[j]]));
				//System.err.println("k=" + k + "\t diff = " + maxdiff);
			}

			int sk = s[k];  //Taxon to re-insert

			//Determine taxa before and after sk in the ordering which have been already inserted
			int u, v;
			int r1 = 0; //Number of already-inserted taxa with index smaller than sk.
			while (r1 < k - 1 && sk > cycle[r1 + 1])
				r1++;
			if (r1 == 0) {
				u = cycle[k - 1];
				v = cycle[1];
			} else if (r1 < k - 1) {
				u = cycle[r1];
				v = cycle[r1 + 1];
			} else {
				u = cycle[k - 1];
				v = cycle[1];
			}
			int r2 = k - r1 - 1; //Number already inserted taxa with index greater than sk.


			double[] z = new double[k];  //Vector indexed by the current k-1 elements of S.
			double[] b = new double[k];
			for (int j = 1; j < k; j++) {
				z[j] = d[cycle[j]][sk] - p[cycle[j]][u];
				b[j] = x[cycle[j]][v];
			}
			int trivialSplit = r1 + 1;
			if (trivialSplit > k - 1)
				trivialSplit = 1;
			b[trivialSplit] = Double.MAX_VALUE; // No upper bound for this weight.

			//Now we want to determine the split weights for the new splits created by inserting sk.
			//We do this by funding gamma which minimizes ||M gamma - z ||
			//such that 0 \leq \gamma_i \leq b_i  for all i

			//We have an initial feasible guess at gamma0, and then use the linear time algorithm to solve
			// M gamma_1 = z. We then search along the curve { proj[(1-t) gamma_0 + t gamma_1]: t \in [0,1]} to
			//find the feasible point minizing ||M gamma - z ||. Here proj[ ] is the projection back into the
			//feasible region.
			//TODO: We could afford multiple iterations here - suspect that gamma0 is a lousy choice which is leading
			//      to inflated split weights.


			double[] gamma0 = new double[k];
			for (int j = 1; j < k; j++)
				gamma0[j] = b[j] / 2.0;
			double sumz2 = 0.0;
			for (int j = 1; j < k; j++)
				sumz2 += z[j] * z[j];
			gamma0[trivialSplit] = sqrt(sumz2) / (k - 1);
			double[] gamma1 = solveM(r1, r2, z);

			boolean isFeasible = true;
			for (int j = 1; isFeasible && j < k; j++)
				isFeasible = (gamma1[j] >= 0 && gamma1[j] <= b[j]);

			double[] gamma = new double[k];
			if (isFeasible)
				System.arraycopy(gamma1, 1, gamma, 1, k - 1);
			else {
				gamma = goldenInsertion(gamma0, gamma1, b, r1, r2, z, tol);
			}
			//TODO Perhaps use a projected gradient method here instead?


			isFeasible = true;
			for (int j = 1; isFeasible && j < k; j++)
				isFeasible = (gamma[j] >= 0 && gamma[j] <= b[j]);


			//Update the circular distances and split weights
			double[] Mgamma = multiplyM(r1, r2, gamma);
			for (int j = 1; j < k; j++) {
				int sj = cycle[j];
				p[sk][sj] = p[sj][sk] = p[u][sj] + Mgamma[j];
				x[sk][sj] = x[sj][sk] = gamma[j];
				x[v][sj] = max(x[v][sj] - gamma[j], 0);
				x[sj][v] = x[v][sj];
			}

			//Insert sk into the cycle
			for (int j = 1; j <= r2; j++)
				cycle[k - j + 1] = cycle[k - j];
			cycle[r1 + 1] = sk;

			if (false && search) {
				//We carry out one iteration  of a steepest descent search
				for (int i = 1; i <= k; i++) {
					var si = cycle[i];
					for (int j = i + 1; j <= k; j++) {
						var sj = cycle[j];
						res[si][sj] = res[sj][si] = p[si][sj] - d[si][sj];
					}
				}
				NeighborNetUtilities.calcAtx(res, grad);

				final int kk = k;
				final UnivariateFunction fn = t -> insertionObjectiveFunction(t, x, grad, d, kk, cycle);

				var optimizer = new BrentOptimizer(1e-10, 0.6); //TODO Add tolerance parameter

				var result = optimizer.optimize(new MaxEval(20), new UnivariateObjectiveFunction(fn),
						GoalType.MINIMIZE, new SearchInterval(0, 1.0));

				var tmin = result.getPoint();
				for (int i = 1; i <= k; i++) {
					var si = cycle[i];
					for (int j = i + 1; j <= k; j++) {
						var sj = cycle[j];
						x[si][sj] = x[sj][si] = x[si][sj] - tmin * grad[si][sj];
					}
				}
				NeighborNetUtilities.calcAx(x, res);
				for (int i = 1; i <= k; i++) {
					var si = cycle[i];
					for (int j = i + 1; j <= k; j++) {
						var sj = cycle[j];
						p[si][sj] = p[sj][si] = res[si][sj];
					}
				}
			}
		}
	}

	static double insertionObjectiveFunction(double t, double[][] x, double[][] grad, double[][] d, int k, int[] cycle) {
		var n = x.length - 1;
		double[][] xt = new double[n + 1][n + 1];
		double[][] Ax = new double[n + 1][n + 1];
		for (var i = 1; i <= k; i++) {
			var si = cycle[i];
			for (var j = i + 1; j <= k; j++) {
				var sj = cycle[j];
				xt[si][sj] = xt[sj][si] = max(x[si][sj] - t * grad[si][sj], 0.0);
			}
		}
		NeighborNetUtilities.calcAx(xt, Ax);
		var fx = 0.0;
		for (var i = 1; i <= k; i++) {
			var si = cycle[i];
			for (var j = i + 1; j <= k; j++) {
				var sj = cycle[j];
				var res = Ax[si][sj] - d[si][sj];
				fx += 2.0 * res * res;
			}
		}
		//System.err.println("\t\tt="+t+"\tfx = "+fx);
		return fx;
	}


	/**
	 * Computes an ordering of the taxa with the goal that for every k, the first k taxa should be far away from each
	 * other.
	 * In practice, this is done by choosing the first taxa to be the one with the greatest sum of distances to other
	 * taxa. After that, we greedily choose the taxon which hasn't been inserted in the order but which has the
	 * greatest summed distances to those already placed.
	 *
	 * @param d editDistance matrix. Assumed symmetric and non-negative
	 * @return permutation of 1,...,n, returned in an integer array (indices 1..n)
	 */
	static private int[] maxDivergenceOrder(double[][] d) {
		int n = d.length - 1;
		int[] order = new int[n + 1];

		//Find the row with the maximum row sum
		//TODO use Apache math code for this. Also use only one triangle of d.
		int maxrow = 0;
		double maxsum = -1.0;
		for (int i = 1; i <= n; i++) {
			double si = 0;
			for (int j = 1; j <= n; j++)
				si += d[i][j];
			if (si > maxsum) {
				maxsum = si;
				maxrow = i;
			}
		}
		order[1] = maxrow;

		//Set A of elements already placed in order
		boolean[] A = new boolean[n + 1];
		A[maxrow] = true;

		//The vector s is the sum of distances from each element to everything in A.
		double[] s = new double[n + 1];
		System.arraycopy(d[maxrow], 1, s, 1, n);

		for (int k = 2; k <= n; k++) {
			//Find the element maxj not i A but maximizing s[maxj]
			int maxj = 0;
			double maxs = -1.0;
			for (int j = 1; j <= n; j++) {
				if (!A[j] && s[j] > maxs) {
					maxj = j;
					maxs = s[j];
				}
			}
			order[k] = maxj;
			A[maxj] = true;
			for (int j = 1; j <= n; j++) {
				s[j] += d[maxj][j];
			}
		}
		return order;
	}

	static private double[] solveM(int r1, int r2, double[] z) {
		int m = r1 + r2;
		double[] gamma = new double[m + 1];
		for (int i = 1; i <= m; i++)
			gamma[i] = 0.5 * z[i];

		gamma[1] -= 0.5 * z[m];
		for (int i = 2; i <= m; i++) {
			gamma[i] -= 0.5 * z[i - 1];
		}

		if (0 < r1 && r1 < m)
			gamma[r1 + 1] += z[r1];
		else
			gamma[1] += z[m];
		return gamma;
	}

	/**
	 * Fast multiplication of a vector by the specially structured matrix M.
	 * <p>
	 * Let V(r) denote an rxr matrix with 1s on and below the diagonal and
	 * -1 everywhere else. Let U(r,s) denote the rxs matrix of ones. Then
	 * M = [V(r1) U(r1,r2); -U(r2,r1) V(r2)]
	 *
	 * @param r1 parameter of M
	 * @param r2 parameter of M
	 * @param g  vector of size r1+r2
	 * @return Mg
	 */
	static private double[] multiplyM(int r1, int r2, double[] g) {
		int m = r1 + r2;
		double[] y = new double[m + 1];
		if (r1 == 0) {
			r1 = m;
			r2 = 0;
		}

		double y1 = g[1];
		for (int j = 2; j <= r1; j++)
			y1 -= g[j];
		for (int j = r1 + 1; j <= m; j++)
			y1 += g[j];
		y[1] = y1;

		for (int i = 2; i <= r1; i++) {
			y[i] = y[i - 1] + 2 * g[i];
		}
		if (r2 > 0) {
			y[r1 + 1] = -y[r1] + 2 * g[r1 + 1];
			for (int i = r1 + 2; i <= m; i++) {
				y[i] = y[i - 1] + 2 * g[i];
			}
		}
		return y;
	}


	/**
	 * Fast multiplication of a vector by the transpose of the specially structured matrix M.
	 * <p>
	 * Let V(r) denote an rxr matrix with 1s on and below the diagonal and
	 * -1 everywhere else. Let U(r,s) denote the rxs matrix of ones. Then
	 * M = [V(r1) U(r1,r2); -U(r2,r1) V(r2)]
	 *
	 * @param r1 parameter of M
	 * @param r2 parameter of M
	 * @param g  vector of size r1+r2
	 * @return Mg
	 */
	static private double[] multiplyMt(int r1, int r2, double[] g) {
		int m = r1 + r2;
		double[] y = new double[m + 1];
		if (r1 == 0) {
			r1 = m;
			r2 = 0;
		}

		double y1 = g[1];
		for (int j = 2; j <= r1; j++)
			y1 += g[j];
		for (int j = r1 + 1; j <= m; j++)
			y1 -= g[j];
		y[1] = y1;

		for (int i = 2; i <= r1; i++) {
			y[i] = y[i - 1] - 2 * g[i - 1];
		}
		if (r2 > 0) {
			y[r1 + 1] = -y[r1] + 2 * g[r1 + 1];
			for (int i = r1 + 2; i <= m; i++) {
				y[i] = y[i - 1] + 2 * g[i];
			}
		}
		return y;
	}


	static private double[] goldenInsertion(double[] gamma0, double[] gamma1, double[] b, int r1, int r2, double[] z, double tol) {


		class ObjectiveFunction {
			private final int m;
			private final double[] gammax;

			public ObjectiveFunction() {
				m = r1 + r2;
				gammax = new double[m + 1];
			}

			public double eval(double x) {
				for (int i = 1; i <= m; i++)   //Project onto box
					gammax[i] = min(max((1 - x) * gamma0[i] + x * gamma1[i], 0.0), b[i]);
				double[] mg = multiplyM(r1, r2, gammax);
				double val = 0.0;
				for (int i = 1; i <= m; i++) {
					val += (mg[i] - z[i]) * (mg[i] - z[i]);
				}
				return sqrt(val);
			}

		}

		int m = r1 + r2;
		ObjectiveFunction f = new ObjectiveFunction();
		final UnivariateFunction fn = x -> f.eval(x);

		var optimizer = new BrentOptimizer(1e-10, tol);
		var result = optimizer.optimize(new MaxEval(100), new UnivariateObjectiveFunction(fn),
				GoalType.MINIMIZE, new SearchInterval(0, 1.0));

		var x = result.getPoint();
		double[] gamma = new double[m + 1];
		for (int i = 1; i <= m; i++) { //Project onto box {
			double g_i = (1 - x) * gamma0[i] + x * gamma1[i];
			g_i = min(max(g_i, 0.0), b[i]);
			gamma[i] = g_i;
		}
		return gamma;
	}
//
//
//        var n = x.length - 1;
//        for (var i = 1; i <= n; i++) {
//            for (var j = i + 1; j <= n; j++) {
//                var newx_ij = max((1 - tmin) * x0[i][j] + tmin * x[i][j], 0);
//                x[i][j] = x[j][i] = newx_ij;
//            }
//        }
//        return tmin;
//
//
//        int m = r1+r2;
//        double maxdiff = 0.0;
//
//        for(int i=1;i<=m;i++) {
//            maxdiff = max(maxdiff,abs(gamma0[i]-gamma1[i]));
//        }
//        if (maxdiff == 0.0) {
//            double[] gamma = new double[m + 1];
//            System.arraycopy(gamma0, 1, gamma, 1, m);
//            return gamma;
//        }
//
//
//        double C = (3.0 - sqrt(5))/2.0;
//        double R = 1.0 - C;
//        ObjectiveFunction f = new ObjectiveFunction();
//        double x0 = 0, x1=C, x2 = C + C*(1-C), x3 = 1;
//
//        double f1 = f.eval(x1);
//        double f2 = f.eval(x2);
//
//        while(abs(x3-x0)>tol/maxdiff) {
//            if (f2<f1) {
//                x0 = x1;
//                x1 = x2;
//                x2 = R*x1 + C*x3;
//                f1 = f2;
//                f2 = f.eval(x2);
//            } else {
//                x3=x2;
//                x2 = x1;
//                x1 = R*x2 + C*x0;
//                f2=f1;
//                f1 = f.eval(x1);
//            }
//        }
//
//        double x=x2;
//        if (f1<f2)
//            x = x1;
//
//        double[] gamma = new double[m+1];
//        for (int i = 1; i <= m; i++) { //Project onto box {
//            double g_i = (1 - x) * gamma0[i] + x * gamma1[i];
//            g_i = min(max(g_i, 0.0), b[i]);
//            gamma[i] = g_i;
//        }
//
//        return gamma;
//    }


}
