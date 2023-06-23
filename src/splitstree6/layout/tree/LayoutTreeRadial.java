/*
 * LayoutTreeRadial.java Copyright (C) 2023 Daniel H. Huson
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

package splitstree6.layout.tree;

import javafx.geometry.Point2D;
import jloda.fx.util.GeometryUtilsFX;
import jloda.graph.Node;
import jloda.graph.NodeArray;
import jloda.graph.algorithms.FruchtermanReingoldLayout;
import jloda.graph.fmm.FastMultiLayerMethodLayout;
import jloda.graph.fmm.FastMultiLayerMethodOptions;
import jloda.phylo.PhyloTree;
import jloda.util.APoint2D;
import org.ejml.data.DGrowArray;
import splitstree6.xtra.DavidsonHarelLayout;

import java.util.HashMap;

/**
 * compute a radial layout
 * Daniel Huson, 12.2021
 */
public class LayoutTreeRadial {
	/**
	 * compute layout for a radial phylogram
	 */
	public static NodeArray<Point2D> apply(PhyloTree tree) {
		// compute angles:
		try (var nodeAngleMap = tree.newNodeDoubleArray()) {
			HeightAndAngles.computeAngles(tree, nodeAngleMap, HeightAndAngles.Averaging.LeafAverage);

			var percentOffset = 50.0;
			var averageWeight = tree.edgeStream().mapToDouble(tree::getWeight).average().orElse(1);
			var smallOffsetForRecticulateEdge = (percentOffset / 100.0) * averageWeight;

			final NodeArray<Point2D> nodePointMap = tree.newNodeArray();
			nodePointMap.put(tree.getRoot(), new Point2D(0, 0));
			tree.preorderTraversal(v -> {
				if (v.getInDegree() == 1) {
					var e = v.getFirstInEdge();
					var weight = (e.getSource() == tree.getRoot() && !tree.getEdgeWeights().containsKey(e) ? 1.0 / e.getSource().getOutDegree() : tree.getWeight(e));
					nodePointMap.put(v, GeometryUtilsFX.translateByAngle(nodePointMap.get(e.getSource()), nodeAngleMap.get(v), weight));
				} else if (v.getInDegree() > 1) {
					var radius = 0.0;
					var angle = 0.0;
					var count = 0;
					for (var e : v.inEdges()) {
						var w = e.getSource();
						if (nodePointMap.containsKey(w)) {
							radius = Math.max(radius, nodePointMap.get(w).magnitude());
							angle += nodeAngleMap.get(w);
							count++;
						}
					}
					if (count > 0) {
						angle /= count;
						nodePointMap.put(v, GeometryUtilsFX.translateByAngle(0, 0, angle, radius + smallOffsetForRecticulateEdge));
					}
				}
			});

			if (true) {
				try (NodeArray<APoint2D<?>> nodeAPointMap = tree.newNodeArray()) {
					for (var v : nodePointMap.keySet()) {
						nodeAPointMap.put(v, new APoint2D<>(nodePointMap.get(v).getX(), nodePointMap.get(v).getY()));
					}
					var layouter = new DavidsonHarelLayout();
					layouter.setIterations(1);

					nodePointMap.clear();
					try (var result = layouter.performLayout(tree, null)) {
						for (var v : result.keySet()) {
							nodePointMap.put(v, new Point2D(result.get(v).getX(), result.get(v).getY()));
						}
					}
				}
			}
			if (false) {
				try (NodeArray<APoint2D<?>> nodeAPointMap = tree.newNodeArray()) {
					for (var v : nodePointMap.keySet()) {
						nodeAPointMap.put(v, new APoint2D<>(nodePointMap.get(v).getX(), nodePointMap.get(v).getY()));
					}
					var layouter = new FruchtermanReingoldLayout(tree, null, nodeAPointMap);
					layouter.setGravity(0.1);

					nodePointMap.clear();
					try (var result = layouter.apply(1000)) {
						for (var v : result.keySet()) {
							nodePointMap.put(v, new Point2D(result.get(v).getX(), result.get(v).getY()));
						}
					}
				}
			}
			return nodePointMap;
		}
	}
}
