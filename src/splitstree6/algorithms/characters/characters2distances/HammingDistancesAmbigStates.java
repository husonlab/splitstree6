/*
 * HammingDistancesAmbigStates.java Copyright (C) 2021. Daniel H. Huson
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

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.Property;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import jloda.fx.window.NotificationManager;
import jloda.util.ProgressListener;
import splitstree6.algorithms.characters.characters2distances.utils.PairwiseCompare;
import splitstree6.data.CharactersBlock;
import splitstree6.data.DistancesBlock;
import splitstree6.data.TaxaBlock;
import splitstree6.data.parts.AmbiguityCodes;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

public class HammingDistancesAmbigStates extends Characters2Distances {

	public enum AmbiguousOptions {Ignore, AverageStates, MatchStates}

	private BooleanProperty optionNormalize = new SimpleBooleanProperty(true);
	private Property<AmbiguousOptions> optionHandleAmbiguousStates = new SimpleObjectProperty<>(AmbiguousOptions.Ignore);

	public List<String> listOptions() {
		return Arrays.asList("Normalize", "HandleAmbiguousStates");
	}

	@Override
	public String getToolTip(String optionName) {
		switch (optionName) {
			case "Normalize":
				return "Normalize distances";
			case "HandleAmbiguousStates":
				return "Choose way to handle ambiguous nucleotides";
		}
		return optionName;
	}

	@Override
	public String getCitation() {
		return "Hamming 1950; Hamming, Richard W. Error detecting and error correcting codes. Bell System Technical Journal. 29 (2): 147–160. MR 0035935, 1950.";
	}

	@Override
	public void compute(ProgressListener progress, TaxaBlock taxa, CharactersBlock characters, DistancesBlock distances) throws IOException {
		progress.setMaximum(taxa.getNtax());

		distances.setNtax(characters.getNtax());

		if (optionHandleAmbiguousStates.getValue().equals(AmbiguousOptions.MatchStates)
				&& characters.getDataType().isNucleotides() && characters.isHasAmbiguityCodes())
			computeMatchStatesHamming(taxa, characters, distances);
		else {
			// all the same here
			int numMissing = 0;
			final int ntax = taxa.getNtax();
			for (int s = 1; s <= ntax; s++) {
				for (int t = s + 1; t <= ntax; t++) {

					final PairwiseCompare seqPair;
					if (optionHandleAmbiguousStates.getValue().equals(AmbiguousOptions.Ignore))
						seqPair = new PairwiseCompare(characters, s, t, true);
					else
						seqPair = new PairwiseCompare(characters, s, t, false);

					double p = 1.0;

					final double[][] F = seqPair.getF();

					if (F == null) {
						numMissing++;
					} else {
						for (int x = 0; x < seqPair.getNumStates(); x++) {
							p = p - F[x][x];
						}

						if (!isOptionNormalize())
							p = Math.round(p * seqPair.getNumNotMissing());
					}
					distances.set(s, t, p);
					distances.set(t, s, p);
				}
				progress.incrementProgress();
			}
			if (numMissing > 0)
				NotificationManager.showWarning("Proceed with caution: " + numMissing + " saturated or missing entries in the distance matrix");
		}
	}

	/**
	 * Computes 'Best match' Hamming distances with a given characters block.
	 *
	 * @param taxa       the taxa
	 * @param characters the input characters
	 */
	private void computeMatchStatesHamming(TaxaBlock taxa, CharactersBlock characters, DistancesBlock distances) {
		final String ALLSTATES = "acgt" + AmbiguityCodes.CODES;
		final int ntax = taxa.getNtax();
		final int nstates = ALLSTATES.length();

		/* Fill in the costs ascribed to comparing different allele combinations */
		final double[][] weights = new double[nstates][nstates];
		for (int s1 = 0; s1 < nstates; s1++)
			for (int s2 = 0; s2 < nstates; s2++)
				weights[s1][s2] = stringDiff(AmbiguityCodes.getNucleotides(ALLSTATES.charAt(s1)),
						AmbiguityCodes.getNucleotides(ALLSTATES.charAt(s2)));

        /*for (char s1 : ALLSTATES.toCharArray())
            for (char s2 : ALLSTATES.toCharArray())
                weights[s1][s2] = stringDiff(AmbiguityCodes.getNucleotides(s1), AmbiguityCodes.getNucleotides(s2));*/

		/*Fill in the distance matrix */
		for (int s = 1; s <= ntax; s++) {
			for (int t = s + 1; t <= ntax; t++) {

				double[][] F = getFmatrix(ALLSTATES, characters, s, t);
				double diff = 0.0;
				for (int s1 = 0; s1 < F.length; s1++)
					for (int s2 = 0; s2 < F.length; s2++)
						diff += F[s1][s2] * weights[s1][s2];

				distances.set(s, t, (float) diff);
				distances.set(t, s, (float) diff);
			}
		}
	}

	private double stringDiff(String s1, String s2) {
		int matchCount = 0;
		for (int i = 0; i < s1.length(); i++) {
			char ch = s1.charAt(i);
			if (s2.indexOf(ch) >= 0) {
				matchCount++;
			}
		}
		for (int i = 0; i < s2.length(); i++) {
			char ch = s2.charAt(i);
			if (s1.indexOf(ch) >= 0) {
				matchCount++;
			}
		}

		return 1.0 - (double) matchCount / ((double) s1.length() + s2.length());
		//SAME IN INVERSE.
	}

	private double[][] getFmatrix(String ALLSTATES, CharactersBlock characters, int i, int j) {
		int nstates = ALLSTATES.length();
		double[][] F = new double[nstates][nstates];
		double fsum = 0.0;
		for (int k = 1; k <= characters.getNchar(); k++) {
			char ch1 = characters.get(i, k);
			char ch2 = characters.get(j, k);
			int state1 = ALLSTATES.indexOf(ch1);
			int state2 = ALLSTATES.indexOf(ch2);
			if (state1 >= 0 && state2 >= 0) {
				F[state1][state2] += 1.0;
				fsum += 1.0;
			}
		}
		if (fsum > 0.0) {
			for (int x = 0; x < nstates; x++)
				for (int y = 0; y < nstates; y++)
					F[x][y] = F[x][y] / fsum;

		}

		return F;
	}

	// GETTERS AND SETTERS

	public boolean isOptionNormalize() {
		return optionNormalize.getValue();
	}

	public BooleanProperty optionNormalizeProperty() {
		return optionNormalize;
	}

	public void setOptionNormalize(boolean optionNormalize) {
		this.optionNormalize.setValue(optionNormalize);
	}

	public AmbiguousOptions getOptionHandleAmbiguousStates() {
		return this.optionHandleAmbiguousStates.getValue();
	}

	public Property<AmbiguousOptions> optionHandleAmbiguousStatesProperty() {
		return this.optionHandleAmbiguousStates;
	}

	public void setOptionHandleAmbiguousStates(AmbiguousOptions optionHandleAmbiguousStates) {
		this.optionHandleAmbiguousStates.setValue(optionHandleAmbiguousStates);
	}
}
