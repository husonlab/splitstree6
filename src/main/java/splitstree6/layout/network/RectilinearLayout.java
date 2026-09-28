/*
 * RectilinearLayout.java Copyright (C) 2026 Daniel H. Huson
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

package splitstree6.layout.network;

import jloda.graph.Edge;
import jloda.graph.Node;
import jloda.phylo.PhyloGraph;

import java.util.*;
import java.util.function.ToDoubleFunction;

/**
 * straightens a drawing of a network on a grid, so that edges run horizontally or vertically where possible and
 * diagonally otherwise, as in hand-drawn haplotype networks
 * <p>
 * A local search in the manner of Stott et al. (2011) for metro maps. Starting from the current drawing, with every
 * node on a grid point of its own, it moves a node, or a node together with what hangs from it by bridges, to a
 * nearby free grid point whenever that lowers the cost of the drawing. The cost adds, over edges and nodes:
 * <ul>
 *     <li>direction: nothing for a horizontal or vertical edge, a little for a diagonal one, and more for any other,
 *     growing with its angle to the nearest of these eight directions;</li>
 *     <li>length: the relative difference between the length of an edge and its target length;</li>
 *     <li>crossings: a lot for each pair of crossing edges;</li>
 *     <li>clearance: a lot for a node on an edge not its own, less for one close to such an edge, and something for
 *     two nodes closer than two grid steps, as their labels need the room;</li>
 *     <li>angles: something for two edges at a node that are less than 45 degrees apart, and a little for a bend in
 *     a chain through a node of degree two;</li>
 *     <li>displacement: a little per grid step that a node has moved from where it started.</li>
 * </ul>
 * The move radius shrinks from four grid steps to one. At each radius the search works through a list of nodes,
 * the most costly first, and a move puts back on the list the nodes whose best position it may have changed, until
 * no node has an improving move left. Besides the grid points around a node, it tries the points at about the
 * target length from each neighbor in each of the eight directions. As that ends in a local optimum, there follow
 * twenty rounds that shake a few costly nodes and search again, each kept only if the whole drawing got cheaper; on
 * the example networks, this removed most of the edges left slanted. Every kept move lowers the cost, so the result
 * is never worse than the start. The search stops early when the time budget is spent; otherwise the same drawing
 * gives the same result. Only the part of the cost that a move changes is evaluated, using a bucket grid to find the
 * nearby edges and nodes.
 * <p>
 * Pure: grid points in, grid points out, no JavaFX.
 * <p>
 * Stott J., Rodgers P., Martínez-Ovando J.C., Walker S.G. (2011) Automatic metro map layout using multicriteria
 * optimization. IEEE Trans. Vis. Comput. Graph. 17(1):101-114.
 * <p>
 * Daniel Huson, 9.2026
 */
public class RectilinearLayout {
	/**
	 * a point of the grid, in grid steps
	 */
	public record GridPoint(int x, int y) {
	}

	/**
	 * cost of a diagonal edge
	 */
	private static final double DIAGONAL = 2.0;
	/**
	 * cost of an edge in none of the eight directions, plus up to SLANTED_ANGLE as its angle to the nearest of them
	 * grows to 22.5 degrees
	 */
	private static final double SLANTED = 6.0;
	private static final double SLANTED_ANGLE = 4.0;
	/**
	 * cost per unit of relative difference between the length of an edge and its target length
	 */
	private static final double LENGTH = 2.0;
	/**
	 * cost of two crossing edges
	 */
	private static final double CROSSING = 40.0;
	/**
	 * cost of a node on an edge not its own
	 */
	private static final double ON_EDGE = 40.0;
	/**
	 * cost of a node close to an edge not its own, falling from this to zero at half a grid step
	 */
	private static final double NEAR_EDGE = 10.0;
	/**
	 * cost per grid step by which two nodes are closer than two steps
	 */
	private static final double CROWDING = 3.0;
	/**
	 * cost per 45 degrees by which two consecutive edges at a node are closer than 45 degrees
	 */
	private static final double SHARP_ANGLE = 2.0;
	/**
	 * cost per 90 degrees of bend in a chain through a node of degree two
	 */
	private static final double BEND = 0.5;
	/**
	 * cost per grid step that a node has moved from its start
	 */
	private static final double DISPLACEMENT = 0.05;

	private static final double EPSILON = 1e-6;
	private static final double EIGHTH = Math.PI / 4;

