/*
 *  Utilities.java Copyright (C) 2024 Daniel H. Huson
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
package splitstree6.autumn;


import jloda.graph.Node;
import jloda.phylo.PhyloTree;
import splitstree6.data.TaxaBlock;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

/**
 * utilities
 * Daniel Huson, 7.2011
 */
public class Utilities {

	/**
	 * get the number of reticulations in a cluster network
	 *
	 * @return number of reticulate nodes
	 */
	public static int getNumberOfReticulationsInClusterNetwork(PhyloTree tree1, PhyloTree tree2) throws IOException {
		var allTaxa = new TaxaBlock();
		AutumnUtilities.extractTaxa(1, tree1, allTaxa);
		AutumnUtilities.extractTaxa(2, tree2, allTaxa);

		var clusters = extractClusters(allTaxa, tree1, tree2);
		PhyloTree hasseDiagram = HasseDiagram.constructHasse(clusters);

		return (int) hasseDiagram.nodeStream().filter(v -> v.getInDegree() > 1).count();
	}

	/**
	 * extract the set of clusters from the two given trees
	 *
	 * @return set of clusters, each cluster a BitSet
	 */
	static public Cluster[] extractClusters(TaxaBlock allTaxa, PhyloTree tree1, PhyloTree tree2) {
		var result = new HashSet<Cluster>();

		var taxa = extractClustersRec(tree1, tree1.getRoot(), allTaxa, result);
		taxa.or(extractClustersRec(tree2, tree2.getRoot(), allTaxa, result));
		assert (taxa.cardinality() == allTaxa.size());

		return result.toArray(new Cluster[0]);
	}

	/**
	 * recursively find all clusters in a tree
	 *
	 * @return all taxa on or below v
	 */
	private static Cluster extractClustersRec(PhyloTree tree, Node v, TaxaBlock taxa, Set<Cluster> clusters) {
		var clusterV = new Cluster();
		var label = tree.getLabel(v);
		if (label != null && label.length() > 0 && taxa.indexOf(label) != -1)
			clusterV.set(taxa.indexOf(label));

		for (var e = v.getFirstOutEdge(); e != null; e = v.getNextOutEdge(e)) {
			var w = e.getTarget();
			var clusterW = extractClustersRec(tree, w, taxa, clusters);
			assert (clusterW.cardinality() > 0);
			clusters.add(new Cluster(clusterW, tree.getWeight(e), tree.getConfidence(e), 0));
			clusters.add(clusterW);
			clusterV.or(clusterW);
		}
		return clusterV;
	}
}
