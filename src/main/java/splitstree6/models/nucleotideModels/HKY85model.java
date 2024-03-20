/*
 *  HKY85model.java Copyright (C) 2024 Daniel H. Huson
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
 * @author Miguel Jette, 2004
 */
public class HKY85model extends NucleotideModel {
	/**
	 * Constructor taking the expected rate of transitions versus transversions (rather
	 * than the parameter kappa in Swofford et al, pg 436.)
	 * We first compute the corresponding kappa, fill in Q according to the standard model.
	 */
	public HKY85model(double[] basefreqs, double TsTv) {
		final double a = basefreqs[0] * basefreqs[2] + basefreqs[1] * basefreqs[3];
		final double b = (basefreqs[0] * basefreqs[1] + basefreqs[0] * basefreqs[3])
						 + (basefreqs[1] * basefreqs[2] + basefreqs[2] * basefreqs[3]);

		/* We have the identity
		 *    a * kappa =  TsTv * b
		 * which we solve to get kappa
		 */
		final double kappa = (TsTv * b) / a;

		final double[][] Q = new double[4][4];
		for (int i = 0; i < 4; i++) {
			for (int j = 0; j < 4; j++) {
				if (i != j) {
					Q[i][j] = basefreqs[j];
				}
			}
		}
		Q[0][2] *= kappa;
		Q[1][3] *= kappa;
		Q[3][1] *= kappa;
		Q[2][0] *= kappa;

		setRateMatrix(Q, basefreqs);
		normaliseQ();
	}

	/**
	 * no exact distance associated with this model
	 */
	public double exactDistance(double[][] F) {
		throw new RuntimeException("exactDistance: not implemented");
	}
}

