/*
 * AverageDistances.java Copyright (C) 2024 Daniel H. Huson
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

package splitstree6.algorithms.trees.trees2distances;

import jloda.util.CanceledException;
import jloda.util.NumberUtils;
import jloda.util.ProgramExecutorService;
import jloda.util.Single;
import jloda.util.progress.ProgressListener;
import jloda.util.progress.ProgressSilent;
import splitstree6.algorithms.trees.trees2splits.TreeSelectorSplits;
import splitstree6.data.DistancesBlock;
import splitstree6.data.SplitsBlock;
import splitstree6.data.TaxaBlock;
import splitstree6.data.TreesBlock;

import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * calculates average distances between taxa for a list of trees
 * Daniel Huson, 10.2021
 */

public class AverageDistances extends Trees2Distances {
	@Override
	public void compute(ProgressListener progress, TaxaBlock taxaBlock, TreesBlock treesBlock, DistancesBlock distancesBlock) throws IOException {
		var nTax = taxaBlock.getNtax();
		distancesBlock.setNtax(nTax);

		if (treesBlock.getNTrees() > 0) {
			final var trees = treesBlock.getTrees();

			final var numberOfThreads = NumberUtils.min(trees.size(), ProgramExecutorService.getNumberOfCoresToUse());
			final var executor = Executors.newFixedThreadPool(numberOfThreads);

			var count = new int[numberOfThreads][nTax + 1][nTax + 1];
			var distances = new double[numberOfThreads][nTax + 1][nTax + 1];

			final var exception = new Single<IOException>();

			progress.setMaximum(trees.size());
			progress.setProgress(0);

			for (var t = 0; t < numberOfThreads; t++) {
				final int threadNumber = t;
				executor.execute(() -> {
					try {
						var selector = new TreeSelectorSplits();
						for (int which = threadNumber + 1; which <= trees.size(); which += numberOfThreads) {
							selector.setOptionWhich(which);
							var tmpTaxa = (TaxaBlock) taxaBlock.clone();
							var splits = new SplitsBlock();
							selector.compute(new ProgressSilent(), tmpTaxa, treesBlock, splits); // modifies tmpTaxa, too!
							for (var a = 1; a <= nTax; a++) {
								for (var b = 1; b <= nTax; b++) {
									var i = taxaBlock.indexOf(tmpTaxa.getLabel(a)); // translate numbering
									var j = taxaBlock.indexOf(tmpTaxa.getLabel(b));
									count[threadNumber][i][j]++;
									count[threadNumber][j][i]++;
								}
							}
							for (var s = 1; s <= splits.getNsplits(); s++) {
								var split = splits.get(s);
								var A = split.getA();
								var B = split.getB();
								for (var a = A.nextSetBit(1); a > 0; a = A.nextSetBit(a + 1)) {
									for (var b = B.nextSetBit(1); b > 0; b = B.nextSetBit(b + 1)) {
										var i = taxaBlock.indexOf(tmpTaxa.getLabel(a)); // translate numbering
										var j = taxaBlock.indexOf(tmpTaxa.getLabel(b));
										distances[threadNumber][i][j] += split.getWeight();
										distances[threadNumber][j][i] = distances[threadNumber][i][j];
									}
								}
							}
							synchronized (progress) {
								progress.incrementProgress();
							}
							if (exception.isNotNull())
								break;
						}
					} catch (IOException ex) {
						exception.setIfCurrentValueIsNull(ex);
					}
				});
			}
			try {
				executor.shutdown();
				executor.awaitTermination(1000, TimeUnit.DAYS);
			} catch (InterruptedException e) {
				if (exception.get() == null) // must have been canceled
					exception.set(new CanceledException());
			}
			if (exception.get() != null) {
				throw exception.get();
			}

			var allCounts = new int[nTax + 1][nTax + 1];
			for (var i = 1; i < allCounts.length; i++) {
				for (var j = 1; j < allCounts.length; j++) {
					var value = 0;
					for (int[][] v : count) {
						value += v[i][j];
					}
					allCounts[i][j] = value;
				}
			}

			var allDistances = new double[nTax + 1][nTax + 1];
			for (var i = 1; i < allDistances.length; i++) {
				for (var j = 1; j < allDistances.length; j++) {
					var value = 0;
					for (double[][] v : distances) {
						value += v[i][j];
					}
					allDistances[i][j] = value;
				}
			}

			// divide by count
			for (var i = 1; i <= nTax; i++) {
				for (var j = 1; j <= nTax; j++) {
					if (allDistances[i][j] > 0)
						distancesBlock.set(i, j, allDistances[i][j] / allCounts[i][j]);
					else
						distancesBlock.set(i, j, 0); // shouldn't ever happen!
				}
			}
		}
	}

	@Override
	public boolean isApplicable(TaxaBlock taxaBlock, TreesBlock parent) {
		return !parent.isPartial() && !parent.isReticulated();
	}
}
