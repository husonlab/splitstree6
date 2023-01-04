/*
 *  RerootAndRescaleTrees.java Copyright (C) 2023 Daniel H. Huson
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

package splitstree6.view.trees.densitree;

import jloda.fx.window.NotificationManager;
import jloda.graph.Node;
import jloda.graph.NodeArray;
import jloda.phylo.PhyloTree;
import jloda.util.BitSetUtils;
import jloda.util.IteratorUtils;
import jloda.util.Single;
import jloda.util.progress.ProgressSilent;
import splitstree6.algorithms.utils.GreedyCompatible;
import splitstree6.algorithms.utils.RerootingUtils;
import splitstree6.algorithms.utils.TreesUtilities;
import splitstree6.data.TaxaBlock;
import splitstree6.data.parts.ASplit;

import java.io.IOException;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

/**
 * reroot and rescale trees
 * Daniel Huson, 1.2023
 * todo: this is under construction
 */
public class RerootAndRescaleTrees {

	/**
	 * reroot and rescale a set of trees so that they fit nicely into a densi-tree visualization
	 * this is useful for gene trees and also allows partial trees to be present
	 *
	 * @param trees the input trees
	 * @return rescaled and rerooted trees
	 * @throws IOException
	 */
	public static List<PhyloTree> apply(TaxaBlock taxaBlock, List<PhyloTree> trees) {
		try {
			var allTaxa = taxaBlock.getTaxaSet();
			var nTax = allTaxa.cardinality();

			var fullTrees = trees.stream().filter(tree -> IteratorUtils.size(tree.getTaxa()) == allTaxa.cardinality())
					.map(PhyloTree::new).collect(Collectors.toCollection(ArrayList::new));

			if (fullTrees.size() < 0.1 * trees.size())
				throw new IOException("Reroot and Rescale not applicable: at least 10% of all trees must have all taxa");

			// extract all splits and their weights:
			var splitPartWeightMap = new HashMap<BitSet, ArrayList<Double>>();
			for (var tree : fullTrees) {
				var splits = new ArrayList<ASplit>();
				TreesUtilities.computeSplits(allTaxa, tree, splits);
				for (var split : splits) {
					var weights = splitPartWeightMap.computeIfAbsent(split.getSmallerPart(), k -> new ArrayList<>());
					weights.add(split.getWeight());
				}
			}

			// compute the greedy consensus tree:

			var splits = new ArrayList<ASplit>();
			for (var entry : splitPartWeightMap.entrySet()) {
				var a = entry.getKey();
				var b = BitSetUtils.minus(allTaxa, a);
				var weight = entry.getValue().stream().mapToDouble(d -> d).average().orElse(0);
				splits.add(new ASplit(a, b, weight));
			}
			var consensusSplits = GreedyCompatible.apply(new ProgressSilent(), splits, s -> (double) splitPartWeightMap.get(s.getSmallerPart()).size());
			var consensusTree = TreesUtilities.computeTreeFromCompatibleSplits(taxaBlock, consensusSplits);

			rescale(consensusTree);
			RerootingUtils.rerootByMidpoint(consensusTree);

			var maxDistanceToLeaves = getMaxDistanceToLeaves(consensusTree);
			if (maxDistanceToLeaves > 0) {
				for (var e : consensusTree.edges()) {
					consensusTree.setWeight(e, 100 * consensusTree.getWeight(e) / maxDistanceToLeaves);
				}
			}

			System.err.printf("Consensus tree total length: %.5f%n", getTotalPairwiseDistances(allTaxa, consensusTree));
			System.err.printf("Consensus average leaf dist: %.5f%n", getAverageDistanceToLeaves(consensusTree));
			System.err.printf("Consensus max leaf dist: %.5f%n", getMaxDistanceToLeaves(consensusTree));

			if (false) {
				for (var v : consensusTree.nodes()) {
					if (v.isLeaf()) {
						consensusTree.setLabel(v, taxaBlock.get(consensusTree.getTaxa(v).iterator().next()).getName());
					}
				}
				System.err.println(consensusTree.toBracketString(true) + ";");
			}

			BitSet outGroup = null;

			try (NodeArray<BitSet> taxaBelow = consensusTree.newNodeArray()) {
				consensusTree.postorderTraversal(v -> {
					var taxa = new BitSet();
					taxa.or(BitSetUtils.asBitSet(consensusTree.getTaxa(v)));
					for (var u : v.children()) {
						taxa.or(taxaBelow.get(u));
					}
					taxaBelow.put(v, taxa);
				});
				for (var v : consensusTree.getRoot().children()) {
					var taxa = taxaBelow.get(v);
					if (outGroup == null || taxa.cardinality() < outGroup.cardinality()) {
						outGroup = taxa;
					}
				}
			}

			if (true) {
				var reduce = new ArrayList<PhyloTree>();
				for (var i = 0; i < 4; i++)
					reduce.add(fullTrees.get(i));
				fullTrees.clear();
				fullTrees.addAll(reduce);
			}


			if (outGroup != null) {
				for (var tree : fullTrees) {
					RerootingUtils.rerootByOutgroup(tree, outGroup);
				}
			}

			for (var tree : fullTrees) {
				var maxDistanceToLeaves1 = getMaxDistanceToLeaves(tree);
				if (maxDistanceToLeaves1 > 0) {
					for (var e : tree.edges()) {
						tree.setWeight(e, 100 * tree.getWeight(e) / maxDistanceToLeaves1);
					}
				}
				System.err.printf(tree.getName() + " total length: %.5f%n", getTotalPairwiseDistances(allTaxa, tree));
				System.err.printf(tree.getName() + " average leaf dist: %.5f%n", getAverageDistanceToLeaves(tree));
				System.err.printf(tree.getName() + " max leaf dist: %.5f%n", getMaxDistanceToLeaves(tree));
			}


			if (true) { // topology scaling:
				for (var tree : fullTrees) {
					try (var x = tree.newNodeDoubleArray()) {
						tree.postorderTraversal(v -> {
							if (v.isLeaf())
								x.put(v, 0.0);
							else {
								x.put(v, v.childrenStream().mapToDouble(x::get).min().orElse(0) - 1.0);
							}
						});
						var height = -x.get(tree.getRoot());
						for (var e : tree.edges()) {
							tree.setWeight(e, (x.get(e.getTarget()) - x.get(e.getSource())) / height);
						}
					}
				}
			}

			// now process partial trees, if any:
			var partialTrees = trees.stream().filter(tree -> IteratorUtils.size(tree.getTaxa()) < nTax)
					.map(PhyloTree::new).collect(Collectors.toCollection(ArrayList::new));

			if (false && partialTrees.size() > 0) {

				for (var tree : partialTrees) {
					rescale(tree);
					var taxa = BitSetUtils.asBitSet(tree.getTaxa());
					// rescale tree so total pairwise distances equals those found in consensus tree
					var total = getTotalPairwiseDistances(consensusSplits, taxa);
					if (total > 0) {
						for (var e : tree.edges()) {
							tree.setWeight(e, tree.getWeight(e) / total);
						}
					}
					var partialOutGroup = BitSetUtils.intersection(outGroup, allTaxa);
					if (partialOutGroup.cardinality() > 0) {
						RerootingUtils.rerootByOutgroup(tree, partialOutGroup);
					} else
						RerootingUtils.rerootByMidpoint(tree);
				}
				fullTrees.addAll(partialTrees);
			}
			return fullTrees;
		} catch (IOException ex) {
			NotificationManager.showWarning(ex.getMessage());
			return trees;
		}
	}

