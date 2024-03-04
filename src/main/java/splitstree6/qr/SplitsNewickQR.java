/*
 *  TreeNewickQR.java Copyright (C) 2024 Daniel H. Huson
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

package splitstree6.qr;

import javafx.util.Pair;
import jloda.fx.window.NotificationManager;
import splitstree6.data.SplitsBlock;
import splitstree6.data.TaxaBlock;
import splitstree6.splits.SplitNewick;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.function.Function;

/**
 * utilities for compressing the length of a Newick string for a set of splits
 * Daniel Huson, 2.2024
 */
public class SplitsNewickQR {
	public static Function<Pair<TaxaBlock, SplitsBlock>, String> createFunction() {
		return p -> SplitsNewickQR.apply(p.getKey(), p.getValue(), true, false, 4296);
	}

	/**
	 * creates a Newick string suitable for a QR code
	 *
	 * @param taxa            taxa
	 * @param splits          splits
	 * @param showWeights     show weights?
	 * @param showConfidences show confidences?
	 * @param maxLength       max length of string
	 * @return string
	 */
	public static String apply(TaxaBlock taxa, SplitsBlock splits, boolean showWeights, boolean showConfidences, int maxLength) {
		if (taxa == null || taxa.getNtax() == 0 || splits == null || splits.getNsplits() == 0)
			return null;

		var taxLabelMap = new HashMap<Integer, String>();
		var maxLabelLength = 0;
		for (var t = 1; t <= taxa.getNtax(); t++) {
			taxLabelMap.put(t, taxa.getLabel(t));
			maxLabelLength = Math.max(maxLabelLength, taxa.getLabel(t).length());
		}

		try {
			var newickString = SplitNewick.toString(taxLabelMap::get, splits.getSplits(), showWeights, showConfidences, splits.getCycle()) + ";";
			while (newickString.length() >= maxLength) {
				if (showConfidences) {
					showConfidences = false;
				} else if (showWeights) {
					showWeights = false;
				} else {
					var length = newickString.length();
					var charactersSaved = 0;
					while (length - charactersSaved > maxLength) {
						maxLabelLength--;
						if (maxLabelLength == 0) {
							NotificationManager.showWarning("Newick string too long for QR code");
							return null;
						}
						charactersSaved = 0;
						var seen = new HashSet<String>();
						for (var t = 1; t <= taxa.getNtax(); t++) {
							var label0 = taxLabelMap.get(t);
							if (label0.length() > maxLabelLength)
								label0 = label0.substring(0, maxLabelLength);
							var count = 0;
							var label = label0;
							while (seen.contains(label)) {
								label = label0 + (++count);
							}
							seen.add(label);
							charactersSaved = label0.length() - label.length();
						}
					}
					maxLabelLength -= 2;
					var seen = new HashSet<String>();
					for (var t = 1; t <= taxa.getNtax(); t++) {
						var label0 = taxLabelMap.get(t);
						if (label0.length() > maxLabelLength)
							label0 = label0.substring(0, maxLabelLength);
						var count = 0;
						var label = label0;
						while (seen.contains(label)) {
							label = label0 + (++count);
						}
						seen.add(label);
						taxLabelMap.put(t, label);
					}
				}
				newickString = SplitNewick.toString(taxLabelMap::get, splits.getSplits(), showWeights, showConfidences) + ";";
			}
			return newickString + ";";
		} catch (IOException ignored) {
			return null;
		}
	}
}
