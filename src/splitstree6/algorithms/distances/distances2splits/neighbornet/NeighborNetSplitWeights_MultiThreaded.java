package splitstree6.algorithms.distances.distances2splits.neighbornet;

import jloda.fx.util.ProgramExecutorService;
import jloda.fx.window.NotificationManager;
import jloda.util.CanceledException;
import jloda.util.progress.ProgressListener;
import splitstree6.splits.ASplit;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static java.lang.Math.*;
import static splitstree6.algorithms.distances.distances2splits.neighbornet.IncrementalFitting.incrementalFitting;
import static splitstree6.algorithms.distances.distances2splits.neighbornet.SquareArrays.*;
//import static splitstree6.algorithms.distances.distances2splits.neighbornet.NeighborNetSplitWeightsClean.NNLSParams;

public class NeighborNetSplitWeights_MultiThreaded {
	static ExecutorService service = Executors.newFixedThreadPool(16); // todo: fixme
	static int countCalls = 0;

	static long timeCalls = 0L;

	public static class NNLSParams {

		public NNLSParams(int ntax) {
			cgIterations = min(max(ntax, 10), 25);
			outerIterations = max(ntax, 10);
		}

		static public int GRADPROJECTION = 0;
		static public int ACTIVE_SET = 1;
		static public int PROJECTEDGRAD = 2;
		static public int BLOCKPIVOT = 3;

		//static public int

		public int nnlsAlgorithm = GRADPROJECTION;
		public double tolerance = 1e-6; //Approximate tolerance in split weights
		public boolean greedy = false;
		public boolean useInsertionAlgorithm = true; //Use taxon insertion algorithm for the initial split weights
		public boolean useGradientNorm = false; //Use stopping condition based on norm of projected gradient
		public int cgIterations; //Max number of iterations on the calls to conjugate gradients.
		public int outerIterations; //Max number of iterations through the outer loop
		public boolean collapseMultiple = false; //Collapse multiple negative splits (ST4 only)
		public double fractionNegativeToKeep = 0.4; //Propostion of negative splits to collapse (ST4 only)
		public double kktBound = tolerance / 100;
		public boolean printConvergenceData = false;
		public double pgbound = 1e-4; //Bound on the projective gradient norm

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

		int n = cycle.length - 1;  //Number of taxa

		countCalls = 0;
		timeCalls = 0L;
		testIncremental(n);


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

		//Set up square array of distances
		var d = new double[n + 1][n + 1];
		for (var i = 1; i <= n; i++)
			for (var j = i + 1; j <= n; j++)
				d[i][j] = d[j][i] = distances[cycle[i] - 1][cycle[j] - 1];

		var x = new double[n + 1][n + 1];

		/*
		if (params.nnlsAlgorithm == NNLSParams.ACTIVE_SET) {
			activeSetST4(x, d, null, params, progress);  //ST4 Algorithm
		} else {
			x = calcAinvx(d); //Check if unconstrained solution is feasible.
			if (minArray(x) >= -params.tolerance)
				makeNegElementsZero(x); //Fix roundoff
			else {
				incrementalFitting(x, d, params.tolerance / 100,true);
				if (params.nnlsAlgorithm == NNLSParams.PROJECTEDGRAD)
					acceleratedProjectedGradientDescent(x, d, params, progress);
				else if (params.nnlsAlgorithm == NNLSParams.BLOCKPIVOT)
					blockPivot(x, d, params, progress);
				else
					projectedConjugateGradient(x, d, params, progress);
			}
		} */

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

		System.err.println("calls: " + countCalls);
		System.err.printf("total: %.1f seconds%n", timeCalls / 1000.0);
		return splitList;
	}


