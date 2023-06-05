/*
 *  NeighborNetSplitWeightOptimizerSplitsTree4.java Copyright (C) 2023
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

import jloda.util.BitSetUtils;
import splitstree6.data.DistancesBlock;
import splitstree6.data.parts.ASplit;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;

/**
 * Given a circular ordering and a distance matrix,
 * computes the unconstrained or constrained least square weighted splits
 * <p/>
 * For all vectors, the canonical ordering of pairs is (0,1),(0,2),...,(0,n-1),(1,2),(1,3),...,(1,n-1), ...,(n-1,n)
 * <p/>
 * (i,j) ->  (2n - i -3)i/2 + j-1  .
 * <p/>
 * Increase i -> increase index by n-i-2
 * Decrease i -> decrease index by n-i-1.
 * <p/>
 * <p/>
 * x[i][j] is the split {i+1,i+2,...,j} | -------
 * Dave Bryant, 2006
 */
public class NeighborNetSplitWeightOptimizerSplitsTree4 {
	/* Epsilon constant for the conjugate gradient algorithm */
	private static final double CG_EPSILON = 0.0001;

	/**
	 * Class  CircularSplitWeights.Options
	 *
	 * @author dbryant
	 * <p>
	 * A utility class for passing sets of options to the least squares optimizer (and within the procedure).
	 * <p>
	 * Options are:
	 * cutoff			Threshold for split weights
	 * regularization	Enumerated type for selecting method used
	 * lambdaFraction	Regularization parameter. (WILL CHANGE TO: lambda = 0 -> no regularization; lambda = 1-> complete regularization.)
	 * lassoWeights		Weights used when compute the lasso penalty function
	 * var				String used to describe variance options.
	 */
	static public class Options {
		public enum Regularization {
			NNLS,  //non-negative least squares (standard)
			LASSO,  //lasso
			NORMLASSO,   //lasso, weighted by norms of columns in X matrix
			INTERNALLASSO  //partial lasso applied only to internal splits, weighted by norms
		}

		//private boolean constrained = true; //Constrain non-negative weights
		private double cutoff = 0.0; //Threshold for split weights
		private Regularization regularization = Regularization.NNLS;
		private double lambdaFraction = 1.0; //L1 regularization parameter
		private String variance = null;

		public Options() {
			variance = "ols";
			cutoff = 0.000001;
		}

		public Options(String variance, double cutoff) {
			this.variance = variance;
			this.cutoff = cutoff;
		}

		public Options(String var, double cutoff, Regularization regularization) {
			this.variance = var;
			this.cutoff = cutoff;
			this.regularization = regularization;
		}

		public void setLambdaFraction(double lambdaFraction) {
			this.lambdaFraction = lambdaFraction;
		}
	}

	/**
	 * Compute optimal weight squares under least squares for Splits compatible with a circular ordering.
	 * <p>
	 * This version carries out adaptive L1 regularisation, controlled by the parameter lambdaFraction
	 * <p>
	 * That is, it minimizes  0.5*||Ax - d||^2_2  +  \lambda ||x||_1
	 *
	 * @param ordering Circular ordering
	 * @param dist     Input distance
	 * @return Splits  splits with the estimated weights.
	 */
	public static ArrayList<ASplit> apply(int[] ordering, DistancesBlock dist) {
		return computeWeightedSplits(ordering, dist, new Options());
	}

