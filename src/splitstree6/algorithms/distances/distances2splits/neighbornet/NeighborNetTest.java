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

public class NeighborNetTest {

    static final boolean runThese = true;

    public static void main(String[] args) {

        //Generate a single random data set
        int n=30;
        double p=0.2;

        double[][] x = new double[n+1][n+1];
        double[][] xinitial = new double[n+1][n+1];
        double[][] y = new double[n+1][n+1];
        double sigma = 0.05;
        randomData(x,y,p,sigma);
        calcAinv_y(y,xinitial);
        zeroNegativeEntries(xinitial);
        var params = new NNLSParams();
        params.printResiduals = true;
        params.projGradBound = 2e-8;
        params.maxIterations = 10000;
        params.cgnrIterations = n*(n-1)/2;
        params.cgnrTolerance = 1e-8;

        if (!runThese) {
            testCGNR();

        }
        if (runThese) {
            testActiveSet(x,y,xinitial,params);
            testGradientProjection(x,y,xinitial,params);
            testAPGD(x,y,xinitial,params);
            testIPG(x,y,xinitial,params);
        }
    }


    public static void printGraphs(double[][] d, String filename) {
        int n = d.length-1;

        var params = new NNLSParams();
        params.cgnrPrintResiduals = false;
        params.printResiduals = true;
        params.projGradBound = 2e-8;
        params.maxIterations = 5000;
        params.cgnrIterations = n*(n-1)/2;
        params.cgnrTolerance = 1e-8;
        params.maxTime = 1000*5*60; //Max of 5 minutes per attempt

        if (runThese) {
            IPG_Graphs(d,params,filename);
        }
        if (!runThese) {
            activeSetGraphs(d,params,filename);
            gradientProjectionGraphs(d,params,filename);


            int maxInt = params.maxIterations;
            params.maxIterations = Integer.MAX_VALUE;
            APGD_Graphs(d,params,filename);
            params.maxIterations = maxInt;


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
        params.printResiduals = true;
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

    private static void testActiveSet(double[][] x, double[][] y, double[][] xinitial, NNLSParams params) {
        int n=x.length-1;
        double[][] x2 = new double[n+1][n+1];
        //Call Active Set
        params.log = setupLogfile("TestActive",false);
        params.activeSetRho = 0.4;
        copyArray(xinitial,x2);
        try {
            activeSetMethod(x2,y,params,null);
        } catch (CanceledException e) {
            e.printStackTrace();
        }
        params.log.close();
    }


    private static void testGradientProjection(double[][] x, double[][] y, double[][] xinitial,NNLSParams params) {
        int n=x.length-1;
        double[][] x2 = new double[n+1][n+1];

        params.cgnrIterations = Math.max(n,10);
        params.log = setupLogfile("TestGradientProjection.m",false);
        copyArray(xinitial,x2);
        try {
            gradientProjection(x2,y,params,null);
        } catch (CanceledException e) {
            e.printStackTrace();
        }
        params.log.close();
    }

    private static void testAPGD(double[][] x, double[][] y, double[][] xinitial, NNLSParams params) {
        int n=x.length-1;
        double[][] x2 = new double[n+1][n+1];

        params.log = setupLogfile("TestAPGD.m",false);
        params.APGDalpha = 0.5;

        copyArray(xinitial,x2);
        try {
            APGD(x2,y,params,null);
        } catch (CanceledException e) {
            e.printStackTrace();
        }
        params.log.close();
    }

    private static void testIPG(double[][] x, double[][] y, double[][] xinitial, NNLSParams params) {
        int n=x.length-1;
        double[][] x2 = new double[n+1][n+1];

        params.log = setupLogfile("TestIPG.m",false);
        params.IPGthreshold = 1e-6;
        params.IPGtau = 0.6;

        copyArray(xinitial,x2);
        try {
            IPG(x2,y,params,null);
        } catch (CanceledException e) {
            e.printStackTrace();
        }
        params.log.close();
    }





    public static void activeSetGraphs(double[][] d, NNLSParams params, String filename) {
        int n=d.length-1;

        params.log = setupLogfile(filename+"ActiveSetGraphs.m",false);
        params.log.println("%Active set Traces for the Active Set Method");
        params.log.println("%Options: rho \nRHOS=[0.2,0.3,0.4,0.5,0.6,0.7,0.8,0.9,1];");
        double[] rhos = {0.2,0.3,0.4,0.5,0.6,0.7,0.8,0.9,1};
        int[] cgiter = {n,1000,n*(n-1)/2};
        params.log.println("%Options: cgiter \nCGITER=["+n+" 1000 "+((n*(n-1)/2)) + "];");

        params.log.println("% Then columns are k, time, projected gradient, number of variables");
        params.log.println("activeSetData = cell("+rhos.length+","+cgiter.length+");");

        double[][] xinitial = new double[n+1][n+1];
        for (int r = 0;r<rhos.length;r++) {
            for(int c=0;c<cgiter.length;c++) {
                params.activeSetRho = rhos[r];
                params.cgnrIterations = cgiter[c];
                params.log.println("activeSetData{" + (r + 1) + ", "+(c+1) +"}=[");
                params.cgnrPrintResiduals = false;
                calcAinv_y(d, xinitial);
                zeroNegativeEntries(xinitial);
                try {
                    activeSetMethod(xinitial, d, params, null);
                } catch (CanceledException e) {
                    e.printStackTrace();
                }
                params.log.println("];\n\n\n");
                params.log.close();
                params.log = setupLogfile(filename + "ActiveSetGraphs.m", true);
            }
        }
        params.log.close();
    }




    public static void gradientProjectionGraphs(double[][] d, NNLSParams params, String filename) {
        int n=d.length-1;
        params.log = setupLogfile(filename+"GradientProjectionGraphs2.m",false);//*

        params.log.println("%Projected Gradient Traces for the Gradient Descent Method");
        params.log.println("%First dimension CGNR iterations \nCGNR=[10,100,"+n+",1000,"+(n*n/2)+"];");
        params.log.println("% Then columns are k, time, projected gradient, num vars");
       // int[] CGNRiter = {10,100,n,1000,n*(n-1)/2}; //*
        int[] CGNRiter = {n}; //*

        params.log.println("gradientProjectionData2 = cell("+CGNRiter.length+",1);");

        double[][] xinitial = new double[n+1][n+1];
        for (int c = 0;c<CGNRiter.length;c++) {
            params.cgnrIterations = CGNRiter[c];
            params.log.println("gradientProjectionData2{"+(c+1)+"}=["); //*
            calcAinv_y(d,xinitial);
            zeroNegativeEntries(xinitial);
            try {
                gradientProjection(xinitial,d,params,null);
            } catch (CanceledException e) {
                e.printStackTrace();
            }
            params.log.println("];\n\n\n");
            params.log.close();
            params.log = setupLogfile(filename+"GradientProjectionGraphs.m",true);
        }

        params.log.close();
    }

    public static void APGD_Graphs(double[][] d, NNLSParams params, String filename) {
        int n=d.length-1;


        params.log = setupLogfile(filename+"APGD_Graphs.m",false);

        params.log.println("%Projected Gradient Traces for the APGD");
        params.log.println("%First dimension, alpha0 \nALPHA0=[0.1,0.5,0.9,1.0];");
        params.log.println("% Then columns are k, time, projected gradient, number of variables");
        params.log.println("apgd_Data = cell(4,1);");
        double[] alpha0 = {0.1,0.5,0.9,1.0};

        double[][] xinitial = new double[n+1][n+1];
        for (int c = 0;c<=3;c++) {
            params.APGDalpha = alpha0[c];
            params.log.println("apgd_Data{"+(c+1)+"}=[");
            calcAinv_y(d,xinitial);
            zeroNegativeEntries(xinitial);
            try {
                APGD(xinitial,d,params,null);
            } catch (CanceledException e) {
                e.printStackTrace();
            }
            params.log.println("];\n\n\n");
            params.log.close();
            params.log = setupLogfile(filename+"APGD_Graphs.m",true);
        }
        params.log.close();
    }

    public static void IPG_Graphs(double[][] d, NNLSParams params, String filename) {
        int n=d.length-1;

        params.log = setupLogfile(filename+"IPG_Graphs.m",false);

        params.log.println("%Projected Gradient Traces for the IPG");
        params.log.println("%First dimension, tau \nTAU=[0.1,0.5,0.9];");
        params.log.println("%Second dimenion, threshold \nTHRESH = [1e-6,1e-8,1e-10];");
        params.log.println("% Then columns are k, time, projected gradient, number of variables");
        params.log.println("IPGdata = cell(3,3);");
        double[] taus = {0.1,0.5,0.9};
        double[] thresh = {1e-6,1e-8,1e-10};

        double[][] xinitial = new double[n+1][n+1];
        for (int c = 0;c<=2;c++) {
            for(int t = 0;t<=2;t++) {
                params.IPGtau = taus[c];
                params.IPGthreshold = thresh[t];
                params.log.println("IPGdata{"+(c+1)+","+(t+1)+"}=[");

                calcAinv_y(d,xinitial);
                zeroNegativeEntries(xinitial);
                try {
                    IPG(xinitial,d,params,null);
                } catch (CanceledException e) {
                    e.printStackTrace();
                }
                params.log.println("];\n\n\n");
                params.log.close();
                params.log = setupLogfile(filename+"IPG_Graphs.m",true);
            }
        }
        params.log.close();
    }






}
