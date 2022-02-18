package splitstree6.algorithms.distances.distances2splits.neighbornet.NeighborNetPCG;

import Jama.Matrix;

import java.util.Random;

import static splitstree6.algorithms.distances.distances2splits.neighbornet.NeighborNetPCG.BlockXMatrix.blocks2vector;
import static splitstree6.algorithms.distances.distances2splits.neighbornet.NeighborNetPCG.BlockXMatrix.vector2blocks;
import static splitstree6.algorithms.distances.distances2splits.neighbornet.NeighborNetPCG.TridiagonalMatrix.multiplyLU;
import static splitstree6.algorithms.distances.distances2splits.neighbornet.NeighborNetPCG.VectorUtilities.add;
import static splitstree6.algorithms.distances.distances2splits.neighbornet.NeighborNetPCG.VectorUtilities.minus;

public class Preconditioner {

	public BlockXMatrix X;

	public TridiagonalMatrix[] L;
	public TridiagonalMatrix[] U;
	public SparseRowMatrix[] Y;
	public SparseRowMatrix[] Z;

	/**
	 * Constructs the block conditioner, in O(n^2) time [linear in the number of variables].
	 *
	 * @param X     BlockXMatrix
	 * @param bands Number of bands used when computing the Y and Z matrices.
	 */
	public Preconditioner(BlockXMatrix X, int bands) {
		this.X = X; //TODO Check that this is the kind of copy/assignment that we want. I think copy by ref is ok here.
		int n = X.n;
		int[] m = X.m;

		L = new TridiagonalMatrix[n];
		U = new TridiagonalMatrix[n];
		Y = new SparseRowMatrix[n - 1];
		Z = new SparseRowMatrix[n - 1];
		if (m[1] > 0) {
			TridiagonalMatrix[] LU = X.A[1].trilu();
			L[1] = LU[0];
			U[1] = LU[1];
			Y[1] = X.C[1];
			Z[1] = X.C[1];
		}

		for (int i = 2; i <= n - 2; i++) {
			if (m[i] > 0) {
				if (m[i - 1] > 0) {
					TridiagonalMatrix T = multiplyLU(L[i - 1], U[i - 1]);
					T.preprocessInverse();

					TridiagonalMatrix T2 = tri_X_Tinv_Y(X.B[i - 1], T, X.B[i - 1]);
					TridiagonalMatrix[] LU = TridiagonalMatrix.minus(X.A[i], T2).trilu();
					L[i] = LU[0];
					U[i] = LU[1];

					SparseRowMatrix S2 = sparse_X_Tinv_Y(Y[i - 1], T, X.B[i - 1], false, bands);
					Y[i] = SparseRowMatrix.minus(X.C[i], S2, 4);
					S2 = sparse_X_Tinv_Y(Z[i - 1], T, X.B[i - 1], true, bands);
					Z[i] = SparseRowMatrix.minus(X.C[i], S2, 4);
				} else {
					TridiagonalMatrix[] LU = X.A[i].trilu();
					L[i] = LU[0];
					U[i] = LU[1];
					Y[i] = X.C[i];
					Z[i] = X.C[i];
				}
			}
		}
		if (m[n - 1] > 0) {
			TridiagonalMatrix YZ = X.A[n - 1].clone();
			for (int i = 1; i <= n - 2; i++) {
				if (m[i] > 0) {
					TridiagonalMatrix T = multiplyLU(L[i], U[i]);
					T.preprocessInverse();
					YZ = TridiagonalMatrix.minus(YZ, tri_X_Tinv_Y(Y[i], T, Z[i]));
				}
			}
			TridiagonalMatrix[] LU = YZ.trilu();
			L[n - 1] = LU[0];
			U[n - 1] = LU[1];

		}
	}


	/**
	 * Computes the matrix \tau(X*inv(T)*Y') where X,Y is sparse and T is triangular. Assumes that T.preprocessInverse() has
	 * already been called. Takes linear time
	 *
	 * @param X Sparse matrix
	 * @param T Tridiagonal matrix
	 * @param Y Sparse matrix
	 * @return Tridiagonal matrix formed from central three diagonals of X inv(T) Y'.
	 */
	private TridiagonalMatrix tri_X_Tinv_Y(SparseRowMatrix X, TridiagonalMatrix T, SparseRowMatrix Y) {
		int m = X.m;
		TridiagonalMatrix T2 = new TridiagonalMatrix();
		T2.n = m;
		T2.a = new double[m + 1];
		T2.b = new double[m];
		T2.c = new double[m];

		T2.a[1] = evalX_Tinv_Y(X, T, Y, 1, 1, false);
		for (int i = 2; i <= m; i++) {
			T2.a[i] = evalX_Tinv_Y(X, T, Y, i, i, false);
			T2.b[i - 1] = evalX_Tinv_Y(X, T, Y, i, i - 1, false);
			T2.c[i - 1] = evalX_Tinv_Y(X, T, Y, i - 1, i, false);
		}
		return T2;
	}

