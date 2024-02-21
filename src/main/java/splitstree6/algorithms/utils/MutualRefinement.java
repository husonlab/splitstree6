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
import jloda.util.BitSetUtils;
import splitstree6.splits.TreesUtils;
import splitstree6.xtra.kernelize.ClusterIncompatibilityGraph;

import java.util.*;

import static splitstree6.algorithms.trees.trees2trees.RootedConsensusTree.isCompatibleWithAll;

/**
 * mutually refines a collection of trees and removes any topological duplicates
 * Daniel Huson, 2.2024
 */
public class MutualRefinement {
	public enum Strategy {
		All, Majority, Compatible
	}

	/**
	 * apply mutual refinement
	 *
	 * @param trees            input trees
	 * @param strategy         All: use all clusters, Majority, use clusters found in the majority of trees,
	 *                         compatible: refine only using clusters that are compatible will all trees
	 * @param removeDuplicates remove any duplicate trees after refining
	 * @return refined trees, with any duplicates removed
	 */
	public static List<PhyloTree> apply(Collection<PhyloTree> trees, Strategy strategy, boolean removeDuplicates) {
		var allClusters = switch (strategy) {
			case Compatible -> {
				var incompatibilityGraph = ClusterIncompatibilityGraph.apply(trees);
				yield incompatibilityGraph.nodeStream()
						.filter(v -> v.getDegree() == 0) // compatible with all
						.filter(v -> ((BitSet) v.getData()).cardinality() < trees.size()) // not in all trees
						.map(v -> (BitSet) v.getInfo()).toList();

			}
			case All -> extract(trees, 0);
			case Majority -> extract(trees, (int) Math.ceil(0.5 * trees.size()));
		};

		var result = new ArrayList<PhyloTree>();
		var seen = new HashSet<Set<BitSet>>();
		var idLabelMap = new HashMap<Integer, String>();
		{
			for (var tree : trees) {
				tree.nodeStream().filter(v -> tree.getLabel(v) != null && tree.hasTaxa(v)).forEach(v -> idLabelMap.put(tree.getTaxon(v), tree.getLabel(v)));
			}
		}
		for (var tree : trees) {
			var treeClusters = TreesUtils.collectAllHardwiredClusters(tree);

			var added = false;
			for (var cluster : allClusters) {
				if (!treeClusters.contains(cluster) && isCompatibleWithAll(cluster, treeClusters)) {
					treeClusters.add(cluster);
					added = true;
				}
			}
			if (!removeDuplicates || !seen.contains(treeClusters)) {
				if (!added) {
					result.add(new PhyloTree(tree));
				} else {
					var newtree = new PhyloTree();
					newtree.setName(tree.getName() + "-refined");

					for (var cluster : treeClusters) {
						for (var t : BitSetUtils.members(cluster)) {
							if (!idLabelMap.containsKey(t))
								System.err.println("Taxon with missing label: " + t);
						}
					}

					ClusterPoppingAlgorithm.apply(treeClusters, newtree);
					if (newtree.getRoot().getOutDegree() == 1) {
						var v = newtree.getRoot().getFirstOutEdge().getTarget();
						newtree.deleteNode(newtree.getRoot());
						newtree.setRoot(v);
					}
					newtree.nodeStream().filter(newtree::hasTaxa).forEach(v -> newtree.setLabel(v, idLabelMap.get(newtree.getTaxon(v))));
					result.add(newtree);
				}
				if (removeDuplicates)
					seen.add(treeClusters);
			}
		}
		return result;
	}

	private static List<BitSet> extract(Collection<PhyloTree> trees, int threshold) {
		var clusterCountMap = new HashMap<BitSet, Integer>();
		for (var tree : trees) {
			for (var cluster : TreesUtils.collectAllHardwiredClusters(tree)) {
				clusterCountMap.put(cluster, clusterCountMap.getOrDefault(cluster, 0) + 1);
			}
		}
		var list = new ArrayList<>(clusterCountMap.keySet().stream().
				filter(c -> clusterCountMap.get(c) < trees.size())
				.filter(c -> clusterCountMap.get(c) > threshold).toList());
		list.sort((a, b) -> -Integer.compare(clusterCountMap.get(a), clusterCountMap.get(b)));
		return list;
	}
}
