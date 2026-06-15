/*
 *  NeighborNetSplitWeightsClean.java Copyright (C) 2024 Daniel H. Huson
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
import splitstree6.splits.ASplit;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;

import static java.lang.Math.*;
import static splitstree6.algorithms.distances.distances2splits.neighbornet.NeighborNetUtilities.*;
import static splitstree6.algorithms.distances.distances2splits.neighbornet.SquareArrays.copyArray;

/**
 * Neighbor-net weights computation
 * Dave Bryant, 2024, 2026 performance improvements
 */
public class NeighborNetSplitWeightsClean {

	/**
	 * NNLSParams
	 * <p>
	 * Parameter files describing the method choice and options
	 */
	public static class NNLSParams {

		public enum MethodTypes {GRADPROJECTION, ACTIVESET, APGD, IPG, SPLITSTREE4}

		public MethodTypes method = MethodTypes.ACTIVESET;

		public double cutoff = 0.0001; //Post-processing: Only include split weights greater than this amount

		//Stopping conditions - main method
		public double projGradBound = 1e-5; //Stop if squared projected gradient is less than this. This should be larger than the CGNR bound
		//TODO This should be linked to n and also to the condition of the Hessian. Someone?

		public boolean activeCleanup = false; //If true, run active set immediately after the selected method.

		public int maxIterations = Integer.MAX_VALUE;
		public long maxTime = Long.MAX_VALUE; //Stop if the method has taken more than this many milliseconds

		//Stopping conditions - ProjectedGradient
		double gcp_kl = 0.9;
		double gcp_ku = 0.2;
		double gcp_ke = 0.3;


		//Stopping conditions - CGNR
		public int cgnrIterations; //Max number of iterations in CGNR
		public double cgnrTolerance = projGradBound / 2; //Stopping condition for CGNR - bound on norm gradient squared.

		//Debugging
		public boolean printResiduals = false;
		public boolean cgnrPrintResiduals = false;
		public PrintWriter log;

		//Legacy options
		public double activeSetRho;
		public double APGDtheta;
		public double IPGtau;
		public double IPGthreshold;
		public double ST4pgbound;
		public boolean ST4useGradientNorm;
	}

	/**
	 * Estimate the split weights using non-negative least squares
	 *
	 * @param cycle     Neighbor-net cycle
	 * @param distances Array of distances, indexed 0..(n-1)
	 * @param params    Parameters
	 * @param progress  Progress bar - used to implement cancel
	 * @return Array list of splits with associated weights.
	 * @throws CanceledException User pressed cancel in progress bar
	 */
	static public ArrayList<ASplit> compute(int[] cycle, double[][] distances, NNLSParams params, ProgressListener progress) throws CanceledException {
		if (params == null)
			params = new NNLSParams();
		var n = cycle.length - 1;  //Number of taxa
		var npairs = n * (n - 1) / 2;

		//Handle cases for n<3 directly.
		if (n == 1) {
			return new ArrayList<>();
		}
		if (n == 2) {
			final var splits = new ArrayList<ASplit>();
			var d_ij = (float) distances[cycle[1] - 1][cycle[2] - 1];
			if (d_ij > 0.0) {
				final var A = new BitSet();
				A.set(cycle[1]);
				splits.add(new ASplit(A, 2, d_ij));
			}
			return splits;
		}

		//Set up vector distances indexed 1..n in order of the cycle
		var d = new double[npairs];
		var index = 0;
		for (var i = 1; i <= n; i++)
			for (var j = i + 1; j <= n; j++) {
				d[index] = distances[cycle[i] - 1][cycle[j] - 1];
				index++;
			}

		var Atd = new double[npairs];
		calcAtx(d, Atd, n);
		var normAtd = sqrt(sumArraySquared(Atd, n));
		params.projGradBound = (0.0001 * normAtd) * (0.0001 * normAtd);

		var x = new double[npairs]; //array of split weights
		calcAinv_y(d, x, n); //Compute unconstrained solution
		var minVal = minArray(x);
		if (minVal < 0) {
			zeroNegativeEntries(x);
			switch (params.method) {
				case GRADPROJECTION -> {
					params.gcp_ke = 0.1; //Constants for Wolfe conditions
					params.gcp_ku = 0.2;
					params.gcp_kl = 0.8;
					gradientProjection(x, d, n, params, progress);
				}
				case ACTIVESET -> {
					params.cgnrTolerance = params.projGradBound / 2;
					params.cgnrIterations = max(50, n * (n - 1) / 2);
					params.activeSetRho = 0.4;
					activeSetMethodFast(x, d, n, params, progress);
				}
				case APGD -> {
					params.maxIterations = 100 * n * n;
					params.APGDtheta = 0.5;
					APGD(x, d, n, params, progress);
				}
				case IPG -> {
					params.maxIterations = 100 * n * n;
					IPG(x, d, n, params, progress);
				}
				case SPLITSTREE4 -> {
					// The legacy Splitstree4 mode is not implemented in this cleaned class.
					// Fall back to the active-set solver, which is the most robust default.
					params.cgnrTolerance = params.projGradBound / 2;
					params.cgnrIterations = max(50, n * (n - 1) / 2);
					params.activeSetRho = 0.4;
					activeSetMethodFast(x, d, n, params, progress);
				}
			}
			if (params.activeCleanup) {
				params.cgnrTolerance = params.projGradBound / 2;
				params.cgnrIterations = max(50, n * (n - 1) / 2);
				params.activeSetRho = 0.4;
				activeSetMethodFast(x, d, n, params, progress);
			}
		}
		if (progress != null)
			progress.checkForCancel();

		//Construct the corresponding set of weighted splits
		final var splitList = new ArrayList<ASplit>();
		index = 0;
		for (var i = 1; i <= n; i++) {
			final var A = new BitSet();
			for (var j = i + 1; j <= n; j++) {
				A.set(cycle[j - 1]);
				if (x[index] > params.cutoff || A.cardinality() == 1 || A.cardinality() == n - 1) { // positive weight or trivial split
					splitList.add(new ASplit(A, n, max(0, x[index])));
				}
				index++;
			}
		}
		return splitList;
	}


