/*
 *  MoveEdges.java Copyright (C) 2024 Daniel H. Huson
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

package splitstree6.xtra.draw;

import javafx.geometry.Point2D;
import javafx.scene.shape.*;
import jloda.graph.Graph;
import jloda.graph.Node;

import java.util.Set;

public class MoveEdges {

	public static void apply(Graph network, Set<Node> selectedNodes, double dx, double dy) {
		for (var e : network.edges()) {
			var path = (Path) e.getData();

			if (selectedNodes.contains(e.getSource()) && selectedNodes.contains(e.getTarget())) {
				for (var element : path.getElements()) {
					shiftEdge(element, dx, dy);
				}
			} else if (selectedNodes.contains(e.getSource()) && !selectedNodes.contains(e.getTarget())) {
				var sourceLocation = new Point2D(((Shape) e.getSource().getData()).getTranslateX() - dx, ((Shape) e.getSource().getData()).getTranslateY() - dy);
				var targetLocation = new Point2D(((Shape) e.getTarget().getData()).getTranslateX(), ((Shape) e.getTarget().getData()).getTranslateY());
				for (var element : path.getElements()) {
					shiftUsingSource(element, sourceLocation, targetLocation, dx, dy);
				}
			} else if (!selectedNodes.contains(e.getSource()) && selectedNodes.contains(e.getTarget())) {
				var sourceLocation = new Point2D(((Shape) e.getSource().getData()).getTranslateX(), ((Shape) e.getSource().getData()).getTranslateY());
				var targetLocation = new Point2D(((Shape) e.getTarget().getData()).getTranslateX() - dx, ((Shape) e.getTarget().getData()).getTranslateY() - dy);
				for (var element : path.getElements()) {
					shiftUsingTarget(element, sourceLocation, targetLocation, dx, dy);
				}
			}
		}
	}

	private static void shiftEdge(PathElement element, double dx, double dy) {
		if (element instanceof MoveTo one) {
			one.setX(one.getX() + dx);
			one.setY(one.getY() + dy);
		} else if (element instanceof LineTo one) {
			one.setX(one.getX() + dx);
			one.setY(one.getY() + dy);
		}
	}

	private static void shiftUsingSource(PathElement element, Point2D sourceLocation, Point2D targetLocation, double dx, double dy) {
		if (element instanceof MoveTo point) {
			var d = targetLocation.distance(sourceLocation);
			if (d > 0.1) {
				var factor = targetLocation.distance(point.getX(), point.getY()) / d;
				point.setX(point.getX() + factor * dx);
				point.setY(point.getY() + factor * dy);
			}
		} else if (element instanceof LineTo point) {
			var d = targetLocation.distance(sourceLocation);
			if (d > 0.1) {
				var factor = targetLocation.distance(point.getX(), point.getY()) / d;
				point.setX(point.getX() + factor * dx);
				point.setY(point.getY() + factor * dy);
			}
		}
	}

	private static void shiftUsingTarget(PathElement element, Point2D sourceLocation, Point2D targetLocation, double dx, double dy) {
		if (element instanceof MoveTo point) {
			var d = sourceLocation.distance(targetLocation);
			if (d > 0.1) {
				var factor = sourceLocation.distance(point.getX(), point.getY()) / d;
				point.setX(point.getX() + factor * dx);
				point.setY(point.getY() + factor * dy);
			}
		} else if (element instanceof LineTo point) {
			var d = sourceLocation.distance(targetLocation);
			if (d > 0.1) {
				var factor = sourceLocation.distance(point.getX(), point.getY()) / d;
				point.setX(point.getX() + factor * dx);
				point.setY(point.getY() + factor * dy);
			}
		}
	}
}
