package splitstree6.algorithms.distances.distances2splits.neighbornet;

import jloda.util.CanceledException;
import jloda.util.progress.ProgressListener;
import splitstree6.algorithms.distances.distances2splits.neighbornet.NeighborNetPCG.CircularConjugateGradient;
import splitstree6.algorithms.distances.distances2splits.neighbornet.NeighborNetPCG.CircularSplitAlgorithms;
import splitstree6.algorithms.distances.distances2splits.neighbornet.NeighborNetPCG.DualPCG;
import splitstree6.algorithms.distances.distances2splits.neighbornet.NeighborNetPCG.VectorUtilities;
import splitstree6.data.parts.ASplit;

import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;

import static splitstree6.algorithms.distances.distances2splits.neighbornet.NeighborNetPCG.CircularSplitAlgorithms.*;
import static splitstree6.algorithms.distances.distances2splits.neighbornet.NeighborNetPCG.VectorUtilities.*;
import static splitstree6.algorithms.distances.distances2splits.neighbornet.SpeedKnitter.incrementalFitting;


//Things to do next
//(1) Implement warm starts
//(2) More thorough tests
//(3) Explore algorithm for more bands with A_{n-1}.


public class NeighborNetSplits {

    public static class NNLSParams {
        static public int CG = 0;
        static public int DUAL_PCG = 1;

        public int maxOuterIterations = 1000; //Maximum number of iterations for the outer algorithm
        public int maxPCGIterations = 1000; //Maximum number of iterations for the conjugate gradient algorithm
        public double pcgTol = 1e-4; //Tolerance for pcg: will stop when residual has norm less than this. default  1e-7 //TODO rename to PCG_EPSILON
        public double finalTol = 1e-4; //Tolerance for the final 'tidy up' call to least squares.
        public double vectorCutoff = 1e-5; //Cutoff - values in block pivot with value smaller than this are set to zero.
        public boolean usePreconditioner = false; //True if the conjugate gradient makes use of preconditioner (only implemented when useDual is true).
        public int preconditionerBands = 10; //Number of bands used when computing Y,Z submatrices in the preconditioner.
        // Note that alot of the calculations for preconditioning are done even if this is false, so use this flag only to assess #iterations.
        public boolean useBlockPivot = true; //Use the block pivot algorithm rather than least squares.
        public boolean initAllActive; //Start active set / block pivot iterations with x = 0, otherwise x = A\d.
        public boolean useWarmStart = false; //Initialise the CG iterations with the current best guess.
        //public double proportionToZero = 0.4; //The proportion of indices to keep when doing a partial initialization.
        //public double pgBound = 0.0; //Terminate if the l_infinity of the projected gradient is smaller that this.
        public double propKept = 0.6; //Proportion of negative splits to keep in the first iteration of the active set method.
        public int leastSquaresAlgorithm = CG;

        public boolean printCGconvergence = true; //For debugging and profiling - prints the appropriate residual at each step of CG/PCG
        public boolean printNNLSconvergence = true; //For debugging and profiling - prints the residual at each step of blockPivot/ActiveSet
        public String CGconvergenceFilename = "cgConvergeTest.txt";

        public boolean verboseOutput= true;
        public String verboseFilename = "nnetConvergeData.m";
        public FileWriter writer = null;

        //Some active set method choices, here just for development.
        public double proportionInitiallyActive = 1.0;  // After finding the LS solution, this proportion of negative indices are added to the active set.

        public int moveStrategy = 0; //Trying different strategies for adding constraints.
        public double proportionConstraintsRemoved=0.0; //Which proportion of indices with negative gradients are added back to the active set.
        //If zero, then exactly one is added.



    }