	/**
	 * the move radius goes down from this, in grid steps, to one
	 */
	private static final int MAX_RADIUS = 4;
	/**
	 * the rounds that shake up a few nodes and search again, and how far a node is shaken, in grid steps. A fixed
	 * number of rounds and a fixed seed make the result the same every time, unless the time budget cuts it short
	 */
	private static final int ROUNDS = 20;
	private static final int SHAKE_RADIUS = 2;
	private static final long SEED = 42;
	/**
	 * a node is moved together with what hangs from it by bridges only if that has at most this many nodes
	 */
	private static final int MAX_GROUP = 50;
	/**
	 * the side of a bucket of the grid that indexes the nodes and edges, in grid steps
	 */
	private static final int BUCKET = 4;

	/**
	 * straightens a drawing
	 *
	 * @param graph        the network
	 * @param start        the grid point of each node, all distinct
	 * @param targetLength the length each edge should have, in grid steps
	 * @param budgetMillis the time allowed, in milliseconds
	 * @return the new grid point of each node
	 */
	public static Map<Node, GridPoint> apply(PhyloGraph graph, Map<Node, GridPoint> start, ToDoubleFunction<Edge> targetLength, long budgetMillis) {
		return prepare(graph, start, targetLength).run(budgetMillis);
	}

	/**
	 * reads the network into a search that no longer touches the graph, so that it can run on another thread
	 *
	 * @param graph        the network
	 * @param start        the grid point of each node, all distinct
	 * @param targetLength the length each edge should have, in grid steps
	 * @return the search, ready to run
	 */
	public static Search prepare(PhyloGraph graph, Map<Node, GridPoint> start, ToDoubleFunction<Edge> targetLength) {
		return new Search(graph, start, targetLength);
	}

	/**
	 * the state of the search, with nodes and edges numbered from 0
	 */
	public static class Search {
		private final Node[] nodes;
		private final int n;
		private final int m;
		private final int[] x;
		private final int[] y;
		private final int[] startX;
		private final int[] startY;
		private final int[] source;
		private final int[] target;
		private final double[] length;
		private final int[][] incident;
		private final int[][] groups;

		private final Map<Long, Integer> occupied = new HashMap<>();
		private final Map<Long, List<Integer>> nodeBuckets = new HashMap<>();
		private final Map<Long, List<Integer>> edgeBuckets = new HashMap<>();

		// stamps that de-duplicate the results of a query, and that mark the nodes and edges of a set
		private final int[] nodeStamp;
		private final int[] edgeStamp;
		private int stamp = 0;
		private final int[] nodeInSet;
		private final int[] edgeInSet;
		private int setStamp = 0;
		private final int[] angleStamp;
		private int angleStampValue = 0;

		private int[] found = new int[64];
		private int numberFound = 0;
		private final List<Integer> setEdges = new ArrayList<>();

		Search(PhyloGraph graph, Map<Node, GridPoint> start, ToDoubleFunction<Edge> targetLength) {
			nodes = graph.getNodesAsList().toArray(new Node[0]);
			n = nodes.length;
			var index = new HashMap<Node, Integer>();
			for (var i = 0; i < n; i++)
				index.put(nodes[i], i);

			var edges = new ArrayList<Edge>();
			for (var e : graph.edges()) {
				if (e.getSource() != e.getTarget())
					edges.add(e);
			}
			m = edges.size();
			source = new int[m];
			target = new int[m];
			length = new double[m];
			var degree = new int[n];
			for (var i = 0; i < m; i++) {
				var e = edges.get(i);
				source[i] = index.get(e.getSource());
				target[i] = index.get(e.getTarget());
				length[i] = Math.max(1.0, targetLength.applyAsDouble(e));
				degree[source[i]]++;
				degree[target[i]]++;
			}
			incident = new int[n][];
			for (var v = 0; v < n; v++)
				incident[v] = new int[degree[v]];
			var fill = new int[n];
			for (var e = 0; e < m; e++) {
				incident[source[e]][fill[source[e]]++] = e;
				incident[target[e]][fill[target[e]]++] = e;
			}

			x = new int[n];
			y = new int[n];
			for (var v = 0; v < n; v++) {
				var p = start.get(nodes[v]);
				var cell = findFree(p != null ? p.x() : 0, p != null ? p.y() : 0); // the start should have no collisions
				x[v] = cell.x();
				y[v] = cell.y();
				occupied.put(key(x[v], y[v]), v);
			}
			startX = x.clone();
			startY = y.clone();

			nodeStamp = new int[n];
			edgeStamp = new int[m];
			nodeInSet = new int[n];
			edgeInSet = new int[m];
			angleStamp = new int[n];

			for (var v = 0; v < n; v++)
				addToBuckets(nodeBuckets, v, x[v], y[v], x[v], y[v]);
			for (var e = 0; e < m; e++)
				addEdgeToBuckets(e);

			groups = computeGroups();
		}

