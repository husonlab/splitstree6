/*
 *  JCmodel.java Copyright (C) 2022 Daniel H. Huson
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
 * @author Miguel Jett�
 * June 10th 2004
 * <p/>
 * Jukes Cantor model of evolution.
 */

public class JCmodel extends NucleotideModel {
	public JCmodel() {
		super();

		double[] basefreqs = {0.25, 0.25, 0.25, 0.25};

		double[][] Q = new double[4][4];
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

	public double exactDistance(double[][] F) {
		final double D = 1 - (F[0][0] + F[1][1] + F[2][2] + F[3][3]);
		final double B = 0.75;
		return -B * mInverse(1 - D / B, getPropInvariableSites(), getGamma());
	}


	/**
	 * is this a group valued model
	 *
	 * @return true, if group valued model
	 */
	public boolean isGroupBased() {
		return true;
	}
}
