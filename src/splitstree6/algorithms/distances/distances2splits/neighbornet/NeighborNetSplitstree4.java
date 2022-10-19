package splitstree6.algorithms.distances.distances2splits.neighbornet;

import jloda.util.CanceledException;
import jloda.util.progress.ProgressListener;

import java.io.PrintWriter;
import java.util.Arrays;

import static java.lang.Math.sqrt;
import static splitstree6.algorithms.distances.distances2splits.neighbornet.NeighborNetSplitWeights.NNLSParams;


//TODO: There is something odd which is allowing entries in old_x to be positive even though active is true.
//Need to figure out how to fix this.


public class NeighborNetSplitstree4 {

    private static final double CG_EPSILON = 0.0001;

    /**
     * Compute split weights using the same active set algorithm used in Splitstree4.
     *
     * @param xArray  square array, overwritten with split weights
     * @param distances  square array of distances
     * @param log  PrintWriter - if non-null then matlab code for convergence plots is output
     * @param progress  Progress listener - graphical indication to user that something is happening and permits cancellation
     *                  TODO: Need to give some progress information if possible.
     * @throws CanceledException   Thrown if user presses cancel in the progress box.
     */

    static public void activeSetST4(double[][] xArray, double[][] distances, PrintWriter log, NNLSParams nnlsParams, ProgressListener progress) throws CanceledException {

        int ntax = distances.length-1;
        int npairs = (ntax * (ntax - 1)) / 2;


        //Copy distance array into a 1dim vector
        double[] d = new double[npairs];
        convertArray2Vec(distances,d);

        double[] x = new double[npairs];
        runUnconstrained(ntax, d, x);
        boolean all_positive = true;
        for (int k = 0; k < npairs && all_positive; k++) {
            if (x[k] < 0) {
                all_positive = false;
                x[k] = 0;
            }
        }

        if (all_positive) {
            convertLegacyVec2Array(x,xArray);
            return;
        }




        /* Allocate memory for the "utility" vectors */
        double[] r = new double[npairs];
        double[] w = new double[npairs];
        double[] p = new double[npairs];
        double[] y = new double[npairs];
        double[] old_x = new double[npairs];


        Arrays.fill(old_x, 1.0);

        double deltax; // distance between current x and previous

        /* Initialise active - originally no variables are active (held to 0.0) */
        boolean[] active = new boolean[npairs];
        Arrays.fill(active, false);

        /* Allocate and compute Atd */
        double[] Atd = new double[npairs];
        calculateAtx(ntax, d, Atd);

        long startTime =  System.currentTimeMillis();
        if (log!=null) {
            log.println("% Active Set ST4");
            log.println("% \t Fraction to collapse = "+nnlsParams.fractionNegativeToCollapse);
            log.println("% \t Max CG iterations = "+npairs);
            log.println("% Convergence for CG is ||A'(Ax_{active}-d)|| < "+CG_EPSILON * sqrt(sumSquares(Atd)));
            log.println("% Outer convergence condition grad > -0.0001 ");
            log.println("% Using insertion heuristic = "+nnlsParams.useInsertionAlgorithm);
            log.println("% time \t ||res|| \t ||proj grad|| \t ||Delta x|| \t Num nonzero \t fractionToCollapse\n\n");
            log.println(nnlsParams.logArrayName +" = [");
        }


        CGparams params = new CGparams();
        params.useGradientNorm = nnlsParams.useGradientNorm;
        params.epsilon = nnlsParams.pgbound;

        boolean first_pass = true; //This is the first time through the loops.
        while (true) {
            while (true) /* Inner loop: find the next feasible optimum */ {
                if (!first_pass)  /* The first time through we use the unconstrained branch lengths */
                    circularConjugateGrads(ntax, npairs, r, w, p, y, Atd, active, x,params);
                first_pass = false;

                int[] entriesToContract = worstIndices(x, 1.0-nnlsParams.fractionNegativeToCollapse);
                if (entriesToContract != null) {
                    for (int index : entriesToContract) {
                        x[index] = 0.0;
                        active[index] = true;
                    }
                    circularConjugateGrads(ntax, npairs, r, w, p, y, Atd, active, x, params); /* Re-optimise, so that the current x is always optimal */
                }

                //Move from old_x towards the optimal x as far as possible while remaining feasible.
                int min_i = -1;
                double min_lambda = -1.0;
                for (int i = 0; i < npairs; i++) {
                    if (x[i] < 0.0) {
                        double lambda = (old_x[i]) / (old_x[i] - x[i]);
                        if ((min_i == -1) || (lambda < min_lambda)) {
                            min_i = i;
                            min_lambda = lambda;
                        }
                    }
                }

                deltax = 0.0;

                if (min_i == -1) {/* This is a feasible solution - go to the next stage to check if its also optimal */
                    if (log!=null) {
                        for(int i=0;i<npairs;i++) {
                            deltax += (old_x[i] - x[i])*(old_x[i] - x[i]);
                        }
                        deltax = sqrt(deltax);
                    }
                    break;
                }
                else {
                    for (int i = 0; i < npairs; i++) /* Move to the last feasible solution on the path from old_x to x */
                        if (!active[i]) {
                            double deltaxi= min_lambda*(old_x[i]-x[i]);
                            deltax += deltaxi*deltaxi;
                            old_x[i] = min_lambda * x[i] + (1 - min_lambda) * old_x[i];
                        }
                    deltax = sqrt(deltax);
                    active[min_i] = true; /* Add the first constraint met to the active set */
                    old_x[min_i] = 0.0; /* This fixes problems with round-off errors */
                }

                if (log!=null) {
                    //Calculate the residual and projected gradient
                    double[] res = new double[npairs];
                    double[] grad = new double[npairs];
                    calculateAb(ntax,old_x,res);
                    double fx = 0.0;
                    for(var i=0;i<npairs;i++) {
                        res[i] -= d[i];
                        fx += res[i]*res[i];
                    }
                    calculateAtx(ntax,res,grad);
                    double pgx = 0.0;
                    int count_nonzero = 0;

                    for(var i=0;i<npairs;i++) {
                        double grad_i = grad[i];
                        if (old_x[i]==0)
                            grad_i = Math.min(grad_i,0.0);
                        else
                            count_nonzero++;
                        pgx += grad_i*grad_i;
                    }
                    long timestamp = System.currentTimeMillis() - startTime;
                    String output ="\t"+timestamp+"\t"+ sqrt(fx)+"\t"+ sqrt(pgx) + "\t" + deltax + "\t"+count_nonzero+"\t"+nnlsParams.fractionNegativeToCollapse+"\t0";
                    log.println(output);
                    System.out.println(output);


                }



                progress.checkForCancel();
            }


            if (log!=null) {
                //Calculate the residual and projected gradient
                double[] res = new double[npairs];
                double[] grad = new double[npairs];
                calculateAb(ntax,x,res);
                double fx = 0.0;
                for(var i=0;i<npairs;i++) {
                    res[i] -= d[i];
                    fx += res[i]*res[i];
                }
                calculateAtx(ntax,res,grad);
                double pgx = 0.0;
                int count_nonzero = 0;
                for(var i=0;i<npairs;i++) {
                    double grad_i = grad[i];
                    if (x[i]==0)
                        grad_i = Math.min(grad_i,0.0);
                    else
                        count_nonzero++;
                    pgx += grad_i*grad_i;
                }
                long timestamp = System.currentTimeMillis() - startTime;
                String output = "\t"+timestamp+"\t"+ sqrt(fx)+"\t"+ sqrt(pgx) + "\t" + deltax + "\t"+count_nonzero +"\t"+nnlsParams.fractionNegativeToCollapse+"\t1";
                log.println(output);
                System.out.println(output);


            }




            /* Find i,j that minimizes the gradient over all i,j in the active set. Note that grad = (AtAb-Atd)  */
            calculateAb(ntax, x, y);
            calculateAtx(ntax, y, r); /* r = AtWAx */

            /* We check to see that we are at a constrained minimum.... that is that the gradient is positive for
             * all i,j in the active set.
             */
            int min_i = -1;
            double min_grad = 0.0; //Minimum value of the projected gradient
            double pgradSumSquares = 0.0; //Norm of the projected gradient.

            for (int i = 0; i < npairs; i++) {
                r[i] -= Atd[i];
                double grad_ij = r[i];
                if (active[i]) {

                    if  (grad_ij < min_grad) {
                        min_i = i;
                        min_grad = grad_ij;
                    }
                    if (grad_ij < 0.0)
                        pgradSumSquares += grad_ij*grad_ij;
                }
                else
                    pgradSumSquares+=grad_ij*grad_ij;
            }


            if (nnlsParams.useGradientNorm) {
                if ((min_i == -1) || pgradSumSquares<nnlsParams.pgbound * nnlsParams.pgbound)
                    break;
            }
            else {
                double gradbound = -.0001;
                if ((min_i == -1) || (min_grad > gradbound))
                    break; /* We have arrived at the constrained optimum */
            }
            active[min_i] = false;
            System.arraycopy(x, 0, old_x, 0, npairs);
            progress.checkForCancel();
        }


        if (log!=null)
            log.println("];");
        convertLegacyVec2Array(x,xArray);

    }

