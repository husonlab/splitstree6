package splitstree6.algorithms.distances.distances2splits.neighbornet;

import jloda.util.CanceledException;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.util.Random;

import static splitstree6.algorithms.distances.distances2splits.neighbornet.NeighborNetSplitWeightsClean.*;
import static splitstree6.algorithms.distances.distances2splits.neighbornet.NeighborNetUtilities.*;
import static splitstree6.algorithms.distances.distances2splits.neighbornet.SquareArrays.copyArray;

@SuppressWarnings("SpellCheckingInspection")
public class NeighborNetTest {

    static final boolean runThese = true;

    public static void main(String[] args) {

        if (!runThese) {
            testCGNR();
            testActiveSet();
            testBlockPivot();
        }
        if (runThese) {
            testGradientProjection();
        }
    }


    public static void printGraphs(double[][] d) {
        if (!runThese) {
            activeSetGraphs(d);
            blockPivotGraphs(d);
        }
        if (runThese) {
            gradientProjectionGraphs(d);
        }
    }

    /**
     * Open a file for log output
     * @param logfile filename
     * @return PrintWrite log file.
     */
    static private PrintWriter setupLogfile(String logfile, boolean append) {
        PrintWriter log = null;
        if (logfile!=null) {
            try {
                File f = new File(logfile);
                if (append &&  f.exists() && !f.isDirectory() ) {
                    log = new PrintWriter(new FileOutputStream(logfile, true));
                }
                else {
                    log = new PrintWriter(logfile);
                }
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
        }
        return log;
    }

    /**
     * Fills an array x with (uniform) random split weights and zeros, then
     * computes y = Ax + noise, where noise is Gaussian with s.d. sigma
     * @param x  square array, overwritten by split weights
     * @param y  square array, overwritten by noisy distances
     * @param p  probability a given entry in x is non-zero
     * @param sigma standard deviation for Gaussian noise added to y
     */
    private static void randomData(double[][] x, double[][] y, double p, double sigma) {
        int n = x.length-1;
        Random generator = new Random(System.currentTimeMillis());
        //Generate a random split weight vector with zeros
        for (int i=1;i<=n;i++) {
            for (int j=i+1;j<=n;j++) {
                if (Math.random()<p) {
                    x[i][j] = x[j][i] = generator.nextDouble();
                }
            }
        }
        calcAx(x,y);

        //Add noise to the observed distances
        for(int i=1;i<=n;i++) {
            for(int j=i+1;j<=n;j++) {
                y[j][i] = y[i][j] = y[i][j] + generator.nextGaussian()*sigma;
            }
        }

    }

    private static void testCGNR() {
        //Test CGNR
        int n=500;
        double p=0.2;
        boolean[][] active = new boolean[n+1][n+1];
        double[][] x = new double[n+1][n+1];
        double[][] x2 = new double[n+1][n+1];

        double[][] y = new double[n+1][n+1];

        //Generate a random active set and corresponding split weight vector
        for (int i=1;i<=n;i++) {
            for (int j=i+1;j<=n;j++) {
                if (Math.random()<p) {
                    active[i][j] = active[j][i] = false;
                    x[i][j] = x[j][i] = Math.random();
                } else {
                    active[i][j] = active[j][i] = true;
                }
            }
        }
        calcAx(x,y);

        //Call CGNR
        NeighborNetSplitWeightsClean.NNLSParams params = new NeighborNetSplitWeightsClean.NNLSParams();
        params.cgnrIterations = n*n;
        params.cgnrTolerance = 1e-8;
        params.cgnrPrintResiduals = true;
        int numIterations = 0;
        try {
            numIterations = cgnr(x2,y,active,params,null);
        } catch (CanceledException e) {
            e.printStackTrace();
        }
        params.log = setupLogfile("TestCGNR",false);

        //Compute and print results
        params.log.println("Tested CGNR");
        double[][] grad= new double[n+1][n+1];
        evalGradient(x2,y,grad);

        double norm2 = 0.0;
        double grad2 = 0.0;
        for(int i=1;i<=n;i++)
            for(int j=i+1;j<=n;j++) {
                norm2 += (x[i][j] - x2[i][j]) * (x[i][j] - x2[i][j]);
                grad2 += grad[i][j]*grad[i][j];
            }
        params.log.println("Num Iterations = "+numIterations);
        params.log.println("Max Iterations = "+params.cgnrIterations);
        params.log.println("Diff squared= "+ norm2);
        params.log.println("Grad squared= "+ grad2);
    }

    private static void testActiveSet() {
        int n=100;
        double p=0.2;

        double[][] x = new double[n+1][n+1];
        double[][] xinitial = new double[n+1][n+1];
        double[][] x2 = new double[n+1][n+1];

        double[][] y = new double[n+1][n+1];
        double sigma = 0.0;

        //Initial is unconstrained solution, with negative entries set to 0.
        randomData(x,y,p,sigma);
        calcAinv_y(y,xinitial);
        zeroNegativeEntries(x);

        //Call Active Set
        NeighborNetSplitWeightsClean.NNLSParams params = new NeighborNetSplitWeightsClean.NNLSParams();
        params.cgnrIterations = n*n;
        params.cgnrTolerance = 1e-8;
        params.cgnrPrintResiduals = false;
        params.activeSetPrintResiduals = false;
        params.log = setupLogfile("TestActive",false);

        //First run through to max iterations, in order to get an estimate of the optimal x.
        params.projGradBound = 0.0;
        params.activeSetRho = 0.4;
        copyArray(xinitial,x2);
        try {
            activeSetMethod(x2,y,params,null);
        } catch (CanceledException e) {
            e.printStackTrace();
        }
        params.finalx = new double[n+1][n+1];
        params.activeSetPrintResiduals = true;
        params.projGradBound = 2e-8;

        copyArray(x2,params.finalx);
        copyArray(xinitial,x2);
        try {
            activeSetMethod(x2,y,params,null);
        } catch (CanceledException e) {
            e.printStackTrace();
        }
    }

    public static void activeSetGraphs(double[][] d) {
        int n=d.length-1;
        NeighborNetSplitWeightsClean.NNLSParams params = new NeighborNetSplitWeightsClean.NNLSParams();
        params.cgnrTolerance = 1e-8;
        params.cgnrPrintResiduals = false;
        params.activeSetPrintResiduals = true;
        params.projGradBound = 10.0*params.cgnrTolerance;
        params.activeSetMaxIterations = 100000;
        params.log = setupLogfile("ActiveSetGraphs.m",false);

        params.log.println("%Projected Gradient Traces for the Active Set Method");
        params.log.println("%First dimension CGNR iterations \nCGNR=[10,100,1000,"+(n*n/2)+"];");
        params.log.println("%Second dimension rho \nRHOS=[0,0.2,0.4,0.6];");
        params.log.println("% Then columns are k, time, projected gradient");
        params.log.println("activeSetData = cells(4,4);");
        int[] CGNRiter = {10,100,1000,n*(n-1)/2};
        double[] rhos = {0,0.2,0.4,0.6};

        double[][] xinitial = new double[n+1][n+1];
        for (int c = 0;c<=3;c++) {
            for (int r = 0;r<=3;r++) {
                params.cgnrIterations = CGNRiter[c];
                params.activeSetRho = rhos[r];
                params.log.println("activeSetData{"+(c+1)+","+(r+1)+"}=[");
                calcAinv_y(d,xinitial);
                for(int i=1;i<=n;i++)
                    for(int j=i+1;j<=n;j++)
                        if (xinitial[i][j]<0.0)
                            xinitial[i][j] = xinitial[j][i] = 0.0;
                try {
                    activeSetMethod(xinitial,d,params,null);
                } catch (CanceledException e) {
                    e.printStackTrace();
                }
                params.log.println("];\n\n\n");
                params.log.close();
                params.log = setupLogfile("ActiveSetGraphs.m",true);
            }
        }


        params.cgnrIterations = n*n;
        params.activeSetRho = 0.4;
    }

    private static void testBlockPivot() {
        int n=100;
        double p=0.2;

        double[][] x = new double[n+1][n+1];
        double[][] xinitial = new double[n+1][n+1];
        double[][] x2 = new double[n+1][n+1];

        double[][] y = new double[n+1][n+1];
        double sigma = 0.05;

        //Initial is unconstrained solution, with negative entries set to 0.
        randomData(x,y,p,sigma);
        calcAinv_y(y,xinitial);
        zeroNegativeEntries(x);

        //Call Active Set
        NeighborNetSplitWeightsClean.NNLSParams params = new NeighborNetSplitWeightsClean.NNLSParams();
        params.cgnrIterations = n*n/2;
        params.cgnrTolerance = 1e-8;
        params.cgnrPrintResiduals = false;
        params.blockPivotCutoff = 1e-10;
        params.blockPivotPrintResiduals = false;
        params.blockPivotMaxIterations = 10000;

        params.log = setupLogfile("TestBlockPivot.m",false);

        //First run through to max iterations, in order to get an estimate of the optimal x.
        params.projGradBound = 0.0;

        copyArray(xinitial,x2);
        try {
            blockPivot(x2,y,params,null);
        } catch (CanceledException e) {
            e.printStackTrace();
        }
        params.finalx = new double[n+1][n+1];
        params.blockPivotPrintResiduals = true;
        params.projGradBound = 2e-8;

        copyArray(x2,params.finalx);
        copyArray(xinitial,x2);
        try {
            blockPivot(x2,y,params,null);
        } catch (CanceledException e) {
            e.printStackTrace();
        }
        params.log.close();
    }

    public static void blockPivotGraphs(double[][] d) {
        int n=d.length-1;
        NeighborNetSplitWeightsClean.NNLSParams params = new NeighborNetSplitWeightsClean.NNLSParams();
        params.cgnrTolerance = 1e-8;
        params.cgnrPrintResiduals = false;
        params.blockPivotPrintResiduals = true;
        params.projGradBound = 10.0*params.cgnrTolerance;
        params.log = setupLogfile("BlockPivotGraphs.m",false);

        params.log.println("%Projected Gradient Traces for the Block Pivot Method");
        params.log.println("%First dimension CGNR iterations \nCGNR=[10,100,1000,"+(n*n/2)+"];");
        params.log.println("%Second dimension, CUTOFF \nCutoff = [1e-6,1e-8,1e-10,1e-12];");
        params.log.println("% Then columns are k, time, projected gradient");
        params.log.println("activeSetData = cells(4,1);");
        int[] CGNRiter = {10,100,1000,n*(n-1)/2};
        double[] Cutoff = {1e-6,1e-8,1e-10,1e-12};

        double[][] xinitial = new double[n+1][n+1];
        for (int c = 0;c<=3;c++) {
            for (int cutoff = 0;cutoff<=3;cutoff++) {
                params.cgnrIterations = CGNRiter[c];
                params.blockPivotCutoff = Cutoff[cutoff];
                params.log.println("activeSetData{"+(c+1)+","+(cutoff+1)+"}=[");
                calcAinv_y(d,xinitial);
                zeroNegativeEntries(xinitial);
                try {
                    blockPivot(xinitial,d,params,null);
                } catch (CanceledException e) {
                    e.printStackTrace();
                }
                params.log.println("];\n\n\n");
                params.log.close();
                params.log = setupLogfile("BlockPivotGraphs.m",true);
            }
        }

        params.log.close();
    }

    private static void testGradientProjection() {
        int n=100;
        double p=0.2;

        double[][] x = new double[n+1][n+1];
        double[][] xinitial = new double[n+1][n+1];
        double[][] x2 = new double[n+1][n+1];

        double[][] y = new double[n+1][n+1];
        double sigma = 0.05;

        //Initial is unconstrained solution, with negative entries set to 0.
        randomData(x,y,p,sigma);
        calcAinv_y(y,xinitial);
        zeroNegativeEntries(x);

        //Call Active Set
        NeighborNetSplitWeightsClean.NNLSParams params = new NeighborNetSplitWeightsClean.NNLSParams();
        params.cgnrIterations = n*n/2;
        params.cgnrTolerance = 1e-8;
        params.cgnrPrintResiduals = false;
        params.gradientProjectionTol = params.cgnrTolerance;
        params.gradientProjectionMaxIterations = 100000;
        params.gradientProjectionPrintResiduals = false;
        params.log = setupLogfile("TestGradientProjection.m",false);

        //First run through to max iterations, in order to get an estimate of the optimal x.
        params.projGradBound = 0.0;

        copyArray(xinitial,x2);
        try {
            blockPivot(x2,y,params,null);
        } catch (CanceledException e) {
            e.printStackTrace();
        }
        params.finalx = new double[n+1][n+1];
        params.gradientProjectionPrintResiduals = true;
        params.projGradBound = 2e-8;

        copyArray(x2,params.finalx);
        copyArray(xinitial,x2);
        try {
            gradientProjection(x2,y,params,null);
        } catch (CanceledException e) {
            e.printStackTrace();
        }
        params.log.close();
    }

    public static void gradientProjectionGraphs(double[][] d) {
        int n=d.length-1;
        NeighborNetSplitWeightsClean.NNLSParams params = new NeighborNetSplitWeightsClean.NNLSParams();
        params.cgnrTolerance = 1e-8;
        params.cgnrPrintResiduals = false;
        params.gradientProjectionPrintResiduals = true;
        params.projGradBound = 10.0*params.cgnrTolerance;
        params.log = setupLogfile("BlockPivotGraphs.m",false);

        params.log.println("%Projected Gradient Traces for the Block Pivot Method");
        params.log.println("%First dimension CGNR iterations \nCGNR=[10,100,1000,"+(n*n/2)+"];");
        params.log.println("%Second dimension, CUTOFF \nCutoff = [1e-6,1e-8,1e-10,1e-12];");
        params.log.println("% Then columns are k, time, projected gradient");
        params.log.println("gradientProjectionData = cells(4,1);");
        int[] CGNRiter = {10,100,1000,n*(n-1)/2};
        double[] Cutoff = {1e-6,1e-8,1e-10,1e-12};

        double[][] xinitial = new double[n+1][n+1];
        for (int c = 0;c<=3;c++) {
            for (int cutoff = 0;cutoff<=3;cutoff++) {
                params.cgnrIterations = CGNRiter[c];
                params.gradientProjectionTol = Cutoff[cutoff];
                params.log.println("gradientProjectionData{"+(c+1)+","+(cutoff+1)+"}=[");
                calcAinv_y(d,xinitial);
                zeroNegativeEntries(xinitial);
                try {
                    gradientProjection(xinitial,d,params,null);
                } catch (CanceledException e) {
                    e.printStackTrace();
                }
                params.log.println("];\n\n\n");
                params.log.close();
                params.log = setupLogfile("GradientProjectionGraphs.m",true);
            }
        }

        params.log.close();
    }



}
