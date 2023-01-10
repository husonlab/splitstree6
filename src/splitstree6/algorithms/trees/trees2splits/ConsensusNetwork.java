/*
 * ConsensusNetwork.java Copyright (C) 2023 Daniel H. Huson
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

package splitstree6.algorithms.trees.trees2splits;

import javafx.beans.property.*;
import jloda.fx.util.ProgramExecutorService;
import jloda.fx.window.NotificationManager;
import jloda.util.*;
import jloda.util.progress.ProgressListener;
import splitstree6.algorithms.splits.splits2splits.DimensionFilter;
import splitstree6.algorithms.utils.SplitsException;
import splitstree6.algorithms.utils.SplitsUtilities;
import splitstree6.algorithms.utils.TreesUtilities;
import splitstree6.data.SplitsBlock;
import splitstree6.data.TaxaBlock;
import splitstree6.data.TreesBlock;
import splitstree6.data.parts.ASplit;
import splitstree6.data.parts.Compatibility;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.CountDownLatch;

/**
 * implements consensus network
 *
 * @author Daniel Huson, 1/2017
 */
public class ConsensusNetwork extends Trees2Splits {
	public enum EdgeWeights {Mean, TreeSizeWeightedMean, Median, Count, Sum, Uniform}

	private final ObjectProperty<EdgeWeights> optionEdgeWeights = new SimpleObjectProperty<>(this, "optionEdgeWeights", EdgeWeights.TreeSizeWeightedMean);
	private final DoubleProperty optionThresholdPercent = new SimpleDoubleProperty(this, "optionThresholdPercent", 30.0);
	private final BooleanProperty optionHighDimensionFilter = new SimpleBooleanProperty(this, "optionHighDimensionFilter", true);

	private final Object sync = new Object();

	@Override
	public String getCitation() {
		return "Holland and Moulton 2003; B. Holland and V. Moulton. Consensus networks:  A method for visualizing incompatibilities in  collections  of  trees. " +
			   "In  G.  Benson  and  R.  Page,  editors, Proceedings  of  “Workshop  on Algorithms in Bioinformatics, volume 2812 of LNBI, pages 165–176. Springer, 2003.";
	}

	public List<String> listOptions() {
		return List.of(optionEdgeWeights.getName(), optionThresholdPercent.getName(), optionHighDimensionFilter.getName());
	}

	@Override
	public String getToolTip(String optionName) {
		if (optionName.equals(optionEdgeWeights.getName()))
			return "Determine how to calculate edge weights in resulting network";
		else if (optionName.equals(optionThresholdPercent.getName()))
			return "Determine threshold for percent of input trees that split has to occur in for it to appear in the output";
		else if (optionName.equals(optionHighDimensionFilter.getName()))
			return "Heuristically remove splits causing high-dimensional network";
		else
			return super.getToolTip(optionName);
	}

