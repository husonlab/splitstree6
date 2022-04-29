package splitstree6.algorithms.distances.distances2splits.neighbornet;

public class SpeedKnitter {

    /**
     * Computes circular distances from an array of split weights.
     *
     * @param x split weights. Symmetric array. For i<j, x(i,j)  is the weight of the
     *          split {i,i+1,...,j-1} | rest.
     * @return
     */
    static public double[][] calcAx(double[][] x) {
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

    static public double[][]  calcAtx(double[][] x) {
        int n=x.length-1;
        double[][] p = new double[n+1][n+1];

        for(int i=1;i<=n-1;i++)
            p[i+1][i] = p[i][i+1]=sum(x[i],1,n);

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

    static public double[][] incrementalFitting(double[][] d, double tol) {
        int n = d.length - 1; //ntax
        double[][] x = new double[n+1][n+1];
        double[][] p = new double[n+1][n+1];

        int[] s = maxDivergenceOrder(d);

        boolean[] S = new boolean[n+1]; //Set of taxa already placed.
        int[] cycle = new int[n+1]; //Circular ordering for taxa already placed - in increasing order

        //First two taxa
        //Swap if s1>s2
        int s1=s[1], s2 = s[2];
               S[s1]=S[s2]=true;
        x[s1][s2] = x[s2][s1] = d[s1][s2];
        p[s1][s2] = p[s2][s1] = d[s1][s2];
        cycle[1]=Math.min(s1,s2);
        cycle[2]=Math.max(s1,s2);

        for (int k=3;k<=n;k++) {
            int sk = s[k];
            int u,v;
            int r1=0;
            while(r1<k-1 && sk>cycle[r1+1]) {
                r1++;
            }
            if (r1==0) {
                u = cycle[k - 1];
                v = cycle[1];
            } else if (r1<k-1) {
                u = cycle[r1];
                v = cycle[r1+1];
            } else {
                u = cycle[k-1];
                v = cycle[1];
            }
            int r2 = k-r1-1;



            //Now we want to determine the split weights for the new splits created by inserting sk.
            //We do this by funding gamma which minimizes ||M gamma - z ||
            //such that gamma_i \geq 0 for all i, and gamma_i \leq b_i for all i except that corresponding to the
            //split with sk by itself. (See manuscript for details)

            double[] z = new double[k];  //Vector indexed by the current k-1 elements of S.
            double[] b = new double[k];
            for(int j=1;j<k;j++) {
                z[j] = d[cycle[j]][sk] - p[cycle[j]][u];
                b[k] = x[cycle[j]][v];
            }
            int trivialSplit = r1+1;
            if (trivialSplit > k-1)
                trivialSplit = 1;
            b[trivialSplit]=Integer.MAX_VALUE; // No upper bound for this weight.

            //gamma0 is an initial guess of the weights - equal weights for splits A sk | B and A| sk B,
            //and complete guess for sk|AB
            double[] gamma0 = new double[k];
            for(int j=1;j<k;j++)
                gamma0[k]=b[j]/2.0;
            double meanz = 0.0;
            for(int j=1;j<k;j++)
                meanz += z[j]/(k-1);
            gamma0[trivialSplit] = meanz;

            double[] gamma1 = solveM(r1,r2,z);

            boolean isFeasible = true;
            for(int j=1;j<k && isFeasible; j++)
                if (gamma1[j]<0 || gamma1[j]>b[j])
                    isFeasible = false;

            double[] gamma = new double[k];
            if (isFeasible)
                System.arraycopy(gamma1,1,gamma,1,k-1);
            else {
                gamma = goldenInsertion(gamma0,gamma1,b,r1,r2,z,tol);
            }

            //Update the circular distances and split weights
            double[] Mgamma = multiplyM(r1,r2,gamma);
            for(int j=1;j<=r1;j++) {
                int sj = cycle[j];
                p[sk][sj] = p[sj][sk] = p[u][sj] + Mgamma[j];
                x[sk][sj] = x[sj][sk] = gamma[j];
                x[v][sj] -= gamma[j];
                x[sj][v] -= gamma[j];
                //TODO Check all non-negative
            }
        }
        return x;
    }


    /**
     * Computes an ordering of the taxa with the goal that for every k, the first k taxa should be far away from each
     * other.
     * In practice, this is done by choosing the first taxa to be the one with the greatest sum of distances to other
     * taxa. After that, we greedily choose the taxon which hasn't been inserted in the order but which has the
     * greatest summed distances to those already placed.
     * @param d  distance matrix. Assumed symmetric and non-negative
     * @return  permutation of 1,...,n, returned in an integer array (indices 1..n)
     */
    static private int[] maxDivergenceOrder(double[][] d) {
        int n = d.length - 1;
        int[] order = new int[n+1];

        //Find the row with the maximum row sum
        //TODO use Apache math code for this. Also use only one triangle of d.
        int maxrow=0;
        double maxsum=-1.0;
        for(int i=1;i<=n;i++) {
            double si = 0;
            for(int j=1;j<=n;j++)
                si+=d[i][j];
            if (si>maxsum) {
                maxsum = si;
                maxrow = i;
            }
        }
        order[1]=maxrow;

        //Set A of elements already placed in order
        boolean[] A = new boolean[n+1];
        A[maxrow]=true;

        //The vector s is the sum of distances from each element to everything in A.
        double[] s = new double[n+1];
        System.arraycopy(d[maxrow],1,s,1,n);

        for(int k=2;k<=n;k++) {
            //Find the element maxj not i A but maximizing s[maxj]
            int maxj=0;
            double maxs = -1.0;
            for (int j=1;j<=n;j++) {
                if (!A[j] && s[j]>maxs) {
                    maxj = j;
                    maxs = s[j];
                }
            }
            order[k]=maxj;
            A[maxj]=true;
            for(int j=1;j<=n;j++) {
                s[j] += d[maxj][j];
            }
        }
        return order;
    }


    static private double[] solveM(int r1, int r2, double[] z) {
        int m = r1+r2;
        double[] gamma = new double[m+1];
        for(int i=1;i<=m;i++)
            gamma[i]=0.5*z[i];

        gamma[1] -= 0.5*z[m];
        for(int i=2;i<=m;i++) {
            gamma[i] -= 0.5*z[i-1];
        }

        if (0<r1 && r1<m)
            gamma[r1+1]+=z[r1];
        else
            gamma[1]+=z[m];
        return gamma;
    }

    static private double[] multiplyM(int r1, int r2, double[] g) {
        int m = r1+r2;
        double[] y = new double[m+1];
        if (r1==0) {
            r1=m; r2=0;
        }

        double y1 = g[1];
        for(int j=2;j<=r1;j++)
            y1-=g[j];
        for(int j=r1+1;j<=m;j++)
            y1+=g[j];
        y[1]=y1;

        for(int i=2;i<=r1;i++) {
            y[i] = y[i-1]+2*g[i];
        }
        if (r2>0) {
            y[r1+1] = -y[r1] + 2*g[r1+1];
            for(int i=r1+2;i<=m;i++) {
                y[i] = y[i-1] + 2*g[i];
            }
        }
        return y;
    }

    static private double[] goldenInsertion(double[] gamma0, double[] gamma1, double[] b, int r1, int r2, double[] z, double tol) {


        class ObjectiveFunction {
            private final int m;
            private final double[] gammax;

            public ObjectiveFunction() {
                m=r1+r2;
                gammax = new double[m+1];
            }
            public double eval(double x) {
                for (int i = 1; i <= m; i++)   //Project onto box
                    gammax[i] = Math.min(Math.max((1 - x) * gamma0[i] + x * gamma1[i], 0.0), b[i]);
                double[] mg = multiplyM(r1, r2, gammax);
                double val = 0.0;
                for (int i = 1; i <= m; i++) {
                    val += (mg[i] - z[i]) * (mg[i] - z[i]);
                }
                return Math.sqrt(val);
            }

        }


        int m = r1+r2;
        double C = (3.0 - Math.sqrt(5))/2.0;
        double R = 1.0 - C;
        ObjectiveFunction f = new ObjectiveFunction();
        double x0 = 0, x1=C, x2 = C + C*(1-C), x3 = 1;

        double f1 = f.eval(x1);
        double f2 = f.eval(x2);

        int k=1;
        while(Math.abs(x3-x0)>tol) {
            if (f2<f1) {
                x0 = x1;
                x1 = x2;
                x2 = R*x1 + C*x3;
                f1 = f2;
                f2 = f.eval(x2);
            } else {
                x3=x2;
                x2 = x1;
                x1 = R*x2 + C*x0;
                f2=f1;
                f1 = f.eval(x1);
            }
        }

        double x=x2;
        if (f1<f2)
            x = x1;

        double[] gamma = new double[m+1];
        for (int i = 1; i <= m; i++)   //Project onto box
            gamma[i] = Math.min(Math.max((1 - x) * gamma0[i] + x * gamma1[i], 0.0), b[i]);

        return gamma;
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




}
