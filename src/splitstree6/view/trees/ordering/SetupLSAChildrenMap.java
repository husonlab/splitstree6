/*
 *  Copyright (C) 2018. Daniel H. Huson
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

/*
 *  SetupLSAChildrenMap.java Copyright (C) 2021 Daniel H. Huson
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
import jloda.graph.NodeArray;
import jloda.graph.NodeIntArray;
import jloda.phylo.LSAUtils;
import jloda.phylo.PhyloTree;
import jloda.util.Counter;
import jloda.util.IteratorUtils;

import java.util.ArrayList;

/**
 * sets up the LSA children map if tree contains a rooted network
 */
public class SetupLSAChildrenMap {
	/**
	 * setup LSA children map if required
	 *
	 * @param tree phylo tree
	 */
	public static void apply(PhyloTree tree) {
		if (tree.getRoot() == null || tree.getNumberSpecialEdges() == 0) {
			tree.getLSAChildrenMap().clear();
			return; // if this is a tree, don't need LSA guide tree
		}

		//System.err.println("Maintaining current embedding");

		try (NodeArray<Node> reticulationLSAMap = tree.newNodeArray()) {
			if (false && isTransferNetwork(tree)) {
				tree.getLSAChildrenMap().clear();
				for (Node v : tree.nodes()) {
					var children = new ArrayList<Node>(v.getOutDegree());
					for (var e : v.outEdges()) {
						if (!tree.isSpecial(e) || tree.getWeight(e) > 0) {
							children.add(e.getTarget());
							reticulationLSAMap.put(e.getTarget(), e.getSource());
						}
					}
					tree.getLSAChildrenMap().put(v, children);

				}
			} else // must be combining network
			{
				tree.getLSAChildrenMap().putAll(LSAUtils.computeLSAChildrenMap(tree, reticulationLSAMap));
				// maps reticulate nodes to lsa nodes
				// compute preorder numbering of all nodes
				try (var ordering = tree.newNodeIntArray(); var visited = tree.newNodeSet()) {
					var counter = new Counter(0);
					tree.preorderTraversal(tree.getRoot(), v -> ordering.put(v, (int) counter.incrementAndGet()));
					reorderLSAChildren(tree, ordering);
				}
			}
		}

		// retNode2GuideParent is required to implement optimization algorithms of LSA ordering
	}

	/**
	 * reorder LSA children of each node to reflect the topological embedding of the network
	 *
	 * @param tree
	 * @param ordering
	 */
	private static void reorderLSAChildren(PhyloTree tree, final NodeIntArray ordering) {
		// System.err.println("------ v="+v);
		for (Node v : tree.nodes()) {
			var children = tree.getLSAChildrenMap().get(v);
			if (children != null) {
				var list = new ArrayList<>(children);
				list.sort((v1, v2) -> {
					if (ordering.getInt(v1) < ordering.getInt(v2))
						return -1;
					else if (ordering.getInt(v1) > ordering.getInt(v2))
						return 1;
					if (v1.getId() != v2.getId())
						System.err.println("ERROR in sort");
					// different nodes must have different ordering values!
					return 0;
				});
				tree.getLSAChildrenMap().put(v, list);
			}
		}
	}

	/**
	 * does network look like a transfer network?
	 *
	 * @param tree
	 * @return true, if is transfer network
	 */
	public static boolean isTransferNetwork(PhyloTree tree) {
		return IteratorUtils.asStream(tree.specialEdges()).anyMatch(e -> tree.getWeight(e) != 0);
	}
}
