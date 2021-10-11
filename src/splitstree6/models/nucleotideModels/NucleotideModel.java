/*
 * NucleotideModel.java Copyright (C) 2021. Daniel H. Huson
 *
 * (Some code written by other authors, as named in code.)
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
 *
 */

/*
 * Created on May 10, 2004
 *
 * To change the template for this generated file go to
 * Window - Preferences - Java - Code Generation - Code and Comments
 */
package splitstree6.models.nucleotideModels;

import Jama.EigenvalueDecomposition;
import Jama.Matrix;
import jloda.fx.window.NotificationManager;
import jloda.util.CanceledException;
import jloda.util.ProgressListener;
import splitstree6.algorithms.characters.characters2distances.utils.PairwiseCompare;
import splitstree6.algorithms.characters.characters2distances.utils.SaturatedDistancesException;
import splitstree6.data.CharactersBlock;
import splitstree6.data.DistancesBlock;
import splitstree6.models.SubstitutionModel;
import splitstree6.utils.SplitsException;

/**
 * @author bryant
 * <p/>
 * Generic 4x4 nucleotide model, for a general Q matrix.
 * <p/>
 * We are given the Q matrix, which is assumed to be a valid GTR rate matrix.
 */
public abstract class NucleotideModel implements SubstitutionModel {

	private final static double EPSILON = 1e-6; //Threshold for round-off error when checking matrices

	private double[] freqs; /* base frequencies */
	private double[] sqrtf; /* SquareShape roots of frequencies */
	private double[] evals; /* evalues of Pi^(1/2) Q Pi^(-1/2) */
	private double[][] evecs; /* evectors of Pi^(1/2) Q Pi^(-1/2) */

	private double[][] Pmatrix; /* Current P matrix */
	private double[][] Qmatrix; /* Current Q matrix */
	private double tval;
	private double propInvariableSites; /* Proportion of invariant sites */
	private double gamma = 0.0;

	/**
	 * computes the exact distance.
	 *
	 * @param F
	 * @return exact distance
	 */
	abstract public double exactDistance(double[][] F);

	/**
	 * Get the base frequency for state i (ranging from 0 to 3)
	 *
	 * @param i state (0..3)
	 * @return base frequency for state i.
	 */
	public double getPi(int i) {
		return freqs[i];
	}

	/**
	 * Set the rate matrix and base frequencies and compute diagonalisation
	 *
	 * @param Q               rate matrix (0..3 x 0..3). Diagonal values are ignored
	 * @param baseFrequencies (0..3)
	 */
	public void setRateMatrix(double[][] Q, double[] baseFrequencies) {
		//Test GTR property.
		for (int i = 0; i < 4; i++) {
			for (int j = i + 1; j < 4; j++) {
				if (Math.abs(baseFrequencies[i] * Q[i][j] - baseFrequencies[j] * Q[j][i]) > EPSILON)
					throw new IllegalArgumentException("Rate matrix and frequencies do not satisfy detailed balance condition");
			}
		}

		freqs = new double[4];
		sqrtf = new double[4];
		for (int i = 0; i < 4; i++) {
			freqs[i] = baseFrequencies[i];
			this.sqrtf[i] = Math.sqrt(freqs[i]);
		}

		Qmatrix = new double[4][4];
		for (int i = 0; i < 4; i++) {
			double qsum = 0.0;
			for (int j = 0; j < 4; j++) {
				if (i != j) {
					qsum += Q[i][j];
					Qmatrix[i][j] = Q[i][j];
				}

			}
			Qmatrix[i][i] = -qsum;
		}

		final Matrix M = new Matrix(4, 4);

        /* The matrix \Pi Q is symmetric, so the matrix M = \Pi^{1/2} Q \Pi^{-1/2} will also
  be symmetric and hence easier to diagonalise*/

		for (int i = 0; i < 4; i++) {
			for (int j = 0; j <= i; j++) {
				double x = sqrtf[i] * Qmatrix[i][j] / sqrtf[j];
				double y = sqrtf[j] * Qmatrix[j][i] / sqrtf[i];
				M.set(i, j, (x + y) / 2.0);
				if (i != j)
					M.set(j, i, (x + y) / 2.0);
			}
		}

		final EigenvalueDecomposition EX = new EigenvalueDecomposition(M);
		evals = EX.getRealEigenvalues();
		evecs = (EX.getV().getArrayCopy());

		/* Default Pvalue is for tval = 0 */
		Pmatrix = new double[4][4];
		tval = 0.0;
		for (int i = 0; i < 4; i++)
			Pmatrix[i][i] = 1.0;
	}

	/**
	 * get the Q matrix
	 *
	 * @return double[][] Q
	 */
	public double[][] getQ() {
		double[][] Q = new double[4][4];
		for (int i = 0; i < 4; i++) {
			System.arraycopy(Qmatrix[i], 0, Q[i], 0, 4);
		}
		return Q;
	}

