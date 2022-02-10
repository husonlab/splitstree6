package splitstree6.algorithms.distances.distances2splits.neighbornet.NeighborNetPCG;

import java.util.Arrays;

//TODO: Make all of these with index starting at one.

/**
 * A few utility classes for handing arrays of doubles. NOTE: in all of these, indexing starts at 1.
 */
public class VectorUtilities {
    /**
     * Add two arrays of doubles
     *
     * @param x array of doubles
     * @param y array of doubles with the same length as x
     * @return x+y
     */
    static public double[] add(double[] x, double[] y) {
        assert x.length == y.length : "Adding arrays with different lengths";
        double[] z = new double[x.length];
        for (int i = 1; i < x.length; i++)
            z[i] = x[i] + y[i];
        return z;
    }

    /**
     * Subtract one vector from another
     *
     * @param x array
     * @param y array of the same length as x
     * @return array with x-y.
     */
    static public double[] minus(double[] x, double[] y) {
        assert x.length == y.length : "Computing difference between vectors of different sizes";
        double[] z = new double[x.length];
        for (int i = 1; i < x.length; i++)
            z[i] = x[i] - y[i];
        return z;
    }

    static public double dist(double[] x, double[] y) {
        double ss = 0.0;
        for (int i = 1; i < x.length; i++)
            ss += (x[i] - y[i]) * (x[i] - y[i]);
        return Math.sqrt(ss);
    }

    static public double normSquared(double[] x) {
        double ss = 0.0;
        for (int i = 1; i < x.length; i++)
            ss += x[i] * x[i];
        return ss;
    }

    /**
     * Multiply every entry of x by lambda
     *
     * @param x      vector
     * @param lambda scalar
     *               overwrites x with lambda*x
     */
    static public void scale(double[] x, double lambda) {
        for (int i = 1; i < x.length; i++)
            x[i] *= lambda;
    }

    /**
     * Set elements of x with |x[i]| < val to zero.
     *
     * @param val double
     * @param x   vector
     *            sets every element of x with |x[i]|<val to value and overwrites x with the result
     */
    static public void cutoff(double[] x, double val) {
        for (int i = 1; i < x.length; i++)
            if (Math.abs(x[i]) < val)
                x[i] = 0.0;
    }

    /**
     * Scans x and overwrites negIndices with a boolean vector where 'true' indicates the entry of x is negative.
     *
     * @param x          vector
     * @param negIndices vector of boolean (assumed to be of same length as x)
     * @return int number of negative entries in x. Overwrites negIndices with true/false depending on whether the entry
     * is negative (true) or non-negative (false)
     */
    static public int findNegative(double[] x, boolean[] negIndices) {
        int numNeg = 0;
        Arrays.fill(negIndices, false);
        for (int i = 1; i < x.length; i++)
            if (x[i] < 0.0) {
                numNeg++;
                negIndices[i] = true;
            }
        return numNeg;
    }

    /**
     * Check if all entries are positive
     *
     * @param x vector
     * @return true if all entries of x are non-negative, otherwise false
     */
    static public boolean allPositive(double[] x) {
        for (int i = 1; i < x.length; i++) {
            if (x[i] < 0)
                return false;
        }
        return true;
    }


    /**
     * Counts number of true entries in x
     *
     * @param x boolean vector
     * @return Number of true entries
     */
    static public int count(boolean[] x) {
        int n = 0;
        for (int i = 1; i < x.length; i++) {
            if (x[i])
                n++;
        }
        return n;
    }

    /**
     * Zero elements of x for which the mask is true.
     *
     * @param x    vector of doubles
     * @param mask boolean vector with the same size as x
     *             overwrites x with the same array except masked elements are set to false.
     */
    static public void maskEntries(double[] x, boolean[] mask) {
        for (int i = 1; i < x.length; i++)
            if (mask[i])
                x[i] = 0.0;
    }

    /**
     * Finds index of minimum entry of x, restricted to those indices i for which mask[i] is true.
     *
     * @param x    vector
     * @param mask boolean vector
     * @return Returns index of minimum masked entry, or 0 if there are no unmasked values.
     */
    static public int indexOfMin(double[] x, boolean[] mask) {
        int min_i = 0;
        double min_val = Double.MAX_VALUE;
        for (int i = 1; i < x.length; i++) {
            if (mask[i] && x[i] < min_val) {
                min_i = i;
                min_val = x[i];
            }
        }
        return min_i;
    }

    /**
     * Produces dot product (x^Ty) of two vectors
     *
     * @param x vector
     * @param y vector with the same length as x
     * @return dot product
     */
    static public double dotProduct(double[] x, double[] y) {
        double xty = 0.0;
        for (int i = 1; i < x.length; i++)
            xty += x[i] * y[i];
        return xty;
    }
}
