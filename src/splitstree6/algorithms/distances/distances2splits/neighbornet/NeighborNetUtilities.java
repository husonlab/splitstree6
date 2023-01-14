package splitstree6.algorithms.distances.distances2splits.neighbornet;

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
    static public void calcAinvx(double[][] x, double[][] y) {
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
            for (var j = 1; j <= n; j++)
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
                if (x[i][j] > 0.0 || grad[i][j] < 0.0)
                    pg += grad[i][j]*grad[i][j];
            }
        }
        return pg;
    }

}
