/*
 *  LayoutTreeTriangular.java Copyright (C) 2021 Daniel H. Huson
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
import jloda.graph.Node;
import jloda.graph.NodeArray;
import jloda.phylo.LSAUtils;
import jloda.phylo.PhyloTree;
import jloda.util.IteratorUtils;
import jloda.util.Pair;

import java.util.Comparator;

/**
 * computes a triangular layout for a tree
 * Daniel Huson, 12.2021
 */
public class LayoutTreeTriangular {
	public static NodeArray<Point2D> apply(PhyloTree tree, int[] taxon2pos) {
		final NodeArray<Point2D> nodePointMap = tree.newNodeArray();
		final NodeArray<Pair<Node, Node>> firstLastLeafBelowMap = tree.newNodeArray();

		var root = tree.getRoot();
		if (root != null) {
			nodePointMap.put(root, new Point2D(0.0, 0.0));
			// compute all y-coordinates:
			{
				var lsaLeafHeightMap = splitstree6.view.trees.layout.LSAUtils.computeHeightForLSALeaves(tree, taxon2pos);
				LSAUtils.postorderTraversalLSA(tree, tree.getRoot(), v -> {
					if (tree.isLeaf(v)) {
						var pos = taxon2pos[tree.getTaxa(v).iterator().next()];
						nodePointMap.put(v, new Point2D(0.0, pos));
						firstLastLeafBelowMap.put(v, new Pair<>(v, v));
					} else if (tree.isLsaLeaf(v)) {
						nodePointMap.put(v, new Point2D(0.0, lsaLeafHeightMap.get(v)));
						firstLastLeafBelowMap.put(v, new Pair<>(v, v));
					} else {
						var firstLeafBelow = IteratorUtils.asStream(tree.lsaChildren(v)).map(w -> firstLastLeafBelowMap.get(w).getFirst()).map(w -> new Pair<>(nodePointMap.get(w).getY(), w))
								.min(Comparator.comparing(Pair::getFirst)).orElseThrow(null).getSecond();
						var lastLeafBelow = IteratorUtils.asStream(tree.lsaChildren(v)).map(w -> firstLastLeafBelowMap.get(w).getSecond()).map(w -> new Pair<>(nodePointMap.get(w).getY(), w))
								.max(Comparator.comparing(Pair::getFirst)).orElseThrow(null).getSecond();
						var y = 0.5 * (nodePointMap.get(firstLeafBelow).getY() + nodePointMap.get(lastLeafBelow).getY());
						var x = -(Math.abs(nodePointMap.get(lastLeafBelow).getY() - nodePointMap.get(firstLeafBelow).getY()));
						nodePointMap.put(v, new Point2D(x, y));
						firstLastLeafBelowMap.put(v, new Pair<>(firstLeafBelow, lastLeafBelow));
					}
				});
			}
		}
		return nodePointMap;
	}

}