	//*************************************************************************
	// METHOD IMPLEMENTATIONS
	//  **************************************************************************/

	/**
	 * <p>cgnr</p>
	 * <p>
	 * Implementation of the CGNR algorithm in Saad, "Iterative Methods for Sparse Linear Systems", applied to the
	 * problem of minimizing ||Ax - d|| such that x_{ij} = 0 for all ij in the activeSet.
	 *
	 * @param x         Initial value, overwritten with final value. Initially, we set x[i][j] = 0 for all [i][j] in the active set.
	 * @param d         square array of distances
	 * @param activeSet square array of boolean: specifying active (zero) set.
	 * @param params    parameters - uses params.cgnrIterations for max number of iterations
	 *                  and params.tolerance for bound on gradient at convergence
	 * @return int          number of iterations
	 */
	static public int cgnr(double[][] x, double[][] d, boolean[][] activeSet, NNLSParams params, ProgressListener progress) throws CanceledException {
		if (params.cgnrPrintResiduals && params.log == null)
			System.err.println("Error with initialising log file");

		var n = x.length - 1;
		var p = new double[n + 1][n + 1];
		var r = new double[n + 1][n + 1];
		var z = new double[n + 1][n + 1];
		var w = new double[n + 1][n + 1];

		zeroNegativeEntries(x);

		calcAx(x, r);
		for (var i = 1; i <= n; i++)
			for (var j = i + 1; j <= n; j++)
				r[i][j] = d[i][j] - r[i][j];

		calcAtx(r, z);
		maskElements(z, activeSet);
		copyArray(z, p);
		var ztz = sumArraySquared(z);
		var k = 1;

		while (true) {
			calcAx(p, w);
			var alpha = ztz / sumArraySquared(w);

			for (var i = 1; i <= n; i++) {
				for (var j = i + 1; j <= n; j++) {
					x[i][j] += alpha * p[i][j];
					r[i][j] -= alpha * w[i][j];
				}
			}
			calcAtx(r, z);
			maskElements(z, activeSet);
			var ztz2 = sumArraySquared(z);
			var beta = ztz2 / ztz;

			if (ztz2 < params.cgnrTolerance || k >= params.cgnrIterations)
				break;

			for (var i = 1; i <= n; i++) {
				for (var j = i + 1; j <= n; j++) {
					p[i][j] = z[i][j] + beta * p[i][j];
				}
			}
			ztz = ztz2;

			if (params.cgnrPrintResiduals)
				System.err.println("\t\t" + k + "\t" + ztz + "\t" + beta);

			k++;
			if (progress != null && (k % n) == 0)
				progress.checkForCancel();
		}
		return k;
	}

