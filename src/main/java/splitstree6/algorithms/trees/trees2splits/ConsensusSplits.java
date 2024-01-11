/*
 * ConsensusSplits.java Copyright (C) 2024 Daniel H. Huson
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
import splitstree6.algorithms.utils.*;
import splitstree6.data.SplitsBlock;
import splitstree6.data.TaxaBlock;
import splitstree6.data.TreesBlock;
import splitstree6.splits.ASplit;
import splitstree6.splits.Compatibility;
import splitstree6.splits.SplitUtils;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.CountDownLatch;

/**
 * implements consensus splits calculations
 * <p>
 * Daniel Huson, 2.2018
 */
public class ConsensusSplits extends Trees2Splits {

	public enum Consensus {Strict, Majority, GreedyCompatible, ConsensusOutline, GreedyWeaklyCompatible, ConsensusNetwork} // todo: add loose?

	public enum EdgeWeights {Mean, TreeSizeWeightedMean, Median, Count, Sum, Uniform, TreeNormalizedSum}

	private final ObjectProperty<Consensus> optionConsensus = new SimpleObjectProperty<>(this, "optionConsensus", Consensus.Majority);
	protected final ObjectProperty<EdgeWeights> optionEdgeWeights = new SimpleObjectProperty<>(this, "optionEdgeWeights", ConsensusNetwork.EdgeWeights.TreeSizeWeightedMean);
	protected final DoubleProperty optionThresholdPercent = new SimpleDoubleProperty(this, "optionThresholdPercent", 0.0);
	protected final BooleanProperty optionHighDimensionFilter = new SimpleBooleanProperty(this, "optionHighDimensionFilter", true);

	@Override
	public List<String> listOptions() {
		return List.of(optionConsensus.getName(), optionEdgeWeights.getName(), optionThresholdPercent.getName(), optionHighDimensionFilter.getName());
	}

	@Override
	public String getToolTip(String optionName) {
		if (optionName.equals(optionConsensus.getName()))
			return "Consensus method";
		else if (optionName.equals(optionEdgeWeights.getName()))
			return "How to calculate edge weights in resulting network";
		else if (optionName.equals(optionThresholdPercent.getName()))
			return "Threshold for percent of input trees that split has to occur in for it to appear in the output";
		else if (optionName.equals(optionHighDimensionFilter.getName()))
			return "Heuristically remove splits causing high-dimensional consensus network";
		else
			return super.getToolTip(optionName);
	}

	/**
	 * compute the consensus splits
	 */
	public void compute(ProgressListener progress, TaxaBlock taxaBlock, TreesBlock treesBlock, SplitsBlock splitsBlock) throws IOException {
		double consensusSplitsThreshold;
		boolean useHighDimensionsFilter;
		switch (getOptionConsensus()) {
			case Majority -> {
				setOptionThresholdPercent(0);
				consensusSplitsThreshold = 50;
				useHighDimensionsFilter = false;
			}
			case Strict -> {
				consensusSplitsThreshold = 99.999999; // todo: implement without use of splits
				setOptionThresholdPercent(0);
				useHighDimensionsFilter = false;
			}
			case ConsensusOutline, GreedyCompatible, GreedyWeaklyCompatible -> {
				consensusSplitsThreshold = getOptionThresholdPercent();
				useHighDimensionsFilter = false;
			}
			case ConsensusNetwork -> {
				consensusSplitsThreshold = getOptionThresholdPercent();
				useHighDimensionsFilter = isOptionHighDimensionFilter();
			}
			default -> throw new RuntimeException("Unhandled case");
		}

		final var consensusSplits = new ArrayList<ASplit>();

		compute(progress, taxaBlock, treesBlock, consensusSplits, getOptionEdgeWeights(), consensusSplitsThreshold, useHighDimensionsFilter);

		splitsBlock.clear();

		switch (getOptionConsensus()) {
			case GreedyCompatible -> {
				splitsBlock.getSplits().addAll(GreedyCompatible.apply(progress, consensusSplits, ASplit::getConfidence));
				splitsBlock.setCycle(SplitUtils.computeCycle(taxaBlock.getNtax(), splitsBlock.getSplits()));
				splitsBlock.setCompatibility(Compatibility.compatible);
			}
			case ConsensusOutline -> {
				splitsBlock.getSplits().addAll(GreedyCircular.apply(progress, taxaBlock.getTaxaSet(), consensusSplits, ASplit::getConfidence));
				splitsBlock.setCycle(SplitUtils.computeCycle(taxaBlock.getNtax(), splitsBlock.getSplits()));
				splitsBlock.setCompatibility(Compatibility.compute(taxaBlock.getNtax(), splitsBlock.getSplits(), splitsBlock.getCycle()));
			}
			case GreedyWeaklyCompatible -> {
				splitsBlock.getSplits().addAll(GreedyWeaklyCompatible.apply(progress, consensusSplits, ASplit::getConfidence));
				splitsBlock.setCycle(SplitUtils.computeCycle(taxaBlock.getNtax(), splitsBlock.getSplits()));
				splitsBlock.setCompatibility(Compatibility.compute(taxaBlock.getNtax(), splitsBlock.getSplits(), splitsBlock.getCycle()));
			}
			case Majority, Strict -> {
				splitsBlock.getSplits().addAll(consensusSplits);
				splitsBlock.setCycle(SplitUtils.computeCycle(taxaBlock.getNtax(), splitsBlock.getSplits()));
				splitsBlock.setCompatibility(Compatibility.compatible);
			}
			case ConsensusNetwork -> {
				splitsBlock.getSplits().addAll(consensusSplits);
				splitsBlock.setCycle(SplitUtils.computeCycle(taxaBlock.getNtax(), splitsBlock.getSplits()));
				splitsBlock.setCompatibility(Compatibility.compute(taxaBlock.getNtax(), splitsBlock.getSplits(), splitsBlock.getCycle()));
			}
		}

		{
			var splitWeightMap = new HashMap<ASplit, Double>();
			{
				var allSplits = new ArrayList<ASplit>();
				compute(progress, taxaBlock, treesBlock, allSplits, EdgeWeights.TreeNormalizedSum, 0.0, false);
				for (var split : allSplits)
					splitWeightMap.put(split, split.getWeight());
			}
			var totalWeight = splitWeightMap.values().stream().mapToDouble(d -> d).sum();
			var consensusWeight = splitsBlock.getSplits().stream().mapToDouble(s -> splitWeightMap.getOrDefault(s, 0.0)).sum();
			splitsBlock.setFit((float) (totalWeight > 0 ? 100 * consensusWeight / totalWeight : -1));
			if (false)
				System.err.printf("Fit: %.1f%n", splitsBlock.getFit());
		}
	}

