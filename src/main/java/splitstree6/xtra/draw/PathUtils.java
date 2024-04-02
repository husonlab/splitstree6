/*
 *  PathUtils.java Copyright (C) 2024 Daniel H. Huson
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
import javafx.scene.shape.LineTo;
import javafx.scene.shape.MoveTo;
import javafx.scene.shape.Path;
import javafx.scene.shape.PathElement;
import jloda.util.Pair;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * utils for paths
 * Daniel Huson, 3.3024
 */
public class PathUtils {

	/**
	 * computes all intersections between a path and a collection of paths
	 *
	 * @param p     the path
	 * @param paths the collection of paths to intersect with
	 * @return list of paths that and intersection points
	 * @parm ignoreSelfIntersections if p is contained in collection of paths, ignore it?
	 */
	public static List<Pair<Path, Point2D>> allIntersections(Path p, Collection<Path> paths, boolean ignoreSelfIntersections) {
		var list = new ArrayList<Pair<Path, Point2D>>();
		for (var q : paths) {
			if (!ignoreSelfIntersections || p != q) {
				for (var point : getIntersectionPoints(p, q)) {
					list.add(new Pair<>(q, new Point2D(point.getX(), point.getY())));
				}
			}
		}
		return list;
	}

	/**
	 * determines whether the point lines on a path and if so returns the path and the approximate location
	 *
	 * @param apt   the point
	 * @param paths the collection of paths to intersect with
	 * @param delta tolerance around the point
	 * @return the path and point or null
	 */
	public static Pair<Path, Point2D> pointOnPath(Point2D apt, Collection<Path> paths, double delta) {
		var p = new Path();
		p.getElements().add(new MoveTo(apt.getX() - delta, apt.getY() - delta));
		p.getElements().add(new LineTo(apt.getX() - delta, apt.getY() + delta));
		p.getElements().add(new LineTo(apt.getX() + delta, apt.getY() + delta));
		p.getElements().add(new LineTo(apt.getX() + delta, apt.getY() - delta));
		p.getElements().add(new LineTo(apt.getX() - delta, apt.getY() - delta));

		for (var q : paths) {
			var list = new ArrayList<Point2D>();
			for (var point : getIntersectionPoints(p, q)) {
				list.add(new Point2D(point.getX(), point.getY()));
			}
			if (!list.isEmpty()) {
				var x = list.stream().mapToDouble(Point2D::getX).average();
				var y = list.stream().mapToDouble(Point2D::getY).average();
				if (x.isPresent() && y.isPresent())
					return new Pair<>(q, new Point2D(x.getAsDouble(), y.getAsDouble()));
			}
		}
		return null;
	}

	/**
	 * gets all intersection points of two paths, only implemented for line-to parts
	 *
	 * @param a first path
	 * @param b second path
	 * @return all points in which a and b intersect
	 */
	public static List<Point2D> getIntersectionPoints(Path a, Path b) {
		var intersections = new ArrayList<Point2D>();

		if (a != null && b != null) {
			Point2D startA = null;
			for (PathElement elementA : a.getElements()) {
				if (elementA instanceof MoveTo moveToA) {
					startA = new Point2D(moveToA.getX(), moveToA.getY());
				} else if (elementA instanceof LineTo lineToA) {
					var endA = new Point2D(lineToA.getX(), lineToA.getY());

					Point2D startB = null;
					for (var elementB : b.getElements()) {
						if (elementB instanceof MoveTo moveToB) {
							startB = new Point2D(moveToB.getX(), moveToB.getY());
						} else if (elementB instanceof LineTo lineToB) {
							var endB = new Point2D(lineToB.getX(), lineToB.getY());

							if (startA != null && startB != null) {
								if (a != b || getCommonEndpoints(startA, endA, startB, endB).isEmpty()) {
									intersections.addAll(lineSegmentLineSegmentIntersection(startA, endA, startB, endB));
								}
							}
							startB = endB;
						}
					}
					startA = endA;
				} else {
					System.err.println("Skipping element of type: " + elementA.getClass().getSimpleName());
				}
			}
		}
		return intersections;
	}