	/**
	 * activeSetMethod
	 * Implement the active set method for minimizing ||Ax-d|| over non-negative x.
	 *
	 * @param x        starting value - assumed feasible. Overwritten by solution.
	 * @param d        vector of distances
	 * @param params   method parameters.
	 * @param progress pointer to progress bar.
	 * @throws CanceledException User cancels calculation.
	 */
	static public void activeSetMethod(double[][] x, double[][] d, NNLSParams params, ProgressListener progress) throws CanceledException {
		var n = x.length - 1;
		var activeSet = new boolean[n + 1][n + 1];
		getActiveEntries(x, activeSet);

		var xstar = new double[n + 1][n + 1];
		var grad = new double[n + 1][n + 1];
		var k = 0;

		while (true) {
			while (true) {
				copyArray(x, xstar);
				var numIterations = cgnr(xstar, d, activeSet, params, progress);
				k++;
				if (progress != null)
					progress.checkForCancel();

				var xstarFeasible = feasibleMoveActiveSet(x, xstar, activeSet, params);

				if (xstarFeasible && numIterations < params.cgnrIterations)
					break;
				if (k > params.maxIterations)
					return;
			}
			copyArray(xstar, x);
			var pg = evalProjectedGradientSquared(x, d);
			if (pg < params.projGradBound) {
				return;
			}

			//At this point x is feasible, but not necessarily a solution to the equality constrained problem.
			//Determine if there is an active constraint which can be removed.

			evalGradient(x, d, grad);
			var imin = 0;
			var jmin = 0;
			var gradmin = 0.0;

			for (var i = 1; i <= n; i++) {
				for (var j = i + 1; j <= n; j++) {
					var g_ij = grad[i][j];
					if (activeSet[i][j] && g_ij < gradmin) {
						gradmin = g_ij;
						imin = i;
						jmin = j;
					}
				}
			}
			if (gradmin < 0.0)
				activeSet[imin][jmin] = false;
			if (progress != null)
				progress.checkForCancel();
		}
	}

	/**
	 * Move the point x towards point xstar, while still maintaining feasibility.
	 * <p>
	 * If xstar is feasible, then returns true. Otherwise, the activeset will change and a proportion
	 * params.activeSetrho of the infeasible entries in xstar will be added to the active set.
	 *
	 * @param x         square matrix --- feasible initial point
	 * @param xstar     square matrix ---  target point, will generally be infeasible
	 * @param activeSet current active set. assumed that xstar will satisfy the active set, and x will be moved
	 *                  to a point satisfying the active set
	 * @param params    uses parameter activeSetRho, which is the proportion of infeasible entries of xstar which will
	 *                  be added to the active set.
	 * @return true if xstar is feasible, false otherwise
	 */
	static private boolean feasibleMoveActiveSet(double[][] x, double[][] xstar, boolean[][] activeSet, NNLSParams params) {
		var n = xstar.length - 1;

		//First check if xstar is feasible, and return true if it is after moving x to xstar
		if (minArray(xstar) >= 0.0) {
			copyArray(xstar, x);
			return true;
		}

		//xstar is infeasible.
		//Store values in a structure for sorting
		SortedPairs sortedPairs = new SortedPairs(n);
		for (var i = 1; i <= n; i++)
			for (var j = i + 1; j <= n; j++)
				if (xstar[i][j] < 0)
					sortedPairs.insert(i, j, x[i][j] / (x[i][j] - xstar[i][j]));

		sortedPairs.sort();
		SortedPairs.Entry firstEntry = sortedPairs.get(0);
		if (firstEntry == null) {  //Should never get here
			copyArray(xstar, x);
			return true;
		}
		var t = firstEntry.val; //max val of t before first constraint met.

		//A proportion rho of the indices for which xstar is negative is added to the active set.
		var numToMakeActive = max(1, (int) Math.ceil(sortedPairs.nentries * params.activeSetRho));
		var index = 0;
		SortedPairs.Entry entry = sortedPairs.get(index);
		while (index < numToMakeActive && entry != null) {
			var i = entry.i;
			var j = entry.j;
			activeSet[i][j] = true;
			x[i][j] = 0.0;
			index++;
			entry = sortedPairs.get(index);
		}

		for (var i = 1; i <= n; i++)
			for (var j = i + 1; j <= n; j++) {
				if (!activeSet[i][j]) {
					x[i][j] = (1 - t) * x[i][j] + t * xstar[i][j];
				}
			}
		return false;
	}

