/*
 *  BootstrapTreeSplits.java Copyright (C) 2024 Daniel H. Huson
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

package splitstree6.algorithms.trees.trees2splits;

import javafx.beans.property.*;
import jloda.util.progress.ProgressListener;
import splitstree6.algorithms.splits.splits2splits.BootstrapSplits;
import splitstree6.algorithms.trees.IToSingleTree;
import splitstree6.algorithms.trees.trees2trees.BootstrapTree;
import splitstree6.data.CharactersBlock;
import splitstree6.algorithms.utils.BootstrappingUtils;
import jloda.util.Pair;
import splitstree6.data.SplitsBlock;
import splitstree6.data.TaxaBlock;
import splitstree6.data.TreesBlock;
import splitstree6.splits.Compatibility;
import splitstree6.splits.SplitUtils;
import splitstree6.workflow.Workflow;

import java.io.IOException;
import java.util.List;

/**
 * bootstrap a tree to get splits
 *
 * @author Daniel Huson, 2.2022
 */
public class BootstrapTreeSplits extends Trees2Splits {
	private final IntegerProperty optionReplicates = new SimpleIntegerProperty(this, "optionReplicates", 100);
	private final DoubleProperty optionMinPercent = new SimpleDoubleProperty(this, "optionMinPercent", 10.0);
	private final BooleanProperty optionShowAllSplits = new SimpleBooleanProperty(this, "optionShowAllSplits", false);
	private final IntegerProperty optionRandomSeed = new SimpleIntegerProperty(this, "optionRandomSeed", 42);
	private final BooleanProperty optionHighDimensionFilter = new SimpleBooleanProperty(this, "optionHighDimensionFilter", true);

	@Override
	public String getCitation() {
		return (new BootstrapTree()).getCitation();
	}

	@Override
	public String getShortDescription() {
		return "Performs bootstrapping on trees.";
	}

	@Override
	public List<String> listOptions() {
		return List.of(optionReplicates.getName(), optionMinPercent.getName(), optionShowAllSplits.getName(), optionRandomSeed.getName(), optionHighDimensionFilter.getName());
	}

	@Override
	public String getToolTip(String optionName) {
		return (new BootstrapSplits()).getToolTip(optionName);
	}

	@Override
	public void compute(ProgressListener progress, TaxaBlock taxaBlock, TreesBlock inputTrees, SplitsBlock splitsBlock) throws IOException {
		// Read the alignment and the pipeline out of the workflow, then hand both over, exactly as
		// BootstrapSplits does. The tree-to-splits conversion below needs neither.
		var workflow = (Workflow) taxaBlock.getNode().getOwner();
		var workingDataNode = workflow.getWorkingDataNode();
		var charactersBlock = (workingDataNode.getDataBlock() instanceof CharactersBlock characters ? characters : null);
		var targetNode = inputTrees.getNode();

		compute(progress, taxaBlock, inputTrees, splitsBlock, charactersBlock,
				() -> {
					var path = BootstrappingUtils.extractPath(workingDataNode, targetNode);
					// the target is a trees node, so the pipeline needs a step that turns the replicate
					// tree into splits; see BootstrapSplits for why this is built fresh per thread
					path.add(new Pair<>(new TreeSelectorSplits(), new SplitsBlock()));
					return path;
				});
	}

	/**
	 * bootstrap, using the given alignment and pipeline rather than looking for them in the workflow
	 * <p>
	 * See {@link BootstrapSplits#compute(ProgressListener, TaxaBlock, SplitsBlock, SplitsBlock, CharactersBlock,
	 * BootstrapSplits.PathSupplier)} for what the two extra arguments mean and why the pipeline arrives as a
	 * supplier. The pipeline must end in a step producing splits, since that is what this aggregates.
	 *
	 * @param charactersBlock the alignment to resample, or null to produce only the trivial splits
	 * @param pathSupplier    supplies a fresh pipeline per worker thread
	 */
	public void compute(ProgressListener progress, TaxaBlock taxaBlock, TreesBlock inputTrees, SplitsBlock splitsBlock,
						CharactersBlock charactersBlock, BootstrapSplits.PathSupplier pathSupplier) throws IOException {
		setOptionReplicates(Math.max(1, optionReplicates.get()));

		setShortDescription(String.format("bootstrapping using %d replicates", getOptionReplicates()));

		var tree = inputTrees.getTree(1);
		var inputSplits = new SplitsBlock();

		{
			var taxaInTree = SplitUtils.computeSplits(taxaBlock.getTaxaSet(), tree, inputSplits.getSplits());

			if (taxaInTree.cardinality() != taxaBlock.getNtax())
				throw new IOException("Unexpected number of taxa in tree");
			inputSplits.setCompatibility(Compatibility.compatible);
		}

		var bootstrapSplits = new BootstrapSplits();
		bootstrapSplits.setOptionShowAllSplits(isOptionShowAllSplits());
		bootstrapSplits.setOptionMinPercent(getOptionMinPercent());
		bootstrapSplits.setOptionRandomSeed(getOptionRandomSeed());
		bootstrapSplits.setOptionReplicates(getOptionReplicates());
		bootstrapSplits.setOptionHighDimensionFilter(isOptionHighDimensionFilter());
		bootstrapSplits.compute(progress, taxaBlock, inputSplits, splitsBlock, charactersBlock, pathSupplier);
	}

	@Override
	public boolean isApplicable(TaxaBlock taxa, TreesBlock datablock) {
		if (datablock.getNTrees() != 1)
			return false;
		// Answer false rather than throwing when there is no workflow. This used to dereference
		// getNode() and getOwner() unguarded, so asking whether the algorithm applies threw a
		// NullPointerException outside the application - and callers ask before every run.
		var dataNode = datablock.getNode();
		if (dataNode == null || !(dataNode.getOwner() instanceof Workflow workflow))
			return false;
		var preferredParent = dataNode.getPreferredParent();
		var workingDataNode = workflow.getWorkingDataNode();
		return preferredParent != null && preferredParent.getAlgorithm() instanceof IToSingleTree
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

	public boolean isOptionHighDimensionFilter() {
		return optionHighDimensionFilter.get();
	}

	public BooleanProperty optionHighDimensionFilterProperty() {
		return optionHighDimensionFilter;
	}

	public void setOptionHighDimensionFilter(boolean optionHighDimensionFilter) {
		this.optionHighDimensionFilter.set(optionHighDimensionFilter);
	}
}