	/**
	 * determines whether point approximately lies on segment
	 *
	 * @param segA  segment start
	 * @param segB  segment end
	 * @param p     point
	 * @param delta tolerance (+-delta)
	 * @return point or null
	 */
	public static Point2D pointOnLine(Point2D segA, Point2D segB, Point2D p, double delta) {
		var list = new ArrayList<Point2D>();
		var a = new Point2D(p.getX() - delta, p.getY() - delta);
		var b = new Point2D(p.getX() - delta, p.getY() + delta);
		var c = new Point2D(p.getX() + delta, p.getY() + delta);
		var d = new Point2D(p.getX() + delta, p.getY() - delta);

		list.addAll(lineSegmentLineSegmentIntersection(a, b, segA, segB));
		list.addAll(lineSegmentLineSegmentIntersection(b, c, segA, segB));
		list.addAll(lineSegmentLineSegmentIntersection(c, d, segA, segB));
		list.addAll(lineSegmentLineSegmentIntersection(d, a, segA, segB));

		if (list.isEmpty())
			return null;
		else {
			var x = list.stream().mapToDouble(Point2D::getX).average();
			var y = list.stream().mapToDouble(Point2D::getY).average();
			return x.isPresent() && y.isPresent() ? new Point2D(x.getAsDouble(), y.getAsDouble()) : null;
		}
	}

	private static final double EPS = 1e-5;

	/**
	 * determines the orientation of point p relative to a line segment
	 * source: https://github.com/williamfiset/Algorithms
	 *
	 * @param segA start of segment
	 * @param segB end of segment
	 * @param p    point
	 * @return 1 if positive, -1, if negative
	 */
	private static int orientation(Point2D segA, Point2D segB, Point2D p) {
		var det = (segB.getY() - segA.getY()) * (p.getX() - segB.getX()) -
				  (segB.getX() - segA.getX()) * (p.getY() - segB.getY());
		if (Math.abs(det) < EPS) return 0;
		return (det > 0) ? -1 : +1;
	}

	/**
	 * does point p lie on line segment (segA,segB)?
	 * Author: William Fiset, source: https://github.com/williamfiset/Algorithms, license: MIT
	 *
	 * @param segA start of segment
	 * @param segB end of segment
	 * @param p    point
	 * @return true, if p lies on line segment
	 */
	public static boolean pointOnLine(Point2D segA, Point2D segB, Point2D p) {
		return orientation(segA, segB, p) == 0 &&
			   Math.min(segA.getX(), segB.getX()) <= p.getX() && p.getX() <= Math.max(segA.getX(), segB.getX()) &&
			   Math.min(segA.getY(), segB.getY()) <= p.getY() && p.getY() <= Math.max(segA.getY(), segB.getY());
	}

	/**
	 * determines whether two line segments intersect
	 * Author: William Fiset, source: https://github.com/williamfiset/Algorithms, license: MIT
	 *
	 * @param p1 start of first line segment
	 * @param p2 end of first line segment
	 * @param q1 start of second line segment
	 * @param q2 end of second line segment
	 * @return true, if line segments intersect
	 */
	public static boolean segmentsIntersect(Point2D p1, Point2D p2, Point2D q1, Point2D q2) {
		var o1 = orientation(p1, p2, q1);
		var o2 = orientation(p1, p2, q2);
		var o3 = orientation(q1, q2, p1);
		var o4 = orientation(q1, q2, p2);

		if (o1 != o2 && o3 != o4) return true;

		if (o1 == 0 && pointOnLine(p1, p2, q1)) return true;
		if (o2 == 0 && pointOnLine(p1, p2, q2)) return true;
		if (o3 == 0 && pointOnLine(q1, q2, p1)) return true;
		if (o4 == 0 && pointOnLine(q1, q2, p2)) return true;

		return false;
	}

