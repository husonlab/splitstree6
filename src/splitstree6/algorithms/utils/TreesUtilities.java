/*
 * TreesUtilities.java Copyright (C) 2023 Daniel H. Huson
 *
 * (Some files contain contributions from other authors, who are then mentioned separately.)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package splitstree6.algorithms.utils;

import jloda.graph.Node;
import jloda.graph.NodeArray;
import jloda.phylo.PhyloTree;
import jloda.util.BitSetUtils;
import jloda.util.NumberUtils;
import splitstree6.data.DistancesBlock;
import splitstree6.data.TaxaBlock;
import splitstree6.data.TreesBlock;
import splitstree6.data.parts.ASplit;
import splitstree6.data.parts.BiPartition;
import splitstree6.data.parts.Compatibility;

import java.util.*;
import java.util.function.Function;

/**
 * some computations on trees
 *
 * @author huson Date: 29-Feb-2004
 * Daria Evseeva,23.01.2017.
 */
public class TreesUtilities {
	/**
	 * gets all taxa in tree, if node to taxa mapping has been set
	 *
	 * @return all taxa in tree
	 */
	public static BitSet getTaxa(PhyloTree tree) {
		final var taxa = new BitSet();
		for (var v : tree.nodes()) {
			for (var t : tree.getTaxa(v)) {
				taxa.set(t);
			}
		}
		return taxa;
	}

	/**
	 * determines whether every pair of taxa occur together in some tree
	 *
	 * @return returns true, if every pair of taxa occur together in some  tree
	 */
	static public boolean hasAllPairs(TaxaBlock taxa, TreesBlock trees) {
		var numPairs = (taxa.getNtax() * (taxa.getNtax() - 1)) / 2;

		var seen = new BitSet();

		for (var which = 1; which <= trees.getNTrees(); which++) {
			BitSet support = //trees.getSupport(taxa, which).getBits();
					//---
					new BitSet();
			var tree = trees.getTrees().get(which);
			for (var v : tree.nodes()) {
				var label = v.getLabel();
				if (label != null)
					support.set(taxa.indexOf(label)); //todo test???
			}
			//---
			for (var i = support.nextSetBit(1); i > 0; i = support.nextSetBit(i + 1)) {
				for (var j = support.nextSetBit(i + 1); j > 0; j = support.nextSetBit(j + 1)) {
					seen.set(i + taxa.getNtax() * j, true);
					if (seen.cardinality() == numPairs)
						return true; // seen all possible pairs
				}
			}
		}
		return false;
	}


	/**
	 * are there any labeled internal nodes and are all such labels numbers?
	 *
	 * @return true, if some internal nodes labeled and all labeled by numbers
	 */
	public static boolean hasNumbersOnInternalNodes(PhyloTree tree) {
		var hasNumbers = false;
		for (var v = tree.getFirstNode(); v != null; v = v.getNext()) {
			if (v.getOutDegree() != 0 && v.getInDegree() != 0) {
				var label = tree.getLabel(v);
				if (label != null) {
					if (NumberUtils.isDouble(label))
						hasNumbers = true;
					else
						return false;
				}
			}
		}
		return hasNumbers;
	}


	/**
	 * reinterpret an numerical label of an internal node as the confidence associated with the incoming edge
	 */
	public static void changeNumbersOnInternalNodesToEdgeConfidences(PhyloTree tree) {
		for (var v = tree.getFirstNode(); v != null; v = v.getNext()) {
			if (v.getOutDegree() != 0 && v.getInDegree() == 1) {
				var label = tree.getLabel(v);
				if (label != null) {
					if (NumberUtils.isDouble(label)) {
						tree.setConfidence(v.getFirstInEdge(), NumberUtils.parseDouble(label));
						tree.setLabel(v, null);
					}
				}
			}
		}
	}

	/**
	 * are there any labeled leaf nodes and are all such labels numbers?
	 *
	 * @return true, if some leaves nodes labeled by numbers
	 */
	public static boolean hasNumbersOnLeafNodes(PhyloTree tree) {
		var hasNumbers = false;
		for (var v = tree.getFirstNode(); v != null; v = v.getNext()) {
			if (v.getOutDegree() == 0) {
				final var label = tree.getLabel(v);
				if (label != null) {
					if (NumberUtils.isDouble(label))
						hasNumbers = true;
					else
						return false;
				}
			}
		}
		return hasNumbers;
	}

