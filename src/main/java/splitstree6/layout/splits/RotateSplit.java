/*
 * RotateSplit.java Copyright (C) 2024 Daniel H. Huson
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

package splitstree6.layout.splits;

import javafx.geometry.Point2D;
import jloda.fx.graph.GraphTraversals;
import jloda.fx.util.GeometryUtilsFX;
import jloda.graph.Node;
import jloda.phylo.PhyloSplitsGraph;
import splitstree6.layout.tree.LabeledNodeShape;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * rotate a split
 * Daniel Huson, 1.2022
 */
public class RotateSplit {
	/**
	 * rotate multiple splits
	 *
	 * @param splits       the splits
	 * @param angle        the angle to rotate by in degrees
	 * @param nodeShapeMap the node getShape map, note that the translateX and translateY properties will be changed
	 */
	public static void apply(Collection<Integer> splits, double angle, Map<Node, LabeledNodeShape> nodeShapeMap) {
		for (var split : splits)
			apply(split, angle, nodeShapeMap);
	}

	/**
	 * rotate about a split
	 *
	 * @param split        the split
	 * @param angle        the angle to rotate by in degrees
	 * @param nodeShapeMap the node shape map, note that the translateX and translateY properties will be changed
	 */
	public static void apply(int split, double angle, Map<Node, LabeledNodeShape> nodeShapeMap) {
		var graphOptional = nodeShapeMap.keySet().stream().map(v -> (PhyloSplitsGraph) v.getOwner()).findAny();
		if (graphOptional.isPresent()) {
			var graph = graphOptional.get();
			if (nodeShapeMap.keySet().size() > 0) {
				if (angle != 0) {
					var e = graph.edgeStream().filter(f -> graph.getSplit(f) == split).findAny().orElse(null);
					if (e != null) {
						var s = e.getSource();
						var t = e.getTarget();

						var mid = new Point2D(nodeShapeMap.get(s).getTranslateX() + nodeShapeMap.get(t).getTranslateX(),
								nodeShapeMap.get(s).getTranslateY() + nodeShapeMap.get(t).getTranslateY()).multiply(0.5);

						for (var v : List.of(s, t)) {
							var shape = nodeShapeMap.get(v);
							var posOld = new Point2D(shape.getTranslateX(), shape.getTranslateY());
							var posNew = GeometryUtilsFX.rotateAbout(posOld, -angle, mid);
							var diff = posNew.subtract(posOld);
							GraphTraversals.traverseReachable(v, f -> graph.getSplit(f) != split, w -> {
								var wShape = nodeShapeMap.get(w);
								wShape.setTranslateX(wShape.getTranslateX() + diff.getX());
								wShape.setTranslateY(wShape.getTranslateY() + diff.getY());
							});
						}
						graph.edgeStream().filter(f -> graph.getSplit(f) == split).forEach(f -> graph.setAngle(f, graph.getAngle(f) + angle));
					}
				}
			}
		}
	}
}