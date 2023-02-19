package splitstree6.algorithms.distances.distances2splits.neighbornet;

import jloda.util.CanceledException;
import jloda.util.progress.ProgressListener;
import splitstree6.data.parts.ASplit;

import java.io.PrintWriter;
import java.util.*;

import static java.lang.Math.*;
import static splitstree6.algorithms.distances.distances2splits.neighbornet.NeighborNetUtilities.*;
import static splitstree6.algorithms.distances.distances2splits.neighbornet.SquareArrays.copyArray;

//import static splitstree6.algorithms.distances.distances2splits.neighbornet.SquareArrays.*;

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
        public double projGradBound; //Stop if projected gradient is less than this. This should be larger than the CGNR bound
        public boolean printResiduals;
        public int maxIterations;
        public long maxTime = Long.MAX_VALUE; //Stop if the method has taken more than this many milliseconds

        public int cgnrIterations; //Max number of iterations in CGNR
        public double cgnrTolerance; //Stopping condition for CGNR - bound on norm gradient squared.
        public boolean cgnrPrintResiduals = false;


        public double activeSetRho; //Proportion of pairs to add to active set in Active Set Method.
        public double blockPivotCutoff;
        public double gradientProjectionTol; //Tolerance for line search
        public double APGDalpha;
        public double IPGtau;
        public double IPGthreshold;


        public PrintWriter log;
        public double[][] finalx; //Target value, used just for debugging.
    }

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
        calcAinv_y(d,x); //Compute unconstrained solution
        var minVal = minArray(x);
        if (minVal < 0) {
            switch (params.method) {
                case ACTIVESET -> {

                }
            }
        }

        if (progress!=null)
            progress.checkForCancel();

        //Construct the corresponding set of weighted splits
        final var splitList = new ArrayList<ASplit>();
        for (var i = 1; i <= n; i++) {
            final var A = new BitSet();
            for (var j = i + 1; j <= n; j++) {
                A.set(cycle[j - 1]);
                if (x[i][j] > params.cutoff || A.cardinality() == 1 || A.cardinality() == n - 1) { // positive weight or trivial split
                    splitList.add(new ASplit(A, n, max(0, x[i][j])));
                }
            }
        }
        return splitList;
    }

    //*************************************************************************
    // METHOD IMPLEMENTATIONS
    //  **************************************************************************/

    /**
     * cgnr
     *
     * Implementation of the CGNR algorithm in Saad, "Iterative Methods for Sparse Linear Systems", applied to the
     * problem of minimizing ||Ax - d|| such that x_{ij} = 0 for all ij in the activeSet.
     *
     * @param x             Initial value, overwritten with final value. Initially, we set x[i][j] = 0 for all [i][j] in the active set.
     * @param d             square array of distances
     * @param activeSet     square array of boolean: specifying active (zero) set.
     * @param params        parameters - uses params.cgnrIterations for max number of iterations
     *                      and params.tolerance for bound on gradient at convergence
     * @return int          number of iterations
     */
    static public int cgnr(double[][] x, double[][] d, boolean[][] activeSet, NNLSParams params, ProgressListener progress) throws CanceledException {
        //TODO add progress listener support.

        if (params.cgnrPrintResiduals && params.log==null)
            System.err.println("Error with initialising log file");


        var n = x.length - 1;

        var p = new double[n + 1][n + 1];
        var r = new double[n + 1][n + 1];
        var z = new double[n + 1][n + 1];
        var w = new double[n + 1][n + 1];
        zeroNegativeEntries(x);

        calcAx(x, r);
        for (var i = 1; i <= n; i++)
            for (var j = i+1; j <= n; j++)
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
                for (var j = i+1; j <= n; j++) {
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
                for (var j = i+1; j <= n; j++) {
                    p[i][j] = z[i][j] + beta * p[i][j];
                }
            }
            ztz = ztz2;

            if (params.cgnrPrintResiduals)
                System.err.println("\t"+k+"\t"+ztz);

            k++;
            if (progress!=null && (k%n)==0)
                progress.checkForCancel();
        }
        return k;
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
    static public void activeSetMethod(double[][] x, double[][] d, NNLSParams params, ProgressListener progress) throws CanceledException {

        //TODO add progress listener support.

        long startTime = System.currentTimeMillis();

        var n = x.length-1;
        boolean[][] activeSet = new boolean[n+1][n+1];
        getActiveEntries(x,activeSet);

        double[][] xstar = new double[n+1][n+1];
        double[][] grad = new double[n+1][n+1];

        int k = 0;

        while(true) {
            while(true) {
                copyArray(x,xstar);
                int numIterations = cgnr(xstar, d, activeSet, params,progress);
                k++;
                if (progress!=null)
                    progress.checkForCancel();

                boolean xstarFeasible = feasibleMoveActiveSet(x,xstar,activeSet,params);

                if (params.printResiduals) {
                    params.log.print("\t" + k + "\t" + (System.currentTimeMillis()-startTime)+"\t"+evalProjectedGradientSquared(x, d) + "\t" + (n*(n-1)/2-cardinality(activeSet)));
                    if (params.finalx!=null)
                        params.log.print("\t"+diff(x,params.finalx));
                    params.log.println();
                }

                if (xstarFeasible && numIterations<params.cgnrIterations)
                    break;
                if (k> params.maxIterations || (startTime-System.currentTimeMillis())>params.maxTime)
                    return;
            }
            copyArray(xstar,x);
            double pg = evalProjectedGradientSquared(x,d);
            if (pg<params.projGradBound)
                return;

            //At this point x is feasible, but not necessarily a solution to the equality constrained problem.
            //Determine if there is an active constraint which can be removed.


            evalGradient(x,d,grad);
            int imin=0,jmin=0;
            var gradmin = 0.0;

            for(int i=1;i<=n;i++) {
                for(int j=i+1;j<=n;j++) {
                    double g_ij = grad[i][j];
                    if (activeSet[i][j] && g_ij<gradmin) {
                        gradmin = g_ij;
                        imin = i; jmin=j;
                    }
                }
            }
            if (gradmin < 0.0)
                activeSet[imin][jmin] = false;
        }

    }




    /**
     * Move the point x towards point xstar, while still maintaining feasibility.
     *
     * If xstar is feasible, then returns true. Otherwise, the activeset will change and a proportion
     * params.activeSetrho of the infeasible entries in xstar will be added to the active set.
     * @param x square matrix --- feasible initial point
     * @param xstar   square matrix ---  target point, will generally be infeasible
     * @param activeSet    current active set. assumed that xstar will satisfy the active set, and x will be moved
     *                     to a point satisfying the active set
     * @param params    uses parameter activeSetRho, which is the proportion of infeasible entries of xstar which will
     *                  be added to the active set.
     * @return      true if xstar is feasible, false otherwise
     */
    static private boolean feasibleMoveActiveSet(double[][] x, double[][] xstar, boolean[][] activeSet, NNLSParams params) {
        var n = xstar.length - 1;

        //First check if xstar is feasible, and return true if it is after moving x to xstar
        if (minArray(xstar) >= 0.0) {
            copyArray(xstar,x);
            return true;
        }

        //xstar is infeasible.
        //Store values in a structure for sorting
        SortedPairs sortedPairs = new SortedPairs(n);
        for (int i = 1; i <= n; i++)
            for (int j = i + 1; j <= n; j++)
                if (xstar[i][j] < 0)
                    sortedPairs.insert(i,j,x[i][j] / (x[i][j] - xstar[i][j]));

        sortedPairs.sort();
        SortedPairs.Entry firstEntry = sortedPairs.get(0);
        if (firstEntry==null) {  //Should never get here
            copyArray(xstar,x);
            return true;
        }
        double t = firstEntry.val; //max val of t before first constraint met.

        //A proportion rho of the indices for which xstar is negative is added to the active set.
        int numToMakeActive = max(1, (int) Math.ceil(sortedPairs.nentries * params.activeSetRho));
        int index = 0;
        SortedPairs.Entry entry = sortedPairs.get(index);
        while(index<numToMakeActive&& entry!=null) {
            int i=entry.i; int j = entry.j;
            activeSet[i][j] =  true;
            x[i][j] =  0.0;
            index++;
            entry = sortedPairs.get(index);
        }

        for(int i=1;i<=n;i++)
            for(int j=i+1;j<=n;j++) {
                if (!activeSet[i][j]) {
                    x[i][j] = (1 - t) * x[i][j] + t * xstar[i][j];
                }
            }
        return false;
    }

    /**
     * Utility class to sort indices
     */
    private static class SortedPairs {
        public static class Entry {
            public Entry(int i, int j, double val) {
                this.i=i; this.j = j; this.val = val;
            }
            double val;
            int i;
            int j;
        }

        public int nentries;
        private final Entry[] entries;

        public SortedPairs(int n) {
            nentries = 0;
            entries = new Entry[n*(n-1)/2];
        }
        public void insert(int i, int j, double val) {
            entries[nentries] = new Entry(i, j, val);
            nentries++;
        }
        public void sort() {
            Arrays.sort(entries, 0,nentries, Comparator.comparingDouble(o -> o.val));
        }

        public Entry get(int index) {
            if (index<0 || index>=nentries)
                return null;
            else
                return entries[index];
        }

    }


    /**
     * Implementation of the Block Pivot method tsnnls, as described in
     *          Cantarella, Jason, and Michael Piatek. "Tsnnls: A solver for large sparse least squares problems with non-negative variables."
     *          arXiv preprint cs/0408029 (2004).
     *
     *
     * @param x  Square array, can be used to warm start
     * @param d   square array of distances
     * @param params parameters for the method
     * @param progress   progressListener
     * @throws CanceledException  exception thrown if user presses cancel
     */
    static public void blockPivot(double[][] x, double[][] d, NNLSParams params, ProgressListener progress) throws CanceledException {
        var n = x.length - 1;
        var G = new boolean[n + 1][n + 1];
        var x2 = new double[n+1][n+1];

        long startTime = System.currentTimeMillis();

        var N = Integer.MAX_VALUE;
        var p = 3;
        boolean done = false;
        int iter = 0;

        getActiveEntries(x, G);
        cgnr(x, d, G, params, progress);

        //gradient
        var y = new double[n + 1][n + 1];
        evalGradient(x, d, y);

        if (params.printResiduals) {
            params.log.print("\t" + iter + "\t" + (System.currentTimeMillis()-startTime)+"\t"+evalProjectedGradientSquared(x,d)+ "\t" + (n*(n-1)/2-cardinality(G)) + "\t0");
            if (params.finalx!=null)
                params.log.print("\t"+diff(x2,params.finalx));
            params.log.println();
        }

        threshold(x,params.blockPivotCutoff);
        threshold(y,params.blockPivotCutoff);
        var infeasible = new boolean[n + 1][n + 1];

        while (!done) {
            iter++;

            //Determine and count infeasible entries: active entries with negative gradient or inactive entries with negative value
            int numInfeasible = 0;
            int switched = 0;

            for (var i = 1; i <= n; i++) {
                for (var j = i + 1; j <= n; j++) {
                    if ((!G[i][j] && x[i][j] < 0) || (G[i][j] && y[i][j] < 0)) {
                        numInfeasible++;
                        infeasible[i][j] =  true;
                    }
                    else
                        infeasible[i][j] =  false;
                }
            }

            if (numInfeasible == 0 || iter>=params.maxIterations)
                done = true;
            else if (numInfeasible < N) {
                N = numInfeasible;
                p = 3;
                xor(G,infeasible);
                switched = numInfeasible;
            } else {
                if (p > 0) {
                    p--;
                    xor(G,infeasible);
                    switched = numInfeasible;
                } else {
                    var foundInfeasible = false;
                    for (var i = 1; i <= n && !foundInfeasible; i++) {
                        for (var j = i + 1; j <= n && !foundInfeasible; j++) {
                            if (infeasible[i][j]) {
                                G[i][j] =  !G[i][j];
                                foundInfeasible = true;
                            }
                        }
                    }
                    switched = -1;
                }
            }
            cgnr(x, d, G, params, progress);
            evalGradient(x,d,y);
            if (progress!=null)
                progress.checkForCancel();

            //Check if zeroing negative entries in x gives small enough projected gradient
            copyArray(x,x2);
            zeroNegativeEntries(x2);
            double pg = evalProjectedGradientSquared(x2, d);
            if (pg<params.projGradBound) {
                copyArray(x2,x);
                return;
            }
            if (params.printResiduals) {
                params.log.print("\t" + iter + "\t" + (System.currentTimeMillis()-startTime)+"\t"+pg+ "\t" + (n*(n-1)/2-cardinality(G))+"\t"+switched);
                if (params.finalx!=null)
                    params.log.print("\t"+diff(x2,params.finalx));
                params.log.println();
            }

            threshold(x,params.blockPivotCutoff);
            threshold(y,params.blockPivotCutoff);
        }
    }

    //TODO: Javadoc
    static public void gradientProjection(double[][] x, double[][] d, NNLSParams params, ProgressListener progress) throws CanceledException {
        var n = x.length - 1;
        long startTime = System.currentTimeMillis();

        double[][] xstar = new double[n + 1][n + 1];
        double[][] p = new double[n + 1][n + 1];
        var activeSet = new boolean[n + 1][n + 1];


        for (var k = 1; k <= params.maxIterations; k++) {

            //Search direction
            evalGradient(x, d, p);
            for(int i=1;i<=n;i++)
                for(int j=i+1;j<=n;j++)
                    p[i][j] = -p[i][j];

            projectedLineSearch(x,p,d);
            getActiveEntries(x,activeSet);

            if(progress!=null)
                progress.checkForCancel();
            //LOCAL SEARCH
            copyArray(x, xstar);
            cgnr(xstar, d, activeSet, params, progress); //Just a few iterations of CG
            for (int i = 1; i <= n; i++)
                for (int j = i + 1; j <= n; j++)
                    p[i][j] = xstar[i][j] - x[i][j];

            projectedLineSearch(x, p, d);
            double pg = evalProjectedGradientSquared(x, d);
            if (params.printResiduals) {
                params.log.println(k+"\t"+(System.currentTimeMillis()-startTime) + "\t"+pg+"\t"+(n*(n-1)/2-cardinality(activeSet)));
            }

            if (progress!=null)
                progress.checkForCancel();
            if (pg< params.projGradBound)
                return;
            if ((startTime-System.currentTimeMillis())>params.maxTime)
                return;
        }
    }

    //TODO: Javadoc
    private static void projectedLineSearch(double[][] x, double[][] p, double[][] d) {
        //Projected line search, following Nocedal, pg 486-488, though the fact we can rapidly compute Ax means we don't fuss
        //around with updating (hopefully will help with accumulating error
        int n=x.length-1;
        double tmin;

        TreeSet<Double> tvals = new TreeSet<>();

        for (int i = 1; i <= n; i++)
            for (int j = i + 1; j <= n; j++)
                if (p[i][j] < 0)
                    tvals.add(-x[i][j] / p[i][j]);

        double[][] xk = new double[n+1][n+1];
        double[][] pk = new double[n+1][n+1];
        double[][] Ap = new double[n+1][n+1];
        double[][] Ax = new double[n+1][n+1];

        double left,right = 0.0;

        Iterator<Double> tval = tvals.iterator();
        while(true) {
            left = right;
            if (!tval.hasNext())
                right = Double.MAX_VALUE;
            else
                right = tval.next();

            for(int i=1;i<=n;i++)
                for(int j=i+1;j<=n;j++) {
                    xk[i][j] = max(x[i][j] + left*p[i][j],0.0);
                    if (xk[i][j] > 0)
                        pk[i][j] = p[i][j];
                    else
                        pk[i][j] = 0.0;
                }
            calcAx(pk,Ap);
            calcAx(xk,Ax);
            double pAtAp = 0.0;
            double pAtr = 0.0;

            for(int i=1;i<=n;i++)
                for(int j=i+1;j<=n;j++) {
                    double APij = Ap[i][j];
                    pAtAp += APij*APij;
                    pAtr += APij * (Ax[i][j] - d[i][j]);
                }
            double step = - pAtr/pAtAp;

            if (step<0) {
                tmin = left;
                break;
            } else if (step < (right-left)) {
                tmin = left + step;
                break;
            }

        }

        for (int i = 1; i <= n; i++)
            for (int j = i + 1; j <= n; j++)
                x[i][j] = max(x[i][j] + tmin * p[i][j],0.0);

    }


    static public void APGD(double[][] x, double[][] d, NNLSParams params, ProgressListener progress) throws CanceledException {
        var n = x.length - 1;

        zeroNegativeEntries(x);
        long startTime = System.currentTimeMillis();

        var L = estimateMatrixNorm(n);

        double alpha_old;
        double alpha = params.APGDalpha;
        ;


        var y = new double[n + 1][n + 1];
        var g = new double[n + 1][n + 1];
        var x_old = new double[n + 1][n + 1];

        copyArray(x, y);
        int k = 0;
        var error_old = evalResidual(x, d);

        while (true) {
            //Store previous variables
            copyArray(x, x_old);
            alpha_old = alpha;

            //Compute acceleration parameters
            double a2 = alpha * alpha;
            alpha = 0.5 * (-a2 + alpha * sqrt(a2 + 4));
            double beta = alpha_old * (1 - alpha_old) / (a2 + alpha);

            //Update
            evalGradient(y, d, g);
            for (var i = 1; i <= n; i++) {
                for (var j = i + 1; j <= n; j++) {
                    x[i][j] = max(y[i][j] - (1.0 / L) * g[i][j], 0.0);
                }
            }
            for (int i = 1; i <= n; i++) {
                for (int j = i + 1; j <= n; j++) {
                    y[i][j] = (1 + beta) * x[i][j] - beta * x_old[i][j];
                }
            }

            //Compute error
            //TODO could shave time here by updating dynamically
            var error = evalResidual(x, d);
            if (k > 0 && (error > error_old)) {
                //Restart - just do a gradient update
                evalGradient(x_old, d, g);
                for (var i = 1; i <= n; i++) {
                    for (var j = i + 1; j <= n; j++) {
                        x[i][j] = max(x_old[i][j] - (1.0 / L) * g[i][j], 0.0);
                    }
                }
                copyArray(x, y);
                alpha = params.APGDalpha;
            }

            double pg = evalProjectedGradientSquared(x,d);
            if (params.printResiduals && k%10==0) {
                params.log.println("\t"+k+"\t"+(System.currentTimeMillis()-startTime) + "\t"+pg + "\t"+(numberNonzero(x)));
            }
            if (pg<params.projGradBound || k >= params.maxIterations || (startTime-System.currentTimeMillis())>params.maxTime)
                return;
            if (progress!=null)
                progress.checkForCancel();
            k++;
        }
    }




    /**
     * Rough estimate of the 2-norm of A'A, which I got in MATLAB by computing the norms and fitting a 4 degree polynomial.
     *
     * @param n number of taxa
     * @return estimate of ||A'A||_2
     */
    static private double estimateMatrixNorm(int n) {
        return (((0.041063124831008 * n + 0.000073540331934) * n + 0.065260125117342) * n + 0.027499142031727) * n - 0.038454953524879;
    }

    static public void IPG(double[][] x, double[][] d, NNLSParams params, ProgressListener progress) throws CanceledException {
        var n = x.length - 1;
        long startTime = System.currentTimeMillis();


        //Initial x needs to be strictly positive.
        zeroNegativeEntries(x);
        double dsum = 0.0;
        for(int i=1;i<n;i++)
            for(int j=i+1;j<=n;j++)
                dsum += d[i][j];
        for(int i=1;i<n;i++)
            for(int j=i+1;j<=n;j++)
                x[i][j] += dsum/(n*n*n);

        int k;
        var g = new double[n+1][n+1];
        var y = new double[n+1][n+1];
        var z = new double[n+1][n+1];
        var p = new double[n+1][n+1];
        var xmapped = new double[n+1][n+1];



        for(k=1;k<=params.maxIterations;k++) {
            evalGradient(x, d, g);
            calcAx(x, y);
            calcAtx(y, z);  //z = A'Ax
            for (int i = 1; i <= n; i++)
                for (int j = i + 1; j <= n; j++)
                    p[i][j] = -x[i][j] / z[i][j] * g[i][j];

            double alphahat = Double.MAX_VALUE;
            for (int i = 1; i <= n; i++)
                for (int j = i + 1; j <= n; j++)
                    if (p[i][j] < 0)
                        alphahat = min(alphahat, -x[i][j] / p[i][j]);

            calcAx(p, y);
            double ptg = 0.0, ptAtAp = 0.0;
            for (int i = 1; i <= n; i++)
                for (int j = i + 1; j <= n; j++) {
                    ptg += p[i][j] * g[i][j];
                    ptAtAp += y[i][j] * y[i][j];
                }
            double alphastar = -ptg / ptAtAp;
            double alpha = min(params.IPGtau * alphahat, alphastar);
            for (int i = 1; i <= n; i++)
                for (int j = i + 1; j <= n; j++) {
                    x[i][j] += alpha * p[i][j];
                }

            copyArray(x,xmapped);
            threshold(xmapped,params.IPGthreshold);
            double pg = evalProjectedGradientSquared(xmapped, d);
            if (params.printResiduals)
                params.log.println(k + "\t" + (System.currentTimeMillis() - startTime) + "\t" + pg+"\t"+numberNonzero(xmapped));
            if (pg < params.projGradBound || (startTime-System.currentTimeMillis())>params.maxTime) {
                copyArray(xmapped,x);
                return;
            }
            if (progress!=null)
                progress.checkForCancel();
        }

    }

}

