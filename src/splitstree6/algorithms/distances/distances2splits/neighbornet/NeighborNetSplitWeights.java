package splitstree6.algorithms.distances.distances2splits.neighbornet;

import jloda.fx.util.Print;
import jloda.fx.window.NotificationManager;
import jloda.util.CanceledException;
import jloda.util.progress.ProgressListener;
import org.apache.commons.math3.analysis.UnivariateFunction;
import org.apache.commons.math3.optim.MaxEval;
import org.apache.commons.math3.optim.nonlinear.scalar.GoalType;
import org.apache.commons.math3.optim.univariate.BrentOptimizer;
import org.apache.commons.math3.optim.univariate.SearchInterval;
import org.apache.commons.math3.optim.univariate.UnivariateObjectiveFunction;
import splitstree6.data.parts.ASplit;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;

import static java.lang.Math.*;
import static splitstree6.algorithms.distances.distances2splits.neighbornet.IncrementalFitting.incrementalFitting;
import static splitstree6.algorithms.distances.distances2splits.neighbornet.NeighborNetSplitstree4.activeSetST4;
import static splitstree6.algorithms.distances.distances2splits.neighbornet.SquareArrays.*;

public class NeighborNetSplitWeights {
	private static int countCalls = 0;
	private static long timeCalls = 0L;

	public static boolean verbose = false;

	public static class NNLSParams {

		public boolean plotGraphs = false;    //SET this to true to generate residual vs time graphs for the paper.

		public NNLSParams(int ntax) {
			cgIterations = min(max(ntax, 10), 25);
			outerIterations = max(ntax, 10);
		}

		static public final int GRADPROJECTION = 0;
		static public final int ACTIVE_SET = 1;
		static public final int PROJECTEDGRAD = 2;
		static public final int BLOCKPIVOT = 3;
		static public final int SBB = 4;

		//static public int

		public int nnlsAlgorithm = GRADPROJECTION;
		public double tolerance = 1e-6; //Approximate tolerance in split weights
		public boolean useRetroCGbound = true; //Use the old-fashioned stopping criterion for Conjugate Gradients
		public boolean greedy = false;
		public boolean useInsertionAlgorithm = true; //Use taxon insertion algorithm for the initial split weights
		public boolean useGradientNorm = false; //Use new projected gradient norm stopping condition
		public int cgIterations; //Max number of iterations on the calls to conjugate gradients.
		public int outerIterations; //Max number of iterations through the outer loop
		public boolean collapseMultiple = false; //Collapse multiple negative splits (ST4 only)
		public double fractionNegativeToCollapse = 0.6; //Propostion of negative splits to collapse (ST4 only)
		public double kktBound = tolerance / 100;
		public boolean printConvergenceData = false;
		public int faceSearch = GRADPROJECTION;

		public double relativeErrorBound = 0.1; //Approx bound on relative numerical error in split weights
		public double pgbound = 1e-4; //Bound on the projective gradient norm

		public String logfile = null;
		public String logArrayName = "convergenceData";
		public PrintWriter log = null;
		public long startTime;
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
		countCalls = 0;
		timeCalls = 0L;
		var startTime = System.currentTimeMillis();

		var n = cycle.length - 1;  //Number of taxa

		//testIncremental(20);

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
				d[i][j] = d[j][i] = distances[cycle[i] - 1][cycle[j] - 1];

		if (params.plotGraphs)
			NeighborNetTest.ActiveSetGraphs(d);


		var x = new double[n + 1][n + 1]; //array of split weights
		params.startTime = System.currentTimeMillis(); //Start time of calculation (for profiling)

		if (params.nnlsAlgorithm == NNLSParams.ACTIVE_SET) {
			activeSetST4(x, d, params.log, params,progress);  //ST4 Algorithm
		} else {
			calcAinvx(d,x); //Compute unconstrained solution
			if (minArray(x) >= -params.tolerance)
				makeNegElementsZero(x); //Fix roundoff
			else {
				if (params.useInsertionAlgorithm)
					incrementalFitting(x, d, params.tolerance / 100,true);
				else
					fill(x,1.0);
				if (params.nnlsAlgorithm == NNLSParams.PROJECTEDGRAD)
					acceleratedProjectedGradientDescent(x, d, params, progress);
				else if (params.nnlsAlgorithm == NNLSParams.BLOCKPIVOT)
					blockPivot(x, d, params, progress);
				else if (params.nnlsAlgorithm == NNLSParams.GRADPROJECTION)
					projectedConjugateGradient(x, d, params, progress);
				else {
					//Debugging: fill x with ones.
					for(var i=1;i<=n;i++)
						for(var j=i+1;j<=n;j++) {
							x[i][j] = x[j][i] = 1.0;
						}
					params.tolerance = 1e-5;
					params.outerIterations = 10000;
					subspaceBB(x, d, params, progress);
				}
			}
		}

		//Construct the corresponding set of weighted splits
		final var splitList = new ArrayList<ASplit>();
		var cutoff = params.tolerance / 10;
		for (var i = 1; i <= n; i++) {
			final var A = new BitSet();
			for (var j = i + 1; j <= n; j++) {
				A.set(cycle[j - 1]);
				if (x[i][j] > cutoff || A.cardinality() == 1 || A.cardinality() == n - 1) { // positive weight or trivial split
					splitList.add(new ASplit(A, n, Math.max(0, (float) (x[i][j]))));
				}
			}
		}
		if (verbose) {
			System.err.printf("Total time: %.3f seconds%n", (System.currentTimeMillis() - startTime) / 1000.0);
			System.err.println("countCalls: " + countCalls);
			System.err.printf("timeCalls:  %.3f seconds%n", timeCalls / 1000.0);
		}

		return splitList;
	}

