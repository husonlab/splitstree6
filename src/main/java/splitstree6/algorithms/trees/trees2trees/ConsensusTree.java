/*
 *  ConsensusTree.java Copyright (C) 2024 Daniel H. Huson
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

import javafx.beans.property.SimpleObjectProperty;
import jloda.util.progress.ProgressListener;
import splitstree6.algorithms.splits.splits2trees.GreedyTree;
import splitstree6.algorithms.trees.trees2splits.ConsensusSplits;
import splitstree6.data.SplitsBlock;
import splitstree6.data.TaxaBlock;
import splitstree6.data.TreesBlock;

import java.io.IOException;
import java.util.List;

/**
 * computes a consensus tree from a list of trees
 * Daniel Huson, 2.2018
 */
public class ConsensusTree extends Trees2Trees {
	public enum Consensus {Majority, Greedy, Strict}

	public enum EdgeWeights {Mean, TreeSizeWeightedMean}

	;

	private final SimpleObjectProperty<Consensus> optionConsensus = new SimpleObjectProperty<>(this, "optionConsensus", Consensus.Majority);

	@Override
	public List<String> listOptions() {
		return List.of(optionConsensus.getName());
	}

	@Override
	public String getToolTip(String optionName) {
		if (!optionName.startsWith("option"))
			optionName = "option" + optionName;

		if (optionName.equals(optionConsensus.getName()))
			return "Consensus method to use";
		else
			return super.getToolTip(optionName);
	}

	@Override
	public String getCitation() {
		return "Bryant 2001;D. Bryant, A classification of consensus methods for phylogenetics, in Bioconsensus, 2001.";
	}

	@Override
	public String getShortDescription() {
		return "Provides several methods for computing a consensus tree.";
	}

	@Override
	public void compute(ProgressListener progress, TaxaBlock taxaBlock, TreesBlock parent, TreesBlock child) throws IOException {
		if (parent.getNTrees() <= 1)
			child.getTrees().addAll(parent.getTrees());
		else {
			final ConsensusSplits consensusTreeSplits = new ConsensusSplits();
			consensusTreeSplits.setOptionConsensus(getOptionConsensus().name());
			consensusTreeSplits.setOptionEdgeWeights(ConsensusSplits.EdgeWeights.Mean);
			final SplitsBlock splitsBlock = new SplitsBlock();
			consensusTreeSplits.compute(progress, taxaBlock, parent, splitsBlock);
			final GreedyTree greedyTree = new GreedyTree();
			greedyTree.compute(progress, taxaBlock, splitsBlock, child);
		}
	}

	@Override
	public boolean isApplicable(TaxaBlock taxaBlock, TreesBlock parent) {
		return !parent.isPartial();
	}

	public Consensus getOptionConsensus() {
		return optionConsensus.get();
	}

	public SimpleObjectProperty<Consensus> optionConsensusProperty() {
		return optionConsensus;
	}

	public void setOptionConsensus(Consensus optionConsensus) {
		this.optionConsensus.set(optionConsensus);
	}
}
