/*
 *  GreedyCompatible.java Copyright (C) 2021 Daniel H. Huson
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
 * GreedyCompatible.java Copyright (C) 2021. Daniel H. Huson
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

package splitstree6.utils;

import jloda.util.CanceledException;
import jloda.util.progress.ProgressListener;
import splitstree6.data.parts.ASplit;
import splitstree6.data.parts.Compatibility;

import java.util.ArrayList;
import java.util.List;

/**
 * greedily compute compatible splits
 * Daniel Huson, 12/31/16.
 */
public class GreedyCompatible {
	/**
	 * greedily computes compatible splits
	 *
	 * @param progress
	 * @param splits
	 * @return compatible splits
	 * @throws CanceledException
	 */
	public static ArrayList<ASplit> apply(ProgressListener progress, final List<ASplit> splits) throws CanceledException {
		progress.setSubtask("Greedy compatible");
		progress.setMaximum(splits.size());
		progress.setProgress(0);

		final ArrayList<ASplit> sorted = SplitsUtilities.sortByDecreasingWeight(splits);
		final ArrayList<ASplit> result = new ArrayList<>(splits.size());
		for (ASplit aSplit : sorted) {
			boolean ok = true;
			for (ASplit bSplit : result) {
				if (!Compatibility.areCompatible(aSplit, bSplit)) {
					ok = false;
					break;
				}
				progress.incrementProgress();
			}
			if (ok) {
				result.add(aSplit);
			}
		}
		return result;
	}
}
