/*
 *  Utilities.java Copyright (C) 2021 Daniel H. Huson
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

package splitstree6.densitree;

import javafx.stage.FileChooser;
import splitstree6.algorithms.distances.distances2splits.neighbornet.NeighborNetCycle;
import splitstree6.algorithms.utils.SplitsUtilities;
import splitstree6.algorithms.utils.TreesUtilities;
import splitstree6.data.TreesBlock;
import splitstree6.data.parts.ASplit;
import splitstree6.io.readers.trees.NewickReader;

import java.util.ArrayList;
import java.util.BitSet;

public class Utilities {

	public static FileChooser.ExtensionFilter getExtensionFilter() {
		return (new NewickReader()).getExtensionFilter();
	}

	public static int[] computeCycle(int ntax, TreesBlock treesBlock) {
		var taxa = new BitSet();
		for (var t = 1; t <= ntax; t++)
			taxa.set(t);
		int max_num_nodes = 3 * ntax - 5;
		var distances = new double[max_num_nodes][max_num_nodes];
		var step = Math.max(1, treesBlock.getNTrees() / 1000);
		var trees = treesBlock.getTrees();
		for (int i = 0; i < trees.size(); i += step) {
			var tree = trees.get(i);
			var splits = new ArrayList<ASplit>();
			TreesUtilities.computeSplits(taxa, tree, splits);
			SplitsUtilities.splitsToDistances(ntax, splits, distances, false);
		}
		return NeighborNetCycle.compute(ntax, distances);
	}

}
