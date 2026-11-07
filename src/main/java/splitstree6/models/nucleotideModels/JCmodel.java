/*
 *  JCmodel.java Copyright (C) 2026 Daniel H. Huson
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
 * the Jukes-Cantor (1969) model of nucleotide substitution: all four bases are equally frequent and
 * every substitution occurs at the same rate
 * Daniel Huson, 11.2026
 */
public class JCmodel extends NucleotideModel {
	public JCmodel() {
		var frequencies = new double[]{0.25, 0.25, 0.25, 0.25};
		// a rate of one third per off-diagonal entry gives one expected substitution per unit time
		var q = new double[4][4];
		for (var a = 0; a < 4; a++) {
			for (var b = 0; b < 4; b++) {
				q[a][b] = (a == b ? -1.0 : 1.0 / 3.0);
			}
		}
		setRateMatrix(q, frequencies);
		normaliseQ();
	}

	/**
	 * the Jukes-Cantor distance -3/4 log(1 - 4/3 p), where p is the proportion of differing sites;
	 * invariable sites and gamma-distributed rates enter through the inverse moment-generating function
	 *
	 * @param F divergence matrix, F[a][b] is the proportion of sites showing state a in one sequence and b in the other
	 * @return distance
	 */
	@Override
	public double exactDistance(double[][] F) throws SaturatedDistancesException {
		var identical = 0.0;
		for (var a = 0; a < 4; a++) {
			identical += F[a][a];
		}
		var p = 1.0 - identical;
		return -0.75 * mInverse(1.0 - p / 0.75, getPropInvariableSites(), getGamma());
	}

	@Override
	public boolean isGroupBased() {
		return true;
	}
}
