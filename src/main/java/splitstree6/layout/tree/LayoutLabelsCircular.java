/*
 *  LayoutLabelsCircular.java Copyright (C) 2024 Daniel H. Huson
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

package splitstree6.layout.tree;

import javafx.beans.InvalidationListener;
import javafx.geometry.Point2D;
import javafx.scene.Group;
import jloda.fx.util.GeometryUtilsFX;
import jloda.graph.Node;
import jloda.phylo.PhyloTree;

import java.util.Map;

/**
 * create labels for a circular layout
 * Daniel Huson, 12.2021
 */
public class LayoutLabelsCircular {

	public static void apply(PhyloTree tree, Map<Node, LabeledNodeShape> nodeShapeMap, Map<Node, Double> nodeAngleMap,
							 double labelGap, Group labelConnectors) {

		var alignLabels = (labelConnectors != null);

		for (var v : tree.nodes()) {
			var shape = nodeShapeMap.get(v);
			var label = shape.getLabel();
			if (label != null) {
				LabelConnector labelConnector;
				if (alignLabels) {
					labelConnector = new LabelConnector();
					labelConnectors.getChildren().add(labelConnector);
				} else labelConnector = null;

				InvalidationListener invalidationListener = a -> {
					if (label.getWidth() > 0 && label.getHeight() > 0) {
						final double maxRadius;
						if (alignLabels) {
							maxRadius = tree.nodeStream().map(nodeShapeMap::get).mapToDouble(s -> GeometryUtilsFX.magnitude(s.getTranslateX(), s.getTranslateY())).max().orElse(0);
						} else
							maxRadius = Double.MIN_VALUE;

						var angle = nodeAngleMap.get(v);
						if (angle == null) {
							if (v.getParent() != null) {
								var vPoint = new Point2D(shape.getTranslateX(), shape.getTranslateY());
								var w = v.getParent();
								var wPoint = new Point2D(nodeShapeMap.get(w).getTranslateX(), nodeShapeMap.get(w).getTranslateY());
								while (vPoint.distance(wPoint) == 0 && w.getParent() != null) {
									w = w.getParent();
									wPoint = new Point2D(nodeShapeMap.get(w).getTranslateX(), nodeShapeMap.get(w).getTranslateY());
								}
								angle = GeometryUtilsFX.computeAngle(shape.getTranslateX() - wPoint.getX(), shape.getTranslateY() - wPoint.getY());
							} else
								angle = 0.0;
						}
						var add = v.isLeaf() ? (maxRadius > Double.MIN_VALUE ? maxRadius - GeometryUtilsFX.magnitude(shape.getTranslateX(), shape.getTranslateY()) : 0) : -10;

						var offset = GeometryUtilsFX.translateByAngle(0, 0, angle, add + labelGap + 0.5 * label.getWidth());
						label.translateXProperty().bind(shape.translateXProperty().add(-0.5 * label.getWidth() + offset.getX()));
						label.translateYProperty().bind(shape.translateYProperty().add(-0.5 * label.getHeight() + offset.getY()));

						//label.setTranslateX(shape.getTranslateX() - 0.5 * label.getWidth() + offset.getX());
						//label.setTranslateY(shape.getTranslateY() - 0.5 * label.getHeight() + offset.getY())

						if (alignLabels) {
							if (add > labelGap) {
								labelConnector.setVisible(true);
								var offset1 = GeometryUtilsFX.translateByAngle(0, 0, angle, 0.5 * labelGap);
								var offset2 = GeometryUtilsFX.translateByAngle(0, 0, angle, add + 0.5 * labelGap);
								labelConnector.update(shape.getTranslateX() + offset1.getX(), shape.getTranslateY() + offset1.getY(), shape.getTranslateX() + offset2.getX(), shape.getTranslateY() + offset2.getY());
							} else
								labelConnector.setVisible(false);
						}
						label.setAnchor(shape);
						label.setRotate(angle);
						label.ensureUpright();
					}
				};
				label.widthProperty().addListener(invalidationListener);
				label.heightProperty().addListener(invalidationListener);
				shape.translateXProperty().addListener(invalidationListener);
				shape.translateYProperty().addListener(invalidationListener);

			}
		}
	}
}
