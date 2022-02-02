/*
 * CircularSplitAlgorithms.java Copyright (C) 2022 Daniel H. Huson
 *
 * (Some files contain contributions from other authors, who are then mentioned separately.)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package splitstree6.algorithms.distances.distances2splits.neighbornet.NeighborNetPCG;

import Jama.Matrix;

import java.util.Random;

public class CircularSplitAlgorithms {

	/**
	 * Computes A*x where A is the matrix for a full circular split system. The indices of the rows and columns
	 * of A and x correspond to an ordering of pairs (1,2),(1,3),...,(1,n),(2,3),...,(2,n),...,(n-1,n).
	 * In A we have A{(i,j)(k,l)} = 1 if i and j are on opposite sides of the split {k,k+1,...,l-1}|...
	 * This algorithm runs in O(n^2) time, which is the number of entries of x.
	 *
	 * @param n Number of taxa.
	 * @param x vector with dimension n(n-1)/2
	 */
	static public void circularAx(int n, double[] x, double[] d) {
		int npairs = n * (n - 1) / 2;
		//double[] d = new double[npairs+1];

		//First compute d[i][i+1] for all i.
		int dindex = 1; //index of (i,i+1)
		for (int i = 1; i <= n - 1; i++) {
			double d_ij = 0;
			int index = i;
			//Sum over weights of splits (1,i+1), (2,i+1),...(i,i+1)
			for (int k = 1; k <= i; k++) {
				d_ij += x[index]; //split (k,i+1)
				index += (n - k - 1);
			}
			//Sum over weights of splits (i+1,i+2), (i+1,i+3),...(i+1,n)
			index = dindex + n - i;
			for (int j = i + 2; j <= n; j++) {
				d_ij += x[index]; //split (i+1,j)
				index++;
			}
			d[dindex] = d_ij;
			dindex += (n - i);
		}
		//Now compute d[i][i+2] for all i.
		int index = 2; //pair (1,3)
		for (int i = 1; i <= n - 2; i++) {
			//d[i ][i+2] = d[i ][i+1] + d[i + 1][i + 2] - 2 * x[i+1][i+2];
			d[index] = d[index - 1] + d[index + n - i - 1] - 2 * x[index + n - i - 1];
			index += n - i;
		}

		//Now loop through remaining pairs.
		for (int k = 3; k <= n - 1; k++) {
			index = k; //Pair (1,k+1)
			for (int i = 1; i <= n - k; i++) {
				//pair (i,i+k)
				//d[i][j] = d[i][j - 1] + d[i+1][j] - d[i+1][j-1] - 2.0 * b[i+1][j];
				d[index] = d[index - 1] + d[index + n - i - 1] - d[index + n - i - 2] - 2 * x[index + n - i - 1];
				index = index + n - i;
			}
		}

	}

	/**
	 * Computes A'*x where A is the matrix for a full circular split system. The indices of the rows and columns
	 * of A and x correspond to an ordering of pairs (1,2),(1,3),...,(1,n),(2,3),...,(2,n),...,(n-1,n).
	 * In A we have A{(i,j)(k,l)} = 1 if i and j are on opposite sides of the split {k,k+1,...,l-1}|...
	 * This algorithm runs in O(n^2) time, which is the number of entries of x.
	 *
	 * @param n  Number of taxa.
	 * @param x  vector with dimension n(n-1)/2 +1
	 * @param p, vector assumed to be of size n(n-1)/2. Overwritten by A'x.
	 */
	static public void circularAtx(int n, double[] x, double[] p) {
		int npairs = n * (n - 1) / 2;
		//double[] p = new double[npairs+1];

		//First compute trivial splits
		int sIndex = 1;
		for (int i = 1; i <= n - 1; i++) {
			//sIndex is pair (i,i+1)
			int xindex = i - 1;  //Index (1,i)
			double total = 0.0;
			for (int j = 1; j <= i - 1; j++) {
				total += x[xindex]; //pair (j,i)
				xindex = xindex + n - j - 1;
			}
			xindex++;
			for (int j = i + 1; j <= n; j++) {
				total += x[xindex]; //pair(i,j)
				xindex++;
			}
			p[sIndex] = total;
			sIndex = xindex;
		}

		sIndex = 2;
		for (int i = 1; i <= n - 2; i++) {
			//p[i][i+2] = p[i][i+1] + p[i + 1][i + 2] - 2 * x[i][i + 1];
			p[sIndex] = p[sIndex - 1] + p[sIndex + n - i - 1] - 2 * x[sIndex - 1];
			sIndex += (n - i);
		}

		for (int k = 3; k <= n - 1; k++) {
			sIndex = k;
			for (int i = 1; i <= n - k; i++) {
				//Index = i(i+k)
				//p[i][j] = p[i][j - 1] + p[i+1][j] - p[i+1][j - 1] - 2.0 * x[i][j-1];
				p[sIndex] = p[sIndex - 1] + p[sIndex + n - i - 1] - p[sIndex + n - i - 2] - 2.0 * x[sIndex - 1];
				sIndex += (n - i);
			}
		}
	}

	/**
	 * Computes A\x where A is the matrix for a full circular split system. The indices of the rows and columns
	 * of A and y correspond to an ordering of pairs (1,2),(1,3),...,(1,n),(2,3),...,(2,n),...,(n-1,n).
	 * In A we have A{(i,j)(k,l)} = 1 if i and j are on opposite sides of the split {k,k+1,...,l-1}|...
	 * This algorithm runs in O(n^2) time, which is the number of entries of x.
	 *
	 * @param n Number of taxa.
	 * @param y vector with dimension n(n-1)/2
	 */
	static public void circularSolve(int n, double[] y, double[] x) {
		int npairs = n * (n - 1) / 2;

		int index = 1;
		//x[1,2]= (y[1,2]+y[1,n] - y[2,n])/2
		x[index] = (y[index] + y[n - 1] - y[2 * n - 3]) / 2.0; //(1,2).
		index++;
		for (int j = 3; j <= n - 1; j++) {
			//x[1,j] = (y[1,j]+y[j-1,n] - y[1,j-1] - y[j,n])/2
			x[index] = (y[index] + y[(2 * n - j) * (j - 1) / 2] - y[index - 1] - y[j * (2 * n - j - 1) / 2]) / 2.0;
			index++;
		}
		//x[1,n] = (y(1,n) + y(n-1,n) - y(1,n-1))/2
		x[index] = (y[n - 1] + y[n * (n - 1) / 2] - y[n - 2]) / 2.0; //(1,n)
		index++;

		for (int i = 2; i <= n - 1; i++) {
			//x[i,i+1] = (y[i][i+1] + y[i-1][i] - y[i-1,i+1])/2
			x[index] = (y[index] - y[index - n + i] + y[index - n + i - 1]) / 2.0;
			index++;
			for (int j = i + 2; j <= n; j++) {
				// x[i][j] = ( y[i,j] + y[i-1,j-1] - y[i,j-1] - y[i-1][j])
				x[index] = (y[index] - y[index - 1] + y[index - n + i - 1] - y[index - n + i]) / 2.0;
				index++;
			}
		}
	}

	/**
	 * Computes inv(A)' * x  where A is the matrix for a full circular split system. The indices of the rows and columns
	 * of A and x correspond to an ordering of pairs (1,2),(1,3),...,(1,n),(2,3),...,(2,n),...,(n-1,n).
	 * In A we have A{(i,j)(k,l)} = 1 if i and j are on opposite sides of the split {k,k+1,...,l-1}|...
	 * This algorithm runs in O(n^2) time, which is the number of entries of x.
	 *
	 * @param n Number of taxa.
	 * @param x vector with dimension n(n-1)/2
	 */
	static public void circularAinvT(int n, double[] x, double[] y) {
		int npairs = n * (n - 1) / 2;

		//Suppose B = inv(A). Evaluates B*x column by column:
		// B*x = \sum_{ij} B(:,ij) * x(ij)

		y[1] = 0.5 * x[1];
		y[n - 1] = 0.5 * x[1];
		y[2 * n - 3] = -0.5 * x[1];

		int ij = 2;
		for (int j = 3; j <= n - 1; j++) {
			//pair ij = (1,j)
			//Nonzero in column: (j-1,n), (1,j),-(j,n),-(1,j-1)
			y[(j - 1) * (2 * n - j) / 2] += 0.5 * x[ij];
			y[ij] += 0.5 * x[ij];
			y[j * (2 * n - j - 1) / 2] += -0.5 * x[ij];
			y[ij - 1] += -0.5 * x[ij];
			ij++;
		}

		y[n - 1] += 0.5 * x[ij];
		y[npairs] += 0.5 * x[ij];
		y[n - 2] += -0.5 * x[ij];
		ij++;

		for (int i = 2; i <= n - 1; i++) {
			//(i,i+1)
			y[ij + i - n - 1] += 0.5 * x[ij];
			y[ij] += 0.5 * x[ij];
			y[ij + i - n] += -0.5 * x[ij];
			ij++;

			for (int j = i + 2; j <= n; j++) {
				y[ij + i - n - 1] += 0.5 * x[ij];
				y[ij] += 0.5 * x[ij];
				y[ij + i - n] += -0.5 * x[ij];
				y[ij - 1] += -0.5 * x[ij];
				ij++;
			}
		}
	}

	/**
	 * Constructs 0-1 design matrix for a full collection of circular splits. Note: JAMA matrix index from 0,1,2,...
	 *
	 * @param n int, ntaxa.
	 * @return Matrix
	 */
	static public Matrix makeA(int n) {
		int npairs = n * (n - 1) / 2;
		Matrix A = new Matrix(npairs, npairs);
		int ij = 1;
		for (int i = 1; i <= n; i++) {
			for (int j = i + 1; j <= n; j++) {
				int kl = 1;
				for (int k = 1; k <= n; k++) {
					for (int l = k + 1; l <= n; l++) {
						if (i < k && k <= j && j < l)
							A.set(ij - 1, kl - 1, 1);
						else if (k <= i && i < l && l <= j)
							A.set(ij - 1, kl - 1, 1);
						else
							A.set(ij - 1, kl - 1, 0);
						kl++;
					}
				}
				ij++;
			}
		}
		return A;
	}

	static public void test(int n) {
		//Test Ax.
		int npairs = n * (n - 1) / 2;
		double[] x = new double[npairs + 1];
		Random rand = new Random();
		for (int i = 1; i <= npairs; i++)
			x[i] = rand.nextDouble();
		double[] y = new double[npairs + 1];
		circularAx(n, x, y);

		Matrix xJ = new Matrix(npairs, 1);
		for (int i = 1; i <= npairs; i++)
			xJ.set(i - 1, 0, x[i]);
		Matrix A = makeA(n);

		Matrix yJ = A.times(xJ);
		double[] y2 = new double[npairs + 1];
		for (int i = 1; i <= npairs; i++)
			y2[i] = yJ.get(i - 1, 0);

		double err = VectorUtilities.diff(y, y2);
		System.err.println("Compare CircularAx, err = " + err);

		circularAinvT(n, x, y);
		yJ = (A.transpose()).inverse().times(xJ);
		for (int i = 1; i <= npairs; i++)
			y2[i] = yJ.get(i - 1, 0);
		err = VectorUtilities.diff(y, y2);
		System.err.println("Compare CircularAinvTx, err = " + err);


		circularAtx(n, x, y);
		yJ = (A.transpose()).times(xJ);
		for (int i = 1; i <= npairs; i++)
			y2[i] = yJ.get(i - 1, 0);
		err = VectorUtilities.diff(y, y2);
		System.err.println("Compare CircularATx, err = " + err);

		circularSolve(n, x, y);
		yJ = (A.inverse()).times(xJ);
		for (int i = 1; i <= npairs; i++)
			y2[i] = yJ.get(i - 1, 0);
		err = VectorUtilities.diff(y, y2);
		System.err.println("Compare CircularSolve, err = " + err);


	}


}
