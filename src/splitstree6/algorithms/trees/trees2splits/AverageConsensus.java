/*
 *  AverageConsensus.java Copyright (C) 2022 Daniel H. Huson
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

import jloda.util.progress.ProgressListener;
import splitstree6.algorithms.distances.distances2splits.NeighborNet;
import splitstree6.algorithms.trees.trees2distances.AverageDistances;
import splitstree6.data.DistancesBlock;
import splitstree6.data.SplitsBlock;
import splitstree6.data.TaxaBlock;
import splitstree6.data.TreesBlock;

import java.io.IOException;

/**
 * average consensus
 * Daniel Huson, 10.2021
 */

public class AverageConsensus extends Trees2Splits {
	@Override
	public void compute(ProgressListener progress, TaxaBlock taxaBlock, TreesBlock treesBlock, SplitsBlock splitsBlock) throws IOException {
		var pairwiseDistances = new DistancesBlock();
		var averageDistances = new AverageDistances();
		averageDistances.compute(progress, taxaBlock, treesBlock, pairwiseDistances);

		//StringWriter sw = new StringWriter();
		//new DistancesNexusOutput().write(sw, taxaBlock, pairwiseDistances, null);
		//dist.write(sw, taxa);
		//System.out.println(sw.toString());

		final var nnet = new NeighborNet();
		nnet.compute(progress, taxaBlock, pairwiseDistances, splitsBlock);
	}

	@Override
	public boolean isApplicable(TaxaBlock taxaBlock, TreesBlock parent) {
		return !parent.isPartial() && !parent.isReticulated();
	}
}
