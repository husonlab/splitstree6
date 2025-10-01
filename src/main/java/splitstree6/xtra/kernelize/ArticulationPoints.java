/*
 * ArticulationPoints.java Copyright (C) 2024 Daniel H. Huson
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

package splitstree6.xtra.kernelize;

import jloda.graph.Graph;
import jloda.graph.Node;
import jloda.phylo.PhyloTree;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * compute articulation points, that is, nodes that separate bi-connected connectedComponents
 * Daniel Huson, 10.2024, using ChatGPT
 */
public class ArticulationPoints {

	final private Set<Node> articulationPoints;
	final private Map<Node, Integer> disc;
	final private Map<Node, Integer> low;
	final private Map<Node, Node> parent;
	private int time;

	/**
	 * compute all articulation points
	 *
	 * @param graph the graph
	 * @return the points
	 */
	public static Set<Node> apply(Graph graph) {
		return (new ArticulationPoints()).findArticulationPoints(graph);
	}

	private ArticulationPoints() {
		articulationPoints = new HashSet<>();
		disc = new HashMap<>();
		low = new HashMap<>();
		parent = new HashMap<>();
		time = 0;
	}

	private Set<Node> findArticulationPoints(Graph graph) {
		// Initialize all nodes as unvisited (-1)
		for (Node node : graph.nodes()) {
			disc.put(node, -1);
			low.put(node, -1);
			parent.put(node, null);
		}

		// Apply DFS to each unvisited node
		for (Node node : graph.nodes()) {
			if (disc.get(node) == -1) {
				dfs(node);
			}
		}

		return articulationPoints;
	}

	private void dfs(Node u) {
		// Set discovery time and low value for u
		disc.put(u, time);
		low.put(u, time);
		time++;

		int children = 0;  // Number of children in DFS tree for root check

		for (Node v : u.adjacentNodes()) {
			if (disc.get(v) == -1) {  // If v is not visited
				parent.put(v, u);
				children++;
				dfs(v);

				// Check if the subtree rooted at v has a connection back to one of u's ancestors
				low.put(u, Math.min(low.get(u), low.get(v)));

				// If u is an articulation point based on the conditions
				if ((parent.get(u) == null && children > 1) ||
					(parent.get(u) != null && low.get(v) >= disc.get(u))) {
					articulationPoints.add(u);
				}

			} else if (!v.equals(parent.get(u))) {
				// Update low[u] for back edge
				low.put(u, Math.min(low.get(u), disc.get(v)));
			}
		}
	}

	public static void main(String[] args) throws IOException {
		var tree = new PhyloTree();
		tree.parseBracketNotation("(((A)#H1,(B)#H2),(#H1,#H2),C);", false);
		var copy = new PhyloTree(tree);
		for (var v : copy.nodes()) {
			if (copy.getLabel(v) == null) {
				copy.setLabel(v, "i{" + v.getId() + "}");

			} else
				copy.setLabel(v, copy.getLabel(v) + "{" + v.getId() + "}");
		}
		System.err.println(copy.toBracketString(false) + ";");

		System.err.println("Articulations:");
		var articulations = apply(tree);
		for (var v : articulations) {
			System.err.print(" " + v.getId());
		}
		System.err.println();
	}
}