	@Override
	public boolean isApplicable(TaxaBlock taxaBlock, TreesBlock parent) {
		return !parent.isPartial();
	}

	public Consensus getOptionConsensus() {
		return optionConsensus.get();
	}

	public ObjectProperty<Consensus> optionConsensusProperty() {
		return optionConsensus;
	}

	public void setOptionConsensus(Consensus optionConsensus) {
		this.optionConsensus.set(optionConsensus);
	}

	public void setOptionConsensus(String optionConsensus) {
		if (optionConsensus.equals("Greedy"))
			optionConsensus = "GreedyCompatible";
		this.optionConsensus.set(Consensus.valueOf(optionConsensus));
	}

	public ConsensusNetwork.EdgeWeights getOptionEdgeWeights() {
		return optionEdgeWeights.get();
	}

	public ObjectProperty<ConsensusNetwork.EdgeWeights> optionEdgeWeightsProperty() {
		return optionEdgeWeights;
	}

	public void setOptionEdgeWeights(ConsensusNetwork.EdgeWeights optionEdgeWeights) {
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

	public boolean isOptionHighDimensionFilter() {
		return optionHighDimensionFilter.get();
	}

	public BooleanProperty optionHighDimensionFilterProperty() {
		return optionHighDimensionFilter;
	}

	public void setOptionHighDimensionFilter(boolean optionHighDimensionFilter) {
		this.optionHighDimensionFilter.set(optionHighDimensionFilter);
	}

	/**
	 * compute the consensus splits
	 */
	public static void compute(ProgressListener progress, TaxaBlock taxaBlock, TreesBlock treesBlock, ArrayList<ASplit> splits,
							   ConsensusNetwork.EdgeWeights edgeWeights, double thresholdPercent, boolean highDimensionFilter) throws IOException {
		splits.clear();
		final var sync = new Object();
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
							if (edgeWeights == ConsensusNetwork.EdgeWeights.TreeSizeWeightedMean || edgeWeights == EdgeWeights.TreeNormalizedSum) {
								final var treeWeight = tree.edgeStream().mapToDouble(tree::getWeight).sum();

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

							final var treeSplits = new ArrayList<ASplit>();
							SplitUtils.computeSplits(taxaInTree, tree, treeSplits);
							try {
								SplitsBlockUtilities.verifySplits(treeSplits, taxaBlock);
							} catch (SplitsException ex) {
								Basic.caught(ex);
							}

							for (var split : treeSplits) {
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

			final var threshold = (thresholdPercent < 100 ? thresholdPercent / 100.0 : 0.999999);

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
								wgt = switch (edgeWeights) {
									case Count -> weightStats.getCount();
									case TreeSizeWeightedMean -> // values have all already been divided by total tree length, just need mean here...
											weightStats.getMean();
									case Mean -> weightStats.getMean();
									case Median -> weightStats.getMedian();
									case TreeNormalizedSum -> weightStats.getSum();
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

		if (highDimensionFilter) {
			DimensionFilter.apply(progress, 4, computedSplits.getSplits(), splits);
		} else
			splits.addAll(computedSplits.getSplits());

		SplitsBlockUtilities.verifySplits(splits, taxaBlock);
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
