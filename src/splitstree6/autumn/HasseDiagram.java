/*
 * HasseDiagram.java Copyright (C) 2022 Daniel H. Huson
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

import java.util.BitSet;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * compute a Hasse diagram
 * Daniel Huson, 4.2009
 */
public class HasseDiagram {
	/**
	 * construct the Hasse diagram for a set of clusters
	 *
	 */
	public static PhyloTree constructHasse(Cluster[] clusters) {
		// make clusters unique:
		Set<Cluster> set = new HashSet<>();
		Collections.addAll(set, clusters);
		// sort
		clusters = Cluster.getClustersSortedByDecreasingCardinality(set.toArray(new Cluster[0]));

		PhyloTree tree = new PhyloTree();

		// build the diagram
		Node root = tree.newNode();
		tree.setRoot(root);
		tree.setLabel(root, "" + new Cluster(Cluster.extractTaxa(clusters)));
		tree.setInfo(root, new Cluster());

		int[] cardinality = new int[clusters.length];
		Node[] nodes = new Node[clusters.length];

		for (int i = 0; i < clusters.length; i++) {
			cardinality[i] = clusters[i].cardinality();
			nodes[i] = tree.newNode();
			tree.setLabel(nodes[i], "" + clusters[i]);
			tree.setInfo(nodes[i], clusters[i]);
		}

		for (int i = 0; i < clusters.length; i++) {
			BitSet cluster = clusters[i];

			if (nodes[i].getInDegree() == 0) {
				tree.newEdge(root, nodes[i]);
			}

			BitSet covered = new BitSet();

			for (int j = i + 1; j < clusters.length; j++) {
				if (cardinality[j] < cardinality[i]) {
					BitSet subCluster = clusters[j];
					if (Cluster.contains(cluster, subCluster) && !Cluster.contains(covered, subCluster)) {
						tree.newEdge(nodes[i], nodes[j]);
						covered.or(subCluster);
						// if (covered.cardinality() == cardinality[i]) break;
					}
				}
			}
		}
		return tree;
	}
}
