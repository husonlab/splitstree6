/*
 * BiConnectedComponents2.java Copyright (C) 2024 Daniel H. Huson
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

import jloda.graph.Edge;
import jloda.graph.Graph;
import jloda.graph.Node;

import java.util.*;


public class BiConnectedComponents2 {
	private int time;
	private Map<Node, Integer> low;
	private Set<Node> visited;
	private Stack<Node> stack;
	private List<Set<Node>> biconnectedComponents;

	/**
	 * find all biconnected components
	 *
	 * @param graph graph
	 * @return components
	 */
	public static List<Set<Node>> apply(Graph graph) {
		return (new BiConnectedComponents2().findBCCs(graph));
	}

	/**
	 * find all biconnected components
	 *
	 * @param graph graph
	 * @return components
	 */
	public List<Set<Node>> findBCCs(Graph graph) {
		low = new HashMap<>();
		visited = new HashSet<>();
		stack = new Stack<>();
		biconnectedComponents = new ArrayList<>();
		time = 0;

		for (var node : graph.nodes()) {
			low.put(node, -1);
		}

		for (var v : graph.nodes()) {
			if (!visited.contains(v)) {
				dfs(v, null);
			}
		}
		return biconnectedComponents;

	}

	/**
	 * perform DFS calculation
	 *
	 * @param v current node
	 * @param e entry edge
	 */
	private void dfs(Node v, Edge e) {
		low.put(v, time++);
		visited.add(v);
		stack.push(v);

		var min = low.get(v);

		for (var f : v.adjacentEdges()) {
			if (f != e) {
				var w = f.getOpposite(v);
				if (!visited.contains(w)) {
					dfs(w, f);
					min = Math.min(low.get(w), min);
				}

				if (min < low.get(v)) {
					low.put(v, min);
					return;
				}

				var component = new HashSet<Node>();
				Node u;
				do {
					u = stack.pop();
					component.add(u);
					low.put(u, u.getOwner().getNumberOfNodes());
				} while (u != v);
				biconnectedComponents.add(component);
			}
		}
	}
}
