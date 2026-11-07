/*
 *  K2Pmodel.java Copyright (C) 2026 Daniel H. Huson
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

import splitstree6.algorithms.characters.characters2distances.utils.SaturatedDistancesException;

/**
 * the Kimura (1980) two-parameter model of nucleotide substitution: all four bases are equally frequent and
 * transitions (A to G, C to T and back) occur at kappa times the rate of transversions
 * Daniel Huson, 11.2026
 */
public class K2Pmodel extends NucleotideModel {
	/**
	 * constructor
	 *
	 * @param tsTvRatio expected ratio of the number of transitions to the number of transversions
	 */
	public K2Pmodel(double tsTvRatio) {
		var frequencies = new double[]{0.25, 0.25, 0.25, 0.25};
		// each base has one transition partner and two transversion partners, so the expected proportion of
		// transitions among all substitutions is kappa / (kappa + 2), and the requested ratio gives kappa = 2 ratio
		var kappa = 2.0 * tsTvRatio;
		var q = new double[4][4];
		for (var a = 0; a < 4; a++) {
			for (var b = 0; b < 4; b++) {
				if (a != b)
					q[a][b] = (HKY85model.isTransition(a, b) ? kappa : 1.0) * frequencies[b];
			}
		}
		setRateMatrix(q, frequencies);
		normaliseQ();
	}

	/**
	 * the Kimura two-parameter distance -1/2 log(1 - 2P - Q) - 1/4 log(1 - 2Q), where P and Q are the proportions
	 * of sites that differ by a transition and by a transversion, respectively. Under the model, 1 - 2P - Q and
	 * 1 - 2Q are the expected values of exp(-4 alpha t) and exp(-4 beta t) over the sites, so invariable sites and
	 * gamma-distributed rates enter by replacing the logarithm with the inverse moment-generating function
	 *
	 * @param F divergence matrix, F[a][b] is the proportion of sites showing state a in one sequence and b in the other
	 * @return distance
	 */
	@Override
	public double exactDistance(double[][] F) throws SaturatedDistancesException {
		var transitions = 0.0;
		var transversions = 0.0;
		for (var a = 0; a < 4; a++) {
			for (var b = 0; b < 4; b++) {
				if (a != b) {
					if (HKY85model.isTransition(a, b))
						transitions += F[a][b];
					else
						transversions += F[a][b];
				}
			}
		}
		var p = getPropInvariableSites();
		var gamma = getGamma();
		return -0.5 * mInverse(1.0 - 2.0 * transitions - transversions, p, gamma) - 0.25 * mInverse(1.0 - 2.0 * transversions, p, gamma);
	}

	@Override
	public boolean isGroupBased() {
		return true;
	}
}