	static private void projectedConjugateGradient(double[][] x, double[][] d, NNLSParams params, ProgressListener progress) throws CanceledException {

		int n = x.length - 1;
		NNLSFunctionObject f = new NNLSFunctionObject(n);
		double fx_old = f.evalf(x, d);
		boolean[][] activeSet = getZeroElements(x);

		for (int k = 1; k <= params.outerIterations; k++) {
			boolean optimalForFace = searchFace(x, d, activeSet, f, params);
			double fx = f.evalf(x, d);
			if (optimalForFace || fx_old - fx < params.tolerance) {
				if (params.greedy)
					return;
				boolean finished = checkKKT(x, d, activeSet, params);
				if (finished)
					return;
			}
			fx_old = fx;
			progress.checkForCancel();
		}
		NotificationManager.showError("Neighbor-net algorithm failed to converge");
	}


	/**
	 * Search a face of the nnls problem
	 * <p>
	 * Minimizes ||Ax - d|| subject to the constraint that x_ij = 0 whenever activeSet[i][j] = true.
	 * <p>
	 * Uses at most n iterations of the conjugate gradient algorithm for normal equations (cf CGNR in Saad's book)
	 * When that converges, or when all iterations are completed,
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
		int n = x.length - 1;
		double[][] x0 = new double[n + 1][n + 1];
		copyArray(x, x0);

		boolean cgConverged = cgnr(x, d, activeSet, params.tolerance, params.cgIterations, f);
		if (params.collapseMultiple) {
			filterMostNegative(x, activeSet, 1.0 - params.fractionNegativeToKeep);
			maskElements(x, activeSet);
			cgConverged = cgnr(x, d, activeSet, params.tolerance, params.cgIterations, f);
		}

		if (minArray(x) < 0) {
			if (params.nnlsAlgorithm == NNLSParams.GRADPROJECTION)
				//Use gradient projection to return the best projection of points on the line between x0 and x
				goldenProjection(x, x0, d, f, params.tolerance);
			else
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
	 * @param tol           tolerance for the squared norm of the residual
	 * @param maxIterations maximum number of iterations
	 * @return boolean  true if the method converged (didn't hit max number of iterations)
	 */
	static private boolean cgnr(double[][] x, double[][] d, boolean[][] activeSet, double tol, int maxIterations, NNLSFunctionObject f) {
		int n = x.length - 1;

		if (false)
			System.err.println("\t\tEntering cgnr. fx = " + f.evalf(x, d) + "\t" + f.evalfprojected(x, d));


		double[][] p = new double[n + 1][n + 1];
		double[][] r = new double[n + 1][n + 1];
		calcAx(x, r);
		for (int i = 1; i <= n; i++)
			for (int j = 1; j <= n; j++)
				r[i][j] = d[i][j] - r[i][j];
		double[][] z = new double[n + 1][n + 1];
		calcAtx(r, z);
		double[][] w = new double[n + 1][n + 1];
		maskElements(z, activeSet);
		copyArray(z, p);
		double ztz = sumArraySquared(z);

		int k = 1;

		while (true) {
			calcAx(p, w);
			double alpha = ztz / sumArraySquared(w);

			for (int i = 1; i <= n; i++) {
				for (int j = 1; j <= n; j++) {
					x[i][j] += alpha * p[i][j];
					r[i][j] -= alpha * w[i][j];
				}
			}

			if (false)
				System.err.println("\t\t\t" + f.evalf(x, d) + "\t" + f.evalfprojected(x, d));

			calcAtx(r, z);
			maskElements(z, activeSet);
			double ztz2 = sumArraySquared(z);
			double beta = ztz2 / ztz;

			if (ztz2 < tol || k >= maxIterations)
				break;

			for (int i = 1; i <= n; i++) {
				for (int j = 1; j <= n; j++) {
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
	 * @param fractionNegativeToKeep double. Minimum fraction of the negative entries to keep.
	 */
	static private void filterMostNegative(double[][] x, boolean[][] activeSet, double fractionNegativeToKeep) {
		int numNeg = 0;
		int n = x.length - 1;
		for (int i = 1; i <= n; i++)
			for (int j = i + 1; j <= n; j++)
				if (!activeSet[i][j] && x[i][j] < 0)
					numNeg++;
		if (numNeg == 0)
			return;
		double[] vals = new double[numNeg];
		int k = 0;
		for (int i = 1; i <= n; i++)
			for (int j = i + 1; j <= n; j++)
				if (!activeSet[i][j] && x[i][j] < 0) {
					vals[k] = x[i][j];
					k++;
				}
		Arrays.sort(vals);
		int numToKeep = (int) ceil(numNeg * fractionNegativeToKeep);
		double threshold;
		if (numToKeep == 0)
			threshold = 0.0;
		else
			threshold = vals[numNeg - numToKeep];
		//Make active all entries with weight strictly less than the threshold.
		for (int i = 1; i <= n; i++)
			for (int j = i + 1; j <= n; j++) {
				if (!activeSet[i][j] && x[i][j] < threshold) {
					activeSet[i][j] = true;
					activeSet[j][i] = true;
				}
			}
	}


	static private class NNLSFunctionObject {
		//Utility class for evaluating ||Ax - b|| without additional allocation.
		private double[][] xt;
		private double[][] Axt;

		NNLSFunctionObject(int n) {
			xt = new double[n + 1][n + 1];
			Axt = new double[n + 1][n + 1];
		}

		public double evalf(double[][] x, double[][] d) {
			calcAx(x, Axt);
			int n = x.length - 1;
			double fx = 0.0;
			for (int i = 1; i <= n; i++) {
				double fx_i = 0.0;
				for (int j = 1; j <= n; j++) {
					double res_ij = Axt[i][j] - d[i][j];
					fx_i += res_ij * res_ij;
				}
				fx += fx_i;
			}
			return sqrt(fx);
		}

		public double evalfprojected(double t, double[][] x0, double[][] x, double[][] d) {
			int n = x.length - 1;
			for (int i = 1; i <= n; i++)
				for (int j = i + 1; j <= n; j++) {
					xt[i][j] = xt[j][i] = max(x0[i][j] * (1 - t) + x[i][j] * t, 0.0);
				}
			return evalf(xt, d);
		}

		public double evalfprojected(double[][] x, double[][] d) {
			int n = x.length - 1;
			for (int i = 1; i <= n; i++)
				for (int j = 1; j <= n; j++) {
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

	static private void goldenProjection(double[][] x, double[][] x0, double[][] d, NNLSFunctionObject f, double tolerance) {
		//Minimize ||A \pi((1-t)x0 + tx) - d||  for t in [0,1]
		double C = (3 - sqrt(5)) / 2.0;
		double R = 1.0 - C;

		double t0 = 0, t1 = C, t2 = C + C * (1 - C), t3 = 1.0;
		double f1 = f.evalfprojected(t1, x0, x, d);
		double f2 = f.evalfprojected(t2, x0, x, d);

		while (abs(t3 - t0) > tolerance) {
			if (f2 < f1) {
				t0 = t1;
				t1 = t2;
				t2 = R * t1 + C * t3;
				f1 = f2;
				f2 = f.evalfprojected(t2, x0, x, d);
			} else {
				t3 = t2;
				t2 = t1;
				t1 = R * t2 + C * t0;
				f2 = f1;
				f1 = f.evalfprojected(t1, x0, x, d);
			}
		}
		double tmin = t1;
		if (f2 < f1)
			tmin = t2;
		else if (t0 == 0) {  //Handle a special case so that if minimum is at the boundary t=0 then this is exactly what is returned
			double f0 = f.evalfprojected(t0, x0, x, d);
			if (f0 < f1)
				tmin = t0;
		}
		int n = x.length - 1;
		for (int i = 1; i <= n; i++) {
			for (int j = i + 1; j <= n; j++) {
				double newx_ij = max((1 - tmin) * x0[i][j] + tmin * x[i][j], 0);
				x[i][j] = x[j][i] = newx_ij;
			}
		}
		double fmin = f.evalf(x, d);
	}


	/**
	 * Determines the point on the path from x0 to x that is furthest from x0 and still feasible.
	 *
	 * @param x         square array, final point
	 * @param x0        square array, initial point
	 * @param tolerance tolerance. Any entry of x less than tolerance is mapped to zero.
	 */
	static private void furthestFeasible(double[][] x, double[][] x0, double tolerance) {
		double tmin = 1.0;
		int n = x.length - 1;

		for (int i = 1; i <= n; i++) {
			for (int j = 1; j <= n; j++) {
				if (x[i][j] < 0) {
					double t_ij = x0[i][j] / (x0[i][j] - x[i][j]);
					tmin = min(tmin, t_ij);
				}
			}
		}

		for (int i = 1; i <= n; i++) {
			for (int j = i + 1; j <= n; j++) {
				double x_ij = (1.0 - tmin) * x0[i][j] + tmin * x[i][j];
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
	static private void calcAx(double[][] x, double[][] d) {
		countCalls++;

		var startTime = System.currentTimeMillis();

		int n = x.length - 1;
//            double[][] d = new double[n+1][n+1];

		if (false) {
			var latch = new CountDownLatch(n - 1);

			for (var i0 = 1; i0 <= (n - 1); i0++) {
				var i = i0;
				service.submit(() -> {
					d[i + 1][i] = d[i][i + 1] = sumSubvector(x[i + 1], i + 1, n) + sumSubvector(x[i + 1], 1, i);
					latch.countDown();
				});
			}
			try {
				latch.await();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		} else {
			for (int i = 1; i <= (n - 1); i++)
				d[i + 1][i] = d[i][i + 1] = sumSubvector(x[i + 1], i + 1, n) + sumSubvector(x[i + 1], 1, i);
		}

		for (var i = 1; i <= (n - 2); i++) {
			d[i + 2][i] = d[i][i + 2] = d[i][i + 1] + d[i + 1][i + 2] - 2 * x[i + 1][i + 2];
		}

		if (true) {
			for (int k0 = 3; k0 <= n - 1; k0++) {
				var nCalculations = n - k0;
				int nThreads;
				if (nCalculations < 16)
					nThreads = 1;
				else
					nThreads = Math.min(1, nCalculations);

				var latch = new CountDownLatch(nThreads);

				var k = k0;

				if (true) {
					var blockSize = (int) Math.ceil((double) nCalculations / (double) nThreads);
					for (var t = 0; t < nThreads; t++) {
						var threadNumber = t;
						service.submit(() -> {
							var start = 1 + threadNumber * blockSize;
							var end = 1 + Math.min((threadNumber + 1) * blockSize, nCalculations);
							for (var i = start; i < end; i++) {
								int j = i + k;
								d[j][i] = d[i][j] = d[i][j - 1] + d[i + 1][j] - d[i + 1][j - 1] - 2 * x[i + 1][j];
							}
							latch.countDown();
						});
					}
				} else {
					for (var t = 0; t < nThreads; t++) {
						var threadNumber = t;
						service.submit(() -> {


							for (int i = 1 + threadNumber; i <= n - k; i += nThreads) {
								int j = i + k;
								d[j][i] = d[i][j] = d[i][j - 1] + d[i + 1][j] - d[i + 1][j - 1] - 2 * x[i + 1][j];
							}
							latch.countDown();
						});
					}
				}
				try {
					latch.await();
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
		}
		if (false) {
			for (int k0 = 3; k0 <= n - 1; k0++) {
				int nThreads = 1;

				var k = k0;
				for (var t = 0; t < nThreads; t++) {
					var threadNumber = t;
					{
						for (int i = 1 + threadNumber; i <= n - k; i += nThreads) {
							int j = i + k;
							d[j][i] = d[i][j] = d[i][j - 1] + d[i + 1][j] - d[i + 1][j - 1] - 2 * x[i + 1][j];
						}
					}
					;
				}
			}
		}
		if (false) {
			for (int k = 3; k <= n - 1; k++) {
				var latch = new CountDownLatch(n - k);
				for (int i0 = 1; i0 <= n - k; i0++) {
					var i = i0;
					int j = i0 + k;
					service.submit(() -> {
						d[j][i] = d[i][j] = d[i][j - 1] + d[i + 1][j] - d[i + 1][j - 1] - 2 * x[i + 1][j];
						latch.countDown();
					});
				}
				try {
					latch.await();
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
		}
		if (false) {
//            double[][] d = new double[n+1][n+1];

			for (int k = 3; k <= n - 1; k++) {
				for (int i = 1; i <= n - k; i++) {  //TODO. This loop can be threaded
					int j = i + k;
					d[j][i] = d[i][j] = d[i][j - 1] + d[i + 1][j] - d[i + 1][j - 1] - 2 * x[i + 1][j];
				}
			}
		}

		timeCalls += System.currentTimeMillis() - startTime;
		//       return d;
	}

	/**
	 * Sum the elements in the vector over a range of indices.
	 * <p>
	 * Separating this out in case we can improve efficiency with threading.
	 *
	 * @param v    vector
	 * @param from start index
	 * @param to   end index
	 * @return \sum_{i=from}^to v(i)
	 */
	static private double sumSubvector(double[] v, int from, int to) {
		double s = 0.0;
		for (int i = from; i <= to; i++)
			s += v[i];
		return s;
	}


	/**
	 * Compute Atx, when x and the result are represented as square arrays
	 *
	 * @param x square array
	 * @param p square array. Overwritten with result
	 */

	static private void calcAtx(double[][] x, double[][] p) {
		if (false) {
			int n = x.length - 1;
			//double[][] p = new double[n+1][n+1];

			{
				var latch = new CountDownLatch(n - 1);
				for (int i0 = 1; i0 <= n - 1; i0++) {
					var i = i0;
					service.submit(() -> {
						p[i + 1][i] = p[i][i + 1] = sumSubvector(x[i], 1, n);
						latch.countDown();
					});
				}
				try {
					latch.await();
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}

			for (int i = 1; i <= n - 2; i++) {
				p[i + 2][i] = p[i][i + 2] = p[i][i + 1] + p[i + 1][i + 2] - 2 * x[i][i + 1];
			}

			if (true) {
				for (int k0 = 3; k0 <= n - 1; k0++) {
					var nCalculations = n - k0;
					int nThreads;
					if (nCalculations < 16)
						nThreads = 1;
					else
						nThreads = Math.min(ProgramExecutorService.getNumberOfCoresToUse(), nCalculations);
					var latch = new CountDownLatch(nThreads);

					var k = k0;
					for (var t = 0; t < nThreads; t++) {
						var threadNumber = t;
						service.submit(() -> {
							for (int i = 1 + threadNumber; i <= n - k; i += nThreads) {
								p[i + k][i] = p[i][i + k] = p[i][i + k - 1] + p[i + 1][i + k] - p[i + 1][i + k - 1] - 2 * x[i][i + k - 1];
							}
							latch.countDown();
						});
					}
					try {
						latch.await();
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
				}
			} else {
				for (int k0 = 3; k0 <= n - 1; k0++) {
					var latch = new CountDownLatch(n - k0);
					for (int i0 = 1; i0 <= n - k0; i0++) {
						var i = i0;
						var k = k0;
						service.submit(() -> {
							p[i + k][i] = p[i][i + k] = p[i][i + k - 1] + p[i + 1][i + k] - p[i + 1][i + k - 1] - 2 * x[i][i + k - 1];
							latch.countDown();
						});
					}
					try {
						latch.await();
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
				}
			}
		} else {
			int n = x.length - 1;
			//double[][] p = new double[n+1][n+1];

			for (int i = 1; i <= n - 1; i++)
				p[i + 1][i] = p[i][i + 1] = sumSubvector(x[i], 1, n);

			for (int i = 1; i <= n - 2; i++) {  //TODO This can be threaded
				p[i + 2][i] = p[i][i + 2] = p[i][i + 1] + p[i + 1][i + 2] - 2 * x[i][i + 1];
			}

			for (int k = 3; k <= n - 1; k++) {
				for (int i = 1; i <= n - k; i++) { //TODO. This inner loop can be threaded
					p[i + k][i] = p[i][i + k] = p[i][i + k - 1] + p[i + 1][i + k] - p[i + 1][i + k - 1] - 2 * x[i][i + k - 1];
				}
			}
		}

		/*
		{
			for (int k = 3; k <= n - 1; k++) {
				for (int i = 1; i <= n - k; i++) { //TODO. This inner loop can be threaded
					p[i + k][i] = p[i][i + k] = p[i][i + k - 1] + p[i + 1][i + k] - p[i + 1][i + k - 1] - 2 * x[i][i + k - 1];
				}
			}
		}
		 */
		//return p;
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
	 * @return square array
	 */
	static private double[][] calcAinvx(double[][] d) {
		int n = d.length - 1;
		double[][] x = new double[n + 1][n + 1];
		x[1][2] = x[2][1] = (d[1][n] + d[1][2] - d[2][n]) / 2.0;
		for (int j = 2; j <= n - 1; j++) {
			x[1][j] = x[j][1] = (d[j - 1][n] + d[1][j] - d[1][j - 1] - d[j][n]) / 2.0;
		}
		x[1][n] = x[n][1] = (d[1][n] + d[n - 1][n] - d[1][n - 1]) / 2.0;

		for (int i = 2; i <= (n - 1); i++) {
			x[i][i + 1] = (d[i - 1][i] + d[i][i + 1] - d[i - 1][i + 1]) / 2.0;
			for (int j = (i + 2); j <= n; j++)
				x[i][j] = x[j][i] = (d[i - 1][j - 1] + d[i][j] - d[i][j - 1] - d[i - 1][j]) / 2.0;
		}
		return x;
	}

	/**
	 * checkKKT
	 * <p>
	 * Checks the KKT conditions, under the assumption that x is optimal for the current active set.
	 *
	 * @param x         square array. point x
	 * @param d         square array of distances, used to compute graient.
	 * @param activeSet current activeSet. Assume activeSet[i][j]=true implies x[i][j]=0.
	 * @param params    Params
	 * @return boolean   true if kkt conditions are (approximately) satisfied.
	 */
	static private boolean checkKKT(double[][] x, double[][] d, boolean[][] activeSet, NNLSParams params) {
		int n = x.length - 1;
		double[][] gradient = new double[n + 1][n + 1];
		evalGradient(x, d, gradient);
		double mingrad = 0.0;
		int min_i = 0, min_j = 0;
		for (int i = 1; i <= n; i++)
			for (int j = i + 1; j <= n; j++) {
				double grad_ij = gradient[i][j];
				if (activeSet[i][j] && grad_ij < mingrad) {
					mingrad = grad_ij;
					min_i = i;
					min_j = j;
				}
			}
		if (mingrad >= -params.kktBound)
			return true;

		//Now remove elements from the active set.
		if (params.nnlsAlgorithm == NNLSParams.ACTIVE_SET) {
			activeSet[min_i][min_j] = activeSet[min_i][min_j] = false;
		} else {
			for (int i = 1; i <= n; i++)
				for (int j = i + 1; j <= n; j++) {
					double grad_ij = gradient[i][j];
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
		int n = x.length - 1;
		double[][] res = new double[n + 1][n + 1];
		calcAx(x, res);
		for (int i = 1; i <= n; i++)
			for (int j = 1; j <= n; j++)
				res[i][j] -= d[i][j];
		calcAtx(res, gradient);
	}

	static private void projectedGradientDescent(double[][] x, double[][] d, NNLSParams params, ProgressListener progress) throws CanceledException {
		int n = x.length - 1;
		double L = estimateNorm(n);
		NNLSFunctionObject f = new NNLSFunctionObject(n);
		double f_old = f.evalf(x, d);
		double[][] grad = new double[n + 1][n + 1];
		System.err.print("ProjGrad=[");
		while (true) {
			evalGradient(x, d, grad);
			System.err.println("\t" + f_old + "\t" + projGradNorm(x, d));

			for (int i = 1; i <= n; i++) {
				for (int j = i + 1; j <= n; j++) {
					x[i][j] = x[j][i] = max(x[i][j] - (1.0 / L) * grad[i][j], 0.0);
				}
			}
			double f_new = f.evalf(x, d);
			if (f_old - f_new < params.tolerance) {
				System.err.println("\t" + f_new + "\t" + projGradNorm(x, d) + "];");
				break;
			}
			f_old = f_new;
			progress.checkForCancel();
		}
	}

	static private void acceleratedProjectedGradientDescent(double[][] x, double[][] d, NNLSParams params, ProgressListener progress) throws CanceledException {
		int n = x.length - 1;
		double L = estimateNorm(n);
		NNLSFunctionObject f = new NNLSFunctionObject(n);
		double f_old = f.evalf(x, d);
		double[][] grad = new double[n + 1][n + 1];
		int k = 1;
		double alpha0 = 0.5;   //TODO: Find out what to use here - Nesterov

		double alpha_old = alpha0;
		double alpha;

		double[][] y = new double[n + 1][n + 1];
		double[][] x_old = new double[n + 1][n + 1];

		copyArray(x, y);

		if (params.printConvergenceData)
			System.err.print("AccProjGrad=[");
		while (true) {
			if (params.printConvergenceData)
				System.err.println("\t" + f_old + "\t" + projGradNorm(x, d));


			copyArray(x, x_old);
			evalGradient(y, d, grad);
			for (int i = 1; i <= n; i++) {
				for (int j = i + 1; j <= n; j++) {
					x[i][j] = x[j][i] = max(y[i][j] - (1.0 / L) * grad[i][j], 0.0);
				}
			}

			double f_new = f.evalf(x, d);
			if (f_new > f_old) {
				evalGradient(x_old, d, grad);
				for (int i = 1; i <= n; i++) {
					for (int j = i + 1; j <= n; j++) {
						x[i][j] = x[j][i] = max(x_old[i][j] - (1.0 / L) * grad[i][j], 0.0);
						y[i][j] = y[j][i] = x[i][j];
					}
				}
				f_new = f.evalf(x, d);
				alpha = alpha0;
			} else if (f_old - f_new < params.tolerance) {
				if (params.printConvergenceData)
					System.err.println("\t" + f_new + "\t" + projGradNorm(x, d));
				break;
			} else {
				double a2 = alpha_old * alpha_old;
				alpha = 0.5 * sqrt(a2 * a2 + 4 * a2) - a2;
				double beta = alpha_old * (1 - alpha_old) / (a2 + alpha);
				for (int i = 1; i <= n; i++) {
					for (int j = i + 1; j <= n; j++) {
						y[i][j] = y[j][i] = x[i][j] + beta * (x[i][j] - x_old[i][j]);
					}
				}
			}
			alpha_old = alpha;
			f_old = f_new;

			progress.checkForCancel();
		}
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


	static private double projGradNorm(double[][] x, double[][] d) {
		int n = x.length - 1;
		double[][] grad = new double[n + 1][n + 1];
		evalGradient(x, d, grad);
		for (int i = 1; i <= n; i++) {
			for (int j = i + 1; j <= n; j++) {
				if (x[i][j] == 0)
					grad[i][j] = min(grad[i][j], 0.0);
			}
		}
		return sqrt(sumSquares(grad));
	}

	static private void blockPivot(double[][] x, double[][] d, NNLSParams params, ProgressListener progress) throws CanceledException {
		int n = x.length - 1;
		double cutoff = params.tolerance * 1e-3;
		//Initialise the active set from zero entries of x (note values in x are ignored)
		boolean[][] G = new boolean[n + 1][n + 1];
		getZeroElements(x, G);

		//gradient
		double[][] y = new double[n + 1][n + 1];

		//Initial call
		NNLSFunctionObject f = new NNLSFunctionObject(n);
		boolean converged = cgnr(x, d, G, params.tolerance, params.cgIterations, f);

		System.err.println("\t\tconverged = " + converged + "\t||grad|| after CG" + debugLS(x, d, G));

		evalGradient(x, d, y);
		boolean[][] infeasible = new boolean[n + 1][n + 1];

		int N = Integer.MAX_VALUE;
		int p = 3;

		while (true) {
			int numBad = numInfeasible(x, y, G, infeasible);
			if (numBad < N) {
				N = numBad;
				p = 3;
				for (int i = 1; i <= n; i++) {
					for (int j = i + 1; j <= n; j++) {
						G[i][j] = G[j][i] = (G[i][j] ^ infeasible[i][j]);   //XOR. flip G if infeasible true.
					}
				}
			} else {
				if (p > 0) {
					p--;
					for (int i = 1; i <= n; i++) {
						for (int j = i + 1; j <= n; j++) {
							G[i][j] = G[j][i] = (G[i][j] ^ infeasible[i][j]);   //XOR. flip G if infeasible true.
						}
					}
				} else {

					boolean done = false;
					for (int i = 1; i <= n && !done; i++) {
						for (int j = i + 1; j <= n && !done; j++) {
							if (infeasible[i][j]) {
								G[i][j] = G[j][i] = !G[i][j];
								done = true;
							}
						}
					}
				}
			}
			converged = cgnr(x, d, G, params.tolerance, params.cgIterations, f);
			System.err.println("\t\tconverged = " + converged + "\t||grad|| after CG" + debugLS(x, d, G));
			progress.checkForCancel();

			for (int i = 1; i <= n; i++)
				for (int j = i + 1; j <= n; j++) {
					if (abs(x[i][j]) < cutoff)
						x[i][j] = x[j][i] = 0.0;
					if (abs(x[i][j]) < cutoff)
						y[i][j] = y[j][i] = 0.0;
				}
		}
	}

	static private int numInfeasible(double[][] x, double[][] y, boolean[][] G, boolean[][] infeasible) {
		int count = 0;
		int n = x.length - 1;

		for (int i = 1; i <= n; i++) {
			for (int j = i + 1; j <= n; j++) {
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
		int n = x.length - 1;
		double[][] grad = new double[n + 1][n + 1];
		evalGradient(x, d, grad);
		double maxAbs = 0.0;
		for (int i = 1; i <= n; i++) {
			for (int j = i + 1; j <= n; j++) {
				if (!G[i][j])
					maxAbs = max(abs(grad[i][j]), maxAbs);
			}
		}
		return maxAbs;
	}

	static public double testIncremental(int n) {
		double[][] x = new double[n + 1][n + 1];
		for (int i = 1; i <= n; i++) {
			for (int j = i + 1; j <= n; j++) {
				if (Math.random() < 0.2)
					x[i][j] = x[j][i] = random();
			}
		}
		double[][] d = new double[n + 1][n + 1];
		calcAx(x, d);
		double[][] x2 = new double[n + 1][n + 1];
		incrementalFitting(x2, d, 1e-10,false);
		double diff = 0.0;
		int nmissedZero = 0;
		int nfalseZero = 0;
		int nneg = 0;
		for (int i = 1; i <= n; i++) {
			for (int j = i + 1; j <= n; j++) {
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
			incrementalFitting(x2, d, 1e-10,false);
		return diff;
	}
}
