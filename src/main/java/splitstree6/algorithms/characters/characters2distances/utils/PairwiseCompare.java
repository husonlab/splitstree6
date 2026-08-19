/*
 *  PairwiseCompare.java Copyright (C) 2024 Daniel H. Huson
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

package splitstree6.algorithms.characters.characters2distances.utils;

import splitstree6.algorithms.utils.SplitsException;
import splitstree6.data.CharactersBlock;
import splitstree6.data.parts.AmbiguityCodes;
import splitstree6.models.SubstitutionModel;

/**
 * Computes pairwise distances
 *
 * @author David Bryant and Daniel Huson, 2005, 2018
 */

public class PairwiseCompare { // todo: add support for character weights
	private final int numStates;
	private int numNotMissing;
	private final double[][] fCount; /* Stored as doubles, to handle ambiguities and character weights*/

	/**
	 * constructor
	 */

	// TODO: SOMETHING SLOWS DOWN THE ALGORITHM WHILE OPEN AMBIG. CHARACTERS. NEED TO FIND OUT!
	public PairwiseCompare(final CharactersBlock characters, final int i, final int j) throws SplitsException {
		numStates = characters.getStateSymbols().length();
		// The fCount matrix has rows and columns for missing and gap states as well
		fCount = new double[numStates + 2][numStates + 2];
		calculatePairwiseCompare(characters, i, j, false);
	}

	public PairwiseCompare(final CharactersBlock characters, final int i, final int j, boolean isIgnoreAmbiguous)
			throws SplitsException {
		numStates = characters.getStateSymbols().length();
		fCount = new double[numStates + 2][numStates + 2];
		calculatePairwiseCompare(characters, i, j, isIgnoreAmbiguous);
	}

