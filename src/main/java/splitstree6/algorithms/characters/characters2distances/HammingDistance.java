/*
 *  HammingDistance.java Copyright (C) 2026 Daniel H. Huson
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

package splitstree6.algorithms.characters.characters2distances;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import jloda.util.progress.ProgressListener;
import splitstree6.algorithms.characters.characters2distances.utils.FixUndefinedDistances;
import splitstree6.data.CharactersBlock;
import splitstree6.data.DistancesBlock;
import splitstree6.data.TaxaBlock;
import splitstree6.data.parts.AmbiguityCodes;
import splitstree6.data.parts.CharactersType;

import java.io.IOException;
import java.util.List;

/**
 * computes the Hamming distance, the number of sites at which two sequences differ
 * <p>
 * A site is compared only if both sequences have an observed character there. A gap, the missing
 * character, and a code standing for the whole alphabet ('n' for nucleotides, 'x' for protein) all
 * count as unobserved: such a site contributes neither a difference nor a compared site. So each
 * pair is scored on its own overlap, and a pair with no overlap at all gets the undefined distance
 * -1, which FixUndefinedDistances then reports and replaces. Returning 0 there would claim that two
 * sequences sharing no observed site are identical.
 * <p>
 * Because every pair has its own denominator, unnormalized counts coming from pairs with very
 * different overlaps are not on one scale. Run CharactersFilter with optionExcludeGapSites first if
 * the raw counts have to be comparable across the whole matrix.
 * <p>
 * Daniel Huson, 2006, 8.2026
 */
public class HammingDistance extends Characters2Distances {
	private final BooleanProperty optionNormalize = new SimpleBooleanProperty(this, "optionNormalize", false);
	private final BooleanProperty optionMatchAmbiguityCodes = new SimpleBooleanProperty(this, "optionMatchAmbiguityCodes", true);
	private final BooleanProperty optionMatchGapToGap = new SimpleBooleanProperty(this, "optionMatchGapToGap", false);

	public List<String> listOptions() {
		return List.of(optionMatchAmbiguityCodes.getName(), optionMatchGapToGap.getName());
	}

	@Override
	public String getToolTip(String optionName) {
		if (!optionName.startsWith("option"))
			optionName = "option" + optionName;
		if (optionName.equals(optionNormalize.getName())) {
			return "Divide the number of differences by the number of sites compared";
		} else if (optionName.equals(optionMatchAmbiguityCodes.getName())) {
			return "Do not count two compatible nucleotide ambiguity codes, such as Y and C, as a difference (nucleotide data only)";
		} else if (optionName.equals(optionMatchGapToGap.getName())) {
			return "Count a site at which both sequences have a gap as a match, rather than leaving it out";
		} else
			return super.getToolTip(optionName);
	}

	@Override
	public String getCitation() {
		return "Hamming 1950; Hamming, Richard W. Error detecting and error correcting codes. Bell System Technical Journal. 29 (2): 147-160. MR 0035935, 1950.";
	}

	@Override
	public String getShortDescription() {
		return "Computes the Hamming distance, that is, the number of sites at which two sequences differ";
	}

