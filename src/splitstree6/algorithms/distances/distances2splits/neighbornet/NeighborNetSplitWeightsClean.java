package splitstree6.algorithms.distances.distances2splits.neighbornet;

import jloda.util.CanceledException;
import jloda.util.progress.ProgressListener;
import org.apache.commons.math3.analysis.UnivariateFunction;
import org.apache.commons.math3.optim.MaxEval;
import org.apache.commons.math3.optim.nonlinear.scalar.GoalType;
import org.apache.commons.math3.optim.univariate.BrentOptimizer;
import org.apache.commons.math3.optim.univariate.SearchInterval;
import org.apache.commons.math3.optim.univariate.UnivariateObjectiveFunction;
import splitstree6.data.parts.ASplit;

import java.io.PrintWriter;
import java.util.*;

import static java.lang.Math.*;
import static splitstree6.algorithms.distances.distances2splits.neighbornet.NeighborNetUtilities.*;
import static splitstree6.algorithms.distances.distances2splits.neighbornet.SquareArrays.copyArray;


//import static splitstree6.algorithms.distances.distances2splits.neighbornet.SquareArrays.*;

//TODO Make the arrays upper triangular

public class NeighborNetSplitWeightsClean {

	/**
	 * NNLSParams
	 * <p>
	 * Parameter files describing the method choice and options
	 */
	public static class NNLSParams {

		public enum MethodTypes {GRADPROJECTION, ACTIVESET, APGD, IPG}

		public MethodTypes method = MethodTypes.GRADPROJECTION;

		public enum ProjectedLineSearches {NOCEDALWRIGHT, BRENTS_F, BRENTS_PG, BRENTS_FIXED}

		//These are the options for line searches.
		//  NOCEDALWRIGHT is the method we were using. Should work well for small ntax, but
		//    appears to take too much time for larger number of taxa as it spends ages
		//    doing tiny steps.
		//  BRENTS_F uses the apache implementation of Brent's optimization algorithm, and
		//    minimizes the sum of squares difference. A problem is that performance seems
		//    to be really dependent on the tolerance levels (termination conditions) and
		//    can think of no good way to select these.
		//  BRENTS_PG is the same as BRENTS_F, expect that it minimizes the squared norm
		//    of the projected gradient instead of the sum of squares. I thought this
		//    would work better since that is the criterion we use as a stopping
		//    condition for the whole algorithm. Unfortunately it doesnt seem to work. I
		//    don't understand why - perhaps bugs?
		//  BRENTS_FIXED is experimental - no convergence error, just we run the algorithm
		//    for a fixed number of iterations. I haven't been able to test this.

		public ProjectedLineSearches linesearchMethod = ProjectedLineSearches.BRENTS_F;
		public int brentsIterations = 20; //Number of iterations to use in fixed algorithm
		public double gradProjLinesearchBound;

		public double cutoff = 0.0001; //Post processing: Only include split weights greater than this amount

		//Stopping conditions - main method
		public double projGradBound = 1e-5; //Stop if squared projected gradient is less than this. This should be larger than the CGNR bound
		//TODO This should be linked to n and also to the condition of the Hessian. Someone?

		public int maxIterations = Integer.MAX_VALUE;
		public long maxTime = Long.MAX_VALUE; //Stop if the method has taken more than this many milliseconds

		//Stopping conditions - ProjectedGradient
		double gcp_kl = 0.9;
		double gcp_ku = 0.2;
		double gcp_ke = 0.3;



		//Stopping conditions - CGNR
		public int cgnrIterations; //Max number of iterations in CGNR
		public double cgnrTolerance = projGradBound / 2; //Stopping condition for CGNR - bound on norm gradient squared.

		public boolean abortIfNegative = false;  //Abort CGNR if x becomes infeasible.

		//Debugging
		public boolean printResiduals = false;
		public boolean cgnrPrintResiduals = false;
		public PrintWriter log;

		//Legacy options
		public double activeSetRho;
		public double APGDalpha;
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