	/**
	 * determines all common endpoints of two line segments
	 * Author: William Fiset, source: https://github.com/williamfiset/Algorithms, license: MIT
	 *
	 * @param p1 start of first line segment
	 * @param p2 end of first line segment
	 * @param q1 start of second line segment
	 * @param q2 end of second line segment
	 * @return list of common endpoints
	 */
	public static List<Point2D> getCommonEndpoints(Point2D p1, Point2D p2, Point2D q1, Point2D q2) {
		var points = new ArrayList<Point2D>();

		if (p1.equals(q1)) {
			points.add(p1);
			if (p2.equals(q2)) points.add(p2);

		} else if (p1.equals(q2)) {
			points.add(p1);
			if (p2.equals(q1)) points.add(p2);

		} else if (p2.equals(q1)) {
			points.add(p2);
			if (p1.equals(q2)) points.add(p1);

		} else if (p2.equals(q2)) {
			points.add(p2);
			if (p1.equals(q1)) points.add(p1);
		}

		return points;
	}

	/**
	 * finds all intersection points between two lines. Result will be empty, if segments do not intersect,
	 * or contain one point, if they cross, or two points, if one lines on top of the other
	 * Author: William Fiset, source: https://github.com/williamfiset/Algorithms, license: MIT
	 *
	 * @param p1 start of first line segment
	 * @param p2 end of first line segment
	 * @param q1 start of second line segment
	 * @param q2 end of second line segment
	 * @return list of intersection points
	 */
	// Finds the intersection point(s) of two line segments. Unlike regular line 
	// segments, segments which are points (x1 = x2 and y1 = y2) are allowed.
	public static List<Point2D> lineSegmentLineSegmentIntersection(Point2D p1, Point2D p2, Point2D q1, Point2D q2) {
		if (!segmentsIntersect(p1, p2, q1, q2)) return Collections.emptyList();

		if (p1.equals(p2) && p2.equals(q1) && q1.equals(q2))
			return List.of(p1);

		var endpoints = getCommonEndpoints(p1, p2, q1, q2);
		var n = endpoints.size();

		var singleton = p1.equals(p2) || q1.equals(q2);
		if (n == 1 && singleton) return List.of(endpoints.get(0));

		// Segments are equal.
		if (n == 2) return List.of(endpoints.get(0), endpoints.get(1));

		var collinearSegments = (orientation(p1, p2, q1) == 0) && (orientation(p1, p2, q2) == 0);

		if (collinearSegments) {
			if (pointOnLine(p1, p2, q1) && pointOnLine(p1, p2, q2))
				return List.of(q1, q2);

			if (pointOnLine(q1, q2, p1) && pointOnLine(q1, q2, p2))
				return List.of(p1, p2);

			var midPoint1 = pointOnLine(p1, p2, q1) ? q1 : q2;
			var midPoint2 = pointOnLine(q1, q2, p1) ? p1 : p2;

			if (midPoint1.equals(midPoint2))
				return List.of(midPoint1);
			else
				return List.of(midPoint1, midPoint2);
		}

		/* Beyond this point there is a unique intersection point. */

		if (Math.abs(p1.getX() - p2.getX()) < EPS) {
			var m = (q2.getY() - q1.getY()) / (q2.getX() - q1.getX());
			var b = q1.getY() - m * q1.getX();
			return List.of(new Point2D(p1.getX(), m * p1.getX() + b));
		}

		if (Math.abs(q1.getX() - q2.getX()) < EPS) {
			var m = (p2.getY() - p1.getY()) / (p2.getX() - p1.getX());
			var b = p1.getY() - m * p1.getX();
			return List.of(new Point2D(q1.getX(), m * q1.getX() + b));
		}

		var m1 = (p2.getY() - p1.getY()) / (p2.getX() - p1.getX());
		var m2 = (q2.getY() - q1.getY()) / (q2.getX() - q1.getX());
		var b1 = p1.getY() - m1 * p1.getX();
		var b2 = q1.getY() - m2 * q1.getX();
		var x = (b2 - b1) / (m1 - m2);
		var y = (m1 * b2 - m2 * b1) / (m1 - m2);

		return List.of(new Point2D(x, y));
	}
}
