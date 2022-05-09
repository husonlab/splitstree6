package splitstree6.algorithms.distances.distances2splits.neighbornet;


import static java.lang.Math.*;
import static splitstree6.algorithms.distances.distances2splits.neighbornet.SquareArrays.*;

//TODO: Reduce amount of needless memory allocation by passing arrays by reference.


public class SpeedKnitter {



    /**
     * greedyGradientProjection
     *
     * iterative heuristic for optimizing ||Ax - d|| where A is the matrix of distances vs splits and d are the
     * observed distances. Implements an algorithm related to that in
     *      O'Leary D. (1980) A Generalized conjugate algorithm for solving a class of quadratic programming
     *      problems. Linear Algebra and its Applications 34:371-399.
     *
     *The 'face' that a point x lies on is the set of dimensions i such that x_i is non-zero. The algorithm
     *takes the initial face from the starting value given to x. It minimizes the objective function using
     * CGNR (the version of conjugate gradient for least squares in Saad's book, pg 236), stopping at
     * convergence or after at most ntax iterations. If the resulting point is infeasible we search along
     * the curve given by the projection of the line from the prior value of x to this new value. This process
     * continues until the function value shows insufficient decline, noting that the dimension of the face
     * is shrinking in each step.
     *
     * This heuristic does not check the KKT conditions on conclusion, so it might fail to find the optimum
     * if this requires removing indices from the active set. Our initial experience is that we find good
     * estimates nevertheless (and in any case the method will be consistent with exacty arithmetic).
     *
     * @param x   Initial value for x. This is overwritten with the final value
     * @param d    Square array of observed distances
     * @param tolerance   tolerance used within CGNR and also to assess convergence of function values.
     * @param maxIterations  maximum number of iterations of the outer loop before an error is thrown.
     */
    static public void greedyGradientProjection(double[][] x, double[][] d, double tolerance, int maxIterations) {
        double fx_old = evalf(x,d);

        for (int k = 1; k <= maxIterations; k++) {
            searchFace(x, d,tolerance);
            double fx = evalf(x,d);
            //Q:- what is an appropriate bound here?
            if (fx_old-fx<tolerance) {
                return;
            }
            fx_old = fx;
        }
        System.err.println("Gradient projection algorithm failed to converge");
    }




    /**
     * Computes circular distances from an array of split weights.
     *
     * @param x split weights. Symmetric array. For i<j, x(i,j)  is the weight of the
     *          split {i,i+1,...,j-1} | rest.
     * @return double[][] circular metric corresponding to these split weights.
     */
    static private double[][] calcAx(double[][] x) {
        int n = x.length - 1;
        double[][] d = new double[n+1][n+1];

        for (int i=1;i<=(n-1);i++)
            d[i+1][i]=d[i][i+1] =sumSubvector(x[i+1],i+1,n)+sumSubvector(x[i+1],1,i);

        for (int i=1;i<=(n-2);i++)
            d[i+2][i]=d[i][i+2]=d[i][i+1]+d[i+1][i+2]-2*x[i+1][i+2];

        for (int k=3;k<=n-1;k++) {
            for(int i=1;i<=n-k;i++) {  //TODO. This loop can be threaded
                int j=i+k;
                d[j][i]=d[i][j] = d[i][j-1]+d[i+1][j] - d[i+1][j-1]-2*x[i+1][j];
            }
        }
        return d;
    }

    /**
     * Sum the elements in the vector over a range of indices.
     *
     * Separating this out in case we can improve efficiency with threading.
     * @param v vector
     * @param from   start index
     * @param to  end index
     * @return  \sum_{i=from}^to v(i)
     */
    static private double sumSubvector(double[] v, int from, int to) {
        double s=0.0;
        for(int i=from;i<=to;i++)
            s+=v[i];
        return s;
    }


