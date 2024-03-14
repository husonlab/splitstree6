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

import jloda.fx.util.ProgramProperties;
import jloda.util.progress.ProgressListener;
import splitstree6.algorithms.splits.IToCircularSplits;
import splitstree6.data.SplitsBlock;
import splitstree6.data.TaxaBlock;
import splitstree6.data.TreesBlock;

import java.io.IOException;
import java.util.List;

/**
 * runs consensus outline
 * <p>
 * Daniel Huson, 2.2018
 */
public class ConsensusOutline extends ConsensusSplits implements IToCircularSplits {
	@Override
	public String getCitation() {
		return "Huson & Cetinkaya 2023;DH Huson and B Cetinkaya, Visualizing incompatibilities in phylogenetic trees using consensus outlines, Front. Bioinform, 2023.";
	}

	@Override
	public String getShortDescription() {
		return "Computes the consensus outline.";
	}

	@Override
	public List<String> listOptions() {
		return List.of(optionEdgeWeights.getName(), optionThresholdPercent.getName());
	}

	public ConsensusOutline() {
		setOptionConsensus(Consensus.ConsensusOutline);
		setOptionThresholdPercent(0.0);
		setOptionHighDimensionFilter(false);
		ProgramProperties.track(optionEdgeWeightsProperty(), EdgeWeights::valueOf, EdgeWeights.Count);
	}

	/**
	 * compute the consensus splits
	 */
	public void compute(ProgressListener progress, TaxaBlock taxaBlock, TreesBlock treesBlock, SplitsBlock splitsBlock) throws IOException {
		super.compute(progress, taxaBlock, treesBlock, splitsBlock);
	}
}