	/**
	 * rescale a tree so that the sum of distances over all pairs of taxa equals 1
	 *
	 * @param tree input tree
	 */
	private static void rescale(PhyloTree tree) {
		double totalLength = 0;
		try (NodeArray<WeightedCluster> clustersBelowMap = tree.newNodeArray()) {
			tree.postorderTraversal(v -> {
				var taxa = new BitSet();
				if (tree.getNumberOfTaxa(v) > 0)
					taxa.or(BitSetUtils.asBitSet(tree.getTaxa(v)));
				for (var u : v.children()) {
					taxa.or(clustersBelowMap.get(u).taxa());
				}
				var weight = (v.getInDegree() == 1 ? tree.getWeight(v.getFirstInEdge()) : 0);
				clustersBelowMap.put(v, new WeightedCluster(taxa, weight));
			});
			var clusters = new ArrayList<>(clustersBelowMap.values());
			var taxa = BitSetUtils.union(clusters.stream().map(WeightedCluster::taxa).collect(Collectors.toList()));
			for (var a : BitSetUtils.members(taxa)) {
				for (var b : BitSetUtils.members(taxa, a + 1)) {
					for (var cluster : clusters) {
						if (cluster.taxa().get(a) != cluster.taxa().get(b)) {
							totalLength += cluster.weight();
						}
					}
				}
			}
		}
		if (totalLength > 0) {
			for (var e : tree.edges()) {
				tree.setWeight(e, tree.getWeight(e) / totalLength);
			}
		}
	}

	private static double getTotalPairwiseDistances(ArrayList<ASplit> splits, BitSet taxa) {
		var total = 0.0;
		for (var split : splits) {
			total += BitSetUtils.intersection(taxa, split.getA()).cardinality() * BitSetUtils.intersection(taxa, split.getB()).cardinality() * split.getWeight();
		}
		return total;
	}

	private static double getTotalPairwiseDistances(BitSet allTaxa, PhyloTree tree) {
		var totalWeight = new Single<>(0.0);
		try (NodeArray<BitSet> taxaBelow = tree.newNodeArray()) {
			tree.postorderTraversal(v -> {
				var taxa = new BitSet();
				taxa.or(BitSetUtils.asBitSet(tree.getTaxa(v)));
				for (var u : v.children()) {
					taxa.or(taxaBelow.get(u));
				}
				taxaBelow.put(v, taxa);
				if (v.getInDegree() == 1) {
					totalWeight.set(totalWeight.get() + tree.getWeight(v.getFirstInEdge()) * taxa.cardinality() * (allTaxa.cardinality() - taxa.cardinality()));
				}
			});
		}
		return totalWeight.get();
	}

	private static double getAverageDistanceToLeaves(PhyloTree tree) {
		try (var distanceFromRoot = tree.newNodeDoubleArray()) {
			tree.preorderTraversal(v -> {
				var distance = (v.getInDegree() == 1 ? distanceFromRoot.get(v.getFirstInEdge().getSource()) + tree.getWeight(v.getFirstInEdge()) : 0.0);
				distanceFromRoot.put(v, distance);
			});
			return tree.nodeStream().filter(Node::isLeaf).mapToDouble(distanceFromRoot::get).average().orElse(0.0);
		}

	}

	private static double getMaxDistanceToLeaves(PhyloTree tree) {
		try (var distanceFromRoot = tree.newNodeDoubleArray()) {
			tree.preorderTraversal(v -> {
				var distance = (v.getInDegree() == 1 ? distanceFromRoot.get(v.getFirstInEdge().getSource()) + tree.getWeight(v.getFirstInEdge()) : 0.0);
				distanceFromRoot.put(v, distance);
			});
			return tree.nodeStream().filter(Node::isLeaf).mapToDouble(distanceFromRoot::get).max().orElse(0.0);
		}

	}

	private static record WeightedCluster(BitSet taxa, double weight) {
	}
}