		/**
		 * runs the search: a local search, then rounds that shake up a few of the most costly nodes and search again,
		 * keeping the result of a round only if the whole drawing got cheaper
		 *
		 * @param budgetMillis the time allowed, in milliseconds
		 * @return the new grid point of each node
		 */
		public Map<Node, GridPoint> run(long budgetMillis) {
			search(System.currentTimeMillis() + budgetMillis);
			var result = new HashMap<Node, GridPoint>();
			for (var v = 0; v < n; v++)
				result.put(nodes[v], new GridPoint(x[v], y[v]));
			return result;
		}

		private void search(long deadline) {
			if (m == 0)
				return;
			localSearch(MAX_RADIUS, deadline, allNodes());
			var bestX = x.clone();
			var bestY = y.clone();
			var bestCost = cost(allNodes());
			var random = new Random(SEED);
			for (var round = 0; round < ROUNDS && System.currentTimeMillis() < deadline; round++) {
				localSearch(SHAKE_RADIUS, deadline, shake(random));
				var cost = cost(allNodes());
				if (cost < bestCost - EPSILON) {
					bestCost = cost;
					bestX = x.clone();
					bestY = y.clone();
				} else
					setPositions(bestX, bestY);
			}
		}

		/**
		 * moves a few nodes, drawn with probability proportional to their share of the cost, by up to SHAKE_RADIUS
		 *
		 * @return the nodes whose best position the shaking may have changed
		 */
		private int[] shake(Random random) {
			var costs = new double[n];
			var total = 0.0;
			for (var v = 0; v < n; v++) {
				costs[v] = cost(new int[]{v});
				total += costs[v];
			}
			var affected = new LinkedHashSet<Integer>();
			for (var i = 0; total > 0 && i < Math.max(2, n / 10); i++) {
				var r = random.nextDouble() * total;
				var v = 0;
				while (v < n - 1 && r >= costs[v]) {
					r -= costs[v];
					v++;
				}
				var set = new int[]{v};
				var dx = random.nextInt(2 * SHAKE_RADIUS + 1) - SHAKE_RADIUS;
				var dy = random.nextInt(2 * SHAKE_RADIUS + 1) - SHAKE_RADIUS;
				if ((dx != 0 || dy != 0) && isFree(set, dx, dy)) {
					collectAffected(set, affected);
					move(set, dx, dy);
					collectAffected(set, affected);
				}
			}
			return affected.stream().mapToInt(Integer::intValue).toArray();
		}

		/**
		 * the local search. At each radius, going down from the given one to one, it repeatedly takes the most costly
		 * node of a work list and moves it, alone or with what hangs from it, to its best candidate position. The
		 * work list starts with the given nodes, and each move adds the nodes it may have given a better position:
		 * those moved, their neighbors, the nodes near them, and the ends of the edges near their edges. The search
		 * at a radius ends when the work list is empty, that is, when no node has an improving move left
		 */
		private void localSearch(int maxRadius, long deadline, int[] start) {
			var touched = new LinkedHashSet<Integer>();
			for (var v : start)
				touched.add(v);
			var affected = new LinkedHashSet<Integer>();
			for (var radius = maxRadius; radius >= 1; radius--) {
				var costs = new double[n];
				var order = new ArrayList<>(touched);
				for (var v : order)
					costs[v] = cost(new int[]{v});
				order.sort((a, b) -> Double.compare(costs[b], costs[a])); // stable: ties stay in node order
				var queue = new ArrayDeque<>(order);
				var queued = new boolean[n];
				for (var v : order)
					queued[v] = true;
				while (!queue.isEmpty()) {
					if (System.currentTimeMillis() > deadline)
						return;
					var v = queue.poll();
					queued[v] = false;
					affected.clear();
					if (improve(new int[]{v}, v, radius, affected) || (groups[v] != null && improve(groups[v], v, radius, affected))) {
						for (var w : affected) {
							touched.add(w);
							if (!queued[w]) {
								queued[w] = true;
								queue.add(w);
							}
						}
					}
				}
			}
		}

