package splitstree6.algorithms.distances.distances2splits.neighbornet;


import static java.lang.Math.*;

public class SpeedKnitter {

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

    static public double[][] incrementalFitting(double[][] d, double tol) {
        int n = d.length - 1; //ntax
        double[][] x = new double[n+1][n+1];
        double[][] p = new double[n+1][n+1];

        int[] s = maxDivergenceOrder(d);
        int[] cycle = new int[n+1]; //Circular ordering for taxa already placed - in increasing order

        //First two taxa
        int s1=s[1], s2 = s[2];
        x[s1][s2] = x[s2][s1] = d[s1][s2];
        p[s1][s2] = p[s2][s1] = d[s1][s2];
        cycle[1]=min(s1,s2);
        cycle[2]=max(s1,s2);

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
                b[j] = x[cycle[j]][v];
            }
            int trivialSplit = r1+1;
            if (trivialSplit > k-1)
                trivialSplit = 1;
            b[trivialSplit]=Double.MAX_VALUE; // No upper bound for this weight.

            //gamma0 is an initial guess of the weights - equal weights for splits A sk | B and A| sk B,
            //and complete guess for sk|AB
            double[] gamma0 = new double[k];
            for(int j=1;j<k;j++)
                gamma0[j]=b[j]/2.0;


            double sumz2 = 0.0;
            for(int j=1;j<k;j++)
                sumz2 += z[j]*z[j];
            gamma0[trivialSplit] = sqrt(sumz2)/(k-1);

            double[] gamma1 = solveM(r1,r2,z);

            boolean isFeasible = true;
            for(int j=1;j<k; j++)
                if (gamma1[j]<0 || gamma1[j]>b[j]) {
                    isFeasible = false;
                    break;
                }

            double[] gamma = new double[k];
            if (isFeasible)
                System.arraycopy(gamma1,1,gamma,1,k-1);
            else {
                gamma = goldenInsertion(gamma0,gamma1,b,r1,r2,z,tol);
            }

            //Update the circular distances and split weights
            double[] Mgamma = multiplyM(r1,r2,gamma);
            for(int j=1;j<k;j++) {
                int sj = cycle[j];
                p[sk][sj] = p[sj][sk] = p[u][sj] + Mgamma[j];
                x[sk][sj] = x[sj][sk] = gamma[j];
                x[v][sj] = max(x[v][sj] - gamma[j],0);
                x[sj][v] = x[v][sj];
            }

            //Insert sk into the cycle
            for(int j=1;j<=r2;j++)
                cycle[k-j+1]=cycle[k-j];
            cycle[r1+1]=sk;

            //DEBUGGING
            double[][] pS = new double[k+1][k+1];
            double[][] xS = new double[k+1][k+1];

            for(int i=1;i<=k;i++) {
                for (int j = 1; j <= k; j++) {
                    pS[i][j] = p[cycle[i]][cycle[j]];
                    xS[i][j] = x[cycle[i]][cycle[j]];
                }
            }

