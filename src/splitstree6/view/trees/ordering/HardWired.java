/*
 *  HardWired.java Copyright (C) 2021 Daniel H. Huson
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

package splitstree6.view.trees.ordering;

import jloda.graph.Node;
import jloda.phylo.PhyloTree;
import jloda.util.BitSetUtils;
import splitstree6.data.parts.ASplit;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.Map;

/**
 * extracts clusters and splits from a rooted tree or network, in the hardwired-sense
 * Daniel Huson, 12.2021
 */
public class HardWired {
	/**
	 * compute all splits contained in a hardwired network
	 *
	 * @param taxa only consider these taxa
	 * @param tree tree or rooted network
	 * @return splits
	 */
	public static ArrayList<ASplit> computeSplits(BitSet taxa, PhyloTree tree) {
		var splits = new ArrayList<ASplit>();
		var clusterWeightMap = collectAllHardwiredClusters(tree);
		for (var cluster : clusterWeightMap.keySet()) {
			var a = BitSetUtils.intersection(cluster, taxa);
			if (!a.isEmpty()) {
				var b = BitSetUtils.minus(taxa, a);
				if (!b.isEmpty()) {
					splits.add(new ASplit(a, b, clusterWeightMap.get(cluster)));
				}
			}
		}
		return splits;
	}

	/**
	 * collects all hardwired clusters contained in a rooted tree or network
	 */
	public static Map<BitSet, Double> collectAllHardwiredClusters(PhyloTree tree) {
		var clusterWeightMap = new HashMap<BitSet, Double>();
		collectAllHardwiredClustersRec(tree, tree.getRoot(), clusterWeightMap);
		return clusterWeightMap;
	}

	private static BitSet collectAllHardwiredClustersRec(PhyloTree tree, Node v, Map<BitSet, Double> clusterWeightMap) {
		var taxa = new BitSet();
		var weight = v.inEdgesStream(false).mapToDouble(e -> tree.getWeight()).average().orElse(0.001);

		if (v.isLeaf()) {
			taxa.or(BitSetUtils.asBitSet(tree.getTaxa(v)));
		} else {
			for (var w : v.children()) {
				taxa.or(collectAllHardwiredClustersRec(tree, w, clusterWeightMap));
			}
		}
		if (clusterWeightMap.containsKey(taxa))
			clusterWeightMap.put(taxa, clusterWeightMap.get(taxa) + weight);
		else
			clusterWeightMap.put(taxa, weight);
		return taxa;
	}

	public record Cluster(BitSet members, double weight) {
	}
}
