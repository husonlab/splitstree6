/*
 *  AltsNonBinary.java Copyright (C) 2024 Daniel H. Huson
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

package splitstree6.xtra.kernelize;

import jloda.graph.Graph;
import jloda.graph.Node;
import jloda.graph.NodeArray;
import jloda.phylo.NewickIO;
import jloda.phylo.PhyloTree;
import jloda.phylo.algorithms.ClusterPoppingAlgorithm;
import jloda.util.Basic;
import jloda.util.BitSetUtils;
import jloda.util.IteratorUtils;
import jloda.util.StringUtils;
import jloda.util.progress.ProgressListener;
import splitstree6.autumn.Cluster;
import splitstree6.data.TaxaBlock;
import splitstree6.splits.TreesUtils;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Kernelization of multi-furcating rooted-tree reconciliation algorithms
 * Daniel Huson, 10.23
 */
public class Kernelize {
	/**
	 * run the given algorithm on each minimal subproblem
	 *
	 * @param progress           progress listener
	 * @param taxaBlock          taxa
	 * @param inputTrees         input trees
	 * @param algorithm          the algorithm
	 * @param maxNumberOfResults maximum number of solutions to report
	 * @return computed networks
	 */
	public static List<PhyloTree> apply(ProgressListener progress, TaxaBlock taxaBlock, Collection<PhyloTree> inputTrees,
										BiFunctionWithIOException<Collection<PhyloTree>, ProgressListener, Collection<PhyloTree>> algorithm,
										int maxNumberOfResults, boolean mutualRefinement) throws IOException {

		// setup incompatibility graph
		var incompatibilityGraph = computeClusterIncompatibilityGraph(inputTrees);
		System.err.println("Compatible clusters:   " + incompatibilityGraph.nodeStream().filter(v -> v.getDegree() == 0).count());
		System.err.println("Incompatible clusters: " + incompatibilityGraph.nodeStream().filter(v -> v.getDegree() > 0).count());

		// extract all incompatibility components
		var components = incompatibilityGraph.extractAllConnectedComponents();
		System.err.println("Components:     " + components.stream().filter(c -> c.getNumberOfNodes() > 1).count());
		System.err.println();

		// compute the blob-tree:
		var blobTree = new PhyloTree();
		var clusterNodeMap = new HashMap<BitSet, Node>();
		{
			var clusters = new HashSet<BitSet>();
			for (var component : components) {
				if (component.getNumberOfNodes() == 1)
					clusters.add((BitSet) component.getFirstNode().getInfo());
				else {
					clusters.add(BitSetUtils.union(component.nodeStream().map(v -> (BitSet) v.getInfo()).toList()));
				}
			}
			ClusterPoppingAlgorithm.apply(clusters, blobTree);
			blobTree.nodeStream().filter(v -> blobTree.getNumberOfTaxa(v) > 0)
					.forEach(v -> blobTree.setLabel(v, taxaBlock.getLabel(blobTree.getTaxon(v))));
			try (var nodeClusterMap = TreesUtils.extractClusters(blobTree)) {
				for (var v : nodeClusterMap.keySet()) {
					clusterNodeMap.put(nodeClusterMap.get(v), v);
				}
			}
		}

		if (true) { // report incompatibility components and the associated reduced trees:
			var count = 0;
			for (var component : components) {
				if (component.getNumberOfNodes() > 1) {
					System.err.println("Component " + (++count) + ": " + component.getNumberOfNodes() + " clusters");
					var reducedTrees = extractTrees(component);
					System.err.println(reducedTrees.report());
				}
			}
		}

		System.err.println("Blob tree: " + NewickIO.toString(blobTree, false) + ";");

		// run the algorithm on all components:
		try (NodeArray<TreesAndTaxonClasses> blobNetworksMap = blobTree.newNodeArray()) {
			for (var component : components) {
				if (component.getNumberOfNodes() > 1) {
					var reducedTrees = extractTrees(component);
					if (mutualRefinement) {
						mutualRefinement(reducedTrees.trees());
					}

					System.err.println("Input trees:");
					for (var tree : reducedTrees.trees()) {
						System.err.println(NewickIO.toString(tree, false) + ";");
					}

					var networks = algorithm.apply(reducedTrees.trees(), progress);
					if (true) { // todo: algorithm doesn't return taxon ids
						System.err.println("Output networks:");
						for (var tree : networks) {
							System.err.println(NewickIO.toString(tree, false) + ";");
						}
						if (IteratorUtils.size(networks.iterator().next().getTaxa()) == 0) {
							var labelTaxonMap = new HashMap<String, Integer>();
							for (var tree : inputTrees) {
								for (var v : tree.nodes()) {
									var label = tree.getLabel(v);
									if (label != null)
										labelTaxonMap.put(label, tree.getTaxon(v));
								}
							}
							for (var network : networks) {
								for (var v : network.nodes()) {
									var label = network.getLabel(v);
									if (label != null) {
										var taxonId = labelTaxonMap.get(label);
										network.addTaxon(v, taxonId);
									}
								}
							}
						}
					}
					var subnetworks = new TreesAndTaxonClasses(networks, reducedTrees.taxonClasses());
					var donorTaxa = BitSetUtils.union(reducedTrees.taxonClasses());
					var acceptorNode = clusterNodeMap.get(donorTaxa);
					blobNetworksMap.put(acceptorNode, subnetworks);
				}
			}
			var networks = new ArrayList<PhyloTree>();
			var numberOfBlobs = blobNetworksMap.size();
			try {
				insertRec(blobTree, clusterNodeMap, blobNetworksMap, numberOfBlobs, new HashSet<>(), maxNumberOfResults, networks);
			} catch (Exception ex) {
				Basic.caught(ex);
				throw new IOException(ex);
			}
			for (var network : networks) {
				for (var v : network.nodeStream().filter(v -> v.getInDegree() == 1 && v.getOutDegree() == 1).toList()) {
					network.delDivertex(v);
				}
				for (var e : network.edges()) {
					network.setReticulate(e, e.getTarget().getInDegree() > 1);
				}
			}
			return networks;
		}
	}