	static private void projectedConjugateGradient(double[][] x, double[][] d, NNLSParams params, ProgressListener progress) throws CanceledException {
		var n = x.length - 1;
		var f = new NNLSFunctionObject(n);
		var fx_old = f.evalf(x, d);
		var activeSet = getZeroElements(x);

		MethodTime timer = new MethodTime(params.startTime);
		if (params.log!=null) {
			if (params.nnlsAlgorithm==NNLSParams.GRADPROJECTION)
				params.log.println("% GradientProjection algorithm");
			else
				params.log.println("% Active set algorithm");
			if (params.collapseMultiple) {
				params.log.println("% Collapse Multiple: fraction to collapse"+params.fractionNegativeToCollapse);
			}
			params.log.println("% active and projected C");
			params.log.println("% cg iterations: "+params.cgIterations);
			params.log.println("% cutoff "+params.tolerance * 1e-3);
			params.log.println("% cg tolerance ||res|| < " + Math.sqrt(params.tolerance));
			params.log.println("% Line search tolerance " + params.tolerance);
			params.log.println(params.logArrayName+" = [");
		}



		for (var k = 1; k <= params.outerIterations; k++) {
			var optimalForFace = searchFace(x, d, activeSet, f, params);
			var fx = f.evalf(x, d);
			if (optimalForFace) {
				if (params.greedy && params.log!=null) {
					double fxlog = residualNorm(x,d);
					double pgx = projGradNorm(x,d);
					params.log.println("\t"+timer.get()+"\t"+Math.sqrt(fxlog)+"\t"+Math.sqrt(pgx)+"\t"+numNonzeroEntries(x));
					params.log.println("];");
					return;
				}
				//boolean finished = projGradNorm(x,d) < params.pgbound;
				boolean finished = checkKKT(x,d,activeSet,params);
				if (finished) {
					if (params.log!=null) {
						double fxlog = residualNorm(x,d);
						double pgx = projGradNorm(x,d);
						params.log.println("\t"+timer.get()+"\t"+Math.sqrt(fxlog)+"\t"+Math.sqrt(pgx)+"\t"+numNonzeroEntries(x));
						params.log.println("];");

					}
					return;
				}
			}
			fx_old = fx;
			progress.checkForCancel();

			if (params.log!=null) {
				double fxlog = residualNorm(x,d);
				double pgx = projGradNorm(x,d);
				params.log.println("\t"+timer.get()+"\t"+Math.sqrt(fxlog)+"\t"+Math.sqrt(pgx)+"\t"+numNonzeroEntries(x));
				System.err.println("\t"+timer.get()+"\t"+Math.sqrt(fxlog)+"\t"+Math.sqrt(pgx)+"\t"+numNonzeroEntries(x));
			}



		}
		if (params.log!=null)
			params.log.println("];");
		NotificationManager.showError("Neighbor-net projected CG algorithm failed to converge");

	}


	/**
	 * Search a face of the nnls problem
	 * <p>
	 * Minimizes ||Ax - d|| subject to the constraint that x_ij = 0 whenever activeSet[i][j] = true.
	 * <p>
	 * Uses at most n iterations of the conjugate gradient algorithm for normal equations (cf CGNR in Saad's book)
	 * When that converges, or when all iterations are completed, and collapseMultiple is selected, then
	 * a proportion of negative weight splits are added to the active set and CGNR starts up again.
	 * (1) if projectedMin is true, considers points along the projection of the line segment connecting the
	 * initial value of x and the result of the CG, where the 'projection' of a vector xt is just obtained
	 * by setting xt[i][j]=0 whenever xt[i][j]<0, otherwise
	 * (2) picks the last feasible point on the path from initial x to the final x.
	 *
	 * @param x         Initial point, overwritten with final point
	 * @param d         SquareArray of distances
	 * @param activeSet Square array of boolean, indicating which entries are constrained to zero
	 * @param params    options - search depends on nnls algorithm
	 * @return boolean. True if method finishes with x as the approx minimizer of the current face.
	 */
	static private boolean searchFace(double[][] x, double[][] d, boolean[][] activeSet, NNLSFunctionObject f, NNLSParams params) {
		var n = x.length - 1;
		var x0 = new double[n + 1][n + 1];
		boolean cgConverged = true;
		copyArray(x, x0);

		if (isEmpty(activeSet))
			calcAinvx(d,x);
		else
			cgConverged = cgnr(x, d, activeSet, params, f);

		if (params.collapseMultiple) {
			int before = countTrueEntries(activeSet);
			filterMostNegative(x, activeSet, params.fractionNegativeToCollapse);
			int after = countTrueEntries(activeSet);

			System.err.println("Collapsed from "+before+" entries to "+after+" entries");

			maskElements(x, activeSet);
			cgConverged = cgnr(x, d, activeSet, params, f);
		}

		if (minArray(x) < 0) {
			if (params.faceSearch == NNLSParams.GRADPROJECTION) {
				//Use gradient projection to return the best projection of points on the line between x0 and x
				brentProjection(x, x0, d, f, params.tolerance);
			} else
				furthestFeasible(x, x0, params.tolerance);
			getZeroElements(x, activeSet);
			return false;
		} else
			return cgConverged;
	}

	/**
	 * Implementation of the CGNR algorithm in Saad, "Iterative Methods for Sparse Linear Systems", applied to the
	 * problem of minimizing ||Ax - d|| such that x_{ij} = 0 for all ij in the activeSet.
	 *
	 * @param x             Initial value, overwritten with final value
	 * @param d             square array of distances
	 * @param activeSet     square array of boolean: specifying active set.
	 * @return boolean  true if the method converged (didn't hit max number of iterations)
	 */
	static private boolean cgnr(double[][] x, double[][] d, boolean[][] activeSet, NNLSParams params, NNLSFunctionObject f) {
		var n = x.length - 1;
		var tol = params.tolerance;
		var  maxIterations = params.cgIterations;
		final double CG_EPSILON = 0.0001;

		if (false)
			System.err.println("\t\tEntering cgnr. fx = " + f.evalf(x, d) + "\t" + f.evalfprojected(x, d));





		var p = new double[n + 1][n + 1];
		var r = new double[n + 1][n + 1];
		calcAx(x, r);
		for (var i = 1; i <= n; i++)
			for (var j = 1; j <= n; j++)
				r[i][j] = d[i][j] - r[i][j];
		var z = new double[n + 1][n + 1];
		if (params.useRetroCGbound) {
			calcAtx(x,z);
			tol = CG_EPSILON*CG_EPSILON*sumSquares(z);
		}

		calcAtx(r, z);
		var w = new double[n + 1][n + 1];
		maskElements(z, activeSet);
		copyArray(z, p);
		double ztz = sumArraySquared(z);

		var k = 1;

		while (true) {
			calcAx(p, w);
			var alpha = ztz / sumArraySquared(w);

			for (var i = 1; i <= n; i++) {
				for (var j = 1; j <= n; j++) {
					x[i][j] += alpha * p[i][j];
					r[i][j] -= alpha * w[i][j];
				}
			}

			calcAtx(r, z);
			maskElements(z, activeSet);
			var ztz2 = sumArraySquared(z);
			var beta = ztz2 / ztz;

			if (ztz2 < tol || k >= maxIterations)
				break;

			for (var i = 1; i <= n; i++) {
				for (var j = 1; j <= n; j++) {
					p[i][j] = z[i][j] + beta * p[i][j];
				}
			}
			ztz = ztz2;
			k++;
		}
		return (k < maxIterations);
	}


