/*
 * TreeMutualRefinement.java Copyright (C) 2026 Daniel H. Huson
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
 *
 */

package splitstree6.algorithms.utils;

import jloda.graph.Node;
import jloda.phylo.PhyloTree;
import jloda.util.BitSetUtils;
import jloda.util.IteratorUtils;

import java.util.*;

/**
 * refines trees mutually
 * Daniel Huson, 4.2026
 */
public class TreeMutualRefinement {

	/**
	 * apply refinement and return list of modified trees
	 *
	 * @param trees0 input trees
	 * @return refined output trees
	 */
	public static List<PhyloTree> apply(List<PhyloTree> trees0) {
		var trees = new ArrayList<>(trees0.stream().map(PhyloTree::new).toList());

		// setup maps:
		var clusterNodeMaps = new ArrayList<Map<BitSet, Node>>();
		var nodeClusterMaps = new ArrayList<Map<Node, BitSet>>();
		for (var tree : trees) {
			var clusterNodeMap = new HashMap<BitSet, Node>();
			var nodeClusterMap = new HashMap<Node, BitSet>();

			tree.postorderTraversal(v -> {
				BitSet cluster;
				if (v.isLeaf()) {
					cluster = BitSetUtils.asBitSet(tree.getTaxon(v));
				} else {
					cluster = BitSetUtils.union(v.childrenStream().map(nodeClusterMap::get).toList());
				}
				clusterNodeMap.put(cluster, v);
				nodeClusterMap.put(v, cluster);
			});
			clusterNodeMaps.add(clusterNodeMap);
			nodeClusterMaps.add(nodeClusterMap);
		}

		// compare all pairs of trees:
		var count = 0;
		for (var i = 0; i < trees.size(); i++) {
			var iTree = trees.get(i);
			var iClusterNodeMap = clusterNodeMaps.get(i);
			var iNodeClusterMap = nodeClusterMaps.get(i);

			for (var j = 0; j < trees.size(); j++) {
				if (i != j) {
					var jClusterNodeMap = clusterNodeMaps.get(j);
					var jNodeClusterMap = nodeClusterMaps.get(j);
					for (var cluster : jClusterNodeMap.keySet()) {
						var iV = iClusterNodeMap.get(cluster);
						var jV = jClusterNodeMap.get(cluster);
						if (iV != null && jV != null && iV.getOutDegree() > jV.getOutDegree()) { // will try to refine iV
							var iChildren = IteratorUtils.asList(iV.children());
							var iChildClusters = iChildren.stream().map(iNodeClusterMap::get).toList();
							var jChildren = IteratorUtils.asList(jV.children());
							var jChildClusters = jChildren.stream().map(jNodeClusterMap::get).toList();
							for (var entry : findUnions(iChildClusters, jChildClusters).entrySet()) {
								var jIndex = entry.getKey();
								var iUnionIndices = entry.getValue();
								var refinementNode = iTree.newNode();
								var refinementCluster = jNodeClusterMap.get(jChildren.get(jIndex));
								iClusterNodeMap.put(refinementCluster, refinementNode);
								iNodeClusterMap.put(refinementNode, refinementCluster);
								iTree.newEdge(iV, refinementNode);
								for (var iIndex : iUnionIndices) {
									var child = iChildren.get(iIndex);
									iTree.deleteEdge(child.getFirstInEdge());
									iTree.newEdge(refinementNode, child);
								}
								count++;
							}
						}
					}
				}
			}
		}
		if (false) System.err.println("Mutual refinement: " + count);
		return trees;
	}

	/**
	 * Returns a map from index j in B to the list of indices i in A such that
	 * B[j] is the union of two or more members of A.
	 * Assumptions:
	 * - sets in A are pairwise disjoint
	 * - sets in B are pairwise disjoint
	 * - union(A) == union(B)
	 */
	private static Map<Integer, List<Integer>> findUnions(List<BitSet> A, List<BitSet> B) {
		var result = new LinkedHashMap<Integer, List<Integer>>();

		for (var j = 0; j < B.size(); j++) {
			var b = B.get(j);
			var parts = new ArrayList<Integer>();
			for (var i = 0; i < A.size(); i++) {
				var a = A.get(i);
				if (BitSetUtils.contains(b, a)) {
					parts.add(i);
				}
			}
			if (parts.size() >= 2) {
				result.put(j, parts);
			}
		}
		return result;
	}
}
