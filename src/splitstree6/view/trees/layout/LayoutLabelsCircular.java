/*
 * LayoutLabelsCircular.java Copyright (C) 2022 Daniel H. Huson
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

package splitstree6.view.trees.layout;

import javafx.beans.InvalidationListener;
import javafx.beans.binding.Bindings;
import javafx.geometry.Point2D;
import javafx.scene.Group;
import javafx.scene.shape.Shape;
import jloda.fx.control.RichTextLabel;
import jloda.fx.util.GeometryUtilsFX;
import jloda.graph.NodeArray;
import jloda.graph.NodeDoubleArray;
import jloda.phylo.PhyloTree;

/**
 * setup labels for a circular layout
 * Daniel Huson, 12.2021
 */
public class LayoutLabelsCircular {

	public static void apply(PhyloTree tree, NodeArray<Shape> nodeShapeMap, NodeArray<RichTextLabel> nodeLabelMap, NodeDoubleArray nodeAngleMap, double labelGap,
							 boolean linkNodesEdgesLabels, Group labelConnectors) {

		var alignLabels = (labelConnectors != null);
		final double maxRadius;
		if (alignLabels) {
			maxRadius = tree.nodeStream().map(nodeShapeMap::get).mapToDouble(s -> GeometryUtilsFX.magnitude(s.getTranslateX(), s.getTranslateY())).max().orElse(0);
		} else
			maxRadius = Double.MIN_VALUE;

		for (var v : tree.nodes()) {
			var shape = nodeShapeMap.get(v);
			var label = nodeLabelMap.get(v);
			if (label != null) {
				InvalidationListener changeListener = a -> {
					if (label.getWidth() > 0 && label.getHeight() > 0) {
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
						var add = (maxRadius > Double.MIN_VALUE ? maxRadius - GeometryUtilsFX.magnitude(shape.getTranslateX(), shape.getTranslateY()) : 0);

						var offset = GeometryUtilsFX.translateByAngle(0, 0, angle, add + labelGap + 0.5 * label.getWidth());
						if (linkNodesEdgesLabels) {
							label.translateXProperty().bind(shape.translateXProperty().subtract(0.5 * label.getWidth()).add(offset.getX()));
							label.translateYProperty().bind(shape.translateYProperty().subtract(0.5 * label.getHeight()).add(offset.getY()));


							if (alignLabels && add > 1.1 * labelGap) {
								// todo: this is untested
								var offset1 = GeometryUtilsFX.translateByAngle(0, 0, angle, 0.5 * labelGap);
								var offset2 = GeometryUtilsFX.translateByAngle(0, 0, angle, add + 0.5 * labelGap);
								labelConnectors.getChildren().add(new LabelConnector(
										Bindings.createDoubleBinding(() -> shape.getTranslateX() + offset1.getX(), shape.translateXProperty()),
										Bindings.createDoubleBinding(() -> shape.getTranslateY() + offset1.getY(), shape.translateYProperty()),
										Bindings.createDoubleBinding(() -> shape.getTranslateX() + offset2.getX(), shape.translateXProperty()),
										Bindings.createDoubleBinding(() -> shape.getTranslateY() + offset2.getY(), shape.translateYProperty())));
							}
						} else {
							label.setTranslateX(shape.getTranslateX() - 0.5 * label.getWidth() + offset.getX());
							label.setTranslateY(shape.getTranslateY() - 0.5 * label.getHeight() + offset.getY());

							if (alignLabels && add > 1.1 * labelGap) {
								var offset1 = GeometryUtilsFX.translateByAngle(0, 0, angle, 0.5 * labelGap);
								var offset2 = GeometryUtilsFX.translateByAngle(0, 0, angle, add + 0.5 * labelGap);
								labelConnectors.getChildren().add(new LabelConnector(shape.getTranslateX() + offset1.getX(), shape.getTranslateY() + offset1.getY(), shape.getTranslateX() + offset2.getX(), shape.getTranslateY() + offset2.getY()));
							}
						}
						label.setRotate(angle);
						label.ensureUpright();
					}
				};
				label.widthProperty().addListener(changeListener);
				label.heightProperty().addListener(changeListener);
			}
		}
	}

}
