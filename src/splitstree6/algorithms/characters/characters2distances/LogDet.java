/*
 * LogDet.java Copyright (C) 2021. Daniel H. Huson
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

package splitstree6.algorithms.characters.characters2distances;

import Jama.Matrix;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import jloda.fx.window.NotificationManager;
import jloda.util.ProgressListener;
import splitstree6.algorithms.characters.characters2distances.utils.PairwiseCompare;
import splitstree6.data.CharactersBlock;
import splitstree6.data.DistancesBlock;
import splitstree6.data.TaxaBlock;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * Calculation of the LogDet transform.
 *
 * @author Dave Bryant, 2008
 * <p>
 * <p>
 * The standard formula that we use for computing log det distances is
 * \[d_{xy} = -1/r ln(\frac{ det F_{xy} }{\sqrt{det(\Pi_x \Pi_y)}}\]
 * which is formula (23) on page 460 of Swofford et al.
 * <p/>
 * Taking the log of the determinant is numerically unstable, so instead we compute
 * trace(log(F_xy)) using an eigenvalue decomposition of F_xy.
 * <p/>
 * Both ways of computing log det will run into problems when F has zero or negative
 * eigenvalues. To avoid this, the implementation of logDet in LDDist makes some rather arbitrary
 * modifications to the F_xy matrix. These might be especially useful in protein log Det, where
 * it can often happen that rows or columns of F_xy are all zero. It seems to me that there are
 * better ways of dealing with that (e.g. using logDet on a subset of states), but that
 * requires a lot of further investigation that, given general problems of distance based methods,
 * might not be worth it.
 * <p/>
 * Here is a description of the fudge factor as I've interpreted it from LdDist code:
 * <p/>
 * Let F_{xy}[i,j] be the number of sites with an i for x and a j for y.
 * <p/>
 * for each state i, let \f_x[i] be the number of sites in x with state i. Likewise for \f_y[i].
 * let m_x[i] be the number of sites where x has an i and y has a missing or gap.
 * let m_y[i] be the number of sites where y has an i and x has a missing or gap.
 * <p/>
 * For each pair of states i,j multiply F_{xy}[i,j] by (1.0 + m_x[i]/f_x[i] + m_y[j]/f_y[j])
 * <p/>
 * I'm not quite sure why we don't just add m_x[i]/f_x[i] + m_y[j]/f_y[j] to F_{xy}, as this would correspond
 * to 'allocating' the sites  i -- gap  over the different pairs (i,1),...,(i,r) according to the total frequencies.
 * <p/>
 * The next step is to replace zero elements in F_{xy}[i,j] by 0.5. This is, I guess, imputing missing values,
 * but it won't solve the problem of zero rows or columns.
 * <p/>
 * Finally, we rescale F so that its entries sum to 1.0.
 * <p/>
 * <p/>
 * ToDo: come up with a better way to do this.
 * <p/>
 * The other option involves the use of invariable sites. These can be estimated using the 'Estimate' button (we
 * use the capture-recapture method because it is fast and easy to implement), or you can plug in values from
 * Quartet puzzling or Phyml. Note that pvar is the proportion of variable sites, which is 1.0 minus the proportion
 * of invariable sites. Maybe we should change this.
 * <p/>
 * The formula we use is identical to 'Method 2' in the thesis of Peter Waddell. Namely, let pi[i] denote the
 * estimated frequency for state i and let p be the proportion of invariable sites. Using F (fudged or not) we compute
 * V = (F - p \Pi)
 * where \Pi is the diagonal matrix with \pi_i values down the diagonal. We then replace F by V in the formula above.
 */

public class LogDet extends Characters2Distances {
	private final BooleanProperty optionFudgeFactor = new SimpleBooleanProperty(false);
	private final BooleanProperty optionFillZeros = new SimpleBooleanProperty(false);
	private final DoubleProperty optionPropInvariableSites = new SimpleDoubleProperty(0.0);

	@Override
	public String getCitation() {
		return "Steel 1994; M.A. Steel. Recovering a tree from the leaf colorations it generates under a Markov model. Appl. Math. Lett., 7(2):19–24, 1994.";
	}

	public List<String> listOptions() {
		return Arrays.asList("PropInvariableSites", "FudgeFactor", "FillZeros");
	}

	@Override
	public String getToolTip(String optionName) {
		switch (optionName) {
			case "PropInvariableSites":
				return "Proportion of invariable sites";
			case "FudgeFactor":
				return "Input missing matrix entries using LDDist method";
			case "FillZeros":
				return "Replace zeros with small numbers in rows/columns with values";
		}
		return optionName;
	}

