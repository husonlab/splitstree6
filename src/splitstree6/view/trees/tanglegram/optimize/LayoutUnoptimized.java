/*
 *  LayoutUnoptimized.java Copyright (C) 2022 Daniel H. Huson
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

package splitstree6.view.trees.tanglegram.optimize;

import jloda.graph.Edge;
import jloda.graph.Node;
import jloda.graph.NodeIntArray;
import jloda.graph.NodeSet;
import jloda.phylo.PhyloTree;
import jloda.util.progress.ProgressListener;

import java.util.*;


/**
 * this algorithm set the LSA children to reflect the trivial embedding
 * Daniel Huson, 7.2009
 */
public class LayoutUnoptimized {
    /**
     * compute standard embedding
     *
     * @param tree
     * @param progressListener
     */
    public void apply(PhyloTree tree, ProgressListener progressListener) {
        if (tree.getRoot() == null || tree.getNumberReticulateEdges() == 0) {
            tree.getLSAChildrenMap().clear();
            return; // if this is a tree, don't need LSA guide tree
        }

        //System.err.println("Maintaining current embedding");

        if (isAllReticulationsAreTransfers(tree)) {
            tree.getLSAChildrenMap().clear();
            for (Node v = tree.getFirstNode(); v != null; v = tree.getNextNode(v)) {
                List<Node> children = new LinkedList<Node>();
                for (Edge e = v.getFirstOutEdge(); e != null; e = v.getNextOutEdge(e)) {
                    if (!tree.isReticulatedEdge(e) || tree.getWeight(e) > 0) {
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
     * @param v
     * @param visited
     * @param ordering
     * @param number
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

            for (Edge e = v.getFirstOutEdge(); e != null; e = v.getNextOutEdge(e)) {
                Node w = e.getTarget();
                number = computePreOrderNumberingRec(tree, w, visited, ordering, number);
            }
        }
        return number;
    }

    /**
     * reorder LSA children of each node to reflect the topological embedding of the network
     *
     * @param tree
     * @param ordering
     */
    private void reorderLSAChildren(PhyloTree tree, final NodeIntArray ordering) {
        // System.err.println("------ v="+v);
        for (Node v = tree.getFirstNode(); v != null; v = tree.getNextNode(v)) {
            List<Node> children = tree.getLSAChildrenMap().get(v);
            if (children != null) {
                if (false) {
                    System.err.println("LSA children old for v=" + v.getId() + ":");
                    for (Node u : children) {
                        System.err.println(" " + u.getId() + " order: " + ordering.get(u));
                    }
                }
                SortedSet<Node> sorted = new TreeSet<Node>(new Comparator<Node>() {

                    public int compare(Node v1, Node v2) {
                        if (ordering.getInt(v1) < ordering.getInt(v2))
                            return -1;
                        else if (ordering.getInt(v1) > ordering.getInt(v2))
                            return 1;
                        if (v1.getId() != v2.getId())
                            System.err.println("ERROR in sort");
                        // different nodes must have different ordering values!
                        return 0;
                    }
                });
                sorted.addAll(children);
                List<Node> list = new LinkedList<Node>();
                list.addAll(sorted);
                tree.getLSAChildrenMap().put(v, list);
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
     * @param tree
     * @return true, if is reticulate network that only contains
     */
    public static boolean isAllReticulationsAreTransfers(PhyloTree tree) {
        var hasTransferReticulation = false;
        var hasNonTransferReticulation = false;

        for (var v : tree.nodes()) {
            if (v.getInDegree() > 1) {
                var transfer = false;
                for (var e : v.inEdges()) {
                    if (tree.getWeight(e) != 0)
                        transfer = true;
                }
                if (!transfer)
                    hasNonTransferReticulation = true;
                else
                    hasTransferReticulation = true;
            }
        }
        return hasTransferReticulation && !hasNonTransferReticulation;
    }
}

