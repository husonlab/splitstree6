/*
 *  GraphUtils.java Copyright (C) 2024 Daniel H. Huson
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

package splitstree6.splits;

import jloda.graph.Graph;
import jloda.graph.Node;
import jloda.phylo.PhyloGraph;
import jloda.util.IteratorUtils;
import jloda.util.Pair;

import java.util.ArrayList;
import java.util.function.Function;

/**
 * some graph utilities
 * Daniel Huson, 2.2019
 */
public class GraphUtils {
	/**
	 * add labels to graph
	 */
	public static void addLabels(Function<Integer, String> taxonLabel, PhyloGraph graph) {
		// remove labels for taxon nodes, in case some algorithm has already been applied
		for (int t = 1; t <= graph.getNumberOfTaxa(); t++) {
			final Node v = graph.getTaxon2Node(t);
			graph.setLabel(v, null);
		}

		for (int t = 1; t <= graph.getNumberOfTaxa(); t++) {
			final Node v = graph.getTaxon2Node(t);
			if (graph.getLabel(v) == null)
				graph.setLabel(v, taxonLabel.apply(t));
			else
				graph.setLabel(v, graph.getLabel(v) + ", " + taxonLabel.apply(t));
		}
	}

	/**
	 * convert the graph into its complement
	 *
	 * @param graph the graph
	 */
	public static void convertToComplement(Graph graph) {
		var newPairs = new ArrayList<Pair<Node, Node>>();
		var nodes = IteratorUtils.asList(graph.nodes());
		for (var i = 0; i < nodes.size(); i++) {
			var v = nodes.get(i);
			for (var j = i + 1; j < nodes.size(); j++) {
				var w = nodes.get(j);
				if (!v.isAdjacent(w))
					newPairs.add(new Pair<>(v, w));
			}
		}
		graph.deleteAllEdges();
		for (var pair : newPairs) {
			graph.newEdge(pair.getFirst(), pair.getSecond());
		}
	}
}