		/**
		 * adds the nodes of the set, their neighbors, the nodes near them and the ends of the edges near their edges
		 */
		private void collectAffected(int[] set, Set<Integer> affected) {
			for (var w : set) {
				affected.add(w);
				for (var e : incident[w])
					affected.add(opposite(e, w));
				findNodes(x[w] - 2, y[w] - 2, x[w] + 2, y[w] + 2);
				for (var i = 0; i < numberFound; i++)
					affected.add(found[i]);
			}
			for (var e : edgesOf(set)) {
				findEdges(Math.min(x[source[e]], x[target[e]]), Math.min(y[source[e]], y[target[e]]),
						Math.max(x[source[e]], x[target[e]]), Math.max(y[source[e]], y[target[e]]));
				for (var i = 0; i < numberFound; i++) {
					affected.add(source[found[i]]);
					affected.add(target[found[i]]);
				}
			}
		}

		private int[] allNodes() {
			var all = new int[n];
			for (var v = 0; v < n; v++)
				all[v] = v;
			return all;
		}

		/**
		 * puts every node at the given position, and rebuilds the occupied grid points and the buckets
		 */
		private void setPositions(int[] newX, int[] newY) {
			System.arraycopy(newX, 0, x, 0, n);
			System.arraycopy(newY, 0, y, 0, n);
			occupied.clear();
			nodeBuckets.clear();
			edgeBuckets.clear();
			for (var v = 0; v < n; v++) {
				occupied.put(key(x[v], y[v]), v);
				addToBuckets(nodeBuckets, v, x[v], y[v], x[v], y[v]);
			}
			for (var e = 0; e < m; e++)
				addEdgeToBuckets(e);
		}

		/**
		 * moves the set to the best of its candidate positions, if that lowers the cost
		 *
		 * @param set      the nodes to move together
		 * @param anchor   the node of the set whose neighbors outside the set suggest positions
		 * @param affected receives the nodes whose best position the move may have changed
		 * @return whether the set was moved
		 */
		private boolean improve(int[] set, int anchor, int radius, Set<Integer> affected) {
			var best = cost(set) - EPSILON;
			var bestDx = 0;
			var bestDy = 0;
			for (var offset : candidateOffsets(set, anchor, radius)) {
				var dx = offset.x();
				var dy = offset.y();
				double cost;
				if (set.length == 1) {
					// a single node is tried in place, without updating the buckets: the queries still find every
					// other node and edge, and those of the node itself are left out of its cost anyway
					var v = set[0];
					if (occupied.containsKey(key(x[v] + dx, y[v] + dy)))
						continue;
					x[v] += dx;
					y[v] += dy;
					cost = cost(set);
					x[v] -= dx;
					y[v] -= dy;
				} else if (isFree(set, dx, dy)) {
					move(set, dx, dy);
					cost = cost(set);
					move(set, -dx, -dy);
				} else
					continue;
				if (cost < best) {
					best = cost;
					bestDx = dx;
					bestDy = dy;
				}
			}
			if (bestDx != 0 || bestDy != 0) {
				collectAffected(set, affected);
				move(set, bestDx, bestDy);
				collectAffected(set, affected);
				return true;
			} else
				return false;
		}

		/**
		 * the offsets to try: all within the radius, and those that put the anchor at about the target length from a
		 * neighbor outside the set, in one of the eight directions
		 */
		private List<GridPoint> candidateOffsets(int[] set, int anchor, int radius) {
			var seen = new HashSet<Long>();
			var list = new ArrayList<GridPoint>();
			for (var dx = -radius; dx <= radius; dx++) {
				for (var dy = -radius; dy <= radius; dy++) {
					if ((dx != 0 || dy != 0) && seen.add(key(dx, dy)))
						list.add(new GridPoint(dx, dy));
				}
			}
			markSet(set);
			for (var e : incident[anchor]) {
				var u = opposite(e, anchor);
				if (nodeInSet[u] == setStamp)
					continue;
				for (var dirX = -1; dirX <= 1; dirX++) {
					for (var dirY = -1; dirY <= 1; dirY++) {
						if (dirX == 0 && dirY == 0)
							continue;
						var steps = (int) Math.round(dirX != 0 && dirY != 0 ? length[e] / Math.sqrt(2) : length[e]);
						for (var k = Math.max(1, steps - 1); k <= steps + 1; k++) {
							var dx = x[u] + k * dirX - x[anchor];
							var dy = y[u] + k * dirY - y[anchor];
							if ((dx != 0 || dy != 0) && seen.add(key(dx, dy)))
								list.add(new GridPoint(dx, dy));
						}
					}
				}
			}
			return list;
		}

