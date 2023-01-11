/*
 * GreedyCompatible.java Copyright (C) 2023 Daniel H. Huson
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
import jloda.util.IteratorUtils;
import jloda.util.progress.ProgressListener;
import jloda.util.progress.ProgressSilent;
import splitstree6.data.parts.ASplit;
import splitstree6.data.parts.BiPartition;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.function.Function;

/**
 * greedily compute compatible splits
 * Daniel Huson, 12/31/16.
 */
public class GreedyCompatible {
	/**
	 * computes compatible splits, greedily maximizing the score
	 *
	 * @return compatible splits
	 */
	public static ArrayList<ASplit> apply(ProgressListener progress, final Collection<ASplit> splits, Function<ASplit, Double> score) throws CanceledException {
		progress.setSubtask("Greedy compatible");
		progress.setMaximum(splits.size());
		progress.setProgress(0);

		final var result = new ArrayList<ASplit>(splits.size());
		for (var split : IteratorUtils.sorted(splits, (a, b) -> -Double.compare(score.apply(a), score.apply(b)))) {
			var ok = true;
			for (var bSplit : result) {
				if (!BiPartition.areCompatible(split, bSplit)) {
					ok = false;
					break;
				}
				progress.incrementProgress();
			}
			if (ok) {
				result.add(split);
			}
		}
		return result;
	}

	/**
	 * computes compatible splits, greedily maximizing the score
	 *
	 * @return compatible splits
	 */
	public static ArrayList<ASplit> apply(final Collection<ASplit> splits, Function<ASplit, Double> score) {
		try {
			return apply(new ProgressSilent(), splits, score);
		} catch (IOException ignored) { // can't happen
			return new ArrayList<>();
		}
	}
}
