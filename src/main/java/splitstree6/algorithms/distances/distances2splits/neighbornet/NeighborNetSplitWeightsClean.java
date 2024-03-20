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
import java.util.Comparator;

import static java.lang.Math.*;
import static splitstree6.algorithms.distances.distances2splits.neighbornet.NeighborNetUtilities.*;
import static splitstree6.algorithms.distances.distances2splits.neighbornet.SquareArrays.copyArray;

public class NeighborNetSplitWeightsClean {

	/**
	 * NNLSParams
	 * <p>
	 * Parameter files describing the method choice and options
	 */
	public static class NNLSParams {

		public enum MethodTypes {GRADPROJECTION, ACTIVESET, APGD, IPG, SPLITSTREE4}

		public MethodTypes method = MethodTypes.GRADPROJECTION;

		public double gradProjLinesearchBound;

		public double cutoff = 0.0001; //Post processing: Only include split weights greater than this amount

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
		var n = cycle.length - 1;  //Number of taxa

		long before = System.currentTimeMillis();

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

		//Set up square array of distances indexed 1..n in order of the cycle
		var d = new double[n + 1][n + 1];
		for (var i = 1; i <= n; i++)
			for (var j = i + 1; j <= n; j++)
				d[i][j] = distances[cycle[i] - 1][cycle[j] - 1];
		double[][] Atd = new double[n + 1][n + 1];
		calcAtx(d, Atd);
		double normAtd = sqrt(sumArraySquared(Atd));
		params.projGradBound = (0.0001 * normAtd) * (0.0001 * normAtd);


		var x = new double[n + 1][n + 1]; //array of split weights
		calcAinv_y(d, x); //Compute unconstrained solution
		var minVal = minArray(x);
		if (minVal < 0) {
			zeroNegativeEntries(x);
			switch (params.method) {
				case GRADPROJECTION -> {
					params.gcp_ke = 0.1;
					params.gcp_ku = 0.2;
					params.gcp_kl = 0.8;
					gradientProjection(x, d, params, progress);
				}
				case ACTIVESET -> {
					params.cgnrTolerance = params.projGradBound / 2;
					params.cgnrIterations = max(50, n * (n - 1) / 2);
					params.activeSetRho = 0.4;
					activeSetMethod(x, d, params, progress);
				}
				case APGD -> {
					params.maxIterations = 100 * n * n;
					params.APGDtheta = 1.0;
					APGD(x, d, params, progress);
				}
				case IPG -> {
					params.maxIterations = 100 * n * n;
					IPG(x, d, params, progress);
				}

			}
		}
		if (progress != null)
			progress.checkForCancel();

		//Construct the corresponding set of weighted splits
		final var splitList = new ArrayList<ASplit>();
		for (var i = 1; i <= n; i++) {
			final var A = new BitSet();
			for (var j = i + 1; j <= n; j++) {
				A.set(cycle[j - 1]);
				if (x[i][j] > params.cutoff || A.cardinality() == 1 || A.cardinality() == n - 1) { // positive weight or trivial split
					splitList.add(new ASplit(A, n, max(0, x[i][j])));
				}
			}
		}

		//System.err.println("Completed split weight calculation in "+(System.currentTimeMillis()-before)+" ms");
		return splitList;
	}

