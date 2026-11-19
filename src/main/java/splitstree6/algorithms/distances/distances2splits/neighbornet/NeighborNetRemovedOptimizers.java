/*
 *  NeighborNetRemovedOptimizers.java Copyright (C) 2024 Daniel H. Huson
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
import jloda.util.progress.ProgressListener;

import static java.lang.Math.*;
import static splitstree6.algorithms.distances.distances2splits.neighbornet.NeighborNetSplitWeightsClean.NNLSParams;
import static splitstree6.algorithms.distances.distances2splits.neighbornet.NeighborNetUtilities.*;
import static splitstree6.algorithms.distances.distances2splits.neighbornet.SquareArrays.copyArray;

/**
 * The gradient projection, accelerated projected gradient descent (APGD) and interior-point gradient (IPG) optimisers
 * for the Neighbor Net split weights.
 * Not used: Neighbor Net offered the first two as inference algorithms until 2026 and now offers ActiveSet and SplitsTree4 only.
 * Moved here unchanged from NeighborNetSplitWeightsClean.
 * Dave Bryant, 2024
 */
public class NeighborNetRemovedOptimizers {
	/**
	 * gradient Projection algorithm
	 * <p>
	 * Adaptation (and simplification) of the projected-gradient type algorithm in
	 * Cartis et al. 2011  doi:10.1093/imanum/drr035
	 * but for the case with no regularisation and simple non-negativity points.
	 *
	 * @param x        Starting value
	 * @param d        Distances
	 * @param params   Parameters for search
	 * @param progress Progress bar (for feedback)
	 * @throws CanceledException User presses cancel
	 */
	static public void gradientProjection(double[][] x, double[][] d, NNLSParams params, ProgressListener progress) throws CanceledException {
		var startTime = System.currentTimeMillis();
		var n = x.length - 1;
		var p = new double[n + 1][n + 1];

		for (var k = 1; k <= params.maxIterations; k++) {
			evalGradient(x, d, p);
			scale(p, -1);

			projectedGCP(x, p, d, params);

			if (progress != null)
				progress.checkForCancel();

			var pg = evalProjectedGradientSquared(x, d);

			if (params.printResiduals) {
				System.err.println(k + "\t" + (System.currentTimeMillis() - startTime) + "\t" + pg + "\t" + numberNonzero(x));
			}

			if (pg < params.projGradBound)
				return;
			if (progress != null)
				progress.checkForCancel();
		}
	}

	static public void gradientProjection(double[] x, double[] d, int n, NNLSParams params, ProgressListener progress) throws CanceledException {
		var npairs = n * (n - 1) / 2;
		var startTime = System.currentTimeMillis();
		var p = new double[npairs];
		var scratch = new double[2][npairs];

		for (var k = 1; k <= params.maxIterations; k++) {
			evalGradient(x, d, p, scratch[0], n);
			scale(p, -1);

			projectedGCP(x, p, d, scratch[0], scratch[1], n, params);

			if (progress != null)
				progress.checkForCancel();

			var pg = evalProjectedGradientSquared(x, d, scratch[0], scratch[1], n);
			if (params.printResiduals)
				logResidual(params, "\t" + k + "\t" + (System.currentTimeMillis() - startTime) + "\t" + pg + "\t" + numberNonzero(x));

			if (pg < params.projGradBound)
				return;
			if (progress != null)
				progress.checkForCancel();
		}
	}