	/**
	 * compute all the splits in a tree
	 *
	 * @return bit set of taxa found in tree
	 */
	public static BitSet computeSplits(BitSet taxaInTree, final PhyloTree tree, final Collection<ASplit> splits) {
		if (taxaInTree == null)
			taxaInTree = getTaxa(tree);

		if (tree.getRoot() == null)
			throw new RuntimeException("Tree is empty or no root");
		else {
			var biPartitionSplitMap = new HashMap<BiPartition, ASplit>();
			for (var entry : extractClusters(tree).entrySet()) {
				var v = entry.getKey();
				var cluster = entry.getValue();
				if (v != tree.getRoot()) {
					var complement = BitSetUtils.minus(taxaInTree, cluster);
					if (cluster.cardinality() > 0 && complement.cardinality() > 0) {
						var e = v.getFirstInEdge();
						var weight = tree.getWeight(e);
						var biPartition = new BiPartition(cluster, complement);
						var split = biPartitionSplitMap.computeIfAbsent(biPartition, k -> new ASplit(cluster, complement, 0));
						split.setWeight(split.getWeight() + weight); // this ensures that complementary clusters get mapped to same split
						if (tree.hasEdgeConfidences()) {
							split.setConfidence(tree.getConfidence(e));
						}
					}
				}
			}
			splits.clear();
			splits.addAll(biPartitionSplitMap.values());
		}
		return taxaInTree;
	}

	/**
	 * computes the induced tree
	 */
	public static PhyloTree computeInducedTree(int[] oldTaxonId2NewTaxonId, PhyloTree originalTree) {
		final var inducedTree = new PhyloTree();
		inducedTree.copy(originalTree);
		inducedTree.getLSAChildrenMap().clear();

		final var toRemove = new LinkedList<Node>(); // initially, set to all leaves that have lost their taxa

		// change taxa:
		for (var v : inducedTree.nodes()) {
			if (inducedTree.getNumberOfTaxa(v) > 0) {
				var taxa = new BitSet();
				for (var t : inducedTree.getTaxa(v)) {
					if (oldTaxonId2NewTaxonId[t] > 0)
						taxa.set(oldTaxonId2NewTaxonId[t]);
				}
				inducedTree.clearTaxa(v);
				if (taxa.cardinality() == 0)
					toRemove.add(v);
				else {
					for (var t : BitSetUtils.members(taxa)) {
						inducedTree.addTaxon(v, t);
					}
				}
			}
		}

		// delete all nodes that don't belong to the induced tree
		while (toRemove.size() > 0) {
			final var v = toRemove.remove(0);
			for (var e : v.inEdges()) {
				final var w = e.getSource();
				if (w.getOutDegree() == 1 && inducedTree.getNumberOfTaxa(w) == 0) {
					toRemove.add(w);
				}
			}
			if (inducedTree.getRoot() == v) {
				inducedTree.deleteNode(v);
				inducedTree.setRoot(null);
				return null; // tree has completely disappeared...
			}
			inducedTree.deleteNode(v);
		}

		// remove path from original root to new root:

		var root = inducedTree.getRoot();
		while (inducedTree.getNumberOfTaxa(root) == 0 && root.getOutDegree() == 1) {
			root = root.getFirstOutEdge().getTarget();
			inducedTree.deleteNode(inducedTree.getRoot());
			inducedTree.setRoot(root);
		}

		// remove all divertices
		final var diVertices = new LinkedList<Node>();
		for (var v : inducedTree.nodes()) {
			if (v.getInDegree() == 1 && v.getOutDegree() == 1)
				diVertices.add(v);
		}
		for (var v : diVertices) {
			inducedTree.delDivertex(v);
		}

		return inducedTree;
	}

	public static DistancesBlock computeDistances(PhyloTree tree, DistancesBlock distances, boolean useWeights) {
		if (distances == null)
			distances = new DistancesBlock();
		var splits = new ArrayList<ASplit>();
		var taxa = BitSetUtils.asBitSet(tree.getTaxa());
		computeSplits(taxa, tree, splits);
		distances.setNtax(BitSetUtils.max(taxa));
		for (var split : splits) {
			for (var i : BitSetUtils.members(split.getA())) {
				for (var j : BitSetUtils.members(split.getB())) {
					var dist = distances.get(i, j) + (useWeights ? split.getWeight() : 1.0);
					distances.set(i, j, dist);
					distances.set(j, i, dist);
				}
			}
		}
		return distances;
	}

