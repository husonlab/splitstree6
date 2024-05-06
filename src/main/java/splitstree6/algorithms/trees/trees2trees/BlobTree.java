/*
 *  TreeSelector.java Copyright (C) 2024 Daniel H. Huson
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

package splitstree6.algorithms.trees.trees2trees;

import jloda.phylo.PhyloTree;
import jloda.phylo.algorithms.ClusterPoppingAlgorithm;
import jloda.util.progress.ProgressListener;
import splitstree6.algorithms.IFilter;
import splitstree6.data.TaxaBlock;
import splitstree6.data.TreesBlock;
import splitstree6.splits.TreesUtils;
import splitstree6.xtra.kernelize.ClusterIncompatibilityGraph;

import java.util.BitSet;
import java.util.HashMap;
import java.util.List;

/**
 * blob trees
 * Daniel Huson, 4/2024
 */
public class BlobTree extends Trees2Trees implements IFilter {


	@Override
	public String getShortDescription() {
		return "Extracts the blob tree from a rooted network (by contracting all non-trivial bi-connected components).";
	}

	@Override
	public String getCitation() {
		return "Huson et al 2012;DH Huson, R. Rupp and C. Scornavacca, Phylogenetic Networks, Cambridge, 2012.";
	}

	@Override
	public void compute(ProgressListener progress, TaxaBlock taxaBlock, TreesBlock inputData, TreesBlock outputData) {
		outputData.clear();
		outputData.setPartial(inputData.isPartial());
		outputData.setRooted(true);
		outputData.setReticulated(false);

		for (var network : inputData.getTrees()) {
			if (network.isReticulated()) {
				var tree = new PhyloTree();
				computeBlobTree(network, tree);
				for (var v : tree.nodes()) {
					if (tree.hasTaxa(v)) {
						tree.setLabel(v, taxaBlock.getLabel(tree.getTaxon(v)));
					}
				}
				outputData.getTrees().add(tree);
			} else
				outputData.getTrees().add(network);
		}
	}

	@Override
	public boolean isApplicable(TaxaBlock taxa, TreesBlock datablock) {
		return datablock.isReticulated() && datablock.isRooted();
	}

	@Override
	public boolean isActive() {
		return true;
	}

	public static void computeBlobTree(PhyloTree network, PhyloTree tree) {
		// setup cluster weight map
		var clusterWeightMap = new HashMap<BitSet, Double>();
		try (var nodeClusterMap = TreesUtils.extractClusters(network)) {
			for (var entry : nodeClusterMap.entrySet()) {
				var v = entry.getKey();
				var cluster = entry.getValue();
				if (v.getInDegree() == 1) {
					clusterWeightMap.put(cluster, network.getWeight(v.getFirstInEdge()));
				} else {
					clusterWeightMap.put(cluster, 0.0);
				}
			}
		}
		// determine clusters to keep:
		var incompatibiltyGraph = ClusterIncompatibilityGraph.apply(List.of(network));
		var clusters = incompatibiltyGraph.nodeStream().filter(v -> v.getDegree() == 0).map(v -> (BitSet) v.getInfo()).toList();

		ClusterPoppingAlgorithm.apply(clusters, tree);
		try (var nodeClusterMap = TreesUtils.extractClusters(tree)) {
			for (var v : nodeClusterMap.keySet()) {
				if (v.getInDegree() == 0) {
					var cluster = nodeClusterMap.get(v);
					tree.setWeight(v.getFirstInEdge(), clusterWeightMap.get(cluster));
				}
			}
		}
	}
}
