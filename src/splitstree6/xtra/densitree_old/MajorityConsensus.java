/*
 *  MajorityConsensus.java Copyright (C) 2023 Daniel H. Huson
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

package splitstree6.xtra.densitree_old;

import jloda.phylo.PhyloTree;
import jloda.util.progress.ProgressPercentage;
import splitstree6.algorithms.trees.trees2trees.ConsensusTree;
import splitstree6.data.TreesBlock;

import java.io.IOException;

/**
 * computes the majority consensus tree
 * Daniel Huson, 3.2022
 */
@Deprecated
public class MajorityConsensus {
	/**
	 * computes the majority consensus tree
	 */
	public static PhyloTree apply(Model model) throws IOException {
		var consensus = new ConsensusTree();
		consensus.setOptionConsensus(ConsensusTree.Consensus.Majority);
		if (!consensus.isApplicable(model.getTaxaBlock(), model.getTreesBlock()))
			throw new IOException("Majority consensus: not applicable");
		var result = new TreesBlock();
		consensus.compute(new ProgressPercentage("Compute", "consensus"), model.getTaxaBlock(), model.getTreesBlock(), result);
		return result.getTree(1);
	}
}