		var x = new double[n + 1][n + 1]; //array of split weights
		calcAinv_y(d, x); //Compute unconstrained solution
		var minVal = minArray(x);
		if (minVal < 0) {
			zeroNegativeEntries(x);
			switch (params.method) {
				case GRADPROJECTION -> {
					params.cgnrTolerance = (0.0001 * normAtd) * (0.0001 * normAtd);
					params.gradProjLinesearchBound = 1e-3 * normAtd;
					params.projGradBound = params.cgnrTolerance;

					gradientProjection(x, d, params, progress);
				}
				case ACTIVESET -> {
					params.cgnrTolerance = (0.0001 * normAtd) * (0.0001 * normAtd) / 2;

					params.projGradBound = params.cgnrTolerance;
					params.cgnrIterations = max(50, n * (n - 1) / 2);
					params.activeSetRho = 0.4;
					long before = System.currentTimeMillis();
					activeSetMethod(x, d, params, progress);
					System.err.println("Active set finished in " + (System.currentTimeMillis() - before) + " ms");
				}
				case APGD -> {
					params.APGDalpha = 1.0;
					APGD(x, d, params,progress);
				}
//                case IPG -> {
//                    IPG(x,d,params,progress);
//                }

			}
		}
		//TODO: Catch the cancel here so that the incomplete solution can still be used.

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
		return splitList;
	}

	//*************************************************************************
	// METHOD IMPLEMENTATIONS
	//  **************************************************************************/

	/**
	 * cgnr
	 *
	 * Implementation of the CGNR algorithm in Saad, "Iterative Methods for Sparse Linear Systems", applied to the
	 * problem of minimizing ||Ax - d|| such that x_{ij} = 0 for all ij in the activeSet.
	 *
	 * @param x             Initial value, overwritten with final value. Initially, we set x[i][j] = 0 for all [i][j] in the active set.
	 * @param d             square array of distances
	 * @param activeSet     square array of boolean: specifying active (zero) set.
	 * @param params        parameters - uses params.cgnrIterations for max number of iterations
	 *                      and params.tolerance for bound on gradient at convergence
	 * @return int          number of iterations
	 */
	static public int cgnr(double[][] x, double[][] d, boolean[][] activeSet, NNLSParams params, ProgressListener progress) throws CanceledException {
		//TODO add progress listener support.

		long before = System.currentTimeMillis();

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

			if (ztz2 < params.cgnrTolerance || k >= params.cgnrIterations)
				break;
			if (params.abortIfNegative) {
				double backstep = 0;
				for (int i = 1; i <= n; i++)
					for (int j = i + 1; j <= n; j++) {
						if (x[i][j] < 0) {
							backstep = max(backstep, x[i][j] / p[i][j]);
						}
					}
				if (backstep > 0) {
					for (int i = 1; i <= n; i++) {
						for (int j = i + 1; j <= n; j++) {
							x[i][j] -= backstep * p[i][j];
						}
					}
					break;
				}
			}

			for (var i = 1; i <= n; i++) {
				for (var j = i + 1; j <= n; j++) {
					p[i][j] = z[i][j] + beta * p[i][j];
				}
			}
			ztz = ztz2;

			if (params.cgnrPrintResiduals)
				System.err.println("\t" + k + "\t" + ztz);

			k++;
			if (progress != null && (k % n) == 0)
				progress.checkForCancel();
		}
		//System.err.println("Exit CGNR - running time = "+(System.currentTimeMillis()-before)+" \t num iterations = "+k);