	/**
	 * Determine the most negative entries in x. Finds the largest threshold t so that a proportion of
	 * fractionNegativeToKeep entries have values at least t. Those entries with value less than t are added to the
	 * active set.
	 * At present this runs in O(n^2 + k log k) where k is the number of negative entries. Could be made simpler?
	 * This method is here in order to emulate the ST4 algorithm.
	 *
	 * @param x                      square array of doubles
	 * @param activeSet              activeset. Entries with most negative values are added to this
	 * @param fractionNegativeToCollapse  double. Minimum fraction of the negative entries to collapse to 0.
	 */
	static private void filterMostNegative(double[][] x, boolean[][] activeSet, double fractionNegativeToCollapse) {
		var numNeg = 0;
		var n = x.length - 1;
		for (var i = 1; i <= n; i++)
			for (var j = i + 1; j <= n; j++)
				if (!activeSet[i][j] && x[i][j] < 0)
					numNeg++;
		if (numNeg == 0)
			return;
		var vals = new double[numNeg];
		var k = 0;
		for (var i = 1; i <= n; i++)
			for (var j = i + 1; j <= n; j++)
				if (!activeSet[i][j] && x[i][j] < 0) {
					vals[k] = x[i][j];
					k++;
				}
		Arrays.sort(vals);
		var numToKeep = (int) ceil(numNeg * (1.0-fractionNegativeToCollapse));
		double threshold;
		if (numToKeep == 0)
			threshold = 0.0;
		else
			threshold = vals[numNeg - numToKeep];
		//Make active all entries with weight strictly less than the threshold.
		for (var i = 1; i <= n; i++)
			for (var j = i + 1; j <= n; j++) {
				if (!activeSet[i][j] && x[i][j] < threshold) {
					activeSet[i][j] = true;
					activeSet[j][i] = true;
				}
			}
	}

	static public class NNLSFunctionObject {
		//Utility class for evaluating ||Ax - b|| without additional allocation.
		private final double[][] xt;
		private final double[][] Axt;

		NNLSFunctionObject(int n) {
			xt = new double[n + 1][n + 1];
			Axt = new double[n + 1][n + 1];
		}

		public double evalf(double[][] x, double[][] d) {
			calcAx(x, Axt);
			var n = x.length - 1;
			var fx = 0.0;
			for (var i = 1; i <= n; i++) {
				double fx_i = 0.0;
				for (var j = 1; j <= n; j++) {
					var res_ij = Axt[i][j] - d[i][j];
					fx_i += res_ij * res_ij;
				}
				fx += fx_i;
			}
			return sqrt(fx);
		}

		public double evalfprojected(double t, double[][] x0, double[][] x, double[][] d) {
			var n = x.length - 1;
			for (var i = 1; i <= n; i++)
				for (var j = i + 1; j <= n; j++) {
					xt[i][j] = xt[j][i] = max(x0[i][j] * (1 - t) + x[i][j] * t, 0.0);
				}
			return evalf(xt, d);
		}

		public double evalfprojected(double[][] x, double[][] d) {
			var n = x.length - 1;
			for (var i = 1; i <= n; i++)
				for (var j = 1; j <= n; j++) {
					xt[i][j] = xt[j][i] = max(x[i][j], 0.0);
				}
			return evalf(xt, d);
		}
	}


	/**
	 * Minimizes ||Ax-b|| along the projection of the line segment between x0 and x, where the projection of a point
	 * is the closest point in the non-negative quadrant.
	 *
	 * @param x         square array   final point, overwritten by optimal point
	 * @param x0        square array  initial point
	 * @param d         square array of distances
	 * @param tolerance tolerance used for golden section search
	 */
	static public double brentProjection(double[][] x, double[][] x0, double[][] d, NNLSFunctionObject f, double tolerance) {
		final UnivariateFunction fn = t -> f.evalfprojected(t, x0, x, d);

		var optimizer = new BrentOptimizer(1e-10, tolerance);
		var result = optimizer.optimize(new MaxEval(10000), new UnivariateObjectiveFunction(fn),
				GoalType.MINIMIZE, new SearchInterval(0, 1.0));

		var tmin = result.getPoint();
		var n = x.length - 1;
		for (var i = 1; i <= n; i++) {
			for (var j = i + 1; j <= n; j++) {
				var newx_ij = max((1 - tmin) * x0[i][j] + tmin * x[i][j], 0);
				x[i][j] = x[j][i] = newx_ij;
			}
		}
		return tmin;

	}

	/**
	 * Determines the point on the path from x0 to x that is furthest from x0 and still feasible.
	 *
	 * @param x         square array, final point
	 * @param x0        square array, initial point
	 * @param tolerance tolerance. Any entry of x less than tolerance is mapped to zero.
	 */
	static private void furthestFeasible(double[][] x, double[][] x0, double tolerance) {
		var tmin = 1.0;
		var n = x.length - 1;

		for (var i = 1; i <= n; i++) {
			for (var j = i+1; j <= n; j++) {
				if (x[i][j] < 0) {
					var t_ij = x0[i][j] / (x0[i][j] - x[i][j]);
					tmin = min(tmin, t_ij);
				}
			}
		}

		for (var i = 1; i <= n; i++) {
			for (var j = i + 1; j <= n; j++) {
				var x_ij = (1.0 - tmin) * x0[i][j] + tmin * x[i][j];
				if (x_ij < tolerance)
					x_ij = 0;
				x[i][j] = x[j][i] = x_ij;
			}
		}
	}