	/**
	 * Get an entry in the Q matrix
	 *
	 * @param i first state
	 * @param j second state
	 * @return Q[i][j]
	 */
	public double getQ(int i, int j) {
		return Qmatrix[i][j];
	}

	/**
	 * Compute the transition probabilities. These can be extracted using getP
	 *
	 * @param t length of branch
	 */
	protected void computeP(double t) {

		double[] expD = new double[4];
		for (int i = 0; i < 4; i++) {
			if (gamma <= 0.0)
				expD[i] = Math.exp(evals[i] * t);
			else
				expD[i] = Math.pow(1.0 - gamma * evals[i] * t, -1.0 - gamma);
		}
		for (int i = 0; i < 4; i++) {
			for (int j = 0; j < 4; j++) {
				double Xij = 0.0;
				for (int k = 0; k < 4; k++) {
					Xij += evecs[i][k] * expD[k] * evecs[j][k];

				}
				//System.err.println(Xij);
				Pmatrix[i][j] = (1.0 / sqrtf[i]) * Xij * sqrtf[j];
			}
		}

		//Handle invariant sites

		if (propInvariableSites != 0.0) {
			for (int i = 0; i < 4; i++) {
				for (int j = 0; j < 4; j++) {
					Pmatrix[i][j] *= (1.0 - propInvariableSites);
				}
				Pmatrix[i][i] += propInvariableSites;
			}
		}
		tval = t;
	}

	/**
	 * Compute the X_ij value for this distance. This is the probability of observing state i at the beginning
	 * and state j at the end, or pi_i P_{ij}(t).
	 *
	 * @param i first state (0..3)
	 * @param j second state (0..3)
	 * @param t time (t>=0)
	 * @return double X_ij(t) value
	 */
	public double getX(int i, int j, double t) {
		if (t != tval) {
			computeP(t);
		}
		return freqs[i] * Pmatrix[i][j];
	}

	/**
	 * Compute the P_ij value for this distance. This is the probability of observing state state j at the end
	 * conditional on state i at the beginning or P_{ij}(t).
	 *
	 * @param i first state (0..3)
	 * @param j second state (0..3)
	 * @param t time (t>=0)
	 * @return double P_ij(t) value
	 */
	public double getP(int i, int j, double t) {
		if (t != tval) {
			computeP(t);
		}

		return Pmatrix[i][j];
	}

	/**
	 * Get proportion of invariance sites
	 *
	 * @return double proportion
	 */
	public double getPropInvariableSites() {
		return propInvariableSites;
	}

	/**
	 * Set proportion of invariance sites
	 *
	 * @param p proportion  (double)
	 */
	public void setPropInvariableSites(double p) {
		if (p != propInvariableSites) {
			propInvariableSites = p;
			if (tval != 0.0)
				computeP(tval);
		}
	}

	/**
	 * Get gamma parameter for site rate distribution
	 *
	 * @return gamma parameter
	 */
	public double getGamma() {
		return gamma;
	}

	/**
	 * Sets gamma parameter for site rate distribution
	 *
	 * @param val gamma parameter
	 */
	public void setGamma(double val) {
//Note: negative gamma -> equals rates.
		if (val != gamma) {
			gamma = val;
			if (tval != 0.0) {
				computeP(tval);
			}
		}
	}

	/**
	 * Gets number of states
	 *
	 * @return int
	 */
	public int getNstates() {
		return 4;
	}

	/**
	 * getRate
	 * <p/>
	 * Returns rate
	 */
	public double getRate() {
		return (1.0 - propInvariableSites);
	}

	/**
	 * Given Q and base frequencies, normalises Q so that the rate of mutation equals one.
	 */
	protected void normaliseQ() {

		double r = 0.0;
		for (int i = 0; i < 4; i++)
			r -= freqs[i] * Qmatrix[i][i];

		//Normalise so rate is one.
		for (int i = 0; i < 4; i++) {
			for (int j = 0; j < 4; j++) {
				Qmatrix[i][j] /= r;
			}
		}
		//Normalise the diagonalisation by scaling eigenvalues
		for (int i = 0; i < 4; i++)
			evals[i] /= r;

		//Recompute transition probabilities
		computeP(this.tval);
	}

//    /**
//     * Computes a random value according to probabilities in the base frequency vector
//     *
//     * @param random random generator
//     * @return int random state (0..3)
//     */
//    public int randomPi(Random random) {
//        double x = random.nextDouble();
//        int i = 0;
//        x -= getPi(i);
//        while (x >= 0.0) {
//            i++;
//            x -= getPi(i);
//        }
//        return i;
//    }
//
//    /**
//     * Given a start state, computes a random end state
//     *
//     * @param start  state (0..3)
//     * @param t      double (length of branch)
//     * @param random random number generator
//     * @return int (0..3) state
//     */
//    public int randomEndState(int start, double t, Random random) {
//        double x = random.nextDouble();
//        int i = 0;
//        x -= getP(start, i, t);
//        while (x >= 0.0) {
//            i++;
//            x -= getP(start, i, t);
//        }
//        return i;
//    }