	/**
	 * recursively inserts subnetworks in all possible combinations
	 *
	 * @param blobTree           the backbone tree
	 * @param clusterNodeMap     maps clusters to the blob tree
	 * @param blobNetworksMap    the blob to networks map
	 * @param numberOfBlobs      total number of blobs (non-trivial connected components in the incompatibity graph)
	 * @param resolvedBlobs      blobs that have been resolved
	 * @param maxNumberOfResults max number of desired results
	 * @param networks           resulting networks
	 */
	private static void insertRec(PhyloTree blobTree, HashMap<BitSet, Node> clusterNodeMap, NodeArray<TreesAndTaxonClasses> blobNetworksMap,
								  int numberOfBlobs, Set<Node> resolvedBlobs, int maxNumberOfResults, List<PhyloTree> networks) {
		for (var blobNode : blobNetworksMap.keySet()) {
			if (blobNetworksMap.get(blobNode) != null && !resolvedBlobs.contains(blobNode)) {
				resolvedBlobs.add(blobNode);
				var taxonClasses = blobNetworksMap.get(blobNode).taxonClasses();
				for (var donorNetwork : blobNetworksMap.get(blobNode).trees()) {
					if (networks.size() > maxNumberOfResults)
						return;

					var blobChildren = IteratorUtils.asList(blobNode.children());
					var addedNodes = new ArrayList<Node>();

					try (NodeArray<Node> donor2acceptorMap = donorNetwork.newNodeArray()) {
						for (var v : donorNetwork.nodes()) {
							var w = blobTree.newNode(v.getInfo());
							donor2acceptorMap.put(v, w);
							addedNodes.add(w);
						}
						for (var e : donorNetwork.edges()) {
							blobTree.newEdge(donor2acceptorMap.get(e.getSource()), donor2acceptorMap.get(e.getTarget()));
						}
						for (var e : IteratorUtils.asList(blobNode.outEdges())) {
							blobTree.deleteEdge(e);
						}
						var donorRoot = donorNetwork.getRoot();
						blobTree.newEdge(blobNode, donor2acceptorMap.get(donorRoot));
						for (var v : donorNetwork.nodeStream().filter(Node::isLeaf).toList()) {
							var cluster = BitSetUtils.asBitSet(donorNetwork.getTaxa(v));
							for (var set : taxonClasses) {
								if (cluster.get(set.nextSetBit(1)))
									cluster.or(set);
							}
							var target = clusterNodeMap.get(cluster);
							blobTree.newEdge(donor2acceptorMap.get(v), target);
						}
					}
					insertRec(blobTree, clusterNodeMap, blobNetworksMap, numberOfBlobs, resolvedBlobs, maxNumberOfResults, networks);
					if (resolvedBlobs.size() == numberOfBlobs) {
						networks.add(new PhyloTree(blobTree));
					}
					for (var v : addedNodes) {
						blobTree.deleteNode(v);
					}
					for (var v : blobChildren) {
						blobTree.newEdge(blobNode, v);
					}
				}
				resolvedBlobs.remove(blobNode);
				return;
			}
		}
	}