		/**
		 * whether every node of the set, moved by the offset, lands on a grid point that is free or held by the set
		 */
		private boolean isFree(int[] set, int dx, int dy) {
			markSet(set);
			for (var v : set) {
				var other = occupied.get(key(x[v] + dx, y[v] + dy));
				if (other != null && nodeInSet[other] != setStamp)
					return false;
			}
			return true;
		}

		/**
		 * moves the nodes of the set by the offset, keeping the occupied grid points and the buckets up to date
		 */
		private void move(int[] set, int dx, int dy) {
			var edges = edgesOf(set);
			for (var e : edges)
				removeEdgeFromBuckets(e);
			for (var v : set) {
				occupied.remove(key(x[v], y[v]));
				removeFromBuckets(nodeBuckets, v, x[v], y[v], x[v], y[v]);
			}
			for (var v : set) {
				x[v] += dx;
				y[v] += dy;
			}
			for (var v : set) {
				occupied.put(key(x[v], y[v]), v);
				addToBuckets(nodeBuckets, v, x[v], y[v], x[v], y[v]);
			}
			for (var e : edges)
				addEdgeToBuckets(e);
		}

		/**
		 * the part of the cost of the drawing that depends on where the nodes of the set are
		 */
		private double cost(int[] set) {
			markSet(set);
			setEdges.clear();
			for (var v : set) {
				for (var e : incident[v]) {
					if (edgeInSet[e] != setStamp) {
						edgeInSet[e] = setStamp;
						setEdges.add(e);
					}
				}
			}

			var cost = 0.0;
			for (var e : setEdges) {
				cost += directionCost(e) + lengthCost(e);
				var u = source[e];
				var v = target[e];
				var minX = Math.min(x[u], x[v]);
				var maxX = Math.max(x[u], x[v]);
				var minY = Math.min(y[u], y[v]);
				var maxY = Math.max(y[u], y[v]);

				// crossings, counting each pair once
				findEdges(minX, minY, maxX, maxY);
				for (var i = 0; i < numberFound; i++) {
					var f = found[i];
					if (f != e && !(edgeInSet[f] == setStamp && f < e) && !adjacent(e, f) && intersect(e, f))
						cost += CROSSING;
				}
				// nodes on or near the edge
				findNodes(minX - 1, minY - 1, maxX + 1, maxY + 1);
				for (var i = 0; i < numberFound; i++) {
					var w = found[i];
					if (w != u && w != v)
						cost += clearanceCost(w, e);
				}
			}

			for (var w : set) {
				// edges near the node that are not at the set (those were counted above)
				findEdges(x[w] - 1, y[w] - 1, x[w] + 1, y[w] + 1);
				for (var i = 0; i < numberFound; i++) {
					var f = found[i];
					if (edgeInSet[f] != setStamp)
						cost += clearanceCost(w, f);
				}
				// other nodes too close, joined by an edge or not, counting each pair once: labels need the room
				findNodes(x[w] - 2, y[w] - 2, x[w] + 2, y[w] + 2);
				for (var i = 0; i < numberFound; i++) {
					var z = found[i];
					if (z != w && !(nodeInSet[z] == setStamp && z < w)) {
						var distance = Math.hypot(x[w] - x[z], y[w] - y[z]);
						if (distance < 2)
							cost += CROWDING * (2 - distance);
					}
				}
				cost += DISPLACEMENT * Math.hypot(x[w] - startX[w], y[w] - startY[w]);
			}

			// angles at the nodes of the set and at their neighbors, each node once
			angleStampValue++;
			for (var w : set) {
				cost += angleCostOnce(w);
				for (var e : incident[w])
					cost += angleCostOnce(opposite(e, w));
			}
			return cost;
		}

		private double angleCostOnce(int v) {
			if (angleStamp[v] == angleStampValue)
				return 0;
			angleStamp[v] = angleStampValue;
			return angleCost(v);
		}

		/**
		 * nothing for a horizontal or vertical edge, DIAGONAL for a diagonal one, and SLANTED plus up to SLANTED_ANGLE
		 * for any other, by its angle to the nearest of the eight directions
		 */
		private double directionCost(int e) {
			var dx = Math.abs(x[target[e]] - x[source[e]]);
			var dy = Math.abs(y[target[e]] - y[source[e]]);
			if (dx == 0 || dy == 0)
				return 0;
			if (dx == dy)
				return DIAGONAL;
			var angle = Math.atan2(dy, dx); // strictly between 0 and 90 degrees, and not 45
			var deviation = Math.min(Math.min(angle, Math.abs(angle - EIGHTH)), 0.5 * Math.PI - angle);
			return SLANTED + SLANTED_ANGLE * deviation / (0.5 * EIGHTH);
		}