	/**
	 * is this a group valued model
	 *
	 * @return true, if group valued model
	 */
	public boolean isGroupBased() {
		return false;
	}

	public double[] getFreqs() {
		return freqs;
	}

	public void setFreqs(double[] freqs) {
		this.freqs = freqs;
	}

	/**
	 * gets normalized base frequencies
	 *
	 * @return noramlized base frequencies
	 * todo: is this really necessary?
	 */
	public double[] getNormedBaseFreq() {
		double sum = 0.0;
		for (int i = 0; i < 4; i++) {
			sum += freqs[i];
		}
		for (int i = 0; i < 4; i++) {
			freqs[i] = freqs[i] / sum;
		}
		return freqs;
	}

	/**
	 * Return the inverse of the moment generating function
	 *
	 * @param x
	 * @return double
	 */
	public static double mInverse(double x, double propInvariableSites, final double gamma) throws SaturatedDistancesException {
		if (x <= 0.0)
			throw new SaturatedDistancesException();
		final double p = (propInvariableSites < 0.0 || propInvariableSites > 1.0 ? 0 : propInvariableSites);

		if (x - p <= 0.0)
			throw new SaturatedDistancesException();
		if (gamma > 0.0) {
			return gamma * (1.0 - Math.pow((x - p) / (1.0 - p), -1.0 / gamma));
		} else
			return Math.log((x - p) / (1 - p));
	}

	/**
	 * apply the model and fill the distance
	 *
	 * @param progress   used to display the progress
	 * @param characters
	 * @return
	 * @throws SplitsException
	 * @throws CanceledException
	 */
	public void apply(ProgressListener progress, CharactersBlock characters, DistancesBlock distances, boolean useML) throws SplitsException, CanceledException {
		final int ntax = characters.getNtax();
		distances.setNtax(ntax);
		progress.setMaximum(ntax);

		int numMissing = 0;

		for (int s = 1; s <= ntax; s++) {
			for (int t = s + 1; t <= ntax; t++) {
				final PairwiseCompare seqPair = new PairwiseCompare(characters, s, t);
				double dist = 100.0;

				if (useML) {
					//Maximum likelihood distance
					try {
						dist = seqPair.mlDistance(this);
					} catch (SaturatedDistancesException e) {
						numMissing++;
					}
				} else {
					//Exact distance
					final double[][] F = seqPair.getF();
					if (F == null)
						numMissing++;
					else {
						try {
							dist = exactDistance(F);
						} catch (SaturatedDistancesException e) {
							numMissing++;
						}
					}
				}

				distances.set(s, t, dist);
				distances.set(t, s, dist);

				final double var = seqPair.bulmerVariance(dist, 0.75);
				distances.setVariance(s, t, var);
				distances.setVariance(t, s, var);
			}
			progress.incrementProgress();
		}
		progress.close();

		if (numMissing > 0) {
			NotificationManager.showWarning("Proceed with caution: " + numMissing + " saturated or missing entries in the distance matrix");
		}
	}

	/**
	 * Computes the frequencies matrix from *all* taxa
	 *
	 * @param chars  the chars
	 * @param warned Shows an alert if an unexpected symbol appears.
	 * @return the frequencies matrix
	 */

	static public double[] computeFreqs(CharactersBlock chars, boolean warned) {
		int numNotMissing = 0;
		final String symbols = chars.getSymbols();
		final int numStates = symbols.length();
		final double[] Fcount = new double[numStates];
		final char missingChar = chars.getMissingCharacter();
		final char gapChar = chars.getGapCharacter();

		for (int i = 1; i < chars.getNtax(); i++) {
			char[] seq = chars.getMatrix()[i];
			for (int k = 1; k < chars.getNchar(); k++) {
				char c = seq[k];

				//Convert to lower case if the respectCase option is not set
				if (!chars.isRespectCase()) {
					if (c != missingChar && c != gapChar)
						c = Character.toLowerCase(c);
				}
				if (c != missingChar && c != gapChar) {
					numNotMissing = numNotMissing + 1;

					int state = symbols.indexOf(c);

					if (state >= 0) {
						Fcount[state] += 1.0;
					} else if (!warned) {

						NotificationManager.showWarning("Unknown symbol encountered in characters: " + c);
						warned = true;
					}
				}
			}
		}

		for (int i = 0; i < numStates; i++) {
			Fcount[i] = Fcount[i] / (double) numNotMissing;
		}
		return Fcount;
	}
}
