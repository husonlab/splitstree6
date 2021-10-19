/*
 * ConsensusTree.java Copyright (C) 2021. Daniel H. Huson
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

package splitstree6.algorithms.trees.trees2trees;

import javafx.beans.property.SimpleObjectProperty;
import jloda.util.progress.ProgressListener;
import splitstree6.algorithms.splits.splits2trees.GreedyTree;
import splitstree6.algorithms.trees.trees2splits.ConsensusNetwork;
import splitstree6.algorithms.trees.trees2splits.ConsensusTreeSplits;
import splitstree6.data.SplitsBlock;
import splitstree6.data.TaxaBlock;
import splitstree6.data.TreesBlock;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * computes a consensus tree from a list of trees
 * Daniel Huson, 2.2018
 */
public class ConsensusTree extends Trees2Trees {

	private final SimpleObjectProperty<ConsensusTreeSplits.Consensus> optionConsensus = new SimpleObjectProperty<>(ConsensusTreeSplits.Consensus.Majority);
	private final SimpleObjectProperty<ConsensusNetwork.EdgeWeights> optionEdgeWeights = new SimpleObjectProperty<>(ConsensusNetwork.EdgeWeights.TreeSizeWeightedMean);

	@Override
	public List<String> listOptions() {
		return Arrays.asList("Consensus", "EdgeWeights");
	}

	@Override
	public String getToolTip(String optionName) {
		return switch (optionName) {
			case "EdgeWeights" -> "Determine how to calculate edge weights in resulting network";
			case "Consensus" -> "Consensus method to use";
			default -> optionName;
		};
	}

	@Override
	public void compute(ProgressListener progress, TaxaBlock taxaBlock, TreesBlock parent, TreesBlock child) throws IOException {
		if (parent.getNTrees() <= 1)
			child.getTrees().addAll(parent.getTrees());
		else {
			final ConsensusTreeSplits consensusTreeSplits = new ConsensusTreeSplits();
			consensusTreeSplits.setOptionConsensus(getOptionConsensus());
			consensusTreeSplits.setOptionEdgeWeights(getOptionEdgeWeights());
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

	public ConsensusTreeSplits.Consensus getOptionConsensus() {
		return optionConsensus.get();
	}

	public SimpleObjectProperty<ConsensusTreeSplits.Consensus> optionConsensusProperty() {
		return optionConsensus;
	}

	public void setOptionConsensus(ConsensusTreeSplits.Consensus optionConsensus) {
		this.optionConsensus.set(optionConsensus);
	}

	public ConsensusNetwork.EdgeWeights getOptionEdgeWeights() {
		return optionEdgeWeights.get();
	}

	public SimpleObjectProperty<ConsensusNetwork.EdgeWeights> optionEdgeWeightsProperty() {
		return optionEdgeWeights;
	}

	public void setOptionEdgeWeights(ConsensusNetwork.EdgeWeights optionEdgeWeights) {
		this.optionEdgeWeights.set(optionEdgeWeights);
	}

}
