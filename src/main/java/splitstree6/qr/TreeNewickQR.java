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

import jloda.phylo.NewickIO;
import jloda.phylo.PhyloTree;

import java.util.HashSet;
import java.util.function.Function;

/**
 * utilities for compressing the length of a Newick string
 */
public class TreeNewickQR {
	public static Function<PhyloTree, String> createFunction() {
		return t -> TreeNewickQR.apply(t, true, false, false, 4296);
	}

	public static String apply(PhyloTree tree0, boolean showWeights, boolean showConfidences, boolean showProbabilities, int maxLength) {
		var tree = new PhyloTree(tree0);

		var newickIO = new NewickIO();
		var weightPrecision = (showWeights ? 8 : 0);
		var confidencePrecision = (showConfidences ? 8 : 0);
		var probabilityPrecision = (showProbabilities ? 8 : 0);
		var newickFormat = createFormat(showWeights, showConfidences, showProbabilities);
		var maxLabelLength = tree.nodeStream().filter(v -> tree.getLabel(v) != null)
				.mapToInt(v -> tree.getLabel(v).length()).max().orElse(0);

		var newickString = newickIO.toBracketString(tree, newickFormat);
		while (newickString.length() > maxLength) {
			if (probabilityPrecision > 0) {
				probabilityPrecision--;
				if (probabilityPrecision == 0)
					newickFormat.setProbabilityUsingColon(false);
				else
					newickFormat.setProbabilityFormat("%." + probabilityPrecision + "f");

			} else if (confidencePrecision > 0) {
				confidencePrecision--;
				if (confidencePrecision == 0)
					newickFormat.setConfidenceUsingColon(false);
				else
					newickFormat.setConfidenceFormat("%." + probabilityPrecision + "f");
			} else if (weightPrecision > 0) {
				weightPrecision--;
				if (weightPrecision == 0)
					newickFormat.setWeights(false);
				else
					newickFormat.setWeightFormat("%." + probabilityPrecision + "f");
			} else if (maxLabelLength > 1) {
				maxLabelLength--;
				var seen = new HashSet<String>();
				for (var v : tree.nodes()) {
					if (tree.getLabel(v) != null && tree.getLabel(v).length() > maxLabelLength) {
						var newLabel0 = tree.getLabel(v).substring(0, maxLabelLength);
						var newLabel = newLabel0;
						var count = 0;
						while (seen.contains(newLabel)) {
							newLabel = newLabel0 + (++count);
						}
						seen.add(newLabel);
						tree.setLabel(v, newLabel);
					}
				}
			} else {
				return null;
			}
			newickString = newickIO.toBracketString(tree, newickFormat);
		}
		return newickString + ";";
	}

	private static NewickIO.OutputFormat createFormat(boolean weights, boolean confidenceUsingColon, boolean probabilitiesUsingColon) {
		return new NewickIO.OutputFormat(weights, false, confidenceUsingColon, probabilitiesUsingColon, false);
	}
}
