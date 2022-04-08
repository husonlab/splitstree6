package splitstree6.algorithms.distances.distances2splits.neighbornet.NeighborNetPCG;

import splitstree6.algorithms.distances.distances2splits.neighbornet.NeighborNetSplits;

import java.io.IOException;
import java.util.Arrays;

import static splitstree6.algorithms.distances.distances2splits.neighbornet.NeighborNetPCG.CircularSplitAlgorithms.circularAtx;
import static splitstree6.algorithms.distances.distances2splits.neighbornet.NeighborNetPCG.CircularSplitAlgorithms.circularAx;
import static splitstree6.algorithms.distances.distances2splits.neighbornet.NeighborNetPCG.VectorUtilities.*;


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

        long startTime = System.currentTimeMillis();
        if (params.verboseOutput) {
            int nG = count(G);

            try {
                params.writer.write("\t\t %Entering circularConjugateGradient. |G| = "+nG+"\n");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }




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

        double[] residuals= new double[1];
        if (params.printCGconvergence) {
            residuals = new double[params.maxPCGIterations];
        }


        while (k < params.maxPCGIterations && ztz > tol * tol) {
            /* Check residual is calculated correctly. ***
            double[] resvec = new double[npairs+1];
            circularAx(nTax,x,resvec);
            for(int i=1;i<=npairs;i++)
                resvec[i] = d[i] - resvec[i];
            double res2 = normSquared(resvec); //Should be the same as res
            */

            if (params.printCGconvergence) {
                residuals[k] = Math.sqrt(ztz);
            }

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

        if (params.printCGconvergence) {
            try {
                params.writer.write("res = [");
                for(int i=0;i<k;i++) {
                    params.writer.write("\t\t\t"+i+"\t"+residuals[i]+";\n");
                }
                params.writer.write("]; hold on; plot(res(:,1),log(res(:,2)),'LineWidth',8,'DisplayName','"+count(G)+"'); hold off \n\n");
                params.writer.write("numIters(rep,:)=["+count(G)+" size(res,1)]; rep=rep+1;\n");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }



        if (k >= params.maxPCGIterations)
            throw new RuntimeException("Maximum number of iterations exceeded in circularConjugateGradient");

        if (params.verboseOutput) {
            try {
                params.writer.write("\t\t%Exiting circularConjugateGradient. Number of iterations was "+k+"\tTime = "+(System.currentTimeMillis()-startTime)+"\t");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

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
