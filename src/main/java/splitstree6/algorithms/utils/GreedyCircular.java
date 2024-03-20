/*
 *  GreedyCircular.java Copyright (C) 2024 Daniel H. Huson
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

import jloda.graph.algorithms.PQTree;
import jloda.util.*;
import jloda.util.progress.ProgressListener;
import splitstree6.splits.ASplit;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Random;
import java.util.function.Function;

/**
 * greedily compute circular splits
 * Daniel Huson, 12/31/16.
 */
public class GreedyCircular {

	/**
	 * computes greedily circular splits
	 *
	 * @return compatible splits
	 */
	public static Pair<ArrayList<ASplit>, ArrayList<Integer>> apply(ProgressListener progress, BitSet taxaSet, final List<ASplit> splits, Function<ASplit, Double> weight) throws CanceledException {
		progress.setSubtask("Greedy outline");
		progress.setMaximum(splits.size());
		progress.setProgress(0);

		final ArrayList<ASplit> result = new ArrayList<>(splits.size());

		var sorted = IteratorUtils.sorted(splits, (a, b) -> {
			var compare = -Double.compare(weight.apply(a), weight.apply(b));
			if (compare == 0)
				compare = -Integer.compare(a.size(), b.size());
			if (compare == 0)
				compare = a.compareTo(b);
			return compare;
		});

		var pqTree = new PQTree(taxaSet);

		for (var split : sorted) {
			var set = split.getPartNotContaining(1);
			if (pqTree.accept(set)) {
				result.add(split);
			}
			progress.incrementProgress();
		}

		if (false) {
			var ordering = pqTree.extractAnOrdering();
			System.err.println("Ordering: " + ordering);
			for (var split : result) {
				var set = split.getPartNotContaining(1);
				var minPos = Integer.MAX_VALUE;
				var maxPos = Integer.MIN_VALUE;
				for (var i = 0; i < ordering.size(); i++) {
					if (set.get(ordering.get(i))) {
						minPos = Math.min(minPos, i);
						maxPos = Math.max(maxPos, i);
					}
				}
				if (Math.abs(maxPos - minPos) + 1 != set.cardinality()) {
					System.err.println("Error in ordering for: " + set);
				}
			}
		}

		if (false) {
			progress.setSubtask("testing pqtree");
			progress.setMaximum(1000);
			progress.setProgress(0);
			var random = new Random(666);
			for (var run = 0; run < 1000; run++) {
				var pqTree1 = new PQTree(taxaSet);
				var accepted = new ArrayList<BitSet>();
				var randomized = CollectionUtils.randomize(result, random);
				for (var split : randomized) {
					if (pqTree1.accept(split.getPartNotContaining(1))) {
						accepted.add(split.getPartNotContaining(1));
					}
				}
				BitSet bad = null;
				for (var cluster : accepted) {
					if (!pqTree1.check(cluster)) {
						System.err.println("Not accepted: " + StringUtils.toString(cluster));
						bad = cluster;
						break;
					}
				}
				if (bad != null) {
					var pqtree2 = new PQTree(taxaSet);
					pqtree2.verbose = true;
					for (var split : randomized) {
						var cluster = split.getPartNotContaining(1);
						if (cluster.equals(bad))
							System.err.println("Bad: " + StringUtils.toString(bad));
						pqtree2.accept(cluster);
						if (!pqtree2.check(bad))
							System.err.println("Not accepted: " + StringUtils.toString(bad));
						progress.checkForCancel();
					}
				}
				progress.incrementProgress();
			}
			progress.reportTaskCompleted();
		}

		return new Pair<>(result, pqTree.extractAnOrdering());
	}
}
