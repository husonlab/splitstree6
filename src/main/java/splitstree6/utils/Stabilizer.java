/*
 *  Stabilizer.java Copyright (C) 2024 Daniel H. Huson
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

package splitstree6.utils;

import jloda.graph.algorithms.PQTree;
import jloda.phylo.PhyloTree;
import jloda.util.BitSetUtils;
import jloda.util.ExecuteInParallel;
import jloda.util.IteratorUtils;
import jloda.util.ProgramExecutorService;
import splitstree6.splits.TreesUtils;

import java.util.*;

/**
 * attempts to stabilize the layout of taxa across multiple rooted trees
 */
public class Stabilizer {
	private int[] tax2pos;

	/**
	 * setup an ordering that will hopefully work
	 *
	 * @param trees input trees
	 */
	public void setup(Collection<PhyloTree> trees) {
		var taxa = new BitSet();
		trees.forEach(t -> taxa.or(BitSetUtils.asBitSet(t.getTaxa())));
		if (taxa.cardinality() > 0) {
			var pqTree = new PQTree(taxa);
			var clusterCountMap = new HashMap<BitSet, Integer>();
			trees.forEach(tree -> {
				for (var cluster : TreesUtils.collectAllHardwiredClusters(tree)) {
					if (cluster.cardinality() > 1)
						clusterCountMap.put(cluster, clusterCountMap.getOrDefault(cluster, 0) + 1);
				}
			});
			clusterCountMap.entrySet().stream().sorted((a, b) -> -Integer.compare(a.getValue(), b.getValue())).map(Map.Entry::getKey).forEach(pqTree::accept);

			var bestOrder = pqTree.extractAnOrdering();
			tax2pos = new int[bestOrder.size() + 1];
			int pos = 1;
			for (var t : bestOrder) {
				tax2pos[t] = pos++;
			}
		} else
			tax2pos = null;
	}

	/**
	 * apply the ordering to a tree, rearranging the order of children to attempt to attain the setup ordering
	 *
	 * @param tree phylo tree
	 */
	public void apply(PhyloTree tree) {
		if (tax2pos != null) {
			try (var smallestBelow = tree.newNodeIntArray()) {
				tree.postorderTraversal(tree.getRoot(), v -> !smallestBelow.containsKey(v), v -> {
					if (v.getInDegree() == 1) {
						var e = v.getFirstInEdge();
						var smallest = Integer.MAX_VALUE;
						for (var t : tree.getTaxa(v))
							smallest = Math.min(smallest, tax2pos[t]);
						for (var w : v.children()) {
							smallest = Math.min(smallest, smallestBelow.get(w));
						}
						smallestBelow.set(v, smallest);
					}
				});

				for (var v : tree.nodes()) {
					var outEdges = IteratorUtils.asList(v.outEdges());
					outEdges.sort(Comparator.comparing(e -> smallestBelow.get(e.getTarget())));
					var allEdges = new ArrayList<>(IteratorUtils.asList(v.inEdges()));
					allEdges.addAll(outEdges);
					v.rearrangeAdjacentEdges(allEdges);
				}
			}
		}
	}

	/**
	 * apply the ordering to a collection of trees, rearranging the order of children to attempt to attain the setup ordering
	 */
	public void apply(Collection<PhyloTree> trees) throws Exception {
		ExecuteInParallel.apply(trees, this::apply, ProgramExecutorService.getNumberOfCoresToUse());
	}
}
