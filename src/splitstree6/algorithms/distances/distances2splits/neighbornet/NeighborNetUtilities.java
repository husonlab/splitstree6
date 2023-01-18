package splitstree6.algorithms.distances.distances2splits.neighbornet;

import static java.lang.Math.abs;
import static java.lang.Math.min;

public class NeighborNetUtilities {

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
            y[i][i + 1] = s;
        }

        for (var i = 1; i <= (n - 2); i++) {
            y[i + 2][i] = y[i][i + 2] = y[i][i + 1] + y[i + 1][i + 2] - 2 * x[i + 1][i + 2];
        }

        for (var k = 3; k <= n - 1; k++) {
            for (var i = 1; i <= n - k; i++) {  //TODO. This loop can be threaded, but it is not worth it
                var j = i + k;
                y[i][j] = y[i][j - 1] + y[i + 1][j] - y[i + 1][j - 1] - 2 * x[i + 1][j];
            }
        }
    }

    /**
     * Compute Atx, when x and the result are represented as square arrays
     *
     * @param x square array
     * @param y square array. Overwritten with result
     */
    static public void calcAtx(double[][] x, double[][] y) {
        var n = x.length - 1;
        //double[][] y = new double[n+1][n+1];

        for (var i = 1; i <= n - 1; i++) {
            var s = 0.0;
            for (var j=1;j<i;j++)
                s+=x[j][i];
            for(var j=i+1;j<=n;j++)
                s+=x[i][j];
            y[i][i + 1] = s;
        }

        for (var i = 1; i <= n - 2; i++) {  //TODO This can be threaded, but is not worth it
            y[i][i + 2] = y[i][i + 1] + y[i + 1][i + 2] - 2 * x[i][i + 1];
        }

        for (var k = 3; k <= n - 1; k++) {
            for (var i = 1; i <= n - k; i++) { //TODO. This inner loop can be threaded, but is not worth it
                y[i][i + k] = y[i][i + k - 1] + y[i + 1][i + k] - y[i + 1][i + k - 1] - 2 * x[i][i + k - 1];
            }
        }
    }

    /**
     * calcAinv_y
     * <p>
     * Computes A^{-1}(y).
     * <p>
     * When y is circular, result will be corresponding weights. If y is not circular, some
     * elements will be negative.
     *
     * @param y square array
     * @param x square array, overwritten by A^{-1}y.
     */
    static public void calcAinv_y(double[][] y, double[][] x) {
        var n = y.length - 1;
        x[1][2] = (y[1][n] + y[1][2] - y[2][n]) / 2.0;
        for (var j = 2; j <= n - 1; j++) {
            x[1][j] = (y[j - 1][n] + y[1][j] - y[1][j - 1] - y[j][n]) / 2.0;
        }
        x[1][n] =  (y[1][n] + y[n - 1][n] - y[1][n - 1]) / 2.0;

        for (var i = 2; i <= (n - 1); i++) {
            x[i][i + 1] = (y[i - 1][i] + y[i][i + 1] - y[i - 1][i + 1]) / 2.0;
            for (var j = (i + 2); j <= n; j++)
                x[i][j] =  (y[i - 1][j - 1] + y[i][j] - y[i][j - 1] - y[i - 1][j]) / 2.0;
        }
    }


    /**
     * cardinality
     *
     * Returns number of entries in lower triangular of matrix which are set true.
     * @param s boolean square matrix
     * @return number of entries
     */
    static public int cardinality(boolean[][] s) {
        int n=s.length-1;
        int count = 0;
        for(int i=1;i<=n;i++)
            for(int j=i+1;j<=n;j++)
                if (s[i][j])
                    count++;
        return count;
    }

    /**
     * Replace X with X XOR Y. Equivalently, swap entries of X for which Y is true.
     * @param X boolean square array
     * @param Y  boolean square array
     */
    static public void xor(boolean[][] X, boolean[][] Y) {
        int n = X.length-1;
        for(int i=1;i<=n;i++)
            for(int j=i+1;j<=n;j++)
                X[i][j] = (X[i][j]^Y[i][j]);
    }


    /**
     * Compute the gradient at x of 1/2 ||Ax - d||
     *
     * @param x        square array
     * @param d        square array
     * @param gradient square array, overwritten by the gradient.
     */
    static public void evalGradient(double[][] x, double[][] d, double[][] gradient) {
        var n = x.length - 1;
        var res = new double[n + 1][n + 1];
        calcAx(x, res);
        for (var i = 1; i <= n; i++)
            for (var j = i+1; j <= n; j++) 
                res[i][j] -= d[i][j];
        calcAtx(res, gradient);
    }

    /**
     * Computes the square of the norm of the projected gradient. This is inefficient and should
     * probably only be used for development and debugging.
     * @param x  square array
     * @param d square array
     * @return square of the norm of the projected gradient
     */
    static public double evalProjectedGradientSquared(double[][] x, double[][] d) {
        int n=x.length-1;
        double[][] grad = new double[n+1][n+1];
        evalGradient(x,d,grad);
        double pg = 0.0;
        for(int i=1;i<=n;i++) {
            for(int j=i+1;j<=n;j++) {
                double grad_ij = grad[i][j];
                if (x[i][j] > 0.0 || grad_ij < 0.0)
                    pg += grad_ij*grad_ij;
            }
        }
        return pg;
    }


    /**
     * Fill a boolean array indicating the elements of an array which are not positive
     * @param x square array of doubles. Negative entries replaced by zeros.
     * @param A overwrites square array of boolean, with A[i][j] = (x[i][j] <=0);
     */
    static void getActiveEntries(double[][] x, boolean[][] A) {
        int n= x.length-1;
        for(int i=1;i<=n;i++)
            for(int j=i+1;j<=n;j++) {
                if (x[i][j] <= 0.0) {
                    if (A!=null)
                        A[i][j] =  true;
                    x[i][j] = 0.0;
                } else if (A!=null)
                    A[i][j] =  false;
            }
    }

    /**
     * Replaces negative entries of a square matrix with zeros
     * @param x square array
     */
    static void zeroNegativeEntries(double[][] x) {
        getActiveEntries(x, null);
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
            for(int j=i+1;j<=n;j++)
                if (A[i][j])
                    r[i][j]=0;
    }


    /**
     * Replace values in v with absolute value less than val with zeros.
     * @param v  square array, overwritten
     * @param val   cutoff value.
     */
    static void threshold(double[][] v, double val) {
        int n = v.length-1;
        for(var i=1;i<=n;i++)
            for(var j=i+1;j<=n;j++)
                if (abs(v[i][j])<val)
                    v[i][j] = 0.0;
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
            for(int j=i+1;j<=n;j++)
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
            System.arraycopy(from[i],i+1,to[i],i+1,n-i);
        }
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
     * Return the sum of squared differences between two arrays of the same size (using
     * lower triangular parts only)
     * @param x square array
     * @param y  square array
     * @return  sum_{i<j} (x[i][j] - y[i][j])^2
     */
    static public double diff(double[][] x, double[][] y) {
        int n=x.length-1;
        double df = 0.0;
        for(int i=1;i<=n;i++)
            for(int j=i+1;j<=n;j++) {
                double res_ij = (x[i][j] - y[i][j]);
                df +=res_ij*res_ij;
            }
        return df;
    }

    
}
