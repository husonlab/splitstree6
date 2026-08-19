/*
 *  BootstrapSplits.java Copyright (C) 2024 Daniel H. Huson
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

package splitstree6.algorithms.splits.splits2splits;

import javafx.beans.property.*;
import jloda.util.Pair;
import jloda.util.ProgramExecutorService;
import jloda.util.Single;
import jloda.util.progress.ProgressListener;
import jloda.util.progress.ProgressSilent;
import splitstree6.algorithms.trees.trees2splits.TreeSelectorSplits;
import splitstree6.algorithms.utils.BootstrappingUtils;
import splitstree6.algorithms.utils.SplitsBlockUtilities;
import splitstree6.data.CharactersBlock;
import splitstree6.data.SplitsBlock;
import splitstree6.data.TaxaBlock;
import splitstree6.data.TreesBlock;
import splitstree6.splits.ASplit;
import splitstree6.splits.Compatibility;
import splitstree6.workflow.Algorithm;
import splitstree6.workflow.DataBlock;
import splitstree6.workflow.DataNode;
import splitstree6.workflow.Workflow;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * bootstrap splits to get bootstrap splits
 *
 * @author Daniel Huson, 2.2022
 */
public class BootstrapSplits extends Splits2Splits {
	private final IntegerProperty optionReplicates = new SimpleIntegerProperty(this, "optionReplicates", 100);
	private final DoubleProperty optionMinPercent = new SimpleDoubleProperty(this, "optionMinPercent", 10.0);
	private final BooleanProperty optionShowAllSplits = new SimpleBooleanProperty(this, "optionShowAllSplits", false);
	private final IntegerProperty optionRandomSeed = new SimpleIntegerProperty(this, "optionRandomSeed", 42);
	private final BooleanProperty optionHighDimensionFilter = new SimpleBooleanProperty(this, "optionHighDimensionFilter", true);

	@Override
	public List<String> listOptions() {
		return List.of(optionReplicates.getName(), optionMinPercent.getName(), optionShowAllSplits.getName(), optionRandomSeed.getName(), optionHighDimensionFilter.getName());
	}

	@Override
	public String getShortDescription() {
		return "Performs bootstrapping on splits.";
	}

	@Override
	public String getToolTip(String optionName) {
		if (!optionName.startsWith("option"))
			optionName = "option" + optionName;
		if (optionName.equals(optionReplicates.getName()))
			return "Number of bootstrap replicates";
		else if (optionName.equals(optionShowAllSplits.getName()))
			return "Show all bootstrap splits, not just the original splits";
		else if (optionName.equals(optionMinPercent.getName()))
			return "Minimum percentage support for a split to be included";
		else if (optionName.equals(optionRandomSeed.getName()))
			return "If non-zero, is used as seed for random number generator";
		else if (optionName.equals(optionHighDimensionFilter.getName()))
			return "Heuristically remove splits causing high-dimensional network";

		return optionName;
	}

	@Override
	public void compute(ProgressListener progress, TaxaBlock taxaBlock, SplitsBlock inputSplits, SplitsBlock splitsBlock) throws IOException {
		compute(progress, taxaBlock, inputSplits, inputSplits.getNode(), splitsBlock);
	}

	public void compute(ProgressListener progress, TaxaBlock taxaBlock, SplitsBlock inputSplits, DataNode targetNode, SplitsBlock splitsBlock) throws IOException {
		// Read the alignment and the pipeline out of the workflow, then hand both over. Everything
		// below works from the arguments, so the computation itself needs no workflow and no node.
		var workflow = (Workflow) taxaBlock.getNode().getOwner();
		var workingDataNode = workflow.getWorkingDataNode();
		var charactersBlock = (workingDataNode.getDataBlock() instanceof CharactersBlock characters ? characters : null);

		// A supplier, not a list: each worker thread needs its OWN path. The data blocks in it are
		// output buffers, and TreeSelectorSplits mutates its own optionWhich against the block it is
		// given, so sharing one path across threads would have them writing over each other. The
		// algorithms inside are deliberately shared, exactly as extractPath has always returned them:
		// they carry the user's option settings, which a fresh instance would not.
		compute(progress, taxaBlock, inputSplits, splitsBlock, charactersBlock,
				() -> {
					var path = BootstrappingUtils.extractPath(workingDataNode, targetNode);
					if (targetNode.getDataBlock() instanceof TreesBlock)
						path.add(new Pair<>(new TreeSelectorSplits(), new SplitsBlock()));
					else
						path.get(path.size() - 1).setSecond(new SplitsBlock());
					return path;
				});
	}

