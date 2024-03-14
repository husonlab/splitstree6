/*
 * GreedyTree.java Copyright (C) 2024 Daniel H. Huson
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

package splitstree6.algorithms.splits.splits2trees;

import jloda.util.progress.ProgressListener;
import splitstree6.algorithms.utils.GreedyCompatible;
import splitstree6.algorithms.utils.RerootingUtils;
import splitstree6.data.SplitsBlock;
import splitstree6.data.TaxaBlock;
import splitstree6.data.TreesBlock;
import splitstree6.splits.ASplit;
import splitstree6.splits.TreesUtils;

import java.io.IOException;

/**
 * greedy tree
 * Daniel Huson, 12.2017
 */
public class GreedyTree extends Splits2Trees {
	@Override
	public String getCitation() {
		return "Huson et al 2012;DH Huson, R. Rupp and C. Scornavacca, Phylogenetic Networks, Cambridge, 2012.";
	}

	public String getShortDescription() {
		return "Produces a phylogenetic tree based on greedily selecting a compatible set of splits.";
	}

	@Override
	public void compute(ProgressListener progress, TaxaBlock taxaBlock, SplitsBlock splits, TreesBlock trees) throws IOException {
		progress.setTasks("Greedy Tree", "Extracting compatible splits...");

		final var compatibleSplits = GreedyCompatible.apply(progress, splits.getSplits(), ASplit::getWeight);
		var tree = TreesUtils.computeTreeFromCompatibleSplits(taxaBlock::getLabel, compatibleSplits);
		RerootingUtils.rerootByMidpoint(tree);
		trees.getTrees().setAll(tree);
		trees.setRooted(true);
		trees.setPartial(false);
		progress.reportTaskCompleted();
	}
}
