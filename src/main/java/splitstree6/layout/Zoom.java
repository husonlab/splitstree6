/*
 * Zoom.java Copyright (C) 2026 Daniel H. Huson
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
 *
 */

package splitstree6.layout;

import javafx.scene.shape.*;
import jloda.graph.Edge;
import jloda.graph.Node;
import splitstree6.layout.tree.LabeledEdgeShape;
import splitstree6.layout.tree.LabeledNodeShape;

import java.util.ArrayList;
import java.util.Map;

/**
 * zoom all nodes and edges
 * Daniel Huson, 1.2026
 */
public class Zoom {
	public static void apply(double zoomFactorX, double zoomFactorY, Map<Node, LabeledNodeShape> nodeShapeMap, Map<Edge, LabeledEdgeShape> edgeShapeMap) {

		for (var shape : nodeShapeMap.values()) {
			shape.setTranslateX(shape.getTranslateX() * zoomFactorX);
			shape.setTranslateY(shape.getTranslateY() * zoomFactorY);
		}
		for (var shape : edgeShapeMap.values()) {
			if (shape.getShape() instanceof Path path) {
				var elements = new ArrayList<PathElement>();

				for (var element : path.getElements()) {
					if (element instanceof MoveTo moveTo) {
						elements.add(new MoveTo(moveTo.getX() * zoomFactorX, moveTo.getY() * zoomFactorY));

					} else if (element instanceof LineTo lineTo) {
						elements.add(new LineTo(lineTo.getX() * zoomFactorX, lineTo.getY() * zoomFactorY));

					} else if (element instanceof QuadCurveTo quadCurveTo) {
						elements.add(new QuadCurveTo(
								quadCurveTo.getControlX() * zoomFactorX,
								quadCurveTo.getControlY() * zoomFactorY,
								quadCurveTo.getX() * zoomFactorX,
								quadCurveTo.getY() * zoomFactorY));

					} else if (element instanceof ArcTo arcTo) {
						var scaled = new ArcTo();
						scaled.setX(arcTo.getX() * zoomFactorX);
						scaled.setY(arcTo.getY() * zoomFactorY);
						scaled.setRadiusX(arcTo.getRadiusX() * zoomFactorX);
						scaled.setRadiusY(arcTo.getRadiusY() * zoomFactorY);
						scaled.setXAxisRotation(arcTo.getXAxisRotation());
						scaled.setLargeArcFlag(arcTo.isLargeArcFlag());
						scaled.setSweepFlag(arcTo.isSweepFlag());
						elements.add(scaled);
					}
				}
				path.getElements().setAll(elements);
			}
		}
	}
}
