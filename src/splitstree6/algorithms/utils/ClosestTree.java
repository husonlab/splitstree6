/*
 * ClosestTree.java Copyright (C) 2023 Daniel H. Huson
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

package splitstree6.algorithms.utils;

import jloda.util.CanceledException;
import jloda.util.progress.ProgressListener;
import splitstree6.data.parts.ASplit;
import splitstree6.data.parts.Compatibility;

import java.util.ArrayList;
import java.util.List;

/**
 * computes the closest tree
 * Daniel Huson, 12/31/16.
 */
public class ClosestTree {
	/**
	 * computes the closest tree
	 *
	 * @return closest tree
	 */
	public static ArrayList<ASplit> apply(ProgressListener progress, final int ntax, final List<ASplit> splits, int[] cycle) throws CanceledException {
		progress.setSubtask("Closest tree");
		progress.setMaximum(100);

		final var AdjMatrix = Compatibility.getCompatibilityMatrix(splits);
		final var vertexWeights = new double[splits.size() + 1];
		for (var i = 1; i <= splits.size(); i++) {
			vertexWeights[i] = splits.get(i - 1).getWeight();
		}

		if (cycle == null)
			cycle = SplitsUtilities.computeCycle(ntax, splits);

		final var result = new ArrayList<ASplit>(splits.size());
		if (Compatibility.isCyclic(ntax, splits, cycle)) {
			result.addAll(CircularMaxClique.getMaxClique(ntax, splits, vertexWeights, cycle));
		} else {
			var maxClique = new MaxWeightClique(AdjMatrix, vertexWeights);
			var clique = maxClique.getMaxClique();
			for (var i = 1; i <= splits.size(); i++) {
				if (clique[i])
					result.add(splits.get(i - 1).clone());
			}
		}

		var totalSquaredWeight = 0.0;
		for (var split : splits) {
			totalSquaredWeight += split.getWeight() * split.getWeight();
		}
		for (var split : result) {
			totalSquaredWeight -= split.getWeight() * split.getWeight();
		}
		var diff = Math.sqrt(totalSquaredWeight);
		System.err.println("Distance to closest tree = " + diff);
		progress.setProgress(100);

		return result;
	}
}