		private double lengthCost(int e) {
			var actual = Math.hypot(x[target[e]] - x[source[e]], y[target[e]] - y[source[e]]);
			return LENGTH * Math.abs(actual - length[e]) / length[e];
		}

		/**
		 * ON_EDGE for a node on the edge, falling from NEAR_EDGE to zero at half a grid step away
		 */
		private double clearanceCost(int w, int e) {
			var distance = distanceToSegment(x[w], y[w], x[source[e]], y[source[e]], x[target[e]], y[target[e]]);
			if (distance < EPSILON)
				return ON_EDGE;
			else if (distance < 0.5)
				return NEAR_EDGE * (1 - 2 * distance);
			else
				return 0;
		}

		/**
		 * SHARP_ANGLE per 45 degrees by which consecutive edges at the node are closer than 45 degrees, and, at a node of
		 * degree two, BEND per 90 degrees of bend
		 */
		private double angleCost(int v) {
			var degree = incident[v].length;
			if (degree < 2)
				return 0;
			var angles = new double[degree];
			for (var i = 0; i < degree; i++) {
				var w = opposite(incident[v][i], v);
				angles[i] = Math.atan2(y[w] - y[v], x[w] - x[v]);
			}
			Arrays.sort(angles);
			var cost = 0.0;
			for (var i = 0; i < degree; i++) {
				var gap = (i + 1 < degree ? angles[i + 1] : angles[0] + 2 * Math.PI) - angles[i];
				if (gap < EIGHTH)
					cost += SHARP_ANGLE * (EIGHTH - gap) / EIGHTH;
			}
			if (degree == 2) {
				var between = Math.min(angles[1] - angles[0], 2 * Math.PI - (angles[1] - angles[0]));
				cost += BEND * (Math.PI - between) / (0.5 * Math.PI);
			}
			return cost;
		}

		private boolean adjacent(int e, int f) {
			return source[e] == source[f] || source[e] == target[f] || target[e] == source[f] || target[e] == target[f];
		}

		private int opposite(int e, int v) {
			return source[e] == v ? target[e] : source[e];
		}

		/**
		 * whether two edges meet, crossing or touching
		 */
		private boolean intersect(int e, int f) {
			long ax = x[source[e]], ay = y[source[e]], bx = x[target[e]], by = y[target[e]];
			long cx = x[source[f]], cy = y[source[f]], dx = x[target[f]], dy = y[target[f]];
			var o1 = orientation(ax, ay, bx, by, cx, cy);
			var o2 = orientation(ax, ay, bx, by, dx, dy);
			var o3 = orientation(cx, cy, dx, dy, ax, ay);
			var o4 = orientation(cx, cy, dx, dy, bx, by);
			if (o1 != o2 && o3 != o4)
				return true;
			return (o1 == 0 && onSegment(ax, ay, bx, by, cx, cy)) || (o2 == 0 && onSegment(ax, ay, bx, by, dx, dy))
				   || (o3 == 0 && onSegment(cx, cy, dx, dy, ax, ay)) || (o4 == 0 && onSegment(cx, cy, dx, dy, bx, by));
		}

		private static int orientation(long ax, long ay, long bx, long by, long cx, long cy) {
			return Long.signum((bx - ax) * (cy - ay) - (by - ay) * (cx - ax));
		}

		/**
		 * whether the point c, collinear with a and b, lies between them
		 */
		private static boolean onSegment(long ax, long ay, long bx, long by, long cx, long cy) {
			return Math.min(ax, bx) <= cx && cx <= Math.max(ax, bx) && Math.min(ay, by) <= cy && cy <= Math.max(ay, by);
		}

		private static double distanceToSegment(double px, double py, double ax, double ay, double bx, double by) {
			var dx = bx - ax;
			var dy = by - ay;
			var lengthSquared = dx * dx + dy * dy;
			var t = (lengthSquared == 0 ? 0 : Math.max(0, Math.min(1, ((px - ax) * dx + (py - ay) * dy) / lengthSquared)));
			return Math.hypot(px - (ax + t * dx), py - (ay + t * dy));
		}

		private void markSet(int[] set) {
			setStamp++;
			for (var v : set)
				nodeInSet[v] = setStamp;
		}

		private List<Integer> edgesOf(int[] set) {
			markSet(set);
			var list = new ArrayList<Integer>();
			for (var v : set) {
				for (var e : incident[v]) {
					if (edgeInSet[e] != setStamp) {
						edgeInSet[e] = setStamp;
						list.add(e);
					}
				}
			}
			return list;
		}

