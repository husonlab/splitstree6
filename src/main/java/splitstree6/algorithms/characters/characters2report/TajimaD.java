/*
 * EstimateInvariableSites.java Copyright (C) 2024 Daniel H. Huson
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

package splitstree6.algorithms.characters.characters2report;


import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import jloda.fx.util.ProgramProperties;
import jloda.util.CanceledException;
import jloda.util.progress.ProgressListener;
import splitstree6.data.CharactersBlock;
import splitstree6.data.TaxaBlock;
import splitstree6.data.parts.Taxon;

import java.util.BitSet;
import java.util.Collection;
import java.util.List;

/**
 * Estimates Tajima's D
 * Daniel Huson, 2024
 */
public class TajimaD extends AnalyzeCharactersBase {

	private final BooleanProperty optionExcludeGapSites = new SimpleBooleanProperty(this, "optionExcludeGapSites");

	{
		ProgramProperties.track(optionExcludeGapSites, true);
	}

	@Override
	public List<String> listOptions() {
		return List.of(optionExcludeGapSites.getName());
	}

	@Override
	public String getToolTip(String optionName) {
		if (optionExcludeGapSites.getName().endsWith(optionName))
			return "Exclude gapped sites from calculation.";
		else
			return super.getToolTip(optionName);
	}

	@Override
	public String getCitation() {
		return "Tajima 1989;F. Tajima,Statistical method for testing the neutral mutation hypothesis by DNA polymorphism. Genetics. 123(3):585–95, 1989";
	}

	@Override
	public String getShortDescription() {
		return "Performs Tajima's D test to determine whether a DNA sequence is evolving neutrally.";
	}

	@Override
	String runAnalysis(ProgressListener progress, TaxaBlock taxaBlock, CharactersBlock charactersBlock, Collection<Taxon> selectedTaxa) throws CanceledException {
		char[][] alignment = charactersBlock.getMatrix();
		var n = alignment.length;
		var length = alignment[0].length;

		if (optionExcludeGapSites.get()) {
			var gappedSites = new BitSet();
			for (char[] chars : alignment) {
				for (var j = 0; j < chars.length; j++) {
					if (chars[j] == charactersBlock.getGapCharacter()
						|| chars[j] == charactersBlock.getMissingCharacter())
						gappedSites.set(j);
				}
			}
			if (gappedSites.cardinality() > 0) {
				var tmp = new char[n][length - gappedSites.cardinality()];
				for (var i = 0; i < n; i++) {
					var pos = 0;
					for (var j = gappedSites.nextClearBit(0); j != -1 && j < length; j = gappedSites.nextClearBit(j + 1)) {
						tmp[i][pos++] = alignment[i][j];
					}
				}
				alignment = tmp;
				length -= gappedSites.cardinality();
				if (length == 0)
					return "No ungapped sites found";
			}
		}

		var S = calculateSegregatingSites(alignment, length);
		var a1 = sumOfInverses(n - 1);
		var a2 = sumOfInverseSquares(n - 1);
		var b1 = (n + 1) / (3.0 * (n - 1));
		var b2 = 2.0 * (n * n + n + 3) / (9.0 * n * (n - 1));
		var c1 = b1 - 1.0 / a1;
		var c2 = b2 - (n + 2.0) / (a1 * n) + a2 / (a1 * a1);
		var e1 = c1 / a1;
		var e2 = c2 / (a1 * a1 + a2);
		var variance = e1 * S + e2 * S * (S - 1);

		var wattersonEstimator = S / a1;
		var piEstimator = calculatePiEstimator(charactersBlock.getMatrix());

		var D = (piEstimator - wattersonEstimator) / Math.sqrt(variance);

		String text;
		if (D <= -2.0)
			text = "indicates expanding population";
		else if (D >= 2)
			text = "indicates declining population";
		else text = "test is inconclusive";

		return "Tajima's D = %.4f - %s (calculation based on %d sites)%n".formatted(D, text, length);
	}

	@Override
	public boolean isApplicable(TaxaBlock taxa, CharactersBlock datablock) {
		return datablock.getDataType().isNucleotides();
	}

	private static int calculateSegregatingSites(char[][] alignment, int length) {
		var s = 0;
		for (var j = 0; j < length; j++) {
			if (isSegregatingSite(alignment, j)) {
				s++;
			}
		}
		return s;
	}

	private static boolean isSegregatingSite(char[][] alignment, int column) {
		var base = alignment[0][column];
		for (var i = 1; i < alignment.length; i++) {
			if (alignment[i][column] != base) {
				return true;
			}
		}
		return false;
	}

	private static double sumOfInverses(int n) {
		var sum = 0.0;
		for (var i = 1; i <= n; i++) {
			sum += 1.0 / i;
		}
		return sum;
	}

	private static double sumOfInverseSquares(int n) {
		var sum = 0.0;
		for (var i = 1; i <= n; i++) {
			sum += 1.0 / (i * i);
		}
		return sum;
	}

	public static double calculatePiEstimator(char[][] alignment) {
		var n = alignment.length;
		var length = alignment[0].length;
		var totalPairwiseDifferences = 0.0;

		for (var i = 0; i < n; i++) {
			for (var j = i + 1; j < n; j++) {
				totalPairwiseDifferences += calculatePairwiseDifferences(alignment[i], alignment[j], length);
			}
		}

		// Divide by total number of pairwise comparisons
		int numComparisons = n * (n - 1) / 2;
		return totalPairwiseDifferences / numComparisons;
	}

	private static int calculatePairwiseDifferences(char[] seq1, char[] seq2, int length) {
		var differences = 0;
		for (var i = 0; i < length; i++) {
			if (seq1[i] != seq2[i]) {
				differences++;
			}
		}
		return differences;
	}

	public BooleanProperty optionExcludeGapSitesProperty() {
		return optionExcludeGapSites;
	}
}