	@Override
	public void compute(ProgressListener progress, TaxaBlock taxaBlock, CharactersBlock charactersBlock, DistancesBlock distancesBlock) throws IOException {
		final int ntax = charactersBlock.getNtax();
		progress.setTasks("logDet distance", "Init.");
		progress.setMaximum(ntax);
		distancesBlock.setNtax(ntax);
		int numUndefined = 0;

		for (int t = 1; t <= ntax; t++) {
			for (int s = t + 1; s <= ntax; s++) {
				PairwiseCompare seqPair = new PairwiseCompare(charactersBlock, s, t);
				double dist = -1.0;

				int r = seqPair.getNumStates();

				double[][] F = seqPair.getF();
				if (F == null) {
					numUndefined++;
				} else {
					if (this.optionFudgeFactor.getValue()) {
                        /* LDDist 1.2 implements some questionable tricks to avoid singluar matrices. To enable
                   comparisons, I've implemented these here. */
						double[][] extF = seqPair.getfCount();

						double[] rowsum = new double[r];
						double[] colsum = new double[r];
						double[] rowgaps = new double[r]; //sum of gap and missng cols
						double[] colgaps = new double[r]; //sum of gap and missing rows
						for (int i = 0; i < r + 2; i++) {
							for (int j = 0; j < r + 2; j++) {
								if (i < r && j < r) {
									rowsum[i] += extF[i][j];
									colsum[j] += extF[i][j];
								} else if (i < r && j >= r) {
									rowgaps[i] += extF[i][j];
								} else if (i >= r && j < r) {
									colgaps[j] += extF[i][j];
								}
							}
						}

						/* add fudge factors from sites with gap or missing */
						for (int i = 0; i < r; i++) {
							for (int j = 0; j < r; j++) {
								double fudgei = 0.0, fudgej = 0.0;
								if (rowsum[i] != 0) fudgei = rowgaps[i] / rowsum[i];
								if (colsum[j] != 0) fudgej = colgaps[j] / colsum[j];
								F[i][j] = extF[i][j] * (1.0 + fudgei + fudgej);
							}
						}

						/* Replace zeros with small numbers !?! but only in rows/columns with values present*/
						double Fsum = 0.0;
						for (int i = 0; i < r; i++) {
							if (rowsum[i] == 0) continue;
							for (int j = 0; j < r; j++) {
								if (this.optionFillZeros.getValue() && colsum[j] != 0 && F[i][j] < 0.5) F[i][j] = 0.5;
								Fsum += F[i][j];
							}
						}
						/*Normalise */
						for (int i = 0; i < r; i++)
							for (int j = 0; j < r; j++)
								F[i][j] /= Fsum;

					}

					/* Determine base frequencies */
					double[] Pi_x = new double[r];
					double[] Pi_y = new double[r];
					double[] Pi = new double[r];
					for (int i = 0; i < r; i++)
						Pi_x[i] = Pi_y[i] = Pi[i] = 0.0;

					for (int i = 0; i < r; i++)
						for (int j = 0; j < r; j++) {
							double Fij = F[i][j];
							Pi_x[i] += Fij;
							Pi_y[j] += Fij;
						}


					for (int i = 0; i < r; i++)
						Pi[i] = (Pi_x[i] + Pi_y[i]) / 2.0;

					double logPi = 0.0;
					for (int i = 0; i < r; i++)
						if (Pi_x[i] != 0.0 && Pi_y[i] != 0.0)
							logPi += Math.log(Pi_x[i]) + Math.log(Pi_y[i]);
					logPi *= 0.5;

					/* Compute Log Det */

					/* Incorporate proportion of invariable sites */
					double pinv = getOptionPropInvariableSites();
					if (pinv > 0.0)
						for (int i = 0; i < r; i++)
							F[i][i] -= pinv * Pi[i];

					final Matrix Fmatrix = new Matrix(F);
					double[] Feigs = Fmatrix.eig().getRealEigenvalues();
					double x = 0.0;
					boolean thisIsSaturated = false;
					for (double Feig : Feigs) {
						if (Feig <= 0.0)
							thisIsSaturated = true;
						else
							x += Math.log(Feig);
					}
					/* now x =  trace(log(F)) = log(det(F)) */
					if (thisIsSaturated) {
						numUndefined++;
						x = -10000000;
					}

					double PiSum = 0;
					for (int i = 0; i < r; i++) {
						PiSum += Pi[i] * Pi[i];
					}

					dist = -(1.0 - PiSum) / (r - 1.0) * (x - logPi);
				}
				distancesBlock.set(s, t, dist);
				distancesBlock.set(t, s, dist);

			}
			progress.incrementProgress();
		}

		if (numUndefined > 0)
			NotificationManager.showWarning("Proceed with caution: " + numUndefined + " saturated or missing entries in the distance matrix. These have been replaced by very large values.");

		progress.close();
	}


	public boolean isApplicable(TaxaBlock taxa, CharactersBlock characters) {

		/* We can apply as long as there is more than one symbol */
		return characters.getSymbols().length() > 1;
	}

	/**
	 * Gets flag of whether missing entries in the F matrix are imputed using the method
	 * that LDDist uses.
	 *
	 * @return boolean
	 */
	public boolean getOptionFudgeFactor() {
		return optionFudgeFactor.getValue();
	}

	public BooleanProperty optionFudgeFactorProperty() {
		return optionFudgeFactor;
	}

	/**
	 * Sets flag of whether missing entries in the F matrix are imputed, using the method that LDDist uses.
	 *
	 * @param val
	 */
	public void setOptionFudgeFactor(boolean val) {
		this.optionFudgeFactor.setValue(val);
	}

	/**
	 * Sets proportion of invariable sites used when computing log det.
	 *
	 * @return double: proportion being used.
	 */
	public double getOptionPropInvariableSites() {
		return this.optionPropInvariableSites.getValue();
	}

	public DoubleProperty optionPropInvariableSitesProperty() {
		return this.optionPropInvariableSites;
	}

	/**
	 * Set proportion of invariable sites to use for log det.
	 *
	 * @param pInvar
	 */
	public void setOptionPropInvariableSites(double pInvar) {
		this.optionPropInvariableSites.setValue(pInvar);
	}


	public boolean getOptionFillZeros() {
		return optionFillZeros.getValue();
	}

	public BooleanProperty optionFillZerosProperty() {
		return optionFillZeros;
	}

	public void setOptionFillZeros(boolean fillZeros) {
		this.optionFillZeros.setValue(fillZeros);
	}
}