	/**
	 * Computes circular distances from an array of split weights.
	 *
	 * @param x split weights. Symmetric array. For i<j, x(i,j)  is the weight of the
	 *          split {i,i+1,...,j-1} | rest.
	 * @param d square array, overwritten with circular metric corresponding to these split weights.
	 */
	static public void calcAx(double[][] x, double[][] d) {
		countCalls++;
		var startTime = System.currentTimeMillis();
		var n = x.length - 1;

		for (var i = 1; i <= (n - 1); i++)
			d[i + 1][i] = d[i][i + 1] = sumSubvector(x[i + 1], i + 1, n) + sumSubvector(x[i + 1], 1, i);

		for (var i = 1; i <= (n - 2); i++) {
			d[i + 2][i] = d[i][i + 2] = d[i][i + 1] + d[i + 1][i + 2] - 2 * x[i + 1][i + 2];
		}

		startTime = System.currentTimeMillis();

		for (var k = 3; k <= n - 1; k++) {
			for (var i = 1; i <= n - k; i++) {  //TODO. This loop can be threaded, but it is not worth it
				var j = i + k;
				d[j][i] = d[i][j] = d[i][j - 1] + d[i + 1][j] - d[i + 1][j - 1] - 2 * x[i + 1][j];
			}
		}

		timeCalls += System.currentTimeMillis() - startTime;
	}

	/**
	 * Sum the elements in the vector over a range of indices.
	 * <p>
	 * Separating this out in case we can improve efficiency with threading.
	 *
	 * @param v    vector
	 * @param from start index  (inclusive)
	 * @param to   end index  (inclusive)
	 * @return \sum_{i=from}^to v(i)
	 */
	static private double sumSubvector(double[] v, int from, int to) {
		var s = 0.0;
		for (var i = from; i <= to; i++)
			s += v[i];
		return s;
	}

	/**
	 * Compute Atx, when x and the result are represented as square arrays
	 *
	 * @param x square array
	 * @param p square array. Overwritten with result
	 */
	static public void calcAtx(double[][] x, double[][] p) {
		var n = x.length - 1;
		//double[][] p = new double[n+1][n+1];

		for (var i = 1; i <= n - 1; i++)
			p[i + 1][i] = p[i][i + 1] = sumSubvector(x[i], 1, n);

		for (var i = 1; i <= n - 2; i++) {  //TODO This can be threaded, but is not worth it
			p[i + 2][i] = p[i][i + 2] = p[i][i + 1] + p[i + 1][i + 2] - 2 * x[i][i + 1];
		}

		for (var k = 3; k <= n - 1; k++) {
			for (var i = 1; i <= n - k; i++) { //TODO. This inner loop can be threaded, but is not worth it
				p[i + k][i] = p[i][i + k] = p[i][i + k - 1] + p[i + 1][i + k] - p[i + 1][i + k - 1] - 2 * x[i][i + k - 1];
			}
		}
	}

	/**
	 * calcAinvx
	 * <p>
	 * Computes A^{-1}(d).
	 * <p>
	 * When d is circular, result will be corresponding weights. If d is not circular, some
	 * elements will be negative.
	 *
	 * @param d square array
	 * @param x square array, replaced nby A^{-1}d.
	 * @return square array
	 */
	static private void calcAinvx(double[][] d, double[][] x) {
		var n = d.length - 1;
		x[1][2] = x[2][1] = (d[1][n] + d[1][2] - d[2][n]) / 2.0;
		for (var j = 2; j <= n - 1; j++) {
			x[1][j] = x[j][1] = (d[j - 1][n] + d[1][j] - d[1][j - 1] - d[j][n]) / 2.0;
		}
		x[1][n] = x[n][1] = (d[1][n] + d[n - 1][n] - d[1][n - 1]) / 2.0;

		for (var i = 2; i <= (n - 1); i++) {
			x[i][i + 1] = (d[i - 1][i] + d[i][i + 1] - d[i - 1][i + 1]) / 2.0;
			for (var j = (i + 2); j <= n; j++)
				x[i][j] = x[j][i] = (d[i - 1][j - 1] + d[i][j] - d[i][j - 1] - d[i - 1][j]) / 2.0;
		}
	}

	/**
	 * checkKKT
	 * <p>
	 * Checks the KKT conditions, under the assumption that x is optimal for the current active set.
	 * If not optimal, elements are removed from the activeSet.
	 *
	 * @param x         square array. point x
	 * @param d         square array of distances, used to compute graient.
	 * @param activeSet current activeSet. Assume activeSet[i][j]=true implies x[i][j]=0.
	 * @param params    Params
	 * @return boolean   true if kkt conditions are (approximately) satisfied.
	 */
	static private boolean checkKKT(double[][] x, double[][] d, boolean[][] activeSet, NNLSParams params) {
		var n = x.length - 1;
		var gradient = new double[n + 1][n + 1];
		evalGradient(x, d, gradient);
		var mingrad = 0.0;
		var maxgrad2 = 0.0;
		var min_i = 0;
		var min_j = 0;
		for (var i = 1; i <= n; i++)
			for (var j = i + 1; j <= n; j++) {
				var grad_ij = gradient[i][j];
				if (activeSet[i][j]) {
					if (grad_ij < mingrad) {
						mingrad = grad_ij;
						min_i = i;
						min_j = j;
					}
				} else {
					maxgrad2 = max(maxgrad2,grad_ij*grad_ij);
				}
			}
		if (mingrad >= -params.kktBound)
			return true;

		//Now remove elements from the active set.
		if (params.nnlsAlgorithm == NNLSParams.ACTIVE_SET) {
			activeSet[min_i][min_j] = activeSet[min_i][min_j] = false;
		} else {
			for (var i = 1; i <= n; i++)
				for (var j = i + 1; j <= n; j++) {
					var grad_ij = gradient[i][j];
					if (activeSet[i][j] && grad_ij < -params.kktBound) {
						activeSet[i][j] = activeSet[j][i] = false;
					}
				}
		}
		return false;
	}