	/**
	 * Primitive helper used by the legacy 2D active-set implementation.  This
	 * avoids allocating one object per infeasible split when sorting step lengths.
	 */
	private static final class SortedPairs {
		static final class Entry {
			final int i;
			final int j;
			final double val;

			Entry(int i, int j, double val) {
				this.i = i;
				this.j = j;
				this.val = val;
			}
		}

		int nentries;
		private final int[] is;
		private final int[] js;
		private final double[] vals;

		SortedPairs(int n) {
			nentries = 0;
			var npairs = n * (n - 1) / 2;
			is = new int[npairs];
			js = new int[npairs];
			vals = new double[npairs];
		}

		void insert(int i, int j, double val) {
			is[nentries] = i;
			js[nentries] = j;
			vals[nentries] = val;
			nentries++;
		}

		void sort() {
			quickSort(0, nentries - 1);
		}

		Entry get(int index) {
			if (index < 0 || index >= nentries)
				return null;
			return new Entry(is[index], js[index], vals[index]);
		}

		private void quickSort(int lo, int hi) {
			while (lo < hi) {
				var i = lo;
				var j = hi;
				var pivot = vals[(lo + hi) >>> 1];
				while (i <= j) {
					while (vals[i] < pivot)
						i++;
					while (vals[j] > pivot)
						j--;
					if (i <= j) {
						swap(i, j);
						i++;
						j--;
					}
				}
				if (j - lo < hi - i) {
					if (lo < j)
						quickSort(lo, j);
					lo = i;
				} else {
					if (i < hi)
						quickSort(i, hi);
					hi = j;
				}
			}
		}

		private void swap(int a, int b) {
			if (a == b)
				return;
			var ti = is[a];
			is[a] = is[b];
			is[b] = ti;
			var tj = js[a];
			js[a] = js[b];
			js[b] = tj;
			var tv = vals[a];
			vals[a] = vals[b];
			vals[b] = tv;
		}
	}


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

	/**
	 * Packed upper-triangle layout
	 * <p>
	 * Row-major packed order stores (0,1),(0,2),...,(0,n-1),(1,2),...
	 * Band-major order stores anti-diagonals/bands by distance k=j-i:
	 * band 1: (0,1),(1,2),...,(n-2,n-1)
	 * band 2: (0,2),(1,3),...
	 * etc.
	 * <p>
	 * In this layout, the dynamic-programming recurrences for the circular split
	 * design matrix A and its adjoint A^T use contiguous slices,
	 * to reduce cache misses and enable vectorize tight loops.
	 */
	private static final class BandIndex {
		final int n;
		final int npairs;
		final int[] bandOffsets;
		final int[] rowToBand;
		final int[] bandToRow;

		BandIndex(int n) {
			this.n = n;
			this.npairs = n * (n - 1) / 2;
			this.bandOffsets = new int[n];
			int offset = 0;
			for (var k = 1; k < n; k++) {
				bandOffsets[k - 1] = offset;
				offset += n - k;
			}
			this.rowToBand = new int[npairs];
			this.bandToRow = new int[npairs];
			var row = 0;
			for (var i = 0; i < n; i++) {
				for (var j = i + 1; j < n; j++) {
					var k = j - i;
					var band = bandOffsets[k - 1] + i;
					rowToBand[row] = band;
					bandToRow[band] = row;
					row++;
				}
			}
		}
	}

	private static void rowToBand(double[] src, double[] dst, BandIndex band) {
		for (var row = 0; row < band.npairs; row++)
			dst[band.rowToBand[row]] = src[row];
	}

	private static void bandToRow(double[] src, double[] dst, BandIndex band) {
		for (var bandIndex = 0; bandIndex < band.npairs; bandIndex++)
			dst[band.bandToRow[bandIndex]] = src[bandIndex];
	}

