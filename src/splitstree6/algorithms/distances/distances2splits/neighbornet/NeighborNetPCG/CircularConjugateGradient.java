package splitstree6.algorithms.distances.distances2splits.neighbornet.NeighborNetPCG;

import splitstree6.algorithms.distances.distances2splits.neighbornet.NeighborNetSplits;

import java.util.Arrays;

import static splitstree6.algorithms.distances.distances2splits.neighbornet.NeighborNetPCG.CircularSplitAlgorithms.circularAtx;
import static splitstree6.algorithms.distances.distances2splits.neighbornet.NeighborNetPCG.CircularSplitAlgorithms.circularAx;
import static splitstree6.algorithms.distances.distances2splits.neighbornet.NeighborNetPCG.VectorUtilities.maskEntries;
import static splitstree6.algorithms.distances.distances2splits.neighbornet.NeighborNetPCG.VectorUtilities.normSquared;


public class CircularConjugateGradient {

    /**
     * Conjugate gradient algorithm solving A^tA x = Atd (where b = AtWd)
     * such that all x[i][j] for which active[i][j] = true are set to zero.
     * We assume that x[i][j] is zero for all active i,j, and use the given
     * values for x as our starting vector.
     *
     * @param nTax   the number of taxa
     * @param d      the d vector
     * @param G      the active constraints
     * @param x      the x matrix
     * @param params Parameters used in the search.
     */
    static public void circularConjugateGrads(int nTax, double[] d, boolean[] G, double[] x, NeighborNetSplits.NNLSParams params) {
        // min 1/2 ||Ax - d|| -> (A'A) x =A'd
        // WE use the CGNR algorithm on pg 236 of Saad.

        final int npairs = nTax * (nTax - 1) / 2;
        double[] r = new double[npairs + 1];
        double[] z = new double[npairs + 1];
        double[] p = new double[npairs + 1];
        double[] w = new double[npairs + 1];

        double tol = params.pcgTol;

        int k = 0;
        Arrays.fill(x, 0.0);  //Initialise at 0.
        //TODO explore warm starts?
        System.arraycopy(d, 1, r, 1, npairs);
        circularAtx(nTax, r, z);
        maskEntries(z, G);
        System.arraycopy(z, 1, p, 1, npairs);

        double ztz, ztz_new, alpha, beta;
        ztz = normSquared(z);
        while (k < params.maxPCGIterations && ztz > tol * tol) {
            /* Check residual is calculated correctly. ***
            double[] resvec = new double[npairs+1];
            circularAx(nTax,x,resvec);
            for(int i=1;i<=npairs;i++)
                resvec[i] = d[i] - resvec[i];
            double res2 = normSquared(resvec); //Should be the same as res
            */

            k = k + 1;
            circularAx(nTax, p, w);
            ztz = normSquared(z);
            alpha = ztz / normSquared(w);
            for (int i = 1; i <= npairs; i++) {
                x[i] = x[i] + alpha * p[i];
                r[i] = r[i] - alpha * w[i];
            }
            circularAtx(nTax, r, z);
            maskEntries(z, G);
            ztz_new = normSquared(z);
            beta = ztz_new / ztz; //TODO this calculation can be avoided.
            for (int i = 1; i <= npairs; i++)
                p[i] = z[i] + beta * p[i];
            ztz = ztz_new;
        }
        if (k >= params.maxPCGIterations)
            throw new RuntimeException("Maximum number of iterations exceeded in circularConjugateGradient");


        //Overwrite z[G] with the gradient. //TODO try using gradient from CG.
        circularAx(nTax, x, w);
        for (int i = 1; i <= npairs; i++)
            w[i] -= d[i];
        circularAtx(nTax, w, z);
        for (int i = 1; i <= npairs; i++)
            if (G[i])
                x[i] = z[i];

    }


}
