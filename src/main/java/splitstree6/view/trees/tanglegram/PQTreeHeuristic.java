/*
 * PQTreeHeuristic.java Copyright (C) 2025 Daniel H. Huson
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

package splitstree6.view.trees.tanglegram;

import jloda.graph.DAGTraversals;
import jloda.graph.Node;
import jloda.graph.algorithms.PQTree;
import jloda.phylo.PhyloTree;
import jloda.util.BitSetUtils;
import jloda.util.IteratorUtils;
import jloda.util.SetUtils;
import splitstree6.utils.TreesUtils;

import java.util.*;
import java.util.stream.Collectors;

public class PQTreeHeuristic {
	public static boolean reorderUsingPQTree(PhyloTree network1, Map<Node, List<Node>> childMap1, PhyloTree network2, Map<Node, List<Node>> childMap2) {
		var taxaOnLeaves1 = new BitSet();
		network1.nodeStream().filter(v -> v.isLeaf() && network1.hasTaxa(v)).forEach(v -> taxaOnLeaves1.set(network1.getTaxon(v)));
		var taxaOnLeaves2 = new BitSet();
		network2.nodeStream().filter(v -> v.isLeaf() && network2.hasTaxa(v)).forEach(v -> taxaOnLeaves2.set(network2.getTaxon(v)));
		var commonTaxa = BitSetUtils.intersection(taxaOnLeaves1, taxaOnLeaves2);

		var pqtree = new PQTree(commonTaxa);

		var offered = 0;
		var accepted = 0;

		if (false) {
			for (var cluster : TreesUtils.collectAllHardwiredClusters(network1)) {
				cluster.and(commonTaxa);
				offered++;
				if (pqtree.accept(cluster))
					accepted++;
			}
			for (var cluster : TreesUtils.collectAllHardwiredClusters(network2)) {
				cluster.and(commonTaxa);
				offered++;
				if (!pqtree.accept(cluster))
					accepted++;
			}

		} else {
			var clusters1 = TreesUtils.collectAllHardwiredClusters(network1).stream().map(c -> BitSetUtils.intersection(c, commonTaxa)).filter(c -> c.cardinality() >= 2).collect(Collectors.toSet());
			var clusters2 = TreesUtils.collectAllHardwiredClusters(network2).stream().map(c -> BitSetUtils.intersection(c, commonTaxa)).filter(c -> c.cardinality() >= 2).collect(Collectors.toSet());

			var intersection = IteratorUtils.asList(SetUtils.intersection(clusters1, clusters2));
			for (var cluster : intersection) {

				offered++;
				if (pqtree.accept(cluster))
					accepted++;
			}
			var symDiff = IteratorUtils.asList(SetUtils.symmetricDifference(clusters1, clusters2));
			for (var cluster : symDiff) {
				offered++;
				if (pqtree.accept(cluster))
					accepted++;
			}
		}

		var ordering = pqtree.extractAnOrdering();
		var taxonRankMap = new HashMap<Integer, Integer>();
		for (var r = 0; r < ordering.size(); r++) {
			taxonRankMap.put(ordering.get(r), r);
		}

		sortByRank(network1, childMap1, commonTaxa, taxonRankMap);
		sortByRank(network2, childMap2, commonTaxa, taxonRankMap);

		System.err.printf("PQ-tree presort: %d offered, %d accepted%n", offered, accepted);

		return offered == accepted;
	}

	public static void sortByRank(PhyloTree network, Map<Node, List<Node>> childMap, BitSet commonTaxa, Map<Integer, Integer> taxonRankMap) {
		try (var nodeLowestMap = network.newNodeIntArray()) {
			DAGTraversals.postOrderTraversal(network.getRoot(), childMap::get, v -> {
				var children = childMap.get(v);
				if (children.isEmpty() || v.isLeaf()) {
					if (network.hasTaxa(v)) {
						var t = network.getTaxon(v);
						if (commonTaxa.get(t)) {
							nodeLowestMap.put(v, taxonRankMap.get(t));
						}
					}
				} else {
					var av = 0;
					var count = 0;
					var childrenToSort = new ArrayList<Node>();
					for (var child : children) {
						if (nodeLowestMap.get(child) != null) { // ignore unsortable children
							childrenToSort.add(child);
							av += nodeLowestMap.get(child);
							count++;
						}
					}
					if (count > 0)
						nodeLowestMap.put(v, av / count);
					childrenToSort.sort(Comparator.comparingInt(nodeLowestMap::get));
					var pos = 0;
					for (int i = 0; i < children.size(); i++) {
						var child = children.get(i);
						if (nodeLowestMap.get(child) != null) // ignore unsortable children
							children.set(i, childrenToSort.get(pos++));
					}
				}
			});
		}
	}
}
