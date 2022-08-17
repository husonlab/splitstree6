/*
 * ConsensusSplits.java Copyright (C) 2022 Daniel H. Huson
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

import javafx.beans.property.SimpleObjectProperty;
import jloda.util.progress.ProgressListener;
import splitstree6.algorithms.splits.IToCircularSplits;
import splitstree6.data.SplitsBlock;
import splitstree6.data.TaxaBlock;
import splitstree6.data.TreesBlock;

import java.io.IOException;
import java.util.List;

/**
 * implements consensus outline
 * <p>
 * Daniel Huson, 2.2018
 */
public class ConsensusOutline extends Trees2Splits implements IToCircularSplits {
	private final SimpleObjectProperty<ConsensusNetwork.EdgeWeights> optionEdgeWeights = new SimpleObjectProperty<>(this, "optionEdgeWeights", ConsensusNetwork.EdgeWeights.TreeSizeWeightedMean);

	@Override
	public List<String> listOptions() {
		return List.of(optionEdgeWeights.getName());
	}

	@Override
	public String getToolTip(String optionName) {
		if (optionName.equals(optionEdgeWeights.getName()))
			return "Determine how to calculate edge weights in resulting network";
		else
			return super.getToolTip(optionName);
	}

	/**
	 * compute the consensus splits
	 */
	public void compute(ProgressListener progress, TaxaBlock taxaBlock, TreesBlock treesBlock, SplitsBlock splitsBlock) throws IOException {
		var consensusSplits = new ConsensusSplits();
		consensusSplits.setOptionConsensus(ConsensusSplits.Consensus.GreedyPlanar);
		consensusSplits.setOptionEdgeWeights(getOptionEdgeWeights());
		consensusSplits.compute(progress, taxaBlock, treesBlock, splitsBlock);
	}

	@Override
	public boolean isApplicable(TaxaBlock taxaBlock, TreesBlock parent) {
		return !parent.isPartial() && !parent.isReticulated();
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
