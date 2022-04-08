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