	private static void rowToBand(boolean[] src, boolean[] dst, BandIndex band) {
		for (var row = 0; row < band.npairs; row++)
			dst[band.rowToBand[row]] = src[row];
	}

	private static void batchRowSumsBand(double[] b, double[] sums, BandIndex band) {
		if (false) { // old ordering
			Arrays.fill(sums, 0.0);
			final int n = band.n;
			for (var i = 0; i < n; i++) {
				for (var j = i + 1; j < n; j++) {
					var k = j - i;
					var v = b[band.bandOffsets[k - 1] + i];
					sums[i] += v;
					sums[j] += v;
				}
			}
		} else {
			Arrays.fill(sums, 0.0);
			final int n = band.n;
			for (var k = 1; k < n; k++) {
				final int off = band.bandOffsets[k - 1];
				final int len = n - k;
				for (var i = 0; i < len; i++) {
					var v = b[off + i];     // contiguous
					sums[i] += v;       // pair (i, i+k)
					sums[i + k] += v;
				}
			}
		}
	}

	/**
	 * Forward multiply y=A*x in band-major order.
	 */
	private static void calculateForwardBand(double[] x, double[] y, BandIndex band, double[] rowSums, double[] shifted) {
		final int n = band.n;
		batchRowSumsBand(x, rowSums, band);
		System.arraycopy(rowSums, 1, y, band.bandOffsets[0], n - 1);

		// k == 2:
		{
			var km1 = band.bandOffsets[0];
			var ko = band.bandOffsets[1];
			var bkm1 = band.bandOffsets[0];
			var len = n - 2;
			System.arraycopy(y, km1 + 1, shifted, 0, len);   // <-- restore this; was dropped when k==2 was peeled out
			for (var i = 0; i < len; i++)
				y[ko + i] = y[km1 + i] + shifted[i] - 2.0 * x[bkm1 + 1 + i];
		}
		for (var k = 3; k < n; k++) {
			var len = n - k;
			var km1 = band.bandOffsets[k - 2];
			var ko = band.bandOffsets[k - 1];
			var bkm1 = band.bandOffsets[k - 2];
			System.arraycopy(y, km1 + 1, shifted, 0, len);
			int km2 = band.bandOffsets[k - 3];
			for (var i = 0; i < len; i++)
				y[ko + i] = y[km1 + i] + shifted[i] - y[km2 + 1 + i] - 2.0 * x[bkm1 + 1 + i];
		}
	}

	/**
	 * Adjoint multiply y=A^T*x in band-major order.
	 */
	private static void calculateAdjointBand(double[] x, double[] y, BandIndex band, double[] rowSums, double[] shifted) {
		final var n = band.n;
		batchRowSumsBand(x, rowSums, band);
		System.arraycopy(rowSums, 0, y, band.bandOffsets[0], n - 1);
		for (var k = 2; k < n; k++) {
			var len = n - k;
			var km1 = band.bandOffsets[k - 2];
			var ko = band.bandOffsets[k - 1];
			var bkm1 = band.bandOffsets[k - 2];
			System.arraycopy(y, km1 + 1, shifted, 0, len);
			if (k == 2) {
				for (var i = 0; i < len; i++)
					y[ko + i] = y[km1 + i] + shifted[i] - 2.0 * x[bkm1 + i];
			} else {
				int km2 = band.bandOffsets[k - 3];
				for (var i = 0; i < len; i++)
					y[ko + i] = y[km1 + i] + shifted[i] - y[km2 + 1 + i] - 2.0 * x[bkm1 + i];
			}
		}
	}

	private static double calculateForwardBandWithNorm(double[] x, double[] y, BandIndex band, double[] rowSums, double[] shifted) {
		calculateForwardBand(x, y, band, rowSums, shifted);
		var sum = 0.0;
		for (var i = 0; i < band.npairs; i++) {
			var v = y[i];
			sum += v * v;
		}
		return sum;
	}

	private static double calculateAdjointBandWithMaskedNorm(double[] x, double[] y, boolean[] activeSet, BandIndex band, double[] rowSums, double[] shifted) {
		calculateAdjointBand(x, y, band, rowSums, shifted);
		var sum = 0.0;
		for (var i = 0; i < band.npairs; i++) {
			if (activeSet[i]) {
				y[i] = 0.0;            // mask in-place, no extra pass
			} else {
				var v = y[i];
				sum += v * v;
			}
		}
		return sum;
	}

