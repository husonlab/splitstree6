/*
 * RazorExpand.java Copyright (C) 2025 Daniel H. Huson
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

package splitstree6.algorithms.distances.distances2network.razor2;

import jloda.util.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import static splitstree6.algorithms.distances.distances2network.razor2.RazorDebugPrint.showGraph;
import static splitstree6.algorithms.distances.distances2network.razor2.RazorSlack.slackWithArgmin;
import static splitstree6.algorithms.distances.distances2network.razor2.RazorUtils.checkAllDistancesEven;
import static splitstree6.algorithms.distances.distances2network.razor2.RazorUtils.fullIndexSet;


public class RazorExpand {

	public static Collection<OutputEdge> expand(int[][] D) {
		checkAllDistancesEven(D);
		var subset = fullIndexSet(D.length);
		var workingGraph = RazorUtils.realizeFromDistances(D, false);
		var edges = new HashSet<OutputEdge>();
		expand(D, subset, workingGraph, edges);
		return edges;
	}

	public record Result(Collection<OutputEdge> edges, boolean doubled) {
	}

	private static void expand(int[][] D, Set<Integer> subset, UGraph graph, Set<OutputEdge> outputEdges) {
		System.err.println(">>>>>>>>>>>> Start expand, output: " + outputEdges.size());
		if (true) RazorDebugPrint.showOutputGraph("Output graph at start", outputEdges);
		if (true) System.err.println("Subset: " + StringUtils.toString(subset, " "));
		if (true) RazorDebugPrint.showMatrix("Input D", D);
		if (true) showGraph("Input graph", graph);

		var slackVertices = new ArrayList<Integer>(); // x -> (s, y, z)
		var anySlack = false;
		for (var x : subset) {
			var sx = slackWithArgmin(D, subset, x);
			if (sx.s() > 0) {
				anySlack = true;
				slackVertices.add(x);
			}
		}

		if (!anySlack) {
			return; // No vertex is slack in this subset → nothing to expand here.
		}

		System.err.println("Slack candidates: " + StringUtils.toString(slackVertices, " "));

		for (var x : slackVertices) {
			var slack = slackWithArgmin(D, subset, x);
			if (slack.s() > 0) {
				if (true)
					System.err.println("Slack x=" + x + ", s=" + slack.s() + ", y=" + slack.y() + ", z=" + slack.z());
				var candidateColumn = buildAuxColumnUsingAll(D, x, subset, slack);
				var existing = checkForOffDiagonalZero(candidateColumn, subset);
				//var existing = coincidesWithExisting(D, candidateColumn);
				if (existing == -1) {
					D = expandMatrix(D, candidateColumn);
					var xp = D.length - 1;
					subset.remove(x);
					subset.add(xp);
					for (var e : graph.outEdges(x)) {
						var other = e.other(x);
						if (subset.contains(other)) {
							graph.addEdge(xp, other, graph.getWeight(e) - slack.s());
						}
					}
					graph.removeNode(x);
					graph.addEdge(x, xp, slack.s());
					outputEdges.add(new OutputEdge(x, xp, slack.s()));
					if (true) System.err.println("Expanded " + x + " -> " + xp + " s=" + slack.s());
				} else {
					subset.remove(x);
					graph.removeNode(x);
					graph.addEdge(x, existing, slack.s());
					outputEdges.add(new OutputEdge(x, existing, slack.s()));
					for (var i = 0; i < D.length; i++) {
						if (i == existing) {
							D[x][existing] = D[existing][x] = slack.s();
						} else {
							D[x][i] = D[i][x] = 0;
						}
					}
					if (true) System.err.println("Connected " + x + " -> " + existing + " s=" + slack.s());
				}
				if (true) RazorDebugPrint.showMatrix("D after expand x=" + x, D);
				if (true) showGraph("Graph after expand x=" + x, graph);
			} else
				System.err.println("No longer slack: " + x);
		}

		if (true) System.err.println("Subset after expand: " + StringUtils.toString(subset, " "));


		if (true) showGraph("Before redundant removal", graph);

		var toRemove = new ArrayList<UGraph.Edge>();
		for (var e : graph.edges(subset)) {
			if (RazorUtils.isRedundantEdge(D, e.u(), e.v(), subset)) {
				toRemove.add(e);
			}
		}
		for (var e : toRemove) {
			graph.removeEdge(e.u(), e.v());
		}
		if (true) showGraph("After redundant removal", graph);


		if (true) {
			var degree1Nodes = graph.nodes().stream().filter(u -> graph.degree(u) == 1).toList();
			if (!degree1Nodes.isEmpty()) {
				for (var u : degree1Nodes) {
					var v = graph.neighbors(u).get(0);
					var outputEdge = new OutputEdge(u, v, graph.getWeight(u, v));
					outputEdges.add(outputEdge);
					if (true) System.err.println("added: " + outputEdge);
					graph.removeNode(u);

				}
				if (true) System.err.println("Cleaning1 removed " + StringUtils.toString(degree1Nodes, " "));
				if (true) showGraph("After cleaning1", graph);
			}
		}

		if (true) {
			var degree1Nodes = graph.nodes().stream().filter(u -> graph.degree(u) == 1).toList();
			for (var u : degree1Nodes) {
				var v = graph.neighbors(u).get(0);
				var outputEdge = new OutputEdge(u, v, graph.getWeight(u, v));
				outputEdges.add(outputEdge);
				if (true) System.err.println("added: " + outputEdge);
				graph.removeNode(u);

			}
			var degree2Nodes = graph.nodes().stream().filter(u -> graph.degree(u) == 2).toList();
			if (!degree2Nodes.isEmpty()) {
				for (var u : degree2Nodes) {
					for (var v : graph.neighbors(u, subset)) {
						var outputEdge = new OutputEdge(u, v, graph.getWeight(u, v));
						outputEdges.add(outputEdge);
						if (true) System.err.println("added: " + outputEdge);
					}
					graph.removeNode(u);
				}
				if (true) System.err.println("Cleaning2 removed " + StringUtils.toString(degree2Nodes, " "));
				if (true) showGraph("After cleaning2", graph, subset);
			}
		}

		if (true) System.err.println("Components: " + graph.connectedComponents().size());
		for (var component : graph.connectedComponents()) {
			if (true) System.err.println("Component " + StringUtils.toString(component, " "));
			if (component.size() > 1) {
				var inducedGraph = RazorUtils.realizeFromDistances(D, component, false);
				expand(D, component, inducedGraph, outputEdges);
			}
		}

		if (true) RazorDebugPrint.showOutputGraph("Output graph at end", outputEdges);
	}

	static int[] buildAuxColumnUsingMax3(int[][] D, int x, RazorSlack.Slack sx) {
		var s = sx.s();
		var y = sx.y();
		var z = sx.z();

		var dy = Math.max(D[y][x] - s, 0);
		var dz = Math.max(D[z][x] - s, 0);

		var n = D.length;
		var cand = new int[n];
		for (int a = 0; a < n; a++) {
			if (a == x) {
				cand[a] = s;
				continue;
			}
			if (a == y) {
				cand[a] = dy;
				continue;
			}
			if (a == z) {
				cand[a] = dz;
				continue;
			}
			var v = Math.max(D[a][x] - s, Math.max(D[a][y] - dy, D[a][z] - dz));
			cand[a] = v;
		}
		return cand;
	}

	static int[] buildAuxColumnUsingAll(int[][] D, int x, Set<Integer> subset, RazorSlack.Slack sx) {
		var s = sx.s();
		var y = sx.y();
		var z = sx.z();

		var dy = Math.max(D[y][x] - s, 0);
		var dz = Math.max(D[z][x] - s, 0);

		var n = D.length;
		var col = new int[n];

		for (var a = 0; a < n; a++) {
			if (a == x) {
				col[a] = s;
			} else if (a == y) {
				col[a] = dy;
			} else if (a == z) {
				col[a] = dz;
			} else {
				var a_xp = 0;
				for (var b : subset) {
					if (b != x && b != y && b != z) {
						a_xp = Math.max(a_xp, D[a][x] - s);
					}
				}
				var v = Math.max(D[a][x] - s, Math.max(D[a][y] - dy, D[a][z] - dz));

				if (v != a_xp)
					System.err.println("v vs a_xp: " + v + " " + a_xp);

				col[a] = a_xp;
			}
		}
		return col;
	}

	/**
	 * If cand duplicates an existing vertex row within EPS (and is 0 to that vertex), return its index; else null.
	 */
	static int coincidesWithExisting(int[][] D, int[] cand) {
		int n = D.length;
		for (int a = 0; a < n; a++) {
			if (Math.abs(cand[a]) > 0) continue;      // must be 0 to “stick” to a
			var same = true;
			for (int b = 0; b < n; b++) {
				if (Math.abs(D[a][b] - cand[b]) > 0) {
					same = false;
					break;
				}
			}
			if (same) return a;
		}
		return -1;
	}

	static int checkForOffDiagonalZero(int[] cand, Set<Integer> subset) {
		for (var a : subset) {
			if (cand[a] == 0)
				return a;
		}
		return -1;

	}

	private static int[][] expandMatrix(int[][] D, int[] cand) {
		final var n = D.length;
		final var result = new int[n + 1][n + 1];
		for (var i = 0; i < n; i++) {
			System.arraycopy(D[i], 0, result[i], 0, n);
		}
		for (var i = 0; i < n; i++) {
			result[i][n] = cand[i];
			result[n][i] = cand[i];
		}
		result[n][n] = 0;
		return result;
	}

	public record OutputEdge(int i, int j, int w) {
		public OutputEdge(int i, int j, int w) {
			this.i = Math.min(i, j);
			this.j = Math.max(i, j);
			this.w = w;
		}

		public String toString() {
			return "(" + i + "-" + j + "," + w + ")";
		}
	}
}
