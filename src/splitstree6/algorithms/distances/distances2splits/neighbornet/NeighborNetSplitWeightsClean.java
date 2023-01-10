package splitstree6.algorithms.distances.distances2splits.neighbornet;

import jloda.util.CanceledException;
import jloda.util.progress.ProgressListener;
import splitstree6.data.parts.ASplit;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Random;

import static jloda.util.NumberUtils.max;
import static splitstree6.algorithms.distances.distances2splits.neighbornet.SquareArrays.*;
import static splitstree6.algorithms.distances.distances2splits.neighbornet.SquareArrays.sumArraySquared;

//TODO Make the arrays upper triangular

public class NeighborNetSplitWeightsClean {

    /**
     * NNLSParams
     *
     * Parameter files describing the method choice and options
     */
    public static class NNLSParams {
        public enum MethodTypes {ACTIVESET,BLOCKPIVOT,GRADPROJECTION,APGD,IPG}
        public MethodTypes method = MethodTypes.ACTIVESET;

        public double cutoff; //Only include split weights greater than this amount

        public int cgnrIterations; //Max number of iterations in CGNR
        public double cgnrTolerance; //Stopping condition for CGNR - bound on norm gradient squared.
        public double rho; //Proportion of pairs to add to active set in Active Set Method.
    };

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
        var x = new double[n + 1][n + 1]; //array of split weights
        calcAinvx(d,x); //Compute unconstrained solution
        var minVal = minArray(x);
        if (minVal < 0) {
            switch (params.method) {
                case ACTIVESET -> {

                }
            }
        }