	private static void evalGradientBand(double[] x, double[] d, double[] gradient, double[] residual, BandIndex band, double[] rowSums, double[] shifted) {
		calculateForwardBand(x, residual, band, rowSums, shifted);
		for (var i = 0; i < residual.length; i++)
			residual[i] -= d[i];
		calculateAdjointBand(residual, gradient, band, rowSums, shifted);
	}

	private static void activeSetMethodFast(double[] xRow, double[] dRow, int n, NNLSParams params, ProgressListener progress) throws CanceledException {
		final var npairs = n * (n - 1) / 2;
		final var band = new BandIndex(n);
		final var x = new double[npairs];
		final var d = new double[npairs];
		rowToBand(xRow, x, band);
		rowToBand(dRow, d, band);

		final var activeSetRow = new boolean[npairs];
		getActiveEntries(xRow, activeSetRow);
		final var activeSet = new boolean[npairs];
		rowToBand(activeSetRow, activeSet, band);

		final var xstar = new double[npairs];
		final var scratch = new double[4][npairs]; // p, r, z, w
		final var rowSums = new double[n];
		final var shifted = new double[n];
		final var infeasible = new int[npairs];
		final var order = new int[npairs];
		final var vals = new double[npairs];

		var startTime = System.currentTimeMillis();
		var k = 0;
		while (true) {
			while (true) {
				System.arraycopy(x, 0, xstar, 0, npairs);
				int numIterations = cgnrBand(xstar, d, activeSet, params, scratch, band, rowSums, shifted, progress, startTime);
				k++;
				if (progress != null)
					progress.checkForCancel();

				var xstarFeasible = feasibleMoveActiveSetFast(x, xstar, activeSet, infeasible, order, vals, params);

				if (params.printResiduals) {
					double pg = evalProjectedGradientSquaredBand(x, d, scratch[0], scratch[1], activeSet, band, rowSums, shifted);
					logResidual(params, "\t" + k + "\t" + params.cgnrIterations + "\t" + params.activeSetRho + "\t" + (System.currentTimeMillis() - startTime) + "\t" + pg + "\t" + numberNonzero(x));
				}

				if (xstarFeasible && numIterations < params.cgnrIterations)
					break;
				if (k > params.maxIterations || System.currentTimeMillis() - startTime > params.maxTime) {
					bandToRow(x, xRow, band);
					return;
				}
			}

			System.arraycopy(xstar, 0, x, 0, npairs);
			var pg = evalProjectedGradientSquaredBand(x, d, scratch[0], scratch[1], activeSet, band, rowSums, shifted);
			if (pg < params.projGradBound) {
				bandToRow(x, xRow, band);
				return;
			}

			// Release the active constraint with the most negative projected gradient.
			evalGradientBand(x, d, scratch[0], scratch[1], band, rowSums, shifted);
			var imin = -1;
			var gmin = 0.0;
			for (var i = 0; i < npairs; i++) {
				double g = scratch[0][i];
				if (activeSet[i] && g < gmin) {
					gmin = g;
					imin = i;
				}
			}
			if (imin >= 0)
				activeSet[imin] = false;
		}
	}

