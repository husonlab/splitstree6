/*
 * LayoutScore.java Copyright (C) 2026 Daniel H. Huson
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

package splitstree6.layout.network;

import javafx.geometry.Point2D;
import jloda.graph.Edge;
import jloda.graph.Graph;
import jloda.graph.Node;

import java.util.Map;

/**
 * readability score for a straight-line drawing of a network, used to pick the best of several layout attempts
 * <p>
 * Neither of the layouts we use for networks has any notion of an edge crossing: the force-directed layout
 * minimizes Fruchterman-Reingold energy and the MDS-based one minimizes weighted stress, both of which are
 * purely metric objectives. Nothing in either of them stops a pendant edge from sweeping right across the
 * middle of the drawing, and once a force layout has settled there is no move that would un-cross it. The
 * networks we draw are small, though, so we can afford to simply lay one out several times and keep the
 * result that reads best; this class supplies the "reads best" part.
 * <p>
 * Crossings dominate the comparison, because one crossing costs the reader more than any amount of
 * crowding. Ties - and drawings of a planar network usually do tie, at zero crossings - are broken by the
 * separation: the smallest gap between any two features not incident to one another, relative to the size
 * of the drawing. That prefers a drawing whose nodes and edges keep clear of each other over one that
 * merely happens not to cross.
 * <p>
 * Smaller is better: {@link #compareTo} orders the best drawing first.
 * <p>
 * Daniel Huson, 8.2026
 */
public record LayoutScore(int crossings, double separation) implements Comparable<LayoutScore> {
	/**
	 * counting crossings is quadratic in the number of edges, so we only score drawings of graphs small
	 * enough for that to be free. Above this, {@link #isApplicable} reports false and the caller falls back
	 * to a single layout attempt.
	 */
	public static final int MAX_EDGES_FOR_SCORING = 1000;

	/**
	 * is this graph small enough to score?
	 */
	public static boolean isApplicable(Graph graph) {
		return graph.getNumberOfEdges() <= MAX_EDGES_FOR_SCORING;
	}

	/**
	 * scores the given drawing
	 *
	 * @param graph  the graph
	 * @param points position of each node
	 * @return score, smaller is better
	 */
	public static LayoutScore compute(Graph graph, Map<Node, Point2D> points) {
		var nodes = graph.getNodesAsList();
		var edges = graph.getEdgesAsList();

		// everything below is measured relative to the diagonal of the bounding box, so that the score does
		// not depend on the scale at which a particular layout happens to hand back its coordinates
		var xMin = Double.MAX_VALUE;
		var xMax = -Double.MAX_VALUE;
		var yMin = Double.MAX_VALUE;
		var yMax = -Double.MAX_VALUE;
		for (var v : nodes) {
			var p = points.get(v);
			if (p == null)
				continue;
			xMin = Math.min(xMin, p.getX());
			xMax = Math.max(xMax, p.getX());
			yMin = Math.min(yMin, p.getY());
			yMax = Math.max(yMax, p.getY());
		}
		if (xMin > xMax) // no node has a position, nothing to say about this drawing
			return new LayoutScore(0, 0.0);
		var diagonal = Math.max(1e-12, Math.hypot(xMax - xMin, yMax - yMin));

		// the orientation test below is a cross product of two difference vectors, so its natural tolerance
		// scales with the square of a length
		var epsilon = 1e-9 * diagonal * diagonal;

		var crossings = 0;
		for (var i = 0; i < edges.size(); i++) {
			var e = edges.get(i);
			var a = points.get(e.getSource());
			var b = points.get(e.getTarget());
			if (a == null || b == null)
				continue;
			for (var j = i + 1; j < edges.size(); j++) {
				var f = edges.get(j);
				if (shareEndPoint(e, f)) // two edges at a common node meet there by construction
					continue;
				var c = points.get(f.getSource());
				var d = points.get(f.getTarget());
				if (c != null && d != null && segmentsIntersect(a, b, c, d, epsilon))
					crossings++;
			}
		}

		var separation = Double.MAX_VALUE;
		for (var i = 0; i < nodes.size(); i++) {
			var p = points.get(nodes.get(i));
			if (p == null)
				continue;
			for (var j = i + 1; j < nodes.size(); j++) {
				var q = points.get(nodes.get(j));
				if (q != null)
					separation = Math.min(separation, p.distance(q));
			}
		}
		for (var v : nodes) {
			var p = points.get(v);
			if (p == null)
				continue;
			for (var e : edges) {
				if (e.getSource() == v || e.getTarget() == v) // a node lies on its own edges
					continue;
				var a = points.get(e.getSource());
				var b = points.get(e.getTarget());
				if (a != null && b != null)
					separation = Math.min(separation, pointSegmentDistance(p, a, b));
			}
		}
		if (separation == Double.MAX_VALUE) // fewer than two features, so nothing can be too close
			separation = diagonal;

		return new LayoutScore(crossings, separation / diagonal);
	}

