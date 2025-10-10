/*
 *  SetupRadialLabelLayout.java Copyright (C) 2024 Daniel H. Huson
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

import javafx.application.Platform;
import javafx.geometry.Point2D;
import jloda.fx.util.GeometryUtilsFX;
import jloda.graph.Node;
import jloda.phylo.PhyloTree;

import java.util.Map;
import java.util.function.Consumer;

/**
 * setup labels and layout for a radial layout
 * Daniel Huson, 12.2021
 */
public class SetupRadialLabelLayout implements Consumer<LayoutOrientation> {
	private final RadialLabelLayout labelLayout;

	/**
	 * create labels for tree
	 */
	public SetupRadialLabelLayout(PhyloTree tree, Map<Node, LabeledNodeShape> nodeShapeMap, Map<Node, Double> nodeAngleMap, double labelGap) {

		labelLayout = new RadialLabelLayout();
		labelLayout.setGap(labelGap);

		for (var v : tree.nodes()) {
			var shape = nodeShapeMap.get(v);
			var label = shape.getLabel();
			if (label != null) {
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
				if (false)
					System.err.println(angle);
				label.setVisible(false);
				label.setLayoutX(0);
				label.setLayoutY(0);
				label.translateXProperty().bind(shape.translateXProperty().subtract(label.widthProperty().multiply(0.5)));
				label.translateYProperty().bind(shape.translateYProperty().subtract(label.heightProperty().multiply(0.5)));

				labelLayout.addItem(shape.translateXProperty(), shape.translateYProperty(), angle, label.widthProperty(), label.heightProperty(),
						xOffset -> {
							label.setLayoutX(0);
							label.translateXProperty().bind(shape.translateXProperty().add(xOffset));
						},
						yOffset -> {
							label.setLayoutY(0);
							label.translateYProperty().bind(shape.translateYProperty().add(yOffset));
							label.setVisible(true);
						});
				labelLayout.addAvoidable(() -> shape.getTranslateX() - 0.5 * shape.prefWidth(0), () -> shape.getTranslateY() - 0.5 * shape.prefHeight(0), () -> shape.prefWidth(0), () -> shape.prefHeight(0));
			}
		}
	}

	public void accept(LayoutOrientation layoutOrientation) {
		Platform.runLater(() -> labelLayout.layoutLabels(layoutOrientation.toString()));
	}
}
