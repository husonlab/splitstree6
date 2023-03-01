/*
 *  NeighborNetCycle2023.java Copyright (C) 2023 Daniel H. Huson
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

package splitstree6.algorithms.distances.distances2splits.neighbornet;

import jloda.graph.Graph;
import jloda.graph.Node;
import jloda.util.Pair;
import splitstree6.data.DistancesBlock;

import java.util.ArrayList;

/**
 * computes the circular ordering for a set of splits
 * This is the algorithm described in Bryant and Huson, 2023
 * David Bryant and Daniel Huson, 2.2023
 */
public class NeighborNetCycle2023 {
	private static boolean verbose = false;

	/**
	 * computes the circular ordering
	 *
	 * @param distancesBlock input distances
	 * @return 1-based cycle
	 */
	public static int[] computeOrdering(DistancesBlock distancesBlock) {
		var nTax = distancesBlock.getNtax();

		if (nTax <= 3)
			return createArrayUpwardCount(nTax);

		var graph = new Graph();
		var nodeMap = new Node[nTax + 1]; // taxa are 1-based
		var components = new ArrayList<Component>(); // components are 0-based

		for (var t = 1; t <= nTax; t++) {
			nodeMap[t] = graph.newNode(t);
			components.add(new Component(t));
		}

		var D = new double[nTax + 1][nTax + 1]; // copy distances because we will update them
		for (var i = 1; i <= nTax; i++) {
			for (var j = 1; j <= nTax; j++) {
				D[i][j] = distancesBlock.get(i, j);
			}
		}

		while (components.size() >= 2) {
			var pair = selectClosestPair(components, D);
			int ip = pair.getFirst(); // index of selected component P
			int iq = pair.getSecond(); // index of selected component Q
			var P = components.get(ip);
			var Q = components.get(iq);

			if (verbose)
				System.err.println("Selected: P={" + P + "} Q={" + Q + "}");

			if (P.size() == 1 && Q.size() == 1) {
				var p = P.first();
				var q = Q.first();
				graph.newEdge(nodeMap[p], nodeMap[q]);

				var newComponent = new Component(p, q);
				components.set(ip, newComponent);
				components.remove(iq);
				if (verbose)
					System.err.println("first case -> {" + newComponent + "}");
			} else if (P.size() == 1 && Q.size() == 2) {
				var p = P.first();
				var q = selectClosest1vs2(ip, iq, D, components);
				var qb = Q.other(q); // \bar q in text

				// update distances:
				D[p][qb] = D[qb][p] = (D[p][qb] + D[q][qb] + D[p][q]) / 3.0;
				for (var i = 0; i < components.size(); i++) {
					if (i != ip && i != iq) {
						for (var r : components.get(i).values()) {
							D[p][r] = D[r][p] = (2.0 * D[p][r] + D[q][r]) / 3.0;
							D[qb][r] = D[r][qb] = (2.0 * D[qb][r] + D[q][r]) / 3.0;
						}
					}
				}

				// update graph and components:
				graph.newEdge(nodeMap[p], nodeMap[q]);

				var newComponent = new Component(p, qb);
				components.set(ip, newComponent);
				components.remove(iq);
				if (verbose)
					System.err.println("second case -> {" + newComponent + "}");
			} else if (P.size() == 2 && Q.size() == 2) {
				var pq = selectClosest2vs2(ip, iq, D, components);
				int p = pq.getFirst();
				int pb = P.other(p); // \bar p in text
				int q = pq.getSecond();
				int qb = Q.other(q); // \bar q in text

				D[pb][qb] = D[qb][pb] = (D[pb][p] + D[pb][q] + D[pb][qb] + D[p][q] + D[p][qb] + D[q][qb]) / 6.0;

				for (var i = 0; i < components.size(); i++) {
					if (i != ip && i != iq) {
						for (var r : components.get(i).values()) {
							D[pb][r] = D[r][pb] = D[pb][r] / 2.0 + D[p][r] / 3.0 + D[q][r] / 6.0;
							D[qb][r] = D[r][qb] = D[p][r] / 6.0 + D[q][r] / 3.0 + D[qb][r] / 2.0;
						}
					}
				}
				graph.newEdge(nodeMap[p], nodeMap[q]);

				var newComponent = new Component(pb, qb);
				components.set(ip, newComponent);
				components.remove(iq);
				if (verbose)
					System.err.println("third case -> {" + newComponent + "}");
			} else
				throw new RuntimeException("Internal error: |P|=%d and |Q|=%d".formatted(P.size(), Q.size()));
		}

		// close cycle:
		var p = components.get(0).first();
		var q = components.get(0).second();
		graph.newEdge(nodeMap[p], nodeMap[q]);

		return extractOrdering(graph, nodeMap);
	}

	private static Pair<Integer, Integer> selectClosestPair(ArrayList<Component> components, double[][] D) {
		if (components.size() == 2) {
			return new Pair<>(0, 1);
		} else {
			var R = computeR(components, D);
			var best = Double.MAX_VALUE;
			Pair<Integer, Integer> pair = null;
			for (var ip = 0; ip < components.size(); ip++) {
				var P = components.get(ip);
				//System.err.println("R[{"+StringUtils.toString(P)+"}]="+R[i]);

				for (var iq = ip + 1; iq < components.size(); iq++) {
					var Q = components.get(iq);
					var adjustedD = (components.size() - 2) * averageD(D, P, Q) - R[ip] - R[iq];

					// System.err.println("["+P+"]["+Q+"]="+adjustedD);

					if (adjustedD < best) {
						pair = new Pair<>(ip, iq);
						best = adjustedD;
					}
				}
			}

			assert pair != null;

			if (components.get(pair.getFirst()).size() > components.get(pair.getSecond()).size()) {
				pair = new Pair<>(pair.getSecond(), pair.getFirst());
			}
			return pair;
		}
	}

