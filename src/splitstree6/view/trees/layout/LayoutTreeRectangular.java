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

/**
 * computes the rectangular layout for a rooted tree or network
 * Daniel Huson, 12.2021
 */
public class LayoutTreeRectangular {
	public static NodeArray<Point2D> apply(PhyloTree tree, int[] taxon2pos, boolean toScale) {
		// compute x-coordinates:
		try (var nodeXMap = tree.newNodeDoubleArray(); var nodeYMap = tree.newNodeDoubleArray()) {
			if (toScale) {
				var delta = tree.isReticulated() ? 0.25 * computeAverageEdgeWeight(tree) : 0.0;
				LSAUtils.preorderTraversalLSA(tree, tree.getRoot(),
						v -> nodeXMap.put(v,
								switch (v.getInDegree()) {
									case 0 -> 0.0;
									case 1 -> nodeXMap.get(v.getParent()) + tree.getWeight(v.getFirstInEdge());
									default -> v.parentsStream(false).mapToDouble(nodeXMap::get).max().orElse(0.0) + delta;
								}));
			} else { // not to scale:
				try (var visited = tree.newNodeSet()) {
					tree.postorderTraversal(tree.getRoot(), v -> !visited.contains(v), v -> {
						visited.add(v);
						if (v.isLeaf()) { // leaf, not lsaLeaf
							nodeXMap.put(v, 0.0);
						} else {
							// use children here, not LSA children
							var min = v.outEdgesStream(false).filter(e -> !tree.isTransferEdge(e)).mapToDouble(e -> nodeXMap.get(e.getTarget())).min().orElse(0);
							nodeXMap.put(v, min - 1.0);
						}
					});
				}
			}

			// compute y-coordinates:
			var lsaLeafHeightMap = splitstree6.view.trees.layout.LSAUtils.computeHeightForLSALeaves(tree, taxon2pos);
			LSAUtils.postorderTraversalLSA(tree, tree.getRoot(), v -> {
				if (tree.isLeaf(v)) {
					var y = taxon2pos[tree.getTaxa(v).iterator().next()];
					nodeYMap.put(v, (double) y);
				} else if (tree.isLsaLeaf(v)) {
					nodeYMap.put(v, lsaLeafHeightMap.get(v));
				} else {
					var yMin = IteratorUtils.asStream(tree.lsaChildren(v)).filter(w -> v.getEdgeTo(w) != null)
							.mapToDouble(nodeYMap::get).min().orElse(0);
					var yMax = IteratorUtils.asStream(tree.lsaChildren(v)).filter(w -> v.getEdgeTo(w) != null)
							.mapToDouble(nodeYMap::get).max().orElse(0);
					nodeYMap.put(v, 0.5 * (yMin + yMax));
				}
			});

			final NodeArray<Point2D> nodePointMap = tree.newNodeArray();
			for (var v : tree.nodes()) {
				nodePointMap.put(v, new Point2D(nodeXMap.get(v), nodeYMap.get(v)));
			}

			return nodePointMap;
		}
	}

	public static double computeAverageEdgeWeight(PhyloTree tree) {
		var weight = 0.0;
		var count = 0;
		for (var e : tree.edges()) {
			if (!tree.isReticulatedEdge(e)) {
				weight += Math.max(0, tree.getWeight(e));
				count++;
			}
		}
		return count == 0 ? 0 : weight / count;
	}
}
