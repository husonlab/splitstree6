/*
 * LeastSquares.java Copyright (C) 2022 Daniel H. Huson
 *
 * (Some files contain contributions from other authors, who are then mentioned separately.)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package splitstree6.algorithms.utils;

import Jama.Matrix;
import jloda.util.CanceledException;
import jloda.util.progress.ProgressListener;
import splitstree6.data.DistancesBlock;
import splitstree6.data.SplitsBlock;

import java.util.BitSet;

/**
 * least squares
 * David Bryant, 2004
 */
public class LeastSquares {

    /* ------------------------
       Class variables
     * ------------------------ */

	static final double EPSILON = 1e-10;

	/**
	 * Computes the optimal least squares values for split weights, with or without a positivity constraint
	 * Uses  fairly inefficient Cholesky decomposition algorithm (with updating).
	 *
	 * @param splits    Splits block
	 * @param dist      Distances block (with same number of taxa as splits block)
	 * @param constrain Flag indicating whether to constrain to non-negative weights (true) or allow negative values
	 */
	//TODO: Write test unit for this.
	//ToDo: Solve using the Conjugate Gradient method
	static public void optimizeLS(ProgressListener progress, SplitsBlock splits, DistancesBlock dist, boolean constrain) throws CanceledException {
		// todo: implement constrain option
		if (constrain)
			System.err.println("Least Squares: Constrain option not implemented, using OLS");

		//if (splits.getNtax() != dist.getNtax())
		//  throw new IllegalArgumentException("Splits and distances have different numbers of taxa");

		//First compute the matrices AtWA and vector AtWd
		// todo use varType from DistancesFormat?
		//if (dist.getFormat().getVarType().equalsIgnoreCase("ols"))
		var Amat = getTopoMatrixOLS(progress, dist.getNtax(), splits); //Much faster in the case of OLS
		//else
		//Amat = getTopoMatrixWLS(splits, dist);

		var AtWd = getAtWd(splits, dist);

		//Now computeCycle the active set method to compute the optimal weights
		//todo
		// ActiveSet Aset = new ActiveSet(Amat, AtWd, constrain);

		int nsplits = splits.getNsplits();
		//splits.getFormat().setWeights(true);
		for (int i = 0; i < nsplits; i++) {
			//todo
			// splits.getSplits().get(i).setWeight(Aset.getSoln(i - 1));
		}
		splits.setFit(-1);
		//splits.getProperties().setLSFit(-1);
	}

	// todo do a separate class as in ST4 ???
	// todo DynamicCholesky
    /*private static Matrix getActiveSet(Matrix XtX, Matrix Xty, boolean constrain){
        final double EPSILON = 1E-10;
        int n;

        //System.out.println("Matrix argument passed is\n");
        //XtX.printText(4,4);

        DynamicCholesky A = new DynamicCholesky(XtX);

        n = XtX.getRowDimension();
        Matrix x = new Matrix(n, 1, 0.0);
        //System.out.println("Checking Cholesky Decomposition!");
        //A.CheckCholesky();

        //Xty.printText(10,4);

        if (!constrain) {
            //Unconstrained solution. Just solve using Cholesky decomposition and exit.
            x = A.solve(Xty);
            //Set negative values to zero.
            for (int i = 0; i < n; i++) {
                if (x.get(i, 0) < 0.0)
                    x.set(i, 0, 0.0);
            }
            //x.printText(4,4);
            return;
        }
        //Constrained solution required.
        //Active set stored in mask entry of A - its initially empty, as we want.

        //Initial solution is arbitrary - all 1's.
        Matrix old_x = new Matrix(n, 1, 1.0);
        //System.out.println("Initial feasible solution");
        //old_x.printText(10,4);
        for (; ; ) {
            for (; ; ) {
                x = A.solve(Xty); // Solve current constrained solution.
                //System.out.println("Current unconstrained solution:");
                //x.printText(10,4);
                //Find the last point on the path from old_x to x that is non-negative.
                int bad_i = -1;
                double min_delta = 100000000000.0;
                for (int i = 0; i < n; i++) {
                    if (x.get(i, 0) < -EPSILON) {
                        double oldx_i = old_x.get(i, 0);
                        double x_i = x.get(i, 0);
                        double delta = oldx_i / (oldx_i - x_i);
                        if (delta < min_delta) {
                            bad_i = i;
                            min_delta = delta;
                        }
                    }
                }
                //System.out.println(bad_i);
                //System.out.println(min_delta);

                if (bad_i == -1) {
                    break; //This is a feasible solution. Skip to outer loop.
                }

                //Move oldx to the last feasible point on line from oldx to x
                for (int i = 0; i < n; i++) {
                    if (A.getmask(i))
                        continue;
                    double oldx_i = old_x.get(i, 0);
                    double x_i = x.get(i, 0);
                    oldx_i = oldx_i + min_delta * (x_i - oldx_i);
                    old_x.set(i, 0, oldx_i);
                }

                //old_x.plusEquals(x.times(bad_val));
                //System.out.println("This feasible solution");
                //old_x.printText(10,4);
                A.maskRow(bad_i);

            }

            //Check that gradient is non-negative for all indices in active set.
            // and find index with smallest gradient.
            // Note: gradient/2 = Ax - Xty

            int bad_i = -1;
            double bad_val = 100000000000.0;
            Matrix grad;

            //System.out.println("Gradient");
            grad = XtX.times(x).minus(Xty).times(2.0);
            //grad.printText(10,4);

            for (int i = 0; i < n; i++) {
                if (A.getmask(i)) {
                    double grad_i = grad.get(i, 0);
                    grad_i *= 2.0;
                    if (grad_i < bad_val) {
                        bad_i = i;
                        bad_val = grad_i;
                    }
                } else {
                    if (Math.abs(grad.get(i, 0)) > EPSILON) {
                        throw new IllegalStateException("Problem in the active set method");
                    }

                }
            }
            if (bad_val > -EPSILON) {
                break;
            }
            A.unmaskRow(bad_i); //Remove worst from mask

        }
    }*/

