/*
 * RazorLocalPrunerOnGraph.java Copyright (C) 2025 Daniel H. Huson
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

package splitstree6.algorithms.distances.distances2network.razor1;

import jloda.graph.Edge;
import jloda.graph.Node;
import jloda.phylo.PhyloGraph;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.ToDoubleFunction;

import static splitstree6.algorithms.distances.distances2network.razor1.RazorExpand.EPS;

/**
 * removes edges that are locally redundant
 * Daniel Huson, 9.2025
 */
public class RazorLocalPrunerOnGraph {

	/**
	 * Local neighbor-based pruning for an undirected graph.
	 * For each edge e=(s,t) with unlabeled endpoints:
	 * neighborSet := (neighbors(s) ∪ neighbors(t)) \ {s,t}
	 * localNodes = neighborNet plus any nodes (not s or t) that are adjacent to at least two different nodes in neighborSet
	 * For every unordered (u,v) ⊂ N:
	 * D_via_e(u,v) = sum of weights  on path u - s -t - v (or u - t - s -v, which ever applies)
	 * If in G \ {e} the shortest path distance(u,v) == D_via_e(u,v) (within EPS) for all pairs in neighborSet, using only
	 * localNodes and localEdges (edges between nodes in localNodes)
	 * then e is redundant and is removed.
	 *
	 * @param graph undirected graph
	 * @return number of removed edges
	 */
	public static int prune(PhyloGraph graph, Runnable checkGraph) {
		Function<Node, Boolean> isLabeled = graph::hasTaxa;
		ToDoubleFunction<Edge> getWeight = graph::getWeight;
		BiConsumer<Edge, Double> setWeight = graph::setWeight;

		System.err.println("LocalPruner Graph: " + graph.getNumberOfNodes() + " " + graph.getNumberOfEdges());


		// Collect candidate edges (both endpoints unlabeled)
		var candidates = new ArrayList<Edge>();
		for (var e : graph.edges()) {
			var s = e.getSource();
			var t = e.getTarget();
			if (!isLabeled.apply(s) && !isLabeled.apply(t)) {
				candidates.add(e);
			}
		}
		candidates.sort(Comparator.comparingDouble(e -> -getWeight.applyAsDouble(e)));

		System.err.println("candidates: " + candidates.size());

		var removed = 0;

		for (var e : candidates) {
			if (e.getOwner() != null) {
				var s = e.getSource();
				var t = e.getTarget();

				// neighborNodes = adj(s) ∪ adj(t) \ {s,t}
				var neighborNodes = new LinkedHashSet<Node>();
				for (var u : List.of(s, t)) {
					for (var f : u.adjacentEdges()) {
						if (f != e) {
							neighborNodes.add(f.getOpposite(u));
						}
					}
				}
				if (neighborNodes.size() < 2)
					continue;

				// localNodes = all neighborNodes plus additional nodes that connected to two
				// different nodes in neighborNodes
				var localNodes = new LinkedHashSet<>(neighborNodes);
				for (var u : neighborNodes) {
					for (var v : u.adjacentNodes()) {
						if (!neighborNodes.contains(v)) {
							for (var f : v.adjacentEdges()) {
								var w = f.getOpposite(v);
								if (w != u && neighborNodes.contains(w)) {
									localNodes.add(v);
								}
							}
						}
					}
				}

				var localEdges = new LinkedHashSet<Edge>();
				for (var u : localNodes) {
					for (var f : u.adjacentEdges()) {
						if (f != e && localNodes.contains(f.getOpposite(u))) {
							localEdges.add(f);
						}
					}
				}

				System.err.println("neighborNodes: " + neighborNodes.size() + " localNodes: " + localNodes.size() + " localEdges: " + localEdges.size());


				var allPairs = DijkstraAllPairs.allShortestPaths(neighborNodes, localNodes, localEdges, Edge::getOpposite, Node::adjacentEdges, graph::getWeight);
				var allSatisfied = true;

				var array = new ArrayList<>(neighborNodes);
				loop:
				for (var i = 0; i < array.size(); i++) {
					var v = array.get(i);
					var vc = (v.isAdjacent(e.getSource()) ? e.getSource() : e.getTarget());
					var ve = v.getCommonEdge(vc);
					for (var j = i + 1; j < array.size(); j++) {
						var w = array.get(j);
						var wc = (w.isAdjacent(e.getSource()) ? e.getSource() : e.getTarget());
						var we = w.getCommonEdge(wc);
						if (wc != vc) {
							var dist = getWeight.applyAsDouble(ve) + getWeight.applyAsDouble(e) + getWeight.applyAsDouble(we);
							var altDist = allPairs.get(v).get(w).distance();
							if (altDist > dist + EPS) {
								allSatisfied = false;
								break loop;
							}
						}
					}
				}

				if (allSatisfied) {
					System.err.println("PRUNED: neighborNodes: " + neighborNodes.size() + " localNodes: " + localNodes.size() + " localEdges: " + localEdges.size());

					var sourceAndTarget = e.nodes();
					graph.deleteEdge(e);
					removed++;
					for (var u : sourceAndTarget) {
						if (u.getDegree() == 1) {
							graph.deleteNode(u);
						} else if (u.getDegree() == 2) {
							var f1 = u.getFirstAdjacentEdge();
							var f2 = u.getLastAdjacentEdge();
							var d = getWeight.applyAsDouble(f1) + getWeight.applyAsDouble(f2);
							var f = graph.newEdge(f1.getOpposite(u), f2.getOpposite(u));
							setWeight.accept(f, d);
							graph.deleteNode(u);
						}
					}
					if (checkGraph != null)
						checkGraph.run();
				}
			}
		}
		return removed;
	}
}
