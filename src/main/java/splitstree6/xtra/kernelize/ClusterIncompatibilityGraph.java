/*
 *  ClusterIncompatibilityGraph.java Copyright (C) 2024 Daniel H. Huson
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

package splitstree6.xtra.kernelize;

import jloda.graph.Graph;
import jloda.graph.Node;
import jloda.phylo.PhyloTree;
import splitstree6.compute.autumn.Cluster;
import splitstree6.utils.TreesUtils;

import java.util.BitSet;
import java.util.Collection;
import java.util.HashMap;

/**
 * algorithm for computing the cluster incompatibility graph for rooted trees
 * Daniel Huson, 2.204
 */
public class ClusterIncompatibilityGraph {
	/**
	 * computes the cluster incompatibility graph for a collection of trees of rooted networks
	 * in graph, v.getInfo() contains cluster as bit set
	 * in graph, v.getData() contains indices of all trees that have the cluster, as bit set
	 *
	 * @param trees input trees or networks
	 * @return graph
	 */
	public static Graph apply(Collection<PhyloTree> trees) {
		var graph = new Graph();
		// v.getInfo() contains cluster as bit set
		// v.getData() contains indices of all trees that have the cluster, as bit set
		var clusterNodeMap = new HashMap<BitSet, Node>();
		// setup nodes:
		var treeId = 0;
		for (var tree : trees) {
			treeId++;
			try (var clusterMap = TreesUtils.extractClusters(tree)) {
				for (var cluster : clusterMap.values()) {
					var v = clusterNodeMap.computeIfAbsent(cluster, graph::newNode);
					var which = (BitSet) v.getData();
					if (which == null) {
						which = new BitSet();
						v.setData(which);
					}
					which.set(treeId);
				}
			}
		}
		// setup edges:
		for (var v = graph.getFirstNode(); v != null; v = v.getNext()) {
			var cv = (BitSet) v.getInfo();
			for (var w = v.getNext(); w != null; w = w.getNext()) {
				var cw = (BitSet) w.getInfo();
				if (Cluster.incompatible(cv, cw)) {
					graph.newEdge(v, w);
				}
			}
		}
		return graph;
	}

	public static Graph applyClusters(Collection<BitSet> clusters) {
		var graph = new Graph();
		for (var cluster : clusters) {
			graph.newNode().setInfo(cluster);
		}
		for (var v = graph.getFirstNode(); v != null; v = v.getNext()) {
			var cv = (BitSet) v.getInfo();
			for (var w = v.getNext(); w != null; w = w.getNext()) {
				var cw = (BitSet) w.getInfo();
				if (Cluster.incompatible(cv, cw)) {
					graph.newEdge(v, w);
				}
			}
		}
		return graph;
	}

}