	/**
	 * Compute optimal weight squares under least squares for Splits compatible with a circular ordering.
	 * <p>
	 * This version carries out adaptive L1 regularisation, controlled by the parameter lambdaFraction
	 * <p>
	 * That is, it minimizes  0.5*||Ax - d||^2_2  +  \lambda ||x||_1
	 *
	 * @param ordering Circular ordering
	 * @param dist     Input distance
	 * @param options  parameters for the optimization and model
	 * @return Splits  splits with the estimated weights.
	 */
	static public ArrayList<ASplit> computeWeightedSplits(int[] ordering, DistancesBlock dist, Options options) {
		int ntax = dist.getNtax();
		int npairs = (ntax * (ntax - 1)) / 2;

		var splits = new ArrayList<ASplit>();
		//Handle n=1,2 separately.
		if (ntax == 1)
			return splits;
		if (ntax == 2) {
			var d_ij = dist.get(ordering[1], ordering[2]);
			if (d_ij > 0.0) {
				splits.add(new ASplit(BitSetUtils.asBitSet(ordering[1]), BitSetUtils.asBitSet(ordering[2]), d_ij));
			}
			return splits;
		}

		/* Re-order taxa so that the ordering is 0,1,2,...,n-1 */
		double[] d = setupD(dist, ordering);
		double[] v = setupV(dist, options.variance, ordering);
		double[] x = new double[npairs];

		/* Initialize the weight matrix */
		double[] W = new double[npairs];
		for (int k = 0; k < npairs; k++) {
			if (v[k] == 0.0)
				W[k] = 10E10;
			else
				W[k] = 1.0 / v[k];
		}
		/* Find the constrained optimal values for x */

		runActiveConjugate(ntax, d, W, x, options);

		/* Construct the splits with the appropriate weights */
		int index = 0;
		for (int i = 0; i < ntax; i++) {
			var set = new BitSet();
			for (int j = i + 1; j < ntax; j++) {
				set.set(ordering[j + 1]);
				if (x[index] > options.cutoff) {
					splits.add(new ASplit(set, dist.getNtax(), x[index]));
				}
				index++;
			}
		}
		return splits;
	}

	//    static public Splits computeWeightedSplits(int[] ordering,
//            Distances dist, String var, Options options)
//            { return computeWeightedSplits(ordering,dist,var,options);}

	/**
	 * setup working distance so that ordering is trivial.
	 * Note the the code assumes that taxa are labeled 0..ntax-1 and
	 * we do the transition here. It is undone when extracting the splits
	 *
	 * @param dist     Distances block
	 * @param ordering circular ordering
	 * @return double[] distances stored as a vector
	 */
	static private double[] setupD(DistancesBlock dist, int[] ordering) {
		int ntax = dist.getNtax();
		double[] d = new double[(ntax * (ntax - 1)) / 2];
		int index = 0;
		for (int i = 0; i < ntax; i++)
			for (int j = i + 1; j < ntax; j++)
				d[index++] = dist.get(ordering[i + 1], ordering[j + 1]);
		return d;
	}

	static private double[] setupV(DistancesBlock dist, String var, int[] ordering) {
		int ntax = dist.getNtax();
		int npairs = ((ntax - 1) * ntax) / 2;
		double[] v = new double[npairs];

		int index = 0;
		for (int i = 0; i < ntax; i++)
			for (int j = i + 1; j < ntax; j++) {
				double dij = dist.get(ordering[i + 1], ordering[j + 1]);
				if (var.equalsIgnoreCase("ols"))
					v[index] = 1.0;
				else if (var.equalsIgnoreCase("fm1"))
					v[index] = dij;
				else if (var.equalsIgnoreCase("fm2"))
					v[index] = dij * dij;
				else
					v[index] = dist.getVariance(ordering[i + 1], ordering[j + 1]);
				index++;
			}
		return v;
	}


	/**
	 * Compute the branch lengths for unconstrained least squares using
	 * the formula of Chepoi and Fichet (this takes O(N^2) time only!).
	 *
	 * @param n the number of taxa
	 * @param d the distance matrix
	 * @param x the split weights
	 */
	static private void runUnconstrainedLS(int n, double[] d, double[] x) {
		int index = 0;

		for (int i = 0; i <= n - 3; i++) {
			//index = (i,i+1)
			//x[i,i+1] = (d[i][i+1] + d[i+1][i+2] - d[i,i+2])/2
			x[index] = (d[index] + d[index + (n - i - 2) + 1] - d[index + 1]) / 2.0;
			index++;
			for (int j = i + 2; j <= n - 2; j++) {
				//x[i][j] = ( d[i,j] + d[i+1,j+1] - d[i,j+1] - d[i+1][j])
				x[index] = (d[index] + d[index + (n - i - 2) + 1] - d[index + 1] - d[index + (n - i - 2)]) / 2.0;
				index++;
			}
			//index = (i,n-1)

			if (i == 0) //(0,n-1)
				x[index] = (d[0] + d[n - 2] - d[2 * n - 4]) / 2.0; //(d[0,1] + d[0,n-1] - d[1,n-1])/2
			else
				//x[i][n-1] == (d[i,n-1] + d[i+1,0] - d[i,0] - d[i+1,n-1])
				x[index] = (d[index] + d[i] - d[i - 1] - d[index + (n - i - 2)]) / 2.0;
			index++;
		}
		//index = (n-2,n-1)
		x[index] = (d[index] + d[n - 2] - d[n - 3]) / 2.0;
	}