    /**
     * Copies a 2d array indexed 1..n to a 1d vector with entries 12,13,...,1n,23,..2n...(n-1)n.
     * @param x square array
     * @param xvec vector
     */
    private static void convertArray2Vec(double[][] x, double[] xvec) {
        int n=x.length-1;
        int index = 0;
        for(int i=1;i<=n;i++) {
            for(int j=i+1;j<=n;j++) {
                xvec[index] = x[i][j] = x[j][i];
                index++;
            }
        }
    }

    /**
     * Copies a 1d vector with entries 12,13,...,1n,23,..2n...(n-1)n into a 2d array indexed 1..n.
     * @param xvec vector
     * @param x square array
     */
    private static void convertLegacyVec2Array(double[] xvec, double[][] x) {
        int n=x.length-1;
        int index = 0;
        for(int i=1;i<n;i++) {
            for(int j=i+1;j<n;j++) {
                x[i+1][j+1] = x[j+1][i+1] = xvec[index];
                index++;
            }
            x[1][i+1]=x[i+1][1] = xvec[index];
            index++;
        }
    }

    /**
     * Compute the branch lengths for unconstrained least squares using
     * the formula of Chepoi and Fichet (this takes O(N^2) time only!).
     *
     * @param n the number of taxa
     * @param d the distance matrix
     * @param x the split weights
     */
    static private void runUnconstrained(int n, double[] d, double[] x) {
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
                //If  j = i + k;
                //then d[i][j] = d[i][j - 1] + d[i+1][j] - d[i+1][j - 1] - 2.0 * b[i][j - 1];
                d[index] = d[index - 1] + d[index + (n - i - 2)] - d[index + (n - i - 2) - 1] - 2.0 * b[index - 1];
                index += 1 + (n - i - 2);
            }
        }
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

    static private class CGparams {
        public boolean useGradientNorm;
        public double epsilon;
        public CGparams() {
            useGradientNorm = false;
            epsilon = 1e-7;
        }
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
     * @param b      the b matrix
     * @param active the active constraints
     * @param x      the x matrix
     */
    static private void circularConjugateGrads(int ntax, int npairs, double[] r, double[] w, double[] p, double[] y,
                                               double[] b, boolean[] active, double[] x, CGparams params) {
        int kmax = ntax * (ntax - 1) / 2; /* Maximum number of iterations of the cg algorithm (probably too many) */

        calculateAb(ntax, x, y);
        calculateAtx(ntax, y, r); /*r = AtAx */

        for (int k = 0; k < npairs; k++)
            if (!active[k])
                r[k] = b[k] - r[k];
            else
                r[k] = 0.0;

        double rho;
        double bound;
        if (!params.useGradientNorm) {
            double e_0 = CG_EPSILON * sqrt(sumSquares(b)); //e_0 = 1e-7;
            bound = e_0*e_0;
        } else {
            bound = params.epsilon * params.epsilon;
        }

        rho = sumSquares(r);
        double rho_old = 0;

        int k = 0;


        while ((rho > bound) && (k < kmax)) {
            k = k + 1;
            if (k == 1) {
                System.arraycopy(r, 0, p, 0, npairs);

            } else {
                double beta = rho / rho_old;
                for (int i = 0; i < npairs; i++)
                    p[i] = r[i] + beta * p[i];

            }

            calculateAb(ntax, p, y);
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
            rho = sumSquares(r);
        }
    }

    /**
     * Computes sum of squares of the lower triangle of the matrix x
     *
     * @param x the matrix
     * @return sum of squares of the lower triangle
     */
    static private double sumSquares(double[] x) {
        double ss = 0.0;
        double xk;
        for (double aX : x) {
            xk = aX;
            ss += xk * xk;
        }
        return ss;
    }

}