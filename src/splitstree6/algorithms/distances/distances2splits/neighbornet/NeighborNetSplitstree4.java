package splitstree6.algorithms.distances.distances2splits.neighbornet;

import jloda.util.CanceledException;
import jloda.util.progress.ProgressListener;
import java.util.Arrays;

public class NeighborNetSplitstree4 {


    static public void activeSetST4(double[][] xArray, double[][] distances, ProgressListener progress) throws CanceledException {

        int ntax = distances.length;
        int npairs = (ntax * (ntax - 1)) / 2;

        //Copy distance array into a 1dim vector
        double[] d = new double[npairs];
        convertArray2Vec(distances,d);

        double[] x = new double[npairs];
        runUnconstrained(ntax, d, x);
        boolean all_positive = true;
        for (int k = 0; k < npairs && all_positive; k++)
            all_positive =  (x[k] >= 0.0);

        if (all_positive) {
            convertVec2Array(x,xArray);
            return;
        }

        /* Allocate memory for the "utility" vectors */
        double[] r = new double[npairs];
        double[] w = new double[npairs];
        double[] p = new double[npairs];
        double[] y = new double[npairs];
        double[] old_x = new double[npairs];
        Arrays.fill(old_x, 1.0);

        /* Initialise active - originally no variables are active (held to 0.0) */
        boolean[] active = new boolean[npairs];
        Arrays.fill(active, false);

        /* Allocate and compute Atd */
        double[] Atd = new double[npairs];

        calculateAtx(ntax, y, Atd);

        boolean first_pass = true; //This is the first time through the loops.
        while (true) {
            while (true) /* Inner loop: find the next feasible optimum */ {
                if (!first_pass)  /* The first time through we use the unconstrained branch lengths */
                    circularConjugateGrads(ntax, npairs, r, w, p, y, Atd, active, x);
                first_pass = false;

                int[] entriesToContract = worstIndices(x, 0.6);
                if (entriesToContract != null) {
                    for (int index : entriesToContract) {
                        x[index] = 0.0;
                        active[index] = true;
                    }
                    circularConjugateGrads(ntax, npairs, r, w, p, y, Atd, active, x); /* Re-optimise, so that the current x is always optimal */
                }

                //Move from old_x towards the optimal x as far as possible while remaining feasible.
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
                else {
                    for (int i = 0; i < npairs; i++) /* Move to the last feasible solution on the path from old_x to x */
                        if (!active[i])
                            old_x[i] += min_xi * (x[i] - old_x[i]);
                    active[min_i] = true; /* Add the first constraint met to the active set */
                    x[min_i] = 0.0; /* This fixes problems with round-off errors */
                }
                progress.checkForCancel();
            }

            /* Find i,j that minimizes the gradient over all i,j in the active set. Note that grad = (AtAb-Atd)  */
            calculateAb(ntax, x, y);
            calculateAtx(ntax, y, r); /* r = AtWAx */

            /* We check to see that we are at a constrained minimum.... that is that the gradient is positive for
             * all i,j in the active set.
             */
            int min_i = -1;
            double min_grad = 1.0;
            for (int i = 0; i < npairs; i++) {
                r[i] -= Atd[i];
                //r[i] *= 2.0;
                if (active[i]) {
                    double grad_ij = r[i];
                    if ((min_i == -1) || (grad_ij < min_grad)) {
                        min_i = i;
                        min_grad = grad_ij;
                    }
                }
            }

            if ((min_i == -1) || (min_grad > -0.0001))
                break; /* We have arrived at the constrained optimum */
            active[min_i] = false;
            progress.checkForCancel();
        }
        convertVec2Array(x,xArray);

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
    private static void convertVec2Array(double[] xvec, double[][] x) {
        int n=x.length-1;
        int index = 0;
        for(int i=1;i<=n;i++) {
            for(int j=i+1;j<=n;j++) {
                x[i][j] = x[j][i] = xvec[index];
                index++;
            }
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
                //int j = i + k;
                //d[i][j] = d[i][j - 1] + d[i+1][j] - d[i+1][j - 1] - 2.0 * b[i][j - 1];
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
                                               double[] b, boolean[] active, double[] x) {
        int kmax = ntax * (ntax - 1) / 2; /* Maximum number of iterations of the cg algorithm (probably too many) */

        calculateAb(ntax, x, y);
        calculateAtx(ntax, y, r); /*r = AtAx */

        for (int k = 0; k < npairs; k++)
            if (!active[k])
                r[k] = b[k] - r[k];
            else
                r[k] = 0.0;

        double rho = norm(r);
        double rho_old = 0;

        double e_0 = 1e-7;
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

}