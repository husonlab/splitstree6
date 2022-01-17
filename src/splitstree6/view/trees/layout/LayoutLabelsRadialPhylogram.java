/*
 *  LayoutLabelsRadialPhylogram.java Copyright (C) 2022 Daniel H. Huson
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

import javafx.application.Platform;
import javafx.geometry.Point2D;
import javafx.scene.shape.Shape;
import jloda.fx.control.RichTextLabel;
import jloda.fx.util.GeometryUtilsFX;
import jloda.graph.NodeArray;
import jloda.graph.NodeDoubleArray;
import jloda.phylo.PhyloTree;
import splitstree6.view.splits.layout.RadialLabelLayout;
import splitstree6.view.trees.treepages.LayoutOrientation;

import java.util.function.Consumer;

/**
 * setup labels for a radial layout
 * Daniel Huson, 12.2021
 */
public class LayoutLabelsRadialPhylogram implements Consumer<LayoutOrientation> {
	private final RadialLabelLayout labelLayout;

	/**
	 * setup labels for tree
	 *
	 * @param tree
	 * @param nodeShapeMap
	 * @param nodeLabelMap
	 * @param nodeAngleMap
	 * @param labelGap
	 */
	public LayoutLabelsRadialPhylogram(PhyloTree tree, NodeArray<Shape> nodeShapeMap, NodeArray<RichTextLabel> nodeLabelMap, NodeDoubleArray nodeAngleMap, double labelGap) {

		labelLayout = new RadialLabelLayout();
		labelLayout.setGap(labelGap);

		for (var v : tree.nodes()) {
			var label = nodeLabelMap.get(v);
			if (label != null) {
				var shape = nodeShapeMap.get(v);
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

				var translateXProperty = shape.translateXProperty();
				var translateYProperty = shape.translateYProperty();
				labelLayout.addItem(translateXProperty, translateYProperty, angle, label.widthProperty(), label.heightProperty(),
						xOffset -> label.translateXProperty().bind(translateXProperty.add(xOffset)), yOffset -> label.translateYProperty().bind(translateYProperty.add(yOffset)));
				labelLayout.addAvoidable(translateXProperty, translateYProperty, shape.getLayoutBounds().getWidth(), shape.getLayoutBounds().getHeight());
			}
		}
	}

	public void accept(LayoutOrientation layoutOrientation) {
		Platform.runLater(() -> labelLayout.layoutLabels(layoutOrientation));
	}
}
