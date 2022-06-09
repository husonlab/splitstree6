package splitstree6.algorithms.distances.distances2splits.neighbornet;

import java.util.Arrays;

import static java.lang.Math.*;

/** A collection of utility routines for handling 2D symmetric arrays.
 * In all of these routines, rows and columns with index 0 are ignored (for consistency with Matlab)
 * **/

public class SquareArrays {

    /**
     * Construct an array of all ones.
     * @param n size
     * @return square array of ones.
     */
    static public double[][] ones(int n) {
        double[][] X = new double[n+1][n+1];
        for(int i=1;i<=n;i++)
            Arrays.fill(X[i],1,n+1,1.0);
        return X;
    }

    /**
     * Set a subset of elements of an array to zero
     * @param r  square array of double  (ignore 0 indexed rows and columns)
     * @param A   square array of boolean (ditto)
     *
     * set r[i][j] = 0 whenever A[i][j] is true.
     */
    static public void maskElements(double[][] r, boolean[][] A) {
        int n=r.length-1;
        for(int i=1;i<=n;i++)
            for(int j=1;j<=n;j++)
                if (A[i][j])
                    r[i][j]=0;
    }

    /**
     * Checks if A is empty (all entries set to false).
     * @param A  square array of boolean
     * @return  true if A is empty, false otherwise
     */
    static public boolean isEmpty(boolean[][] A) {
        int n = A.length-1;
        boolean allFalse = true;
        for(int i=1;i<=n && allFalse;i++)
            for(int j=i+1;j<=n && allFalse;j++)
                allFalse = allFalse && !A[i][j];
        return allFalse;
    }

    /**
     * Fill a boolean array indicating the elements of an array which are identically zero.
     * @param x square array of doubles
     * @return square array of boolean, with A[i][j] = (x[i][j]==0);
     */
    static void getZeroElements(double[][] x, boolean[][] A) {
        int n= x.length-1;
        for(int i=1;i<=n;i++)
            for(int j=1;j<=i;j++)
                A[i][j] = A[j][i] = (x[i][j] == 0);
    }

    /**
     * Returns a boolean array indicating the elements of an array which are identically zero.
     * @param x square array of doubles
     * @return square array of boolean, with A[i][j] = (x[i][j]==0);
     */
    static boolean[][] getZeroElements(double[][] x) {
        int n= x.length-1;
        boolean[][] A = new boolean[n+1][n+1];
        getZeroElements(x,A);
        return A;
    }

    /**
     * Make all negative entries zero.
     * @param x square array
     */
    static void makeNegElementsZero(double[][] x) {
        int n = x.length - 1;
        for (int i = 1; i <= n; i++)
            for (int j = i + 1; j <= n; j++)
                x[i][j] = x[j][i] = Math.max(x[i][j], 0);
    }

    /**
     * Sum the squares of entries of an array
     * @param x Square array of doubles
     * @return double sum of the squares of entries in x
     */
    static public double sumArraySquared(double[][] x) {
        double total = 0.0;
        int n=x.length-1;
        double si;
        for(int i=1;i<=n;i++) {
            si=0.0;
            for (int j = 1; j <= n; j++) {
                double x_ij = x[i][j];
                si += x_ij * x_ij;
            }
            total+=si;
        }
        return total;
    }

    /**
     * Find the minimum value of an entry in a square array
     * @param x square array of double
     * @return  double min_ij  x_ij
     */
    static public double minArray(double[][] x) {
        double minx = 0.0;
        int n=x.length-1;
        for(int i=1;i<=n;i++)
            for(int j=1;j<=n;j++)
                minx = min(minx,x[i][j]);
        return minx;
    }




    /**
     * Copy elements from one square array to another of the same size.
     * @param from square array
     * @param to square array (assumed to be allocated already)
     */
    static public void copyArray(double[][] from, double[][] to) {
        int n=from.length-1;
        for(int i=1;i<=n;i++) {
            System.arraycopy(from[i],1,to[i],1,n);
        }
    }

    /**
     * Return the sum of squares of entries in the matrix
     * @param x square array of doubles
     * @return double, sum of squares
     */
    static public double sumSquares(double[][] x) {
        int n = x.length-1;
        double si = 0.0, total=0.0;
        for(int i=1;i<=n;i++) {
            si = 0.0;
            for(int j=i+1;j<=n;j++) {
                si += x[i][j]*x[i][j];
            }
            total+=si;
        }
        return 2.0*total;
    }




}
