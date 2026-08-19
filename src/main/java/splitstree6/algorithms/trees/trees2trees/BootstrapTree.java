/*
 *  BootstrapTree.java Copyright (C) 2024 Daniel H. Huson
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

package splitstree6.algorithms.trees.trees2trees;

import javafx.beans.property.*;
import jloda.graph.Edge;
import jloda.graph.EdgeArray;
import jloda.graph.Node;
import jloda.phylo.PhyloTree;
import jloda.util.*;
import jloda.util.progress.ProgressListener;
import jloda.util.progress.ProgressSilent;
import splitstree6.algorithms.splits.splits2splits.BootstrapSplits;
import splitstree6.algorithms.trees.IToSingleTree;
import splitstree6.algorithms.utils.BootstrappingUtils;
import splitstree6.data.CharactersBlock;
import splitstree6.data.TaxaBlock;
import splitstree6.data.TreesBlock;
import splitstree6.workflow.DataNode;
import splitstree6.workflow.Workflow;

import java.io.IOException;
import java.util.BitSet;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.DoubleAdder;
import java.util.function.BiConsumer;

import static jloda.phylo.algorithms.RootedNetworkProperties.contractEdges;

/**
 * Bootstrapping tree
 * Daniel Huson, 2.2022
 */
public class BootstrapTree extends Trees2Trees {
	private final IntegerProperty optionReplicates = new SimpleIntegerProperty(this, "optionReplicates", 100);
	private final BooleanProperty optionTransferBootstrap = new SimpleBooleanProperty(this, "optionTransferBootstrap", false);
	private final DoubleProperty optionMinPercent = new SimpleDoubleProperty(this, "optionMinPercent", 10.0);
	private final IntegerProperty optionRandomSeed = new SimpleIntegerProperty(this, "optionRandomSeed", 42);

	@Override
	public List<String> listOptions() {
		return List.of(optionReplicates.getName(), optionTransferBootstrap.getName(), optionMinPercent.getName(), optionRandomSeed.getName());
	}

	@Override
	public String getToolTip(String optionName) {
		if (!optionName.startsWith("option")) {
			optionName = "option" + optionName;
		}
		if (optionName.equals(optionReplicates.getName()))
			return "Number of bootstrap replicates";
		else if (optionName.equals(optionTransferBootstrap.getName()))
			return "Use transform bootstrapping (TBE), less susceptible to rouge taxa";
		else if (optionName.equals(optionMinPercent.getName()))
			return "Minimum percentage support for a branch to be included";
		else if (optionName.equals(optionRandomSeed.getName()))
			return "If non-zero, is used as seed for random number generator";
		return optionName;
	}

	@Override
	public String getShortDescription() {
		return "Performs bootstrapping on trees.";
	}

	@Override
	public void compute(ProgressListener progress, TaxaBlock taxaBlock, TreesBlock inputTrees, TreesBlock outputTreesBlock) throws IOException {
		setOptionReplicates(Math.max(1, optionReplicates.get()));

		setShortDescription(String.format("bootstrapping using %d replicates", getOptionReplicates()));

		var result = run(progress, getNode().getOwner(), inputTrees.getNode(), getOptionTransferBootstrap(), getOptionReplicates(), getOptionRandomSeed(), getOptionMinPercent());

		store(inputTrees, outputTreesBlock, result);
	}

