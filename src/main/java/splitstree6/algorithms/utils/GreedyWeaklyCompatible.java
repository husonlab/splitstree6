/*
 *  GreedyWeaklyCompatible.java Copyright (C) 2024 Daniel H. Huson
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

package splitstree6.algorithms.utils;

import jloda.util.CanceledException;
import jloda.util.IteratorUtils;
import jloda.util.progress.ProgressListener;
import splitstree6.splits.ASplit;
import splitstree6.splits.BiPartition;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * greedy compute weakly compatible splits
 * Daniel Huson, 12/31/16.
 */
public class GreedyWeaklyCompatible {

	/**
	 * computes weakly compatible splits, greedily maximizing the score
	 *
	 * @return compatible splits
	 */
	public static ArrayList<ASplit> apply(ProgressListener progress, final List<ASplit> splits, Function<ASplit, Double> weight) throws CanceledException {
		progress.setSubtask("Greedy weakly compatible");
		progress.setMaximum(splits.size());
		progress.setProgress(0);

		var sorted = IteratorUtils.sorted(splits, (a, b) -> {
			var compare = -Double.compare(weight.apply(a), weight.apply(b));
			if (compare == 0)
				compare = -Integer.compare(a.size(), b.size());
			if (compare == 0)
				compare = a.compareTo(b);
			return compare;
		});

		final ArrayList<ASplit> result = new ArrayList<>(splits.size());
		for (ASplit split : sorted) {
			boolean ok = true;
			for (int t = 0; ok && t < result.size(); t++) {
				for (int q = t + 1; ok && q < result.size(); q++) {
					if (!BiPartition.areWeaklyCompatible(split, result.get(t), result.get(q)))
						ok = false;
				}
			}
			if (ok) {
				result.add(split);
			}
			progress.incrementProgress();
		}
		return result;
	}
}
