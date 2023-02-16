/*
 *  ComputeEvolutionaryDistinctiveness.java Copyright (C) 2023 Daniel H. Huson
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

package splitstree6.analysis;

import jloda.graph.NodeDoubleArray;
import jloda.phylo.PhyloTree;
import splitstree6.algorithms.utils.TreesUtilities;
import splitstree6.data.TaxaBlock;

import java.util.ArrayList;

/**
 * computes the fare proportion diversity value for each node in a rooted network
 * Daniel Huson, 2.2023
 */
public class ComputeEvolutionaryDistinctiveness {
	public static NodeDoubleArray apply(PhyloTree tree) {
		try (var node2clusters = TreesUtilities.extractClusters(tree)) {
			var fairProportion = tree.newNodeDoubleArray();
			fairProportion.put(tree.getRoot(), 0.0);
			tree.preorderTraversal(tree.getRoot(), v -> v.parentsStream(false).allMatch(fairProportion::containsKey),
					v -> {
						var weight = 0.0;
						if (v.getInDegree() > 0) {
							var numberBelow = node2clusters.get(v).cardinality();
							for (var e : v.inEdges()) {
								weight += fairProportion.get(e.getSource()) + (tree.getWeight(e) / numberBelow);
							}
						}
						fairProportion.put(v, weight);
					});

			// remove values for unlabeled trees
			var total = tree.edgeStream().mapToDouble(e -> Math.max(0, tree.getWeight(e))).sum();
			for (var v : tree.nodes()) {
				if (tree.getNumberOfTaxa(v) == 0) {
					fairProportion.remove(v);
				} else {
					fairProportion.put(v, fairProportion.get(v) / (tree.getNumberOfTaxa(v) * total));
				}
			}
			if (true) {
				var sum = fairProportion.values().stream().mapToDouble(d -> d).sum();
				if (Math.abs(sum - 1.0) > 0.01)
					System.err.println("Evolutionary distinctiveness calculation wrong? sum=" + sum);
			}
			return fairProportion;
		}
	}

	public static String report(TaxaBlock taxaBlock, PhyloTree tree, boolean sorted) {
		try (var map = apply(tree)) {
			var entries = new ArrayList<>(map.entrySet());
			if (sorted)
				entries.sort((a, b) -> Double.compare(b.getValue(), a.getValue())); // by decreasing value
			var buf = new StringBuilder("Evolutionary distinctiveness" + (tree.getName() != null ? " for '" + tree.getName() + "'" : "") + ":\n");
			for (var entry : entries) {
				for (var t : tree.getTaxa(entry.getKey())) {
					buf.append(String.format("%s: %.2f%%%n", taxaBlock.get(t).getName(), 100 * entry.getValue()));
				}
			}
			return buf.toString();
		}
	}
}
