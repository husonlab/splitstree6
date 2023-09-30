/*
 * CreateEdgesRectangular.java Copyright (C) 2023 Daniel H. Huson
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
import javafx.scene.paint.Color;
import javafx.scene.shape.LineTo;
import javafx.scene.shape.MoveTo;
import javafx.scene.shape.Path;
import javafx.scene.shape.QuadCurveTo;
import jloda.fx.control.RichTextLabel;
import jloda.fx.util.GeometryUtilsFX;
import jloda.graph.Edge;
import jloda.graph.Node;
import jloda.phylo.PhyloTree;

import java.util.Map;

/**
 * draws edges in rectangular layout
 * Daniel Huson, 12.2021
 */
public class CreateEdgesRectangular {

	public static void apply(PhyloTree tree, Map<Node, LabeledNodeShape> nodeShapeMap, Map<Edge, LabeledEdgeShape> edgeShapeMap) {
		for (var e : tree.edges()) {
			var label = (tree.getLabel(e) != null ? new RichTextLabel(tree.getLabel(e)) : null);

			var sourceShape = nodeShapeMap.get(e.getSource());
			var targetShape = nodeShapeMap.get(e.getTarget());
			var line = new Path();
			line.setPickOnBounds(false);

			var moveTo = new MoveTo();
			moveTo.setX(sourceShape.getTranslateX());
			moveTo.setY(sourceShape.getTranslateY());

			line.getElements().add(moveTo);

			if (tree.isTreeEdge(e) || tree.isTransferAcceptorEdge(e)) {
				line.getStyleClass().add("graph-edge");

				var lineTo1 = new LineTo();
				line.getElements().add(lineTo1);

				var dx = targetShape.getTranslateX() - sourceShape.getTranslateX();
				var dy = targetShape.getTranslateY() - sourceShape.getTranslateY();

				if (Math.abs(dx) <= 5 || Math.abs(dy) <= 5) {
					lineTo1.setX(sourceShape.getTranslateX());
					lineTo1.setY(targetShape.getTranslateY());
				} else {
					lineTo1.setX(sourceShape.getTranslateX());
					lineTo1.setY(sourceShape.getTranslateY() + dy + (dy > 0 ? -4 : 4));

					var quadTo = new QuadCurveTo();
					line.getElements().add(quadTo);
					quadTo.setControlX(sourceShape.getTranslateX());
					quadTo.setControlY(targetShape.getTranslateY());
					quadTo.setX(sourceShape.getTranslateX() + (dx > 0 ? +4 : -4));
					quadTo.setY(targetShape.getTranslateY());
				}

				var lineTo2 = new LineTo();
				line.getElements().add(lineTo2);
				lineTo2.setX(targetShape.getTranslateX());
				lineTo2.setY(targetShape.getTranslateY());

				if (label != null) {
					label.setTranslateX(0.5 * (sourceShape.getTranslateX() + targetShape.getTranslateX()));
					label.setTranslateY(targetShape.getTranslateY() - 18);
				}
			} else if (tree.isTransferEdge(e)) {
				var lineTo1 = new LineTo();
				line.getElements().add(lineTo1);
				line.getStyleClass().add("graph-special-edge");

				lineTo1.setX(targetShape.getTranslateX());
				lineTo1.setY(targetShape.getTranslateY());
				addArrowHead(line, moveTo, lineTo1);

				if (label != null) {
					label.setTextFill(Color.DARKORANGE);
					label.setTranslateX(0.5 * (sourceShape.getTranslateX() + targetShape.getTranslateX()));
					label.setTranslateY(0.5 * (sourceShape.getTranslateY() + targetShape.getTranslateY()) - 15);
				}
			} else { // tree.isReticulateEdge(e)
				line.getStyleClass().add("graph-special-edge");
				var quadCurveTo = new QuadCurveTo();
				line.getElements().add(quadCurveTo);
				quadCurveTo.setControlX(sourceShape.getTranslateX());
				quadCurveTo.setControlY(targetShape.getTranslateY());
				quadCurveTo.setX(targetShape.getTranslateX());
				quadCurveTo.setY(targetShape.getTranslateY());
				if (label != null) {
					label.setTextFill(Color.DARKORANGE);
					label.setTranslateX(0.5 * (sourceShape.getTranslateX() + targetShape.getTranslateX()));
					label.setTranslateY(targetShape.getTranslateY() + (sourceShape.getTranslateY() < targetShape.getTranslateY() ? -18 : +18));
				}
			}

			edgeShapeMap.put(e, new LabeledEdgeShape(label, line));
		}
	}

	public static void addArrowHead(Path path, MoveTo moveto, LineTo lineTo) {
		var radian = GeometryUtilsFX.deg2rad(GeometryUtilsFX.computeAngle(lineTo.getX() - moveto.getX(), lineTo.getY() - moveto.getY()));
		var dx = 10 * Math.cos(radian);
		var dy = 10 * Math.sin(radian);

		var head = new Point2D(lineTo.getX(), lineTo.getY());
		var one = head.add(-dx - dy, dx - dy);
		var two = head.add(-dx + dy, -dx - dy);

		path.getElements().add(new LineTo(one.getX(), one.getY()));
		path.getElements().add(new MoveTo(head.getX(), head.getY()));
		path.getElements().add(new LineTo(two.getX(), two.getY()));
	}
}
