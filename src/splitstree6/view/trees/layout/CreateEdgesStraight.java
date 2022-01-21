/*
 *  CreateEdgesStraight.java Copyright (C) 2022 Daniel H. Huson
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

import javafx.scene.paint.Color;
import javafx.scene.shape.*;
import jloda.graph.Edge;
import jloda.graph.NodeArray;
import jloda.phylo.PhyloTree;

import java.util.ArrayList;
import java.util.Collection;
import java.util.function.BiConsumer;

import static splitstree6.view.trees.layout.CreateEdgesRectangular.addArrowHead;

/**
 * draws edges using straight lines
 * Daniel Huson, 12.2021
 */
public class CreateEdgesStraight {

	public static Collection<Shape> apply(TreeDiagramType diagram, PhyloTree tree, NodeArray<Shape> nodeShapeMap, Color color, boolean linkNodesEdgesLabels, BiConsumer<Edge, Shape> edgeCallback) {
		var shapes = new ArrayList<Shape>();
		for (var e : tree.edges()) {
			var sourceShape = nodeShapeMap.get(e.getSource());
			var targetShape = nodeShapeMap.get(e.getTarget());
			var moveTo = new MoveTo();
			if (linkNodesEdgesLabels) {
				moveTo.xProperty().bind(sourceShape.translateXProperty());
				moveTo.yProperty().bind(sourceShape.translateYProperty());
			} else {
				moveTo.setX(sourceShape.getTranslateX());
				moveTo.setY(sourceShape.getTranslateY());
			}

			var lineTo = new LineTo();
			if (linkNodesEdgesLabels) {
				lineTo.xProperty().bind(targetShape.translateXProperty());
				lineTo.yProperty().bind(targetShape.translateYProperty());
			} else {
				lineTo.setX(targetShape.getTranslateX());
				lineTo.setY(targetShape.getTranslateY());
			}

			var line = new Path(moveTo, lineTo);
			line.setFill(Color.TRANSPARENT);
			line.setStrokeLineCap(StrokeLineCap.ROUND);
			line.setStrokeWidth(1);

			if (tree.isReticulatedEdge(e))
				line.setStroke(Color.DARKORANGE);
			else
				line.setStroke(color);

			if (tree.isTransferEdge(e))
				addArrowHead(line, moveTo, lineTo);

			shapes.add(line);
			edgeCallback.accept(e, line);
		}
		return shapes;
	}
}
