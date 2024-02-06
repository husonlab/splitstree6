/*
 *  TreesUtils.java Copyright (C) 2024 Daniel H. Huson
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

import jloda.graph.Edge;
import jloda.graph.Node;
import jloda.graph.NodeArray;
import jloda.phylo.LSAUtils;
import jloda.phylo.NewickIO;
import jloda.phylo.PhyloTree;
import jloda.util.BitSetUtils;
import jloda.util.CollectionUtils;
import jloda.util.NumberUtils;

import java.io.IOException;
import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * some computations on trees and networks
 * <p>
 * Daniel Huson, 1.2024
 */
public class TreesUtils {
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
		while (!toRemove.isEmpty()) {
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

	public static PhyloTree computeTreeFromCompatibleSplits(Function<Integer, String> taxonLabel, List<ASplit> splits) {
		if (splits.isEmpty())
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
		GraphUtils.addLabels(taxonLabel, tree);
		return tree;
	}

	/**
	 * computes a mapping of tree nodes to represented hardwired cluster. Does not contain the cluster at the root
	 *
	 * @param network input tree, may contain reticulations
	 * @return mapping of tree nodes to corresponding hardwired clusters
	 */
	public static NodeArray<BitSet> extractClusters(PhyloTree network) {
		NodeArray<BitSet> nodeClusterMap = network.newNodeArray();
		var stack = new Stack<Node>();
		stack.push(network.getRoot());
		while (!stack.isEmpty()) {
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
					var cluster = BitSetUtils.asBitSet(network.getTaxa(v));
					for (var w : v.children()) {
						cluster.or(nodeClusterMap.get(w));
					}
					nodeClusterMap.put(v, cluster);
				}
			}
		}
		network.nodeStream().filter(v -> v.getInDegree() != 1).forEach(nodeClusterMap::remove);
		return nodeClusterMap;
	}

	/**
	 * collects all hardwired clusters contained in a tree or network
	 */
	public static Set<BitSet> collectAllHardwiredClusters(PhyloTree network) {
		var clusters = new HashSet<BitSet>();
		collectAllHardwiredClustersRec(network, network.getRoot(), e -> true, clusters);
		return clusters;
	}

	public static BitSet collectAllHardwiredClustersRec(PhyloTree network, Node v, Predicate<Edge> useEdge, HashSet<BitSet> clusters) {
		var set = BitSetUtils.asBitSet(network.getTaxa(v));
		for (Edge f : v.outEdges()) {
			if (useEdge.test(f)) {
				var w = f.getTarget();
				set.or(collectAllHardwiredClustersRec(network, w, useEdge, clusters));
			}
		}
		clusters.add(set);
		return set;
	}


	private static record WeightConfidence(double weight, double confidence) {
	}


	/**
	 * collects all softwired clusters contained in a network.
	 */
	public static Set<BitSet> collectAllSoftwiredClusters(PhyloTree network) {
		var reticulateNodes = network.nodeStream().filter(v -> v.getInDegree() > 1).toArray(Node[]::new);

		try (var activeReticulateEdges = network.newEdgeSet()) {
			var clusters = new HashSet<BitSet>();
			collectAllSoftwiredClustersRec(reticulateNodes, 0, network, activeReticulateEdges, clusters);
			return clusters;
		}
	}

	private static void collectAllSoftwiredClustersRec(Node[] reticulateNodes, int which, PhyloTree network, Set<Edge> activeReticulateEdges, HashSet<BitSet> clusters) {
		if (which < reticulateNodes.length) {
			for (var inEdge : reticulateNodes[which].inEdges()) {
				activeReticulateEdges.add(inEdge);
				collectAllSoftwiredClustersRec(reticulateNodes, which + 1, network, activeReticulateEdges, clusters);
				activeReticulateEdges.remove(inEdge);
			}
		} else {
			collectAllHardwiredClustersRec(network, network.getRoot(), e -> !network.isReticulateEdge(e) || activeReticulateEdges.contains(e), clusters);
		}
	}

	public static void main(String[] args) throws IOException {
		var taxaIdMap = new HashMap<String, Integer>();

		//var network=NewickIO.valueOf("(((e,((((a)#H2:0,c))#H1:0,d)),(((b,#H2:0),#H1:0),#H2:0)));");
		//var network = NewickIO.valueOf("((((((((((((((t4,(t1,t2:2):2),(t5,t11:2):2),(t8,t19:2):2),(t6,((t20,t15:2),t12:2):2):2),(((t13,(t16)#H2),(t18)#H3))#H1),#H3),(t3)#H4),(((((t7,(t9,t17:2):2),#H4),#H3),(((((t10,t14:2),#H4),#H1),#H2))#H6))#H5),#H6),#H1),#H5),#H4),#H1));");
		var network = NewickIO.valueOf("((((((((((((((t4,(t1,t2:2):2),(t5,t11:2):2),(t8,t19:2):2),(t6,((t20,t15:2),t12:2):2):2),(((t13,(t16)#H2:0),(t18)#H3:0))#H1:0),#H3:0),(t3)#H4:0),(((((t7,(t9,t17:2):2),#H4:0),#H3:0),(((((t10,t14:2),#H4:0),#H1:0),#H2:0))#H6:0))#H5:0),#H6:0),#H1:0),#H5:0),#H4:0),#H1:0));");
		addAdhocTaxonIds(network, taxaIdMap);

		LSAUtils.computeLSAChildrenMap(network, network.newNodeArray());
		System.err.println(NewickIO.toString(network, false) + ";");

		var softwiredClusters = collectAllSoftwiredClusters(network);
		System.err.println("network clusters: " + softwiredClusters);

		String[] lines = new String[]{
				"(((t10,t14),t3),(((t17,t9),t7),(t18,(((((t20,t15),t12),t6),(((t5,t11),(t4,(t1,t2))),(t19,t8))),(t13,t16)))));",
				"(((((t17,t9),t7),t3),t18),(((((t20,t15),t12),t6),(((t5,t11),(t4,(t1,t2))),(t19,t8))),((t13,t16),(t10,t14))));",
				"((((t17,t9),t7),((t18,(t13,t16)),((t10,t14),((((t20,t15),t12),t6),(((t5,t11),(t4,(t1,t2))),(t19,t8)))))),t3);",
				"((((t17,t9),t7),((t10,t14),t3)),(t18,(((((t20,t15),t12),t6),(((t5,t11),(t4,(t1,t2))),(t19,t8))),(t13,t16))));",
				"(((t10,t14),t3),((((t17,t9),t7),t18),(((((t20,t15),t12),t6),(((t5,t11),(t4,(t1,t2))),(t19,t8))),(t13,t16))));",
				"(((((t17,t9),t7),t3),(t10,t14)),(t18,(((((t20,t15),t12),t6),(((t5,t11),(t4,(t1,t2))),(t19,t8))),(t13,t16))));",
				"(((((t17,t9),t7),t3),((t13,t16),(t10,t14))),(t18,((((t20,t15),t12),t6),(((t5,t11),(t4,(t1,t2))),(t19,t8)))));",
				"((t10,t14),(((t17,t9),t7),((t18,(((((t20,t15),t12),t6),(((t5,t11),(t4,(t1,t2))),(t19,t8))),(t13,t16))),t3)));",
				"((t18,(((((t20,t15),t12),t6),(((t5,t11),(t4,(t1,t2))),(t19,t8))),t13)),((t16,(t10,t14)),(((t17,t9),t7),t3)));",
				"((t18,t13),(((t16,(t10,t14)),((((t20,t15),t12),t6),(((t5,t11),(t4,(t1,t2))),(t19,t8)))),(((t17,t9),t7),t3)));"};

		/*String[] lines = new String[]{
				"((a,(b,c)),(d,e));",
				"((e,(d,c)),(b,a));",
				"((e,d),((c,a),b));",
				"((e,d),(c,(a,b)));",
				"((f,b,c),d,(e,a));",
		"((a,b),x);"};*/

		for (var line : lines) {
			var tree = NewickIO.valueOf(line);
			addAdhocTaxonIds(tree, taxaIdMap);
			System.out.println(NewickIO.toString(tree, false) + ";");

			var treeClusters = collectAllHardwiredClusters(tree);
			System.out.println("tree clusters: " + treeClusters);

			System.out.println("Missing in network: :" + CollectionUtils.difference(treeClusters, softwiredClusters));
		}


	}

	public static void addAdhocTaxonIds(PhyloTree tree, Map<String, Integer> taxaIdMap) {
		tree.nodeStream().filter(v -> tree.getLabel(v) != null).forEach(v -> {
			var taxId = taxaIdMap.getOrDefault(tree.getLabel(v), taxaIdMap.size() + 1);
			tree.addTaxon(v, taxId);
			taxaIdMap.put(tree.getLabel(v), taxId);
		});
	}

	public static List<PhyloTree> getSubTrees(PhyloTree forest) {
		var taxaIdMap = new HashMap<String, Integer>();
		addAdhocTaxonIds(forest, taxaIdMap);
		var list = new ArrayList<PhyloTree>();
		for (var v : forest.nodeStream().filter(v -> v.getInDegree() == 0).toList()) {
			var tree = new PhyloTree();
			var root = tree.copy(forest).get(v);
			tree.setRoot(root);
			try (var toDelete = tree.newNodeSet()) {
				toDelete.addAll(tree.nodes());
				tree.preorderTraversal(root, toDelete::remove);
				for (var w : toDelete)
					tree.deleteNode(w);
			}
			addAdhocTaxonIds(tree, taxaIdMap);
			LSAUtils.computeLSAChildrenMap(tree, tree.newNodeArray());
			list.add(tree);
		}
		return list;
	}
}
