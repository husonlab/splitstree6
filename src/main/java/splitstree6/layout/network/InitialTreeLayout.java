/*
 * InitialTreeLayout.java Copyright (C) 2026 Daniel H. Huson
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

import java.util.*;
import java.util.function.ToDoubleFunction;

/**
 * crossing-free initial placement for a network, obtained by drawing a minimum-weight spanning tree radially
 * <p>
 * A force-directed layout is good at getting edge lengths right and hopeless at getting a rotation system
 * right: it has no move that un-crosses two edges, so whatever tangles are present in the configuration it
 * starts from are still there when it converges. That makes the starting configuration matter a great deal,
 * and it is exactly what was missing - {@code NetworkLayout} asks the FMM layout to keep the positions it is
 * handed, but used to hand it none, so every node started life at the origin and the drawing was untangled
 * by nothing but numerical jitter.
 * <p>
 * This supplies a starting configuration that is guaranteed to have no crossings at all. We take a
 * minimum-weight spanning tree - minimum, so that it keeps the short edges and the long ones are the few
 * that get added back - and draw it radially: nodes at depth d sit on a circle of radius R(d), and each
 * subtree owns an angular wedge disjoint from those of its siblings. Two edges can then only cross if their
 * annuli overlap, which needs them to be at the same depth, and if their angular ranges overlap, which
 * cannot happen because each stays inside its own parent's wedge. Adding the few non-tree edges back may
 * introduce a crossing, but only as many as the graph's skewness forces.
 * <p>
 * The division of labour is deliberate: this fixes the topology, which the force layout cannot, and gets
 * edge lengths only roughly right, which the force layout then fixes. Ring spacing therefore follows the
 * longest edge entering that depth rather than each edge individually - individual radii would break the
 * disjoint-annuli argument above and with it the guarantee.
 * <p>
 * Daniel Huson, 8.2026
 */
public class InitialTreeLayout {
	/**
	 * rings must strictly grow outwards for the crossing-free argument to hold, so an edge of weight zero
	 * still has to push the next ring out by something
	 */
	private static final double MIN_RING_GAP = 0.01;

	/**
	 * a wedge narrowed away to nothing would stack a node's children on top of one another; keep a sliver
	 */
	private static final double MIN_WEDGE = 1e-4;

	/**
	 * relative jitter applied to the weights that the spanning tree is chosen by, when exploring
	 */
	private static final double TREE_WEIGHT_JITTER = 0.6;

	/**
	 * computes a crossing-free placement of the nodes of the given graph
	 *
	 * @param graph       the graph; need be neither connected nor simple
	 * @param edgeWeights desired edge lengths, used both to choose the spanning tree and to space the rings
	 * @param random      null for the canonical placement (tree centre as root, natural sibling order), else
	 *                    a source of randomness used to pick a different root and shuffle siblings, which
	 *                    yields a genuinely different - but equally crossing-free - starting configuration
	 * @return position of every node of the graph
	 */
	public static Map<Node, Point2D> apply(Graph graph, ToDoubleFunction<Edge> edgeWeights, Random random) {
		var points = new HashMap<Node, Point2D>();
		if (graph.getNumberOfNodes() == 0)
			return points;

		// When exploring, jitter the weights the spanning tree is chosen by, so that different candidates
		// leave different edges out. Which edges end up as chords across the drawing matters as much as where
		// the tree's own nodes go, since those are the only edges the construction below cannot place well.
		// The jitter decides the tree and nothing else: the rings are still spaced by the real weights.
		var treeWeights = edgeWeights;
		if (random != null) {
			var perturbed = new HashMap<Edge, Double>();
			for (var e : graph.edges())
				perturbed.put(e, edgeWeights.applyAsDouble(e) * (1.0 + TREE_WEIGHT_JITTER * (random.nextDouble() - 0.5)));
			treeWeights = perturbed::get;
		}
		var treeEdges = SpanningTree.kruskal(graph.getNodesAsList(), graph.getEdgesAsList(),
				Edge::getSource, Edge::getTarget, treeWeights, false);

		// adjacency restricted to the spanning tree, or forest, if the graph is not connected
		var adjacency = new HashMap<Node, List<Edge>>();
		for (var e : treeEdges) {
			adjacency.computeIfAbsent(e.getSource(), k -> new ArrayList<>()).add(e);
			adjacency.computeIfAbsent(e.getTarget(), k -> new ArrayList<>()).add(e);
		}

		var seen = new HashSet<Node>();
		var xOffset = 0.0;
		for (var start : graph.nodes()) {
			if (seen.contains(start))
				continue;
			var component = collectComponent(start, adjacency);
			seen.addAll(component);
			var root = (random == null ? treeCentre(start, adjacency) : component.get(random.nextInt(component.size())));
			var componentPoints = layoutComponent(root, adjacency, edgeWeights, random);
			xOffset = appendComponent(points, componentPoints, xOffset);
		}
		return points;
	}

