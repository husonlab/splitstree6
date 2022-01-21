/*
 *  SubstitutionModel.java Copyright (C) 2022 Daniel H. Huson
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
 * Created on May 10, 2004
 *
 * To change the template for this generated file go to
 * Window - Preferences - Java - Code Generation - Code and Comments
 */
package splitstree6.models;

/**
 * Abstract substitution model
 *
 * @author Dave Bryant, 2005?
 */
public interface SubstitutionModel {

	/**
	 * Returns P_{ij}(t), probability of change to j at time t given i at time 0
	 *
	 * @param i
	 * @param j
	 * @param t
	 * @return P_{ij}(t), probability of change to j at time t given i at time 0
	 */
	double getP(int i, int j, double t);

	/**
	 * Returns  X_{ij}(t) = \pi_i P_{ij}(t) , probability of i at time 0 AND j at time t
	 *
	 * @param i
	 * @param j
	 * @param t
	 * @return X_{ij}(t) = \pi_i P_{ij}(t) , probability of i at time 0 AND j at time t
	 */
	double getX(int i, int j, double t);

	/**
	 * Get an entry in the Q matrix (can involve computation)
	 *
	 * @param i
	 * @param j
	 * @return Q[i][j]
	 */
	double getQ(int i, int j);

	/**
	 * Returns base frequency
	 *
	 * @param i
	 * @return base frequency of ith state
	 */
	double getPi(int i);

//    /**
//     * Returns a state random selected from the base frequencies.
//     *
//     * @param random
//     * @return state (int 0...nstates-1)
//     */
//    int randomPi(Random random);
//
//    /**
//     * Returns a state j from the distribution P_ij(t) with i = start.
//     *
//     * @param start
//     * @param t
//     * @param random
//     * @return Returns a state j from the distribution P_ij(t) with i = start.
//     */
//    int randomEndState(int start, double t, Random random);

	/**
	 * Returns, the rate \sum \pi_i Q_ii
	 *
	 * @return Returns \sum \pi_i Q_ii, the rate
	 */
	double getRate();

	/**
	 * Returns number of states in model
	 *
	 * @return number of states in model
	 */
	int getNstates();

	/**
	 * Is this model group valued (as in Szekely and Steel)
	 *
	 * @return true if model is group based.
	 */
	//boolean isGroupBased();
}

