/*
 *  DrawUtils.java Copyright (C) 2024 Daniel H. Huson
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
import javafx.scene.Group;
import javafx.scene.shape.*;
import javafx.util.Pair;
import jloda.graph.Edge;
import jloda.graph.Graph;
import jloda.graph.Node;
import jloda.util.IteratorUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Set;

public class DrawUtils {

	public static LineTo asRectangular(Path path) {
		var first = (MoveTo) path.getElements().get(0);
		var last = (LineTo) path.getElements().get(path.getElements().size() - 1);

		var points = new ArrayList<Point2D>();
		for (var element : path.getElements()) {
			if ((element instanceof LineTo lineTo) && lineTo != last) {
				points.add(new Point2D(lineTo.getX(), lineTo.getY()));
			}
		}

		var x0 = first.getX();
		var x1 = last.getX();
		var y0 = first.getY();
		var y1 = last.getY();

		var p = new Point2D(x0 <= x1 ? x0 : x1, x0 <= x1 ? y0 : y1);
		var q = new Point2D(x0 <= x1 ? x1 : x0, x0 <= x1 ? y1 : y0);


		var dx = Math.abs(x0 - x1);
		var dy = Math.abs(y0 - y1);

		var factor = 0.3;

		if (p.getY() + 2 * factor * dy < q.getY()) {
			for (var a : points) {
				if (a.getX() >= q.getX() - factor * dx && a.getY() <= p.getY() + factor * dy) {
					return new LineTo(q.getX(), p.getY());
				} else if (a.getX() <= p.getX() + factor * dx && a.getY() >= p.getY() + (1 - factor) * dy) {
					return new LineTo(p.getX(), q.getY());
				}
			}
		} else if (p.getY() - 2 * factor * dy > q.getY()) {
			for (var a : points) {
				if (a.getX() <= p.getX() + factor * dx && a.getY() <= q.getY() + factor * dy) {
					return new LineTo(p.getX(), q.getY());
				} else if (a.getX() >= p.getX() + (1 - factor) * dx && a.getY() >= q.getY() + (1 - factor) * dy) {
					return new LineTo(q.getX(), p.getY());
				}
			}
		}
		return null;
	}

	public static Point2D getCoordinates(PathElement pathElement) {
		if (pathElement instanceof MoveTo moveTo) {
			return new Point2D(moveTo.getX(), moveTo.getY());
		} else if (pathElement instanceof LineTo lineTo) {
			return new Point2D(lineTo.getX(), lineTo.getY());
		} else {
			return new Point2D(0, 0);
		}
	}

	public static Point2D snapToShape(Point2D point, Shape shape, double tolerance) {
		if (point.distance(shape.getTranslateX(), shape.getTranslateY()) <= tolerance) {
			return new Point2D(shape.getTranslateX(), shape.getTranslateY());
		}
		return null;
	}

	public static Point2D snapToPath(Point2D point, Path path, double tolerance) {
		var i = hitPathElement(point, path, tolerance);
		if (i >= 0) {
			return getLocation(path, i);
		} else return null;
	}

	public static int hitPathElement(Point2D point, Path path, double tolerance) {
		for (var i = 0; i < path.getElements().size(); i++) {
			var other = getLocation(path, i);
			if (other != null && point.distance(other) <= tolerance)
				return i;
		}
		return -1;
	}

	public static Point2D getLocation(Path path, int index) {
		if (index < 0 || index >= path.getElements().size())
			return null;
		var element = path.getElements().get(index);
		if (element instanceof MoveTo which)
			return new Point2D(which.getX(), which.getY());
		else if (element instanceof LineTo which)
			return new Point2D(which.getX(), which.getY());
		else if (element instanceof ArcTo which)
			return new Point2D(which.getX(), which.getY());
		else if (element instanceof CubicCurveTo which)
			return new Point2D(which.getX(), which.getY());
		else if (element instanceof QuadCurveTo which)
			return new Point2D(which.getX(), which.getY());
		else return null;
	}

	public static Pair<Node, Point2D> snapToExistingNode(Point2D point, Group nodesGroup, double tolerance) {
		for (var a : nodesGroup.getChildren()) {
			if (a instanceof Shape shape) {
				var other = snapToShape(point, shape, tolerance);
				if (other != null) {
					return new Pair<>((jloda.graph.Node) shape.getUserData(), other);
				}
			}
		}
		return new Pair<>(null, point);
	}

	public static Pair<Edge, Point2D> snapToExistingEdge(Point2D point, Group edgesGroup, double tolerance) {
		for (var a : edgesGroup.getChildren()) {
			if (a instanceof Path path) {
				var other = snapToPath(point, path, tolerance);
				if (other != null)
					return new Pair<>((Edge) path.getUserData(), other);
			}
		}
		return new Pair<>(null, point);
	}

	public static boolean hasCollisions(Graph graph, Set<Node> selected, double dx, double dy) {
		var notSelected = IteratorUtils.asSet(graph.nodes());
		notSelected.removeAll(selected);
		for (var v : selected) {
			var vShape = (Shape) v.getData();
			var vPoint = new Point2D(vShape.getTranslateX() + dx, vShape.getTranslateY() + dy);
			for (var w : notSelected) {
				var wShape = (Shape) w.getData();
				if (vPoint.distance(wShape.getTranslateX(), wShape.getTranslateY()) < 10)
					return true;
			}
		}
		return false;
	}

	public static Path createPath(Point2D a, Point2D b, int step) {
		var path = new Path();
		var start = new MoveTo(a.getX(), a.getY());
		var end = new LineTo(b.getX(), b.getY());
		path.getElements().add(start);
		interpolate(start, end, step);
		path.getElements().add(end);
		return path;
	}

	public static Collection<? extends PathElement> interpolate(PathElement first, PathElement last, double tolerance) {
		var start = getCoordinates(first);
		var end = getCoordinates(last);
		var distance = start.distance(end);
		var n = Math.floor(distance / tolerance) - 1.0;
		var result = new ArrayList<PathElement>();
		for (var i = 0.0; i < n; i++) {
			result.add(new LineTo((i * start.getX() + (n - i) * end.getX()) / n, (i * start.getY() + (n - i) * end.getY()) / n));
		}
		//System.err.println("Added: " + result.size());
		return result;
	}
}
