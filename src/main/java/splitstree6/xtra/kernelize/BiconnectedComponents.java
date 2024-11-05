/*
 * BiconnectedComponents.java Copyright (C) 2024 Daniel H. Huson
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
import jloda.util.SetUtils;
import jloda.util.StringUtils;

import java.io.IOException;
import java.util.*;

public class BiconnectedComponents {

	private List<Set<Node>> biconnectedComponents;  // To store each biconnected component as a set of nodes
	private Map<Node, Integer> disc;                // Discovery time of nodes
	private Map<Node, Integer> low;                 // Lowest discovery time reachable
	private Map<Node, Node> parent;                 // Parent nodes in DFS tree
	private Stack<Node> stack;                      // Stack to store nodes during DFS traversal
	private int time;                               // Global time for DFS discovery times

	public BiconnectedComponents() {
		biconnectedComponents = new ArrayList<>();
		disc = new HashMap<>();
		low = new HashMap<>();
		parent = new HashMap<>();
		stack = new Stack<>();
		time = 0;
	}

	public static List<Set<Node>> apply(Graph graph) {
		return (new BiconnectedComponents()).findBiconnectedComponents(graph);
	}

	public List<Set<Node>> findBiconnectedComponents(Graph graph) {
		// Initialize discovery and low values for all nodes as unvisited (-1)
		for (Node node : graph.nodes()) {
			disc.put(node, -1);
			low.put(node, -1);
			parent.put(node, null);
		}

		// Apply DFS to each unvisited node
		for (Node node : graph.nodes()) {
			if (disc.get(node) == -1) {
				dfs(node, graph);
			}
		}

		return biconnectedComponents;
	}

	private void dfs(Node u, Graph graph) {
		// Set discovery time and low value for u
		disc.put(u, time);
		low.put(u, time);
		time++;

		int children = 0;  // Number of children in DFS tree for root check
		stack.push(u);     // Push the node onto the stack

		for (Node v : u.adjacentNodes()) {
			if (disc.get(v) == -1) {  // If v is not visited
				parent.put(v, u);
				children++;
				dfs(v, graph);

				// Update low value of u for parent function calls
				low.put(u, Math.min(low.get(u), low.get(v)));

				// If u is an articulation point, pop all nodes up to v to form a biconnected component
				if ((parent.get(u) == null && children > 1) ||
					(parent.get(u) != null && low.get(v) >= disc.get(u))) {
					Set<Node> component = new HashSet<>();
					Node node;
					do {
						node = stack.pop();
						component.add(node);
					} while (!node.equals(v));
					component.add(u);  // Add the articulation point to the component
					biconnectedComponents.add(component);
				}

			} else if (!v.equals(parent.get(u)) && disc.get(v) < disc.get(u)) {
				// Update low[u] for back edge
				low.put(u, Math.min(low.get(u), disc.get(v)));
				if (!stack.contains(v)) { // Only add v if it’s not already in the stack
					stack.push(v);
				}
			}
		}

		// Add any remaining component containing u if it's the root and still in the stack
		if (parent.get(u) == null && !stack.isEmpty()) {
			Set<Node> component = new HashSet<>();
			while (!stack.isEmpty()) {
				component.add(stack.pop());
			}
			biconnectedComponents.add(component);
		}
	}

	public static void main(String[] args) throws IOException {
		var tree = new PhyloTree();
		tree.parseBracketNotation("(((A:2)#H1,(B:2)#H2):1,(#H1,#H2):1,C:2);", false);
		var copy = new PhyloTree(tree);
		for (var v : copy.nodes()) {
			if (copy.getLabel(v) == null) {
				copy.setLabel(v, "i{" + v.getId() + "}");

			} else
				copy.setLabel(v, copy.getLabel(v) + "{" + v.getId() + "}");
		}
		System.err.println(copy.toBracketString(true) + ";");

		System.err.println("Components:");
		var components = apply(tree);
		for (var component : components) {
			System.err.println(StringUtils.toString(component.stream().map(v -> v.getId()).toList(), " "));
		}

		for (var component : components) {
			if (component.size() > 2) {
				var nodesWithParentsInComponent = new ArrayList<Node>();
				var nodesWithParentsNotInComponent = new ArrayList<Node>();
				var nodesWithChildrenNotInComponent = new ArrayList<Node>();

				for (var v : component) {
					for (var p : v.parents()) {
						if (component.contains(p)) {
							nodesWithParentsInComponent.add(v);
						} else {
							nodesWithParentsNotInComponent.add(v);
						}
					}

					for (var c : v.children()) {
						if (!component.contains(c)) {
							nodesWithChildrenNotInComponent.add(v);
						}
					}
				}

				if (nodesWithParentsNotInComponent.size() > 1) {
					throw new RuntimeException("Biconnected component has too many top nodes " + nodesWithParentsNotInComponent.size());
				}

				if (SetUtils.intersect(nodesWithParentsInComponent, nodesWithParentsNotInComponent)) {
					throw new RuntimeException("Node has parents both inside and outside of biconnected component");
				}

				Node topNode;
				if (nodesWithParentsNotInComponent.size() == 1) {
					topNode = nodesWithParentsNotInComponent.get(0);
				} else {
					if (component.contains(tree.getRoot()))
						topNode = tree.getRoot();
					else
						throw new RuntimeException("Biconnected component has no top node");
				}
				for (var p : nodesWithChildrenNotInComponent) {
					for (var e : p.outEdges()) {
						var q = e.getTarget();
						if (!component.contains(q) && !topNode.isChild(q)) {
							System.err.println("new edge: " + topNode.getId() + " " + q.getId());
							var f = tree.newEdge(topNode, q);
							if (tree.hasEdgeWeights()) {
								tree.setWeight(f, tree.getWeight(e));
							}
							if (tree.hasEdgeConfidences()) {
								tree.setConfidence(f, tree.getConfidence(e));
							}
						}
					}
				}
				for (var v : component) {
					if (v != topNode) {
						System.err.println("deleting: " + v.getId());
						tree.deleteNode(v);
					}
				}
			}
		}
		tree.clearReticulateEdges();
		System.err.println(tree.toBracketString(true) + ";");
	}
}