	/**
	 * Returns the array indices for the smallest propKept proportion of negative values in x.
	 * In the case of ties, priority is given to the earliest entries.
	 * Size of resulting array will be propKept * (number of negative entries) rounded up.
	 *
	 * @param x        returns an array
	 * @param propKept the
	 * @return int[] array of indices
	 */
	static private int[] worstIndices(double[] x, double propKept) {


		if (propKept == 0)
			return null;

		int n = x.length;

		int numNeg = 0;
		for (double aX1 : x)
			if (aX1 < 0.0)
				numNeg++;

		if (numNeg == 0)
			return null;

		//Make a copy of negative values in x.
		double[] xcopy = new double[numNeg];
		int j = 0;
		for (double aX : x)
			if (aX < 0.0)
				xcopy[j++] = aX;

		//Sort the copy
		Arrays.sort(xcopy);

		//Find the cut-off value. All values greater than this should
		//be returned, as well as some (or perhaps all) of the values
		//equal to this.
		int nkept = (int) Math.ceil(propKept * numNeg);  //Ranges from 1 to n
		double cutoff = xcopy[nkept - 1];

		//we now fill the result vector. Values < cutoff are filled
		//in from the front. Values == cutoff are filled in the back.
		//Values filled in from the back can be overwritten by values
		//filled in from the front, but not vice versa.
		int[] result = new int[nkept];
		int front = 0, back = nkept - 1;

		for (int i = 0; i < n; i++) {
			if (x[i] < cutoff)
				result[front++] = i; //Definitely in the top entries.
			else if (x[i] == cutoff) {
				if (back >= front)
					result[back--] = i;
			}
		}
		return result;
	}

	@SuppressWarnings("unused")
	static private void printvec(String msg, double[] x) {
		int n = x.length;
		DecimalFormat fmt = new DecimalFormat("#0.00000");

		System.out.print(msg + "\t");
		for (double aX : x) System.out.print(" " + fmt.format(aX));
		System.out.println();
	}

