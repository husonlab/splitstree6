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
import splitstree6.utils.TreesUtils;
import splitstree6.xtra.kernelize.ClusterIncompatibilityGraph;

import java.util.*;

import static splitstree6.utils.ClusterUtils.isCompatibleWithAll;

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


		var globalClusterWeights = new HashMap<BitSet, Double>();
		{
			var clusterWeightsMap = new HashMap<BitSet, ArrayList<Double>>();
			for (var tree : trees) {
				var treeLength = tree.edgeStream().mapToDouble(tree::getWeight).sum();
				try (var nodeCluster = TreesUtils.extractClusters(tree)) {
					for (var e : tree.edges()) {
						clusterWeightsMap.computeIfAbsent(nodeCluster.get(e.getTarget()), k -> new ArrayList<>()).add(tree.getWeight(e) / treeLength);
					}
				}
				for (var cluster : clusterWeightsMap.keySet()) {
					globalClusterWeights.put(cluster, clusterWeightsMap.get(cluster).stream().mapToDouble(w -> w).average().orElse(0));
				}
			}
		}

		var result = new ArrayList<PhyloTree>();
		var seen = new HashSet<Set<BitSet>>();
		var idLabelMap = new HashMap<Integer, String>();

		for (var tree : trees) {
			tree.nodeStream().filter(v -> tree.getLabel(v) != null && tree.hasTaxa(v)).forEach(v -> idLabelMap.put(tree.getTaxon(v), tree.getLabel(v)));

			var taxa = BitSetUtils.asBitSet(tree.getTaxa());

			var treeClusters = new HashSet<BitSet>();
			var treeClusterWeights = new HashMap<BitSet, Double>();

			{
				var treeLength = tree.edgeStream().mapToDouble(tree::getWeight).sum();
				try (var nodeCluster = TreesUtils.extractClusters(tree)) {
					for (var e : tree.edges()) {
						treeClusterWeights.put(nodeCluster.get(e.getTarget()), tree.getWeight(e) / treeLength);
					}
					treeClusters.addAll(nodeCluster.values());
				}
			}


			for (var cluster : allClusters) {
				if (BitSetUtils.contains(taxa, cluster) && !treeClusters.contains(cluster) && isCompatibleWithAll(cluster, treeClusters)) {
					treeClusters.add(cluster);
				}
			}

			if (!removeDuplicates || !seen.contains(treeClusters)) {
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

				try (var nodeCluster = TreesUtils.extractClusters(newtree)) {
					for (var e : newtree.edges()) {
						var cluster = nodeCluster.get(e.getTarget());
						if (treeClusterWeights.containsKey(cluster)) {
							newtree.setWeight(e, treeClusterWeights.get(cluster));
						} else if (globalClusterWeights.containsKey(cluster)) {
							newtree.setWeight(e, globalClusterWeights.get(cluster));
						} else
							newtree.setWeight(e, 0);
					}
					treeClusters.addAll(nodeCluster.values());
				}


				newtree.nodeStream().filter(newtree::hasTaxa).forEach(v -> newtree.setLabel(v, idLabelMap.get(newtree.getTaxon(v))));
				result.add(newtree);
			}
			if (removeDuplicates)
				seen.add(treeClusters);
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
		list.sort((a, b) -> {
			var value = Integer.compare(clusterCountMap.get(a), clusterCountMap.get(b));
			if (value != 0)
				return -value; // more support comes first
			else
				return Integer.compare(a.cardinality(), b.cardinality()); // smaller comes first
		});
		return list;
	}
}