        //Construct the corresponding set of weighted splits
        final var splitList = new ArrayList<ASplit>();
        for (var i = 1; i <= n; i++) {
            final var A = new BitSet();
            for (var j = i + 1; j <= n; j++) {
                A.set(cycle[j - 1]);
                if (x[i][j] > params.cutoff || A.cardinality() == 1 || A.cardinality() == n - 1) { // positive weight or trivial split
                    splitList.add(new ASplit(A, n, Math.max(0, x[i][j])));
                }
            }
        }
        return splitList;
    }

    /**************************************************************************
     * METHOD IMPLEMENTATIONS
     **************************************************************************/

    /**
     * cgnr
     *
     * Implementation of the CGNR algorithm in Saad, "Iterative Methods for Sparse Linear Systems", applied to the
     * problem of minimizing ||Ax - d|| such that x_{ij} = 0 for all ij in the activeSet.
     *
     * @param x             Initial value, overwritten with final value
     * @param d             square array of distances
     * @param activeSet     square array of boolean: specifying active (zero) set.
     * @param params        parameters - uses params.cgnrIterations for max number of iterations
     *                      and params.tolerance for bound on gradient at convergence
     * @return boolean  true if the method converged (didn't hit max number of iterations)
     */
    static private boolean cgnr(double[][] x, double[][] d, boolean[][] activeSet, NNLSParams params) {
        var n = x.length - 1;

        var p = new double[n + 1][n + 1];
        var r = new double[n + 1][n + 1];
        var z = new double[n + 1][n + 1];
        var w = new double[n + 1][n + 1];

        calcAx(x, r);
        for (var i = 1; i <= n; i++)
            for (var j = 1; j <= n; j++)
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
                for (var j = 1; j <= n; j++) {
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
                for (var j = 1; j <= n; j++) {
                    p[i][j] = z[i][j] + beta * p[i][j];
                }
            }
            ztz = ztz2;
            k++;
        }
        boolean converged = (k < params.cgnrIterations);
        return converged;
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
    static private void activeSetMethod(double[][] x, double[][] d, NNLSParams params, ProgressListener progress) throws CanceledException {
        var n = x.length-1;
        boolean[][] activeSet = new boolean[n+1][n+1];
        double[][] xstar = new double[n+1][n+1];
        double[][] grad = new double[n+1][n+1];
        copyArray(x,xstar);

        for (int k=1; true; k++) {
            while(true) {
                boolean converged = cgnr(xstar, d, activeSet, params);
                if (converged && (minArray(xstar) >= 0))
                    break;
                feasibleMove(xstar,x,activeSet,params);
            }
            copyArray(xstar,x);
            evalGradient(x,d,grad);
            int imin=0,jmin=0;
            var gradmin = 0.0;
            for(int i=1;i<=n;i++) {
                for(int j=i+1;j<=n;j++) {
                    if (activeSet[i][j] && grad[i][j]<gradmin) {
                        gradmin = grad[i][j];
                        imin = i; jmin=j;
                    }
                }
            }
            if (gradmin<0)
                activeSet[imin][jmin] = false;
            else
                break;
        }

    }


        /**************************************************************************
         * UTILITY ROUTINES (Consider moving)
         **************************************************************************/

    /**
     * Computes circular distances from an array of split weights.
     *
     * @param x split weights. Symmetric array. For i<j, x(i,j)  is the weight of the
     *          split {i,i+1,...,j-1} | rest.
     * @param y square array, overwritten with circular metric corresponding to these split weights.
     */
    static public void calcAx(double[][] x, double[][] y) {
        var n = x.length - 1;

        for (var i = 1; i <= (n - 1); i++) {
            var s = 0.0;
            for(var j=i+1;j<=n;j++)
                s+=x[i+1][j];
            for(var j=1;j<=i;j++)
                s+=x[j][i+1];
            y[i + 1][i] = y[i][i + 1] = s;
        }

        for (var i = 1; i <= (n - 2); i++) {
            y[i + 2][i] = y[i][i + 2] = y[i][i + 1] + y[i + 1][i + 2] - 2 * x[i + 1][i + 2];
        }

        for (var k = 3; k <= n - 1; k++) {
            for (var i = 1; i <= n - k; i++) {  //TODO. This loop can be threaded, but it is not worth it
                var j = i + k;
                y[j][i] = y[i][j] = y[i][j - 1] + y[i + 1][j] - y[i + 1][j - 1] - 2 * x[i + 1][j];
            }
        }
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

        for (var i = 1; i <= n - 1; i++) {
            var s = 0.0;
            for (var j=1;j<i;j++)
                s+=x[j][i];
            for(var j=i+1;j<=n;j++)
                s+=x[i][j];
            p[i + 1][i] = p[i][i + 1] = s;
        }

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
     * Computes A^{-1}(x).
     * <p>
     * When x is circular, result will be corresponding weights. If x is not circular, some
     * elements will be negative.
     *
     * @param x square array
     * @param y square array, overwritten by A^{-1}x.
     */
    static private void calcAinvx(double[][] x, double[][] y) {
        var n = x.length - 1;
        y[1][2] = y[2][1] = (x[1][n] + x[1][2] - x[2][n]) / 2.0;
        for (var j = 2; j <= n - 1; j++) {
            y[1][j] = y[j][1] = (x[j - 1][n] + x[1][j] - x[1][j - 1] - x[j][n]) / 2.0;
        }
        y[1][n] = y[n][1] = (x[1][n] + x[n - 1][n] - x[1][n - 1]) / 2.0;

        for (var i = 2; i <= (n - 1); i++) {
            y[i][i + 1] = (x[i - 1][i] + x[i][i + 1] - x[i - 1][i + 1]) / 2.0;
            for (var j = (i + 2); j <= n; j++)
                y[i][j] = y[j][i] = (x[i - 1][j - 1] + x[i][j] - x[i][j - 1] - x[i - 1][j]) / 2.0;
        }
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

    static private void feasibleMove(double[][] x, double[][] xstar, boolean[][] activeSet, NNLSParams params) {
        var n = xstar.length - 1;

        double[] tvec = new double[n * (n - 1) / 2];
        int countNegative = 0;

        for (int i = 1; i <= n; i++)
            for (int j = i + 1; j <= n; j++) {
                if (xstar[i][j] < 0) {
                    tvec[countNegative] = x[i][j] / (x[i][j] - xstar[i][j]);
                    countNegative++;
                }
            }

        if (countNegative == 0) {
            copyArray(x, xstar);
            return;
        }

        Arrays.sort(tvec,0,countNegative);

        double t = tvec[0];
        for(int i=1;i<=n;i++) {
            for(int j=i+1;j<=n;j++) {
                x[i][j] = (1-t)*x[i][j] + t*xstar[i][j];
            }
        }

        int numToMakeActive = Math.max(1, (int) Math.ceil(countNegative * params.rho));
        double cutoff = tvec[numToMakeActive - 1];

        for (int i = 1; i <= n; i++) {
            for (int j = i + 1; j <= n; j++) {
                if (xstar[i][j] < 0 && x[i][j] <= (x[i][j] - xstar[i][j]) * cutoff) {
                    activeSet[i][j] = activeSet[j][i] = true;
                    numToMakeActive--;
                    if (numToMakeActive == 0)
                        return;
                }

            }
        }
    }


    /**************************************************************************
     * TEST CODE
     **************************************************************************/

    public static void testSplitWeightCode(String[] args) {
        testCGNR();
    }

    private static void testCGNR() {
        //Test CGNR
        int n=20;
        double p=0.2;
        boolean[][] active = new boolean[n+1][n+1];
        double[][] x = new double[n+1][n+1];
        double[][] x2 = new double[n+1][n+1];

        double[][] y = new double[n+1][n+1];

        //Generate a random active set and corresponding split weight vector
        for (int i=1;i<=n;i++) {
            for (int j=i+1;j<=n;j++) {
                if (Math.random()<p) {
                    active[i][j] = active[j][i] = false;
                    x[i][j] = x[j][i] = Math.random();
                } else {
                    active[i][j] = active[j][i] = true;
                }
            }
        }
        calcAx(x,y);

        //Call CGNR
        NNLSParams params = new NNLSParams();
        params.cgnrIterations = n*n;
        params.cgnrTolerance = 1e-8;
        boolean converged = cgnr(x2,y,active,params);

        //Compute and print results
        System.err.println("Tested CGNR");
        double[][] grad= new double[n+1][n+1];
        evalGradient(x2,y,grad);

        double norm2 = 0.0;
        double grad2 = 0.0;
        for(int i=1;i<=n;i++)
            for(int j=i+1;j<=n;j++) {
                norm2 += (x[i][j] - x2[i][j]) * (x[i][j] - x2[i][j]);
                grad2 += grad[i][j]*grad[i][j];
            }
        System.err.println("Converged = "+converged);
        System.err.println("Diff squared= "+ norm2);
        System.err.println("Grad squared= "+ grad);
    }
}