	/**
	 * Compute the gradient at x of 1/2 ||Ax - d||
	 *
	 * @param x        square array
	 * @param d        square array
	 * @param gradient square array, overwritten by the gradient.
	 */
	static private void evalGradient(double[][] x, double[][] d, double[][] gradient) {
		var n = x.length - 1;
		var res = new double[n + 1][n + 1];
		calcAx(x, res);
		for (var i = 1; i <= n; i++)
			for (var j = 1; j <= n; j++)
				res[i][j] -= d[i][j];
		calcAtx(res, gradient);
	}

	static private void projectedGradientDescent(double[][] x, double[][] d, NNLSParams params, ProgressListener progress) throws CanceledException {
		var n = x.length - 1;
		var L = estimateNorm(n);
		var f = new NNLSFunctionObject(n);
		var f_old = f.evalf(x, d);
		var grad = new double[n + 1][n + 1];
		System.err.print("ProjGrad=[");
		while (true) {
			evalGradient(x, d, grad);
			System.err.println("\t" + f_old + "\t" + projGradNorm(x, d));

			for (var i = 1; i <= n; i++) {
				for (var j = i + 1; j <= n; j++) {
					x[i][j] = x[j][i] = max(x[i][j] - (1.0 / L) * grad[i][j], 0.0);
				}
			}
			var f_new = f.evalf(x, d);
			if (f_old - f_new < params.tolerance) {
				System.err.println("\t" + f_new + "\t" + projGradNorm(x, d) + "];");
				break;
			}
			f_old = f_new;
			progress.checkForCancel();
		}
	}

	static private void acceleratedProjectedGradientDescent(double[][] x, double[][] d, NNLSParams params, ProgressListener progress) throws CanceledException {
		var n = x.length - 1;

		//TEMPORARY: SET x to be all ones.
		for(int i=1;i<=n;i++) {
			for(int j=i+1;j<=n;j++)
				x[i][j] = 1.0;
		}

		var L = estimateNorm(n);
		var f = new NNLSFunctionObject(n);
		var f_old = f.evalf(x, d);
		var grad = new double[n + 1][n + 1];
		var alpha0 = 0.5;   //TODO: Find out what to use here - Nesterov

		var alpha_old = alpha0;
		double alpha;








		MethodTime timer = new MethodTime(params.startTime);
		if (params.log!=null) {
			params.log.println("% accelerated Projected Gradient Descent");
			params.log.println("% \t alpha0 = 0.5");
			params.log.println("% time \t ||res|| \t ||proj grad||\n\n");
			params.log.println(params.logArrayName + " = [");
		}


		var y_old = new double[n + 1][n + 1];
		var x_old = new double[n + 1][n + 1];

		copyArray(x, y_old);

		if (params.printConvergenceData)
			System.err.print("AccProjGrad=[");
		while (true) {
			if (params.printConvergenceData)
				System.err.println("\t" + f_old + "\t" + projGradNorm(x, d));


			copyArray(x, x_old);
			evalGradient(y_old, d, grad);
			for (var i = 1; i <= n; i++) {
				for (var j = i + 1; j <= n; j++) {
					x[i][j] = x[j][i] = max(y_old[i][j] - (1.0 / L) * grad[i][j], 0.0);
				}
			}

			var f_new = f.evalf(x, d);
			if (f_new > f_old) {
				evalGradient(x_old, d, grad);
				for (var i = 1; i <= n; i++) {
					for (var j = i + 1; j <= n; j++) {
						x[i][j] = x[j][i] = max(x_old[i][j] - (1.0 / L) * grad[i][j], 0.0);
						y_old[i][j] = y_old[j][i] = x[i][j];
					}
				}
				f_new = f.evalf(x, d);
				alpha = alpha0;
				//} else if (f_old - f_new < params.tolerance) {
				//	break;
			} else if (projGradNorm(x,d)<params.pgbound) {
				break;
			} else {
				var a2 = alpha_old * alpha_old;
				alpha = 0.5 * sqrt(a2 * a2 + 4 * a2) - a2;
				var beta = alpha_old * (1 - alpha_old) / (a2 + alpha);
				for (var i = 1; i <= n; i++) {
					for (var j = i + 1; j <= n; j++) {
						y_old[i][j] = y_old[j][i] = x[i][j] + beta * (x[i][j] - x_old[i][j]);
					}
				}
			}
			alpha_old = alpha;
			f_old = f_new;

			if (params.log!=null) {
				double fx = residualNorm(x,d);
				double pgx = projGradNorm(x,d);
				params.log.println("\t"+timer.get()+"\t"+Math.sqrt(fx)+"\t"+Math.sqrt(pgx)+"\t"+numNonzeroEntries(x));
			}



			progress.checkForCancel();
		}
		if (params.log!=null)
			params.log.println("];");


	}


	/**
	 * Rough estimate of the 2-norm of X'X, which I got in MATLAB by computing the norms and fitting a 4 degree polynomial.
	 *
	 * @param n number of taxa
	 * @return estimate of ||X'X||_2
	 */
	static private double estimateNorm(int n) {
		return (((0.041063124831008 * n + 0.000073540331934) * n + 0.065260125117342) * n + 0.027499142031727) * n - 0.038454953524879;
	}

	static private double residualNorm(double[][] x, double[][] d) {
		var n = x.length - 1;
		var Ax = new double[n+1][n+1];
		calcAx(x,Ax);
		var resnorm = 0.0;
		for (var i = 1; i <= n; i++) {
			for (var j = i + 1; j <= n; j++) {
				double res_ij = Ax[i][j] - d[i][j];
				resnorm += res_ij*res_ij;
			}
		}
		return sqrt(resnorm);
	}

	static private double projGradNorm(double[][] x, double[][] d) {
		var n = x.length - 1;
		var grad = new double[n + 1][n + 1];
		evalGradient(x, d, grad);
		for (var i = 1; i <= n; i++) {
			for (var j = i + 1; j <= n; j++) {
				if (x[i][j] == 0)
					grad[i][j] = min(grad[i][j], 0.0);
			}
		}
		return sqrt(sumSquares(grad));
	}