	/**
	 * Uses an active set method with the conjugate gradient algorithm to find x that minimises
	 * <p/>
	 * 0.5 * (Ax - d)'W(Ax-d) + \lambda 1'x   s.t. x \geq 0
	 * <p/>
	 * Here, A is the design matrix for the set of cyclic splits with ordering 0,1,2,...,n-1
	 * d is the distance vector, with pairs in order (0,1),(0,2),...,(0,n-1),(1,2),(1,3),...,(1,n-1), ...,(n-1,n)
	 * W is a vector of variances for d, with pairs in same order as d.
	 * x is a vector of split weights, with pairs in same order as d. The split (i,j), for i<j, is {i,i+1,...,j-1}| rest
	 * lambda is the regularisation parameter, given by lambda = max_i (A'Wd)_i   * ( 1 - lambdaFraction)
	 * Note that lambdaFraction = 1 => lambda = 0, and lambdaFraction = 0 => x = 0.
	 *
	 * @param ntax    The number of taxa
	 * @param d       the distance matrix
	 * @param W       the weight matrix
	 * @param x       the split weights
	 * @param options fraction parameter for lambda regularisation
	 */
	static private void runActiveConjugate(int ntax, double[] d, double[] W, double[] x, Options options) {
		final boolean collapse_many_negs = true;

		int npairs = d.length;
		if (W.length != npairs || x.length != npairs)
			throw new IllegalArgumentException("Vectors d,W,x have different dimensions");

		/* First evaluate the unconstrained optima. If this is feasible then we don't have to do anything more! */
		NeighborNetSplitWeightOptimizerSplitsTree4.runUnconstrainedLS(ntax, d, x);
		boolean all_positive = true;
		for (int k = 0; k < npairs && all_positive; k++)
			if (x[k] < 0.0) {
				all_positive = false;
				break;
			}

		if (all_positive) /* If the unconstrained optimum is feasible then it is also the constrained optimum */
			return;

		/* Allocate memory for the "utility" vectors */
		double[] r = new double[npairs];
		double[] w = new double[npairs];
		double[] p = new double[npairs];
		double[] y = new double[npairs];
		double[] old_x = new double[npairs];
		Arrays.fill(old_x, 1.0);

		/* Initialise active - originally no variables are active (held to 0.0) */
		boolean[] active = new boolean[npairs];
		boolean[] fixedActive = new boolean[npairs];
		Arrays.fill(active, false);
		Arrays.fill(fixedActive, false);

		/* Allocate and compute AtWd */
		double[] AtWd = new double[npairs];
		for (int k = 0; k < npairs; k++)
			y[k] = W[k] * d[k];
		NeighborNetSplitWeightOptimizerSplitsTree4.calculateAtx(ntax, y, AtWd);

		/* Compute lambda parameter */
		boolean computeRegularised = (options.regularization != NeighborNetSplitWeightOptimizerSplitsTree4.Options.Regularization.NNLS);
		double lambda = 0.0;

		if (computeRegularised) {
			double maxAtWd = 0.0;
			for (double val : AtWd)
				if (val > maxAtWd)
					maxAtWd = val;
			lambda = maxAtWd * (1.0 - options.lambdaFraction);

			/* Replace AtWd with AtWd = lambda. This has same effect as regularisation term */
			for (int k = 0; k < npairs; k++)
				AtWd[k] -= lambda;
		}

		boolean first_pass = true; //This is the first time through the loops.
		while (true) {
			while (true) /* Inner loop: find the next feasible optimum */ {
				if (!first_pass)  /* The first time through we use the unconstrained branch lengths */
					NeighborNetSplitWeightOptimizerSplitsTree4.circularConjugateGrads(ntax, npairs, r, w, p, y, W, AtWd, active, x);
				first_pass = false;

				if (collapse_many_negs) { /* Typically, a large number of edges are negative, so on the first
                                                pass of the algorithm we add the worst 60% to the active set */
					int[] entriesToContract = worstIndices(x, 0.6);
					if (entriesToContract != null) {
						for (int index : entriesToContract) {
							x[index] = 0.0;
							active[index] = true;
						}
						NeighborNetSplitWeightOptimizerSplitsTree4.circularConjugateGrads(ntax, npairs, r, w, p, y, W, AtWd, active, x); /* Re-optimise, so that the current x is always optimal */
					}
				}
				int min_i = -1;
				double min_xi = -1.0;
				for (int i = 0; i < npairs; i++) {
					if (x[i] < 0.0) {
						double xi = (old_x[i]) / (old_x[i] - x[i]);
						if ((min_i == -1) || (xi < min_xi)) {
							min_i = i;
							min_xi = xi;
						}
					}
				}

				if (min_i == -1) /* This is a feasible solution - go to the next stage to check if its also optimal */
					break;
				else {/* There are still negative edges. We move to the feasible point that is closest to
                							x on the line from x to old_x */

					for (int i = 0; i < npairs; i++) /* Move to the last feasible solution on the path from old_x to x */
						if (!active[i])
							old_x[i] += min_xi * (x[i] - old_x[i]);
					active[min_i] = true; /* Add the first constraint met to the active set */
					x[min_i] = 0.0; /* This fixes problems with round-off errors */
				}
			}

			/* Find i,j that minimizes the gradient over all i,j in the active set. Note that grad = (AtWAb-AtWd)  */
			calculateAb(ntax, x, y);
			for (int i = 0; i < npairs; i++)
				y[i] *= W[i];
			calculateAtx(ntax, y, r); /* r = AtWAx */

			/* We check to see that we are at a constrained minimum.... that is that the gradient is positive for
			 * all i,j in the active set.
			 */
			int min_i = -1;
			double min_grad = 1.0;
			for (int i = 0; i < npairs; i++) {
				r[i] -= AtWd[i];
				//r[i] *= 2.0;
				if (active[i] && !fixedActive[i]) {
					double grad_ij = r[i];
					if ((min_i == -1) || (grad_ij < min_grad)) {
						min_i = i;

						min_grad = grad_ij;
					}
				}
			}

			if ((min_i == -1) || (min_grad > -0.0001)) {
//            	if (computeRegularised) {
//            		/* Return to the main loop, without the regularisation term, and fixing active all variables currently active */
//            		for (int k = 0; k < npairs; k++)
//                        y[k] = W[k] * d[k];
//            		CircularSplitWeights.calculateAtx(ntax, y, AtWd);
//            		for(int k=0; k<npairs; k++)
//            			fixedActive[k] = active[k];
//            		computeRegularised = false;
//            	}
//            	else
				return; /* We have arrived at the constrained optimum */
			} else
				active[min_i] = false;

		}
	}

