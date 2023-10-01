/*
 * GTRmodel.java Copyright (C) 2023 Daniel H. Huson
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

/*
 * Created on 11-Jun-2004
 *
 * To change the template for this generated file go to
 * MainWindow&gt;Preferences&gt;Java&gt;Code Generation&gt;Code and Comments
 */
package splitstree6.models.nucleotideModels;

import jama.EigenvalueDecomposition;
import jama.Matrix;
import splitstree6.algorithms.characters.characters2distances.utils.SaturatedDistancesException;

/**
 * @author Mig
 * <p/>
 * To change the template for this generated type comment go to
 * MainWindow&gt;Preferences&gt;Java&gt;Code Generation&gt;Code and Comments
 */
public class GTRmodel extends NucleotideModel {
	private final double[][] Q;

	/**
	 * General time reversible model
	 *
	 * @param basefreqs Takes a provisional Q matrix and the base frequencies. Under the GTR properties, the matrix
	 *                  Pi Q is symmetric. We enforce this as follows:
	 *                  FOR OFF-DIAGONAL
	 *                  First we construct X = PiQ
	 *                  Replace X by (X + X')/2.0,
	 *                  Fill in Q = Pi^{-1} X
	 *                  Equivalently, Q_ij <- (Pi_i Q_ij + Pi_j Q_ji)/2.0 * Pi_i^{-1}
	 *                  FOR DIAGONAL
	 *                  Q_ii <= -\sum_{j \neq i} Q_{ij}
	 */
	public GTRmodel(double[][] QMatrix, double[] basefreqs) {
		Q = new double[4][4];
		for (int i = 0; i < 4; i++) {
			double rowsum = 0.0;
			for (int j = 0; j < 4; j++) {
				if (i != j) {
					Q[i][j] = (basefreqs[i] * QMatrix[i][j] + basefreqs[j] * QMatrix[j][i]) / (2.0 * basefreqs[i]);
					rowsum += Q[i][j];
				}
			}
			Q[i][i] = -rowsum;
		}

		setRateMatrix(Q, basefreqs);
		normaliseQ();

	}

	@Override
	/* Exact distance - pg 456 in Swofford et al.
	 * Let Pi denote the diagonal matrix with base frequencies down the
	 * diagonal. The standard formula is
	 *
	 * dist = -trace(Pi log(Pi^{-1} F))
	 *
	 * The problem is that Pi^{-1}F will probably not be symmetric, so taking the
	 * logarithm is difficult. However we can use an alternative formula:
	 *
	 * dist = -trace(Pi^{1/2} log(Pi^{-1/2} (F'+F)/2 Pi^{-1/2}) Pi^{1/2} )
	 *
	 * Then we will be taking the log (or inverse MGF) of a symmetric matrix.
	 */
	public double exactDistance(double[][] F) throws SaturatedDistancesException {

		final int n = 4;
		final double[] sqrtpi = new double[n];
		final double[] baseFreq = getNormedBaseFreq();

		for (int i = 0; i < n; i++)
			sqrtpi[i] = Math.sqrt(baseFreq[i]);
		final Matrix X = new Matrix(n, n);

		for (int i = 0; i < 4; i++) {
			for (int j = 0; j <= i; j++) {
				double Xij = (F[i][j] + F[j][i]) / (2.0 * sqrtpi[i] * sqrtpi[j]);
				X.set(i, j, Xij);
				if (i != j)
					X.set(j, i, Xij);
			}
		}

		/* Compute M^{-1}(Q)  */
		final EigenvalueDecomposition EX = new EigenvalueDecomposition(X);
		final double[] D = EX.getRealEigenvalues();
		final double[][] V = (EX.getV().getArrayCopy());
		for (int i = 0; i < 4; i++)
			D[i] = mInverse(D[i], getPropInvariableSites(), getGamma());

		/* Now evaluate trace(pi^{1/2} V D V^T pi^{1/2}) */

		double dist = 0.0;
		for (int i = 0; i < 4; i++) {
			double x = 0.0;
			for (int j = 0; j < 4; j++) {
				x += baseFreq[i] * V[i][j] * V[i][j] * D[j];
			}
            dist -= x;
		}

		return dist;
	}
}