	/**
	 * Count the number of non-zero entries in the array x (one triangle only)
	 * @param x double array
	 * @return int,number of non-zero entries x[i][j] with i<j.
	 */
	static private int numNonzeroEntries(double[][] x) {
		var count=0;
		var n=x.length-1;
		for (var i = 1; i <= n; i++) {
			for (var j = i + 1; j <= n; j++) {
				if (x[i][j] > 0)
					count++;
			}
		}
		return count;
	}



	/**
	 * Computes an approximate bound on the projected gradient norm which would
	 * give the specified relativeError on the split weights.
	 * @param d distancers
	 * @return
	 */
	static private double estimateProjGradBound(double relativeError, double[][] d) {
		int n = d.length-1;
		double[][] atd = new double[n+1][n+1];
		calcAtx(d,atd);
		//Bound is epsilon * ||A'd|| / ( ||A'A|| ||(A'A)^{-1}|| )
		//return relativeError*sqrt(sumSquares(atd))/ (2*estimateNorm(n));
		return relativeError*sqrt(sumSquares(atd)/estimateNorm(n));
	}



	static private void blockPivot(double[][] x, double[][] d, NNLSParams params, ProgressListener progress) throws CanceledException {
		var n = x.length - 1;
		var cutoff = params.tolerance * 1e-3;
		//Initialise the active set from zero entries of x (note values in x are ignored)
		var G = new boolean[n + 1][n + 1];
		getZeroElements(x, G);

		//gradient
		var y = new double[n + 1][n + 1];

		MethodTime timer = new MethodTime(params.startTime);
		if (params.log!=null) {
			params.log.println("% blockPivot");
			params.log.println("% cg iterations: "+params.cgIterations);
			params.log.println("% cutoff "+params.tolerance * 1e-3);
			params.log.println(params.logArrayName+ " = [");
		}


		//Initial call
		var f = new NNLSFunctionObject(n);
		var converged = cgnr(x, d, G, params, f);


		evalGradient(x, d, y);
		var infeasible = new boolean[n + 1][n + 1];

		var N = Integer.MAX_VALUE;
		var p = 3;
		boolean done = false;
		int iter = 0;

		while (!done) {
			iter++;
			int numBad = numInfeasible(x, y, G, infeasible);
			if (numBad == 0 || iter>=params.outerIterations)
				done = true;
			else if (numBad < N) {
				N = numBad;
				p = 3;
				for (var i = 1; i <= n; i++) {
					for (var j = i + 1; j <= n; j++) {
						G[i][j] = G[j][i] = (G[i][j] ^ infeasible[i][j]);   //XOR. flip G if infeasible true.
					}
				}
			} else {
				if (p > 0) {
					p--;
					for (var i = 1; i <= n; i++) {
						for (var j = i + 1; j <= n; j++) {
							G[i][j] = G[j][i] = (G[i][j] ^ infeasible[i][j]);   //XOR. flip G if infeasible true.
						}
					}
				} else {
					var foundInfeasible = false;
					for (var i = 1; i <= n && !foundInfeasible; i++) {
						for (var j = i + 1; j <= n && !foundInfeasible; j++) {
							if (infeasible[i][j]) {
								G[i][j] = G[j][i] = !G[i][j];
								foundInfeasible = true;
							}
						}
					}
				}
			}
			converged = cgnr(x, d, G, params, f);
			evalGradient(x,d,y);
			progress.checkForCancel();

			for (var i = 1; i <= n; i++) {
				for (var j = i + 1; j <= n; j++) {
					if (abs(x[i][j]) < cutoff)
						x[i][j] = x[j][i] = 0.0;
					if (abs(y[i][j]) < cutoff)
						y[i][j] = y[j][i] = 0.0;
				}
			}

			if (params.log!=null) {
				double fx = residualNorm(x,d);
				double pgx = projGradNorm(x,d);
				params.log.println("\t"+timer.get()+"\t"+Math.sqrt(fx)+"\t"+Math.sqrt(pgx)+"\t"+numNonzeroEntries(x));
			}

		}

		if (params.log!=null)
			params.log.println("];");


	}

	/**
	 * implements subspaceBB algorithm, as described by D. Kim, S. Sra, I. S. Dhillon.
	 * "A non-monotonic method for large-scale non-negative least squares."
	 * Optimization Methods and Software, Jan. 2012
	 *
	 * @param x
	 * @param d
	 * @param params
	 * @param progress
	 * @throws CanceledException
	 */
	static private void subspaceBB(double[][] x, double[][] d, NNLSParams params, ProgressListener progress) throws CanceledException {

		var n = x.length-1;
		var iter = 0;
		double[][] grad = new double[n+1][n+1];
		evalGradient(x,d,grad);
		double[][] oldGrad = new double[n+1][n+1];
		copyArray(grad,oldGrad);
		double[][] Ag = new double[n+1][n+1]; //Scratch matrix used by computeBBstep
		double[][] Ag2 = new double[n+1][n+1]; //Scratch matrix used by computeBBstep

		MethodTime timer = new MethodTime();

		if (params.log!=null) {
			params.log.println("% subspace BB");
			params.log.println("ConvergenceSBB = [");
		}




		while (true) {
			iter++;
			if (iter > params.outerIterations || checkTerminationBB(x,grad,params))
				break;
			double step = computeBBStep(x,grad,oldGrad, iter, Ag, Ag2);
			for(var i=1;i<=n;i++)
				for(var j=i+1;j<=n;j++) {
					x[i][j] -= step * grad[i][j];
					x[j][i] = x[i][j];
				}
			copyArray(grad,oldGrad);
			evalGradient(x,d,grad);

			if (params.log!=null) {
				double fx = residualNorm(x,d);
				double pgx = projGradNorm(x,d);
				params.log.println("\t"+timer.get()+"\t"+Math.sqrt(fx)+"\t"+Math.sqrt(pgx));
			}



		}
		if (params.log!=null)
			params.log.println("];");


	}

	static private boolean checkTerminationBB(double[][] x, double[][] grad, NNLSParams params) {

		var n=x.length-1;

		//Evaluate the norm of the projected gradient
		var norm_pg = 0.0;
		for (var i = 1; i <= n; i++) {
			for (var j = i + 1; j <= n; j++) {
				var g_ij = grad[i][j];
				if (x[i][j] > 0 || g_ij<0)
					norm_pg += g_ij * g_ij;
			}
		}
		System.err.println("\t"+sqrt(norm_pg));
		if (sqrt(norm_pg)<params.tolerance)
			return true;
		return false;
	}

