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

import java.util.*;

/**
 * refines trees mutually: each tree that is not fully resolved gets the clusters of the other trees that fit into it.
 * <p>
 * A cluster of another tree fits into a tree if it is compatible with all clusters of the tree, that is, if it is the
 * union of two or more, and not all, children of one node. Where two clusters fit that do not fit together, the one
 * that occurs in more of the input trees is taken, then the larger one, then the first in the order of
 * {@link BitSetUtils#compare}. The clusters considered are those of the input trees, so that the result does not
 * depend on the order in which the trees are given, and applying the method to its result changes nothing.
 * <p>
 * Until 11.2026 the method made one pass over the pairs of trees and refined a node only by the children of a node
 * with the same cluster in the other tree; its result depended on the order of the trees.
 * Daniel Huson, 4.2026
 */
public class TreeMutualRefinement {

	/**
	 * apply refinement and return list of modified trees
	 *
	 * @param trees0 input trees
	 * @return refined output trees, copies, in the order of the input
	 */
	public static List<PhyloTree> apply(List<PhyloTree> trees0) {
		var trees = new ArrayList<>(trees0.stream().map(PhyloTree::new).toList());

		// setup maps:
		var clusterNodeMaps = new ArrayList<Map<BitSet, Node>>();
		var nodeClusterMaps = new ArrayList<Map<Node, BitSet>>();
		var taxaOfTree = new ArrayList<BitSet>();
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
			taxaOfTree.add(tree.getRoot() != null ? nodeClusterMap.get(tree.getRoot()) : new BitSet());
		}

		// the clusters of the input trees with two or more taxa, and the trees that have each
		var clusterTreesMap = new HashMap<BitSet, BitSet>();
		for (var i = 0; i < trees.size(); i++) {
			for (var cluster : clusterNodeMaps.get(i).keySet()) {
				if (cluster.cardinality() >= 2 && !cluster.equals(taxaOfTree.get(i)))
					clusterTreesMap.computeIfAbsent(cluster, k -> new BitSet()).set(i);
			}
		}
		// in the order in which they are tried: in more trees first, then larger, then a fixed order
		var candidates = new ArrayList<>(clusterTreesMap.keySet());
		candidates.sort((a, b) -> {
			var compare = -Integer.compare(clusterTreesMap.get(a).cardinality(), clusterTreesMap.get(b).cardinality());
			if (compare == 0)
				compare = -Integer.compare(a.cardinality(), b.cardinality());
			if (compare == 0)
				compare = BitSetUtils.compare(a, b);
			return compare;
		});

		var count = 0;
		for (var i = 0; i < trees.size(); i++) {
			var iTree = trees.get(i);
			if (iTree.getRoot() == null)
				continue;
			var iClusterNodeMap = clusterNodeMaps.get(i);
			var iNodeClusterMap = nodeClusterMaps.get(i);
			for (var cluster : candidates) {
				if (iClusterNodeMap.containsKey(cluster) || !BitSetUtils.contains(taxaOfTree.get(i), cluster))
					continue;
				// the lowest node that has all taxa of the cluster below it
				var iV = iTree.getRoot();
				var descend = true;
				while (descend) {
					descend = false;
					for (var w : iV.children()) {
						if (BitSetUtils.contains(iNodeClusterMap.get(w), cluster)) {
							iV = w;
							descend = true;
							break;
						}
					}
				}
				// a tree that has the cluster must have all taxa below that node, otherwise it says nothing about
				// where the cluster lies among them
				var below = iNodeClusterMap.get(iV);
				var sources = clusterTreesMap.get(cluster);
				var known = false;
				for (var j = sources.nextSetBit(0); !known && j >= 0; j = sources.nextSetBit(j + 1)) {
					known = BitSetUtils.contains(taxaOfTree.get(j), below);
				}
				if (!known)
					continue;
				// the cluster fits if it is the union of some of the children
				var inside = new ArrayList<Node>();
				var union = new BitSet();
				var fits = true;
				for (var w : iV.children()) {
					var childCluster = iNodeClusterMap.get(w);
					if (BitSetUtils.contains(cluster, childCluster)) {
						inside.add(w);
						union.or(childCluster);
					} else if (childCluster.intersects(cluster)) {
						fits = false;
						break;
					}
				}
				if (!fits || inside.size() < 2 || inside.size() == iV.getOutDegree() || !union.equals(cluster))
					continue;

				var refinementNode = iTree.newNode();
				iClusterNodeMap.put(union, refinementNode); // a set owned by this tree, not the one of the other tree
				iNodeClusterMap.put(refinementNode, union);
				var refinementEdge = iTree.newEdge(iV, refinementNode);
				if (iTree.hasEdgeWeights())
					iTree.setWeight(refinementEdge, 0.0);
				if (iTree.hasEdgeConfidences())
					iTree.setConfidence(refinementEdge, 0.0);
				for (var child : inside) {
					var oldInEdge = child.getFirstInEdge();
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
		if (false) System.err.println("Mutual refinement: " + count);
		return trees;
	}
}
