/*
 *  MinSpanningTree.java Copyright (C) 2024 Daniel H. Huson
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

package splitstree6.algorithms.distances.distances2trees;

import jloda.phylo.PhyloTree;
import jloda.util.progress.ProgressListener;
import splitstree6.algorithms.distances.distances2network.MinSpanningNetwork;
import splitstree6.algorithms.trees.trees2trees.RerootOrReorderTrees;
import splitstree6.data.DistancesBlock;
import splitstree6.data.NetworkBlock;
import splitstree6.data.TaxaBlock;
import splitstree6.data.TreesBlock;

import java.io.IOException;

/**
 * computes a minimum spanning tree
 * Daniel Huson, 4.2022
 */
public class MinSpanningTree extends Distances2Trees {
	@Override
	public void compute(ProgressListener progress, TaxaBlock taxaBlock, DistancesBlock inputData, TreesBlock outputData) throws IOException {
		var minSpanningNetwork = new MinSpanningNetwork();
		minSpanningNetwork.setOptionMinSpanningTree(true);

		var networkBlock = new NetworkBlock();
		minSpanningNetwork.compute(progress, taxaBlock, inputData, networkBlock);

		var tree = new PhyloTree();
		tree.copy(networkBlock.getGraph());
		tree.setRoot(tree.getFirstNode());
		tree.redirectEdgesAwayFromRoot();
		//System.err.println(tree.toBracketString(true) + ";");
		var reroot = new RerootOrReorderTrees();
		reroot.setOptionRootBy(RerootOrReorderTrees.RootBy.MidPoint);
		var tmpTreesBlock = new TreesBlock();
		tree.setName("MinSpanningTree");
		tmpTreesBlock.getTrees().add(tree);
		reroot.compute(progress, taxaBlock, tmpTreesBlock, outputData);
	}
}