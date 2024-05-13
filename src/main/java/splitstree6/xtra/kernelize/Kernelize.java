/*
 *  Kernelize.java Copyright (C) 2024 Daniel H. Huson
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

import jloda.graph.Edge;
import jloda.graph.Graph;
import jloda.graph.Node;
import jloda.graph.NodeArray;
import jloda.graph.algorithms.IsDAG;
import jloda.phylo.NewickIO;
import jloda.phylo.PhyloTree;
import jloda.phylo.algorithms.ClusterPoppingAlgorithm;
import jloda.util.*;
import jloda.util.progress.ProgressListener;
import splitstree6.data.TaxaBlock;
import splitstree6.utils.TreesUtils;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Kernelization of multi-furcating rooted-tree reconciliation algorithms
 * Daniel Huson, 10.23
 */
public class Kernelize {
	private static boolean verbose = true;


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
	public static List<PhyloTree> apply(ProgressListener progress, TaxaBlock taxaBlock, List<PhyloTree> inputTrees,
										BiFunctionWithIOException<Collection<PhyloTree>, ProgressListener, Collection<PhyloTree>> algorithm,
										int maxNumberOfResults) throws IOException {

		// setup incompatibility graph
		var incompatibilityGraph = ClusterIncompatibilityGraph.apply(inputTrees);
		if (verbose) {
			System.err.println("Compatible clusters:   " + incompatibilityGraph.nodeStream().filter(v -> v.getDegree() == 0).count());
			System.err.println("Incompatible clusters: " + incompatibilityGraph.nodeStream().filter(v -> v.getDegree() > 0).count());
		}

		// extract all incompatibility components
		var components = incompatibilityGraph.extractAllConnectedComponents();
		if (false) {
			System.err.println("Components:     " + components.stream().filter(c -> c.getNumberOfNodes() > 1).count());
			System.err.println();
		}

		// compute the blob-tree:
		var blobTree = new PhyloTree();
		var clusterNodeMap = new HashMap<BitSet, Node>();
		var blobTreeClusters = new ArrayList<BitSet>();
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
			blobTreeClusters.addAll(clusters);
			blobTreeClusters.sort((a, b) -> -Integer.compare(a.cardinality(), b.cardinality()));
		}

		if (verbose) { // report incompatibility components and the associated reduced trees:
			var count = 0;
			for (var component : components) {
				if (component.getNumberOfNodes() > 1) {
					System.err.println("Component " + (++count) + ": " + component.getNumberOfNodes() + " clusters");
					System.err.print("Clusters:");
					for (var v : component.nodes())
						System.err.print(" " + v.getInfo() + ",");
					System.err.println();
					System.err.print("In trees:");
					for (var v : component.nodes())
						System.err.print(" " + v.getData() + ",");
					System.err.println();
					var reducedTrees = extractTrees(component, blobTreeClusters);
					System.err.println(reducedTrees.report());
				}
			}
		}

		report("  blob tree", blobTree);
			checkNetwork("Blob tree", blobTree);