	static public ArrayList<ASplit> computeUse1D(int[] cycle, double[][] distances, NNLSParams params, ProgressListener progress) throws CanceledException {
		var n = cycle.length - 1;  //Number of taxa
		var npairs = n * (n - 1) / 2;

		long before = System.currentTimeMillis();

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

		double[] Atd = new double[npairs];
		calcAtx(d, Atd, n);
		double normAtd = sqrt(sumArraySquared(Atd, n));
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
					activeSetMethod(x, d, n, params, progress);
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

			}
			if (params.activeCleanup) {
				params.cgnrTolerance = params.projGradBound / 2;
				params.cgnrIterations = max(50, n * (n - 1) / 2);
				params.activeSetRho = 0.4;
				activeSetMethod(x, d, n, params, progress);
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

		//System.err.println("Completed split weight calculation in "+(System.currentTimeMillis()-before)+" ms");
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

//		for(int i=1;i<=n;i++)
//			for(int j=i+1;j<=n;j++)
//				System.err.print(x[i][j]+", ");
//		System.err.println();


		calcAx(x, r);
		for (var i = 1; i <= n; i++)
			for (var j = i + 1; j <= n; j++)
				r[i][j] = d[i][j] - r[i][j];

		calcAtx(r, z);
		maskElements(z, activeSet);
		copyArray(z, p);
		double ztz = sumArraySquared(z);
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

//			for(int i=1;i<=n;i++)
//				for(int j=i+1;j<=n;j++)
//					System.err.print(x[i][j]+", ");
//			System.err.println();


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
		//System.err.println("Exit CGNR - running time = "+(System.currentTimeMillis()-before)+" \t num iterations = "+k);

		return k;
	}