		return k;
	}


	/**
	 * activeSetMethod
	 *
	 * Implement the active set method for minimizing ||Ax-d|| over non-negative x.
	 * @param x starting value - assumed feasible. Overwritten by solution.
	 * @param d vector of distances
	 * @param params method parameters. We use the following:
	 *
	 * @param progress pointer to progress bar.
	 * @throws CanceledException  User cancels calculation.
	 */
	static public void activeSetMethod(double[][] x, double[][] d, NNLSParams params, ProgressListener progress) throws CanceledException {

		//TODO add progress listener support.

		//TODO: NEW VS OLD
		int numInnerLoops = 0;
		int numOuterLoops = 0;
		long startTime = System.currentTimeMillis();

		var n = x.length - 1;
		boolean[][] activeSet = new boolean[n + 1][n + 1];
		getActiveEntries(x, activeSet);

		double[][] xstar = new double[n + 1][n + 1];
		double[][] grad = new double[n + 1][n + 1];

		int k = 0;

		while (true) {
			while (true) {

				//TODO NEW VS OLD
				numInnerLoops++;

				copyArray(x, xstar);
				int numIterations = cgnr(xstar, d, activeSet, params, progress);
				k++;
				if (progress != null)
					progress.checkForCancel();

				boolean xstarFeasible = feasibleMoveActiveSet(x, xstar, activeSet, params);

				if (params.printResiduals) {
					params.log.print("\t" + k + "\t" + (System.currentTimeMillis() - startTime) + "\t" + evalProjectedGradientSquared(x, d) + "\t" + (n * (n - 1) / 2 - cardinality(activeSet)));
					params.log.println();
				}

				if (xstarFeasible && numIterations < params.cgnrIterations)
					break;
				if (k > params.maxIterations)
					return;
			}

			numOuterLoops++;

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


	/**
	 * Move the point x towards point xstar, while still maintaining feasibility.
	 *
	 * If xstar is feasible, then returns true. Otherwise, the activeset will change and a proportion
	 * params.activeSetrho of the infeasible entries in xstar will be added to the active set.
	 * @param x square matrix --- feasible initial point
	 * @param xstar   square matrix ---  target point, will generally be infeasible
	 * @param activeSet    current active set. assumed that xstar will satisfy the active set, and x will be moved
	 *                     to a point satisfying the active set
	 * @param params    uses parameter activeSetRho, which is the proportion of infeasible entries of xstar which will
	 *                  be added to the active set.
	 * @return true if xstar is feasible, false otherwise
	 */
	static private boolean feasibleMoveActiveSet(double[][] x, double[][] xstar, boolean[][] activeSet, NNLSParams params) {
		var n = xstar.length - 1;

		//First check if xstar is feasible, and return true if it is after moving x to xstar
		if (minArray(xstar) >= 0.0) {
			copyArray(xstar,x);
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

		for (int i = 1; i <= n; i++)
			for (int j = i + 1; j <= n; j++) {
				if (!activeSet[i][j]) {
					x[i][j] = (1 - t) * x[i][j] + t * xstar[i][j];
				}
			}
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
			entries = new Entry[n*(n-1)/2];
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




	//TODO: Javadoc
	static public void gradientProjection(double[][] x, double[][] d, NNLSParams params, ProgressListener progress) throws CanceledException {
		var n = x.length - 1;
		long startTime = System.currentTimeMillis();

		double[][] xstar = new double[n + 1][n + 1];
		double[][] p = new double[n + 1][n + 1];
		double[][] Ap = new double[n + 1][n + 1];
		var activeSet = new boolean[n + 1][n + 1];

		boolean printDebugInfo = false;

		for (var k = 1; k <= params.maxIterations; k++) {

			System.err.println(" \t\tRSS, top of iteration = \t"+evalProjectedf(x,0,p,d)+"\t\t"+evalProjectedGradientSquared(x, d));


			//Search direction
			evalGradient(x, d, p);
			scale(p, -1);
			double relErr = 0.01;
			double absErr = 0.01;


			//projectedLineSearchBrents(x, p, d, relErr, absErr);
			//projectedLineSearch(x,p,d);
			//projectedLineSearchM1(x,p,d);
			//projectedLineSearchNoDerivatives(x, p, d, 0.1);
			projectedGCP(x,p,d,params);


			System.err.println(" RSS, after line search = \t"+evalProjectedf(x,0,p,d)+"\t\t"+evalProjectedGradientSquared(x, d));

			getActiveEntries(x, activeSet);

			if (progress != null)
				progress.checkForCancel();
			//LOCAL SEARCH
			//copyArray(x, xstar);
			params.cgnrIterations = max(100, n);

			//params.abortIfNegative = true;
			cgnr(x, d, activeSet, params, progress); //Just a few iterations of CG
			//params.abortIfNegative = false;


			//Move towards xstar as far as possible while preserving feasibility
			double mint = 1.0;
			for (int i = 1; i <= n; i++)
				for (int j = i + 1; j <= n; j++)
					if (xstar[i][j] < 0.0) {
						double t_ij = x[i][j] / (x[i][j] - xstar[i][j]);
						mint = min(t_ij,mint);
					}
			for (int i = 1; i <= n; i++)
				for (int j = i + 1; j <= n; j++)
					x[i][j] += mint*(xstar[i][j] - x[i][j]);

			System.err.println(" RSS, after CG  = \t\t\t"+evalProjectedf(x,0,p,d)+"\t\t"+evalProjectedGradientSquared(x, d));

			double pg = evalProjectedGradientSquared(x, d);

			System.err.println(k + "\t" + (System.currentTimeMillis() - startTime) + "\t" + pg + "\t" + (n * (n - 1) / 2 - cardinality(activeSet)));

			if (progress != null)
				progress.checkForCancel();
			if (pg < params.projGradBound) {
//				getActiveEntries(x, activeSet);
//				params.cgnrIterations = n*(n-1)/2;
//				params.cgnrTolerance = 1e-6;
//				cgnr(x, d, activeSet, params, progress); //Just a few iterations of CG to tidy up.


				return;
			}
			//if ((startTime - System.currentTimeMillis()) > params.maxTime)
			//	return;
		}
	}

//TODO: Test methods and remove line search algorithms which don't work well

	/**
	 * //Projected line search, following Nocedal, pg 486-488, though the fact we can rapidly compute Ax means we don't fuss
	 * //around with updating (hopefully will help with accumulating error
	 * //In Nocedal and Wright, the quadratic being minimized is (1/2) x'Gx+ x'c with l <= x <= u and the search
	 * //direction is -g = -(Gx + c)
	 * // For our problem
	 * //   G = A'A, c = -A'd
	 * // and the search direction may or may not equal -(Gx+c).
	 * <p>
	 * //Problem occurs here when n is large as there can be many tvals which are only a tiny bit different, so this procedure takes too long.
	 * //Potential solutions:
	 * // (i) Have a minimum move size, and skip over tvalues until this is achieved. Should probably be adaptive and scale-free.
	 * // (ii) Implement another search method, something like Simpson's method or equivalent. This could be done using plug-in optimizers, and
	 * //          with a bound on the number of iterations.
	 **/

	private static void projectedLineSearch(double[][] x, double[][] p, double[][] d) {


		int n = x.length - 1;
		double tmin;
		TreeSet<Double> tvals = new TreeSet<>();
		for (int i = 1; i <= n; i++)
			for (int j = i + 1; j <= n; j++) {
				double t = -x[i][j] / p[i][j];
				if (p[i][j] < 0)
					tvals.add(t);
			}

		double[][] xk = new double[n + 1][n + 1];
		double[][] pk = new double[n + 1][n + 1];
		double[][] Ap = new double[n + 1][n + 1];
		double[][] Ax = new double[n + 1][n + 1];

		double left, right = 0.0;

		Iterator<Double> tval = tvals.iterator();
		while (true) {
			left = right;
			if (!tval.hasNext())
				right = Double.MAX_VALUE;
			else
				right = tval.next();

			for (int i = 1; i <= n; i++)
				for (int j = i + 1; j <= n; j++) {
					xk[i][j] = max(x[i][j] + left * p[i][j], 0.0);
					if (xk[i][j] > 0)
						pk[i][j] = p[i][j]; //This is the vector p^{j-1} in NW.
					else
						pk[i][j] = 0.0;
				}
			calcAx(pk, Ap);
			calcAx(xk, Ax);
			double pAtAp = 0.0;
			double pAtr = 0.0;
			double f = 0.0;

			for (int i = 1; i <= n; i++)
				for (int j = i + 1; j <= n; j++) {
					double APij = Ap[i][j];
					pAtAp += APij * APij; //f''
					double rij = (Ax[i][j] - d[i][j]);
					pAtr += APij * rij;   //f'
					f += Ax[i][j] * (0.5 * Ax[i][j] - d[i][j]);
				}

			//System.err.println("t = "+left+"\tf = "+f+"\tf'="+pAtr+"\tfdd="+pAtAp);


			double step = -pAtr / pAtAp;

			if (step < 0) {
				tmin = left;
				break;
			} else if (step < (right - left)) {
				tmin = left + step;
				break;
			}

		}

		for (int i = 1; i <= n; i++)
			for (int j = i + 1; j <= n; j++)
				x[i][j] = max(x[i][j] + tmin * p[i][j], 0.0);

	}

	private static void projectedLineSearchM1(double[][] x, double[][] p, double[][] d) {
		int n = x.length - 1;
		double tmin;
		TreeSet<Double> tvals = new TreeSet<>();
		for (int i = 1; i <= n; i++)
			for (int j = i + 1; j <= n; j++) {
				double t = -x[i][j] / p[i][j];
				if (p[i][j] < 0)
					tvals.add(t);
			}

		double[][] xk = new double[n + 1][n + 1];
		double[][] pk = new double[n + 1][n + 1];
		double[][] Ap = new double[n + 1][n + 1];
		double[][] Ax = new double[n + 1][n + 1];

		double left, right = 0.0;
		double f_left, f_right;
		f_right = evalProjectedf(x, 0, p, d);

		Iterator<Double> tval = tvals.iterator();
		boolean lastInterval = false;

		while (true) {
			left = right;
			f_left = f_right;

			if (!tval.hasNext()) {
				lastInterval = true;
				right = 2 * f_left; //We only use this to interpolate the quadratic
			} else
				right = tval.next();
			f_right = evalProjectedf(x, right, p, d);

			copyArray(p, pk);
			double tmid = (left + right) / 2;
			for (int i = 1; i <= n; i++)
				for (int j = i + 1; j <= n; j++)
					if (x[i][j] + tmid * p[i][j] < 0)
						pk[i][j] = 0.0;
			calcAx(pk, Ap);
			double pAtAp = sumArraySquared(Ap);
			//Determine the quadratic with q(0)=f_left, q(right-left) = f_right and q''(midpoint) = pAtAp
			//Minimize this to find the next step.

			double step = (right - left) / 2 - (f_right - f_left) / ((right - left) * pAtAp);
			if (step < 0) {
				tmin = left;
				break;
			} else if (step < right - left || lastInterval) {
				tmin = left + step;
				break;
			}
		}
		for (int i = 1; i <= n; i++)
			for (int j = i + 1; j <= n; j++)
				x[i][j] = max(x[i][j] + tmin * p[i][j], 0.0);

	}

	/**
	 * Rough projected line search
	 * <p>
	 * The aim of this algorithm is to conduct a rough projected line search.
	 * We only evaluate function values (not derivatives), and continue until the fvalue starts increasing.
	 * If we go past all the t-values, we return a tvalue well past the end. If not, we return a t-value
	 * which is at a midpoint of the interval of increase. The idea is that the CG step is better for
	 * optimizing once the face has been identified.
	 *
	 * @param x
	 * @param p
	 * @param d
	 */
	private static void projectedLineSearchNoDerivatives(double[][] x, double[][] p, double[][] d, double threshold) {
		int n = x.length - 1;
		double tmin;
		TreeSet<Double> tvals = new TreeSet<>();
		for (int i = 1; i <= n; i++)
			for (int j = i + 1; j <= n; j++) {
				double t = -x[i][j] / p[i][j];
				if (p[i][j] < 0)
					tvals.add(t);
			}

		double left, right = 0.0;
		double f_left, f_right;
		f_right = evalProjectedf(x, 0, p, d);

		Iterator<Double> tval = tvals.iterator();
		boolean lastInterval = false;

		double minStep = threshold * (sqrt(sumArraySquared(x)) / sqrt(sumArraySquared(p)));


		while (true) {
			left = right;
			f_left = f_right;
			//	System.err.println("left = "+left+"\tfleft="+f_left);
			while (tval.hasNext()) {
				right = tval.next();
				if (right - left > minStep)
					break;
			}

			if (!tval.hasNext()) {
				tmin = 1.5 * left;
				break;
			}

			f_right = evalProjectedf(x, right, p, d);
			if (f_right > f_left) {
				tmin = (left + right) / 2.0;
				break;
			}
		}
		for (int i = 1; i <= n; i++)
			for (int j = i + 1; j <= n; j++)
				x[i][j] = max(x[i][j] + tmin * p[i][j], 0.0);

	}

	/**
	 * ProjectedGCP
	 * <p>
	 * Adaptation of the algorithm for finding a Generalized Cauchy Point (GCP), due to
	 * Cartis et al. 2011  doi:10.1093/imanum/drr035
	 * but for the case with no regularisation and simple non-negativity points.
	 *
	 * Given x and p, a GCP is a point y = x^GC such that
	 * 		y = [x + tp]_+ for some t >= 0.
	 * 		f(y) <= f(x) - \kappa_u p^T(y-x)
	 * 	and either
	 * 	    f(y) >= f(x) - \kappa_l p^T(y-x)
	 * 	or
	 *      ||w|| \leq \kappa_e  |p^T(y-x)|
	 *  where
	 *      w_i  =   \begin{cases} p_i    if  y_i > 0; max(p_i,0) if y_i = 0 \end{cases}
	 * 	This second condition is equivalent to (2.6) in Cartis et al. Given y, the tangent
	 * 	cone for the positive quadrant at y is {w:w_i \geq 0 for all i such that y_i = 0.}
	 *		The three constants (in the params) are tuning factors which satisfy
	 *			0 < k_u < k_l < 1 and k_e \in (0,0.5)
	 *
	 *   The search algorithm
	 *
	 * @param x  Starting value.This is overwritten by xGC, the Generalized Cauchy point
	 * @param p  Search direction, typically equal to -\grad f(x)
	 * @param d  Distances
	 * @param params  Parameters. WE use gcp_ku,gcp_kl,gcp_ke.
	 */
	private static void projectedGCP(double[][] x, double[][] p, double[][] d, NNLSParams params) {
		int n = x.length - 1;

		double f0 = evalProjectedf(x,0,p,d);

		//Locate the largest breakpoint. We start the search at 0.5 times this.
		double tlimit = 0.0;
		for (int i = 1; i <= n; i++)
			for (int j = i + 1; j <= n; j++) {
				double t = -x[i][j] / p[i][j];
				if (p[i][j] < 0 && t>tlimit)
					tlimit = t;
			}


		double tmin = 0, tmax = Double.MAX_VALUE, tk;
		if (tlimit > 0.0)
			tk = 0.5*tlimit;
		else {
			//For all i,j either p[i][j] >= 0 or x[i][j] = 0 and p[i][j] <0.
			//We jump to an exact solution.
			double[][] phat = new double[n+1][n+1];
			for(int i=1;i<=n;i++)
				for(int j=i+1;j<=n;j++) {
					phat[i][j] = max(p[i][j],0.0);
				}
			/* Min 0.5 (A(x+tp) - d)'(A(x+tp) - d) = 0.5 t^2 p'A'Ap - t d'Ap + const
			t = d'Ap / pA'Ap
			 */
			double[][] Ap = new double[n+1][n+1];
			calcAx(phat,Ap);
			double dAp=0.0, pAAp = 0.0;
			for(int i=1;i<=n;i++)
				for(int j=i+1;j<=n;j++) {
					double Ap_ij = Ap[i][j];
					dAp += Ap_ij * d[i][j];
					pAAp+= Ap_ij * Ap_ij;
				}
			tk = dAp/pAAp;
		}

		while(true) {
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


		//System.err.println("\t\t\t\t\t\tt="+t+"\tdiff="+diff);

		return 0.5*(diff - sumArraySquared(d));
	}



	static private double evalProjectedGrad(double[][] x, double t, double[][] p, double[][] d) {
		int n = x.length - 1;

		double[][] xk = new double[n + 1][n + 1];

		for (int i = 1; i <= n; i++)
			for (int j = i + 1; j <= n; j++)
				xk[i][j] = max(x[i][j] + t * p[i][j], 0);
		double pg = evalProjectedGradientSquared(xk, d);
		//System.err.println("\t\t\t\t\t\tt=" + t + "\tpg=" + pg);
		return pg;
	}


	static private void projectedLineSearchBrents(double[][] x, double[][] p, double[][] d, double rel, double abs) {
		//Projected line search, we perform an (approximate) line search to optimize
		// || A[x + tp]_+ - d ||
		// where x is the current position, p is the search direction, and [y]_+ denotes the vector with all negative
		//entries replaced by 0. Here t \in [0,1]
		//Find the max val of t.
		int n = x.length - 1;
		double tmin;
		double maxt = -1;
		for (int i = 1; i <= n; i++)
			for (int j = i + 1; j <= n; j++) {
				double t = -x[i][j] / p[i][j];
				if (p[i][j] < 0)
					maxt = max(t, maxt);
			}
		if (maxt < 0) {
			//Search direction always feasible, so we use the unconstrained minimum.
			double[][] Ax = new double[n + 1][n + 1];
			double[][] Ap = new double[n + 1][n + 1];
			calcAx(x, Ax);
			calcAx(p, Ap);
			double ptr = 0.0;
			double pAtAp = sumArraySquared(Ap);

			for (int i = 1; i <= n; i++)
				for (int j = i + 1; j <= n; j++)
					ptr += p[i][j] * (Ax[i][j] - d[i][j]);
			tmin = ptr / pAtAp;
		} else {
			final UnivariateFunction fn = t -> evalProjectedf(x, t, p, d);

			var optimizer = new BrentOptimizer(rel, abs); //TODO Make these options
			var result = optimizer.optimize(new MaxEval(100), new UnivariateObjectiveFunction(fn),
					GoalType.MINIMIZE, new SearchInterval(0, maxt));

			tmin = result.getPoint();
			//System.err.println("\t\t\ttmin=\t" + tmin+"\t+val="+result.getValue()+"\tstart val = "+evalProjectedf(x,0.0,p,d)+"\timprovement = "+(evalProjectedf(x,0.0,p,d) - result.getValue()) );
		}

		for (int i = 1; i <= n; i++)
			for (int j = i + 1; j <= n; j++)
				x[i][j] = max(x[i][j] + tmin * p[i][j], 0.0);
	}

	static private void projectedLineSearchBrentsPG(double[][] x, double[][] p, double[][] d, double maxt, double rel, double abs) {
		//Projected line search, we perform an (approximate) line search to optimize
		// || A[x + tp]_+ - d ||
		// where x is the current position, p is the search direction, and [y]_+ denotes the vector with all negative
		//entries replaced by 0. Here t \in [0,1]

		var n = x.length - 1;
		final UnivariateFunction fn2 = t -> evalProjectedGrad(x, t, p, d);

		var optimizer = new BrentOptimizer(rel, abs); //TODO Make these options
		var result = optimizer.optimize(new MaxEval(100), new UnivariateObjectiveFunction(fn2),
				GoalType.MINIMIZE, new SearchInterval(0, maxt));

		var tmin = result.getPoint();
		// System.err.println("\t\t\ttmin=\t" + tmin+"\t+val="+result.getValue()+"\tstart val = "+evalProjectedGrad(x,0.0,p,d)+"\timprovement = "+(evalProjectedGrad(x,0.0,p,d) - result.getValue()) );
		for (int i = 1; i <= n; i++)
			for (int j = i + 1; j <= n; j++)
				x[i][j] = max(x[i][j] + tmin * p[i][j], 0.0);
	}

	static private void projectedLineSearchBrentsFixed(double[][] x, double[][] p, double[][] d, double maxt, int numIterations) {
		//Projected line search, we perform an (approximate) line search to optimize
		// || A[x + tp]_+ - d ||
		// where x is the current position, p is the search direction, and [y]_+ denotes the vector with all negative
		//entries replaced by 0. Here t \in [0,1]

		var n = x.length - 1;
		final UnivariateFunction fn = t -> evalProjectedf(x, t, p, d);

		var optimizer = new BrentOptimizer(0, 0);
		var result = optimizer.optimize(new MaxEval(numIterations), new UnivariateObjectiveFunction(fn),
				GoalType.MINIMIZE, new SearchInterval(0, maxt));

		var tmin = result.getPoint();
		// System.err.println("\t\t\ttmin=\t" + tmin+"\t+val="+result.getValue()+"\tstart val = "+evalProjectedGrad(x,0.0,p,d)+"\timprovement = "+(evalProjectedGrad(x,0.0,p,d) - result.getValue()) );
		for (int i = 1; i <= n; i++)
			for (int j = i + 1; j <= n; j++)
				x[i][j] = max(x[i][j] + tmin * p[i][j], 0.0);
	}


	static public void APGD(double[][] x, double[][] d, NNLSParams params, ProgressListener progress) throws CanceledException {
		var n = x.length - 1;

		zeroNegativeEntries(x);
		long startTime = System.currentTimeMillis();

		var L = estimateMatrixNorm(n);

		double alpha_old;
		double alpha = params.APGDalpha;



		var y = new double[n + 1][n + 1];
		var g = new double[n + 1][n + 1];
		var x_old = new double[n + 1][n + 1];

		copyArray(x, y);
		int k = 0;
		var error_old = evalResidual(x, d);

		while (true) {
			//Store previous variables
			copyArray(x, x_old);
			alpha_old = alpha;

			//Compute acceleration parameters
			double a2 = alpha * alpha;
			alpha = 0.5 * (-a2 + alpha * sqrt(a2 + 4));
			double beta = alpha_old * (1 - alpha_old) / (a2 + alpha);

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
				alpha = params.APGDalpha;
			}

			double pg = evalProjectedGradientSquared(x, d);
			if (params.printResiduals && k % 10 == 0) {
				params.log.println("\t" + k + "\t" + (System.currentTimeMillis() - startTime) + "\t" + pg + "\t" + (numberNonzero(x)));
			}
			if (pg < params.projGradBound || k >= params.maxIterations || (startTime - System.currentTimeMillis()) > params.maxTime)
				return;
			if (progress!=null)
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

}