	/**
	 * bootstrap, using the given alignment and pipeline rather than looking for them in the workflow
	 * <p>
	 * Bootstrapping needs more than its declared input: it resamples the ORIGINAL alignment and pushes
	 * each replicate through the same chain of algorithms that produced the splits it was given. Inside
	 * the application both come from the workflow; this form takes them directly, so a test, a tool or a
	 * language binding can bootstrap without building one.
	 *
	 * @param charactersBlock the alignment to resample, or null to produce only the trivial splits
	 * @param pathSupplier    supplies a FRESH pipeline each time it is called - once per worker thread.
	 *                        Each is a list of (algorithm, output buffer) in the order they are applied,
	 *                        ending in one that produces a SplitsBlock. The buffers must not be shared
	 *                        between calls; the algorithms may be, and should be, so that they carry
	 *                        their configured options.
	 */
	public void compute(ProgressListener progress, TaxaBlock taxaBlock, SplitsBlock inputSplits, SplitsBlock splitsBlock,
						CharactersBlock charactersBlock, PathSupplier pathSupplier) throws IOException {

		setOptionReplicates(Math.max(1, optionReplicates.get()));

		setShortDescription(String.format("bootstrapping using %d replicates", getOptionReplicates()));

		var splitCountMap = new ConcurrentHashMap<ASplit, Integer>();
		var splitWeightMap = new ConcurrentHashMap<ASplit, Double>();

		for (var split : inputSplits.getSplits()) {
			splitCountMap.put(new ASplit(split), 0);
		}

		if (charactersBlock != null) {
			if (charactersBlock.isDiploid())
				throw new IOException("Bootstrapping not implemented for diploid data, if you need this, please contact the authors!");

			var seeds = new int[getOptionReplicates()];
			{
				var random = getOptionRandomSeed() == 0 ? new Random() : new Random(getOptionRandomSeed());
				for (var i = 0; i < seeds.length; i++)
					seeds[i] = random.nextInt();
			}

			var numberOfThreads = Math.max(1, Math.min(getOptionReplicates(), ProgramExecutorService.getNumberOfCoresToUse()));
			var service = Executors.newFixedThreadPool(numberOfThreads);
			try {
				var exception = new Single<IOException>();

				progress.setMaximum(getOptionReplicates() / numberOfThreads);
				progress.setProgress(0);

				for (var t = 0; t < numberOfThreads; t++) {
					var thread = t;

					service.execute(() -> {
						try {
							var path = pathSupplier.get();
							if (thread == 0)
								System.err.println("Bootstrap workflow: " + BootstrappingUtils.toString(charactersBlock, path));

							for (var r = thread; r < getOptionReplicates(); r += numberOfThreads) {
								var replicateSplits = (SplitsBlock) run(new ProgressSilent(), taxaBlock, BootstrappingUtils.createReplicate(charactersBlock, new Random(seeds[r])), path);
								for (var split : replicateSplits.getSplits()) {
									if (isOptionShowAllSplits() || splitCountMap.containsKey(split)) {
										// merge, not get-then-put: the maps are shared by every worker thread,
										// and a read-modify-write across them loses increments whenever two
										// land on the same split at once. That made the support values
										// irreproducible even with optionRandomSeed set - measured at 90, 95
										// and 100 percent for the same split across three runs of the same
										// build and the same seed.
										splitCountMap.merge(split, 1, Integer::sum);
										splitWeightMap.merge(split, split.getWeight(), Double::sum);
									}
								}
								if (thread == 0)
									progress.incrementProgress();
								if (exception.isNotNull())
									return;
							}
						} catch (IOException ex) {
							exception.setIfCurrentValueIsNull(ex);
						}
					});
				}
				progress.reportTaskCompleted();

				service.shutdown();
				try {
					service.awaitTermination(1000, TimeUnit.DAYS);
				} catch (InterruptedException ignored) {
				}
				if (exception.isNotNull())
					throw exception.get();
			} finally {
				service.shutdownNow();
			}

			var computedSplits = new ArrayList<ASplit>();

			for (var split : splitCountMap.keySet()) {
				var count = splitCountMap.getOrDefault(split, 0);
				if (count > 0) {
					var percent = 100.0 * ((double) count / (double) getOptionReplicates());
					if (percent >= getOptionMinPercent()) {
						var totalWeight = splitWeightMap.getOrDefault(split, 0.0);
						if (totalWeight > 0) {
							split.setWeight(totalWeight / count);
							split.setConfidence(percent);
							computedSplits.add(split);
						}
					}
				}
			}

			if (getOptionHighDimensionFilter()) {
				DimensionFilter.apply(progress, 4, computedSplits, splitsBlock.getSplits());
			} else
				splitsBlock.getSplits().addAll(computedSplits);
		}

		SplitsBlockUtilities.addAllTrivial(taxaBlock.getNtax(), splitsBlock);

		SplitsBlockUtilities.verifySplits(splitsBlock.getSplits(), taxaBlock);

		splitsBlock.setCycle(SplitsBlockUtilities.computeCycle(taxaBlock.getNtax(), splitsBlock.getSplits()));
		splitsBlock.setFit(-1);
		splitsBlock.setCompatibility(Compatibility.compute(taxaBlock.getNtax(), splitsBlock.getSplits(), splitsBlock.getCycle()));

		splitsBlock.getFormat().setOptionConfidences(true);
	}