	static double computeBBStep(double[][] x, double[][] grad, double[][] oldGrad, int iter, double[][] Ag, double[][] Ag2) {
		var n=x.length-1;
		double step;

		for(var i=1;i<=n;i++) {
			for(var j=i+1;j<=n;j++) {
				if (x[i][j]==0 && grad[i][j] > 0)
					oldGrad[i][j]=oldGrad[j][i]=0;
			}
		}

		calcAx(oldGrad,Ag);
		if (iter%2==0)
			step = sumSquares(oldGrad) / sumSquares(Ag);
		else {
			double numer = sumSquares(Ag);
			calcAtx(Ag,Ag2);
			for(var i=1;i<=n;i++) {
				for(var j=i+1;j<=n;j++) {
					if (x[i][j]==0 && grad[i][j] > 0)
						Ag2[i][j]=Ag2[j][i]=0;
				}
			}
			step = numer/sumSquares(Ag2);
		}
		return step;
	}


	static private int numInfeasible(double[][] x, double[][] y, boolean[][] G, boolean[][] infeasible) {
		var count = 0;
		var n = x.length - 1;

		for (var i = 1; i <= n; i++) {
			for (var j = i + 1; j <= n; j++) {
				if ((!G[i][j] && x[i][j] < 0) || (G[i][j] && y[i][j] < 0)) {
					count++;
					infeasible[i][j] = infeasible[j][i] = true;
				}
			}
		}
		return count;
	}





	/**
	 * Compute the l-infinity norm of the gradient restricted to G.
	 *
	 * @param x
	 * @param d
	 * @param G
	 * @return
	 */
	private static double debugLS(double[][] x, double[][] d, boolean[][] G) {
		var n = x.length - 1;
		var grad = new double[n + 1][n + 1];
		evalGradient(x, d, grad);
		var maxAbs = 0.0;
		for (var i = 1; i <= n; i++) {
			for (var j = i + 1; j <= n; j++) {
				if (!G[i][j])
					maxAbs = max(abs(grad[i][j]), maxAbs);
			}
		}
		return maxAbs;
	}

	static public double testIncremental(int n) {
		var x = new double[n + 1][n + 1];
		for (var i = 1; i <= n; i++) {
			for (var j = i + 1; j <= n; j++) {
				if (Math.random() < 0.2)
					x[i][j] = x[j][i] = random();
			}
		}
		var d = new double[n + 1][n + 1];
		calcAx(x, d);
		var x2 = new double[n + 1][n + 1];
		incrementalFitting(x2, d, 1e-10,true);
		var diff = 0.0;
		int nmissedZero = 0;
		int nfalseZero = 0;
		int nneg = 0;
		for (var i = 1; i <= n; i++) {
			for (var j = i + 1; j <= n; j++) {
				diff = max(diff, abs(x[i][j] - x2[i][j]));
				if (x[i][j] < 0)
					nneg++;
				if (x[i][j] == 0 && x2[i][j] > 1e-8)
					nmissedZero++;
				if (x[i][j] > 0 && x2[i][j] == 0)
					nfalseZero++;
			}
		}
		System.err.println("Tested incremental fit on circular distance: err = " + diff);
		if (diff > 0.1)
			incrementalFitting(x2, d, 1e-10,true);
		return diff;
	}

	static private class MethodTime {
		private long startTime;
		public MethodTime() {
			reset();
		}
		public MethodTime(long startTime) {
			this.startTime = startTime;
		}
		public long get() {
			return System.currentTimeMillis() - startTime;
		}
		public void reset() {
			startTime = System.currentTimeMillis();
		}
	}

	/************
	 * What follows is code for carrying out the analysis of difference algorithms and parameter values for split weight
	 * computation. This will not be available in a public version (except maybe as an easter egg hidden somewhere).
	 *
	 * The aim is to be able to repeat a lot of analyses exactly for the given dataset, and produce automatic output as
	 * much as possible. For convenience, I'm producing this output for MATLAB (just because I'e been working with
	 * it more recently, and because MATLAB was used to prototype these algorithms.
	 */

