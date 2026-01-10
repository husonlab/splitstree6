/*
 *  LayoutLabelsRectangular.java Copyright (C) 2024 Daniel H. Huson
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
import javafx.scene.Group;
import jloda.graph.Node;
import jloda.phylo.PhyloTree;

import java.util.Map;

/**
 * create node labels for a rectangular layout
 * Daniel Huson, 12.2021
 */
public class LayoutLabelsRectangular {

	public static void apply(PhyloTree tree, Map<Node, LabeledNodeShape> nodeShapeMap, double labelGap, Group labelConnectors) {
		var alignLabels = (labelConnectors != null);

		for (var v : tree.nodes()) {
			var shape = nodeShapeMap.get(v);
			var label = shape.getLabel();
			if (label != null) {
				label.setAnchor(shape);
				LabelConnector labelConnector;
				if (alignLabels) {
					labelConnector = new LabelConnector();
					labelConnectors.getChildren().add(labelConnector);
				} else labelConnector = null;

				InvalidationListener invalidationListener = a -> {
					if (label.getWidth() > 0 && label.getHeight() > 0) {
						if (tree.isLsaLeaf(v)) {
							double max0;
							if (alignLabels) {
								max0 = tree.nodeStream().mapToDouble(u -> nodeShapeMap.get(u).getTranslateX()).max().orElse(0);
							} else {
								max0 = Double.MIN_VALUE;
							}

							var add = (max0 > Double.MIN_VALUE ? max0 - shape.getTranslateX() : 0) + labelGap;
							label.translateXProperty().bind(shape.translateXProperty().add(add));
							if (alignLabels) {
								if (add > labelGap) {
									labelConnector.setVisible(true);
									labelConnector.update(shape.getTranslateX() + 0.5 * labelGap, shape.getTranslateY(), shape.getTranslateX() + add - 0.5 * labelGap, shape.getTranslateY());
								} else
									labelConnector.setVisible(false);
							}
						} else {
							label.translateXProperty().bind(shape.translateXProperty().add(labelGap));
						}
						label.translateYProperty().bind(shape.translateYProperty().subtract(0.5 * label.getHeight()));
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