	/**
	 * lays out one component of the spanning forest, rooted at the given node, centred on the origin
	 */
	private static Map<Node, Point2D> layoutComponent(Node root, Map<Node, List<Edge>> adjacency,
													  ToDoubleFunction<Edge> edgeWeights, Random random) {
		// breadth-first from the root, so that a parent always precedes its children below
		var order = new ArrayList<Node>();
		var children = new HashMap<Node, List<Node>>();
		var depth = new HashMap<Node, Integer>();
		var visited = new HashSet<Node>();
		var queue = new ArrayDeque<Node>();

		order.add(root);
		visited.add(root);
		depth.put(root, 0);
		queue.add(root);

		var maxDepth = 0;
		// longest edge entering each depth, which is what sets that ring's distance from the previous one
		var levelEdgeLength = new HashMap<Integer, Double>();
		while (!queue.isEmpty()) {
			var v = queue.poll();
			for (var e : adjacency.getOrDefault(v, List.of())) {
				var w = e.getOpposite(v);
				if (visited.add(w)) {
					var d = depth.get(v) + 1;
					depth.put(w, d);
					maxDepth = Math.max(maxDepth, d);
					levelEdgeLength.merge(d, Math.max(0.0, edgeWeights.applyAsDouble(e)), Math::max);
					children.computeIfAbsent(v, k -> new ArrayList<>()).add(w);
					order.add(w);
					queue.add(w);
				}
			}
		}

		// a different sibling order is a different rotation system, and so a different drawing; this is what
		// makes repeated attempts explore anything, rather than all starting from the same configuration
		if (random != null) {
			for (var list : children.values())
				Collections.shuffle(list, random);
		}

		var ringRadius = new double[maxDepth + 1];
		for (var d = 1; d <= maxDepth; d++)
			ringRadius[d] = ringRadius[d - 1] + Math.max(MIN_RING_GAP, levelEdgeLength.getOrDefault(d, 0.0));

		// number of leaves below each node, which is how the wedges get shared out; children precede their
		// parent when the breadth-first order is walked backwards
		var leaves = new HashMap<Node, Integer>();
		for (var i = order.size() - 1; i >= 0; i--) {
			var v = order.get(i);
			var kids = children.getOrDefault(v, List.of());
			if (kids.isEmpty())
				leaves.put(v, 1);
			else {
				var sum = 0;
				for (var c : kids)
					sum += leaves.get(c);
				leaves.put(v, sum);
			}
		}

		var points = new HashMap<Node, Point2D>();
		var wedgeStart = new HashMap<Node, Double>();
		var wedgeEnd = new HashMap<Node, Double>();
		var angle = new HashMap<Node, Double>();

		points.put(root, Point2D.ZERO);
		wedgeStart.put(root, 0.0);
		wedgeEnd.put(root, 2 * Math.PI);
		angle.put(root, 0.0);

		for (var v : order) {
			var kids = children.getOrDefault(v, List.of());
			if (kids.isEmpty())
				continue;
			var from = wedgeStart.get(v);
			var to = wedgeEnd.get(v);
			var r = ringRadius[depth.get(v)];
			if (r > 0) {
				// the standard radial-tree restriction: seen from radius r, the part of the next ring that
				// lies "in front of" v spans +- acos(r / rNext) about v's own direction. Keeping children
				// inside that stops their edges from curling back around v's ring, which is legal but ugly.
				var rNext = ringRadius[depth.get(v) + 1];
				var alpha = Math.acos(Math.min(1.0, r / rNext));
				var theta = angle.get(v);
				from = Math.max(from, theta - alpha);
				to = Math.min(to, theta + alpha);
				if (to - from < MIN_WEDGE) {
					from = theta - 0.5 * MIN_WEDGE;
					to = theta + 0.5 * MIN_WEDGE;
				}
			}
			var total = leaves.get(v); // equals the sum over the children, as v has some
			var offset = from;
			for (var c : kids) {
				var width = (to - from) * leaves.get(c) / total;
				var theta = offset + 0.5 * width;
				var radius = ringRadius[depth.get(c)];
				wedgeStart.put(c, offset);
				wedgeEnd.put(c, offset + width);
				angle.put(c, theta);
				points.put(c, new Point2D(radius * Math.cos(theta), radius * Math.sin(theta)));
				offset += width;
			}
		}
		return points;
	}