		/**
		 * collects, into found, the edges registered in the buckets that meet the box
		 */
		private void findEdges(int minX, int minY, int maxX, int maxY) {
			stamp++;
			numberFound = 0;
			for (var bx = Math.floorDiv(minX, BUCKET); bx <= Math.floorDiv(maxX, BUCKET); bx++) {
				for (var by = Math.floorDiv(minY, BUCKET); by <= Math.floorDiv(maxY, BUCKET); by++) {
					var list = edgeBuckets.get(key(bx, by));
					if (list != null) {
						for (var f : list) {
							if (edgeStamp[f] != stamp) {
								edgeStamp[f] = stamp;
								addFound(f);
							}
						}
					}
				}
			}
		}

		/**
		 * collects, into found, the nodes in the box
		 */
		private void findNodes(int minX, int minY, int maxX, int maxY) {
			stamp++;
			numberFound = 0;
			for (var bx = Math.floorDiv(minX, BUCKET); bx <= Math.floorDiv(maxX, BUCKET); bx++) {
				for (var by = Math.floorDiv(minY, BUCKET); by <= Math.floorDiv(maxY, BUCKET); by++) {
					var list = nodeBuckets.get(key(bx, by));
					if (list != null) {
						for (var v : list) {
							if (nodeStamp[v] != stamp && x[v] >= minX && x[v] <= maxX && y[v] >= minY && y[v] <= maxY) {
								nodeStamp[v] = stamp;
								addFound(v);
							}
						}
					}
				}
			}
		}

		private void addFound(int value) {
			if (numberFound == found.length)
				found = Arrays.copyOf(found, 2 * found.length);
			found[numberFound++] = value;
		}

		private void addEdgeToBuckets(int e) {
			addToBuckets(edgeBuckets, e, Math.min(x[source[e]], x[target[e]]), Math.min(y[source[e]], y[target[e]]),
					Math.max(x[source[e]], x[target[e]]), Math.max(y[source[e]], y[target[e]]));
		}

		private void removeEdgeFromBuckets(int e) {
			removeFromBuckets(edgeBuckets, e, Math.min(x[source[e]], x[target[e]]), Math.min(y[source[e]], y[target[e]]),
					Math.max(x[source[e]], x[target[e]]), Math.max(y[source[e]], y[target[e]]));
		}

		private static void addToBuckets(Map<Long, List<Integer>> buckets, int item, int minX, int minY, int maxX, int maxY) {
			for (var bx = Math.floorDiv(minX, BUCKET); bx <= Math.floorDiv(maxX, BUCKET); bx++) {
				for (var by = Math.floorDiv(minY, BUCKET); by <= Math.floorDiv(maxY, BUCKET); by++) {
					buckets.computeIfAbsent(key(bx, by), k -> new ArrayList<>()).add(item);
				}
			}
		}

		private static void removeFromBuckets(Map<Long, List<Integer>> buckets, int item, int minX, int minY, int maxX, int maxY) {
			for (var bx = Math.floorDiv(minX, BUCKET); bx <= Math.floorDiv(maxX, BUCKET); bx++) {
				for (var by = Math.floorDiv(minY, BUCKET); by <= Math.floorDiv(maxY, BUCKET); by++) {
					var list = buckets.get(key(bx, by));
					if (list != null)
						list.remove(Integer.valueOf(item));
				}
			}
		}

		/**
		 * the free grid point nearest to the given one, searching rings of increasing radius
		 */
		private GridPoint findFree(int x0, int y0) {
			for (var r = 0; ; r++) {
				for (var dx = -r; dx <= r; dx++) {
					for (var dy = -r; dy <= r; dy++) {
						if (Math.max(Math.abs(dx), Math.abs(dy)) == r && !occupied.containsKey(key(x0 + dx, y0 + dy)))
							return new GridPoint(x0 + dx, y0 + dy);
					}
				}
			}
		}

		private static long key(int a, int b) {
			return ((long) a << 32) ^ (b & 0xffffffffL);
		}

