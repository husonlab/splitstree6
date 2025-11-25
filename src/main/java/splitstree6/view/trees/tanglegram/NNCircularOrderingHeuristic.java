/*
 * NNCircularOrderingHeuristic.java Copyright (C) 2025 Daniel H. Huson
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

import jloda.graph.Node;
import jloda.phylo.PhyloTree;
import jloda.util.BitSetUtils;
import splitstree6.algorithms.distances.distances2splits.neighbornet.NeighborNetCycleSplitsTree4;

import java.util.*;

import static splitstree6.view.trees.tanglegram.PQTreeHeuristic.sortByRank;

/**
 * reimplementation of NN heuristic for tanglegrams
 * Daniel Huson, 11.2025
 */
public class NNCircularOrderingHeuristic {
	/**
	 * compute a circular ordering using neighbor net and apply to LCA children
	 */
	public static void apply(PhyloTree network1, Map<Node, List<Node>> childMap1, PhyloTree network2, Map<Node, List<Node>> childMap2) {
		var taxaOnLeaves1 = new BitSet();
		network1.nodeStream().filter(v -> v.isLeaf() && network1.hasTaxa(v)).forEach(v -> taxaOnLeaves1.set(network1.getTaxon(v)));
		var taxaOnLeaves2 = new BitSet();
		network2.nodeStream().filter(v -> v.isLeaf() && network2.hasTaxa(v)).forEach(v -> taxaOnLeaves2.set(network2.getTaxon(v)));
		var commonTaxa = BitSetUtils.intersection(taxaOnLeaves1, taxaOnLeaves2);

		var clusters = new HashSet<>(collectClusters(network1, commonTaxa));
		for (var cluster : collectClusters(network2, commonTaxa)) {
			var complement = BitSetUtils.minus(commonTaxa, cluster);
			if (!clusters.contains(complement))
				clusters.add(cluster);
		}

		var ntax = Math.max(BitSetUtils.max(taxaOnLeaves1), BitSetUtils.max(taxaOnLeaves2));

		// todo: map to taxa 1...count, run NNet and then map back to original numbers
		var distances = setupDistances(ntax, clusters);
		var ordering = NeighborNetCycleSplitsTree4.compute(ntax, distances);

		// we restrict the ordering to the common taxa.
		if (commonTaxa.cardinality() < taxaOnLeaves1.cardinality() || commonTaxa.cardinality() < taxaOnLeaves2.cardinality()) {
			var diff = BitSetUtils.union(BitSetUtils.minus(taxaOnLeaves1, commonTaxa), BitSetUtils.minus(taxaOnLeaves2, commonTaxa));
			var newOrdering = new int[diff.cardinality() + 1];

			int index = 0;
			for (int t : ordering) {
				if (t > 0 && diff.get(t)) {
					newOrdering[++index] = t;
				}
			}
			ordering = newOrdering;
		}

		var taxonRankMap = new HashMap<Integer, Integer>();
		for (var r = 0; r < ordering.length; r++) {
			if (ordering[r] != 0)
				taxonRankMap.put(ordering[r], r);
		}

		sortByRank(network1, childMap1, commonTaxa, taxonRankMap);
		sortByRank(network2, childMap2, commonTaxa, taxonRankMap);
	}

	private static Set<BitSet> collectClusters(PhyloTree network, BitSet taxa) {
		var clusters = new HashSet<BitSet>();
		collectClustersRec(network, network.getRoot(), clusters, taxa);
		return clusters;
	}

	public static BitSet collectClustersRec(PhyloTree network, Node v, HashSet<BitSet> clusters, BitSet taxa) {
		var cluster = new BitSet();
		if (v.isLeaf()) {
			if (taxa.get(network.getTaxon(v))) {
				cluster.set(network.getTaxon(v));
			}
		} else {
			for (var f : v.outEdges()) {
				var w = f.getTarget();
				cluster.or(collectClustersRec(network, w, clusters, taxa));
			}
		}
		if (!cluster.isEmpty())
			clusters.add(cluster);
		return cluster;
	}

	/**
	 * compute distances as number of separating clusters
	 *
	 * @return new distance matrix
	 */
	public static double[][] setupDistances(int ntax, Set<BitSet> clusters) {
		var distances = new double[ntax][ntax];

		for (var i = 0; i < ntax; i++)
			for (var j = i + 1; j < ntax; j++) {
				var weight = 0.0;
				for (var cluster : clusters) {
					if (cluster.get(i) != cluster.get(j)) {
						weight++;
					}
				}
				distances[i][j] = distances[j][i] = weight;
			}
		return distances;
	}
}