	/* Compute the row sum in d. */

	static private double rowsum(int n, double[] d, int k) {
		double r = 0;
		int index = 0;

		if (k > 0) {
			index = k - 1;     //The index for (0,k)

			//First sum the pairs (i,k) for i<k
			for (int i = 0; i < k; i++) {
				r += d[index];
				index += (n - i - 2);
			}
			index++;
		}
		//we now have index = (k,k+1)
		//Now sum the pairs (k,j) for k<j
		for (int j = k + 1; j < n; j++)
			r += d[index++];

		return r;
	}


	/**
	 * Computes p = A^Td, where A is the topological matrix for the
	 * splits with circular ordering 0,1,2,....,ntax-1
	 * *
	 *
	 * @param n number of taxa
	 * @param d distance matrix
	 * @param p the result
	 */
	static private void calculateAtx(int n, double[] d, double[] p) {

//First the trivial splits
		int index = 0;
		for (int i = 0; i < n - 1; i++) {
			p[index] = rowsum(n, d, i + 1);
			index += (n - i - 1);
		}

		//Now the splits separating out two.
		index = 1;
		for (int i = 0; i < n - 2; i++) {
			//index = (i,i+2)

			//p[i][i+2] = p[i][i+1] + p[i + 1][i + 2] - 2 * d[i + 1][i + 2];
			p[index] = p[index - 1] + p[index + (n - i - 2)] - 2 * d[index + (n - i - 2)];
			index += (n - i - 2) + 1;
		}

		//Now the remaining splits
		for (int k = 3; k <= n - 1; k++) {
			index = k - 1;
			for (int i = 0; i <= n - k - 1; i++) {
				//index = (i,i+k)

				// p[i][j] = p[i][j - 1] + p[i+1][j] - p[i+1][j - 1] - 2.0 * d[i+1][j];
				p[index] = p[index - 1] + p[index + n - i - 2] - p[index + n - i - 3] - 2.0 * d[index + n - i - 2];
				index += (n - i - 2) + 1;
			}
		}
	}

