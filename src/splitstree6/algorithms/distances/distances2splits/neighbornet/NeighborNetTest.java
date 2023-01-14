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
        }
        if (runThese) {
            testActiveSet();
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
        Random generator = new Random();

        double[][] x = new double[n+1][n+1];
        double[][] xinitial = new double[n+1][n+1];
        double[][] x2 = new double[n+1][n+1];

        double[][] y = new double[n+1][n+1];
        double sigma = 0.0;

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

        //Initial is unconstrained solution, with negative entries set to 0.
        //noinspection SuspiciousNameCombination
        calcAinvx(y,xinitial);
        for(int i=1;i<=n;i++)
            for(int j=i+1;j<=n;j++)
                if (xinitial[i][j]<0.0)
                    xinitial[i][j] = xinitial[j][i] = 0.0;


        //Call Active Set
        NeighborNetSplitWeightsClean.NNLSParams params = new NeighborNetSplitWeightsClean.NNLSParams();
        params.cgnrIterations = n*n;
        params.cgnrTolerance = 1e-8;
        params.cgnrPrintResiduals = false;
        params.activeSetPrintResiduals = false;
        params.log = setupLogfile("TestActive",false);

        params.activeSetProjGradBound = 0.0;
        params.activeSetRho = 0.4;
        copyArray(xinitial,x2);
        try {
            activeSetMethod(x2,y,params,null);
        } catch (CanceledException e) {
            e.printStackTrace();
        }
        params.finalx = new double[n+1][n+1];
        params.activeSetPrintResiduals = true;
        params.activeSetProjGradBound = 1e-8;

        copyArray(x2,params.finalx);
        copyArray(xinitial,x2);
        try {
            activeSetMethod(x2,y,params,null);
        } catch (CanceledException e) {
            e.printStackTrace();
        }


        //Compute and print results
        params.log.println("Tested Active Set");
        double pg = evalProjectedGradientSquared(x2,y);

        double norm2 = 0.0;
        for(int i=1;i<=n;i++)
            for(int j=i+1;j<=n;j++) {
                norm2 += (x[i][j] - x2[i][j]) * (x[i][j] - x2[i][j]);
            }
        params.log.println("Diff squared= "+ norm2);
        params.log.println("ProjGrad squared= "+ pg);
    }

    public static void ActiveSetGraphs(double[][] d) {
        int n=d.length-1;
        NeighborNetSplitWeightsClean.NNLSParams params = new NeighborNetSplitWeightsClean.NNLSParams();
        params.cgnrTolerance = 1e-8;
        params.cgnrPrintResiduals = false;
        params.activeSetPrintResiduals = true;
        params.activeSetProjGradBound = 10.0*params.cgnrTolerance;
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
                calcAinvx(d,xinitial);
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

}
