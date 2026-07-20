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
						// no out-degree gate: findUnions is the arbiter. A grouping can exist even when
						// iV has no more children than jV, e.g. A=[{1,2},{3,4},{5,6},{7,8}] against
						// B=[{1,2,3,4},{5},{6},{7},{8}], where {1,2,3,4} refines iV but 4 > 5 is false.
						if (iV != null && jV != null) { // will try to refine iV
							var iChildren = IteratorUtils.asList(iV.children());
							var iChildClusters = iChildren.stream().map(iNodeClusterMap::get).toList();
							var jChildren = IteratorUtils.asList(jV.children());
							var jChildClusters = jChildren.stream().map(jNodeClusterMap::get).toList();
							for (var iUnionIndices : findUnions(iChildClusters, jChildClusters)) {
								var refinementNode = iTree.newNode();
								// built from iV's own child clusters: a fresh BitSet owned by tree i, and
								// correct by construction rather than by trusting findUnions. Equals the
								// motivating member of jChildClusters, but must not alias it: that object
								// belongs to tree j's maps.
								var refinementCluster = BitSetUtils.union(iUnionIndices.stream().map(iChildClusters::get).toList());
								iClusterNodeMap.put(refinementCluster, refinementNode);
								iNodeClusterMap.put(refinementNode, refinementCluster);
								var refinementEdge = iTree.newEdge(iV, refinementNode);
								if (iTree.hasEdgeWeights())
									iTree.setWeight(refinementEdge, 0.0);
								if (iTree.hasEdgeConfidences())
									iTree.setConfidence(refinementEdge, 0.0);
								for (var iIndex : iUnionIndices) {
									var child = iChildren.get(iIndex);
									var oldInEdge = child.getFirstInEdge();
									if (oldInEdge.getSource() != iV) // cannot happen: findUnions returns disjoint groups
										throw new IllegalStateException("TreeMutualRefinement: child already moved");
									var newInEdge = iTree.newEdge(refinementNode, child);
									if (iTree.hasEdgeWeights())
										iTree.setWeight(newInEdge, iTree.getWeight(oldInEdge));
									if (iTree.hasEdgeConfidences())
										iTree.setConfidence(newInEdge, iTree.getConfidence(oldInEdge));
									iTree.deleteEdge(oldInEdge);
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
	 * Returns, for each member of B that is exactly the union of two or more members of A, the list
	 * of indices of those members of A.
	 * Assumptions:
	 * - sets in A are pairwise disjoint
	 * - sets in B are pairwise disjoint
	 * - union(A) == union(B)
	 * The returned groups are pairwise disjoint, so no member of A is reported twice.
	 */
	private static List<List<Integer>> findUnions(List<BitSet> A, List<BitSet> B) {
		var result = new ArrayList<List<Integer>>();

		for (BitSet b : B) {
			var parts = new ArrayList<Integer>();
			for (var i = 0; i < A.size(); i++) {
				var a = A.get(i);
				if (!a.isEmpty() && BitSetUtils.contains(b, a)) { // an empty a lies inside every b
					parts.add(i);
				}
			}
			// b must be EXACTLY the union of the parts: containment alone lets a b that also crosses
			// some other member of A through, and the resulting node then holds less than b.
			// And it must leave at least one member of A outside, otherwise b == union(A) and the
			// "refinement" only inserts a degree-2 node above all of iV's children.
			if (parts.size() >= 2 && parts.size() < A.size()
				&& BitSetUtils.union(parts.stream().map(A::get).toList()).equals(b)) {
				result.add(parts);
			}
		}
		return result;
	}
}