	@Override
	public void compute(ProgressListener progress, TaxaBlock taxa, CharactersBlock characters, DistancesBlock distancesBlock) throws IOException {
		var ntax = taxa.getNtax();
		progress.setMaximum(ntax);

		distancesBlock.setNtax(ntax);

		var nchar = characters.getNchar();
		var gapChar = Character.toLowerCase(characters.getGapCharacter());
		var missingChar = Character.toLowerCase(characters.getMissingCharacter());
		var dataType = characters.getDataType();

		// a code standing for the whole alphabet says nothing, so a stretch of it must not make two
		// sequences look alike; treat it as missing. Partial codes such as 'y' stay real characters
		final char anyChar;
		if (dataType.isNucleotides())
			anyChar = 'n';
		else if (dataType == CharactersType.Protein)
			anyChar = 'x';
		else
			anyChar = 0; // no character of this data type stands for the whole alphabet

		// the ambiguity codes are nucleotide codes, so protein 'b' or 'd' must never be read as one
		var matchAmbiguityCodes = isOptionMatchAmbiguityCodes() && dataType.isNucleotides();
		var masks = (matchAmbiguityCodes ? setupNucleotideMasks() : null);

		var matchGapToGap = isOptionMatchGapToGap();
		var normalize = isOptionNormalize();

		for (var s = 1; s <= ntax; s++) {
			var rowS = characters.getRow0(s - 1);
			for (var t = s + 1; t <= ntax; t++) {
				var rowT = characters.getRow0(t - 1);

				var differences = 0.0;
				var compared = 0.0;

				for (var k = 1; k <= nchar; k++) {
					var cs = rowS[k - 1];
					var ct = rowT[k - 1];
					var weight = characters.getCharacterWeight(k);

					if (isUnobserved(cs, gapChar, missingChar, anyChar) || isUnobserved(ct, gapChar, missingChar, anyChar)) {
						// the one unobserved pair that can carry information: both share the same deletion
						if (matchGapToGap && cs == gapChar && ct == gapChar)
							compared += weight;
						continue;
					}
					compared += weight;
					if (cs != ct && !(matchAmbiguityCodes && compatible(masks, cs, ct)))
						differences += weight;
				}

				final double dist;
				if (compared == 0)
					dist = -1; // the two sequences have no observed site in common, distance undefined
				else if (normalize)
					dist = differences / compared;
				else
					dist = differences;

				distancesBlock.set(s, t, dist);
				distancesBlock.set(t, s, dist);
			}
			progress.incrementProgress();
		}
		FixUndefinedDistances.apply(distancesBlock);
		progress.reportTaskCompleted();
	}

	/**
	 * does this character fail to report an observed state? A gap, the missing character and a code
	 * covering the whole alphabet all do, and a site at which either sequence has one is not compared
	 */
	private static boolean isUnobserved(char ch, char gapChar, char missingChar, char anyChar) {
		return ch == gapChar || ch == missingChar || ch == anyChar;
	}

	/**
	 * can the two characters stand for the same base?
	 */
	private static boolean compatible(int[] masks, char cs, char ct) {
		var ms = (cs < masks.length ? masks[cs] : 0);
		var mt = (ct < masks.length ? masks[ct] : 0);
		return ms != 0 && (ms & mt) != 0;
	}

	/**
	 * sets up the table mapping a base or ambiguity code to the set of bases it stands for, as a bit set
	 * over a, c, g and t; two characters are compatible exactly when their sets intersect. This is a table
	 * rather than a call to AmbiguityCodes.codesOverlap because that allocates two strings per comparison
	 * and there are ntax^2/2 * nchar comparisons.
	 * <p>
	 * RNA's 'u' is folded onto 't' because AmbiguityCodes is written in terms of DNA. Without the fold,
	 * 'y' (c or t) against 'u' would come out as a difference.
	 *
	 * @return table indexed by character, 0 for anything that is neither a base nor a code
	 */
	private static int[] setupNucleotideMasks() {
		var masks = new int[128];
		for (var ch = 'a'; ch <= 'z'; ch++) {
			var bases = AmbiguityCodes.getNucleotides(ch);
			var mask = 0;
			for (var i = 0; i < bases.length(); i++) {
				var base = (bases.charAt(i) == 'u' ? 't' : bases.charAt(i));
				var bit = "acgt".indexOf(base);
				if (bit >= 0)
					mask |= (1 << bit);
			}
			masks[ch] = mask;
		}
		return masks;
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

	public boolean isOptionMatchAmbiguityCodes() {
		return optionMatchAmbiguityCodes.getValue();
	}

	public BooleanProperty optionMatchAmbiguityCodesProperty() {
		return optionMatchAmbiguityCodes;
	}

	public void setOptionMatchAmbiguityCodes(boolean optionMatchAmbiguityCodes) {
		this.optionMatchAmbiguityCodes.setValue(optionMatchAmbiguityCodes);
	}

	public boolean isOptionMatchGapToGap() {
		return optionMatchGapToGap.getValue();
	}

	public BooleanProperty optionMatchGapToGapProperty() {
		return optionMatchGapToGap;
	}

	public void setOptionMatchGapToGap(boolean optionMatchGapToGap) {
		this.optionMatchGapToGap.setValue(optionMatchGapToGap);
	}

	@Override
	public boolean isApplicable(TaxaBlock taxa, CharactersBlock datablock) {
		return super.isApplicable(taxa, datablock);
	}
}