	/**
	 * bootstrap, using the given alignment and pipeline rather than looking for them in the workflow
	 * <p>
	 * The same shape as {@link splitstree6.algorithms.splits.splits2splits.BootstrapSplits} and
	 * {@link splitstree6.algorithms.trees.trees2splits.BootstrapTreeSplits} have, so that a caller
	 * outside the application meets one signature rather than three. See BootstrapSplits for what the
	 * two extra arguments mean and why the pipeline arrives as a supplier;
	 * {@link splitstree6.algorithms.utils.BootstrappingUtils#newPathSupplier} builds one from a stated
	 * chain of algorithms. The pipeline must end in a step producing trees.
	 *
	 * @param characters   the alignment to resample
	 * @param pathSupplier supplies a fresh pipeline per worker thread
	 */
	public void compute(ProgressListener progress, TaxaBlock taxaBlock, TreesBlock inputTrees, TreesBlock outputTreesBlock,
						CharactersBlock characters, BootstrapSplits.PathSupplier pathSupplier) throws IOException {
		setOptionReplicates(Math.max(1, optionReplicates.get()));

		setShortDescription(String.format("bootstrapping using %d replicates", getOptionReplicates()));

		var result = run(progress, taxaBlock, inputTrees.getTree(1), characters, pathSupplier,
				getOptionTransferBootstrap(), getOptionReplicates(), getOptionRandomSeed(), getOptionMinPercent());

		store(inputTrees, outputTreesBlock, result);
	}

	private static void store(TreesBlock inputTrees, TreesBlock outputTreesBlock, PhyloTree result) {
		outputTreesBlock.getTrees().setAll(result);
		outputTreesBlock.getTree(1).setName(inputTrees.getTree(1).getName() + "-bootstrapped");
	}

	@Override
	public boolean isApplicable(TaxaBlock taxa, TreesBlock datablock) {
		if (datablock.getNTrees() != 1)
			return false;
		// Answer false rather than throwing when there is no workflow, as BootstrapTreeSplits does:
		// this used to dereference getNode() and getOwner() unguarded, so asking whether the algorithm
		// applies threw a NullPointerException outside the application, and callers ask before every run.
		var dataNode = datablock.getNode();
		if (dataNode == null || !(dataNode.getOwner() instanceof Workflow workflow))
			return false;
		var preferredParent = dataNode.getPreferredParent();
		var workingDataNode = workflow.getWorkingDataNode();
		return preferredParent != null && preferredParent.getAlgorithm() instanceof IToSingleTree
			   && workingDataNode != null && workingDataNode.getDataBlock() instanceof CharactersBlock;
	}

	/**
	 * runs bootstrapping for the given target node
	 *
	 * @param progress
	 * @param workflow
	 * @param targetNode
	 * @param transferBootstrap
	 * @param numberOfReplicates
	 * @param randomSeed
	 * @return tree with confidence values
	 * @throws IOException
	 */
	public static PhyloTree run(ProgressListener progress, Workflow workflow, DataNode<TreesBlock> targetNode, boolean transferBootstrap, int numberOfReplicates, int randomSeed, double minPercent) throws IOException {
		// Read the alignment and the pipeline out of the workflow, then hand both over, together with
		// the two other things this used the workflow for: the taxa, and the tree to annotate.
		var workingDataNode = workflow.getWorkingDataNode();
		var characters = (workingDataNode.getDataBlock() instanceof CharactersBlock c ? c : null);
		return run(progress, workflow.getWorkingTaxaBlock(), targetNode.getDataBlock().getTree(1), characters,
				() -> {
					var path = BootstrappingUtils.extractPath(workingDataNode, targetNode);
					path.get(path.size() - 1).setSecond(new TreesBlock());
					return path;
				}, transferBootstrap, numberOfReplicates, randomSeed, minPercent);
	}