	/**
	 * ProjectedGCP
	 * <p>
	 * Adaptation of the algorithm for finding a Generalized Cauchy Point (GCP), due to
	 * Cartis et al. 2011  doi:10.1093/imanum/drr035
	 * but for the case with no regularisation and simple non-negativity points.
	 * <p></p>
	 * Given x and p, a GCP is a point y = x^GC such that
	 * y = [x + tp]_+ for some t >= 0.
	 * f(y) <= f(x) - \kappa_u p^T(y-x)
	 * and either
	 * f(y) >= f(x) - \kappa_l p^T(y-x)
	 * or
	 * ||w|| \leq \kappa_e  |p^T(y-x)|
	 * where
	 * w_i  =   \begin{cases} p_i    if  y_i > 0; max(p_i,0) if y_i = 0 \end{cases}
	 * This second condition is equivalent to (2.6) in Cartis et al. Given y, the tangent
	 * cone for the positive quadrant at y is {w:w_i \geq 0 for all i such that y_i = 0.}
	 * The three constants (in the params) are tuning factors which satisfy
	 * 0 < k_u < k_l < 1 and k_e \in (0,0.5)
	 * <p>
	 * The search algorithm
	 *
	 * @param x      Starting value.This is overwritten by xGC, the Generalized Cauchy point
	 * @param p      Search direction, typically equal to -\grad f(x)
	 * @param d      Distances
	 * @param params Parameters. WE use gcp_ku,gcp_kl,gcp_ke.
	 */
	private static void projectedGCP(double[][] x, double[][] p, double[][] d, NNLSParams params) {
		var n = x.length - 1;

		var f0 = evalProjectedf(x, 0, p, d);

		//Locate the largest breakpoint. We start the search at 0.5 times this.
		var tlimit = 0.0;
		for (var i = 1; i <= n; i++)
			for (var j = i + 1; j <= n; j++) {
				var t = -x[i][j] / p[i][j];
				if (t > 0)
					tlimit = max(tlimit, t);
			}

		var tmin = 0.0;
		var tmax = Double.MAX_VALUE;
		double tk;
		if (tlimit > 0.0)
			tk = 0.5 * tlimit;
		else {
			//For all i,j either p[i][j] >= 0 or x[i][j] = 0 and p[i][j] <0.
			//We jump to an exact solution.
			var phat = new double[n + 1][n + 1];
			for (var i = 1; i <= n; i++)
				for (var j = i + 1; j <= n; j++) {
					phat[i][j] = max(p[i][j], 0.0);
				}
			/* Min 0.5 (A(x+tp) - d)'(A(x+tp) - d) = 0.5 t^2 p'A'Ap - t d'Ap + const
			t = (Ax-d)'Ap / pA'Ap
			 */
			var Ap = new double[n + 1][n + 1];
			var Ax = new double[n + 1][n + 1];
			calcAx(phat, Ap);
			calcAx(x, Ax);
			var rAp = 0.0;
			var pAAp = 0.0;
			for (var i = 1; i <= n; i++)
				for (var j = i + 1; j <= n; j++) {
					var Ap_ij = Ap[i][j];
					var r_ij = Ax[i][j] - d[i][j];
					rAp += Ap_ij * r_ij;
					pAAp += Ap_ij * Ap_ij;
				}
			tk = -rAp / pAAp; //TODO: Replace rAp with phat^T phat.
		}

		while (true) {
			var fk = evalProjectedf(x, tk, p, d);
			var ptz = 0.0;
			for (var i = 1; i <= n; i++)
				for (var j = i + 1; j <= n; j++)
					ptz += p[i][j] * (max(x[i][j] + tk * p[i][j], 0) - x[i][j]);

			if (fk > f0 - params.gcp_ku * ptz) {
				//Too far.
				tmax = tk;
				tk = 0.5 * (tmin + tmax);
			} else {
				var wsum = 0.0;
				for (var i = 1; i <= n; i++)
					for (var j = i + 1; j <= n; j++) {
						var pij = p[i][j];
						if (p[i][j] > 0 || x[i][j] + tk * p[i][j] > 0)
							wsum += pij * pij;
					}
				if (fk < f0 - params.gcp_kl * ptz && sqrt(wsum) > params.gcp_ke * abs(ptz)) {
					tmin = tk;
					if (tmax == Double.MAX_VALUE)
						tk = 2.0 * tk;
					else
						tk = 0.5 * (tmin + tmax);
				} else {
					for (var i = 1; i <= n; i++)
						for (var j = i + 1; j <= n; j++)
							x[i][j] = max(x[i][j] + tk * p[i][j], 0.0);
					return;
				}

			}
		}
	}

