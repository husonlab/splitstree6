/*
 * HammingDistancesAmbigStates.java Copyright (C) 2024 Daniel H. Huson
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

package splitstree6.algorithms.characters.characters2distances;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.Property;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import jloda.util.progress.ProgressListener;
import splitstree6.algorithms.characters.characters2distances.utils.FixUndefinedDistances;
import splitstree6.algorithms.characters.characters2distances.utils.PairwiseCompare;
import splitstree6.data.CharactersBlock;
import splitstree6.data.DistancesBlock;
import splitstree6.data.TaxaBlock;
import splitstree6.data.parts.AmbiguityCodes;

import java.io.IOException;
import java.util.List;

public class HammingDistancesAmbigStates extends Characters2Distances {

	public enum AmbiguousOptions {Ignore, AverageStates, MatchStates}

	private final BooleanProperty optionNormalize = new SimpleBooleanProperty(this, "optionNormalize", true);
	private final Property<AmbiguousOptions> optionHandleAmbiguousStates = new SimpleObjectProperty<>(this, "optionHandleAmbiguousStates", AmbiguousOptions.Ignore);

	public List<String> listOptions() {
		return List.of(optionNormalize.getName(), optionHandleAmbiguousStates.getName());
	}

	@Override
	public String getToolTip(String optionName) {
		if (optionName.equals(optionNormalize.getName())) {
			return "Normalize distances";
		} else if (optionName.equals(optionHandleAmbiguousStates.getName())) {
			return "Choose way to handle ambiguous nucleotides";
		} else
			return super.getToolTip(optionName);
	}

	@Override
	public String getCitation() {
		return "Hamming 1950; Hamming, Richard W. Error detecting and error correcting codes. Bell System Technical Journal. 29 (2): 147–160. MR 0035935, 1950.";
	}

	@Override
	public String getShortDescription() {
		return (new HammingDistances().getShortDescription());
	}

	@Override
	public void compute(ProgressListener progress, TaxaBlock taxa, CharactersBlock characters, DistancesBlock distancesBlock) throws IOException {
		progress.setMaximum(taxa.getNtax());

		distancesBlock.setNtax(characters.getNtax());

		if (optionHandleAmbiguousStates.getValue().equals(AmbiguousOptions.MatchStates)
			&& characters.getDataType().isNucleotides() && characters.isHasAmbiguityCodes())
			computeMatchStatesHamming(taxa, characters, distancesBlock);
		else {
			// all the same here
			var ntax = taxa.getNtax();
			for (var s = 1; s <= ntax; s++) {
				for (var t = s + 1; t <= ntax; t++) {
					var seqPair = new PairwiseCompare(characters, s, t, optionHandleAmbiguousStates.getValue().equals(AmbiguousOptions.Ignore));
					var F = seqPair.getF();
					var dist = -1.0;
					if (F != null) {
						var p = 1.0;
						for (int x = 0; x < seqPair.getNumStates(); x++) {
							p = p - F[x][x];
						}

						if (!isOptionNormalize())
							p = Math.round(p * seqPair.getNumNotMissing());
						dist = p;
					}
					distancesBlock.set(s, t, dist);
					distancesBlock.set(t, s, dist);
				}
				progress.incrementProgress();
			}
		}
		FixUndefinedDistances.apply(distancesBlock);
		progress.reportTaskCompleted();
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

	@Override
	public boolean isApplicable(TaxaBlock taxa, CharactersBlock datablock) {
		return super.isApplicable(taxa, datablock) && datablock.getDataType().isNucleotides();
	}

}
