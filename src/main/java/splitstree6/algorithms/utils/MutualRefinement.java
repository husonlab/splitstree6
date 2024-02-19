/*
 *  MutualRefinement.java Copyright (C) 2024 Daniel H. Huson
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

package splitstree6.algorithms.utils;

import jloda.phylo.PhyloTree;
import jloda.phylo.algorithms.ClusterPoppingAlgorithm;
import splitstree6.splits.TreesUtils;
import splitstree6.xtra.kernelize.ClusterIncompatibilityGraph;

import java.util.*;

/**
 * mutually refines a collection of trees and removes any topological duplicates
 * Daniel Huson, 2.2024
 */
public class MutualRefinement {
	/**
	 * apply mutual refinement
	 *
	 * @param trees input trees
	 * @return output trees
	 */
	public static Collection<PhyloTree> apply(Collection<PhyloTree> trees, boolean removeDuplicateTrees) {
		var incompatibilityGraph = ClusterIncompatibilityGraph.apply(trees);

		var compatibleClusters = incompatibilityGraph.nodeStream()
				.filter(v -> v.getDegree() == 0) // compatible with all
				.filter(v -> ((BitSet) v.getData()).cardinality() < trees.size()) // not in all trees
				.map(v -> (BitSet) v.getInfo()).toList();

		if (compatibleClusters.isEmpty())
			return trees;
		else {
			var result = new ArrayList<PhyloTree>();
			var seen = new HashSet<Set<BitSet>>();
			var idLabelMap = new HashMap<Integer, String>();
			for (var tree : trees) {
				tree.nodeStream().filter(v -> tree.getLabel(v) != null && tree.hasTaxa(v)).forEach(v -> idLabelMap.put(tree.getTaxon(v), tree.getLabel(v)));
				var clusters = TreesUtils.collectAllHardwiredClusters(tree);
				if (!clusters.containsAll(compatibleClusters)) {
					clusters.addAll(compatibleClusters);
					var newTree = new PhyloTree();
					ClusterPoppingAlgorithm.apply(clusters, newTree);
					if (newTree.getRoot().getOutDegree() == 1) {
						var v = newTree.getRoot().getFirstOutEdge().getTarget();
						newTree.deleteNode(newTree.getRoot());
						newTree.setRoot(v);
					}

					newTree.nodeStream().filter(newTree::hasTaxa).forEach(v -> newTree.setLabel(v, idLabelMap.get(newTree.getTaxon(v))));
					if (!removeDuplicateTrees || !seen.contains(clusters)) {
						result.add(newTree);
						if (removeDuplicateTrees)
							seen.add(clusters);
					}
				} else if (!removeDuplicateTrees || !seen.contains(clusters)) {
					result.add(tree);
					if (removeDuplicateTrees)
						seen.add(clusters);
				}
			}
			return result;
		}
	}
}
