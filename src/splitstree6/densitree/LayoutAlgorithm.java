/*
 *  LayoutAlgorithm.java Copyright (C) 2021 Daniel H. Huson
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

import javafx.geometry.Point2D;
import jloda.graph.NodeArray;
import jloda.phylo.PhyloTree;
import jloda.util.IteratorUtils;

public class LayoutAlgorithm {
	public static NodeArray<Point2D> apply(PhyloTree tree, boolean toScale, int[] cycle) {

		final NodeArray<Point2D> nodePointMap = tree.newNodeArray();

		int numberOfTaxa = cycle.length - 1; // cycle[0] is not used
		if (numberOfTaxa > 0) {
			var delta = 360.0 / numberOfTaxa;

			try (var nodeAngleMap = tree.newNodeDoubleArray()) {
				tree.postorderTraversal(v -> {
					if (v.isLeaf()) {
						nodeAngleMap.put(v, IteratorUtils.asStream(tree.getTaxa(v)).mapToInt(t -> cycle[t]).average().orElse(0));
					}
				});

			}
		}
		return nodePointMap;
	}
}