	private static void projectedGCP(double[] x, double[] p, double[] d, double[] scratch1, double[] scratch2, int n, NNLSParams params) {
		var npairs = n * (n - 1) / 2;
		var f0 = evalProjectedf(x, 0, p, d, scratch1, scratch2, n);

		//Locate the largest breakpoint. We start the search at 0.5 times this.
		var tlimit = 0.0;
		for (var i = 0; i < npairs; i++) {
			var t = -x[i] / p[i];
			if (t > 0)
				tlimit = max(tlimit, t);
		}

		var tmin = 0.0;
		var tmax = Double.MAX_VALUE;
		double tk;
		if (tlimit > 0.0)
			tk = 0.5 * tlimit;
		else {
			//For all i,j either p[i][j] >= 0 or x[i][j] = 0 and p[i][j] <0.
			//We jump to an exact solution.
			for (var i = 0; i < npairs; i++)
				scratch1[i] = max(p[i], 0.0); //scratch1 = phat

			/* Min 0.5 (A(x+tp) - d)'(A(x+tp) - d) = 0.5 t^2 p'A'Ap - t d'Ap + const
			t = (Ax-d)'Ap / pA'Ap
			 */
			calcAx(scratch1, scratch2, n); //scratch2 = A * phat
			calcAx(x, scratch1, n); //scratch1 = Ax

			var rAp = 0.0;
			var pAAp = 0.0;
			for (var i = 0; i < npairs; i++) {
				var Ap_i = scratch2[i];
				var r_i = scratch1[i] - d[i];
				rAp += Ap_i * r_i;
				pAAp += Ap_i * Ap_i;
			}
			tk = -rAp / pAAp; //TODO: Replace rAp with phat^T phat.
		}

		while (true) {
			var fk = evalProjectedf(x, tk, p, d, scratch1, scratch2, n);
			var ptz = 0.0;
			for (var i = 0; i < npairs; i++)
				ptz += p[i] * (max(x[i] + tk * p[i], 0) - x[i]); //TODO: Check numerical stability here

			if (fk > f0 - params.gcp_ku * ptz) {
				//Too far.
				tmax = tk;
				tk = 0.5 * (tmin + tmax);
			} else {
				var wsum = 0.0;
				for (var i = 0; i < npairs; i++) {
					var pij = p[i];
					if (p[i] > 0 || x[i] + tk * p[i] > 0)
						wsum += pij * pij;
				}
				if (fk < f0 - params.gcp_kl * ptz && sqrt(wsum) > params.gcp_ke * abs(ptz)) {
					tmin = tk;
					if (tmax == Double.MAX_VALUE)
						tk = 2.0 * tk;
					else
						tk = 0.5 * (tmin + tmax);
				} else {
					for (var i = 0; i < npairs; i++)
						x[i] = max(x[i] + tk * p[i], 0.0);
					return;
				}

			}
		}
	}

	static private double evalProjectedf(double[][] x, double t, double[][] p, double[][] d) {
		var n = x.length - 1;

		var xk = new double[n + 1][n + 1];
		var Axk = new double[n + 1][n + 1];

		for (var i = 1; i <= n; i++)
			for (var j = i + 1; j <= n; j++)
				xk[i][j] = max(x[i][j] + t * p[i][j], 0);
		calcAx(xk, Axk);

		var diff = 0.0;
		for (var i = 1; i <= n; i++)
			for (var j = i + 1; j <= n; j++) {
				var rij = Axk[i][j] - d[i][j];
				diff += rij * rij;
			}

		return 0.5 * (diff - sumArraySquared(d));
	}

	static private double evalProjectedf(double[] x, double t, double[] p, double[] d, double[] xk, double[] resk, int n) {
		var npairs = n * (n - 1) / 2;

		for (var i = 0; i < npairs; i++)
			xk[i] = max(x[i] + t * p[i], 0);
		calcAx(xk, resk, n);
		for (var i = 0; i < npairs; i++)
			resk[i] -= d[i];

		return 0.5 * (sumArraySquared(resk, n) - sumArraySquared(d, n)); //TODO - could pass d'd
	}

