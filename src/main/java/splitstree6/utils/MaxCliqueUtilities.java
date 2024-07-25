/*
 *  MaxCliqueUtilities.java Copyright (C) 2024 Daniel H. Huson
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

package splitstree6.utils;

import jloda.graph.Edge;
import jloda.graph.Graph;
import jloda.graph.Node;
import jloda.util.CollectionUtils;
import jloda.util.IteratorUtils;
import jloda.util.StringUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * max-clique utilities
 * Daniel Huson, 7.2024
 */
public class MaxCliqueUtilities {
	/**
	 * greedily modifies the graph so that it only consists of disjoint max cliques
	 *
	 * @param graph the graph
	 */
	public static void greedilyReduceToDisjointMaxCliques(Graph graph) {
		var disjointMaxCliques = new ArrayList<Set<Node>>();
		var maxCliques = computeMaxCliques(graph);
		maxCliques.sort((a, b) -> -Integer.compare(a.size(), b.size()));
		var used = new HashSet<Node>();
		for (var clique : maxCliques) {
			if (clique.size() == 1)
				break;
			if (!CollectionUtils.intersects(used, clique)) {
				disjointMaxCliques.add(clique);
				used.addAll(clique);
			}
		}
		var keep = new HashSet<Edge>();
		for (var clique : disjointMaxCliques) {
			for (var a : clique) {
				for (var b : clique) {
					var e = a.getEdgeTo(b);
					if (e != null)
						keep.add(e);
				}
			}
		}
		for (var e : graph.edgeStream().filter(e -> !keep.contains(e)).toList()) {
			graph.deleteEdge(e);
		}
	}

	/**
	 * apply the Bron-Kerbosch algorithm to find all max cliques
	 *
	 * @param graph graph
	 * @return list of max cliques
	 */
	public static List<Set<Node>> computeMaxCliques(Graph graph) {
		var maxCliques = new ArrayList<Set<Node>>();
		var R = new HashSet<Node>();
		var P = IteratorUtils.asSet(graph.nodes());
		var X = new HashSet<Node>();
		computeMaxCliquesRec(R, P, X, maxCliques);

		if (false) {
			System.err.println("Number of max cliques: " + maxCliques.size());
			for (var clique : maxCliques) {
				System.err.println(StringUtils.toString(clique.stream().map(Node::getInfo).toList(), " "));
				for (var v : clique) {
					for (var w : clique) {
						if (v != w && !v.isAdjacent(w))
							System.err.println("Not a clique!");
					}
				}
			}
		}

		return maxCliques;
	}

	// recursively run the Bron-Kerbosch algorithm
	private static void computeMaxCliquesRec(Set<Node> R, Set<Node> P, Set<Node> X, List<Set<Node>> maxCliques) {
		if (P.isEmpty() && X.isEmpty()) {
			// Maximal clique found
			maxCliques.add(new HashSet<>(R));
			return;
		}

		var unionPX = CollectionUtils.union(P, X);
		var u = unionPX.iterator().next();

		var PMinusNeighbors = new HashSet<>(P);
		for (var v : P) {
			if (v.isAdjacent(u)) {
				PMinusNeighbors.remove(v);
			}
		}

		for (var v : PMinusNeighbors) {
			var Rv = new HashSet<>(R);
			Rv.add(v);

			var Pv = new HashSet<Node>();
			var Xv = new HashSet<Node>();

			for (var w : P) {
				if (v.isAdjacent(w)) {
					Pv.add(w);
				}
			}

			for (var w : X) {
				if (v.isAdjacent(w)) {
					Xv.add(w);
				}
			}

			computeMaxCliquesRec(Rv, Pv, Xv, maxCliques);

			P.remove(v);
			X.add(v);
		}
	}
}