		/**
		 * for each node, the node together with what hangs from it by bridges, on the side away from the core of its
		 * part of the network; null if nothing hangs from it, or more than MAX_GROUP nodes would move. The core is the
		 * largest part without bridges, and in a tree the node of highest degree
		 */
		private int[][] computeGroups() {
			var bridge = findBridges();

			// the components that remain when the bridges are removed
			var component = new int[n];
			Arrays.fill(component, -1);
			var componentSize = new ArrayList<Integer>();
			var componentDegree = new ArrayList<Integer>();
			var queue = new ArrayDeque<Integer>();
			for (var s = 0; s < n; s++) {
				if (component[s] == -1) {
					var id = componentSize.size();
					var size = 0;
					var degree = 0;
					component[s] = id;
					queue.add(s);
					while (!queue.isEmpty()) {
						var v = queue.poll();
						size++;
						degree += incident[v].length;
						for (var e : incident[v]) {
							var w = opposite(e, v);
							if (!bridge[e] && component[w] == -1) {
								component[w] = id;
								queue.add(w);
							}
						}
					}
					componentSize.add(size);
					componentDegree.add(degree);
				}
			}

			// breadth-first from the core of each connected part; beyond a bridge, everything is reached through it
			var parent = new int[n];
			Arrays.fill(parent, -2);
			var children = new ArrayList<List<Integer>>();
			for (var v = 0; v < n; v++)
				children.add(new ArrayList<>());
			for (var s = 0; s < n; s++) {
				if (parent[s] != -2)
					continue;
				// the connected part of s, and the best component in it
				var part = new ArrayList<Integer>();
				var seen = new HashSet<Integer>();
				seen.add(s);
				queue.add(s);
				while (!queue.isEmpty()) {
					var v = queue.poll();
					part.add(v);
					for (var e : incident[v]) {
						var w = opposite(e, v);
						if (seen.add(w))
							queue.add(w);
					}
				}
				var core = component[s];
				for (var v : part) {
					var c = component[v];
					if (componentSize.get(c) > componentSize.get(core) || (componentSize.get(c).equals(componentSize.get(core)) && componentDegree.get(c) > componentDegree.get(core)))
						core = c;
				}
				for (var v : part) {
					if (component[v] == core) {
						parent[v] = -1;
						queue.add(v);
					}
				}
				while (!queue.isEmpty()) {
					var v = queue.poll();
					for (var e : incident[v]) {
						var w = opposite(e, v);
						if (parent[w] == -2) {
							parent[w] = v;
							if (bridge[e])
								children.get(v).add(w);
							queue.add(w);
						}
					}
				}
			}

			// the tree of the breadth-first search, to collect what lies beyond each bridge
			var treeChildren = new ArrayList<List<Integer>>();
			for (var v = 0; v < n; v++)
				treeChildren.add(new ArrayList<>());
			for (var v = 0; v < n; v++) {
				if (parent[v] >= 0)
					treeChildren.get(parent[v]).add(v);
			}

			var groups = new int[n][];
			for (var v = 0; v < n; v++) {
				if (children.get(v).isEmpty())
					continue;
				var group = new ArrayList<Integer>();
				group.add(v);
				var stack = new ArrayDeque<>(children.get(v));
				while (!stack.isEmpty() && group.size() <= MAX_GROUP) {
					var w = stack.pop();
					group.add(w);
					stack.addAll(treeChildren.get(w));
				}
				if (stack.isEmpty() && group.size() <= MAX_GROUP)
					groups[v] = group.stream().mapToInt(Integer::intValue).toArray();
			}
			return groups;
		}

		/**
		 * the bridges, by an iterative depth-first search that computes low points (Tarjan)
		 */
		private boolean[] findBridges() {
			var bridge = new boolean[m];
			var discovered = new int[n];
			Arrays.fill(discovered, -1);
			var low = new int[n];
			var parentEdge = new int[n];
			var stackNode = new int[n];
			var stackNext = new int[n];
			var time = 0;
			for (var s = 0; s < n; s++) {
				if (discovered[s] != -1)
					continue;
				var top = 0;
				stackNode[0] = s;
				stackNext[0] = 0;
				parentEdge[s] = -1;
				discovered[s] = low[s] = time++;
				while (top >= 0) {
					var v = stackNode[top];
					if (stackNext[top] < incident[v].length) {
						var e = incident[v][stackNext[top]++];
						if (e == parentEdge[v])
							continue;
						var w = opposite(e, v);
						if (discovered[w] == -1) {
							parentEdge[w] = e;
							discovered[w] = low[w] = time++;
							top++;
							stackNode[top] = w;
							stackNext[top] = 0;
						} else
							low[v] = Math.min(low[v], discovered[w]);
					} else {
						top--;
						if (top >= 0) {
							var p = stackNode[top];
							low[p] = Math.min(low[p], low[v]);
							if (low[v] > discovered[p])
								bridge[parentEdge[v]] = true;
						}
					}
				}
			}
			return bridge;
		}
	}
}