	/**
	 * Computes d = Ab, where A is the topological matrix for the
	 * splits with circular ordering 0,1,2,....,ntax-1
	 *
	 * @param n number of taxa
	 * @param b split weights
	 * @param d pairwise distances from split weights
	 */
	static private void calculateAb(int n, double[] b, double[] d) {
		double d_ij;

		//First the pairs distance one apart.
		int index;
		int dindex = 0;

		for (int i = 0; i <= n - 2; i++) {
			d_ij = 0.0;
			//Sum over splits (k,i) 0<=k<i.
			index = i - 1;  //(0,i)
			for (int k = 0; k <= i - 1; k++) {
				d_ij += b[index];  //(k,i)
				index += (n - k - 2);
			}
			index++;
			//index = (i,i+1)
			for (int k = i + 1; k <= n - 1; k++)  //sum over splits (i,k)  i+1<=k<=n-1
				d_ij += b[index++];

			d[dindex] = d_ij;
			dindex += (n - i - 2) + 1;
		}

		//Distances two apart.
		index = 1; //(0,2)
		for (int i = 0; i <= n - 3; i++) {
//            d[i ][i+2] = d[i ][i+1] + d[i + 1][i + 2] - 2 * b[i][i+1];

			d[index] = d[index - 1] + d[index + (n - i - 2)] - 2 * b[index - 1];
			index += 1 + (n - i - 2);
		}

		for (int k = 3; k <= n - 1; k++) {
			index = k - 1;
			for (int i = 0; i <= n - k - 1; i++) {
				//int j = i + k;
				//d[i][j] = d[i][j - 1] + d[i+1][j] - d[i+1][j - 1] - 2.0 * b[i][j - 1];
				d[index] = d[index - 1] + d[index + (n - i - 2)] - d[index + (n - i - 2) - 1] - 2.0 * b[index - 1];
				index += 1 + (n - i - 2);
			}
		}
	}

	/**
	 * Computes sum of squares of the lower triangle of the matrix x
	 *
	 * @param x the matrix
	 * @return sum of squares of the lower triangle
	 */
	static private double norm(double[] x) {
		double ss = 0.0;
		double xk;
		for (double aX : x) {
			xk = aX;
			ss += xk * xk;
		}
		return ss;
	}

	/**
	 * Conjugate gradient algorithm solving A^tWA x = b (where b = AtWd)
	 * such that all x[i][j] for which active[i][j] = true are set to zero.
	 * We assume that x[i][j] is zero for all active i,j, and use the given
	 * values for x as our starting vector.
	 *
	 * @param ntax   the number of taxa
	 * @param npairs dimension of b and x
	 * @param r      stratch matrix
	 * @param w      stratch matrix
	 * @param p      stratch matrix
	 * @param y      stratch matrix
	 * @param W      the W matrix
	 * @param b      the b matrix
	 * @param active the active constraints
	 * @param x      the x matrix
	 */
	static private void circularConjugateGrads(int ntax, int npairs, double[] r, double[] w, double[] p, double[] y,
											   double[] W, double[] b, boolean[] active, double[] x) {
		int kmax = ntax * (ntax - 1) / 2; /* Maximum number of iterations of the cg algorithm (probably too many) */

		calculateAb(ntax, x, y);

		for (int k = 0; k < npairs; k++)
			y[k] = W[k] * y[k];
		calculateAtx(ntax, y, r); /*r = AtWAx */

		for (int k = 0; k < npairs; k++)
			if (!active[k])
				r[k] = b[k] - r[k];
			else
				r[k] = 0.0;

		double rho = norm(r);
		double rho_old = 0;

		double e_0 = CG_EPSILON * Math.sqrt(norm(b));
		int k = 0;

		while ((rho > e_0 * e_0) && (k < kmax)) {

			k = k + 1;
			if (k == 1) {
				System.arraycopy(r, 0, p, 0, npairs);

			} else {
				double beta = rho / rho_old;
				//System.out.println("bbeta = " + beta);
				for (int i = 0; i < npairs; i++)
					p[i] = r[i] + beta * p[i];

			}

			calculateAb(ntax, p, y);
			for (int i = 0; i < npairs; i++)
				y[i] *= W[i];

			calculateAtx(ntax, y, w); /*w = AtWAp */
			for (int i = 0; i < npairs; i++)
				if (active[i])
					w[i] = 0.0;

			double alpha = 0.0;
			for (int i = 0; i < npairs; i++)
				alpha += p[i] * w[i];
			alpha = rho / alpha;

			/* Update x and the residual, r */
			for (int i = 0; i < npairs; i++) {
				x[i] += alpha * p[i];
				r[i] -= alpha * w[i];
			}
			rho_old = rho;
			rho = norm(r);
		}
	}
}
