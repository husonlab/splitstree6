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
			var labeledNodeShape = nodeShapeMap.get(v);
			var label = labeledNodeShape.getLabel();
			if (label != null) {
				var angle = nodeAngleMap.get(v);
				if (angle == null) {
					if (v.getParent() != null) {
						var vPoint = new Point2D(labeledNodeShape.getTranslateX(), labeledNodeShape.getTranslateY());
						var w = v.getParent();
						var wPoint = new Point2D(nodeShapeMap.get(w).getTranslateX(), nodeShapeMap.get(w).getTranslateY());
						while (vPoint.distance(wPoint) == 0 && w.getParent() != null) {
							w = w.getParent();
							wPoint = new Point2D(nodeShapeMap.get(w).getTranslateX(), nodeShapeMap.get(w).getTranslateY());
						}
						angle = GeometryUtilsFX.computeAngle(labeledNodeShape.getTranslateX() - wPoint.getX(), labeledNodeShape.getTranslateY() - wPoint.getY());
					} else
						angle = 0.0;
				}
				label.setVisible(false);
				label.setLayoutX(0);
				label.setLayoutY(0);
				label.translateXProperty().bind(labeledNodeShape.translateXProperty().subtract(label.widthProperty().multiply(0.5)));
				label.translateYProperty().bind(labeledNodeShape.translateYProperty().subtract(label.heightProperty().multiply(0.5)));

				labelLayout.addItem(labeledNodeShape.translateXProperty(), labeledNodeShape.translateYProperty(), angle, label.widthProperty(), label.heightProperty(),
						xOffset -> {
							label.setLayoutX(0);
							label.translateXProperty().bind(labeledNodeShape.translateXProperty().add(xOffset));
						},
						yOffset -> {
							label.setLayoutY(0);
							label.translateYProperty().bind(labeledNodeShape.translateYProperty().add(yOffset));
							label.setVisible(true);
						});


				labelLayout.addAvoidable(() -> labeledNodeShape.getTranslateX() - 0.5 * labeledNodeShape.prefWidth(0), () -> labeledNodeShape.getTranslateY() - 0.5 * labeledNodeShape.prefHeight(0), () -> labeledNodeShape.prefWidth(0), () -> labeledNodeShape.prefHeight(0));
			}
		}
	}

	public void accept(LayoutOrientation layoutOrientation) {
		Platform.runLater(() -> labelLayout.layoutLabels(layoutOrientation.toString()));
	}
}
