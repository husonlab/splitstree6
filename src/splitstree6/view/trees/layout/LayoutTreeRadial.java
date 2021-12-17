/*
 *  LayoutTreeRadial.java Copyright (C) 2021 Daniel H. Huson
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
import jloda.fx.util.GeometryUtilsFX;
import jloda.graph.Node;
import jloda.graph.NodeArray;
import jloda.graph.NodeDoubleArray;
import jloda.phylo.LSAUtils;
import jloda.phylo.PhyloTree;
import jloda.util.IteratorUtils;
import jloda.util.Pair;

import java.util.concurrent.atomic.LongAccumulator;

/**
 * compute a radial layout
 * Daniel Huson, 12.2021
 */
public class LayoutTreeRadial {
	/**
	 * compute layout for a radial cladogram
	 */
	public static NodeArray<Point2D> applyCladogram(PhyloTree tree, int[] taxon2pos, NodeDoubleArray nodeAngleMap, boolean toScale) {
		final NodeArray<Point2D> nodePointMap = tree.newNodeArray();

		final var numberOfLeaves = tree.nodeStream().filter(tree::isLsaLeaf).count();
		if (numberOfLeaves > 0) {
			final var delta = 360.0 / numberOfLeaves;

			final NodeDoubleArray nodeRadiusMap = tree.newNodeDoubleArray();

			final var maxDepth = computeMaxDepth(tree);
			LSAUtils.postorderTraversalLSA(tree, tree.getRoot(), v -> {
				if (tree.isLsaLeaf(v)) {
					nodeRadiusMap.put(v, (double) maxDepth);
				} else {
					nodeRadiusMap.put(v, IteratorUtils.asStream(tree.lsaChildren(v)).mapToDouble(nodeRadiusMap::get).min().orElse(maxDepth) - 1);
				}
			});
			nodeRadiusMap.put(tree.getRoot(), 0.0);

			nodeAngleMap.put(tree.getRoot(), 0.0);
			final NodeArray<Pair<Node, Node>> firstLastLeafBelowMap = tree.newNodeArray();
			LSAUtils.postorderTraversalLSA(tree, tree.getRoot(), v -> {
				if (tree.isLsaLeaf(v)) {
					firstLastLeafBelowMap.put(v, new Pair<>(v, v));
					var pos = taxon2pos[tree.getTaxa(v).iterator().next()];
					nodeAngleMap.put(v, pos * delta);
				} else {
					var firstLeafBelow = firstLastLeafBelowMap.get(tree.getFirstChildLSA(v)).getFirst();
					var lastLeafBelow = firstLastLeafBelowMap.get(tree.getLastChildLSA(v)).getSecond();
					firstLastLeafBelowMap.put(v, new Pair<>(firstLeafBelow, lastLeafBelow));
					nodeAngleMap.put(v, 0.5 * (nodeAngleMap.get(firstLeafBelow) + nodeAngleMap.get(lastLeafBelow)));
				}
				if (toScale && v.getInDegree() > 0) {
					var e = v.getFirstInEdge();
					var parentRadius = nodeRadiusMap.get(e.getSource());
					nodeRadiusMap.put(v, parentRadius + tree.getWeight(e));
				}
			});
			tree.nodeStream().forEach(v -> nodePointMap.put(v, GeometryUtilsFX.computeCartesian(nodeRadiusMap.get(v), nodeAngleMap.get(v))));
		}
		return nodePointMap;
	}

	/**
	 * compute layout for a radial phylogram
	 */
	public static NodeArray<Point2D> applyPhylogram(PhyloTree tree, int[] taxon2pos, ComputeTreeLayout.ParentPlacement parentPlacement) {
		final NodeArray<Point2D> nodePointMap = tree.newNodeArray();

		final var numberOfLeaves = tree.nodeStream().filter(tree::isLsaLeaf).count();
		if (numberOfLeaves > 0) {
			final var delta = 360.0 / numberOfLeaves;

			final NodeDoubleArray nodeAngleMap = tree.newNodeDoubleArray();
			if (parentPlacement == ComputeTreeLayout.ParentPlacement.LeafAverage) {
				final NodeArray<Pair<Node, Node>> firstLastLeafBelowMap = tree.newNodeArray();
				LSAUtils.postorderTraversalLSA(tree, tree.getRoot(), v -> {
					final double angle;
					if (tree.isLsaLeaf(v)) {
						firstLastLeafBelowMap.put(v, new Pair<>(v, v));
						var pos = taxon2pos[tree.getTaxa(v).iterator().next()];
						angle = pos * delta;
					} else {
						var firstLeafBelow = firstLastLeafBelowMap.get(tree.getFirstChildLSA(v)).getFirst();
						var lastLeafBelow = firstLastLeafBelowMap.get(tree.getLastChildLSA(v)).getSecond();
						firstLastLeafBelowMap.put(v, new Pair<>(firstLeafBelow, lastLeafBelow));
						angle = 0.5 * (nodeAngleMap.get(firstLeafBelow) + nodeAngleMap.get(lastLeafBelow));
					}
					nodeAngleMap.put(v, angle);
				});
			} else {
				LSAUtils.postorderTraversalLSA(tree, tree.getRoot(), v -> {
					final double angle;
					if (tree.isLsaLeaf(v)) {
						var pos = taxon2pos[tree.getTaxa(v).iterator().next()];
						angle = pos * delta;
					} else
						angle = IteratorUtils.asStream(tree.lsaChildren(v)).mapToDouble(nodeAngleMap::get).sum() / v.getOutDegree();
					nodeAngleMap.put(v, angle);
				});
			}
			nodePointMap.put(tree.getRoot(), new Point2D(0, 0));
			LSAUtils.preorderTraversalLSA(tree, tree.getRoot(), v -> {
				var p = nodePointMap.get(v);
				for (var e : v.outEdges()) {
					nodePointMap.put(e.getTarget(), GeometryUtilsFX.translateByAngle(p, nodeAngleMap.get(e.getTarget()), tree.getWeight(e)));
				}
			});
		}
		return nodePointMap;
	}

	/**
	 * compute the maximum number of edges from the root to a leaf
	 *
	 * @param tree the tree
	 * @return length of longest path
	 */
	public static int computeMaxDepth(PhyloTree tree) {
		var max = new LongAccumulator(Math::max, 0);
		LSAUtils.breathFirstTraversalLSA(tree, tree.getRoot(), 0, (level, v) -> max.accumulate(level));
		return max.intValue();
	}
}