	static public void APGD(double[][] x, double[][] d, NNLSParams params, ProgressListener progress) throws CanceledException {
		var n = x.length - 1;

		zeroNegativeEntries(x);
		var startTime = System.currentTimeMillis();

		var L = estimateMatrixNorm(n);

		double theta_old;
		var theta = params.APGDtheta;

		var y = new double[n + 1][n + 1];
		var g = new double[n + 1][n + 1];
		var x_old = new double[n + 1][n + 1];

		copyArray(x, y);
		var k = 0;
		var error_old = evalResidual(x, d);

		while (true) {
			//Store previous variables
			copyArray(x, x_old);
			theta_old = theta;

			//Compute acceleration parameters
			var a2 = theta * theta;
			theta = 0.5 * (-a2 + theta * sqrt(a2 + 4));
			var beta = theta_old * (1 - theta_old) / (a2 + theta);

			//Update
			evalGradient(y, d, g);
			for (var i = 1; i <= n; i++) {
				for (var j = i + 1; j <= n; j++) {
					x[i][j] = max(y[i][j] - (1.0 / L) * g[i][j], 0.0);
				}
			}
			for (var i = 1; i <= n; i++) {
				for (var j = i + 1; j <= n; j++) {
					y[i][j] = (1 + beta) * x[i][j] - beta * x_old[i][j];
				}
			}

			//Compute error
			//TODO could shave time here by updating dynamically
			var error = evalResidual(x, d);
			if (k > 0 && (error > error_old)) {
				//Restart - just do a gradient update
				evalGradient(x_old, d, g);
				for (var i = 1; i <= n; i++) {
					for (var j = i + 1; j <= n; j++) {
						x[i][j] = max(x_old[i][j] - (1.0 / L) * g[i][j], 0.0);
					}
				}
				copyArray(x, y);
				theta = params.APGDtheta;
			}

			var pg = evalProjectedGradientSquared(x, d);
			if (params.printResiduals && k % 10 == 0) {
				logResidual(params, "\t" + k + "\t" + params.APGDtheta + "\t" + pg + "\t" + numberNonzero(x));
			}
			if (pg < params.projGradBound || k >= params.maxIterations)
				return;
			if (progress != null)
				progress.checkForCancel();
			k++;
		}
	}


	static public void APGD(double[] x, double[] d, int n, NNLSParams params, ProgressListener progress) throws CanceledException {
		var npairs = n * (n - 1) / 2;
		var startTime = System.currentTimeMillis();

		zeroNegativeEntries(x);
		var scratch = new double[npairs];
		var L = estimateMatrixNorm(n);

		double theta_old;
		var theta = params.APGDtheta;

		var y = new double[npairs];
		var g = new double[npairs];
		var x_old = new double[npairs];

		copyArray(x, y);
		var k = 0;
		var error_old = evalResidual(x, d, scratch, n);

		while (true) {
			//Store previous variables
			copyArray(x, x_old);
			theta_old = theta;

			//Compute acceleration parameters
			var a2 = theta * theta;
			theta = 0.5 * (-a2 + theta * sqrt(a2 + 4));
			var beta = theta_old * (1 - theta_old) / (a2 + theta);

			//Update
			evalGradient(y, d, g, scratch, n);
			for (var i = 0; i < npairs; i++)
				x[i] = max(y[i] - (1.0 / L) * g[i], 0.0);

			for (var i = 0; i < npairs; i++)
				y[i] = (1 + beta) * x[i] - beta * x_old[i];

			//Compute error
			//TODO could shave time here by updating dynamically
			var error = evalResidual(x, d, scratch, n);
			if (k > 0 && (error > error_old)) {
				//Restart - just do a gradient update
				evalGradient(x_old, d, g, scratch, n);
				for (var i = 0; i < npairs; i++)
					x[i] = max(x_old[i] - (1.0 / L) * g[i], 0.0);
				copyArray(x, y);
				theta = params.APGDtheta;
			}

			var pg = evalProjectedGradientSquared(x, d, g, scratch, n);
			if (params.printResiduals && k % 10 == 0) {
				logResidual(params, "\t" + k + "\t" + pg + "\t" + (System.currentTimeMillis() - startTime) + "\t" + numberNonzero(x));
			}
			if (pg < params.projGradBound || k >= params.maxIterations)
				return;
			if (progress != null)
				progress.checkForCancel();
			k++;
		}
	}

	/**
	 * Rough estimate of the 2-norm of A'A, which I got in MATLAB by computing the norms and fitting a 4 degree polynomial.
	 *
	 * @param n number of taxa
	 * @return estimate of ||A'A||_2
	 */
	static private double estimateMatrixNorm(int n) {
		return (((0.041063124831008 * n + 0.000073540331934) * n + 0.065260125117342) * n + 0.027499142031727) * n - 0.038454953524879;
	}