	/**
	 * bootstrap a tree, using the given alignment and pipeline rather than looking for them in a workflow
	 * <p>
	 * See {@link splitstree6.algorithms.splits.splits2splits.BootstrapSplits} for what the extra arguments
	 * mean and why the pipeline arrives as a supplier: each worker thread needs its own, because the data
	 * blocks in it are output buffers. The pipeline must end in a step producing trees.
	 *
	 * @param taxaBlock  the taxa the alignment is over
	 * @param targetTree the tree whose edges are to carry the support values; it is copied, not modified
	 * @param characters the alignment to resample, or null to return the target tree unannotated
	 * @param pathSupplier supplies a fresh pipeline per worker thread
	 */
	public static PhyloTree run(ProgressListener progress, TaxaBlock taxaBlock, PhyloTree targetTree0, CharactersBlock characters,
								BootstrapSplits.PathSupplier pathSupplier, boolean transferBootstrap, int numberOfReplicates,
								int randomSeed, double minPercent) throws IOException {

		var charactersBlock = characters;
		if (charactersBlock != null) {
			if (charactersBlock.isDiploid())
				throw new IOException("Bootstrapping not implemented for diploid data, if you need this, please contact the authors!");

			var seeds = new int[numberOfReplicates];
			{
				var random = randomSeed == 0 ? new Random() : new Random(randomSeed);
				for (var i = 0; i < numberOfReplicates; i++)
					seeds[i] = random.nextInt();
			}

			var targetTree = new PhyloTree();
			targetTree.copy(targetTree0);

			try (var targetEdgeClustersMap = computeEdgeToSplitSideNotContaining1Map(targetTree);
				 EdgeArray<DoubleAdder> targetEdgeSupport = targetTree.newEdgeArray()) {
				for (var e : targetTree.edges()) {
					targetEdgeSupport.put(e, new DoubleAdder());
				}

				var ntax = taxaBlock.getNtax();

				var numberOfThreads = Math.max(1, Math.min(numberOfReplicates, ProgramExecutorService.getNumberOfCoresToUse()));

				var service = Executors.newFixedThreadPool(numberOfThreads);
				try {
					var exception = new Single<IOException>();

					progress.setMaximum(numberOfReplicates / numberOfThreads);
					progress.setProgress(0);

					for (var t = 0; t < numberOfThreads; t++) {
						var thread = t;

						service.execute(() -> {
							try {
								var path = pathSupplier.get();

								if (thread == 0)
									System.err.println("Bootstrap workflow: " + BootstrappingUtils.toString(charactersBlock, path));
								for (var r = thread; r < numberOfReplicates; r += numberOfThreads) {
									var replicateTreeBlock = (TreesBlock) BootstrapSplits.run(new ProgressSilent(), taxaBlock, BootstrappingUtils.createReplicate(charactersBlock, new Random(seeds[r])), path);
									var replicateTree = replicateTreeBlock.getTree(1);
									try (var edgeClusterMap = computeEdgeToSplitSideNotContaining1Map(replicateTree)) {
										var replicateClusters = new HashSet<>(edgeClusterMap.values());
										for (var entry : targetEdgeClustersMap.entrySet()) {
											var e = entry.getKey();
											var cluster = entry.getValue();
											if (cluster.cardinality() == 1 || cluster.cardinality() == ntax - 1 || replicateClusters.contains(cluster)) {
												targetEdgeSupport.get(e).add(1d);
											} else if (transferBootstrap) {
												if (cluster.cardinality() == 2 || cluster.cardinality() == ntax - 2) {
													targetEdgeSupport.get(e).add(1d);
												} else {
													targetEdgeSupport.get(e).add(computeTransferValue(ntax, cluster, edgeClusterMap));
												}
											}
										}
									}
									if (thread == 0)
										progress.incrementProgress();
									if (exception.isNotNull())
										return;
								}

							} catch (Exception ex) {
								exception.setIfCurrentValueIsNull(new IOException(ex));
							}
						});
					}

					service.shutdown();
					try {
						service.awaitTermination(1000, TimeUnit.DAYS);
					} catch (InterruptedException ignored) {
					}
					if (exception.isNotNull())
						throw exception.get();
					progress.reportTaskCompleted();
				} finally {
					service.shutdownNow();
				}
				applyBootstrapValues(numberOfReplicates, targetEdgeSupport, targetTree, minPercent);
			}

			return targetTree;
		} else
			throw new IOException("Bootstrapping: working data must be characters");

	}

