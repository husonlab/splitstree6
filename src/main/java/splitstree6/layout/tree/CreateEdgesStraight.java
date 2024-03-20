/*
 *  CreateEdgesStraight.java Copyright (C) 2024 Daniel H. Huson
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

import javafx.scene.paint.Color;
import javafx.scene.shape.LineTo;
import javafx.scene.shape.MoveTo;
import javafx.scene.shape.Path;
import javafx.scene.shape.StrokeLineCap;
import jloda.fx.control.RichTextLabel;
import jloda.graph.Edge;
import jloda.graph.Node;
import jloda.phylo.PhyloTree;

import java.util.Map;

import static splitstree6.layout.tree.CreateEdgesRectangular.addArrowHead;

/**
 * draws edges using straight lines
 * Daniel Huson, 12.2021
 */
public class CreateEdgesStraight {

	public static void apply(PhyloTree tree, Map<Node, LabeledNodeShape> nodeShapeMap, boolean linkNodesEdgesLabels, Map<Edge, LabeledEdgeShape> edgeShapeMap) {
		for (var e : tree.edges()) {
			var label = (tree.getLabel(e) != null ? new RichTextLabel(tree.getLabel(e)) : null);

			var sourceShape = nodeShapeMap.get(e.getSource());
			var targetShape = nodeShapeMap.get(e.getTarget());
			var moveTo = new MoveTo();
			var lineTo = new LineTo();

			if (linkNodesEdgesLabels) {
				moveTo.xProperty().bind(sourceShape.translateXProperty());
				moveTo.yProperty().bind(sourceShape.translateYProperty());
				lineTo.xProperty().bind(targetShape.translateXProperty());
				lineTo.yProperty().bind(targetShape.translateYProperty());
			} else {
				moveTo.setX(sourceShape.getTranslateX());
				moveTo.setY(sourceShape.getTranslateY());
				lineTo.setX(targetShape.getTranslateX());
				lineTo.setY(targetShape.getTranslateY());
			}

			var line = new Path(moveTo, lineTo);
			line.setFill(Color.TRANSPARENT);
			line.setStrokeLineCap(StrokeLineCap.ROUND);
			line.setStrokeWidth(1);

			if (tree.isTreeEdge(e))
				line.getStyleClass().add("graph-edge");
			else
				line.getStyleClass().add("graph-special-edge");

			if (tree.isTransferEdge(e))
				addArrowHead(line, moveTo, lineTo);

			if (label != null) {
				if (!tree.isTreeEdge(e))
					label.setTextFill(Color.DARKORANGE);
				label.setTranslateX(0.5 * (sourceShape.getTranslateX() + targetShape.getTranslateX()));
				label.setTranslateY(0.5 * (sourceShape.getTranslateY() + targetShape.getTranslateY()) - 15);
			}

			edgeShapeMap.put(e, new LabeledEdgeShape(label, line));
		}
	}
}
