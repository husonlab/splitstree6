/*
 *  LayoutUnoptimized.java Copyright (C) 2024 Daniel H. Huson
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

package splitstree6.view.trees.tanglegram.optimize_old;

import jloda.graph.Node;
import jloda.graph.NodeIntArray;
import jloda.graph.NodeSet;
import jloda.phylo.PhyloTree;

import java.util.ArrayList;
import java.util.TreeSet;


/**
 * this algorithm set the LSA children to reflect the trivial embedding
 * Daniel Huson, 7.2009
 */
public class LayoutUnoptimized {
	/**
	 * compute standard embedding
	 */
	public void apply(PhyloTree tree) {
		if (tree.getRoot() == null || tree.getNumberReticulateEdges() == 0) {
			tree.getLSAChildrenMap().clear();
			return; // if this is a tree, don't need LSA guide tree
		}

		//System.err.println("Maintaining current embedding");

		if (isAllReticulationsAreTransfers(tree)) {
			tree.getLSAChildrenMap().clear();
			for (var v : tree.nodes()) {
				var children = new ArrayList<Node>();
				for (var e : v.outEdges()) {
					if (!tree.isReticulateEdge(e) || tree.isTransferAcceptorEdge(e)) {
						children.add(e.getTarget());
					}
				}
				tree.getLSAChildrenMap().put(v, children);

			}
		} else // must be combining network
		{
			LSATree.computeNodeLSAChildrenMap(tree); // maps reticulate nodes to lsa nodes
			// compute preorder numbering of all nodes
			var ordering = new NodeIntArray(tree);
			computePreOrderNumberingRec(tree, tree.getRoot(), new NodeSet(tree), ordering, 0);
			reorderLSAChildren(tree, ordering);
		}
	}

	/**
	 * recursively compute the pre-ordering numbering of all nodes below v
	 *
	 * @return last number assigned
	 */
	private int computePreOrderNumberingRec(PhyloTree tree, Node v, NodeSet visited, NodeIntArray ordering, int number) {
		if (!visited.contains(v)) {
			visited.add(v);
			ordering.set(v, ++number);

			// todo: use this to label by order:
			if (false) {
				if (tree.getLabel(v) == null)
					tree.setLabel(v, "o" + number);
				else
					tree.setLabel(v, tree.getLabel(v) + "_o" + number);
			}

			for (var w : v.children()) {
				number = computePreOrderNumberingRec(tree, w, visited, ordering, number);
			}
		}
		return number;
	}

	/**
	 * reorder LSA children of each node to reflect the topological embedding of the network
	 */
	private void reorderLSAChildren(PhyloTree tree, final NodeIntArray ordering) {
		// System.err.println("------ v="+v);
		for (var v : tree.nodes()) {
			var children = tree.getLSAChildrenMap().get(v);
			if (children != null) {
				if (false) {
					System.err.println("LSA children old for v=" + v.getId() + ":");
					for (Node u : children) {
						System.err.println(" " + u.getId() + " order: " + ordering.get(u));
					}
				}
				var sorted = new TreeSet<Node>((v1, v2) -> {
					if (ordering.getInt(v1) < ordering.getInt(v2))
						return -1;
					else if (ordering.getInt(v1) > ordering.getInt(v2))
						return 1;
					if (v1.getId() != v2.getId())
						System.err.println("ERROR in sort");
					// different nodes must have different ordering values!
					return 0;
				});
				sorted.addAll(children);
				tree.getLSAChildrenMap().put(v, new ArrayList<>(sorted));
				if (false) {
					System.err.println("LSA children new for v=" + v.getId() + ":");
					for (Node u : children) {
						System.err.println(" " + u.getId() + " order: " + ordering.get(u));
					}
				}
			}
		}
	}

	/**
	 * does network only contain transfers?
	 *
	 * @return true, if is reticulate network that only contains
	 */
	public static boolean isAllReticulationsAreTransfers(PhyloTree tree) {
		return tree.edgeStream().noneMatch(e -> !tree.isTransferEdge(e) && !tree.isTransferAcceptorEdge(e));
	}
}