		// run the algorithm on all components:
		try (NodeArray<TreesAndTaxonClasses> blobNetworksMap = blobTree.newNodeArray()) {
			for (var component : components) {
				if (component.getNumberOfNodes() > 1) {
					var reducedTreesAndTaxonClasses = extractTrees(component, blobTreeClusters);
					for (var tree : reducedTreesAndTaxonClasses.trees())
						TreesUtils.addLabels(tree, taxaBlock::getLabel);

					if (verbose) {
						System.err.println("Subproblem input: " + NewickIO.toString(reducedTreesAndTaxonClasses.trees(), false));
						if (true) {
							for (var tree : reducedTreesAndTaxonClasses.trees()) {
								tree = new PhyloTree(tree);
								for (var v : tree.nodes()) {
									if (tree.hasTaxa(v)) {
										tree.setLabel(v, "t" + tree.getTaxon(v));
									}
								}
								System.err.println("Subproblem input: " + NewickIO.toString(tree, false) + ";");
							}
						}
					}

					for (var tree : reducedTreesAndTaxonClasses.trees()) {
						checkNetwork("component input", tree);
					}

					report("component input", reducedTreesAndTaxonClasses.trees().toArray(new PhyloTree[0]));

					var networks = algorithm.apply(reducedTreesAndTaxonClasses.trees(), progress);

					if (true) { // todo: algorithm doesn't return taxon ids
						if (IteratorUtils.size(networks.iterator().next().getTaxa()) == 0) {
							for (var network : networks) {
								for (var v : network.nodes()) {
									var label = network.getLabel(v);
									if (label != null && NumberUtils.isInteger(label.substring(1))) {
										var taxonId = NumberUtils.parseInt(label.substring(1));
										network.addTaxon(v, taxonId);
									}
								}
							}
						}
					}
					if (verbose) {
						for (var network : networks) {
							report("component");
							System.err.println("Number of nodes: " + network.getNumberOfNodes());
							System.err.println("Number of isolated nodes: " + network.nodeStream().filter(v -> v.getDegree() == 0).count());
							report("network", network);

						}
					}

					for (var network : networks) {
						checkNetwork("Subproblem networks", network);
					}

					var subnetworks = new TreesAndTaxonClasses(networks, reducedTreesAndTaxonClasses.taxonClasses());
					var donorTaxa = BitSetUtils.union(reducedTreesAndTaxonClasses.taxonClasses());
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
				if (network.getRoot().getOutDegree() > 1) {
					var root = network.newNode();
					network.newEdge(root, network.getRoot());
					network.setRoot(root);
				}
				for (var v : network.nodeStream().filter(v -> v.getInDegree() == 1 && v.getOutDegree() == 1).toList()) {
					network.delDivertex(v);
				}
				network.edgeStream().forEach(e -> network.setReticulate(e, e.getTarget().getInDegree() > 1));
				if (verbose)
					System.err.println(NewickIO.toString(network, false) + ";");
				if (!checkNetwork("output", network))
					throw new IOException("Bad output");

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
					var addedEdges = new ArrayList<Edge>();

					try (NodeArray<Node> donor2acceptorMap = donorNetwork.newNodeArray()) {
						for (var v : donorNetwork.nodes()) {
							var w = blobTree.newNode(v.getInfo());
							donor2acceptorMap.put(v, w);
							addedNodes.add(w);
						}
						for (var e : donorNetwork.edges()) {
							addedEdges.add(blobTree.newEdge(donor2acceptorMap.get(e.getSource()), donor2acceptorMap.get(e.getTarget())));
						}
						for (var e : IteratorUtils.asList(blobNode.outEdges())) {
							blobTree.deleteEdge(e);
						}
						var donorRoot = donorNetwork.getRoot();
						blobTree.newEdge(blobNode, donor2acceptorMap.get(donorRoot));
						for (var v : donorNetwork.nodeStream().filter(Node::isLeaf).toList()) {
							var cluster = BitSetUtils.asBitSet(donorNetwork.getTaxa(v));
							if (true) {
								for (var set : taxonClasses) {
									if (cluster.intersects(set)) {
										cluster.or(set);
										break;
									}
								}
							}
							var targets = new HashSet<Node>();
							if (clusterNodeMap.containsKey(cluster))
								targets.add(clusterNodeMap.get(cluster));
							else {
								for (var t : BitSetUtils.members(cluster)) {
									targets.add(clusterNodeMap.get(BitSetUtils.asBitSet(t)));
								}
							}
							if (targets.size() == 1) {
								addedEdges.add(blobTree.newEdge(donor2acceptorMap.get(v), targets.iterator().next()));
							} else {
								for (var target : targets) {
									addedEdges.add(blobTree.newEdge(donor2acceptorMap.get(v), target));
								}
							}
						}
					}
					insertRec(blobTree, clusterNodeMap, blobNetworksMap, numberOfBlobs, resolvedBlobs, maxNumberOfResults, networks);
					if (!checkNetwork("blob tree", blobTree)) {
						System.err.println("What?");
						var copy = new PhyloTree(blobTree);
						for (var v : copy.nodes()) {
							if (v.isLeaf() && copy.getLabel(v) == null)
								copy.setLabel(v, "What?");
							if (v.getInDegree() > 1) {
								for (var e : v.inEdges())
									copy.setReticulate(e, true);
							}
						}
						System.err.println(NewickIO.toString(copy, false) + ";");
					}
					networks.add(new PhyloTree(blobTree));
					for (var e : addedEdges) {
						if (e.getOwner() != null)
							blobTree.deleteEdge(e);
					}
					for (var v : addedNodes) {
						if (v.getOwner() != null)
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
	 * for a given set of taxa and clusters, determines all sets of taxa that are equivalent (i.e. not separated by any cluster)
	 * Also require that any set of equivalent taxa appears in the list of blob tree clusters
	 *
	 * @param taxa     all taxa
	 * @param clusters clusters
	 * @return list of sets of equivalent taxa
	 */
	public static List<BitSet> computeTaxonEquivalenceClasses(BitSet taxa, Collection<BitSet> clusters, List<BitSet> blobTreeClusters) {
		var list = new ArrayList<BitSet>();
		var mapped = new BitSet();
		for (var s : BitSetUtils.members(taxa)) {
			if (!mapped.get(s)) {
				var equivalent = new BitSet();
				equivalent.set(s);
				list.add(equivalent);
				mapped.set(s);
				for (var t : BitSetUtils.members(taxa, s + 1)) {
					if (!mapped.get(t) && clusters.stream().noneMatch(c -> c.cardinality() > 1 && c.get(s) != c.get(t))) {
						equivalent.set(t);
						mapped.set(t);
					}
				}
			}
		}
		var result = new ArrayList<BitSet>();
		for (var set : list) {
			for (var blobTreeCluster : blobTreeClusters) {
				if (set.equals(blobTreeCluster)) {
					result.add(set);
					break;
				} else if (BitSetUtils.contains(set, blobTreeCluster)) {
					result.add(blobTreeCluster);
					set.andNot(blobTreeCluster);
					if (set.cardinality() == 0)
						break;
				}
			}
		}
		return result;
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
	 * @param incompatibilityComponent component
	 * @return distinct trees
	 */
	private static TreesAndTaxonClasses extractTrees(Graph incompatibilityComponent, List<BitSet> blobTreeClusters) {
		var treeIds = new BitSet();
		for (var v : incompatibilityComponent.nodes()) {
			if (v.getData() instanceof BitSet bits) { //getData(): trees containing cluster associated with v
				treeIds.or(bits);
			}
		}

		var allClusters = incompatibilityComponent.nodeStream().map(v -> (BitSet) v.getInfo()).collect(Collectors.toSet());
		var allTaxa = BitSetUtils.union(allClusters);
		var taxonClasses = computeTaxonEquivalenceClasses(allTaxa, allClusters, blobTreeClusters);
		var reducedTaxa = BitSetUtils.asBitSet(taxonClasses.stream().mapToInt(t -> t.nextSetBit(1)).toArray());

		var allClusterSets = new ArrayList<Set<BitSet>>();
		var trees = new ArrayList<PhyloTree>();

		for (var treeId : BitSetUtils.members(treeIds)) {
			var clusters = new HashSet<BitSet>();
			var taxa = new BitSet();
			for (var v : incompatibilityComponent.nodes()) {
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
			var toDelete = new HashSet<BitSet>();
			for (var cluster : clusters) {
				if (cluster.cardinality() == 1) {
					var ok = false;
					for (var other : clusters) {
						if (other.cardinality() > 1 && other.get(cluster.nextSetBit(0))) {
							ok = true;
							break;
						}
					}
					if (!ok) {
						toDelete.add(cluster);
						break;
					}
				}
			}
			if (!toDelete.isEmpty())
				clusters.removeAll(toDelete);
			var tree = new PhyloTree();
			ClusterPoppingAlgorithm.apply(clusters, tree);
			tree.nodeStream().filter(v -> tree.getNumberOfTaxa(v) > 0).forEach(v -> tree.setLabel(v, "t" + tree.getTaxon(v)));

			if (tree.getRoot().getOutDegree() == 1) {
				var root = tree.getRoot().getFirstOutEdge().getTarget();
				tree.deleteNode(tree.getRoot());
				tree.setRoot(root);
			}

			if (true) {
				System.err.println("Reduced clusters:");
				for (var cluster : clusters) {
					System.err.println(StringUtils.toString(cluster, ","));
				}
				report("tree", tree);
			}
			trees.add(tree);
		}

		return new TreesAndTaxonClasses(trees, taxonClasses);
	}

	public static boolean checkNetwork(String label, PhyloTree network) {
		var ok = true;
		if (!IsDAG.apply(network)) {
			System.err.println(label + ": Error: Is not a DAG!");
			ok = false;
		}
		if (network.getRoot() == null) {
			System.err.println(label + ": Error: root not set");
			ok = false;
		} else {
			if (network.getRoot().getInDegree() > 0) {
				System.err.println(label + ": Error: root has indegree " + network.getRoot().getInDegree());
				ok = false;
			}
		}

		var roots = network.nodeStream().filter(v -> v.getInDegree() == 0).count();
		if (roots > 1) {
			System.err.println(label + ": Error: Too many potential roots: " + roots);
			ok = false;
		}
		var isolatedNodes = network.nodeStream().filter(v -> v.getDegree() == 0).count();
		if (isolatedNodes > 1) {
			System.err.println(label + ": Error: has isolated nodes: " + isolatedNodes);
			ok = false;
		}

		var leavesWithoutTaxa = network.nodeStream().filter(v -> v.getOutDegree() == 0 && !network.hasTaxa(v)).count();
		if (leavesWithoutTaxa > 0) {
			System.err.println(label + ": Error: leaves without taxa: " + leavesWithoutTaxa);
			ok = false;
		}
		var internalWithTaxa = network.nodeStream().filter(v -> v.getOutDegree() != 0 && network.hasTaxa(v)).count();

		if (internalWithTaxa > 0) {
			System.err.println(label + ": Error: internal nodes with taxa: " + internalWithTaxa);
			ok = false;
		}
		if (verbose) {
			if (ok)
				System.err.println(label + ": looks ok");
		}
		return ok;

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


	public static void report(String label, PhyloTree... networks) {
		if (verbose) {
			var buf = new StringBuilder(label + ": ");
			if (networks.length != 1)
				buf.append("\n");
			for (var network0 : networks) {
				var network = new PhyloTree(network0);
				network.nodeStream().filter(network::hasTaxa).forEach(v -> {
					if (network.getLabel(v) == null)
						network.setLabel(v, "t" + network.getTaxon(v));
					else if (false)
						network.setLabel(v, network.getLabel(v) + "_" + network.getTaxon(v));
				});
				buf.append(network.toBracketString(false)).append(";\n");
			}
			System.err.print(buf);
		}
	}

	public interface BiFunctionWithIOException<S, T, R> {
		R apply(S s, T t) throws IOException;
	}
}