    static private double[][]  calcAtx(double[][] x) {
        int n=x.length-1;
        double[][] p = new double[n+1][n+1];

        for(int i=1;i<=n-1;i++)
            p[i+1][i] = p[i][i+1]=sumSubvector(x[i],1,n);

        for(int i=1;i<=n-2;i++) {  //TODO This can be threaded
            p[i+2][i]=p[i][i+2] = p[i][i+1]+p[i+1][i+2]-2*x[i][i+1];
        }

        for(int k=3;k<=n-1;k++) {
            for(int i=1;i<=n-k;i++) { //TODO. This inner loop can be threaded
                p[i+k][i]=p[i][i+k]=p[i][i+k-1]+p[i+1][i+k]-p[i+1][i+k-1]-2*x[i][i+k-1];
            }
        }
        return p;
    }










    static private void searchFace(double[][] x, double[][] d, double tolerance) {
        int n=x.length-1;
        int maxIterations = n;

        double[][] x0 = new double[n+1][n+1];
        copyArray(x,x0);
        boolean[][] A = getZeroElements(x);

        double[][] p = new double[n+1][n+1];

        //Implementing CGNR in Saad's book

        double[][] r = calcAx(x);
        for(int i=1;i<=n;i++)
            for(int j=1;j<=n;j++)
                r[i][j] = d[i][j] - r[i][j];
        double[][] z = calcAtx(r);
        maskElements(z,A);
        copyArray(z,p);
        double ztz=sumArraySquared(z);


        int k=1;

        while(true) {

            double[][] w = calcAx(p);
            double alpha = ztz/sumArraySquared(w);

            for(int i=1;i<=n;i++) {
                for (int j = 1; j <= n; j++) {
                    x[i][j] += alpha * p[i][j];
                    r[i][j] -= alpha * w[i][j];
                }
            }
            z = calcAtx(r);
            maskElements(z,A);
            double ztz2 = sumArraySquared(z);
            double beta = ztz2/ztz;

            if (ztz2<tolerance || k >= maxIterations)
                break;

            for(int i=1;i<=n;i++) {
                for (int j = 1; j <= n; j++) {
                    p[i][j] = z[i][j] + beta * p[i][j];
                }
            }
            ztz = ztz2;
            //System.err.println(ztz);
            k++;
        }
        double s = sumArray(x);
        double s2 = sumArraySquared(x);

        if (minArray(x)<0) {
            //Use gradient projection to return the best projection of points on the line between x0 and x
            goldenProjection(x,x0,d,tolerance);
        }
    }



    static private void goldenProjection(double[][] x, double[][] x0,double[][] d, double tolerance) {
        //Minimize ||A \pi((1-t)x0 + tx) - d||  for t in [0,1]
        double C = (3-sqrt(5))/2.0;
        double R = 1.0 - C;

        double t0=0,t1=C,t2 = C+C*(1-C), t3 = 1.0;
        double f1 = evalfprojected(t1,x0,x,d);
        double f2 = evalfprojected(t2,x0,x,d);
        while(abs(t3-t0)>tolerance) {
            if (f2<f1) {
                t0=t1;
                t1=t2;
                t2 = R*t1 + C*t3;
                f1=f2;
                f2 = evalfprojected(t2,x0,x,d);
            } else {
                t3=t2;
                t2=t1;
                t1 = R*t2+C*t0;
                f2=f1;
                f1 = evalfprojected(t1,x0,x,d);
            }
        }
        double tmin = t1;
        if (f2<f1)
            tmin = t2;

        int n=x.length-1;
        for(int i=1;i<=n;i++) {
            for(int j=1;j<=n;j++) {
                double newx_ij = max((1-tmin)*x0[i][j] + tmin*x[i][j],0);
                x[i][j] = newx_ij;
            }
        }
    }

    static private double evalf(double[][] x, double[][] d) {
        int n = x.length-1;
        double[][] Axt = calcAx(x);
        return fro_dist(Axt,d);
    }

    static private double evalfprojected(double t, double[][] x0, double[][] x, double[][] d) {
        int n = x.length-1;
        double[][] xt = new double[n+1][n+1];
        for(int i=1;i<=n;i++)
            for(int j=1;j<=n;j++) {
                xt[i][j] = max(x0[i][j]*(1-t) + x[i][j]*t,0.0);
            }
        return evalf(xt,d);

    }
    
    




}