	/**
	 * compute the consensus splits
	 */
	public void compute(ProgressListener progress, TaxaBlock taxaBlock, TreesBlock treesBlock, SplitsBlock splitsBlock) throws IOException {
		final var trees = treesBlock.getTrees();
		final var splitsAndWeights = new HashMap<BitSet, Pair<BitSet, WeightStats>>();
		final var taxaInTree = taxaBlock.getTaxaSet();

		final var executor = ProgramExecutorService.getInstance();

		if (treesBlock.getNTrees() == 1) System.err.println("Consensus network: only one tree specified");

		progress.setMaximum(100);
		progress.setProgress(0);

		{
			final var numberOfThreads = Math.max(1, NumberUtils.min(trees.size(), ProgramExecutorService.getNumberOfCoresToUse(), Runtime.getRuntime().availableProcessors()));
			final var countDownLatch = new CountDownLatch(numberOfThreads);
			final var exception = new Single<CanceledException>();

			final var warnedAboutZeroWeight = new Single<>(false);

			for (var i = 0; i < numberOfThreads; i++) {
				final var threadNumber = i;
				executor.execute(() -> {
					try {
						for (var which = threadNumber + 1; which <= trees.size(); which += numberOfThreads) {
							final var tree = treesBlock.getTree(which);
							final double factor;
							if (getOptionEdgeWeights() == EdgeWeights.TreeSizeWeightedMean) {
								final var treeWeight = TreesUtilities.computeTotalWeight(tree);
								if (treeWeight == 0) {
									synchronized (warnedAboutZeroWeight) {
										if (!warnedAboutZeroWeight.get()) {
											NotificationManager.showWarning("Tree[" + which + "] '" + tree.getName() + "' has zero weight (check the message window for others)");
											warnedAboutZeroWeight.set(true);
										}
									}
									System.err.println("Warning: Tree " + which + " has zero weight");
									factor = 1;
								} else
									factor = 1.0 / treeWeight;
							} else
								factor = 1;

							//System.err.println("Tree "+which+": "+factor);

							final var splits = new ArrayList<ASplit>();
							TreesUtilities.computeSplits(taxaInTree, tree, splits);
							try {
								SplitsUtilities.verifySplits(splits, taxaBlock);
							} catch (SplitsException ex) {
								Basic.caught(ex);
							}

							for (var split : splits) {
								synchronized (sync) {
									final var pair = splitsAndWeights.computeIfAbsent(split.getPartContaining(1), k -> new Pair<>(k, new WeightStats()));
									pair.getSecond().add((float) (factor * split.getWeight()));
									progress.checkForCancel();
								}
							}
							if (threadNumber == 0) {
								progress.setProgress((long) (which * 80.0 / trees.size()));
							}
							if (exception.get() != null)
								return;
						}
					} catch (CanceledException ex) {
						exception.setIfCurrentValueIsNull(ex);
					} finally {
						countDownLatch.countDown();
					}
				});
			}
			try {
				countDownLatch.await();
			} catch (InterruptedException e) {
				if (exception.get() == null) // must have been canceled
					exception.set(new CanceledException());
			}
			if (exception.get() != null) {
				throw exception.get();
			}
		}

		var computedSplits = new SplitsBlock();
		{
			final var numberOfThreads = Math.min(splitsAndWeights.size(), 8);
			final var countDownLatch = new CountDownLatch(numberOfThreads);
			final var exception = new Single<CanceledException>();

			final var threshold = (optionThresholdPercent.getValue() < 100 ? optionThresholdPercent.getValue() / 100.0 : 0.999999);

			final var array = new ArrayList<>(splitsAndWeights.values());

			for (var i = 0; i < numberOfThreads; i++) {
				final var threadNumber = i;
				executor.execute(() -> {
					try {
						for (var which = threadNumber; which < array.size(); which += numberOfThreads) {
							final var side = array.get(which).getFirst();
							final var weightStats = array.get(which).getSecond();
							final double wgt;
							if (weightStats.getCount() / (double) trees.size() > threshold) {
								wgt = switch (getOptionEdgeWeights()) {
									case Count -> weightStats.getCount();
									case TreeSizeWeightedMean -> // values have all already been divided by total tree length, just need mean here...
											weightStats.getMean();
									case Mean -> weightStats.getMean();
									case Median -> weightStats.getMedian();
									case Sum -> weightStats.getSum();
									default -> 1;
								};
								final var confidence = (float) weightStats.getCount() / (float) trees.size();
								synchronized (sync) {
									computedSplits.getSplits().add(new ASplit(side, taxaBlock.getNtax(), wgt, 100 * confidence));
								}
							}
							if (threadNumber == 0) {
								try {
									progress.setProgress(80 + 20L * (which / array.size()));
								} catch (CanceledException ex) {
									exception.set(ex);
								}
							}
							if (exception.get() != null)
								return;
						}
					} finally {
						countDownLatch.countDown();
					}
				});
			}
			try {
				countDownLatch.await();
			} catch (InterruptedException e) {
				if (exception.get() == null) // must have been canceled
					exception.set(new CanceledException());
			}
			if (exception.get() != null) {
				throw exception.get();
			}
		}

		if (getOptionHighDimensionFilter()) {
			DimensionFilter.apply(progress, 4, computedSplits.getSplits(), splitsBlock.getSplits());
		} else
			splitsBlock.copy(computedSplits);

		SplitsUtilities.verifySplits(splitsBlock.getSplits(), taxaBlock);

		splitsBlock.setCycle(SplitsUtilities.computeCycle(taxaBlock.getNtax(), splitsBlock.getSplits()));
		splitsBlock.setFit(-1);
		splitsBlock.setCompatibility(Compatibility.compute(taxaBlock.getNtax(), splitsBlock.getSplits(), splitsBlock.getCycle()));
	}

	@Override
	public boolean isApplicable(TaxaBlock taxaBlock, TreesBlock parent) {
		return !parent.isPartial() && !parent.isReticulated();
	}

	public EdgeWeights getOptionEdgeWeights() {
		return optionEdgeWeights.get();
	}

	public ObjectProperty<EdgeWeights> optionEdgeWeightsProperty() {
		return optionEdgeWeights;
	}

	public void setOptionEdgeWeights(EdgeWeights optionEdgeWeights) {
		this.optionEdgeWeights.set(optionEdgeWeights);
	}

	public double getOptionThresholdPercent() {
		return optionThresholdPercent.get();
	}

	public DoubleProperty optionThresholdPercentProperty() {
		return optionThresholdPercent;
	}

	public void setOptionThresholdPercent(double optionThresholdPercent) {
		this.optionThresholdPercent.set(optionThresholdPercent);
	}

	public boolean getOptionHighDimensionFilter() {
		return optionHighDimensionFilter.get();
	}

	public BooleanProperty optionHighDimensionFilterProperty() {
		return optionHighDimensionFilter;
	}

	public void setOptionHighDimensionFilter(boolean optionHighDimensionFilter) {
		this.optionHighDimensionFilter.set(optionHighDimensionFilter);
	}

	/**
	 * a value object contains a set of all weights seen so far and their counts
	 */
	private static class WeightStats {
		private final ArrayList<Float> weights;
		private int totalCount;
		private double sum;

		/**
		 * construct a new values map
		 */
		WeightStats() {
			weights = new ArrayList<>();
			totalCount = 0;
			sum = 0;
		}

		/**
		 * add the given weight and count
		 */
		void add(float weight) {
			weights.add(weight);
			totalCount++;
			sum += weight;
		}

		/**
		 * returns the number of values
		 *
		 * @return number
		 */
		int getCount() {
			return totalCount;
		}

		/**
		 * computes the mean values
		 *
		 * @return mean
		 */
		double getMean() {
			return sum / (double) totalCount;
		}

		/**
		 * computes the median value
		 *
		 * @return median
		 */
		public double getMedian() {
			Object[] array = weights.toArray();
			Arrays.sort(array);
			return (Float) array[array.length / 2];
		}

		/**
		 * returns the sum of weights
		 *
		 * @return sum
		 */
		double getSum() {
			return sum;
		}
	}
}
