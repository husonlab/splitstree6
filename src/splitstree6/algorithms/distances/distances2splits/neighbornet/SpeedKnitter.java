package splitstree6.algorithms.distances.distances2splits.neighbornet;

public class SpeedKnitter {

    /**
     * Computes circular distances from an array of split weights.
     *
     * @param x split weights. Symmetric array. For i<j, x(i,j)  is the weight of the
     *          split {i,i+1,...,j-1} | rest.
     * @return
     */
    static public double[][] circularDistance(double[][] x) {
        int n = x.length - 1;
        double[][] d = new double[n+1][n+1];

        for (int i=1;i<=(n-1);i++)
            d[i+1][i]=d[i][i+1] =sum(x[i+1],i+1,n) + sum(x[i+1],1,i);


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

    static public double[][]  splitSum(double[][] d) {
        int n=d.length-1;
        double[][] p = new double[n+1][n+1];

        for(int i=1;i<=n-1;i++)
            p[i+1][i] = p[i][i+1]=sum(d[i],1,n);

        for(int i=1;i<=n-2;i++) {  //TODO This can be threaded
            p[i+2][i]=p[i][i+2] = p[i][i+1]+p[i+1][i+2]-2*d[i][i+1];
        }

        for(int k=3;k<=n-1;k++) {
            for(int i=1;i<=n-k;i++) { //TODO. This inner loop can be threaded
                p[i+k][i]=p[i][i+k]=p[i][i+k-1]+p[i+1][i+k]-p[i+1][i+k-1]-2*d[i][i+k-1];
            }
        }
        return p;
    }


    /**
     *
     * @param x
     * @param d
     * @param G
     * @param nonNeg
     * @param tol
     * @param maxIterations
     * @return
     *
     * TODO: Explore potential 2x speedup by taking advantage of symmetry.
     */
   /** public double[][] ConstrainedCG(double[][] x,
                                    double[][] d,
                                    boolean[][] G,
                                    boolean nonNeg,
                                    double tol,
                                    int maxIterations) {

        final double EPSILON = 1e-12;
        int n=x.length-1;

        for(int i = 1;i<=n;i++)
            for(int j=1;j<=n;j++)
                if (G[i][j])
                    x[i][j]=0; //x(G)=0;

        double[][] res = calcAx(x);
        for(int i=1;i<=n;i++)
            for(int j=1;j<=n;j++)
                res[i][j]-=d[i][j]; //res = Ax-d
        double[][] r = calcAtd(res); //r = A'(Ax-d)

        double[][] p = new double[n+1][n+1];
        for(int i=1;i<=n;i++)
            for(int j=1;j<=n;j++)
                p[i][j] = - r[i][j];  //p = -r

        int k=0;
        double rtr = 0.0;
        for(int i=1;i<=n;i++)
            for(int j=1;j<=n;j++)
                rtr += r[i][j]*r[i][j]; //rtr = ||r||^2

        double[][] w;
        double alpha_cg, alpha_bd, alpha;

        while (rtr>tol && k<maxIterations) {
            w = calcAx(p); //w = Ap

            double wtw = 0.0;
            for(int i=1;i<=n;i++)
                for (int j=1;j<=n;j++)
                    wtw+=w[i][j]*w[i][j]; //wtw = ||w||^2

            alpha_cg = rtr/wtw; //Standard CG steplength

            if (nonNeg) {
                alpha_bd = alpha_cg;
                for (int i=1;i<=n;i++)
                    for (int j=1;j<=n;j++) {
                        if (p(i,j)<0)
                            alpha_bd = Math.min(alpha_bd,-x[i][j]/p[i][j]);
                    }
            }  //Max feasiblse step length.
            if (!nonNeg || alpha_cg<=alpha_bd) {
                alpha = alpha_cg;
                double[][] Atw = calcAtd(w);
                double rtr2 = 0.0;
                for(int i=1;i<=n;i++) {
                    for(int j=1;j<=n;j++) {
                        x[i][j] += alpha*p[i][j];
                        if (G[i][j])
                            r[i][j]=0;
                        else
                            r[i][j] += alpha*Atw[i][j];
                        rtr2 += r[i][j]*r[i][j];
                    }
                }
                double beta = rtr2/rtr;
                for(int i=1;i<=n;i++)
                    for(int j=1;j<=n;j++)
                        p[i][j] = -r[i][j] + beta*p[i][j];

                k=k+1;
                rtr = rtr2;
            } else {
                alpha = alpha_bd;
                for(int i=1;i<=n;i++)
                    for(int j=1;j<=n;j++)
                        x[i][j] += alpha*p[i][j];

                //Check - if we are already adbutting the boundary, add boundary elements
                //to the active set G
                if (alpha_bd<EPSILON)
                    for(int i=1;i<=n;i++)
                        for(int j=1;j<=n;j++) {
                            G[i][j] = G[i][j] | ((i!=j)&&x[i][j]<EPSILON&&p[i][j]<0);
                        }

            }

        }

    }
**/

    /**
     * Sum the elements in the vector over a range of indices.
     *
     * Separating this out in case we can improve efficiency with threading.
     * @param v vector
     * @param from   start index
     * @param to  end index
     * @return  \sum_{i=from}^to v(i)
     */
    static private double sum(double[] v, int from, int to) {
        double s=0.0;
        for(int i=from;i<=to;i++)
            s+=v[i];
        return s;
    }


    static private double[][] calcAtd(double[][] d) {
        return splitSum(d);
    }
    static private double[][] calcAx(double[][] x) {
        return circularDistance(x);
    }


}