	/**
	 * Returns the sparse matrix equal to the central 2bands+1 of X * inv(T)*Y'.
	 *
	 * @param X                Sparse matrix
	 * @param T                Tridiagonal matrix (assumes preprocessInverse() already called)
	 * @param Y                Sparse matrix
	 * @param useTinvTranspose if true then uses inv(T') in place of inv(T)
	 * @param bands            Number of bands
	 * @return Sparse matrix equal to the central 2bands+1 of X * inv(T)*Y'.
	 */
	private SparseRowMatrix sparse_X_Tinv_Y(SparseRowMatrix X, TridiagonalMatrix T, SparseRowMatrix Y, boolean useTinvTranspose, int bands) {
		SparseRowMatrix S = new SparseRowMatrix(X.m, Y.m, 1 + 2 * bands);
		for (int i = 1; i <= S.m; i++) {
			int nnz = 0;
			for (int j = Math.max(i - bands, 1); j <= Math.min(i + bands, S.n); j++) {
				nnz++;
				S.ind[i][nnz] = j;
				S.val[i][nnz] = evalX_Tinv_Y(X, T, Y, i, j, useTinvTranspose);
			}
		}
		return S;
	}

	/**
	 * Returns element i,j of X * inv(T)*Y'.
	 *
	 * @param X                Sparse matrix
	 * @param T                Tridiagonal matrix
	 * @param Y                Sparse matrix
	 * @param i                integer index
	 * @param j                integer index
	 * @param useTinvTranspose if true, uses T' in place of T.
	 * @return entry of matrix X * inv(T)*Y'.
	 */
	private double evalX_Tinv_Y(SparseRowMatrix X, TridiagonalMatrix T, SparseRowMatrix Y, int i, int j, boolean useTinvTranspose) {
		double z = 0;
		for (int k1 = 1; k1 <= X.N; k1++) {
			int i1 = X.ind[i][k1];
			if (i1 != 0) {
				double x = X.val[i][k1];
				for (int k2 = 1; k2 <= Y.N; k2++) {
					int i2 = Y.ind[j][k2];
					if (i2 != 0) {
						double y = Y.val[j][k2];
						if (useTinvTranspose)
							z += x * T.getTinv(i2, i1) * y;
						else
							z += x * T.getTinv(i1, i2) * y;
					}
				}
			}
		}
		return z;
	}

	public double[][] solve(double[][] y) {
		return solveU(solveL(y));
	}

	/**
	 * Solves Mx = y.
	 * M is the preconditioning matrix, with rows/cols corresponding to elements of G.
	 *
	 * @param yvec vector y, given only as a vector with entries corresponding to entries of G
	 * @param G   set, boolean vector 1..npairs.
	 * @return vector x, given only as a vector with entries corresponding to entries of G
	 */
	public double[] solve(double[] yvec, boolean[] G) {
		int n = X.n;
		double[][] y, x;
		y = vector2blocks(n, yvec, G);
		x = solve(y);
		return blocks2vector(n, x, G);
	}

	/**
	 * Computes y=Mx
	 * @param x
	 * @return Mx
	 */
	public double[][] multiply(double[][] x) {
		return multiplyL(multiplyU(x));
	}

	/**
	 * Computes y = Mx.
	 * M is the preconditioning matrix, with rows/cols corresponding to elements of G.
	 *
	 *
	 * @param xvec vector x, indexed 1..|G|
	 * @param G set, boolean vector 1..npairs.
	 * @return vector indexed 1..|G|
	 */
	public double[] multiply(double[] xvec, boolean[] G) {
		int n = X.n;
		double[][] y, x;
		x = vector2blocks(n, xvec, G);
		y = multiply(x);
		return blocks2vector(n, y, G);
	}

	public double[][] multiplyL(double[][] v) {
		//y = Lv
		int n=X.n;
		double[][] y = new double[n][];

		for (int i=1;i<n-1;i++) {
			if (X.m[i]>0) {
				y[i]=L[i].multiply(v[i]);
				if (i>1 && X.m[i-1]>0) {
					y[i] = add(y[i],X.B[i-1].multiply(U[i-1].solveU(v[i-1])));
				}
			}
		}
		if (X.m[n-1]>0) {
			y[n-1] = L[n-1].multiply(v[n-1]);
			for (int j=1;j<n-1;j++) {
				if (X.m[j]>0)
					y[n-1] = add(y[n-1],Y[j].multiply(U[j].solveU(v[j])));
			}
		}
		return y;
	}

