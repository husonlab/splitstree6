/*
 *  Copyright (C) 2018. Daniel H. Huson
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

/*
 *  CreateEdgesCircular.java Copyright (C) 2021 Daniel H. Huson
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

import javafx.geometry.Point2D;
import javafx.scene.paint.Color;
import javafx.scene.shape.*;
import jloda.fx.util.GeometryUtilsFX;
import jloda.graph.Edge;
import jloda.graph.NodeArray;
import jloda.graph.NodeDoubleArray;
import jloda.phylo.LSAUtils;
import jloda.phylo.PhyloTree;

import java.util.ArrayList;
import java.util.Collection;
import java.util.function.BiConsumer;

import static splitstree6.view.trees.layout.CreateEdgesRectangular.addArrowHead;

public class CreateEdgesCircular {

	public static Collection<Shape> apply(TreeDiagramType diagram, PhyloTree tree, NodeArray<Point2D> nodePointMap, NodeDoubleArray nodeAngleMap, Color color, boolean linkNodesEdgesLabels, BiConsumer<Edge, Shape> edgeCallback) {
		var shapes = new ArrayList<Shape>();


		var origin = new Point2D(0, 0);

		LSAUtils.preorderTraversalLSA(tree, tree.getRoot(), v -> {
			for (var e : v.outEdges()) {
				var w = e.getTarget();


				// todo: need to implemented linked

				var vPt = nodePointMap.get(v);
				var wPt = nodePointMap.get(w);

				var line = new Path();
				line.setFill(Color.TRANSPARENT);
				line.setStrokeLineCap(StrokeLineCap.ROUND);
				line.setStrokeWidth(1);

				if (tree.isTreeEdge(e)) {
					line.setStroke(color);

					if (tree.isReticulatedEdge(e))
						line.setStroke(Color.PINK);

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

					line.getElements().add(new LineTo(wPt.getX(), wPt.getY()));
				} else {
					line.setStroke(Color.DARKORANGE);
					var moveTo = new MoveTo(vPt.getX(), vPt.getY());
					var lineTo = new LineTo(wPt.getX(), wPt.getY());
					line.getElements().addAll(moveTo, lineTo);
					if (tree.isTransferEdge(e))
						addArrowHead(line, moveTo, lineTo);
				}
				shapes.add(line);
				edgeCallback.accept(e, line);

				if (tree.isLsaLeaf(w) && diagram == TreeDiagramType.CircularPhylogram) {
					nodeAngleMap.put(w, GeometryUtilsFX.computeAngle(wPt));
				}
			}
		});
		return shapes;
	}

}
