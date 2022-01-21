/*
 *  K2Pmodel.java Copyright (C) 2022 Daniel H. Huson
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


import splitstree6.algorithms.characters.characters2distances.utils.SaturatedDistancesException;

/**
 * K2P model
 * Miguel and David Bryant, 2005
 */
public class K2Pmodel extends NucleotideModel {

	/**
	 * Constructor taking the expected rate of transitions versus transversions (rather
	 * than the parameter kappa in Swofford et al, pg 435.)
	 * We first compute the corresponding kappa, fill in Q according to the standard model.
	 *
	 * @param TsTv
	 */
	public K2Pmodel(double TsTv) {
		final double[] basefreqs = {0.25, 0.25, 0.25, 0.25};

		/* We have the identity
		 *    TsTv = kappa/2
		 * which we solve to get kappa
		 */
		final double kappa = TsTv * 2;

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
	 * is this a group valued model
	 *
	 * @return true, if group valued model
	 */
	public boolean isGroupBased() {
		return true;
	}


	@Override
	public double exactDistance(double[][] F) throws SaturatedDistancesException {
		double P = F[0][2] + F[1][3] + F[2][0] + F[3][1];
		double Q = F[0][1] + F[0][3] + F[1][0] + F[1][2];
		Q += F[2][1] + F[2][3] + F[3][0] + F[3][2];
		double dist = 0.5 * mInverse(1 / (1 - (2 * P) - Q), getPropInvariableSites(), getGamma());
		dist += 0.25 * mInverse(1 / (1 - (2 * Q)), getPropInvariableSites(), getGamma());
		return dist;
	}

}

