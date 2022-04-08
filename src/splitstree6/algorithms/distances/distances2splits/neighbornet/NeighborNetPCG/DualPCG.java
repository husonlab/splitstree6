package splitstree6.algorithms.distances.distances2splits.neighbornet.NeighborNetPCG;


import splitstree6.algorithms.distances.distances2splits.neighbornet.NeighborNetSplits;

import java.io.IOException;
import java.util.Arrays;

import static splitstree6.algorithms.distances.distances2splits.neighbornet.NeighborNetPCG.CircularSplitAlgorithms.*;
import static splitstree6.algorithms.distances.distances2splits.neighbornet.NeighborNetPCG.VectorUtilities.*;


public class DualPCG {

    public static void dualPCG(int n, double[] d, boolean[] G, double[] zvec, NeighborNetSplits.NNLSParams params) {


        int nG = count(G);  //Count the number of equality constraints
        if (nG == 0)  //No equality constraints - use straight solve. G = emptyset
            circularSolve(n, d, zvec);

        /* Let R be the identity matrix restricted to columns in G. To minimise ||Ax - d|| such that R'x = 0 we use the
        KKT system
        [A'A R] [x]  =  [A'd]
        [R'  0] [v]     [0]

        Let B = A^{-T} R. Then B'd = R' A^{-1} d
        We solve B'B v = B' d using preconditioned conjugate gradient, and then
        x = (A'A)^{-1} (A'd - R v) = A^{-1} (d - A^{-T}Rv)
        y = A'(Ax - d) = A'(d - A^{-T}Rv - d) = -Rv

        The preconditioner we use for X takes advantage of the block structure of B'B. For this part of the algorithm we
        store blocks of the vectors separately. If x is the vector stores as blocks, xvec is the corresponding vector
        as a single block.



        TODO: Investigate precomputing B as a block matrix?
         */

        long startTime = System.currentTimeMillis();

        if (params.verboseOutput) {
            try {
                params.writer.write("\t\t%Entering dualPCG. |G| = "+nG+"\tPreconditioner="+params.usePreconditioner+"\n");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        int npairs = n * (n - 1) / 2; //Dimensions of G,d.
        double tol = params.pcgTol;

        BlockXMatrix X = null;
        Preconditioner M = null;
        if (params.usePreconditioner) {
            X = new BlockXMatrix(n, mask2blockmask(n, G));
            M = new Preconditioner(X, params.preconditionerBands);
        }

        double[] nu = new double[nG + 1];
        double[] r = new double[npairs + 1];
        System.arraycopy(d, 1, r, 1, npairs); //r = d - B*0
        double[] rt = new double[nG + 1];  //tilde{r} = B' r
        calculateBtx(n, r, G, rt);
        double[] z = new double[nG + 1];
        if (params.usePreconditioner)
            z = M.solve(rt, G);
        else
            System.arraycopy(rt, 1, z, 1, nG);
        double[] p = new double[nG + 1];
        System.arraycopy(z, 1, p, 1, nG);
        double[] w = new double[npairs + 1];

        double zrt = dotProduct(z, rt);

        double[] residuals = new double[1];
        if (params.printCGconvergence) {
            residuals = new double[params.maxPCGIterations];
        }





        int k = 1;
        while (k < params.maxPCGIterations && normSquared(z) > tol * tol) {

            if (params.printCGconvergence) {
                residuals[k] = normSquared(z);
            }


            calculateBx(n, p, G, w);  //w = Bp
            double alpha = zrt / normSquared(w);
            for (int i = 1; i <= nG; i++)
                nu[i] += alpha * p[i];
            for (int i = 1; i <= npairs; i++)
                r[i] -= alpha * w[i];

            calculateBtx(n, r, G, rt);
            if (params.usePreconditioner)
                z = M.solve(rt, G);
            else
                System.arraycopy(rt, 1, z, 1, nG);
            double zrt_new = dotProduct(z, rt);
            double beta = zrt_new / zrt;
            for (int i = 1; i <= nG; i++)
                p[i] = z[i] + beta * p[i];
            zrt = zrt_new;
            k++;
        }

        /*DEBUG ***
        //Compute B'(Bnu - d)
        double[] Bnu = new double[npairs+1];
        calculateBx(n,nu,G,Bnu);
        for (int i=1;i<=npairs;i++)
            Bnu[i] -= d[i];
        double[] gradnu = new double[nG+1];
        calculateBtx(n,Bnu,G,gradnu);
        System.err.println("DUAL PCG error = "+ normSquared(gradnu));
         */

        if (params.printCGconvergence) {
            if (params.printCGconvergence) {
                try {
                    params.writer.write("res = [");
                    for(int i=0;i<k;i++) {
                        params.writer.write("\t\t\t"+i+"\t"+residuals[i]+";\n");
                    }
                    params.writer.write("]; hold on; plot(res(:,1),log(res(:,2)),'LineWidth',8,'DisplayName','"+count(G)+"'); hold off \n\n");
                    params.writer.write("numIters(rep,:)=["+count(G)+" size(res,1)]; rep=rep+1;\n");

                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }


        /* Compute x and y and copy them into z.*/
        double[] x = new double[npairs + 1];
        double[] y = new double[npairs + 1];
        z = new double[npairs + 1];
        /*
        x = (A'A)^{-1} (A'd - R v) = A^{-1} (d - A^{-T} Rv)
        y = A'(Ax - d) = A'(d - Rv - d) = -A'Rv
         */
        double[] Rv = computeRv(nu, G);
        circularAinvT(n, Rv, y);
        for (int i = 1; i <= npairs; i++)
            y[i] = d[i] - y[i];
        circularSolve(n, y, x);
        circularAtx(n, Rv, y);

        if (params.verboseOutput) {
            try {
                params.writer.write("\t\t%Exiting dualPCG. Number of iterations was "+k+"\tTime = "+(System.currentTimeMillis()-startTime)+"\n");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }




        /* ***
        double[] Ax = new double[npairs+1];
        circularAx(n,x,Ax);
        for(int i=1;i<=npairs;i++)
            Ax[i] -= d[i];
        circularAtx(n,Ax,y);
        double err2=0.0;
        for(int i=1;i<=npairs;i++)
                err2 += (y[i]+Rv[i])*(y[i]+Rv[i]);

        //Evaluate top line of kkt
        Rv = computeRv(nu,G);
        circularAinvT(n,Rv,y);
        circularSolve(n,y,z);
        double[] lhs = computeRtx(z,G);
        circularSolve(n,d,y);
        double[] rhs = computeRtx(y,G);
        double err = 0.0;
        for(int i=1;i<lhs.length;i++)
            err += (lhs[i]-rhs[i])*(lhs[i]-rhs[i]);
        System.err.println("KKT error = "+err);

                Rv = computeRv(nu,G);
        */


        for (int i = 1; i <= npairs; i++) {
            if (G[i])
                zvec[i] = -y[i];
            else
                zvec[i] = x[i];
        }
    }


    static public double[] computeRv(double[] v, boolean[] G) {
        int npairs = G.length - 1;
        double[] Rv = new double[npairs + 1];
        int j = 1;
        for (int i = 1; i <= npairs; i++) {
            if (G[i]) {
                Rv[i] = v[j];
                j++;
            }
        }
        return Rv;
    }

    static public double[] computeRtx(double[] x, boolean[] G) {
        int npairs = G.length - 1;
        double[] Rtx = new double[npairs + 1];
        int j = 1;
        for (int i = 1; i <= npairs; i++) {
            if (G[i]) {
                Rtx[j] = x[i];
                j++;
            }
        }
        return Rtx;
    }


    /**
     * Computes Bx = A^{-T} Rx  where R is the identity matrix with columns restricted to indices in G.
     *
     * @param n  = number of taax
     * @param x  = vector of size |G|
     * @param G  = boolean array giving set G
     * @param Bx = vector of size n(n-1)/2; assumed to be already initialised.
     */
    static public void calculateBx(int n, double[] x, boolean[] G, double[] Bx) {
        int npairs = n * (n - 1) / 2;
        int nG = x.length - 1;
        double[] Rx = new double[npairs + 1];
        int j = 1;
        for (int i = 1; i <= npairs; i++)
            if (G[i]) {
                Rx[i] = x[j];
                j++;
            }
        circularAinvT(n, Rx, Bx);
    }

    /**
     * Computes B'x = R'A^{-1}x
     *
     * @param n   Number of taxa
     * @param x   Vector of dimension n(n-1)/2
     * @param G   Boolean array
     * @param Btx Vector of dimension |G|, assumed to be initialised.
     */
    static public void calculateBtx(int n, double[] x, boolean[] G, double[] Btx) {
        int npairs = n * (n - 1) / 2;
        int nG = Btx.length - 1;
        double[] Ainvx = new double[npairs + 1];
        circularSolve(n, x, Ainvx);
        int j = 1;
        for (int i = 1; i <= npairs; i++) {
            if (G[i]) {
                Btx[j] = Ainvx[i];
                j++;
            }
        }
    }


    /**
     * Converts a boolean mask in  a single 1..npairs vector into separate boolean
     * vectors for each block.
     *
     * @param n number of taxa
     * @param G boolean vector, size n(n-1)/2
     * @return array of boolean arrays, one for each block.
     */
    public static boolean[][] mask2blockmask(int n, boolean[] G) {

        //Allocate arrays, initialising as false.
        boolean[][] gcell = new boolean[n][];
        for (int i = 1; i <= n - 2; i++) {
            gcell[i] = new boolean[n - i];
            Arrays.fill(gcell[i], false);
        }
        gcell[n - 1] = new boolean[n];
        Arrays.fill(gcell[n - 1], false);

        //Transfer mask values into blco format.
        int index = 1;
        for (int i = 1; i <= n - 1; i++) {
            for (int j = i + 1; j <= n - 1; j++) {
                gcell[i][j - i] = G[index];
                index++;
            }
            gcell[n - 1][i] = G[index];
            index++;
        }
        return gcell;
    }


//    public class BlockXMatrix {
//        public int n;  //Number of taxa = one more than the number of blocks
//        public int[] m; //Array of block sizes.
//        public TridiagonalMatrix[] A; //Diagonal blocks: tridiagonal
//        public SparseRowMatrix[] B; //Off-diagonal blocks: sparse
//        public SparseRowMatrix[] C; //Blocks in the last row/column.
//        public boolean hasCorners; //Flag indicating wether the top right and bottom left entries of A[n-1] are present.
//
//        /**
//         * BlockXMatrix
//         * <p>
//         * Construct a full X matrix, with all rows and columns. This is a block matrix, specified in the pdf.
//         *
//         * @param n number of taxa.
//         */
//        public BlockXMatrix(int n) {
//            this.n = n;
//
//            //Construct array of block sizes
//            m = new int[n];
//            for (int i = 1; i <= n - 2; i++) {
//                m[i] = n - i - 1;
//            }
//            m[n - 1] = n - 1;
//
//            //Create the diagonal blocks
//            A = new TridiagonalMatrix[n];
//            for (int i = 1; i <= n - 1; i++) {
//                A[i] = new TridiagonalMatrix();
//                A[i].n = m[i];
//                A[i].a = new double[m[i] + 1];
//                A[i].a[1] = 0.75;
//                if (m[i] > 1)
//                    Arrays.fill(A[i].a, 2, m[i] + 1, 1.0);
//                if (i == n - 1)
//                    A[i].a[n - 1] = 0.75;
//                A[i].b = new double[m[i]];
//                Arrays.fill(A[i].b, 1, m[i], -0.5);
//                A[i].c = new double[m[i]];
//                Arrays.fill(A[i].c, 1, m[i], -0.5);
//            }
//
//            B = new SparseRowMatrix[n - 2];
//            for (int i = 1; i <= n - 3; i++) {
//                B[i] = new SparseRowMatrix(m[i + 1], m[i], 3);
//                for (int j = 1; j <= m[i + 1] - 1; j++) {
//                    B[i].ind[j][1] = j;
//                    B[i].ind[j][2] = j + 1;
//                    B[i].ind[j][3] = j + 2;
//                    B[i].val[j][1] = 0.25;
//                    B[i].val[j][2] = -0.5;
//                    B[i].val[j][3] = 0.25;
//                }
//                B[i].ind[m[i + 1]][1] = m[i + 1];
//                B[i].ind[m[i + 1]][2] = m[i + 1] + 1;
//                B[i].val[m[i + 1]][1] = 0.25;
//                B[i].val[m[i + 1]][2] = -0.5;
//            }
//
//            C = new SparseRowMatrix[n - 1];
//            //First block is a combination of a tridiagonal matrix and some extra bits
//            C[1] = new SparseRowMatrix(m[n - 1], m[1], 3);
//            C[1].ind[1][1] = 1;
//            C[1].ind[1][2] = n - 2;
//            C[1].ind[1][3] = 0;
//            C[1].ind[2][1] = 1;
//            C[1].ind[2][2] = 2;
//            C[1].ind[2][3] = n - 2;
//            C[1].val[1][1] = 0.25;
//            C[1].val[1][2] = -0.5;
//            C[1].val[1][3] = 0.0;
//            C[1].val[2][1] = -0.5;
//            C[1].val[2][2] = 0.25;
//            C[1].val[2][3] = 0.25;
//            for (int j = 3; j <= m[n - 1] - 1; j++) {
//                C[1].ind[j][1] = j - 2;
//                C[1].ind[j][2] = j - 1;
//                C[1].ind[j][3] = j;
//                C[1].val[j][1] = 0.25;
//                C[1].val[j][2] = -0.5;
//                C[1].val[j][3] = 0.25;
//            }
//            C[1].ind[m[n - 1]][1] = m[n - 1] - 2;
//            C[1].ind[m[n - 1]][2] = m[n - 1] - 1;
//            C[1].val[m[n - 1]][1] = 0.25;
//            C[1].val[m[n - 1]][2] = -0.5;
//
//            for (int i = 2; i <= n - 2; i++) {
//                C[i] = new SparseRowMatrix(m[n - 1], m[i], 1);
//                C[i].ind[i - 1][1] = m[i];
//                C[i].val[i - 1][1] = 0.25;
//                C[i].ind[i][1] = m[i];
//                C[i].val[i][1] = -0.5;
//                C[i].ind[i + 1][1] = m[i];
//                C[i].val[i+1][1] = 0.25;
//            }
//            hasCorners = true;
//        }
//
//        /**
//         * Construct a block X matrix, restricted to rows/columns specified in gcell
//         *
//         * @param n     number of taxa
//         * @param gcell (n-1) dimensional array of boolean arrays, indicating which rows/cols in each block to keep.
//         */
//        public BlockXMatrix(int n, boolean[][] gcell) {
//            BlockXMatrix X = new BlockXMatrix(n);
//            this.n = n;
//            A = new TridiagonalMatrix[n];
//            B = new SparseRowMatrix[n - 2];
//            C = new SparseRowMatrix[n - 1];
//            m = new int[n];
//
//            for (int i = 1; i <= n - 1; i++) {
//                A[i] = X.A[i].submatrix(gcell[i]);
//                m[i] = A[i].n;
//                if (i < n - 2)
//                    B[i] = X.B[i].submatrix(gcell[i + 1], gcell[i]);
//                if (i < n - 1)
//                    C[i] = X.C[i].submatrix(gcell[n - 1], gcell[i]);
//            }
//            hasCorners = gcell[n - 1][1] && gcell[n - 1][n-1];
//        }
//
//        public double[][] multiply(double[][] x) {
//            double[][] y = new double[n][];
//            for (int i = 1; i <= n - 2; i++) {
//                if (m[i] > 0) {
//                    double[] yi = A[i].multiply(x[i]);
//                    if (i > 1 && m[i - 1] > 0)
//                        yi = add(yi, B[i - 1].multiply(x[i - 1])); //TODO do this without reallocating yi
//                    if (i < n - 2 && m[i + 1] > 0)
//                        yi = add(yi, B[i].multiplyTranspose(x[i + 1]));
//                    if (m[n - 1] > 0)
//                        yi = add(yi, C[i].multiplyTranspose(x[n - 1]));
//                    y[i] = yi;
//                }
//            }
//
//            if (m[n - 1] > 0) {
//                double[] ylast = A[n - 1].multiply(x[n - 1]);
//                if (hasCorners) {
//                    ylast[1] += 0.25 * x[n - 1][m[n - 1]];
//                    ylast[m[n - 1]] += 0.25 * x[n - 1][1];
//                }
//                for (int i = 1; i <= n - 2; i++) {
//                    if (m[i] > 0)
//                        ylast = add(ylast, C[i].multiply(x[i]));
//                }
//                y[n - 1] = ylast;
//            }
//            return y;
//        }
//
//        /**
//         * Constructs a JAMA matrix version of the matrix X. Note this matrix is indexed 0,1,2...
//         * This method is for debugging only.
//         *
//         * @param n Number of taxa
//         * @param G boolean array, indexed 1,2,....,npairs
//         * @return Matrix
//         */
//        static private Matrix makeX(int n, boolean[] G) {
//            int npairs = n * (n - 1) / 2;
//
//            //First construct the full npairsxnpairs matrix
//            Matrix A = makeA(n);
//            Matrix B = A.inverse();
//            //Set all entries <0.2 in inv(A) to 0.
//            for (int i = 0; i < npairs; i++) {
//                for (int j = 0; j < npairs; j++) {
//                    if (B.get(i, j) < 0.2)
//                        B.set(i, j, 0);
//                }
//            }
//            Matrix XX = B.transpose().times(B);
//
//            //Restrict entries to those in G and move the last block to the end.
//            int[] lastBlock = new int[n - 1]; //Indices of rows to move to last block
//            int[] otherBlocks = new int[(n - 1) * (n - 2) / 2];
//            int lastk = 0;
//            int otherk = 0;
//
//            int ij = 0;
//            for (int i = 1; i <= n; i++) {
//                for (int j = i + 1; j <= n; j++) {
//                    if (G[ij + 1]) {
//                        if (j == n) {
//                            lastBlock[lastk] = ij;
//                            lastk++;
//                        } else {
//                            otherBlocks[otherk] = ij;
//                            otherk++;
//                        }
//                    }
//                    ij = ij + 1;
//                }
//            }
//            lastBlock = Arrays.copyOfRange(lastBlock, 0, lastk);
//            otherBlocks = Arrays.copyOfRange(otherBlocks, 0, otherk);
//
//
//            //XX = [XX(~toMove,~toMove) XX(~toMove,toMove);XX(toMove,~toMove) XX(toMove,toMove)];
//            Matrix X = new Matrix(npairs, npairs);
//            int m = otherk;
//            int mtotal = otherk + lastk;
//            X.setMatrix(0, m - 1, 0, m - 1, XX.getMatrix(otherBlocks, otherBlocks));
//            X.setMatrix(0, m - 1, m, mtotal - 1, XX.getMatrix(otherBlocks, lastBlock));
//            X.setMatrix(m, mtotal - 1, 0, m - 1, XX.getMatrix(lastBlock, otherBlocks));
//            X.setMatrix(m, mtotal - 1, m, mtotal - 1, XX.getMatrix(lastBlock, lastBlock));
//
//            return X;
//
//        }
//
//        /**
//         * Convert the matrix into a JAMA matrix (for debugging)
//         *
//         * @return Matrix
//         */
//        private Matrix toMatrix() {
//            int nrows = 0;
//            for (int i = 1; i <= n - 1; i++) {
//                nrows += m[i];
//            }
//
//            int[][] blocks = new int[n][];
//            int index = 0;
//            for (int i = 1; i <= n - 1; i++) {
//                blocks[i] = new int[m[i]];
//                for (int j = 1; j <= m[i]; j++) {
//                    blocks[i][j - 1] = index;
//                    index++;
//                }
//            }
//
//            Matrix M = new Matrix(nrows, nrows);
//            for (int i = 1; i <= n - 2; i++) {
//                if (m[i] > 0) {
//                    if (i > 1 && m[i - 1] > 0) {
//                        M.setMatrix(blocks[i], blocks[i - 1], B[i - 1].toMatrix());
//                    }
//                    M.setMatrix(blocks[i], blocks[i], A[i].toMatrix());
//                    if (i < n - 2 && m[i + 1] > 0) {
//                        M.setMatrix(blocks[i], blocks[i + 1], B[i].toMatrix().transpose());
//                    }
//                    if (m[n - 1] > 0)
//                        M.setMatrix(blocks[i], blocks[n - 1], C[i].toMatrix().transpose());
//                }
//            }
//            if (m[n - 1] > 0) {
//                for (int i = 1; i <= n - 2; i++) {
//                    if (m[i] > 0) {
//                        M.setMatrix(blocks[n - 1], blocks[i], C[i].toMatrix());
//                    }
//                }
//                M.setMatrix(blocks[n - 1], blocks[n - 1], A[n - 1].toMatrix());
//                if (hasCorners) {
//                    M.set(blocks[n - 1][0], nrows-1, 0.25);
//                    M.set(nrows-1, blocks[n - 1][0], 0.25);
//                }
//            }
//
//            return M;
//        }
//
//
//        static public void test(int n) {
//            int npairs = n * (n - 1) / 2;
//            Random rand = new Random();
//            rand.setSeed(100);
//
//            boolean[] G = new boolean[npairs + 1];
//            int nG = 0;
//            for (int i = 1; i <= npairs; i++) {
//                G[i] = rand.nextBoolean();
//                if (G[i])
//                    nG++;
//            }
//            //Arrays.fill(G, true);
//
//            boolean[][] gcell = DualPCG.mask2blockmask(n, G);
//            BlockXMatrix X = new BlockXMatrix(n, gcell);
//            Matrix XX = X.toMatrix();
//
//
//            //Print to Matlab
//            System.err.print("X = [");
//            for (int i=0;i<XX.getRowDimension();i++) {
//                for(int j=0;j<XX.getColumnDimension();j++) {
//                    System.err.print(XX.get(i,j) + " ");
//                }
//                if (i<XX.getRowDimension()-1)
//                    System.err.println(";");
//                else
//                    System.err.println("];");
//            }
//            System.err.print("G=[");
//            for(int i=1;i<G.length;i++) {
//                if (G[i])
//                    System.err.print("1 ");
//                else
//                    System.err.print("0 ");
//            }
//            System.err.println("]'==1;");
//
//            System.err.println("gcell = mask2blocks("+n+",G);");
//            System.err.println("X2 = maskX(makeXcomplete("+n+"),gcell);\nnorm(sparseX2full(X2)-X)\n");
//
//        }
//
//    }

//    static public void test(int n) throws CanceledException {
////        Random rand = new Random();
////
////        int npairs = n*(n-1)/2;
////        double[] d = new double[npairs+1];
////        for(int i=1;i<=npairs;i++)
////            d[i] = rand.nextDouble();
////
////        double[] y = circularBlockPivot(n,d);
//
//
//        //TODO (Starting Friday) Update this test and use it.
//
//
//        double[] d = new double[]{0, 20, 56, 66, 63, 36, 32, 32, 16, 17, 18, 18, 19, 12, 12, 13, 13, 16, 17, 1, 61, 69, 61, 41, 34, 33, 24, 24, 28, 28, 31, 25, 30, 30, 30, 31, 32, 21, 41, 57, 60, 63, 66, 62, 62, 61, 62, 64, 59, 58, 61, 59, 59, 60, 57, 61, 61, 65, 69, 66, 66, 65, 66, 67, 67, 68, 66, 67, 67, 68, 65, 59, 58, 72, 59, 61, 62, 61, 65, 62, 66, 64, 64, 64, 64, 62, 16, 41, 30, 29, 30, 31, 33, 29, 31, 28, 31, 32, 33, 35, 16, 26, 26, 30, 28, 40, 26, 25, 24, 23, 26, 25, 24, 26, 27, 27, 27, 38, 27, 26, 27, 26, 25, 26, 29, 3, 4, 4, 8, 8, 14, 12, 13, 15, 16, 15, 3, 3, 7, 10, 14, 12, 13, 15, 16, 16, 2, 8, 11, 13, 14, 14, 16, 17, 17, 8, 11, 15, 14, 13, 15, 16, 17, 11, 14, 13, 13, 15, 16, 18, 7, 6, 6, 10, 11, 11, 7, 7, 12, 11, 13, 4, 8, 10, 12, 4, 5, 12, 1, 15, 16};
//        n=20;
//        long startTime = System.currentTimeMillis();
//        NeighborNetSplits.NNLSParams params = new NeighborNetSplits.NNLSParams();
//        params.useBlockPivot=true;
//
//        double[] y = circularBlockPivot(n, d, new ProgressSilent(),params);
//        long finishTime = System.currentTimeMillis();
//        System.err.println("Block Pivot took "+ (finishTime-startTime)+ " milliseconds");
//
//        //Now test with the old algorithm
//        int npairs = n*(n-1)/2;
//        double[] W = new double[npairs];
//        for(int i=0;i<npairs;i++)
//            W[i] = 1.0;
//        double[] y2 = new double[npairs];
//
//        startTime = System.currentTimeMillis();
//
//        //NeighborNetSplits.runActiveConjugate(n,npairs,d,W,y2,reg,0);
//        finishTime = System.currentTimeMillis();
//        System.err.println("Old algorithm took "+ (finishTime-startTime)+ " milliseconds");
//
//        double ydiff = 0.0;
//        for(int i=0;i<npairs;i++) {
//            ydiff = ydiff + (y[i]-y2[i])*(y[i] - y2[i]);
//        }
//        System.err.println("Two solutions differed by 2-norm of " + Math.sqrt(ydiff));
//
//    }


}

