/*
 *  NeighborNetBlockPivot.java Copyright (C) 2021 Daniel H. Huson
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

import jloda.util.CanceledException;
import jloda.util.ProgressListener;
import jloda.util.ProgressSilent;
import splitstree6.algorithms.distances.distances2splits.neighbornet.NeighborNetSplits;

import java.util.Arrays;
import java.util.Random;

import static splitstree6.algorithms.distances.distances2splits.neighbornet.NeighborNetPCG.CircularSplitAlgorithms.*;
import static splitstree6.algorithms.distances.distances2splits.neighbornet.NeighborNetPCG.VectorUtilities.norm;
import static splitstree6.algorithms.distances.distances2splits.neighbornet.NeighborNetSplits.Regularization.nnls;

//Things to do next
//(1) Implement warm starts
//(2) More thorough tests
//(3) Explore algorithm for more bands with A_{n-1}.


public class NeighborNetBlockPivot {
	private static boolean verbose = true;

	public static class BlockPivotParams {
		public int maxBlockPivotIterations = 1000; //Maximum number of iterations for the block pivot algorithm
		public int maxPCGIterations = 1000; //Maximum number of iterations for the conjugate gradient algorithm
		public double pcgTol = 1e-12; //Tolerance for pcg: will stop when residual has norm less than this.
		public double blockPivotCutoff = 1e-8; //Cutoff - values in block pivot with value smaller than this are set to zero.
		public boolean usePreconditioner = true; //True if the conjugate gradient makes use of preconditioner.
		public int preconditionerBands = 10; //Number of bands used when computing Y,Z submatrices in the preconditioner.
		// Note that alot of the calculations for preconditioning are done even if this is false, so use this flag only to assess #iterations.
		public boolean useOldCG = true; //Use the old CG code for least squares
	}


	/**
	 * circularBlockPivot
	 * <p>
	 * Finds a vector x to minimize ||Ax - d|| where A is the circular matrix (for 1,2,...,n) , d is the vector of
	 * distances and x is the non-negative split weights. We use the block pivot algorithm tsnnls combined with preconditioned
	 * conjugate gradient.
	 *
	 * @param n        number of taxa. Assumed circular ordering is 1,2,...,n
	 * @param d        Distances (as a vector with n(n-1)/2+1 entries ), in order 0,12,13,14,..,1n,23,24,....,2n,...,(n-1)n
	 * @param progress Progress indicator
	 * @param params   Search parameters
	 * @return array of split weights in indices 1,...,n(n-1)/2
	 * @throws CanceledException
	 */
	static public double[] circularBlockPivot(int n, double[] d, ProgressListener progress, BlockPivotParams params) throws CanceledException {


		final int npairs = n * (n - 1) / 2;
		final boolean[] G = new boolean[npairs + 1];  //Active set - indices constrained to zero.
		Arrays.fill(G, true);    //F = \emptyset. G = {1,2,...,npairs}

		//We use x_F = z[~G] and y_G = z[G].
		//Since G is initally all indices, y = A'(0 - d), so z = -A'd.

		double[] z = new double[npairs + 1];
		CircularSplitAlgorithms.circularAtx(n, d, z);
		for (int i = 1; i <= npairs; i++) {
			z[i] = -z[i];
			if (Math.abs(z[i]) < params.blockPivotCutoff)
				z[i] = 0.0;
		}

		int p = 3;
		int iter = 1;

		final boolean[] infeasible = new boolean[npairs + 1]; //negative entries are 'infeasible'
		Arrays.fill(infeasible, false);
		int ninf = 0; //Number of infeasible indices
		for (int i = 1; i <= npairs; i++) {
			if (z[i] < 0.0) {
				infeasible[i] = true;
				ninf++;
			}
		}
		int N = npairs + 1;

		while (ninf > 0) {
			if (iter >= params.maxBlockPivotIterations) {
				System.err.println("WARNING: Max Iterations exceeded in Block Pivot Algorithm");
				break;
			}
			ninf = 0;
			for (int i = 1; i <= npairs; i++) {
				infeasible[i] = z[i] < 0.0;
				if (infeasible[i])
					ninf++;
			}
			if (ninf < N) {
				N = ninf;
				p = 3;
				for (int i = 1; i <= npairs; i++) {
					G[i] = G[i] ^ infeasible[i];   //G <- G XOR Infeasible
				}
			} else {
				if (p > 0) {
					p--;
					for (int i = 1; i <= npairs; i++) {
						// F[i] = F[i] ^ infeasible[i]; //XOR
						G[i] = G[i] ^ infeasible[i];
					}
				} else {
					for (int i = npairs; i >= 1; i--)
						if (infeasible[i]) {
							G[i] = !G[i];
							break;
						}
				}
			}

			//z[~G] = x[~G] and z[G] = y[G]  where x mininizes ||Ax - d||  such that x[G] = 0 and
			// y = A'(Ax - d).
			z = circularLeastSquares(n, G, d, params.pcgTol, z, params);

			for (int i = 1; i <= npairs; i++) {
				if (Math.abs(z[i]) < params.blockPivotCutoff)
					z[i] = 0.0;
			}
			iter++;

			progress.checkForCancel();
		}


		//Do one final refitting with these edges.
		z = circularLeastSquares(n, G, d, 1e-3 * params.pcgTol, z, params);
		for (int i = 1; i <= npairs; i++) {
			if (G[i])
				z[i] = 0.0;
		}
		double pgn = projectedGradientNorm(n, d, z, G);
		System.err.println("Block pivot pgnorm: " + pgn);

		return z;
	}


	/**
	 * Finds x that minimizes ||Ax - d|| such that x_i = 0 for all i \in G.
	 * Here A is the circular split weight matrix.
	 * This implementation solves the dual problem by means of a preconditioned conjugate gradient
	 * algorithm, with a 'bespoke' preconditioner.
	 *
	 * @param n      Number of taxa
	 * @param G      Mask: indicating variables to constrain to zero
	 * @param d      Vector of distances
	 * @param tol    Target tolerance: algorithm halts when ||Ax-d|| <= tol.
	 * @param x0     Initial vector (only entries such that G[i]=false are used)
	 * @param params Tuning parameters for the algorithm
	 * @return vector
	 */
	private static double[] circularLeastSquares(int n, boolean[] G, double[] d, double tol, double[] x0, BlockPivotParams params) {


		int npairs = n * (n - 1) / 2; //Dimensions of G,d.
		//int maxiter = params.maxPCGIterations;
		int maxiter = npairs + 10;

		//-----------
		//TODO: This code is for debugging and needs to be stripped out.

		if (params.useOldCG && false) {
			double[] r = new double[npairs];
			double[] u = new double[npairs];
			double[] p = new double[npairs];
			double[] y = new double[npairs];
			double[] W = new double[npairs];
			Arrays.fill(W, 1.0);
			double[] oldd = new double[npairs];
			for (int i = 0; i < npairs; i++)
				oldd[i] = d[i + 1];   //Need to translate the distance vector into the old form
			double[] Atd = new double[npairs + 1];
			circularAtx(n, d, Atd);
			double[] b = new double[npairs];
			for (int i = 0; i < npairs; i++)
				b[i] = Atd[i + 1];
			double[] xOld = new double[npairs];
			boolean[] activeOld = new boolean[npairs];
			for (int i = 0; i < npairs; i++)
				activeOld[i] = G[i + 1];
			NeighborNetSplits.circularConjugateGrads(n, npairs, r, u, p, y, W, b, activeOld, xOld);
			double[] x = new double[npairs + 1];
			for (int i = 1; i <= npairs; i++)
				x[i] = xOld[i - 1];

			//System.err.println("Norm after circular ls is " + pgnorm(n,G,d,x));

			return x;
		}

		//------------


		boolean usePreconditioner = params.usePreconditioner;

		int nG = 0; //Count the number of equality constraints
		for (int i = 1; i <= npairs; i++) {
			if (G[i])
				nG++;
		}

		if (nG == 0) { //No equality constraints - use straight solve.
			double[] x = new double[npairs + 1];
			circularSolve(n, d, x);
			return x;
		}

		//We use a 2dim array structure for the vector indices
		boolean[][] gcell = mask2blockmask(n, G);
		BlockXMatrix X = new BlockXMatrix(n, gcell);
		double[] unconstrained = new double[npairs + 1];
		circularSolve(n, d, unconstrained); //unconstrained = A^{-1} d
		double[][] b = vector2blocks(n, unconstrained, G);

		Preconditioner M = new Preconditioner(X, params.preconditionerBands);

		double[] nuvec = new double[npairs + 1];
		double[][] nu = vector2blocks(n, nuvec, G);

		double[][] r = blockvectorAdd(b, -1, X.multiply(nu));

		double[][] z;
		if (usePreconditioner)
			z = M.solve(r);
		else
			z = r.clone();

		double[][] p = blockclone(z);

		double rnorm, alpha, beta, rtz;
		int j;

		for (j = 1; j <= maxiter; j++) {
			rnorm = Math.sqrt(blockvectorDot(r, r));
			if (rnorm < tol)
				break;

			double[][] xp = X.multiply(p);
			alpha = blockvectorDot(r, z) / blockvectorDot(p, xp);

			nu = blockvectorAdd(nu, alpha, p);
			rtz = blockvectorDot(r, z);
			r = blockvectorAdd(r, -alpha, X.multiply(p));
			if (usePreconditioner)
				z = M.solve(r);
			else
				z = r.clone();

			beta = blockvectorDot(r, z) / rtz;
			p = blockvectorAdd(z, beta, p);
		}
		if (j > maxiter)
			System.err.println("WARNING: Preconditioned Conjugate Gradient reached maximum iterations");

		System.err.println("Number of iterations in PCG = " + j);
		nuvec = blocks2vector(n, nu, G);
		double[] x = new double[npairs + 1];
		double[] y = new double[npairs + 1];
		circularAinvT(n, nuvec, y);
		circularSolve(n, y, x);
		for (int i = 1; i <= npairs; i++)
			x[i] = unconstrained[i] - x[i];
		double[] Ax = new double[npairs + 1];
		circularAx(n, x, Ax);
		for (int i = 1; i <= npairs; i++)
			Ax[i] -= d[i];
		circularAtx(n, Ax, y);

		//Check KKT conditions.
		double xerr = 0.0, graderr = 0.0;
		for (int i = 1; i <= npairs; i++) {
			if (G[i])
				xerr = Math.max(xerr, Math.abs(x[i]));
			else
				graderr = Math.max(xerr, Math.abs(y[i]));
		}
		System.err.println("xerr = " + xerr + "\tgraderr = " + graderr);


		for (int i = 1; i <= npairs; i++) {
			if (G[i])
				x[i] = y[i];
		}
		return x;
	}

	/**
	 * Converts a boolean mask in  a single 1..npairs vector into separate boolean
	 * vectors for each block.
	 *
	 * @param n number of taxa
	 * @param G boolean vector, size n(n-1)/2
	 * @return array of boolean arrays, one for each block.
	 */
	public static boolean[][] mask2blockmask(int n, boolean[] G) {

		//Allocate arrays, initialising as false.
		boolean[][] gcell = new boolean[n][];
		for (int i = 1; i <= n - 2; i++) {
			gcell[i] = new boolean[n - i];
			Arrays.fill(gcell[i], false);
		}
		gcell[n - 1] = new boolean[n];
		Arrays.fill(gcell[n - 1], false);

		//Transfer mask values into blco format.
		int index = 1;
		for (int i = 1; i <= n - 1; i++) {
			for (int j = i + 1; j <= n - 1; j++) {
				gcell[i][j - i] = G[index];
				index++;
			}
			gcell[n - 1][i] = G[index];
			index++;
		}
		return gcell;
	}

	/**
	 * Converts a vector single 1..npairs arrays into separate
	 * vectors for each block.
	 *
	 * @param n number of taxa
	 * @param v double vector, size n(n-1)/2
	 * @param G boolean vector, size n(n-1)/2, indicating blocks
	 * @return array of double arrays, one for each block.
	 */
	public static double[][] vector2blocks(int n, double[] v, boolean[] G) {
		//TODO: Make this simpler using a map from pairs to blocks.

		double[][] vcell = new double[n][];

		int countlast = 0; //Number of elements in block n-1
		double[] vlast = new double[n]; //Elements in block n-1

		int index = 1;
		double[] vi = new double[n];
		for (int i = 1; i <= n - 1; i++) {
			Arrays.fill(vi, 0.0);
			int counti = 0;

			for (int j = i + 1; j <= n - 1; j++) {
				if (G[index]) {
					counti++;
					vi[counti] = v[index];
				}
				index++;
			}
			if (counti > 0)
				vcell[i] = Arrays.copyOfRange(vi, 0, counti + 1);

			if (G[index]) {
				countlast++;
				vlast[countlast] = v[index];
			}
			index++;
		}
		if (countlast > 0) {
			vcell[n - 1] = Arrays.copyOfRange(vlast, 0, countlast + 1);
		}
		return vcell;
	}

	/**
	 * Takes two block vectors x,y with the same sizes, and computes x + alpha*y
	 *
	 * @param x     block vector
	 * @param alpha double
	 * @param y     block vector
	 * @return block vector
	 */
	private static double[][] blockvectorAdd(double[][] x, double alpha, double[][] y) {
		assert x.length == y.length : "Trying to add block vectors with different lengths";
		double[][] z = new double[x.length][];

		for (int i = 1; i < x.length; i++) {
			if (x[i] != null) {
				assert x[i].length == y[i].length : "Trying to add block vectors with different row lengths";
				z[i] = new double[x[i].length];
				for (int j = 1; j < x[i].length; j++)
					z[i][j] = x[i][j] + alpha * y[i][j];
			}
		}
		return z;
	}

	/**
	 * Computes dot product of two block vectors
	 *
	 * @param x block vector
	 * @param y block vector
	 * @return block vector
	 */
	private static double blockvectorDot(double[][] x, double[][] y) {
		assert x.length == y.length : "Trying to compute dot product of block vectors with different lengths";
		double xty = 0.0;

		for (int i = 1; i < x.length; i++) {
			if (x[i] != null) {
				assert x[i].length == y[i].length : "Trying to add block vectors with different row lengths";
				for (int j = 1; j < x[i].length; j++)
					xty += x[i][j] * y[i][j];
			}
		}
		return xty;
	}

	/**
	 * Clone a block array.
	 *
	 * @param x double[][]
	 * @return clone of x
	 */
	private static double[][] blockclone(double[][] x) {
		double[][] y = new double[x.length][];
		for (int i = 0; i < x.length; i++) {
			if (x[i] != null)
				y[i] = x[i].clone();
		}
		return y;
	}

	/**
	 * Converts a vector stored as blocks into a single vector.
	 *
	 * @param n     number of taxa
	 * @param vcell vector of blocks
	 * @param G     boolean vector indicating which rows are kept
	 * @return vector
	 */
	public static double[] blocks2vector(int n, double[][] vcell, boolean[] G) {
		double[] v = new double[n * (n - 1) / 2 + 1];
		int countlast = 0;
		int index = 1;
		int counti;

		for (int i = 1; i <= n - 1; i++) {
			counti = 0;
			for (int j = (i + 1); j <= n - 1; j++) {
				if (G[index]) {
					counti++;
					v[index] = vcell[i][counti];
				}
				index++;
			}
			if (G[index]) {
				countlast++;
				v[index] = vcell[n - 1][countlast];
			}
			index++;
		}
		return v;
	}

	static private void printSet(boolean[] S) {
		for (int i = 0; i < S.length; i++) {
			if (S[i])
				System.err.print(i + ", ");
		}
	}

	static private int randomElement(boolean[] S, Random rand) {
		int n = 0;
		for (int i = 0; i < S.length; i++) {
			if (S[i])
				n++;
		}
		int k = rand.nextInt(n);
		int m = 0;
		for (int i = 0; i < S.length; i++) {
			if (S[i]) {
				if (m == k)
					return i;
				else
					m++;
			}

		}
		return -1;
	}

	/**
	 * Compute the projected gradient at x=z (with z(G)=0) for 0.5 * \|Ax - d\|^2 with x>=0.
	 *
	 * @param n
	 * @param d
	 * @param z
	 * @param G
	 * @return
	 */
	private static double projectedGradientNorm(int n, double d[], double[] z, boolean[] G) {
		int npairs = n * (n - 1) / 2;
		double[] x = z.clone();
		for (int i = 1; i <= npairs; i++) {
			if (G[i])
				x[i] = 0.0;
		}

		double[] Ax = new double[npairs + 1];
		circularAx(n, x, Ax);
		for (int i = 1; i <= npairs; i++)
			Ax[i] -= d[i];
		double[] grad = new double[npairs + 1];
		CircularSplitAlgorithms.circularAtx(n, Ax, grad);
		double gtg = 0.0;
		for (int i = 1; i <= npairs; i++) {
			if (G[i] && grad[i] > 0)
				grad[i] = 0;
			else
				gtg += grad[i] * grad[i];
		}
		return Math.sqrt(gtg);
	}

	/**
	 * Compute the projected gradient at x=z (with z(G)=0) for 0.5 * \|Ax - d\|^2 with x>=0.
	 *
	 * @param n
	 * @param d
	 * @param z
	 * @param G
	 * @return
	 */
	private static double functionVal(int n, double[] d, double[] z, boolean[] G) {
		int npairs = n * (n - 1) / 2;
		double[] x = z.clone();
		for (int i = 1; i <= npairs; i++) {
			if (G[i])
				x[i] = 0.0;
		}

		double[] r = new double[npairs + 1];
		circularAx(n, x, r);
		for (int i = 1; i <= npairs; i++)
			r[i] -= d[i];
		return norm(r); //TODO Check if norm or norm squared wanted here.
	}

	static public void test(int n) throws CanceledException {
//        Random rand = new Random();
//
//        int npairs = n*(n-1)/2;
//        double[] d = new double[npairs+1];
//        for(int i=1;i<=npairs;i++)
//            d[i] = rand.nextDouble();
//
//        double[] y = circularBlockPivot(n,d);

		double[] d = new double[]{0, 20, 56, 66, 63, 36, 32, 32, 16, 17, 18, 18, 19, 12, 12, 13, 13, 16, 17, 1, 61, 69, 61, 41, 34, 33, 24, 24, 28, 28, 31, 25, 30, 30, 30, 31, 32, 21, 41, 57, 60, 63, 66, 62, 62, 61, 62, 64, 59, 58, 61, 59, 59, 60, 57, 61, 61, 65, 69, 66, 66, 65, 66, 67, 67, 68, 66, 67, 67, 68, 65, 59, 58, 72, 59, 61, 62, 61, 65, 62, 66, 64, 64, 64, 64, 62, 16, 41, 30, 29, 30, 31, 33, 29, 31, 28, 31, 32, 33, 35, 16, 26, 26, 30, 28, 40, 26, 25, 24, 23, 26, 25, 24, 26, 27, 27, 27, 38, 27, 26, 27, 26, 25, 26, 29, 3, 4, 4, 8, 8, 14, 12, 13, 15, 16, 15, 3, 3, 7, 10, 14, 12, 13, 15, 16, 16, 2, 8, 11, 13, 14, 14, 16, 17, 17, 8, 11, 15, 14, 13, 15, 16, 17, 11, 14, 13, 13, 15, 16, 18, 7, 6, 6, 10, 11, 11, 7, 7, 12, 11, 13, 4, 8, 10, 12, 4, 5, 12, 1, 15, 16};
		n = 20;
		long startTime = System.currentTimeMillis();
		BlockPivotParams params = new BlockPivotParams();
		params.useOldCG = true;

		double[] y = circularBlockPivot(n, d, new ProgressSilent(), params);
		long finishTime = System.currentTimeMillis();
		System.err.println("Block Pivot took " + (finishTime - startTime) + " milliseconds");

		//Now test with the old algorithm
		int npairs = n * (n - 1) / 2;
		double[] W = new double[npairs];
		for (int i = 0; i < npairs; i++)
			W[i] = 1.0;
		double[] y2 = new double[npairs];

		NeighborNetSplits.Regularization reg = nnls;

		startTime = System.currentTimeMillis();

		NeighborNetSplits.runActiveConjugate(n, npairs, d, W, y2, reg, 0);
		finishTime = System.currentTimeMillis();
		System.err.println("Old algorithm took " + (finishTime - startTime) + " milliseconds");

		double ydiff = 0.0;
		for (int i = 0; i < npairs; i++) {
			ydiff = ydiff + (y[i] - y2[i]) * (y[i] - y2[i]);
		}
		System.err.println("Two solutions differed by 2-norm of " + Math.sqrt(ydiff));

	}
}
