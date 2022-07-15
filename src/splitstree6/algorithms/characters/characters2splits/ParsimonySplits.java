/*
 * ParsimonySplits.java Copyright (C) 2022 Daniel H. Huson
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

package splitstree6.algorithms.characters.characters2splits;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import jloda.util.progress.ProgressListener;
import splitstree6.algorithms.utils.SplitsUtilities;
import splitstree6.data.CharactersBlock;
import splitstree6.data.SplitsBlock;
import splitstree6.data.TaxaBlock;
import splitstree6.data.parts.ASplit;
import splitstree6.data.parts.AmbiguityCodes;
import splitstree6.data.parts.Compatibility;

import java.io.IOException;
import java.util.ArrayList;
import java.util.BitSet;

/**
 * p-splits method
 * Daniel Huson, 2003
 */
public class ParsimonySplits extends Characters2Splits {
	private final BooleanProperty optionGapsAsMissing = new SimpleBooleanProperty(this, "optionGapsAsMissing", false);

	@Override
	public String getCitation() {
		return "Bandelt and Dress 1992; H.-J.Bandelt and A.W.M.Dress. A canonical decomposition theory for metrics on a finite set. Advances in Mathematics, 92:47–105, 1992.";
	}


	@Override
	public String getToolTip(String optionName) {
		if (optionName.equals(optionGapsAsMissing.getName()))
			return "Treat gaps as missing characters";
		else
			return super.getToolTip(optionName);
	}

	@Override
	public void compute(ProgressListener progress, TaxaBlock taxaBlock, CharactersBlock chars, SplitsBlock splitsBlock) throws IOException {
		splitsBlock.clear();

		var previousSplits = new ArrayList<ASplit>(); // list of previously computed splits
		var currentSplits = new ArrayList<ASplit>(); // current list of splits

		progress.setMaximum(taxaBlock.getNtax());
		progress.setProgress(0);

		for (var t = 1; t <= taxaBlock.getNtax(); t++) {
			// initally, just add 1 to set of previous taxa
			if (t == 1) {
				continue;
			}

			// Does t vs previous set of taxa form a split?
			final BitSet At = new BitSet();
			At.set(t);

			//System.err.println("wgt1 stuff: t=" + t + " AT=" + At);
			{
				final var wgt = pIndex(optionGapsAsMissing.getValue(), t, At, chars);
				//System.err.println("wgt1: " + wgt);
				if (wgt > 0) {
					currentSplits.add(new ASplit(At, t, wgt));
				}
			}

			// consider all previously computed splits:
			for (var prevSplit : previousSplits) {
				final var A = prevSplit.getA();
				final var B = prevSplit.getB();
				// is Au{t} vs B a split?
				A.set(t);
				{
					final int wgt = Math.min((int) prevSplit.getWeight(), pIndex(optionGapsAsMissing.getValue(), t, A, chars));
					//System.err.println("wgt2: "+wgt);
					if (wgt > 0) {
						currentSplits.add(new ASplit(A, t, wgt));
					}
				}
				A.set(t, false);

				// is A vs Bu{t} a split?
				B.set(t);
				{
					final var wgt = Math.min((int) prevSplit.getWeight(), pIndex(optionGapsAsMissing.getValue(), t, B, chars));
					//System.err.println("wgt3: "+wgt);
					if (wgt > 0)
						currentSplits.add(new ASplit(B, t, wgt));
				}
				B.set(t, false);
			}

			// swap lists:
			{
				final var tmp = previousSplits;
				previousSplits = currentSplits;
				currentSplits = tmp;
				currentSplits.clear();
			}
			progress.incrementProgress();
		}

		previousSplits.addAll(SplitsUtilities.createAllMissingTrivial(previousSplits, taxaBlock.getNtax(), 0.0));

		splitsBlock.getSplits().addAll(previousSplits);

		splitsBlock.setCycle(SplitsUtilities.computeCycle(taxaBlock.getNtax(), previousSplits));
		splitsBlock.setCompatibility(Compatibility.compute(taxaBlock.getNtax(), splitsBlock.getSplits(), splitsBlock.getCycle()));
	}