	static public int cgnr(double[] x, double[] d, boolean[] activeSet, int n, NNLSParams params, double[][] scratch, ProgressListener progress) throws CanceledException {

		if (params.cgnrPrintResiduals && params.log == null)
			System.err.println("Error with initialising log file");

		var p = scratch[0];
		var r = scratch[1];
		var z = scratch[2];
		var w = scratch[3];

		zeroNegativeEntries(x);

//		for(int i=0;i<x.length;i++)
//			System.err.print(x[i]+", ");
//		System.err.println();

		calcAx(x, r, n);
		for (var i = 0; i < r.length; i++)
			r[i] = d[i] - r[i];

		calcAtx(r, z, n);
		maskElements(z, activeSet);
		copyArray(z, p);
		double ztz = sumArraySquared(z, n);
		var k = 1;

		while (true) {
			calcAx(p, w, n);
			var alpha = ztz / sumArraySquared(w, n);

			for (var i = 0; i < x.length; i++) {
				x[i] += alpha * p[i];
				r[i] -= alpha * w[i];
			}
			calcAtx(r, z, n);
			maskElements(z, activeSet);
			var ztz2 = sumArraySquared(z, n);
			var beta = ztz2 / ztz;

//			for(int i=0;i<x.length;i++)
//				System.err.print(x[i]+", ");
//			System.err.println();


			if (ztz2 < params.cgnrTolerance || k >= params.cgnrIterations)
				break;

			for (var i = 0; i < p.length; i++) {
				p[i] = z[i] + beta * p[i];
			}
			ztz = ztz2;

			if (params.cgnrPrintResiduals)
				System.err.println("\t" + k + "\t" + ztz + "\t" + beta);

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

		//TODO add progress listener support.
		long startTime = System.currentTimeMillis();

		var n = x.length - 1;
		boolean[][] activeSet = new boolean[n + 1][n + 1];
		getActiveEntries(x, activeSet);

		double[][] xstar = new double[n + 1][n + 1];
		double[][] grad = new double[n + 1][n + 1];

		int k = 0;

		while (true) {
			while (true) {
				copyArray(x, xstar);
				int numIterations = cgnr(xstar, d, activeSet, params, progress);
				k++;
				if (progress != null)
					progress.checkForCancel();

				boolean xstarFeasible = feasibleMoveActiveSet(x, xstar, activeSet, params);


				if (xstarFeasible && numIterations < params.cgnrIterations)
					break;
				if (k > params.maxIterations)
					return;
			}
			copyArray(xstar, x);
			double pg = evalProjectedGradientSquared(x, d);
			if (pg < params.projGradBound) {
				//System.err.println("Exiting new Active Set. numInner="+numInnerLoops+"\tnumOuter="+numOuterLoops);
				return;
			}

			//At this point x is feasible, but not necessarily a solution to the equality constrained problem.
			//Determine if there is an active constraint which can be removed.


			evalGradient(x, d, grad);
			int imin = 0, jmin = 0;
			var gradmin = 0.0;

			for (int i = 1; i <= n; i++) {
				for (int j = i + 1; j <= n; j++) {
					double g_ij = grad[i][j];
					if (activeSet[i][j] && g_ij < gradmin) {
						gradmin = g_ij;
						imin = i;
						jmin = j;
					}
				}
			}
			if (gradmin < 0.0)
				activeSet[imin][jmin] = false;
		}

	}

	static public void activeSetMethod(double[] x, double[] d, int n, NNLSParams params, ProgressListener progress) throws CanceledException {

		var startTime = System.currentTimeMillis();

		int npairs = n * (n - 1) / 2;
		boolean[] activeSet = new boolean[npairs];
		getActiveEntries(x, activeSet);

		double[] xstar = new double[npairs];
		double[][] scratch = new double[4][npairs]; //Allocate memory
		int[] scratchInt = new int[npairs];
		Integer[] scratchInteger = new Integer[npairs];

		int k = 0;

		while (true) {
			while (true) {
				copyArray(x, xstar);

				int numIterations = cgnr(xstar, d, activeSet, n, params, scratch, progress);
				k++;
				if (progress != null)
					progress.checkForCancel();

				boolean xstarFeasible = feasibleMoveActiveSet(x, xstar, activeSet, scratchInt, scratchInteger, scratch[0], n, params);

				if (params.printResiduals) {
					params.log.print("\t" + k + "\t" + params.cgnrIterations + "\t" + params.activeSetRho + "\t" + (System.currentTimeMillis() - startTime) + "\t" + evalProjectedGradientSquared(x, d, scratch[0], scratch[1], n) + "\t" + numberNonzero(x));
					params.log.println();
				}

				if (xstarFeasible && numIterations < params.cgnrIterations)
					break;
				if (k > params.maxIterations)
					return;
			}
			copyArray(xstar, x);
			//Compute the projected gradient
			double[] pgrad = scratch[0];

//			System.err.print("x=");
//			for(int i=0;i<npairs;i++)
//				System.err.print(x[i]+", ");
//			System.err.println();


			evalGradient(x, d, pgrad, scratch[1], n);

//			System.err.print("grad=");
//			for(int i=0;i<npairs;i++)
//				System.err.print(pgrad[i]+", ");
//			System.err.println();


			for (int i = 0; i < npairs; i++)
				if (x[i] == 0)
					pgrad[i] = min(0, pgrad[i]);
			double pg = sumArraySquared(pgrad, n);

			if (pg < params.projGradBound) {
				//System.err.println("Exiting Active set\tpg=" + pg + "\t target = "+ +params.projGradBound+"\tNumber iterations=" + k);
				//System.err.println("Exiting new Active Set. numInner="+numInnerLoops+"\tnumOuter="+numOuterLoops);
				return;
			}

			//At this point x should be appox feasible, but not necessarily a solution to the equality constrained problem.
			//Determine if there is an active constraint which can be removed.
			int imin = 0;
			var pgradmin = 0.0;

			for (int i = 0; i < npairs; i++) {
				double g_i = pgrad[i];
				if (activeSet[i] && g_i < pgradmin) {
					pgradmin = g_i;
					imin = i;
				}
			}
			if (pgradmin < 0.0)
				activeSet[imin] = false;
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
		for (int i = 1; i <= n; i++)
			for (int j = i + 1; j <= n; j++)
				if (xstar[i][j] < 0)
					sortedPairs.insert(i, j, x[i][j] / (x[i][j] - xstar[i][j]));

		sortedPairs.sort();
		SortedPairs.Entry firstEntry = sortedPairs.get(0);
		if (firstEntry == null) {  //Should never get here
			copyArray(xstar, x);
			return true;
		}
		double t = firstEntry.val; //max val of t before first constraint met.

		//A proportion rho of the indices for which xstar is negative is added to the active set.
		int numToMakeActive = max(1, (int) Math.ceil(sortedPairs.nentries * params.activeSetRho));
		int index = 0;
		SortedPairs.Entry entry = sortedPairs.get(index);
		while (index < numToMakeActive && entry != null) {
			int i = entry.i;
			int j = entry.j;
			activeSet[i][j] = true;
			x[i][j] = 0.0;
			index++;
			entry = sortedPairs.get(index);
		}

		//System.err.println("tmin = "+t);

		for (int i = 1; i <= n; i++)
			for (int j = i + 1; j <= n; j++) {
				if (!activeSet[i][j]) {
					x[i][j] = (1 - t) * x[i][j] + t * xstar[i][j];
				}
			}
		return false;
	}

	static private boolean feasibleMoveActiveSet(double[] x, double[] xstar, boolean[] activeSet, int[] splits, Integer[] indices, double[] vals, int n, NNLSParams params) {

		int npairs = n * (n - 1) / 2;
		int count = 0;

		for (int i = 0; i < npairs; i++) {
			if (xstar[i] < 0) {
				vals[count] = x[i] / (x[i] - xstar[i]);
				splits[count] = i;
				indices[count] = count;
				count++;
			}
		}
		if (count == 0) {
			copyArray(xstar, x);
			return true;
		}

		Arrays.sort(indices, 0, count, Comparator.comparingDouble(i -> vals[i]));
		double tmin = vals[indices[0]];
		int numToMakeActive = max(1, (int) Math.ceil(count * params.activeSetRho));
		for (int i = 0; i < numToMakeActive; i++)
			activeSet[splits[indices[i]]] = true;

		//System.err.println("tmin = "+tmin);


		for (int i = 0; i < npairs; i++)
			if (activeSet[i])
				x[i] = 0;
			else
				x[i] = (1 - tmin) * x[i] + tmin * xstar[i];
		return false;
	}


	/**
	 * Utility class to sort indices
	 */
	private static class SortedPairs {
		public static class Entry {
			public Entry(int i, int j, double val) {
				this.i = i;
				this.j = j;
				this.val = val;
			}

			double val;
			int i;
			int j;
		}

		public int nentries;
		private final Entry[] entries;

		public SortedPairs(int n) {
			nentries = 0;
			entries = new Entry[n * (n - 1) / 2];
		}

		public void insert(int i, int j, double val) {
			entries[nentries] = new Entry(i, j, val);
			nentries++;
		}

		public void sort() {
			Arrays.sort(entries, 0, nentries, Comparator.comparingDouble(o -> o.val));
		}

		public Entry get(int index) {
			if (index < 0 || index >= nentries)
				return null;
			else
				return entries[index];
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
		double[][] p = new double[n + 1][n + 1];

		for (var k = 1; k <= params.maxIterations; k++) {

			evalGradient(x, d, p);
			scale(p, -1);

			projectedGCP(x, p, d, params);

			if (progress != null)
				progress.checkForCancel();

			double pg = evalProjectedGradientSquared(x, d);

			if (params.printResiduals) {
				System.err.println(k + "\t" + (System.currentTimeMillis() - startTime) + "\t" + pg + "\t" + numberNonzero(x));
			}
//			System.err.print("X=");
//			for(int i=1;i<=n;i++)
//				for(int j=i+1;j<=n;j++)
//					System.err.print(x[i][j]+", ");
//			System.err.println();
//
			//System.err.println("k="+k+"\tOld pg = "+pg);

			if (pg < params.projGradBound) {
				System.err.println(" Terminating gradient projection. \t\tpg=" + evalProjectedGradientSquared(x, d) + "\ttarget = " + params.projGradBound);
				return;
			}
			if (progress != null)
				progress.checkForCancel();
		}
	}

	static public void gradientProjection(double[] x, double[] d, int n, NNLSParams params, ProgressListener progress) throws CanceledException {
		var npairs = n * (n - 1) / 2;
		var startTime = System.currentTimeMillis();
		double[] p = new double[npairs];
		double[][] scratch = new double[2][npairs];


		for (var k = 1; k <= params.maxIterations; k++) {

			evalGradient(x, d, p, scratch[0], n);
			scale(p, -1);

			projectedGCP(x, p, d, scratch[0], scratch[1], n, params);

//			System.err.print("\tx=");
//			for(int i=0;i<npairs;i++)
//				System.err.print(x[i]+", ");
//			System.err.println();


			if (progress != null)
				progress.checkForCancel();

			double pg = evalProjectedGradientSquared(x, d, scratch[0], scratch[1], n);
			if (params.printResiduals) {
				params.log.print("\t" + k + "\t" + (System.currentTimeMillis() - startTime) + "\t" + pg + "\t" + numberNonzero(x));
				params.log.println();
			}

			//System.err.println("k="+k+"\tNew pg = "+pg);

			if (pg < params.projGradBound) {
				System.err.println("Exiting GradientProjection\tpg=" + pg + "\t target = " + +params.projGradBound + "\tNumber iterations=" + k);
				return;
			}
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
		int n = x.length - 1;

		double f0 = evalProjectedf(x, 0, p, d);

		//Locate the largest breakpoint. We start the search at 0.5 times this.
		double tlimit = 0.0;
		for (int i = 1; i <= n; i++)
			for (int j = i + 1; j <= n; j++) {
				double t = -x[i][j] / p[i][j];
				if (t > 0)
					tlimit = max(tlimit, t);
			}

		double tmin = 0, tmax = Double.MAX_VALUE, tk;
		if (tlimit > 0.0)
			tk = 0.5 * tlimit;
		else {
			//For all i,j either p[i][j] >= 0 or x[i][j] = 0 and p[i][j] <0.
			//We jump to an exact solution.
			double[][] phat = new double[n + 1][n + 1];
			for (int i = 1; i <= n; i++)
				for (int j = i + 1; j <= n; j++) {
					phat[i][j] = max(p[i][j], 0.0);
				}
			/* Min 0.5 (A(x+tp) - d)'(A(x+tp) - d) = 0.5 t^2 p'A'Ap - t d'Ap + const
			t = (Ax-d)'Ap / pA'Ap
			 */
			double[][] Ap = new double[n + 1][n + 1];
			double[][] Ax = new double[n + 1][n + 1];
			calcAx(phat, Ap);
			calcAx(x, Ax);
			double rAp = 0.0, pAAp = 0.0;
			for (int i = 1; i <= n; i++)
				for (int j = i + 1; j <= n; j++) {
					double Ap_ij = Ap[i][j];
					double r_ij = Ax[i][j] - d[i][j];
					rAp += Ap_ij * r_ij;
					pAAp += Ap_ij * Ap_ij;
				}
			tk = -rAp / pAAp; //TODO: Replace rAp with phat^T phat.
		}

		while (true) {
			double fk = evalProjectedf(x, tk, p, d);
			double ptz = 0.0;
			for (int i = 1; i <= n; i++)
				for (int j = i + 1; j <= n; j++)
					ptz += p[i][j] * (max(x[i][j] + tk * p[i][j], 0) - x[i][j]);

			if (fk > f0 - params.gcp_ku * ptz) {
				//Too far.
				tmax = tk;
				tk = 0.5 * (tmin + tmax);
			} else {
				double wsum = 0.0;
				for (int i = 1; i <= n; i++)
					for (int j = i + 1; j <= n; j++) {
						double pij = p[i][j];
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
					for (int i = 1; i <= n; i++)
						for (int j = i + 1; j <= n; j++)
							x[i][j] = max(x[i][j] + tk * p[i][j], 0.0);
					return;
				}

			}
		}
	}

	private static void projectedGCP(double[] x, double[] p, double[] d, double[] scratch1, double[] scratch2, int n, NNLSParams params) {
		int npairs = n * (n - 1) / 2;

		double f0 = evalProjectedf(x, 0, p, d, scratch1, scratch2, n);

		//Locate the largest breakpoint. We start the search at 0.5 times this.
		double tlimit = 0.0;
		for (int i = 0; i < npairs; i++) {
			double t = -x[i] / p[i];
			if (t > 0)
				tlimit = max(tlimit, t);
		}

		double tmin = 0, tmax = Double.MAX_VALUE, tk;
		if (tlimit > 0.0)
			tk = 0.5 * tlimit;
		else {
			//For all i,j either p[i][j] >= 0 or x[i][j] = 0 and p[i][j] <0.
			//We jump to an exact solution.
			for (int i = 0; i < npairs; i++)
				scratch1[i] = max(p[i], 0.0); //scratch1 = phat

			/* Min 0.5 (A(x+tp) - d)'(A(x+tp) - d) = 0.5 t^2 p'A'Ap - t d'Ap + const
			t = (Ax-d)'Ap / pA'Ap
			 */
			calcAx(scratch1, scratch2, n); //scratch2 = A * phat
			calcAx(x, scratch1, n); //scratch1 = Ax

			double rAp = 0.0, pAAp = 0.0;
			for (int i = 0; i < npairs; i++) {
				double Ap_i = scratch2[i];
				double r_i = scratch1[i] - d[i];
				rAp += Ap_i * r_i;
				pAAp += Ap_i * Ap_i;
			}
			tk = -rAp / pAAp; //TODO: Replace rAp with phat^T phat.
		}

		while (true) {
			double fk = evalProjectedf(x, tk, p, d, scratch1, scratch2, n);
			double ptz = 0.0;
			for (int i = 0; i < npairs; i++)
				ptz += p[i] * (max(x[i] + tk * p[i], 0) - x[i]); //TODO: Check numerical stability here

			if (fk > f0 - params.gcp_ku * ptz) {
				//Too far.
				tmax = tk;
				tk = 0.5 * (tmin + tmax);
			} else {
				double wsum = 0.0;
				for (int i = 0; i < npairs; i++) {
					double pij = p[i];
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
					for (int i = 0; i < npairs; i++)
						x[i] = max(x[i] + tk * p[i], 0.0);
					return;
				}

			}
		}
	}


	static private double evalProjectedf(double[][] x, double t, double[][] p, double[][] d) {
		int n = x.length - 1;

		double[][] xk = new double[n + 1][n + 1];
		double[][] Axk = new double[n + 1][n + 1];

		for (int i = 1; i <= n; i++)
			for (int j = i + 1; j <= n; j++)
				xk[i][j] = max(x[i][j] + t * p[i][j], 0);
		calcAx(xk, Axk);

		double diff = 0.0;
		for (int i = 1; i <= n; i++)
			for (int j = i + 1; j <= n; j++) {
				double rij = Axk[i][j] - d[i][j];
				diff += rij * rij;
			}

		return 0.5 * (diff - sumArraySquared(d));
	}

	static private double evalProjectedf(double[] x, double t, double[] p, double[] d, double[] xk, double[] resk, int n) {
		int npairs = n * (n - 1) / 2;


		for (int i = 0; i < npairs; i++)
			xk[i] = max(x[i] + t * p[i], 0);
		calcAx(xk, resk, n);
		for (int i = 0; i < npairs; i++)
			resk[i] -= d[i];

		return 0.5 * (sumArraySquared(resk, n) - sumArraySquared(d, n)); //TODO - could pass d'd
	}

	static public void APGD(double[][] x, double[][] d, NNLSParams params, ProgressListener progress) throws CanceledException {
		var n = x.length - 1;

		zeroNegativeEntries(x);
		long startTime = System.currentTimeMillis();

		var L = estimateMatrixNorm(n);

		double theta_old;
		double theta = params.APGDtheta;


		var y = new double[n + 1][n + 1];
		var g = new double[n + 1][n + 1];
		var x_old = new double[n + 1][n + 1];

		copyArray(x, y);
		int k = 0;
		var error_old = evalResidual(x, d);

		while (true) {
			//Store previous variables
			copyArray(x, x_old);
			theta_old = theta;

			//Compute acceleration parameters
			double a2 = theta * theta;
			theta = 0.5 * (-a2 + theta * sqrt(a2 + 4));
			double beta = theta_old * (1 - theta_old) / (a2 + theta);

			//Update
			evalGradient(y, d, g);
			for (var i = 1; i <= n; i++) {
				for (var j = i + 1; j <= n; j++) {
					x[i][j] = max(y[i][j] - (1.0 / L) * g[i][j], 0.0);
				}
			}
			for (int i = 1; i <= n; i++) {
				for (int j = i + 1; j <= n; j++) {
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

			double pg = evalProjectedGradientSquared(x, d);
			if (params.printResiduals && k % 10 == 0) {
				params.log.println("\t" + k + "\t" + params.APGDtheta + "\t" + pg + "\t" + (numberNonzero(x)));
			}
			if (pg < params.projGradBound || k >= params.maxIterations) {
				System.err.println("Exiting (old) AGPD\tpg=" + pg + "\tk=" + k);
				return;
			}
			if (progress != null)
				progress.checkForCancel();
			k++;
		}
	}


	static public void APGD(double[] x, double[] d, int n, NNLSParams params, ProgressListener progress) throws CanceledException {
		var npairs = n * (n - 1) / 2;
		var startTime = System.currentTimeMillis();

		zeroNegativeEntries(x);
		double[] scratch = new double[npairs];
		var L = estimateMatrixNorm(n);

		double theta_old;
		double theta = params.APGDtheta;

		var y = new double[npairs];
		var g = new double[npairs];
		var x_old = new double[npairs];

		copyArray(x, y);
		int k = 0;
		var error_old = evalResidual(x, d, scratch, n);

		while (true) {
			//Store previous variables
			copyArray(x, x_old);
			theta_old = theta;

			//Compute acceleration parameters
			double a2 = theta * theta;
			theta = 0.5 * (-a2 + theta * sqrt(a2 + 4));
			double beta = theta_old * (1 - theta_old) / (a2 + theta);

			//Update
			evalGradient(y, d, g, scratch, n);
			for (var i = 0; i < npairs; i++)
				x[i] = max(y[i] - (1.0 / L) * g[i], 0.0);

			for (int i = 0; i < npairs; i++)
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

			double pg = evalProjectedGradientSquared(x, d, g, scratch, n);
			if (params.printResiduals && k % 10 == 0) {
				params.log.println("\t" + k + "\t" + pg + "\t" + (System.currentTimeMillis() - startTime) + "\t" + (numberNonzero(x)));
			}
			if (pg < params.projGradBound || k >= params.maxIterations) {
				System.err.println("Exiting AGPD\tpg=" + pg + "\tk=" + k);
				return;
			}
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
		double dsum = 0.0;
		for (int i = 1; i < n; i++)
			for (int j = i + 1; j <= n; j++)
				dsum += d[i][j];
		for (int i = 1; i < n; i++)
			for (int j = i + 1; j <= n; j++)
				x[i][j] += dsum / (n * n * n);

		int k;
		var g = new double[n + 1][n + 1];
		var y = new double[n + 1][n + 1];
		var z = new double[n + 1][n + 1];
		var p = new double[n + 1][n + 1];
		var xmapped = new double[n + 1][n + 1];


		for (k = 1; k <= params.maxIterations; k++) {
			evalGradient(x, d, g);
			calcAx(x, y);
			calcAtx(y, z);  //z = A'Ax
			for (int i = 1; i <= n; i++)
				for (int j = i + 1; j <= n; j++)
					p[i][j] = -x[i][j] / z[i][j] * g[i][j];

			double alphahat = Double.MAX_VALUE;
			for (int i = 1; i <= n; i++)
				for (int j = i + 1; j <= n; j++)
					if (p[i][j] < 0)
						alphahat = min(alphahat, -x[i][j] / p[i][j]);

			calcAx(p, y);
			double ptg = 0.0, ptAtAp = 0.0;
			for (int i = 1; i <= n; i++)
				for (int j = i + 1; j <= n; j++) {
					ptg += p[i][j] * g[i][j];
					ptAtAp += y[i][j] * y[i][j];
				}
			double alphastar = -ptg / ptAtAp;
			double alpha = min(params.IPGtau * alphahat, alphastar);
			for (int i = 1; i <= n; i++)
				for (int j = i + 1; j <= n; j++) {
					x[i][j] += alpha * p[i][j];
				}

			copyArray(x, xmapped);
			threshold(xmapped, params.IPGthreshold);
			double pg = evalProjectedGradientSquared(xmapped, d);
			if (params.printResiduals)
				params.log.println(k + "\t" + (System.currentTimeMillis() - startTime) + "\t" + pg + "\t" + numberNonzero(xmapped));
			if (pg < params.projGradBound || (startTime - System.currentTimeMillis()) > params.maxTime) {
				copyArray(xmapped, x);
				return;
			}
			if (progress != null)
				progress.checkForCancel();
		}

	}

	static public void IPG(double[] x, double[] d, int n, NNLSParams params, ProgressListener progress) throws CanceledException {

		var npairs = n * (n - 1) / 2;
		long startTime = System.currentTimeMillis();


		//Initial x needs to be strictly positive.
		zeroNegativeEntries(x);
		double offset = 0.0;
		for (int i = 0; i < npairs; i++)
			offset += d[i];
		offset *= 1.0 / (n * n * n);
		for (int i = 0; i < npairs; i++)
			x[i] += offset;

		int k;
		var g = new double[npairs];
		var y = new double[npairs];
		var z = new double[npairs];
		var p = new double[npairs];
		var xmapped = new double[npairs];
		var scratch = new double[npairs];


		for (k = 1; k <= params.maxIterations; k++) {
			evalGradient(x, d, g, scratch, n);
			calcAx(x, y, n);
			calcAtx(y, z, n);  //z = A'Ax
			for (int i = 0; i < npairs; i++)
				p[i] = -x[i] / z[i] * g[i];

			double alphahat = Double.MAX_VALUE;
			for (int i = 0; i < npairs; i++)
				if (p[i] < 0)
					alphahat = min(alphahat, -x[i] / p[i]);

			calcAx(p, y, n);
			double ptg = 0.0, ptAtAp = 0.0;
			for (int i = 0; i < npairs; i++) {
				ptg += p[i] * g[i];
				ptAtAp += y[i] * y[i];
			}
			double alphastar = -ptg / ptAtAp;
			double alpha = min(params.IPGtau * alphahat, alphastar);
			for (int i = 0; i < npairs; i++)
				x[i] += alpha * p[i];


			copyArray(x, xmapped);
			threshold(xmapped, params.IPGthreshold);
			double pg = evalProjectedGradientSquared(xmapped, d, g, scratch, n);
			if (params.printResiduals)
				params.log.println(k + "\t" + (System.currentTimeMillis() - startTime) + "\t" + pg + "\t" + numberNonzero(xmapped));
			if (pg < params.projGradBound || (startTime - System.currentTimeMillis()) > params.maxTime) {
				copyArray(xmapped, x);
				return;
			}
			if (progress != null)
				progress.checkForCancel();
		}

	}


}