	/**
	 * Return matrix A'A, where A is the topological matrix for the set of splits.
	 * Algorithm takes O(m^2n) time  where m=number of splits and n is number of taxa.
	 *
	 * @param splits Splits blockk
	 * @return Matrix. The matrix A'A
	 */

	static private Matrix getTopoMatrixOLS(ProgressListener progress, int ntaxa, SplitsBlock splits) throws CanceledException {
		int nsplits = splits.getNsplits();

		Matrix Amat = new Matrix(nsplits, nsplits);
		BitSet I, J;
		int Isize, Jsize;
		progress.setMaximum(nsplits);
		progress.setProgress(0);
		for (int i = 0; i < nsplits; i++) {
			I = splits.getSplits().get(i).getA(); //Note - indices off by one.
			Isize = I.cardinality();
			for (int j = 0; j < i; j++) {
				J = (BitSet) splits.getSplits().get(j).getA().clone();
				Jsize = J.cardinality();
				J.and(I);
				int x = J.cardinality(); //Size of intersection
				int Aij = x * (ntaxa - Isize - Jsize + x) + (Isize - x) * (Jsize - x);
				Amat.set(i, j, Aij);
				Amat.set(j, i, Aij);
			}
			Amat.set(i, i, Isize * (ntaxa - Isize));
			progress.incrementProgress();
		}
		return Amat;
	}

	/**
	 * Returns Atd where A is the topological matrix for the splits and W is the
	 * diagonal matrix with 1/var on the diagonal. Variances are taken from the
	 * distances block.
	 *
	 * @param splits Splits
	 * @param dist   Distances
	 * @return Matrix (vector) AtWd, using variances from the distances block
	 */

	static private Matrix getAtWd(SplitsBlock splits, DistancesBlock dist) {
		int nsplits = splits.getNsplits();
		int ntaxa = dist.getNtax();

		//if (splits.getNtax() != dist.getNtax()) //todo test? can happens?
		//  throw new IllegalArgumentException("Splits and distances have different numbers of taxa");


		Matrix AtWd = new Matrix(nsplits, 1);
		double AtWdi;
		BitSet I;
		for (int i = 0; i < nsplits; i++) {
			AtWdi = 0.0;
			I = splits.getSplits().get(i).getA(); //Note - indices off by one.
			for (int a = 1; a <= ntaxa; a++) {
				if (!I.get(a))
					continue;
				for (int b = 1; b <= ntaxa; b++) { //Note - have to loop through all 1..ntaxa
					if (I.get(b))
						continue;
					//At this point, we know a and b are on opposite sides of I
					AtWdi += dist.get(a, b) / dist.getVariance(a, b);
				}
			}
			AtWd.set(i, 0, AtWdi);
		}
		return AtWd;
	}


}
