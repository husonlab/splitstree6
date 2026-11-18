/*
 *  GTRmodel.java Copyright (C) 2026 Daniel H. Huson
 *
 *  (Some files contain contributions from other authors, who are then mentioned separately.)
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package splitstree6.models.nucleotideModels;

import jama.EigenvalueDecomposition;
import jama.Matrix;
import splitstree6.algorithms.characters.characters2distances.utils.SaturatedDistancesException;

/**
 * the general time-reversible model of nucleotide substitution (Tavaré 1986), in which the rate of change
 * from state a to state b is Q[a][b] = s[a][b] pi[b] for symmetric exchangeabilities s and base frequencies pi
 * Daniel Huson, 11.2026
 */
public class GTRmodel extends NucleotideModel {
	/**
	 * constructor. The supplied rate matrix need not be time reversible: its exchangeabilities
	 * rates[a][b] / frequencies[b] are averaged with those of the reverse changes, which makes them symmetric
	 *
	 * @param rates       rate matrix, in the order A, C, G, T; the diagonal is ignored
	 * @param frequencies base frequencies, in the order A, C, G, T
	 */
	public GTRmodel(double[][] rates, double[] frequencies) {
		var q = new double[4][4];
		for (var a = 0; a < 4; a++) {
			for (var b = 0; b < 4; b++) {
				if (a != b) {
					var exchangeability = 0.5 * (rates[a][b] / frequencies[b] + rates[b][a] / frequencies[a]);
					q[a][b] = exchangeability * frequencies[b];
				}
			}
		}
		setRateMatrix(q, frequencies);
		normaliseQ();
	}

	/**
	 * the general time-reversible distance d = -trace(Pi log(Pi^{-1} F)) (Rodríguez et al. 1990), where F is the
	 * divergence matrix, symmetrized, and Pi is the diagonal matrix of the base frequencies of the two sequences.
	 * The rate matrix and the base frequencies of the model do not enter, they are used for maximum-likelihood
	 * distances only.
	 * The matrix Pi^{-1/2} F Pi^{-1/2} is symmetric and has the same eigenvalues as Pi^{-1} F, so we diagonalize
	 * it as V L V^T and obtain the distance as -sum_a pi[a] sum_k V[a][k]^2 g(L[k]), where g is the logarithm,
	 * or more generally the inverse moment-generating function that accounts for invariable sites and
	 * gamma-distributed rates
	 *
	 * @param F divergence matrix, F[a][b] is the proportion of sites showing state a in one sequence and b in the other
	 * @return distance
	 */
	@Override
	public double exactDistance(double[][] F) throws SaturatedDistancesException {
		// the base frequencies are those of the two sequences, that is, the marginals of the symmetrized divergence
		// matrix: with any other frequencies the formula does not give zero for two identical sequences
		var pi = new double[4];
		for (var a = 0; a < 4; a++) {
			for (var b = 0; b < 4; b++) {
				pi[a] += 0.5 * (F[a][b] + F[b][a]);
			}
		}
		// leave out bases that occur in neither sequence, they would give a division by zero
		var present = new int[4];
		var n = 0;
		for (var a = 0; a < 4; a++) {
			if (pi[a] > 0.0)
				present[n++] = a;
		}

		var symmetric = new Matrix(n, n);
		for (var a = 0; a < n; a++) {
			for (var b = 0; b < n; b++) {
				var x = present[a];
				var y = present[b];
				symmetric.set(a, b, 0.5 * (F[x][y] + F[y][x]) / Math.sqrt(pi[x] * pi[y]));
			}
		}

		var eigen = new EigenvalueDecomposition(symmetric);
		var eigenvalues = eigen.getRealEigenvalues();
		var eigenvectors = eigen.getV().getArray();

		var distance = 0.0;
		for (var k = 0; k < n; k++) {
			var transformed = mInverse(eigenvalues[k], getPropInvariableSites(), getGamma());
			for (var a = 0; a < n; a++) {
				distance -= pi[present[a]] * eigenvectors[a][k] * eigenvectors[a][k] * transformed;
			}
		}
		return distance;
	}
}