	/**
	 * computes the cluster incompatibility graph for a collection of trees
	 * in graph, v.getInfo() contains cluster as bit set
	 * in graph, v.getData() contains indices of all trees that have the cluster, as bit set
	 *
	 * @param trees input trees
	 * @return graph
	 */
	public static Graph computeClusterIncompatibilityGraph(Collection<PhyloTree> trees) {
		var graph = new Graph();
		// v.getInfo() contains cluster as bit set
		// v.getData() contains indices of all trees that have the cluster, as bit set
		var clusterNodeMap = new HashMap<BitSet, Node>();
		// setup nodes:
		var treeId = 0;
		for (var tree : trees) {
			treeId++;
			try (var clusterMap = TreesUtils.extractClusters(tree)) {
				for (var cluster : clusterMap.values()) {
					var v = clusterNodeMap.computeIfAbsent(cluster, graph::newNode);
					var which = (BitSet) v.getData();
					if (which == null) {
						which = new BitSet();
						v.setData(which);
					}
					which.set(treeId);
				}
			}
		}
		// setup edges:
		for (var v = graph.getFirstNode(); v != null; v = v.getNext()) {
			var cv = (BitSet) v.getInfo();
			for (var w = v.getNext(); w != null; w = w.getNext()) {
				var cw = (BitSet) w.getInfo();
				if (Cluster.incompatible(cv, cw)) {
					graph.newEdge(v, w);
				}
			}
		}
		return graph;
	}

	/**
	 * for a given set of taxa and clusters, determines all sets of taxa that are equivalent (i.e. not separated by any cluster)
	 *
	 * @param taxa     all taxa
	 * @param clusters clusters
	 * @return list of sets of equivalent taxa
	 */
	public static List<BitSet> computeTaxonEquivalenceClasses(BitSet taxa, Collection<BitSet> clusters) {
		var list = new ArrayList<BitSet>();
		var mapped = new BitSet();
		for (var s : BitSetUtils.members(taxa)) {
			if (!mapped.get(s)) {
				var equivalent = new BitSet();
				equivalent.set(s);
				list.add(equivalent);
				mapped.set(s);
				for (var t : BitSetUtils.members(taxa, s + 1)) {
					if (!mapped.get(t) && clusters.stream().noneMatch(c -> c.get(s) != c.get(t))) {
						equivalent.set(t);
						mapped.set(t);
					}
				}
			}
		}
		return list;
	}

	/**
	 * for a given list of clusters and associated taxon reduction, computes the set of reduced clusters
	 * in which each taxon equivalence classes is represented by its first member
	 *
	 * @param clusters     clusters
	 * @param taxonClasses taxon equivalence classes
	 * @return reduced clusters
	 */
	private static Set<BitSet> reduceClusters(Collection<BitSet> clusters, Collection<BitSet> taxonClasses) {
		var reducedClusters = new HashSet<BitSet>();

		for (var cluster : clusters) {
			var reduced = new BitSet();
			for (var taxonSet : taxonClasses) {
				if (BitSetUtils.contains(cluster, taxonSet)) {
					reduced.set(taxonSet.nextSetBit(1));
				}
			}
			reducedClusters.add(reduced);
		}
		return reducedClusters;
	}