	private static double computeTransferValue(int ntax, BitSet cluster, EdgeArray<BitSet> replicateClusters) {
		var smallest = Integer.MAX_VALUE;
		for (var replicateCluster : replicateClusters.values()) {
			var size = BitSetUtils.xor(cluster, replicateCluster).cardinality();
			smallest = NumberUtils.min(smallest, size, ntax - size);
		}
		return 1.0 - (double) smallest / Math.min(cluster.cardinality() - 1, ntax - cluster.cardinality() - 1);
	}

	public static EdgeArray<BitSet> computeEdgeToSplitSideNotContaining1Map(PhyloTree tree) {
		EdgeArray<BitSet> edgeClusterMap = tree.newEdgeArray();
		applyToEdgeUsingSplitSideNotContaining1Map(tree, edgeClusterMap::put);
		return edgeClusterMap;
	}

	public static void applyToEdgeUsingSplitSideNotContaining1Map(PhyloTree tree, BiConsumer<Edge, BitSet> consumer) {
		tree.nodeStream().filter(v -> tree.getTaxon(v) == 1).findAny()
				.ifPresent(start -> applyToEdgeUsingSplitSideNotContaining1MapRec(tree, start.getFirstAdjacentEdge().getOpposite(start), start.getFirstAdjacentEdge(), consumer));
	}

	private static BitSet applyToEdgeUsingSplitSideNotContaining1MapRec(PhyloTree tree, Node v, Edge e, BiConsumer<Edge, BitSet> consumer) {
		var belowV = BitSetUtils.asBitSet(tree.getTaxa(v));
		for (var f : v.adjacentEdges()) {
			if (f != e) {
				belowV.or(applyToEdgeUsingSplitSideNotContaining1MapRec(tree, f.getOpposite(v), f, consumer));
			}
		}
		consumer.accept(e, belowV);
		return belowV;
	}

	private static void applyBootstrapValues(int numberOfReplicates, EdgeArray<DoubleAdder> edgeSupport, PhyloTree tree, double minPercent) {
		minPercent = Math.min(100, minPercent);

		var toContract = new HashSet<Edge>();
		for (var entry : edgeSupport.entrySet()) {
			var e = entry.getKey();
			var v = e.getTarget();
			if (!v.isLeaf()) {
				var value = Math.min(100d, Math.max(0d, 100 * entry.getValue().doubleValue() / numberOfReplicates));

				if (value < minPercent) {
					toContract.add(e);
				} else if (PhyloTree.SUPPORT_RICH_NEWICK) {
					tree.setConfidence(e, Double.parseDouble("%.1f".formatted(value)));
				} else {
					tree.setLabel(v, StringUtils.trim("%.1f", value));
				}
			}
		}
		contractEdges(tree, toContract, new Single<>());
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

	public int getOptionRandomSeed() {
		return optionRandomSeed.get();
	}

	public IntegerProperty optionRandomSeedProperty() {
		return optionRandomSeed;
	}

	public void setOptionRandomSeed(int optionRandomSeed) {
		this.optionRandomSeed.set(optionRandomSeed);
	}

	public boolean getOptionTransferBootstrap() {
		return optionTransferBootstrap.get();
	}

	public BooleanProperty optionTransferBootstrapProperty() {
		return optionTransferBootstrap;
	}

	public void setOptionTransferBootstrap(boolean optionTransferBootstrap) {
		this.optionTransferBootstrap.set(optionTransferBootstrap);
	}

	@Override
	public String getCitation() {
		if (!getOptionTransferBootstrap())
			return "Felsenstein 1985;J. Felsenstein. Confidence limits on phylogenies: an approach using the bootstrap. Evolution, 39(4):783-791, 1985.;";
		else
			return "Lemoine et al 2018;F. Lemoine, JB Entfellner, E. Wilkinson, et al. Renewing Felsenstein’s phylogenetic bootstrap in the era of big data. Nature 556, 452–456, 2018.";
	}
}

