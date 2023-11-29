/*
 * HasseDiagram.java Copyright (C) 2023 Daniel H. Huson
 *
 * (Some files contain contributions from other authors, who are then mentioned separately.)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package splitstree6.autumn;

import jloda.graph.Node;
import jloda.phylo.PhyloTree;
import jloda.util.BitSetUtils;

import java.util.Arrays;
import java.util.BitSet;

/**
 * compute a Hasse diagram
 * Daniel Huson, 4.2009
 */
public class HasseDiagram {
	/**
	 * construct the Hasse diagram for a set of clusters
	 *
	 */
	public static PhyloTree constructHasse(BitSet[] clusters) {
		Arrays.sort(clusters, Cluster.getComparatorByDecreasingSize());
		var tree = new PhyloTree();

		// build the diagram
		var root = tree.newNode();
		tree.setRoot(root);
		tree.setLabel(root, "" + new Cluster(Cluster.extractTaxa(clusters)));
		tree.setInfo(root, new Cluster());

		var cardinality = new int[clusters.length];
		var nodes = new Node[clusters.length];

		for (var i = 0; i < clusters.length; i++) {
			cardinality[i] = clusters[i].cardinality();
			nodes[i] = tree.newNode();
			tree.setLabel(nodes[i], "" + clusters[i]);
			tree.setInfo(nodes[i], clusters[i]);
		}

		for (var i = 0; i < clusters.length; i++) {
			var cluster = clusters[i];

			if (nodes[i].getInDegree() == 0) {
				tree.newEdge(root, nodes[i]);
			}

			var covered = new BitSet();

			for (var j = i + 1; j < clusters.length; j++) {
				if (cardinality[j] < cardinality[i]) {
					var subCluster = clusters[j];
					if (BitSetUtils.contains(cluster, subCluster) && !BitSetUtils.contains(covered, subCluster)) {
						tree.newEdge(nodes[i], nodes[j]);
						covered.or(subCluster);
						// if (covered.size() == size[i]) break;
					}
				}
			}
		}
		return tree;
	}
}