	/**
	 * copies one component's points into the result, shifted so that components sit side by side rather than
	 * on top of each other, and returns the x offset at which the next component should start
	 */
	private static double appendComponent(Map<Node, Point2D> points, Map<Node, Point2D> componentPoints, double xOffset) {
		if (componentPoints.isEmpty())
			return xOffset;
		var xMin = componentPoints.values().stream().mapToDouble(Point2D::getX).min().orElse(0.0);
		var xMax = componentPoints.values().stream().mapToDouble(Point2D::getX).max().orElse(0.0);
		var gap = Math.max(MIN_RING_GAP, 0.1 * (xMax - xMin));
		for (var entry : componentPoints.entrySet())
			points.put(entry.getKey(), entry.getValue().add(xOffset - xMin, 0));
		return xOffset + (xMax - xMin) + gap;
	}

	/**
	 * all nodes in the spanning-forest component containing the given node
	 */
	private static List<Node> collectComponent(Node start, Map<Node, List<Edge>> adjacency) {
		var component = new ArrayList<Node>();
		var visited = new HashSet<Node>();
		var queue = new ArrayDeque<Node>();
		queue.add(start);
		visited.add(start);
		while (!queue.isEmpty()) {
			var v = queue.poll();
			component.add(v);
			for (var e : adjacency.getOrDefault(v, List.of())) {
				var w = e.getOpposite(v);
				if (visited.add(w))
					queue.add(w);
			}
		}
		return component;
	}

	/**
	 * a node in the middle of the component, found by the usual two sweeps: the far end of the tree from an
	 * arbitrary node, then the far end from there, then the midpoint of that path. Rooting there keeps the
	 * drawing shallow and so keeps the wedges wide.
	 */
	private static Node treeCentre(Node start, Map<Node, List<Edge>> adjacency) {
		var first = farthest(start, adjacency, null);
		var parents = new HashMap<Node, Node>();
		var second = farthest(first, adjacency, parents);
		var path = new ArrayList<Node>();
		for (var v = second; v != null; v = parents.get(v))
			path.add(v);
		return path.get(path.size() / 2);
	}

	/**
	 * the node at the greatest number of hops from the given one, optionally recording the search tree
	 */
	private static Node farthest(Node start, Map<Node, List<Edge>> adjacency, Map<Node, Node> parents) {
		var visited = new HashSet<Node>();
		var queue = new ArrayDeque<Node>();
		queue.add(start);
		visited.add(start);
		var last = start;
		while (!queue.isEmpty()) {
			var v = queue.poll();
			last = v; // breadth-first, so the node dequeued last is one of the deepest
			for (var e : adjacency.getOrDefault(v, List.of())) {
				var w = e.getOpposite(v);
				if (visited.add(w)) {
					if (parents != null)
						parents.put(w, v);
					queue.add(w);
				}
			}
		}
		return last;
	}
}
