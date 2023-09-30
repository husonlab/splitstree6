/*
 * CreateEdgesCircular.java Copyright (C) 2023 Daniel H. Huson
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
import javafx.scene.shape.ArcTo;
import javafx.scene.shape.LineTo;
import javafx.scene.shape.MoveTo;
import javafx.scene.shape.Path;
import jloda.fx.control.RichTextLabel;
import jloda.fx.util.GeometryUtilsFX;
import jloda.graph.Edge;
import jloda.graph.Node;
import jloda.phylo.LSAUtils;
import jloda.phylo.PhyloTree;

import java.util.Map;

import static splitstree6.layout.tree.CreateEdgesRectangular.addArrowHead;

/**
 * create edges for a circular layout
 * Daniel Huson, 1.2022
 */
public class CreateEdgesCircular {

	public static void apply(TreeDiagramType diagram, PhyloTree tree, Map<Node, Point2D> nodePointMap, Map<Node, Double> nodeAngleMap,
							 Map<Edge, LabeledEdgeShape> edgeShapeMap) {

		var origin = new Point2D(0, 0);

		LSAUtils.preorderTraversalLSA(tree, tree.getRoot(), v -> {
			for (var e : v.outEdges()) {
				var label = (tree.getLabel(e) != null ? new RichTextLabel(tree.getLabel(e)) : null);

				var w = e.getTarget();
				var vPt = nodePointMap.get(v);
				var wPt = nodePointMap.get(w);

				var line = new Path();

				if (tree.isTreeEdge(e) || tree.isTransferAcceptorEdge(e)) {
					line.getStyleClass().add("graph-edge");

					line.getElements().add(new MoveTo(vPt.getX(), vPt.getY()));

					if (vPt.magnitude() > 0 && wPt.magnitude() > 0) {
						var corner = wPt.multiply(vPt.magnitude() / wPt.magnitude());

						var arcTo = new ArcTo();
						arcTo.setX(corner.getX());
						arcTo.setY(corner.getY());
						arcTo.setRadiusX(vPt.magnitude());
						arcTo.setRadiusY(vPt.magnitude());
						arcTo.setLargeArcFlag(GeometryUtilsFX.computeObservedAngle(origin, vPt, wPt) > 180);
						arcTo.setSweepFlag(GeometryUtilsFX.computeObservedAngle(origin, vPt, wPt) > 0);

						line.getElements().add(arcTo);
					}

					if (label != null) {
						var corner = wPt.multiply(vPt.magnitude() / wPt.magnitude());
						label.setTranslateX(0.5 * (corner.getX() + wPt.getX()));
						label.setTranslateY(0.5 * (corner.getY() + wPt.getY()));
					}
					line.getElements().add(new LineTo(wPt.getX(), wPt.getY()));
				} else {
					line.getStyleClass().add("graph-special-edge");

					var moveTo = new MoveTo(vPt.getX(), vPt.getY());
					var lineTo = new LineTo(wPt.getX(), wPt.getY());
					line.getElements().addAll(moveTo, lineTo);
					if (tree.isTransferEdge(e))
						addArrowHead(line, moveTo, lineTo);

					if (label != null) {
						label.setTextFill(Color.DARKORANGE);
						label.setTranslateX(0.5 * (moveTo.getX() + lineTo.getX()));
						label.setTranslateY(0.5 * (moveTo.getY() + lineTo.getY()));
					}
				}

				edgeShapeMap.put(e, new LabeledEdgeShape(label, line));

				if (tree.isLsaLeaf(w) && diagram == TreeDiagramType.CircularPhylogram) {
					nodeAngleMap.put(w, GeometryUtilsFX.computeAngle(wPt));
				}
			}
		});
	}
}