	static public void IPG(double[][] x, double[][] d, NNLSParams params, ProgressListener progress) throws CanceledException {
		var n = x.length - 1;
		long startTime = System.currentTimeMillis();

		//Initial x needs to be strictly positive.
		zeroNegativeEntries(x);
		var dsum = 0.0;
		for (var i = 1; i < n; i++)
			for (var j = i + 1; j <= n; j++)
				dsum += d[i][j];
		for (var i = 1; i < n; i++)
			for (var j = i + 1; j <= n; j++)
				x[i][j] += dsum / (n * n * n);

		var g = new double[n + 1][n + 1];
		var y = new double[n + 1][n + 1];
		var z = new double[n + 1][n + 1];
		var p = new double[n + 1][n + 1];
		var xmapped = new double[n + 1][n + 1];


		for (var k = 1; k <= params.maxIterations; k++) {
			evalGradient(x, d, g);
			calcAx(x, y);
			calcAtx(y, z);  //z = A'Ax
			for (var i = 1; i <= n; i++)
				for (var j = i + 1; j <= n; j++)
					p[i][j] = -x[i][j] / z[i][j] * g[i][j];

			var alphahat = Double.MAX_VALUE;
			for (var i = 1; i <= n; i++)
				for (var j = i + 1; j <= n; j++)
					if (p[i][j] < 0)
						alphahat = min(alphahat, -x[i][j] / p[i][j]);

			calcAx(p, y);
			var ptg = 0.0;
			var ptAtAp = 0.0;
			for (var i = 1; i <= n; i++)
				for (var j = i + 1; j <= n; j++) {
					ptg += p[i][j] * g[i][j];
					ptAtAp += y[i][j] * y[i][j];
				}
			var alphastar = -ptg / ptAtAp;
			var alpha = min(params.IPGtau * alphahat, alphastar);
			for (var i = 1; i <= n; i++)
				for (var j = i + 1; j <= n; j++) {
					x[i][j] += alpha * p[i][j];
				}

			copyArray(x, xmapped);
			threshold(xmapped, params.IPGthreshold);
			var pg = evalProjectedGradientSquared(xmapped, d);
			if (params.printResiduals)
				logResidual(params, k + "\t" + (System.currentTimeMillis() - startTime) + "\t" + pg + "\t" + numberNonzero(xmapped));
			if (pg < params.projGradBound || (System.currentTimeMillis() - startTime) > params.maxTime) {
				copyArray(xmapped, x);
				return;
			}
			if (progress != null)
				progress.checkForCancel();
		}
	}

	static public void IPG(double[] x, double[] d, int n, NNLSParams params, ProgressListener progress) throws CanceledException {
		var npairs = n * (n - 1) / 2;
		var startTime = System.currentTimeMillis();

		//Initial x needs to be strictly positive.
		zeroNegativeEntries(x);
		var offset = 0.0;
		for (var i = 0; i < npairs; i++)
			offset += d[i];
		offset *= 1.0 / (n * n * n);
		for (var i = 0; i < npairs; i++)
			x[i] += offset;

		var g = new double[npairs];
		var y = new double[npairs];
		var z = new double[npairs];
		var p = new double[npairs];
		var xmapped = new double[npairs];
		var scratch = new double[npairs];


		for (var k = 1; k <= params.maxIterations; k++) {
			evalGradient(x, d, g, scratch, n);
			calcAx(x, y, n);
			calcAtx(y, z, n);  //z = A'Ax
			for (var i = 0; i < npairs; i++)
				p[i] = -x[i] / z[i] * g[i];

			double alphahat = Double.MAX_VALUE;
			for (var i = 0; i < npairs; i++)
				if (p[i] < 0)
					alphahat = min(alphahat, -x[i] / p[i]);

			calcAx(p, y, n);
			var ptg = 0.0;
			var ptAtAp = 0.0;
			for (var i = 0; i < npairs; i++) {
				ptg += p[i] * g[i];
				ptAtAp += y[i] * y[i];
			}
			var alphastar = -ptg / ptAtAp;
			var alpha = min(params.IPGtau * alphahat, alphastar);
			for (var i = 0; i < npairs; i++)
				x[i] += alpha * p[i];

			copyArray(x, xmapped);
			threshold(xmapped, params.IPGthreshold);
			var pg = evalProjectedGradientSquared(xmapped, d, g, scratch, n);
			if (params.printResiduals)
				logResidual(params, k + "\t" + (System.currentTimeMillis() - startTime) + "\t" + pg + "\t" + numberNonzero(xmapped));
			if (pg < params.projGradBound || (System.currentTimeMillis() - startTime) > params.maxTime) {
				copyArray(xmapped, x);
				return;
			}
			if (progress != null)
				progress.checkForCancel();
		}

	}

	private static void logResidual(NNLSParams params, String line) {
		if (params.log != null)
			params.log.println(line);
		else
			System.err.println(line);
	}
}