	public double[][] multiplyU(double[][] x){
		//v = Ux.
		int n=X.n;
		double[][] v = new double[n][];
		for (int i=1;i<n-1;i++) {
			if (X.m[i]>0) {
				v[i]=U[i].multiply(x[i]);
				if (i<n-2 && X.m[i+1]>0) {
					v[i] = add(v[i],L[i].solveL(X.B[i].multiplyTranspose(x[i+1])));
				}
				if (X.m[n-1]>0) {
					v[i] = add(v[i],L[i].solveL(Z[i].multiplyTranspose(x[n-1])));
				}
			}
		}
		if (X.m[n-1]>0) {
			v[n-1] = U[n-1].multiply(x[n-1]);
		}
		return v;
	}

	public double[][] solveL(double[][] y) {
		int n=X.n;
		double[][] eta = new double[n][];

		if (X.m[1] > 0) {
			eta[1] = L[1].solveL(y[1]);
		}
		for (int i = 2; i <= n - 2; i++) {
			if (X.m[i] > 0) {
				if (X.m[i - 1] > 0)
					//eta[i] = L[i]^{-1} (y[i] - B[i-1] U[i-1]^{-1} eta[i-1]
					eta[i] = L[i].solveL(minus(y[i], X.B[i - 1].multiply(U[i - 1].solveU(eta[i - 1]))));
				else
					//eta[i] = L[i]^{-1} y[i]
					eta[i] = L[i].solveL(y[i]);
			}
		}
		if (X.m[n - 1] > 0) {
			double[] v = y[n - 1].clone();
			for (int i = 1; i <= n - 2; i++) {
				if (X.m[i] > 0) {
					v = minus(v, Y[i].multiply(U[i].solveU(eta[i])));
				}
			}
			eta[n - 1] = L[n - 1].solveL(v);
		}
		return eta;
	}

	public double[][] solveU(double[][] eta) {
		int n=X.n;
		double[][] nu = new double[n][];

		if (X.m[n - 1] > 0) {
			nu[n - 1] = U[n - 1].solveU(eta[n - 1]);
		}
		for(int i=n-2;i>=1;i--) {
			if (X.m[i] > 0) {
				double[] v = eta[i].clone();
				if (i < n - 2 && X.m[i + 1] > 0)
					v = minus(v, L[i].solveL(X.B[i].multiplyTranspose(nu[i + 1])));
				if (X.m[n - 1] > 0)
					v = minus(v, L[i].solveL(Z[i].multiplyTranspose(nu[n - 1])));
				nu[i] = U[i].solveU(v);
			}
		}
		//TODO Some efficiency gains possible here - apply L[i].solveL once instead of twice
		return nu;
	}


	public static void main(String[] args) {
		int n=20;
		int npairs = n * (n - 1) / 2;
		Random rand = new Random();
		rand.setSeed(100);

		boolean[] G = new boolean[npairs + 1];
		int nG = 0;
		for (int i = 1; i <= npairs; i++) {
			G[i] = rand.nextBoolean();
			//G[i] = true;
			if (G[i])
				nG++;
		}
		//Arrays.fill(G, true);

		boolean[][] gcell = DualPCG.mask2blockmask(n, G);
		BlockXMatrix X = new BlockXMatrix(n, gcell);
		Preconditioner M = new Preconditioner(X,3);

		double[] v = new double[nG+1];
		for(int i=1;i<=nG;i++)
			v[i] = rand.nextDouble();

		double[][] vcell;
		vcell = vector2blocks(n, v, G);
		double[][] ucell = M.multiplyL(vcell);
		double[][] vcell2 = M.solveL(ucell);
		double[] v2 = blocks2vector(n,vcell2,G);

		double[][] ucell2 = M.multiplyU(vcell);
		double[][] vcell3 = M.solveU(ucell2);
		double[] v3 = blocks2vector(n,vcell3,G);

		double diff1 = 0.0, diff2=0.0;
		for(int i=1;i<=nG;i++) {
			diff1 += (v2[i] - v[i]) * (v2[i] - v[i]);
			diff2 += (v3[i] - v[i]) * (v3[i] - v[i]);
			System.err.println(i+"\t"+v[i]+"\t"+v2[i]+"\t"+v3[i]);
		}
		System.err.println("Testing preconditioner multiply and solve. Should be zero: "+diff1+"\t"+diff2);
		System.err.println();


	}
}
