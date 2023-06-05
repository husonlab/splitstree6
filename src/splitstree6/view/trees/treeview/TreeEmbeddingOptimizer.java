/*
 *  TreeEmbeddingOptimizer.java Copyright (C) 2023 Daniel H. Huson
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

package splitstree6.view.trees.treeview;

import jloda.graph.Edge;
import jloda.graph.Node;
import jloda.graph.algorithms.PQTree;
import jloda.phylo.LSAUtils;
import jloda.phylo.PhyloTree;
import jloda.util.BitSetUtils;
import jloda.util.CanceledException;
import jloda.util.IteratorUtils;
import jloda.util.progress.ProgressListener;
import splitstree6.view.trees.tanglegram.optimize.LSATree;

import java.util.*;

/**
 * attempt to layout rooted network using pq-tree
 * Daniel Huson, 5.2023
 * todo: this needs more work
 */
public class TreeEmbeddingOptimizer {
	public static void apply(PhyloTree tree, ProgressListener progress) throws CanceledException {
		if (tree.isReticulated()) {
			LSATree.computeNodeLSAChildrenMap(tree);
			var bestSize = 0;
			var bestOrder = new ArrayList<Integer>();
			var taxa = BitSetUtils.asBitSet(tree.getTaxa());
			progress.setMaximum(taxa.cardinality());
			for (var t : BitSetUtils.members(taxa)) { // is this really necessary?
				var count = 0;
				var pqTree = new PQTree(taxa);
				var lsaClusters = collectAllLSAClusters(tree);
				for (var cluster : lsaClusters) {
					if (cluster.get(t))
						count += (pqTree.accept(BitSetUtils.minus(taxa, cluster)) ? 1 : 0);
					else
						count += (pqTree.accept(cluster) ? 1 : 0);
				}
				var hardwiredClusters = collectAllHardwiredClusters(tree);
				hardwiredClusters.removeAll(lsaClusters);
				// order these clusters?
				for (var cluster : hardwiredClusters) {
					count += (pqTree.accept(cluster) ? 1 : 0);
				}
				if (count > bestSize)
					bestOrder = pqTree.extractAnOrdering();
				progress.incrementProgress();
			}

			// System.err.println("Cycle: " + StringUtils.toString(bestOrder, " "));

			var tax2pos = new int[bestOrder.size() + 1];
			int pos = 1;
			for (var t : bestOrder) {
				tax2pos[t] = pos++;
			}


			// label each node by the smallest position below and then sort out edges accordingly
			try (var smallestBelow = tree.newNodeIntArray()) {
				tree.postorderTraversal(tree.getRoot(), v -> {
					var smallest = tax2pos.length + 1;
					for (var t : tree.getTaxa(v))
						smallest = Math.min(smallest, tax2pos[t]);
					for (var w : v.children()) {
						smallest = Math.min(smallest, smallestBelow.get(w));
					}
					smallestBelow.set(v, smallest);
				});


				for (var v : tree.nodes()) {
					var edges = IteratorUtils.asList(v.inEdges());
					edges.addAll(IteratorUtils.asStream(v.outEdges()).sorted(Comparator.comparingInt(a -> smallestBelow.get(a.getTarget()))).toList());
					v.rearrangeAdjacentEdges(edges);
				}

				if (false) {
					tree.postorderTraversal(v -> {
						if (v.isLeaf()) {
							System.err.println(tree.getLabel(v) + " (" + tree.getTaxon(v) + "):pos=" + tax2pos[tree.getTaxon(v)]);
						}
					});
				}
			}
			try (var smallestBelow = tree.newNodeIntArray()) {
				LSAUtils.postorderTraversalLSA(tree, tree.getRoot(), v -> {
					var smallest = tax2pos.length + 1;
					for (var t : tree.getTaxa(v))
						smallest = Math.min(smallest, tax2pos[t]);
					for (var w : tree.lsaChildren(v)) {
						smallest = Math.min(smallest, smallestBelow.get(w));
					}
					smallestBelow.set(v, smallest);
				});

				for (var v : tree.nodes()) {
					var lsaChildren = IteratorUtils.asList(tree.lsaChildren(v));
					if (!IteratorUtils.asSet(lsaChildren).equals(IteratorUtils.asSet(v.children()))) {
						//System.err.println("Before (v=" + v + "): " + StringUtils.toString(lsaChildren.stream().mapToInt(smallestBelow::get).toArray(), " "));
						lsaChildren.sort(Comparator.comparingInt(smallestBelow::get));
						tree.getLSAChildrenMap().put(v, lsaChildren);
						//System.err.println("After (v=" + v + "): " + StringUtils.toString(lsaChildren.stream().mapToInt(smallestBelow::get).toArray(), " "));
					}
				}

				if (false) {
					LSAUtils.postorderTraversalLSA(tree, tree.getRoot(), v -> {
						if (v.isLeaf()) {
							System.err.println("LSA: " + tree.getLabel(v) + " (" + tree.getTaxon(v) + "):pos=" + tax2pos[tree.getTaxon(v)]);
						}
					});
				}
			}
		}
	}

	/**
	 * collects all LSA contained in the tree.
	 */
	public static Set<BitSet> collectAllLSAClusters(PhyloTree tree) {
		var clusters = new HashSet<BitSet>();
		collectAllLSAClustersRec(tree, tree.getRoot(), clusters);
		return clusters;
	}

	public static BitSet collectAllLSAClustersRec(PhyloTree tree, Node v, HashSet<BitSet> clusters) {
		var set = BitSetUtils.asBitSet(tree.getTaxa(v));
		for (var w : tree.getLSAChildrenMap().get(v)) {
			set.or(collectAllLSAClustersRec(tree, w, clusters));
		}
		clusters.add(set);
		return set;
	}


	/**
	 * collects all hardwired clusters contained in the tree.
	 */
	public static Set<BitSet> collectAllHardwiredClusters(PhyloTree tree) {
		var clusters = new HashSet<BitSet>();
		collectAllHardwiredClustersRec(tree, tree.getRoot(), clusters);
		return clusters;
	}

	public static BitSet collectAllHardwiredClustersRec(PhyloTree tree, Node v, HashSet<BitSet> clusters) {
		var set = BitSetUtils.asBitSet(tree.getTaxa(v));
		for (Edge f : v.outEdges()) {
			var w = f.getTarget();
			set.or(collectAllHardwiredClustersRec(tree, w, clusters));
		}
		clusters.add(set);
		return set;
	}
}
