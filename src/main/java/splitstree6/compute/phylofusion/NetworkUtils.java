/*
 *  NetworkUtils.java Copyright (C) 2024 Daniel H. Huson
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

package splitstree6.compute.phylofusion;

import jloda.graph.Edge;
import jloda.graph.EdgeArray;
import jloda.graph.Node;
import jloda.graph.algorithms.IsDAG;
import jloda.phylo.PhyloTree;
import jloda.util.*;
import splitstree6.utils.TreesUtils;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

public class NetworkUtils {
	/**
	 * set all edge weights as average of tree-length-normalized input weights
	 *
	 * @param trees        input trees
	 * @param network      network
	 * @param milliseconds amount of time allowed for computation
	 */
	public static void setEdgeWeights(Collection<PhyloTree> trees, PhyloTree network, boolean normalizeEdgeWeights, long milliseconds) {
		try (var edgeClustersMap = collectAllSoftwiredClusters(network, milliseconds)) {
			var edgeWeightsMap = new ConcurrentHashMap<Edge, Collection<Double>>();

			ExecuteInParallel.apply(trees, tree -> {
				var treeLength = tree.edgeStream().mapToDouble(tree::getWeight).sum();
				try (var nodeClusterMap = TreesUtils.extractClusters(tree)) {
					var clusterWeightMap = new HashMap<BitSet, Double>();
					for (var v : tree.nodes()) {
						if (v.getInDegree() == 1) {
							var e = v.getFirstInEdge();
							clusterWeightMap.put(nodeClusterMap.get(v), tree.getWeight(e));
						}
					}
					for (var entry : edgeClustersMap.entrySet()) {
						var e = entry.getKey();
						var clusters = entry.getValue();
						for (var cluster : clusters) {
							var weight = clusterWeightMap.get(cluster);
							if (weight != null) {
								if (normalizeEdgeWeights && treeLength > 0) {
									weight /= treeLength;
								}
								edgeWeightsMap.computeIfAbsent(e, k -> new HashSet<>()).add(weight);
							}
						}
					}
				}
			}, ProgramExecutorService.getNumberOfCoresToUse());

				var allTaxa = BitSetUtils.asBitSet(network.getTaxa());
				for (var e : network.edges()) {
					var values = edgeWeightsMap.get(e);
					if (values != null) {
						var weight = values.stream().mapToDouble(w -> w).average().orElse(0);
						network.setWeight(e, weight);
					} else {
						network.setWeight(e, 0.0001);
						if (edgeClustersMap.get(e) == null || !edgeClustersMap.get(e).contains(allTaxa)) {
							System.err.println("Undefined weight, network edge " + e);
							System.err.println("Associated clusters: " + StringUtils.toString(edgeClustersMap.get(e), ";"));
						}
					}
				}

		} catch (CanceledException ex) {
			System.err.println("Set network edge lengths: timed out");
			network.edgeStream().forEach(e -> network.setWeight(e, 1.0));
		} catch (Exception ex) {
			network.edgeStream().forEach(e -> network.setWeight(e, 1.0));
		}
	}

	public static EdgeArray<Set<BitSet>> collectAllSoftwiredClusters(PhyloTree network, long milliseconds) throws CanceledException {
		var reticulateNodes = network.nodeStream().filter(v -> v.getInDegree() > 1).toArray(Node[]::new);
		EdgeArray<Set<BitSet>> edgeClustersMap = network.newEdgeArray();

		var timeToStop = System.currentTimeMillis() + milliseconds;

		try (var activeReticulateEdges = network.newEdgeSet()) {
			collectAllSoftwiredClustersRec(reticulateNodes, 0, network, activeReticulateEdges, edgeClustersMap, timeToStop);
		}
		return edgeClustersMap;
	}

	private static void collectAllSoftwiredClustersRec(Node[] reticulateNodes, int which, PhyloTree network, Set<Edge> activeReticulateEdges,
													   Map<Edge, Set<BitSet>> edgeClustersMap, long timeToStop) throws CanceledException {
		if (which < reticulateNodes.length) {
			for (var inEdge : reticulateNodes[which].inEdges()) {
				activeReticulateEdges.add(inEdge);
				collectAllSoftwiredClustersRec(reticulateNodes, which + 1, network, activeReticulateEdges, edgeClustersMap, timeToStop);
				activeReticulateEdges.remove(inEdge);
			}
		} else {
			collectAllHardwiredClustersRec(network, network.getRoot(), e -> !network.isReticulateEdge(e) || activeReticulateEdges.contains(e), edgeClustersMap, timeToStop);
		}
	}

	private static BitSet collectAllHardwiredClustersRec(PhyloTree network, Node v, Predicate<Edge> useEdge, Map<Edge, Set<BitSet>> edgeClustersMap, long timeToStop) throws CanceledException {
		var set = BitSetUtils.asBitSet(network.getTaxa(v));
		for (var f : v.outEdges()) {
			if (useEdge.test(f)) {
				var w = f.getTarget();
				var cluster = collectAllHardwiredClustersRec(network, w, useEdge, edgeClustersMap, timeToStop);
				edgeClustersMap.computeIfAbsent(f, k -> new HashSet<>()).add(cluster);
				set.or(cluster);
			}
		}
		if (System.currentTimeMillis() > timeToStop)
			throw new CanceledException();
		return set;
	}


	public static boolean check(PhyloTree network) {
		var ok = true;
		if (!IsDAG.apply(network)) {
			System.err.println("Is not a DAG");
			ok = false;
		}
		var roots = network.nodeStream().filter(v -> v.getInDegree() == 0).toList();
		if (roots.size() != 1) {
			System.err.println("Wrong number of root nodes: " + roots.size());
			ok = false;
		}
		if (network.getRoot() == null) {
			System.err.println("Root node not declared");

		} else if (!roots.contains(network.getRoot())) {
			System.err.println("Network declared root has wrong in-degree: " + network.getRoot().getInDegree());
		}

		for (var v : network.nodes()) {
			if (v.isLeaf() && network.getTaxon(v) == -1) {
				System.err.println("Leaf with no taxon: " + v);
				ok = false;
			}
			if (v.isLeaf() && network.getLabel(v) == null) {
				System.err.println("Leaf with no label: " + v);
				ok = false;
			}
			if (!v.isLeaf() && network.getTaxon(v) != -1) {
				System.err.println("Non-leaf with no taxon: " + v);
				ok = false;
			}
			if (!v.isLeaf() && network.getLabel(v) != null) {
				System.err.println("Non-leaf with label: " + v);
				ok = false;
			}
		}
		try (var reachableFromRoot = network.newNodeSet()) {
			network.postorderTraversal(reachableFromRoot::add);
			if (reachableFromRoot.size() != network.getNumberOfNodes()) {
				System.err.println("Root reaches " + reachableFromRoot.size() + " of " + network.getNumberOfNodes() + " nodes");
				ok = false;
			}
		}
		for (var f : network.edges()) {
			if (f.getTarget().getInDegree() == 1 && network.isReticulateEdge(f)) {
				System.err.println("Non-reticulate edge marked as reticulate: " + f);
				ok = false;
			} else if (f.getTarget().getInDegree() > 1 && !network.isReticulateEdge(f)) {
				System.err.println("Reticulate edge marked as non-reticulate: " + f);
				ok = false;
			}
		}
		return ok;
	}

}
