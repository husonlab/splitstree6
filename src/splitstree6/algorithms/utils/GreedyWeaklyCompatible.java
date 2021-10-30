/*
 *  GreedyWeaklyCompatible.java Copyright (C) 2021 Daniel H. Huson
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

/*
 * GreedyWeaklyCompatible.java Copyright (C) 2021. Daniel H. Huson
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

package splitstree6.algorithms.utils;

import jloda.util.CanceledException;
import jloda.util.progress.ProgressListener;
import splitstree6.data.parts.ASplit;
import splitstree6.data.parts.Compatibility;

import java.util.ArrayList;
import java.util.List;

/**
 * greedy compute weakly compatible splits
 * Daniel Huson, 12/31/16.
 */
public class GreedyWeaklyCompatible {

	/**
	 * computes greedily compatible splits
	 *
	 * @param progress
	 * @param splits
	 * @return compatible splits
	 * @throws CanceledException
	 */
	public static ArrayList<ASplit> apply(ProgressListener progress, final List<ASplit> splits) throws CanceledException {
		progress.setSubtask("Greedy weakly compatible");
		progress.setMaximum(splits.size());
		progress.setProgress(0);

		final ArrayList<ASplit> sorted = SplitsUtilities.sortByDecreasingWeight(splits);
		final ArrayList<ASplit> result = new ArrayList<>(splits.size());

		for (int s = 0; s <= sorted.size(); s++) {
			boolean ok = true;
			for (int t = 0; ok && t < result.size(); t++) {
				for (int q = t + 1; ok && q < result.size(); q++) {
					if (!Compatibility.areWeaklyCompatible(sorted.get(s), result.get(t), result.get(q)))
						ok = false;
				}
			}
			if (ok) {
				result.add(sorted.get(s));
			}
			progress.incrementProgress();
		}
		return result;
	}
}
