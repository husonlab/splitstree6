/*
 *  BiConnectedComponents.java Copyright (C) 2024 Daniel H. Huson
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

package splitstree6.xtra.kernelize;

import jloda.graph.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

/**
 * use Tarjan's algorithm to determine all bi-connected components
 * todo: this is untested
 * Daniel Huson, 2.2024
 */
public class BiConnectedComponents {

	private int time;
	private NodeIntArray discovery;
	private NodeIntArray low;
	private NodeArray<Node> parent;
	private Stack<Node> stack;
	private List<List<Node>> biconnectedComponents;
	private NodeSet isVisited;

	private BiConnectedComponents() {
	}

	private List<List<Node>> findBiConnectedComponents(Graph graph) {
		discovery = graph.newNodeIntArray();
		low = graph.newNodeIntArray();
		parent = graph.newNodeArray();
		isVisited = graph.newNodeSet();
		try {
			stack = new Stack<>();
			biconnectedComponents = new ArrayList<>();
			time = 0;

			for (var v : graph.nodes()) {
				discovery.set(v, -1);
				low.set(v, -1);
				parent.put(v, null);
			}

			for (var v : graph.nodes()) {
				if (!isVisited.contains(v))
					dfs(v);
			}

			return biconnectedComponents;
		} finally {
			discovery.close();
			low.close();
			parent.close();
			isVisited.close();
		}
	}

	private void dfs(Node u) {
		isVisited.add(u);
		time++;
		discovery.put(u, time);
		low.put(u, time);
		stack.push(u);
		int children = 0;

		for (var v : u.adjacentNodes()) {
			if (!isVisited.contains(v)) {
				children++;
				parent.put(v, u);
				dfs(v);

				low.put(u, Math.min(low.get(u), low.get(v)));

				if ((parent.get(u) == null && children > 1) || (parent.get(u) != null && low.get(v) >= discovery.get(u))) {
					addComponent(stack, u, v);
				}
			} else if (v != parent.get(u) && discovery.get(v) < low.get(u)) {
				low.put(u, discovery.get(v));
			}
		}
	}

	private void addComponent(Stack<Node> stack, Node u, Node v) {
		var component = new ArrayList<Node>();
		while (!stack.isEmpty()) {
			var node = stack.pop();
			component.add(node);
			if (node == u && v == null)
				break;
			if (node == v)
				break;
		}
		biconnectedComponents.add(component);
	}

	/**
	 * apply the algorithm
	 *
	 * @param graph graph
	 *              todo: this is untested
	 * @return bi-connected components
	 */
	public static List<List<Node>> apply(Graph graph) {
		return (new BiConnectedComponents()).findBiConnectedComponents(graph);
	}
}
