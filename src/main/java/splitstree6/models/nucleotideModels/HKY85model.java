/*
 *  HKY85model.java Copyright (C) 2026 Daniel H. Huson
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

/**
 * the Hasegawa-Kishino-Yano (1985) model of nucleotide substitution: arbitrary base frequencies, with
 * transitions (A to G, C to T and back) occurring at kappa times the rate of transversions
 * Daniel Huson, 11.2026
 */
public class HKY85model extends NucleotideModel {
	/**
	 * constructor
	 *
	 * @param frequencies base frequencies, in the order A, C, G, T
	 * @param tsTvRatio   expected ratio of the number of transitions to the number of transversions
	 */
	public HKY85model(double[] frequencies, double tsTvRatio) {
		var kappa = computeKappa(frequencies, tsTvRatio);
		var q = new double[4][4];
		for (var a = 0; a < 4; a++) {
			for (var b = 0; b < 4; b++) {
				if (a != b)
					q[a][b] = (isTransition(a, b) ? kappa : 1.0) * frequencies[b];
			}
		}
		setRateMatrix(q, frequencies);
		normaliseQ();
	}

	/**
	 * computes the rate parameter kappa from the transition/transversion ratio. Under the model, the expected
	 * proportion of substitutions that are transitions is 2 kappa (piA piG + piC piT) and the proportion that
	 * are transversions is 2 (piA + piG)(piC + piT), so kappa = ratio (piA + piG)(piC + piT) / (piA piG + piC piT)
	 *
	 * @param pi        base frequencies, in the order A, C, G, T
	 * @param tsTvRatio transition/transversion ratio
	 * @return kappa
	 */
	public static double computeKappa(double[] pi, double tsTvRatio) {
		var transitionPairs = pi[0] * pi[2] + pi[1] * pi[3];
		var transversionPairs = (pi[0] + pi[2]) * (pi[1] + pi[3]);
		return tsTvRatio * transversionPairs / transitionPairs;
	}

	/**
	 * is the change from state a to state b (0=A, 1=C, 2=G, 3=T) a transition, that is, between the two
	 * purines A and G or between the two pyrimidines C and T?
	 */
	public static boolean isTransition(int a, int b) {
		return a != b && a % 2 == b % 2;
	}

	/**
	 * this model is used with maximum-likelihood distances only
	 */
	@Override
	public double exactDistance(double[][] F) {
		throw new UnsupportedOperationException("HKY85 model: no closed-form distance, use maximum-likelihood distances");
	}
}
