/*
 *  F84Model.java Copyright (C) 2022 Daniel H. Huson
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
 * Created on Jun 9, 2004
 *
 * To change the template for this generated file go to
 * MainWindow&gt;Preferences&gt;Java&gt;Code Generation&gt;Code and Comments
 */
package splitstree6.models.nucleotideModels;

import splitstree6.algorithms.characters.characters2distances.utils.SaturatedDistancesException;

/**
 * @author bryant
 * <p/>
 * To change the template for this generated type comment go to
 * MainWindow&gt;Preferences&gt;Java&gt;Code Generation&gt;Code and Comments
 */
public class F84Model extends NucleotideModel {
	/**
	 * Constructor taking the expected rate of transitions versus transversions (rather
	 * than the parameter K in Swofford et al, pg 436.)
	 * We first compute the corresponding K, fill in Q according to the standard model/.
	 *
	 * @param baseFreqs
	 * @param TsTv
	 */
	public F84Model(double[] baseFreqs, double TsTv) {
		super();

		final double a = baseFreqs[0] * baseFreqs[2] + baseFreqs[1] * baseFreqs[3];
		final double b = (baseFreqs[0] * baseFreqs[2] / (baseFreqs[0] + baseFreqs[2])) + (baseFreqs[1] * baseFreqs[3] / (baseFreqs[1] + baseFreqs[3]));
		final double c = (baseFreqs[0] * baseFreqs[1] + baseFreqs[0] * baseFreqs[3]) + (baseFreqs[1] * baseFreqs[2] + baseFreqs[2] * baseFreqs[3]);

		/* We have the identity
		 *    a + bK =  TsTv * c
		 * which we solve to get K
		 */
		double K = (TsTv * c - a) / b;

		double[][] Q = new double[4][4];
		for (int i = 0; i < 4; i++) {
			for (int j = 0; j < 4; j++) {
				if (i != j) {
					Q[i][j] = baseFreqs[j];
				}
			}
		}
		double piR = baseFreqs[0] + baseFreqs[2];
		double piY = baseFreqs[1] + baseFreqs[3];
		Q[0][2] *= (1.0 + K / piR);
		Q[1][3] *= (1.0 + K / piY);
		Q[3][1] *= (1.0 + K / piR);
		Q[2][0] *= (1.0 + K / piY);

		setRateMatrix(Q, baseFreqs);
		normaliseQ();
	}

	@Override
	public double exactDistance(double[][] F) throws SaturatedDistancesException {
		final double[] baseFreq = getNormedBaseFreq();
		final double piA = baseFreq[0],
				piC = baseFreq[1],
				piG = baseFreq[2],
				piT = baseFreq[3];
		final double piR = piA + piG; //frequency of purines
		final double piY = piC + piT; //frequency of pyrimidines
		final double A = piC * piT / piY + piA * piG / piR;
		final double B = piC * piT + piA * piG;
		final double C = piR * piY;

		final double P = F[0][2] + F[1][3] + F[2][0] + F[3][1];
		final double Q = (F[0][1] + F[0][3] + F[1][0] + F[1][2]) + (F[2][1] + F[2][3] + F[3][0] + F[3][2]);

		return (-2.0 * A * mInverse(1.0 - P / (2.0 * A) - (A - B) * Q / (2.0 * A * C), getPropInvariableSites(), getGamma()))
			   + (2.0 * (A - B - C) * mInverse((1.0 - Q / (2.0 * C)), getPropInvariableSites(), getGamma()));
	}
}