	private static int cgnrBand(double[] x, double[] d, boolean[] activeSet, NNLSParams params, double[][] scratch, BandIndex band, double[] rowSums, double[] shifted, ProgressListener progress, long startTime) throws CanceledException {
		final var npairs = x.length;
		final var n = band.n;
		final var p = scratch[0];
		final var r = scratch[1];
		final var z = scratch[2];
		final var w = scratch[3];

		zeroNegativeEntries(x);
		calculateForwardBand(x, r, band, rowSums, shifted);
		for (var i = 0; i < npairs; i++)
			r[i] = d[i] - r[i];

		// z = A^T r (full vector); ztz = ||z||^2 over the free (non-active) set
		var ztz = calculateAdjointBandWithMaskedNorm(r, z, activeSet, band, rowSums, shifted);

		// p = z on the free set, 0 on the active set (single pass, no separate fill)
		for (var i = 0; i < npairs; i++)
			p[i] = activeSet[i] ? 0.0 : z[i];

		if (ztz <= 1e-300 || !Double.isFinite(ztz))
			return 1;

		var k = 1;
		while (true) {
			var denom = calculateForwardBandWithNorm(p, w, band, rowSums, shifted);
			if (denom <= 1e-300 || !Double.isFinite(denom))
				break;
			var alpha = ztz / denom;

			// p is identically 0 on the active set, so the x update needs no mask:
			// active entries get x[i] += alpha*0, i.e. they are left untouched.
			// This makes the loop branch-free and vectorizable, and is bit-identical
			// to the previous `if (!activeSet[i]) x[i] += alpha*p[i];`.
			for (var i = 0; i < npairs; i++) {
				x[i] += alpha * p[i];
				r[i] -= alpha * w[i];
			}

			var ztz2 = calculateAdjointBandWithMaskedNorm(r, z, activeSet, band, rowSums, shifted);
			if (ztz2 < params.cgnrTolerance || k >= params.cgnrIterations)
				break;
			var beta = ztz2 / ztz;

			// free set: p = z + beta*p ; active set: p stays 0
			for (var i = 0; i < npairs; i++)
				p[i] = activeSet[i] ? 0.0 : z[i] + beta * p[i];

			ztz = ztz2;
			k++;
			if (progress != null && (k % n) == 0)
				progress.checkForCancel();
			if (System.currentTimeMillis() - startTime > params.maxTime)
				break;
		}
		return k;
	}

	private static double evalProjectedGradientSquaredBand(double[] x, double[] d, double[] gradient, double[] residual, boolean[] activeSet, BandIndex band, double[] rowSums, double[] shifted) {
		evalGradientBand(x, d, gradient, residual, band, rowSums, shifted);
		var sum = 0.0;
		for (var i = 0; i < x.length; i++) {
			var g = (x[i] == 0.0 || activeSet[i]) ? Math.min(0.0, gradient[i]) : gradient[i];
			sum += g * g;
		}
		return sum;
	}

	private static boolean feasibleMoveActiveSetFast(double[] x, double[] xstar, boolean[] activeSet, int[] splits, int[] order, double[] vals, NNLSParams params) {
		var count = 0;
		for (var i = 0; i < xstar.length; i++) {
			if (xstar[i] < 0.0) {
				vals[count] = x[i] / (x[i] - xstar[i]);
				splits[count] = i;
				order[count] = count;
				count++;
			}
		}
		if (count == 0) {
			System.arraycopy(xstar, 0, x, 0, x.length);
			return true;
		}

		var numToMakeActive = Math.max(1, (int) Math.ceil(count * params.activeSetRho));
		selectSmallestByVals(order, vals, 0, count - 1, numToMakeActive - 1);

		var tmin = Double.POSITIVE_INFINITY;
		for (var i = 0; i < numToMakeActive; i++) {
			var sortedIndex = order[i];
			activeSet[splits[sortedIndex]] = true;
			if (vals[sortedIndex] < tmin)
				tmin = vals[sortedIndex];
		}

		for (var i = 0; i < x.length; i++) {
			if (activeSet[i])
				x[i] = 0.0;
			else
				x[i] = (1.0 - tmin) * x[i] + tmin * xstar[i];
		}
		return false;
	}

	/**
	 * Partially orders {@code order} so that positions {@code 0..k} contain the
	 * indices of the {@code k + 1} smallest values.  Their order within that prefix
	 * is unspecified; this is enough for the active-set update and avoids a full
	 * O(m log m) sort of all infeasible splits.
	 */
	private static void selectSmallestByVals(int[] order, double[] vals, int lo, int hi, int k) {
		while (lo < hi) {
			var i = lo;
			var j = hi;
			var pivot = vals[order[(lo + hi) >>> 1]];
			while (i <= j) {
				while (vals[order[i]] < pivot)
					i++;
				while (vals[order[j]] > pivot)
					j--;
				if (i <= j) {
					var tmp = order[i];
					order[i] = order[j];
					order[j] = tmp;
					i++;
					j--;
				}
			}
			if (k <= j)
				hi = j;
			else if (k >= i)
				lo = i;
			else
				return;
		}
	}
}