	/**
	 * extracts the list of distinct trees from a connected cluster-incompatibility graph
	 *
	 * @param graph component
	 * @return distinct trees
	 */
	private static TreesAndTaxonClasses extractTrees(Graph graph) {
		var treeIds = new BitSet();
		for (var v : graph.nodes()) {
			if (v.getData() instanceof BitSet bits) {
				treeIds.or(bits);
			}
		}

		var allClusters = graph.nodeStream().map(v -> (BitSet) v.getInfo()).collect(Collectors.toSet());
		var allTaxa = BitSetUtils.union(allClusters);
		var taxonClasses = computeTaxonEquivalenceClasses(allTaxa, allClusters);
		var reducedTaxa = BitSetUtils.asBitSet(taxonClasses.stream().mapToInt(t -> t.nextSetBit(1)).toArray());

		var allClusterSets = new ArrayList<Set<BitSet>>();
		var trees = new ArrayList<PhyloTree>();

		for (var treeId : BitSetUtils.members(treeIds)) {
			var clusters = new HashSet<BitSet>();
			var taxa = new BitSet();
			for (var v : graph.nodes()) {
				if (v.getData() instanceof BitSet bits && bits.get(treeId) && v.getInfo() instanceof BitSet cluster) {
					clusters.add(BitSetUtils.copy(cluster));
					taxa.or(cluster);
				}
			}

			var reducedClusters = reduceClusters(clusters, taxonClasses);
			for (var t : BitSetUtils.members(reducedTaxa)) {
				reducedClusters.add(BitSetUtils.asBitSet(t));
			}
			allClusterSets.add(reducedClusters);
		}

		if (true) { // remove any contained or equal sets
			var all = new ArrayList<>(allClusterSets);
			var toDelete = new TreeSet<Integer>();
			for (var i = 0; i < all.size(); i++) {
				if (!toDelete.contains(i)) {
					var iSet = all.get(i);
					for (var j = 0; j < all.size(); j++) {
						if (i != j && !toDelete.contains(j)) {
							var jSet = all.get(j);
							if (iSet.equals(jSet)) {
								if (i < j) {
									toDelete.add(j); // always remove the latter of two sets
								}
							} else if (iSet.containsAll(jSet)) {
								toDelete.add(j);
							}
						}
					}
				}
			}
			for (var i : toDelete) {
				all.set(i, null);
			}
			allClusterSets.clear();
			all.stream().filter(Objects::nonNull).forEach(allClusterSets::add);
		}

		for (var clusters : allClusterSets) {
			var tree = new PhyloTree();
			ClusterPoppingAlgorithm.apply(clusters, tree);
			tree.nodeStream().filter(v -> tree.getNumberOfTaxa(v) > 0).forEach(v -> tree.setLabel(v, "t" + tree.getTaxon(v)));

			if (tree.getRoot().getOutDegree() == 1) {
				var root = tree.getRoot().getFirstOutEdge().getTarget();
				tree.deleteNode(tree.getRoot());
				tree.setRoot(root);
			}

			if (false) {
				System.err.println("Reduced clusters:");
				for (var cluster : clusters) {
					System.err.println(StringUtils.toString(cluster, ","));
				}
				System.err.println("Resulting tree: " + NewickIO.toString(tree, false));
			}
			trees.add(tree);
		}

		return new TreesAndTaxonClasses(trees, taxonClasses);
	}

	/**
	 * performs mutatual refinement of all input trees
	 *
	 * @param trees input trees
	 * @return mut
	 */
	public static void mutualRefinement(Collection<PhyloTree> trees) {
		var incompatibilityGraph = computeClusterIncompatibilityGraph(trees);

		var compatibleClusters = incompatibilityGraph.nodeStream()
				.filter(v -> v.getDegree() == 0) // compatible with all
				.filter(v -> ((BitSet) v.getData()).cardinality() < trees.size()) // not in all trees
				.map(v -> (BitSet) v.getInfo()).toList();

		if (!compatibleClusters.isEmpty()) {
			var result = new ArrayList<PhyloTree>();
			for (var tree : trees) {
				// todo: insert clusters into existing tree
				var clusters = TreesUtils.collectAllHardwiredClusters(tree);
				if (!clusters.containsAll(compatibleClusters)) {
					clusters.addAll(compatibleClusters);
					var newTree = new PhyloTree();
					ClusterPoppingAlgorithm.apply(clusters, newTree);
					result.add(newTree);
				} else result.add(tree);
			}
			trees.clear();
			trees.addAll(result);
		}
	}

	/**
	 * list of trees together will the associated taxon classes
	 *
	 * @param trees        reduced trees or networks
	 * @param taxonClasses the associated taxon equivalence classes
	 */
	private record TreesAndTaxonClasses(Collection<PhyloTree> trees, Collection<BitSet> taxonClasses) {
		public String report() {
			var buf = new StringBuilder();
			buf.append("Taxon classes: ");
			var first = true;
			for (var set : taxonClasses) {
				if (first)
					first = false;
				else
					buf.append(" | ");
				buf.append(StringUtils.toString(set, ","));
			}
			buf.append("\n");
			for (var tree : trees) {
				buf.append(NewickIO.toString(tree, false));
				buf.append(";\n");
			}
			return buf.toString();
		}
	}

	public interface BiFunctionWithIOException<S, T, R> {
		R apply(S s, T t) throws IOException;
	}
}