	public void calculatePairwiseCompare(final CharactersBlock characters, final int i, final int j, boolean isIgnoreAmbiguous)
			throws SplitsException {
		final String states = characters.getStateSymbols(); // the states, so without the ambiguity codes
		// legal characters that are not states. In practice this is protein's 'b', 'z', 'x' and '*': the
		// nucleotide codes are non-states too, but they are expanded in the branch below and never reach the
		// lookup. Treating them as missing is what keeps a protein alignment containing X readable
		final String nonStates = characters.getNonStateSymbols();
		final char gapChar = characters.getGapCharacter();
		final char missingChar = characters.getMissingCharacter();
		final boolean isNucleotides = characters.getDataType().isNucleotides();

		final int gapIndex = numStates;
		final int missingIndex = numStates + 1;

		numNotMissing = 0;

		for (int k = 1; k <= characters.getNchar(); k++) {
			char ci = characters.get(i, k); // todo use final?
			char cj = characters.get(j, k);

			final double charWeight = characters.getCharacterWeight(k);

			// Does this site reach the states-by-states block of fCount, which is the only part getF() sums over?
			// numNotMissing used to be incremented here for every site whose two characters were neither gap nor
			// missing, which counted sites getF() then dropped: the ambiguous ones skipped just below, and the
			// ones whose mass lands in the gap or missing row. A caller multiplying a proportion from getF() by
			// numNotMissing therefore overcounted, and that is the defect that made HammingDistance wrong. Count
			// the site where the mass is actually added instead, so the two always agree.
			var contributes = false;

			//Handle ambiguous states?
			final boolean ambigI, ambigJ;
			if (isNucleotides) {
				ambigI = AmbiguityCodes.isAmbiguityCode(ci);
				ambigJ = AmbiguityCodes.isAmbiguityCode(cj);
			} else {
				ambigI = ambigJ = false;
			}

			if (ambigI || ambigJ) {
				if (isIgnoreAmbiguous)
					continue;
				// expand in the alphabet actually in use: the codes are written in terms of DNA, so for RNA data
				// 'y' would otherwise expand to "ct" and 't' is not one of RNA's symbols
				final var si = AmbiguityCodes.getNucleotides(ci, states);
				final var sj = AmbiguityCodes.getNucleotides(cj, states);

				//Two cases... if they are the same states, then this needs to be distributed
				//down the diagonal of F. Otherwise, average.

				if (si.equals(sj)) {
					var weight = 1.0 / si.length();
					for (int pos = 0; pos < si.length(); pos++) {
						final var base = si.charAt(pos);
						int statei = states.indexOf(base);
						// the two branches below throw on an unknown state; this one used to index fCount with -1
						if (statei < 0)
							throw new SplitsException("Position " + k + " for taxa " + i + ": code '" + ci
													  + "' expands to '" + base + "', which is not in the alphabet '" + states + "'");
						fCount[statei][statei] += weight * charWeight;
						contributes = true;
					}
				} else {
					var weight = 1.0 / (si.length() * sj.length());

					for (var x = 0; x < si.length(); x++) {
						for (var y = 0; y < sj.length(); y++) {
							final var cx = si.charAt(x);
							final var cy = sj.charAt(y);
							var stateX = states.indexOf(cx);
							var stateY = states.indexOf(cy);
							if (cx == gapChar) stateX = gapIndex;
							if (cx == missingChar) stateX = missingIndex;
							if (cy == gapChar) stateY = gapIndex;
							if (cy == missingChar) stateY = missingIndex;
							if (stateX >= 0 && stateY >= 0) {
								fCount[stateX][stateY] += weight * charWeight;
								if (stateX < numStates && stateY < numStates)
									contributes = true;
							} else {
								if (stateX < 0)
									throw new SplitsException("Position " + k + " for taxa " + i + ": invalid character '" + cx + "'");
								else if (stateY < 0)
									throw new SplitsException("Position " + k + " for taxa " + j + ": invalid character '" + cy + "'");
							}
						}
					}
				}
			} else {
				final int stateI;
				if (ci == gapChar)
					stateI = gapIndex;
				else if (ci == missingChar || nonStates.indexOf(ci) >= 0)
					stateI = missingIndex;
				else
					stateI = states.indexOf(ci);
				final int stateJ;
				if (cj == gapChar)
					stateJ = gapIndex;
				else if (cj == missingChar || nonStates.indexOf(cj) >= 0)
					stateJ = missingIndex;
				else
					stateJ = states.indexOf(cj);

				if (stateI >= 0 && stateJ >= 0) {
					fCount[stateI][stateJ] += charWeight;
					if (stateI < numStates && stateJ < numStates)
						contributes = true;
				} else {
					if (stateI < 0)
						throw new SplitsException("Position " + k + " for taxa " + i + ": invalid character '" + ci + "'");
					else // if (stateJ < 0)
						throw new SplitsException("Position " + k + " for taxa " + j + ": invalid character '" + cj + "'");
				}
			}

			if (contributes)
				numNotMissing++;
		}
	}

	/**
	 * Number of sites the two sequences were actually compared on, that is, the number that contributed to the
	 * states-by-states block of fCount. This is exactly the set of sites getF() normalises over, so with unit
	 * character weights it equals the sum of the matrix getF() is built from, and multiplying a proportion
	 * returned by getF() by this number gives the corresponding count of sites.
	 *
	 * @return numNotMissing
	 */
	public int getNumNotMissing() {
		return numNotMissing;
	}

	/**
	 * Number of states (the number of valid symbols)
	 *
	 * @return numStates
	 */
	public int getNumStates() {
		return numStates;
	}


	/**
	 * Returns matrix containing the number of sites for each kind of transition
	 *
	 * @return Fcound
	 */
	public double[][] getfCount() {
		return fCount;
	}