	private static double[] computeR(ArrayList<Component> components, double[][] D) {
		var R = new double[components.size()];
		for (var ip = 0; ip < components.size(); ip++) {
			var sum = 0.0;
			for (var iq = 0; iq < components.size(); iq++) {
				if (ip != iq) {
					sum += averageD(D, components.get(ip), components.get(iq));
				}
			}
			R[ip] = sum;
		}
		return R;
	}

	private static double averageD(double[][] D, Component P, Component Q) {
		var sum = 0.0;
		for (var p : P.values()) {
			for (var q : Q.values()) {
				sum += D[p][q];
			}
		}
		return sum / (P.size() * Q.size());
	}

	private static double averageD(double[][] D, int p, Component Q) {
		var sum = 0.0;
		for (var q : Q.values()) {
			sum += D[p][q];
		}
		return sum / Q.size();
	}

	private static int selectClosest1vs2(int ip, int iq, double[][] D, ArrayList<Component> components) {
		var P = components.get(ip);
		assert P.size() == 1;
		var p = P.first();

		var Q = components.get(iq);
		assert Q.size() == 2;
		var q1 = Q.first();
		var q2 = Q.second();

		var pR = D[q1][p] + D[q2][p];
		var q1R = D[q1][q2] + D[q1][p];
		var q2R = D[q1][q2] + D[q2][p];

		for (var i = 0; i < components.size(); i++) {
			if (i != iq && i != ip) {
				var other = components.get(i);
				pR += averageD(D, p, other);
				q1R += averageD(D, q1, other);
				q2R += averageD(D, q2, other);
			}
		}

		// System.err.printf("p=%d: pR=%f%n", p,pR);
		// System.err.printf("q1=%d: q1R=%f%n", q1,q1R);
		// System.err.printf("q2=%d: q2R=%f%n", q2,q2R);

		var m = components.size();
		var q1pAjustedD = (m - 1) * D[q1][p] - q1R - pR;
		var q2pAdjustedD = (m - 1) * D[q2][p] - q2R - pR;

		if (q1pAjustedD <= q2pAdjustedD)
			return q1;
		else
			return q2;
	}

	private static Pair<Integer, Integer> selectClosest2vs2(int ip, int iq, double[][] D, ArrayList<Component> components) {
		var P = components.get(ip);
		assert P.size() == 2;
		var p1 = P.first();
		var p2 = P.second();

		var Q = components.get(iq);
		assert Q.size() == 2;
		var q1 = Q.first();
		var q2 = Q.second();

		var p1R = D[q1][p1] + D[q2][p1];
		var p2R = D[q1][p2] + D[q2][p2];
		var q1R = D[q1][q2] + D[q1][p1];
		var q2R = D[q1][q2] + D[q1][p1];

		for (var i = 0; i < components.size(); i++) {
			if (i != iq && i != ip) {
				var other = components.get(i);
				p1R += averageD(D, p1, other);
				p2R += averageD(D, p2, other);
				q1R += averageD(D, q1, other);
				q2R += averageD(D, q2, other);
			}
		}

		var m = components.size();
		var p1q1AdjustedD = m * D[p1][q1] - p1R - q1R;
		var p2q1AdjustedD = m * D[p2][q1] - p2R - q1R;
		var p1q2AdjustedD = m * D[p1][q2] - p1R - q2R;
		var p2q2AdjustedD = m * D[p2][q2] - p2R - q2R;

		return switch (rankOfMin(p1q1AdjustedD, p2q1AdjustedD, p1q2AdjustedD, p2q2AdjustedD)) {
			case 0 -> new Pair<>(p1, q1);
			case 1 -> new Pair<>(p2, q1);
			case 2 -> new Pair<>(p1, q2);
			default -> new Pair<>(p2, q2);
		};
	}

	/**
	 * get the rank of the smallest value
	 *
	 * @param values values
	 * @return position in array of smallest value
	 */
	public static int rankOfMin(double... values) {
		var rank = 0;
		for (var i = 1; i < values.length; i++) {
			if (values[i] < values[rank])
				rank = i;
		}
		return rank;
	}

	private static int[] extractOrdering(Graph graph, Node[] nodeMap) {
		var order = new ArrayList<Integer>();
		order.add(0); // cycle is 1-based

		try (var seen = graph.newNodeSet()) {
			var v = nodeMap[1];
			while (true) {
				order.add((int) v.getInfo());
				seen.add(v);
				if (seen.size() == graph.getNumberOfNodes())
					break;
				for (var w : v.adjacentNodes()) {
					if (!seen.contains(w)) {
						v = w;
						break;
					}
				}
			}
		}
		return order.stream().mapToInt(t -> t).toArray();
	}

	/**
	 * create an array of length n+1 and fill it with the numbers 0,..,n
	 *
	 * @param n last entry
	 * @return array containing values 0..n
	 */
	public static int[] createArrayUpwardCount(int n) {
		var array = new int[n + 1];
		for (var i = 0; i < n; i++)
			array[i] = i;
		return array;
	}

	private static record Component(int first, int second) {
		public Component(int first) {
			this(first, -1);
		}

		public boolean singleton() {
			return second == -1;
		}

		public int size() {
			return second == -1 ? 1 : 2;
		}

		public int[] values() {
			return singleton() ? new int[]{first} : new int[]{first, second};
		}

		public String toString() {
			if (singleton())
				return String.valueOf(first());
			else
				return first() + "," + second();
		}

		public int other(int p) {
			if (p != first)
				return first;
			else
				return second;
		}
	}
}
