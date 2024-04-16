/*
 *  ConsensusNetwork.java Copyright (C) 2024 Daniel H. Huson
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

import jloda.fx.util.ProgramProperties;
import jloda.util.progress.ProgressListener;
import splitstree6.data.SplitsBlock;
import splitstree6.data.TaxaBlock;
import splitstree6.data.TreesBlock;

import java.io.IOException;
import java.util.List;

/**
 * run consensus network
 *
 * @author Daniel Huson, 1/2017
 */
public class ConsensusNetwork extends ConsensusSplits {

	@Override
	public String getCitation() {
		return "Holland et al 2004; BR Holland, KT Huber, V Moulton, and PJ Lockhart." +
			   " Using consensus networks to visualize contradictory evidence for species phylogeny." +
			   " Molecular Biology and Evolution, 21 (7):1459–1461, 2004.";
	}

	@Override
	public String getShortDescription() {
		return "Computes the consensus network.";
	}

	public List<String> listOptions() {
		return List.of(optionEdgeWeights.getName(), optionThresholdPercent.getName(), optionHighDimensionFilter.getName());
	}

	public ConsensusNetwork() {
		setOptionThresholdPercent(30);
		ProgramProperties.track(optionEdgeWeightsProperty(), EdgeWeights::valueOf, EdgeWeights.Count);
	}

	/**
	 * compute the consensus splits
	 */
	public void compute(ProgressListener progress, TaxaBlock taxaBlock, TreesBlock treesBlock, SplitsBlock splitsBlock) throws IOException {
		setOptionConsensus(Consensus.ConsensusNetwork);
		super.compute(progress, taxaBlock, treesBlock, splitsBlock);
	}
}