	/**
	 * Frequency matrix - returns matrix containing the proportion of each kind of site
	 *
	 * @return proportions, or null if the two sequences have nothing that can be compared
	 */
	public double[][] getF() {
		double Fsum = 0.0;
		for (int i = 0; i < getNumStates(); i++)
			for (int j = 0; j < getNumStates(); j++)
				Fsum += fCount[i][j];

		// Guard on Fsum itself rather than on numNotMissing. The two now count the same sites, but a site can
		// still carry weight 0, so Fsum can be 0 with sites compared, and the division below then filled F with
		// 0.0/0.0, that is NaN. That NaN went straight into the distance matrix, because FixUndefinedDistances
		// only tested for -1, and it also destroyed that class's replacement value, Math.max(x, NaN) being NaN.
		// Report the distance as undefined instead.
		if (Fsum <= 0.0)
			return null;

		final double[][] F = new double[getNumStates()][getNumStates()];
		for (int i = 0; i < getNumStates(); i++) {
			for (int j = 0; j < getNumStates(); j++) {
				F[i][j] =
						fCount[i][j] / Fsum;
			}
		}
		return F;
	}

	/**
	 * Returns negative log likelihood of a given F matrix and t value
	 *
	 * @return negative log likelihood [double]
	 */
	private double evalL(SubstitutionModel model, double[][] F, double t) {

		int numstates = model.getNstates();
		double logL = 0.0;
		for (int i = 0; i < numstates; i++) {
			for (int j = 0; j < numstates; j++) {
				if (F[i][j] != 0.0)
					logL += F[i][j] * Math.log(model.getX(i, j, t));
			}
		}
		return -logL;
	}

	/**
	 * golden section
	 */
	private double goldenSection(SubstitutionModel model, double[][] F, double tmin, double tmax) {
		final double GS_EPSILON = 0.000001;
		final double tau = 2.0 / (1.0 + Math.sqrt(5.0)); //Golden ratio

		double a = tmin;
		double b = tmax;
		double aa = a + (1.0 - tau) * (b - a);
		double bb = a + tau * (b - a);
		double faa = evalL(model, F, aa);
		double fbb = evalL(model, F, bb);

		while ((b - a) > GS_EPSILON) {
			if (faa < fbb) {
				b = bb;
				bb = aa;
				fbb = faa;
				aa = a + (1.0 - tau) * (b - a);
				faa = evalL(model, F, aa);
				//System.out.println("faa was the smallest at this iteration :" + faa);
			} else {
				a = aa;
				aa = bb;
				faa = fbb;
				bb = a + tau * (b - a);
				fbb = evalL(model, F, bb);
				//System.out.println("fbb was the smallest at this iteration :" + fbb);
			}
		}

		return b;
	}

	/**
	 * Max Likelihood Distance - returns maximum likelihood distance for a given substitution
	 * model.
	 *
	 * @param model Substitution model in use
	 * @return distance
	 * @throws SaturatedDistancesException distance undefined if saturated (distance more than 10 substitutions per site)
	 */
	public double mlDistance(SubstitutionModel model) throws SaturatedDistancesException {

		//TODO: Replace the golden arc method with Brent's algorithm
		final int nStates = model.getNstates();
		final double[][] fullF = getF();

		if (fullF == null)
			return -1;

		final double[][] F = new double[nStates][nStates];


		double k = 0.0;
		for (int i = 0; i < nStates; i++) {
			for (int j = 0; j < nStates; j++) {
				double Fij = fullF[i][j];
				F[i][j] = Fij;
				k += Fij;
			}
		}
		// the model may have fewer states than the characters block, so the copied sub-matrix can be empty even
		// when the full one is not; without this guard the rescale below is 0.0/0.0 and every entry becomes NaN
		if (k <= 0.0)
			return -1;

		for (int i = 0; i < nStates; i++) {
			for (int j = 0; j < nStates; j++) {
				F[i][j] /= k; /* Rescale so the entries sum to 1.0 */
			}
		}

		double t = goldenSection(model, F, 0.00000001, 2.0);
		if (t == 2.0) {
			t = goldenSection(model, F, 2.0, 10.0);
			if (t == 10.0) {
				throw new SaturatedDistancesException();
			}
		}
		return t * model.getRate();
	}

	public double bulmerVariance(double dist, double b) {
		if (getNumNotMissing() == 0)
			return -1; // undefined, rather than the infinity that dividing by zero would give
		return (Math.exp(2 * dist / b) * b * (1 - Math.exp(-dist / b)) * (1 - b + b * Math.exp(-dist / b))) / ((double) this.getNumNotMissing());
	}
}