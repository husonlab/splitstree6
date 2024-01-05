/*
 * GreedyWeaklyCompatible.java Copyright (C) 2024 Daniel H. Huson
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

import jloda.graph.algorithms.PQTree;
import jloda.util.CanceledException;
import jloda.util.CollectionUtils;
import jloda.util.IteratorUtils;
import jloda.util.StringUtils;
import jloda.util.progress.ProgressListener;
import splitstree6.splits.ASplit;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Random;
import java.util.function.Function;

/**
 * greedy compute weakly compatible splits
 * Daniel Huson, 12/31/16.
 */
public class GreedyCircular {

	/**
	 * computes greedily circular splits
	 *
	 * @return compatible splits
	 */
	public static ArrayList<ASplit> apply(ProgressListener progress, BitSet taxaSet, final List<ASplit> splits, Function<ASplit, Double> weight) throws CanceledException {
		progress.setSubtask("Greedy circular");
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

		for (ASplit split : sorted) {
			var set = split.getPartNotContaining(1);
			if (pqTree.accept(set)) {
				result.add(split);
			}
			progress.incrementProgress();
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

		return result;
	}
}