	public static PhyloTree computeTreeFromCompatibleSplits(Function<Integer, String> taxonLabel, List<ASplit> splits) {
		if (splits.size() == 0)
			return new PhyloTree();

		if (!Compatibility.isCompatible(splits))
			throw new RuntimeException("Internal error: Splits are not compatible");
		final var clusterWeightConfidenceMap = new HashMap<BitSet, WeightConfidence>();
		for (var split : splits) {
			clusterWeightConfidenceMap.put(split.getPartNotContaining(1), new WeightConfidence(split.getWeight(), split.getConfidence()));
		}

		final BitSet[] clusters;
		{
			clusters = new BitSet[splits.size()];
			var i = 0;
			for (var split : splits) {
				clusters[i++] = split.getPartNotContaining(1);

			}
		}
		Arrays.sort(clusters, (a, b) -> Integer.compare(b.cardinality(), a.cardinality()));

		final var allTaxa = splits.get(0).getAllTaxa();
		var tree = new PhyloTree();

		tree.setRoot(tree.newNode());

		try (NodeArray<BitSet> node2taxa = tree.newNodeArray()) {
			node2taxa.put(tree.getRoot(), allTaxa);
			// create tree:
			for (var cluster : clusters) {
				var v = tree.getRoot();
				while (BitSetUtils.contains(node2taxa.get(v), cluster)) {
					var isBelow = false;
					for (var e : v.outEdges()) {
						final var w = e.getTarget();
						if (BitSetUtils.contains(node2taxa.get(w), cluster)) {
							v = w;
							isBelow = true;
							break;
						}
					}
					if (!isBelow)
						break;
				}
				final var u = tree.newNode();
				final var f = tree.newEdge(v, u);
				var weightConfidence = clusterWeightConfidenceMap.get(cluster);
				tree.setWeight(f, weightConfidence.weight());
				if (weightConfidence.confidence() != -1)
					tree.setConfidence(f, weightConfidence.confidence());
				node2taxa.put(u, cluster);
			}

			// add all labels:

			for (var t : BitSetUtils.members(allTaxa)) {
				var v = tree.getRoot();
				while (node2taxa.get(v).get(t)) {
					var isBelow = false;
					for (var e : v.outEdges()) {
						final var w = e.getTarget();
						if (node2taxa.get(w).get(t)) {
							v = w;
							isBelow = true;
							break;
						}
					}
					if (!isBelow)
						break;
				}
				tree.addTaxon(v, t);
			}
		}
		PhyloGraphUtils.addLabels(taxonLabel, tree);

		// todo: ask about internal node labels
		RerootingUtils.rerootByMidpoint(tree);
		return tree;
	}

	/**
	 * computes a mapping of tree nodes to represented hardwired cluster. Does not contain the cluster at the root
	 *
	 * @param tree input tree, may contain reticulations
	 * @return mapping of tree nodes to corresponding hardwired clusters
	 */
	public static NodeArray<BitSet> extractClusters(PhyloTree tree) {
		NodeArray<BitSet> nodeClusterMap = tree.newNodeArray();
		var stack = new Stack<Node>();
		stack.push(tree.getRoot());
		while (stack.size() > 0) {
			var v = stack.peek();
			if (nodeClusterMap.containsKey(v))
				stack.pop();
			else {
				var hasUnprocessedChild = false;
				for (var w : v.children()) {
					if (!nodeClusterMap.containsKey(w)) {
						stack.push(w);
						hasUnprocessedChild = true;
					}
				}
				if (!hasUnprocessedChild) {
					stack.pop();
					var cluster = BitSetUtils.asBitSet(tree.getTaxa(v));
					for (var w : v.children()) {
						cluster.or(nodeClusterMap.get(w));
					}
					nodeClusterMap.put(v, cluster);
				}
			}
		}
		tree.nodeStream().filter(v -> v.getInDegree() != 1).forEach(nodeClusterMap::remove);
		return nodeClusterMap;
	}

	private static record WeightConfidence(double weight, double confidence) {
	}

}
