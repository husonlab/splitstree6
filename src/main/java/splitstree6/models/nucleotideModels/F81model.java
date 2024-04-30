/*
 *  F81model.java Copyright (C) 2024 Daniel H. Huson
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

/*
 * Created on 11-Jun-2004
 *
 * To change the template for this generated file go to
 * MainWindow&gt;Preferences&gt;Java&gt;Code Generation&gt;Code and Comments
 */
package splitstree6.models.nucleotideModels;

/**
 * F81Distance model
 */
public class F81model extends NucleotideModel {
	/**
	 * Constructor taking the base frequencies and building the
	 * Q matrix of Felsenstein's F81Distance model (1981).
	 */
	public F81model(double[] basefreqs) {

		final double[][] Q = new double[4][4];
		for (int i = 0; i < 4; i++) {
			for (int j = 0; j < 4; j++) {
				if (i != j) {
					Q[i][j] = basefreqs[j];
				}
			}
		}

		setRateMatrix(Q, basefreqs);
		normaliseQ();
	}

	/**
	 * get exact editDistance
	 *
	 * @return exact editDistance
	 */
	public double exactDistance(double[][] F) {
		final double[] freq = getNormedBaseFreq();
		final double piA = freq[0],
				piC = freq[1],
				piG = freq[2],
				piT = freq[3];

		final double B = 1.0 - ((piA * piA) + (piC * piC) + (piG * piG) + (piT * piT));
		double D = 1 - (F[0][0] + F[1][1] + F[2][2] + F[3][3]);
		return -B * mInverse(1 - D / B, getPropInvariableSites(), getGamma());
	}
}
