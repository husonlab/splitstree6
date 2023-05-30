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
import jloda.phylo.PhyloTree;
import jloda.util.BitSetUtils;
import jloda.util.CanceledException;
import jloda.util.progress.ProgressListener;
import splitstree6.layout.tree.LSATree;
import splitstree6.view.trees.tanglegram.optimize.EmbedderForOrderPrescribedNetwork;

import java.util.*;

/**
 * attempt to layout rooted network using pq-tree
 * Daniel Huson, 5.2023
 * todo: this needs more work
 */
public class TreeEmbeddingOptimizer {
	public static void apply(PhyloTree tree, ProgressListener progress) throws CanceledException {
		var bestSize = 0;
		var bestOrder = new ArrayList<Integer>();
		var taxa = BitSetUtils.asBitSet(tree.getTaxa());
		progress.setMaximum(taxa.cardinality());
		for (var t : BitSetUtils.members(taxa)) {
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
			for (var cluster : hardwiredClusters) {
				count += (pqTree.accept(cluster) ? 1 : 0);
			}
			if (count > bestSize)
				bestOrder = pqTree.extractAnOrdering();
			progress.incrementProgress();
		}
		var tax2pos = new float[bestOrder.size() + 1];
		int pos = 1;
		for (var t : bestOrder) {
			tax2pos[t] = pos++;
		}

		// todo: need to reimplement a simpler algorithm for rordering based on taxon order.

		var node2pos = new HashMap<Node, Float>();
		for (var v : tree.nodes()) {
			if (tree.hasTaxa(v))
				node2pos.put(v, tax2pos[tree.getTaxon(v)]);
		}
		EmbedderForOrderPrescribedNetwork.apply(tree, node2pos);
	}

	/**
	 * collects all LSA contained in the tree.
	 */
	public static Set<BitSet> collectAllLSAClusters(PhyloTree tree) {
		LSATree.computeNodeLSAChildrenMap(tree);
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
