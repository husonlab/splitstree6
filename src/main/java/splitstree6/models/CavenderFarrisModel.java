/*
 * CavenderFarrisModel.java Copyright (C) 2024 Daniel H. Huson
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

package splitstree6.models;

import java.util.Random;

/**
 * The Cavender Farris for binary sequences
 * Dave Bryant, 3.2006
 */
public class CavenderFarrisModel implements SubstitutionModel {

	private double tCurrent; //Current value of t.
	private double pChange; // probability of change for current t.

	public CavenderFarrisModel() {
		pChange = 0;
		tCurrent = 0.0;
	}


	private void computeP(double t) {
		pChange = 0.5 - 0.5 * Math.exp(-2.0 * t);
		tCurrent = t;
	}

	/**
	 * Returns P_{ij}(t), probability of change to j at time t given i at time 0
	 *
	 * @return P_{ij}(t), probability of change to j at time t given i at time 0
	 */
	public double getP(int i, int j, double t) {
		if (t != tCurrent)
			computeP(t);
		if (i != j)
			return pChange;
		else
			return 1.0 - pChange;
	}

	/**
	 * Get an entry in the Q matrix
	 *
	 * @return Q[i][j]
	 */
	public double getQ(int i, int j) {
		if (i == j)
			return -1;
		else
			return 1;
	}


	public double getX(int i, int j, double t) {
		if (t != tCurrent)
			computeP(t);
		if (i != j)
			return 0.5 * pChange;
		else
			return 0.5 * (1.0 - pChange);
	}

	public double getPi(int i) {
		return 0.5;
	}

	public int randomPi(Random random) {
		return random.nextInt(2);
	}

	public int randomEndState(int start, double t, Random random) {
		if (t != tCurrent)
			computeP(t);
		if (random.nextDouble() < pChange)
			return 1 - start;
		else
			return start;
	}

	public double getRate() {
		return 1;
	}

	public int getNstates() {
		return 2;
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