	@Override
	public boolean isApplicable(TaxaBlock taxa, SplitsBlock datablock) {
		// Answer false rather than throwing when there is no workflow. This used to dereference
		// getNode() and getOwner() unguarded, so asking whether the algorithm applies threw a
		// NullPointerException outside the application - and callers ask before every run. It is
		// the question "may this be added to this workflow"; the form of compute that takes the
		// alignment and the pipeline explicitly answers to its arguments instead.
		var dataNode = datablock.getNode();
		if (dataNode == null || !(dataNode.getOwner() instanceof Workflow workflow))
			return false;
		var preferredParent = dataNode.getPreferredParent();
		var workingDataNode = workflow.getWorkingDataNode();
		return preferredParent != null && preferredParent.getAlgorithm().getToClass().equals(SplitsBlock.class)
			   && workingDataNode != null && workingDataNode.getDataBlock() instanceof CharactersBlock;
	}

	public int getOptionReplicates() {
		return optionReplicates.get();
	}

	public IntegerProperty optionReplicatesProperty() {
		return optionReplicates;
	}

	public void setOptionReplicates(int optionReplicates) {
		this.optionReplicates.set(optionReplicates);
	}

	public double getOptionMinPercent() {
		return optionMinPercent.get();
	}

	public DoubleProperty optionMinPercentProperty() {
		return optionMinPercent;
	}

	public void setOptionMinPercent(double optionMinPercent) {
		this.optionMinPercent.set(optionMinPercent);
	}

	public boolean isOptionShowAllSplits() {
		return optionShowAllSplits.get();
	}

	public BooleanProperty optionShowAllSplitsProperty() {
		return optionShowAllSplits;
	}

	public void setOptionShowAllSplits(boolean optionShowAllSplits) {
		this.optionShowAllSplits.set(optionShowAllSplits);
	}

	public int getOptionRandomSeed() {
		return optionRandomSeed.get();
	}

	public IntegerProperty optionRandomSeedProperty() {
		return optionRandomSeed;
	}

	public void setOptionRandomSeed(int optionRandomSeed) {
		this.optionRandomSeed.set(optionRandomSeed);
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
	 * run all algorithms in the path on the given characters
	 *
	 * @param progress
	 * @param taxa
	 * @param characters
	 * @param path
	 * @return the final datablock that is computed
	 * @throws IOException
	 */
	/**
	 * supplies a fresh pipeline per worker thread; separate from Supplier because extractPath throws
	 */
	@FunctionalInterface
	public interface PathSupplier {
		ArrayList<Pair<Algorithm, DataBlock>> get() throws IOException;
	}

	public static DataBlock run(ProgressListener progress, TaxaBlock taxa, CharactersBlock characters, Collection<Pair<Algorithm, DataBlock>> path) throws IOException {
		DataBlock inputData = characters;
		for (var pair : path) {
			var algorithm = pair.getFirst();
			var outputData = pair.getSecond();
			if (true || algorithm.isApplicable(taxa, inputData)) {
				outputData.clear();
				algorithm.compute(progress, taxa, inputData, outputData);
			}
			inputData = outputData;
		}
		return inputData;
	}

	@Override
	public String getCitation() {
		return "Felsenstein 1985;J. Felsenstein. Confidence limits on phylogenies: an approach using the bootstrap. Evolution, 39(4):783-791, 1985.";
	}
}