	static public ArrayList<ASplit>  evaluateConvergenceAlgorithms(int[] cycle, double[][] distances, ProgressListener progress) throws CanceledException {
		int n = cycle.length-1;
		var params = new NeighborNetSplitWeights.NNLSParams(n);
		ArrayList<ASplit> splitsComputed = null;
		ArrayList<ASplit> splits = null;

		boolean evalST4 = true; //Evaluate Splitstree4 Active Set algorithm
		boolean evalAPG = false;  //Evaluate the accelerated projective gradient algorithm

		boolean evalProjectedCG = true;
		boolean evalBlockPivot = false;


		if (evalST4) {

		/*
		Legacy Splitstree4 Algorithm (with modifications)
 		*/
			params = new NeighborNetSplitWeights.NNLSParams(n);
			params.greedy = false;
			params.nnlsAlgorithm = NNLSParams.ACTIVE_SET;
			params.outerIterations = n * (n - 1) / 2;
			params.collapseMultiple = true;
			params.fractionNegativeToCollapse = 0.6;
			params.useInsertionAlgorithm = false;
			params.useGradientNorm = false;

			params.logfile = "ST4Convergence.m";
			params.logArrayName = "ST4_60";
			params.pgbound = estimateProjGradBound(params.relativeErrorBound, distances);

			params.log = setupLogfile(params.logfile, false);
			splits= NeighborNetSplitWeights.compute(cycle, distances, params, progress);

			if (params.log != null) {
				params.log.flush();
				params.log.close();
			}



			/*---------------------------------------------------- */

//			params = new NeighborNetSplitWeights.NNLSParams(n);
//			params.greedy = false;
//			params.nnlsAlgorithm = NNLSParams.ACTIVE_SET;
//			params.outerIterations = n * (n - 1) / 2;
//			params.collapseMultiple = false;
//			params.fractionNegativeToCollapse = 0.2;
//			params.useInsertionAlgorithm = false;
//			params.logfile = "ST4Convergence.m";
//			params.logArrayName = "ST4_20";
//			params.pgbound = estimateProjGradBound(params.relativeErrorBound, distances);
//
//			params.log = setupLogfile(params.logfile, true);
//			splits = NeighborNetSplitWeights.compute(cycle, distances, params, progress);
//
//			if (params.log != null) {
//				params.log.flush();
//				params.log.close();
//			}
//			/*---------------------------------------------------- */
//
//			params = new NeighborNetSplitWeights.NNLSParams(n);
//			params.greedy = false;
//			params.nnlsAlgorithm = NNLSParams.ACTIVE_SET;
//			params.outerIterations = n * (n - 1) / 2;
//			params.collapseMultiple = false;
//			params.fractionNegativeToCollapse = 0.1;
//			params.useInsertionAlgorithm = false;
//			params.logfile = "ST4Convergence.m";
//			params.logArrayName = "ST4_10";
//			params.pgbound = estimateProjGradBound(params.relativeErrorBound, distances);
//
//			params.log = setupLogfile(params.logfile, true);
//			splits = NeighborNetSplitWeights.compute(cycle, distances, params, progress);
//
//			if (params.log != null) {
//				params.log.flush();
//				params.log.close();
//			}
//
//
//			/*---------------------------------------------------- */
//
//			params = new NeighborNetSplitWeights.NNLSParams(n);
//			params.greedy = false;
//			params.nnlsAlgorithm = NNLSParams.ACTIVE_SET;
//			params.outerIterations = n * (n - 1) / 2;
//			params.collapseMultiple = false;
//			params.fractionNegativeToCollapse = 0.0;
//			params.useInsertionAlgorithm = false;
//			params.logfile = "ST4Convergence.m";
//			params.logArrayName = "ST4_00";
//			params.pgbound = estimateProjGradBound(params.relativeErrorBound, distances);
//
//			params.log = setupLogfile(params.logfile, true);
//			splits = NeighborNetSplitWeights.compute(cycle, distances, params, progress);
//
//			if (params.log != null) {
//				params.log.flush();
//				params.log.close();
//			}



			/*---------------------------------------------------- */

		}

		if (evalAPG) {

			params = new NeighborNetSplitWeights.NNLSParams(n);
			params.nnlsAlgorithm = NNLSParams.PROJECTEDGRAD;

			params.logfile = "APGConvergence.m";
			params.logArrayName = "APG";
			params.pgbound = estimateProjGradBound(params.relativeErrorBound, distances);
			params.printConvergenceData = false;

			params.log = setupLogfile(params.logfile, false);
			splitsComputed = NeighborNetSplitWeights.compute(cycle, distances, params, progress);

			if (params.log != null) {
				params.log.flush();
				params.log.close();
			}
		}

		if (evalProjectedCG)  {

			/*******/



			params = new NeighborNetSplitWeights.NNLSParams(n);
			params.greedy = false;
			params.nnlsAlgorithm = NNLSParams.GRADPROJECTION;
			params.faceSearch = NNLSParams.ACTIVE_SET;

			params.outerIterations = n * (n - 1) / 2;
			params.collapseMultiple = true;
			params.fractionNegativeToCollapse = 0.6;
			params.useInsertionAlgorithm = false;
			params.useGradientNorm = true;
			params.logfile = "ProjectedCGConvergence.m";
			params.logArrayName = "PCG_25";
			params.cgIterations = n * (n - 1) / 2;
			params.pgbound = estimateProjGradBound(params.relativeErrorBound, distances);
			params.kktBound = 1e-2;

			params.log = setupLogfile(params.logfile, false);
			splitsComputed = NeighborNetSplitWeights.compute(cycle, distances, params, progress);

			if (params.log != null) {
				params.log.flush();
				params.log.close();
			}

//			params = new NeighborNetSplitWeights.NNLSParams(n);
//			params.greedy = true;
//			params.nnlsAlgorithm = NNLSParams.GRADPROJECTION;
//			params.outerIterations = n * (n - 1) / 2;
//			params.collapseMultiple = true;
//			params.fractionNegativeToCollapse = 0.8;
//			params.useInsertionAlgorithm = true;
//			params.useGradientNorm = true;
//
//			params.logfile = "ProjectedCGConvergence.m";
//			params.logArrayName = "PCG_25_8";
//			params.cgIterations = 25;
//			params.pgbound = estimateProjGradBound(params.relativeErrorBound, distances);
//
//			params.log = setupLogfile(params.logfile, true);
//			splitsComputed = NeighborNetSplitWeights.compute(cycle, distances, params, progress);
//
//			if (params.log != null) {
//				params.log.flush();
//				params.log.close();
//			}


		}

		if (evalBlockPivot) {
			params = new NeighborNetSplitWeights.NNLSParams(n);
			params.greedy = false;
			params.nnlsAlgorithm = NNLSParams.BLOCKPIVOT;

			params.outerIterations = 1000;
			params.collapseMultiple = true;
			params.fractionNegativeToCollapse = 0.6;
			params.useInsertionAlgorithm = true;
			params.useGradientNorm = true;

			params.logfile = "BlockPivotConvergence.m";
			params.logArrayName = "BPivot";
			params.pgbound = estimateProjGradBound(params.relativeErrorBound, distances);
			params.tolerance = 0.01; //Used for Brents Method.

			params.log = setupLogfile(params.logfile, false);
			splitsComputed = NeighborNetSplitWeights.compute(cycle, distances, params, progress);

			if (params.log != null) {
				params.log.flush();
				params.log.close();
			}
		}
		return splits;

	}


	/**
	 * Open a file for log output
	 * @param logfile filename
	 * @return PrintWrite log file.
	 */
	static private PrintWriter setupLogfile(String logfile, boolean append) {
		PrintWriter log = null;
		if (logfile!=null) {
			try {
				File f = new File(logfile);
				if (append &&  f.exists() && !f.isDirectory() ) {
					log = new PrintWriter(new FileOutputStream(new File(logfile), true));
				}
				else {
					log = new PrintWriter(logfile);
				}
			} catch (FileNotFoundException e) {
				e.printStackTrace();
			}
		}
		return log;
	}











}