            pS[1][1] = pS[1][1];


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
                    gammax[i] = min(max((1 - x) * gamma0[i] + x * gamma1[i], 0.0), b[i]);
                double[] mg = multiplyM(r1, r2, gammax);
                double val = 0.0;
                for (int i = 1; i <= m; i++) {
                    val += (mg[i] - z[i]) * (mg[i] - z[i]);
                }
                return sqrt(val);
            }

        }


        int m = r1+r2;
        double maxdiff = 0.0;

        for(int i=1;i<=m;i++) {
            maxdiff = max(maxdiff,abs(gamma0[i]-gamma1[i]));
        }
        if (maxdiff == 0.0) {
            double[] gamma = new double[m + 1];
            System.arraycopy(gamma0, 1, gamma, 1, m);
            return gamma;
        }


        double C = (3.0 - sqrt(5))/2.0;
        double R = 1.0 - C;
        ObjectiveFunction f = new ObjectiveFunction();
        double x0 = 0, x1=C, x2 = C + C*(1-C), x3 = 1;

        double f1 = f.eval(x1);
        double f2 = f.eval(x2);

        while(abs(x3-x0)>tol/maxdiff) {
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
            gamma[i] = min(max((1 - x) * gamma0[i] + x * gamma1[i], 0.0), b[i]);

        return gamma;
    }


    static public void greedyGradientProjection(double[][] x, double[][] d, double tolerance, int maxIterations) {
        int n = x.length - 1; //Number of taxa
        double fx_old = evalf(x,d);

        for (int k = 1; k <= maxIterations; k++) {
            //TODO Sort out proper handling of arrays and references to arrays to avoid needless copying
            searchFace(x, d,tolerance);
            double fx = evalf(x,d);
            if (fx_old-fx<tolerance) {
                return;
            }
                fx_old = fx;

        }
        System.err.println("Gradient projection algorithm failed to converge");
    }


    //Returns the l_infinity difference
    static private double diff(double[][] x, double[][] y) {
        int n = x.length-1;
        double delta=0.0;
        for(int i=1;i<=n;i++) {
            for (int j = 1; j <=n; j++) {
                delta = max(delta,abs(x[i][j] - y[i][j]));
            }
        }
        return delta;
    }

    static private void copyArray(double[][] from, double[][] to) {
        int n=from.length-1;
        for(int i=1;i<=n;i++) {
            System.arraycopy(from[i],1,to[i],1,n);
        }
    }


    static private void searchFace(double[][] x, double[][] d, double tolerance) {
        int n=x.length-1;
        int maxIterations = n;

        double[][] x0 = new double[n+1][n+1];
        copyArray(x,x0);
        boolean[][] A = new boolean[n+1][n+1];
        for(int i=1;i<=n;i++)
            for(int j=1;j<=n;j++)
                A[i][j] = (x[i][j] == 0);

        double[][] p = new double[n+1][n+1];

        //Implementing CGNR in Saad's book

        double[][] r = calcAx(x);
        for(int i=1;i<=n;i++)
            for(int j=1;j<=n;j++)
                r[i][j] = d[i][j] - r[i][j];
        double[][] z = calcAtx(r);
        zeroElements(z,A);
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
            zeroElements(z,A);
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

    static private void zeroElements(double[][] r, boolean[][] A) {
        int n=r.length-1;
        for(int i=1;i<=n;i++)
            for(int j=1;j<=n;j++)
                if (A[i][j])
                    r[i][j]=0;
    }

    static private double sumArray(double[][] x) {
        double total = 0.0;
        double si;
        int n=x.length-1;

        for(int i=1;i<=n;i++) {
            si = 0.0;
            for (int j = 1; j <= n; j++) {
                si += x[i][j];
            }
            total += si;
        }
        return total;
    }

    static private double sumArraySquared(double[][] x) {
        double total = 0.0;
        int n=x.length-1;
        double si;
        for(int i=1;i<=n;i++) {
            si=0.0;
            for (int j = 1; j <= n; j++) {
                double x_ij = x[i][j];
                si += x_ij * x_ij;
            }
            total+=si;
        }
        return total;
    }

    static private double minArray(double[][] x) {
        double minx = 0.0;
        int n=x.length-1;
        for(int i=1;i<=n;i++)
            for(int j=1;j<=n;j++)
                minx = min(minx,x[i][j]);
        return minx;
    }

    static private int numTrue(boolean[][] x) {
        int total = 0;
        int n=x.length-1;
        for(int i=1;i<=n;i++)
            for(int j=1;j<=n;j++) {
                if (x[i][j])
                    total++;
            }
        return total;
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
        double fx = 0.0;
        for(int i=1;i<=n;i++)
            for(int j=1;j<=n;j++) {
                double res_ij = Axt[i][j] - d[i][j];
                fx += res_ij * res_ij;
            }
        return sqrt(fx);
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