    /**
     * Compute optimal weight squares under least squares for Splits compatible with a circular ordering.
     * This version carries out adaptive L1 regularisation, controlled by the parameter lambdaFraction.
     * That is, it minimizes  0.5*||Ax - d||^2_2  +  \lambda ||x||_1
     * This revised version uses a different indexing scheme for the splits and distances. In this system, pair (i,j)
     * refers to the split {i,i+1,i+2,...j-1}| ----
     *
     * @param cycle         taxon cycle, 1-based
     * @param distances     pairwise distances, 0-based
     * @param cutoff        min split weight
     * @param useBlockPivot Use the block pivoting algorithm
     * @param useDual    Use the dual least squares method algorithm for least squares
     * @param usePreconditioner Use the preconditioner with the dual problem.
     * @param progress      progress listener
     * @return weighted splits
     * @throws CanceledException
     */
    static public ArrayList<ASplit> compute(int[] cycle, double[][] distances, double cutoff, boolean useBlockPivot, boolean useDual, boolean usePreconditioner, ProgressListener progress) throws CanceledException {

        int nTax = cycle.length - 1;

        //Handle cases for n<3 directly.
        if (nTax == 1) {
            return new ArrayList<>();
        }
        if (nTax == 2) {
            final ArrayList<ASplit> splits = new ArrayList<>();
            float d_ij = (float) distances[cycle[1] - 1][cycle[2] - 1];
            if (d_ij > 0.0) {
                final BitSet A = new BitSet();
                A.set(cycle[1]);
                splits.add(new ASplit(A, 2, d_ij));
            }
            return splits;
        }

        if (false) {
            double[][] d = new double[nTax+1][nTax+1];
            for(int i=1;i<=nTax;i++)
                for (int j=i+1;j<=nTax;j++)
                    d[i][j] = d[j][i] = distances[cycle[i]-1][cycle[j]-1];

            double[][] x = incrementalFitting(d,1e-5);

//Copy back to the splits
            final ArrayList<ASplit> splitList = new ArrayList<>();

            for (int i = 1; i <= nTax; i++) {
                final BitSet A = new BitSet();
                for (int j = i + 1; j <= nTax; j++) {
                    A.set(cycle[j - 1]);
                    if (x[i][j] > cutoff)
                        splitList.add(new ASplit(A, nTax, (float) (x[i][j])));

                }
            }
            return splitList;

        }



        final int npairs = (nTax * (nTax - 1)) / 2;
        //Set up the distance vector.
        double[] d = new double[npairs + 1];
        int index = 1;
        for (int i = 1; i <= nTax; i++) {
            for (int j = i + 1; j <= nTax; j++) {
                d[index++] = distances[cycle[i] - 1][cycle[j] - 1];
            }
        }








        //Call the appropriate least squares routine
        NNLSParams params = new NNLSParams();
        if (useDual)
            params.leastSquaresAlgorithm = params.DUAL_PCG;
        else
            params.leastSquaresAlgorithm = params.CG;
        params.maxPCGIterations = Math.max(1000, npairs / 10);
        params.usePreconditioner = usePreconditioner;
        params.useBlockPivot = useBlockPivot;

        if (params.verboseOutput) {
            try {
                params.writer = new FileWriter(params.verboseFilename,false);
                params.writer.write("figure()\n\nrep=1;\nnumIters=zeros(1000,2);\n");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        double[] x = new double[npairs + 1];
        if (useBlockPivot)
            circularBlockPivot(nTax, d, x, progress, params);
        else
            circularActiveSet(nTax, d, x, progress, params);

        //Copy back to the splits
        final ArrayList<ASplit> splitList = new ArrayList<>();

        double xsum = 0.0;

        index = 1;
        for (int i = 1; i <= nTax; i++) {
            final BitSet A = new BitSet();
            for (int j = i + 1; j <= nTax; j++) {
                A.set(cycle[j - 1]);
                if (x[index] > cutoff)
                    splitList.add(new ASplit(A, nTax, (float) (x[index])));
                xsum += x[index];
                index++;

            }
        }

        if (params.verboseOutput) {
            try {
                params.writer.write("numIters = numIters(1:(rep-1),:)\n");
                params.writer.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        return splitList;

    }

    /**
     * Uses an active set method with the conjugate gradient algorithm to find x that minimises
     * <p>
     * 0.5 * ||Ax - d||    s.t. x \geq 0
     * <p>
     * Here, A is the design matrix for the set of cyclic splits with ordering 1,2,...,n
     * d is the distance vector, with pairs in order (1,2),(1,3),...,(1,n),...,(n-1,n)
     * x is a vector of split weights, with pairs in same order as d. The split (i,j), for i<j, is {i,i+1,...,j-1}| rest
     * <p>
     */
    static public void circularActiveSet(int nTax, double[] d, double[] x, ProgressListener progress, NNLSParams params) {

        int npairs = nTax * (nTax - 1) / 2;
        int iter = 0; //Iteration number. This is the number of calls to least squares.

        /* First evaluate the unconstrained optima. If this is feasible then we don't have to do anything more! */
        CircularSplitAlgorithms.circularSolve(nTax, d, x);
        if (VectorUtilities.allPositive(x))
            return;




        /* Allocate memory for the "utility" vectors */
        double[] grad = new double[npairs + 1];
        double[] s = new double[npairs + 1];
        final double[] old_x = new double[npairs + 1];
        final boolean[] active = new boolean[npairs + 1];

        //The following is for debugging. It could be removed, but only after everything is working really well.
        //Columns: iterate, num active, num inactive,function val, log(residual)
        double[][] convergenceStats = new double[1][1];
        if (params.printNNLSconvergence) {
            convergenceStats = new double[params.maxOuterIterations+1][6];
        }
        if (params.verboseOutput) {
            try {
                params.writer.write("\t%Entering circularActiveSet\tntax = "+nTax+"\n");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }




        /* Typically, we need to contract a lot of the splits in the full set. For the first iteration we contact all
        but a proportion params.propKept of the splits with negative weight, chosen so that the splits with more
        negative weight are contracted preferentially.
         */
        final int[] entriesToContract = worstIndicesRevised(x, 1.0 - params.proportionInitiallyActive);
        for (int index : entriesToContract) {
            x[index] = 0.0;
            active[index] = true;
        }

        while (true) {
            while (iter<params.maxOuterIterations) /* Inner loop: find the next feasible optimum */ {
                circularLeastSquares(nTax, active, d, x, params); //x is optimal s.t. xi = 0 when active[i] true.
                iter++;

                int c=0;
                for(int i=1;i<=npairs;i++)
                    if (old_x[i]==0 && !active[i])
                        c++;
                System.err.println("c = "+c + "\n");



                if (params.printNNLSconvergence) {
                    convergenceStats[iter][0] = iter;
                    convergenceStats[iter][1] = count(active);
                    convergenceStats[iter][2] = npairs - convergenceStats[iter][1];
                    double[] cleanx = new double[npairs+1];
                    System.arraycopy(old_x,1,cleanx,1,npairs);
                    maskEntries(cleanx,active);
                    double[] debugStats = computeStats(nTax,cleanx,d);
                    for(int j = 0;j<3;j++)
                        convergenceStats[iter][3+j] = debugStats[j];
                }

                //Move old_x as far as we can towards x while remaining feasible. If x is already feasible, exit inner loop.
                if (addConstraints(x,old_x, active,params))
                    break;
            }
            //At this point, old_x is feasible, and is the best estimate so far. Check if it is optimal

            /* Compute the gradient */
            circularAx(nTax, x, s);
            for (int i = 1; i <= npairs; i++) {
                s[i] -= d[i];
            }
            circularAtx(nTax, s, grad);
            int min_i = indexOfMin(grad, active); //Get active index giving minimum gradient
            if (min_i == 0 || grad[min_i] >= 0) {
                break;
            } else if (iter>=params.maxOuterIterations) {
                System.err.println("Exceeded max number of iterations in the Active Set procedure");
                break;
            } else {
                active[min_i] = false;
            }
        }

        if (params.verboseOutput&&params.printNNLSconvergence) {
            try {
                params.writer.write("nnlsStats=[\n");
                for(int k=1;k<=iter;k++) {
                    for(int j=1;j<5;j++)
                        params.writer.write("\t"+convergenceStats[k][j]);
                    params.writer.write(";\n");
                }
                params.writer.write("];\n");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }


    }

    /**
     * Returns the array indices for the smallest propKept proportion of negative values in x.
     * In the case of ties, priority is given to the earliest entries.
     * Size of resulting array will be propKept * (number of negative entries) rounded up.
     */
    static private int[] worstIndicesRevised(double[] x, double propKept) {
        //System.err.println(Basic.toString(x, " "));
        if (propKept == 0)
            return null;

        //TODO This should be refactored. We could do this in a few lines using a TreeMap, for example.

        final int n = x.length;

        int numNeg = 0;
        for (double aX1 : x)
            if (aX1 < 0.0)
                numNeg++;

        if (numNeg == 0)
            return null;

        //Make a copy of negative values in x.
        final double[] xCopy = new double[numNeg + 1];
        int j = 1;
        for (double aX : x)
            if (aX < 0.0)
                xCopy[j++] = aX;

        //Sort the copy
        Arrays.sort(xCopy);

        //Find the cut-off value. All values greater than this should
        //be returned, as well as some (or perhaps all) of the values
        //equals to this.
        final int nkept = (int) Math.ceil(propKept * numNeg);  //Ranges from 1 to n
        final double cutoff = xCopy[nkept - 1];

        //we now fill the result vector. Values < cutoff are filled
        //in from the front. Values == cutoff are filled in the back.
        //Values filled in from the back can be overwritten by values
        //filled in from the front, but not vice versa.
        final int[] result = new int[nkept + 1];
        int front = 1;
        int back = nkept;

        for (int i = 1; i < n; i++) {
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
     * Finds the last feasible point on the line segment from old_x to x.
     * Dimensions i for which alpha[i] = true are ignore (and should be zero for both anyway)
     * @param oldx  feasible vector
     * @param x vector which may or may not be feasible
     * @param active vector indicating entries constrained to zero.
     * @return boolean, true if x is feasible. Note, will also change active.
     */
    static private boolean addConstraints(double[] x, double[] oldx, boolean[] active, NNLSParams params) {
        /*
        oldx is a
         */
        int npairs = oldx.length - 1;


        //Classical case: we add the first constraint that we come across

        double alpha = 1.0;
        int firstBoundary = 0;
        boolean isFeasible = true;

        //Find optimal value of alpha
        for (int i = 1; i <= npairs; i++) {
            if (!active[i] && x[i] < 0.0) {
                double alphai = oldx[i] / (oldx[i] - x[i]);
                if (alphai < alpha) {
                    alpha = alphai;
                    firstBoundary = i;
                    isFeasible = false;
                }
            }
        }
        if (isFeasible)
            return true;
        else {
            //move old_x to  alpha x + (1-alpha) oldx
            for (int i = 1; i <= npairs; i++) {
                if (!active[i])
                    oldx[i] = Math.max(alpha * x[i] + (1 - alpha) * oldx[i], 0);
            }
            oldx[firstBoundary] = 0.0;
            active[firstBoundary] = true;
            return false;
        }

    }


    /**
     * circularBlockPivot
     * <p>
     * Finds a vector x to minimize ||Ax - d|| where A is the circular matrix (for 1,2,...,n) , d is the vector of
     * distances and x is the non-negative split weights. We use the block pivot algorithm tsnnls combined with preconditioned
     * conjugate gradient.
     *
     * @param n        number of taxa. Assumed circular ordering is 1,2,...,n
     * @param d        Distances (as a vector with n(n-1)/2+1 entries ), in order 0,12,13,14,..,1n,23,24,....,2n,...,(n-1)n
     * @param progress Progress indicator
     * @param params   Search parameters
     * @return array of split weights in indices 1,...,n(n-1)/2
     * @throws CanceledException
     */
    static public void circularBlockPivot(int n, double[] d, double[] z, ProgressListener progress, NNLSParams params) throws CanceledException {

        if (params.verboseOutput) {
            try {
                params.writer.write("\t%Entering circularBlockPivot. ntax = "+n+"\n");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }



        //Initialise G, x & y (these are stored in the vector z)
        final int npairs = n * (n - 1) / 2;
        final boolean[] G = new boolean[npairs + 1];  //Active (zero) set
        final boolean[] infeasible = new boolean[npairs + 1]; //negative entries are 'infeasible'
        if (params.initAllActive) {
            Arrays.fill(G, true);  //Start with G = {1...npairs}, so F = \emptyset, x = 0 and y = -A'd.
            circularAtx(n, d, z);
            scale(z, -1.0);
        } else {
            Arrays.fill(G, false); //Start with G = \emptyset, so x = A^{-1} d and y = 0
            CircularSplitAlgorithms.circularSolve(n, d, z);
        }
        cutoff(z, params.vectorCutoff);  //Set small entries of x and y to zero.

        int p = 3;
        int iter = 1;
        int ninf = findNegative(z, infeasible);
        int N = npairs + 1;

        while (ninf > 0) {
            if (iter >= params.maxOuterIterations) {
                System.err.println("WARNING: Max Iterations exceeded in Block Pivot Algorithm");
                break;
            }
            ninf = findNegative(z, infeasible);
            if (ninf < N) {
                N = ninf;
                p = 3;
                for (int i = 1; i <= npairs; i++) {
                    G[i] = G[i] ^ infeasible[i];   //G <- G XOR Infeasible
                }
            } else {
                if (p > 0) {
                    p--;
                    for (int i = 1; i <= npairs; i++) {
                        // F[i] = F[i] ^ infeasible[i]; //XOR
                        G[i] = G[i] ^ infeasible[i];
                    }
                } else {
                    for (int i = npairs; i >= 1; i--)
                        if (infeasible[i]) {
                            G[i] = !G[i];
                            break;
                        }
                }
            }

            //z[~G] = x[~G] and z[G] = y[G]  where x mininizes ||Ax - d||  such that x[G] = 0 and
            // y = A'(Ax - d).
            circularLeastSquares(n, G, d, z, params);
            cutoff(z, params.vectorCutoff);  //Set small entries of x and y to zero.

            iter++;

            progress.checkForCancel();
        }

        //Do one final refitting with these edges. For this we use a smaller tolerance
        NNLSParams polishParams = params;
        polishParams.pcgTol = polishParams.finalTol;
        circularLeastSquares(n, G, d, z, polishParams);
        maskEntries(z, G); //Zero entries in G. Now z is the solution, and zero for all active variables.
    }

    /**
     * Finds x that minimizes 0.5*||Ax - d||^2 such that x_i = 0 for all i \in G.
     * Here A is the circular split weight matrix.
     * <p>
     * The algorithm used depends on params.
     *
     * @param n      Number of taxa
     * @param G      Mask: indicating variables to constrain to zero
     * @param d      Vector of distances
     * @param z      Initial vector (only entries such that G[i]=false are used). Ignored if params.useWarmStart is false.
     * @param params Tuning parameters for the algorithm
     *               Let x be the optimal vector, and y =A'(Ax-d) the corresponding gradient vector. These are returned by overwriting the
     *               vector z, so that z[~G] = x[~G] and z[G] = y[~G]
     */
    private static void circularLeastSquares(int n, boolean[] G, double[] d, double[] z, NNLSParams params) {
        double diff1 = 0.0, diff2 = 0.0;
        int npairs = n * (n - 1) / 2;
        double[] zold = new double[npairs + 1];

        if (params.leastSquaresAlgorithm == NNLSParams.CG) {
            //Use conjugate gradient on Primal
            CircularConjugateGradient.circularConjugateGrads(n, d, G, z, params);
        } else if (params.leastSquaresAlgorithm == NNLSParams.DUAL_PCG) {
            DualPCG.dualPCG(n, d, G, z, params);
        }
    }

    /**
     * Computes statistics from the optimization search for use in profiling and debugging.
     * @param ntax  number of taxa
     * @param x     current split weights
     * @param d     vector of distances
     * @return array with 0th entry equal to min value of x, 1st entry equal to function value, 2nd entry equal to log of projected gradient.
     */
    private static double[] computeStats(int ntax, double[] x, double[] d) {
        double[] output = new double[3];
        int npairs = ntax * (ntax - 1) / 2;

        double minx = 1e10;
        for(int i=1;i<=npairs;i++)
            if (x[i]<minx)
                minx = x[i];
        output[0]=minx;

        double[] r = new double[npairs + 1];
        double[] grad = new double[npairs+1];

        circularAx(ntax, x, r);
        for (int i = 1; i <= npairs; i++)
            r[i] -= d[i];
        output[1]=normSquared(r); // ||Ax-d||^2
        circularAtx(ntax, r, grad);
        double pgnorm = 0.0;
        for(int i=1;i<=npairs;i++) {
            if (x[i]==0)
                grad[i] = Math.min(grad[i],0.0);
        }
        output[2] = normSquared(grad);
        return output;
    }




}







