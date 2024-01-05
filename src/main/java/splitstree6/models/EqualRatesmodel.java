/*
 * EqualRatesmodel.java Copyright (C) 2024 Daniel H. Huson
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
 * Implements the equals rates model for an arbitrary number of sites.
 * <p/>
 * Takes base frequencies   pi[i], i=0...nstates-1.
 * The generator is   Q
 * where
 * mu Q_ij = pi[j]
 * for i \neq j. Here mu is chosen to give rate 1. That is
 * <p/>
 * 1 = \sum_i \sum_{j \neq i} mu \pi_i pi_j = mu \sum_i pi_i (1-pi_i) = mu(1 - \sum_i pi_i^2)
 * <p/>
 * So
 * <p/>
 * mu = 1/(1 - \sum pi_i^2)
 * <p/>
 * <p/>
 * <p/>
 * The transition probabilities have a closed form:
 * <p/>
 * P_ij = pi_j (1 - exp(- mu t))  for j \neq i, with
 * P_ii = pi_i + (1-pi_i) exp(-mu t))
 */
public class EqualRatesmodel implements SubstitutionModel {

	private double[] pi;
	private final double mu;
	private final int nstates;

	private double tCurrent; //Current value of t.

	public EqualRatesmodel(int n) {
		nstates = n;
		for (int i = 0; i < n; i++)
			pi[i] = 1.0 / n;
		mu = (double) n / (n - 1);
	}

	public EqualRatesmodel(double[] pi) {
		nstates = pi.length;
		double total = 0.0;
		for (double aPi : pi) total += aPi;
		this.pi = new double[nstates];
		double sumSquares = 0;
		for (int i = 0; i < nstates; i++) {
			this.pi[i] = pi[i];
			sumSquares += pi[i] * pi[i];
		}
		mu = 1.0 / (1.0 - sumSquares);
	}


	private void computeP(double t) {
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

		if (i == j)
			return pi[i] + (1 - pi[i]) * Math.exp(-mu * t);
		else
			return pi[j] * (1 - Math.exp(-mu * t));
	}

	/**
	 * Get an entry in the Q matrix
	 *
	 * @return Q[i][j]
	 */
	public double getQ(int i, int j) {
		if (i == j)
			return pi[i] - 1;
		else
			return pi[j];
	}


	public double getX(int i, int j, double t) {
		if (t != tCurrent)
			computeP(t);
		return pi[i] * getP(i, j, t);

	}

	public double getPi(int i) {
		return pi[i];
	}

	public int randomPi(Random random) {
		double r = random.nextDouble();
		int i = 0;
		r -= pi[0];
		while (i < nstates - 1 && r >= 0) {
			i++;
			r -= pi[i];
		}
		return i;
	}

	public int randomEndState(int start, double t, Random random) {
		if (t != tCurrent)
			computeP(t);
		double r = random.nextDouble();
		int i = 0;
		r -= getP(start, 0, t);
		while (i < nstates - 1 && r >= 0) {
			i++;
			r -= getP(start, i, t);
		}
		return i;
	}

	public double getRate() {
		return 1;
	}

	public int getNstates() {
		return nstates;
	}

	/**
	 * is this a group valued model
	 *
	 * @return true, if group valued model
	 */
	public boolean isGroupBased() {
		return false;
	}
}