	/**
	 * orders the best drawing first: fewest crossings wins, ties go to the drawing that is least crowded
	 */
	@Override
	public int compareTo(LayoutScore other) {
		if (crossings != other.crossings)
			return Integer.compare(crossings, other.crossings);
		else
			return Double.compare(other.separation, separation);
	}

	@Override
	public String toString() {
		return "crossings=%d, separation=%.4f".formatted(crossings, separation);
	}

	private static boolean shareEndPoint(Edge e, Edge f) {
		return e.getSource() == f.getSource() || e.getSource() == f.getTarget()
			   || e.getTarget() == f.getSource() || e.getTarget() == f.getTarget();
	}

	/**
	 * do segments ab and cd meet? This is the textbook orientation test. It is only ever called on edges that
	 * do not share an end point, so any contact at all - a proper crossing, an end point sitting on the other
	 * segment, or a collinear overlap - counts: all three read as a crossing.
	 */
	private static boolean segmentsIntersect(Point2D a, Point2D b, Point2D c, Point2D d, double epsilon) {
		var d1 = orientation(c, d, a, epsilon);
		var d2 = orientation(c, d, b, epsilon);
		var d3 = orientation(a, b, c, epsilon);
		var d4 = orientation(a, b, d, epsilon);

		if (((d1 > 0 && d2 < 0) || (d1 < 0 && d2 > 0)) && ((d3 > 0 && d4 < 0) || (d3 < 0 && d4 > 0)))
			return true; // the two segments properly cross

		// degenerate cases: an end point of one segment lies on the other
		return (d1 == 0 && withinBoundingBox(c, d, a)) || (d2 == 0 && withinBoundingBox(c, d, b))
			   || (d3 == 0 && withinBoundingBox(a, b, c)) || (d4 == 0 && withinBoundingBox(a, b, d));
	}

	/**
	 * is r to the left (1), to the right (-1) or on (0) the line through p and q?
	 */
	private static int orientation(Point2D p, Point2D q, Point2D r, double epsilon) {
		var cross = (q.getX() - p.getX()) * (r.getY() - p.getY()) - (q.getY() - p.getY()) * (r.getX() - p.getX());
		if (cross > epsilon)
			return 1;
		else if (cross < -epsilon)
			return -1;
		else
			return 0;
	}

	/**
	 * given that r is known to be collinear with p and q, does it lie between them?
	 */
	private static boolean withinBoundingBox(Point2D p, Point2D q, Point2D r) {
		return r.getX() >= Math.min(p.getX(), q.getX()) && r.getX() <= Math.max(p.getX(), q.getX())
			   && r.getY() >= Math.min(p.getY(), q.getY()) && r.getY() <= Math.max(p.getY(), q.getY());
	}

	/**
	 * distance from point p to the segment ab
	 */
	private static double pointSegmentDistance(Point2D p, Point2D a, Point2D b) {
		var dx = b.getX() - a.getX();
		var dy = b.getY() - a.getY();
		var lengthSquared = dx * dx + dy * dy;
		if (lengthSquared <= 0)
			return p.distance(a);
		var t = ((p.getX() - a.getX()) * dx + (p.getY() - a.getY()) * dy) / lengthSquared;
		t = Math.max(0.0, Math.min(1.0, t)); // clamp to the segment, we want the segment, not the line
		return p.distance(a.getX() + t * dx, a.getY() + t * dy);
	}
}
