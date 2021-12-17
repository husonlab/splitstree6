/*
 *  Copyright (C) 2018. Daniel H. Huson
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

/*
 *  LayoutTreeRectangular.java Copyright (C) 2021 Daniel H. Huson
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

package splitstree6.view.trees.layout;

import javafx.geometry.Point2D;
import jloda.graph.NodeArray;
import jloda.phylo.LSAUtils;
import jloda.phylo.PhyloTree;
import jloda.util.IteratorUtils;
import jloda.util.Pair;

/**
 * computes the rectangular layout for a rooted tree or network
 * Daniel Huson, 12.2021
 */
public class LayoutTreeRectangular {
	public static NodeArray<Point2D> apply(PhyloTree tree, int[] taxon2pos, boolean toScale, ComputeTreeLayout.ParentPlacement parentPlacement) {
		final NodeArray<Point2D> nodePointMap = tree.newNodeArray();

		// compute x-coordinates:
		if (toScale) {
			var delta = tree.isRootedNetwork() ? 0.1 * computeAverageEdgeWeight(tree) : 0.0;
			LSAUtils.preorderTraversalLSA(tree, tree.getRoot(), v -> {
				double x;
				if (v.getInDegree() == 0) {
					x = 0.0;
				} else if (v.getInDegree() == 1) {
					x = nodePointMap.get(v.getParent()).getX() + tree.getWeight(v.getFirstInEdge());
				} else {
					x = v.parentsStream(false).mapToDouble(w -> nodePointMap.get(w).getX()).max().orElse(0.0) + delta;
				}
				nodePointMap.put(v, new Point2D(x, 0.0));

			});
		} else { // not to scale:
			LSAUtils.postorderTraversalLSA(tree, tree.getRoot(), v -> {
				if (v.isLeaf()) { // leaf, not lsaLeaf
					nodePointMap.put(v, new Point2D(0.0, 0.0));
				} else {
					// use children here, not LSA children
					var min = IteratorUtils.asStream(tree.lsaChildren(v)).mapToDouble(w -> nodePointMap.get(w).getX()).min().orElse(0);
					nodePointMap.put(v, new Point2D(min - 1, 0.0));
				}
			});
		}

		// compute y-coordinates:
		if (parentPlacement == ComputeTreeLayout.ParentPlacement.LeafAverage) {
			final NodeArray<Pair<Integer, Integer>> nodeFirstLastLeafYMap = tree.newNodeArray();
			LSAUtils.postorderTraversalLSA(tree, tree.getRoot(), v -> {
				if (v.isLeaf()) {
					var pos = taxon2pos[tree.getTaxa(v).iterator().next()];
					nodePointMap.put(v, new Point2D(nodePointMap.get(v).getX(), pos));
					nodeFirstLastLeafYMap.put(v, new Pair<>(pos, pos));
				}
				if (tree.isLsaLeaf(v)) { // todo: need to debug this
					var min = nodeFirstLastLeafYMap.get(v.getFirstOutEdge().getTarget()).getFirst();
					var max = nodeFirstLastLeafYMap.get(v.getLastOutEdge().getTarget()).getSecond();
					nodePointMap.put(v, new Point2D(nodePointMap.get(v).getX(), (0.5 * (min + max))));
					nodeFirstLastLeafYMap.put(v, new Pair<>(min, max));
				} else {
					var min = nodeFirstLastLeafYMap.get(tree.getFirstChildLSA(v)).getFirst();
					var max = nodeFirstLastLeafYMap.get(tree.getLastChildLSA(v)).getSecond();
					nodePointMap.put(v, new Point2D(nodePointMap.get(v).getX(), (0.5 * (min + max))));
					nodeFirstLastLeafYMap.put(v, new Pair<>(min, max));
				}
			});
		} else { // child average
			LSAUtils.postorderTraversalLSA(tree, tree.getRoot(), v -> {
				if (v.isLeaf()) {
					var pos = taxon2pos[tree.getTaxa(v).iterator().next()];
					nodePointMap.put(v, new Point2D(nodePointMap.get(v).getX(), pos));
				} else if (tree.isLsaLeaf(v)) { // todo: need to debug this
					var min = nodePointMap.get(v.getFirstOutEdge().getTarget()).getY();
					var max = nodePointMap.get(v.getLastOutEdge().getTarget()).getY();
					nodePointMap.put(v, new Point2D(nodePointMap.get(v).getX(), (0.5 * (min + max))));
				} else {
					var min = nodePointMap.get(tree.getFirstChildLSA(v)).getY();
					var max = nodePointMap.get(tree.getLastChildLSA(v)).getY();
					nodePointMap.put(v, new Point2D(nodePointMap.get(v).getX(), (0.5 * (min + max))));
				}
			});
		}
		return nodePointMap;
	}

	private static double computeAverageEdgeWeight(PhyloTree tree) {
		var weight = 0.0;
		var count = 0;
		for (var e : tree.edges()) {
			if (!tree.isSpecial(e)) {
				weight += Math.max(0, tree.getWeight(e));
				count++;
			}
		}
		return count == 0 ? 0 : weight / count;
	}
}