	/**
	 * Computes the p-index of a split:
	 *
	 */
	private int pIndex(boolean gapsAsMissing, int t, BitSet A, CharactersBlock characters) {
		var value = Integer.MAX_VALUE;

		if (!A.get(t)) // a1==t
			System.err.println("pIndex(): a1=" + t + " not in A");

		for (var a2 = 1; a2 <= t; a2++) {
			if (A.get(a2))
				for (var b1 = 1; b1 <= t; b1++) {
					if (!A.get(b1))
						for (var b2 = b1; b2 <= t; b2++) {
							if (!A.get(b2)) {
								var val_a1a2b1b2 = pScore(gapsAsMissing, t, a2, b1, b2, characters);
								//System.err.println(" a1, a2, b1, b2 = "+ a1+"; "+ a2+"; " +b1+"; "+ b2);
								if (val_a1a2b1b2 != 0)
									value = Math.min(value, val_a1a2b1b2);
								else
									return 0;
							}
						}
				}
		}
		return value;
	}

	/**
	 * Computes the parsimony-score for the four given taxa:
	 *
	 */
	private int pScore(boolean gapMissingMode, int a1, int a2, int b1, int b2, CharactersBlock characters) {
		final char missingChar = characters.getMissingCharacter();
		final char gapChar = characters.getGapCharacter();
		final int nchar = characters.getNchar();

		int a1a2_b1b2 = 0, a1b1_a2b2 = 0, a1b2_a2b1 = 0;
		for (int pos = 1; pos <= nchar; pos++) {
			char c_a1 = characters.get(a1, pos);
			char c_a2 = characters.get(a2, pos);
			char c_b1 = characters.get(b1, pos);
			char c_b2 = characters.get(b2, pos);
			// ambiguity characters are treated as gaps:
			if (characters.getDataType().isNucleotides()) {
				if (AmbiguityCodes.isAmbiguityCode(c_a1))
					c_a1 = gapChar;
				if (AmbiguityCodes.isAmbiguityCode(c_a2))
					c_a2 = gapChar;
				if (AmbiguityCodes.isAmbiguityCode(c_b1))
					c_b1 = gapChar;
				if (AmbiguityCodes.isAmbiguityCode(c_b2))
					c_b2 = gapChar;
			}

			if (c_a1 == missingChar || c_a2 == missingChar || c_b1 == missingChar || c_b2 == missingChar)
				continue;
			if (gapMissingMode && (c_a1 == gapChar || c_a2 == gapChar || c_b1 == gapChar || c_b2 == gapChar))
				continue;
			if (c_a1 == c_a2 && c_b1 == c_b2) {
				a1a2_b1b2++;
				//System.err.println("CHARS: "+c_a1+c_a2+c_b1+c_b2);
			}
			if (c_a1 == c_b1 && c_a2 == c_b2)
				a1b1_a2b2++;
			if (c_a1 == c_b2 && c_a2 == c_b1)
				a1b2_a2b1++;
		}
		final int min_val = Math.min(a1b1_a2b2, a1b2_a2b1);
		//System.err.println("min_val: " + min_val);
		//System.err.println("a1a2_b1b2 " + a1a2_b1b2); // todo problem here!
		if (a1a2_b1b2 > min_val)
			return a1a2_b1b2 - min_val;
		else
			return 0;
	}

	public boolean isOptionGapsAsMissing() {
		return optionGapsAsMissing.getValue();
	}

	public BooleanProperty optionGapsAsMissingProperty() {
		return this.optionGapsAsMissing;
	}

	public void setOptionGapsAsMissing(boolean optionGapsAsMissing) {
		this.optionGapsAsMissing.setValue(optionGapsAsMissing);
	}
}
