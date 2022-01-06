/*
 * ConvexHull.java Copyright (C) 2021. Daniel H. Huson
 *
 * (Some code written by other authors, as named in code.)
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

package splitstree6.view.splits.layout.algorithms;

import jloda.graph.Edge;
import jloda.graph.EdgeSet;
import jloda.graph.Node;
import jloda.graph.NodeArray;
import jloda.phylo.PhyloSplitsGraph;
import jloda.util.CanceledException;
import jloda.util.progress.ProgressListener;
import splitstree6.algorithms.utils.PhyloGraphUtils;
import splitstree6.data.SplitsBlock;
import splitstree6.data.TaxaBlock;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Stack;
import java.util.TreeSet;

/**
 * applies the convex hull algorithm to build a split network from splits
 * Daniel Huson, 11.2017
 */
public class ConvexHull {
    /**
     * apply the algorithm to build a new graph
     *
     * @param progress
     * @param taxa
     * @param splits
     * @param splitsGraph
     */
    public static void apply(ProgressListener progress, TaxaBlock taxa, SplitsBlock splits, PhyloSplitsGraph splitsGraph) throws CanceledException {
        splitsGraph.clear();
        apply(progress, taxa, splits, splitsGraph, new BitSet());
    }

    /**
     * assume that some splits have already been processed and applies convex hull algorithm to remaining splits
     *
     * @param progress
     * @param taxaBlock
     * @param splits
     * @param graph
     * @param usedSplits
     */
    public static void apply(ProgressListener progress, TaxaBlock taxaBlock, SplitsBlock splits, PhyloSplitsGraph graph, BitSet usedSplits) throws CanceledException {

        if (usedSplits.cardinality() == splits.getNsplits())
            return; // all nodes have been processed
        //System.err.println("Running convex hull algorithm");

        progress.setTasks("Computing Splits Network", "Convex Hull algorithm");
		progress.setMaximum(splits.getNsplits());    //initialize maximum progress
        progress.setProgress(-1);        //set progress to 0

        try {
            if (graph.getNumberOfNodes() == 0) {
                Node startNode = graph.newNode();
                graph.addTaxon(startNode, 1);
                //graph.setLabel(startNode, taxa.getLabel(1));

                for (var i = 2; i <= taxaBlock.getNtax(); i++) {
                    //graph.setLabel(startNode, (graph.getLabel(startNode)+", "+taxa.getLabel(i)));
                    graph.addTaxon(startNode, i);
                }
            } else {
                for (var t = 1; t <= taxaBlock.getNtax(); t++) {
                    if (graph.getTaxon2Node(t) == null)
                        System.err.println("Internal error: incomplete taxa");
                }
                for (var e : graph.edges()) {
                    if (graph.getSplit(e) == 0)
                        System.err.println("Internal error: edge without split: " + e);
                }
            }

            final var order = getOrderToProcessSplitsIn(splits, usedSplits);

            //process one split at a time
            progress.setMaximum(order.length);    //initialize maximum progress
            progress.setProgress(0);

            for (var j : order) {
                progress.incrementProgress();

                final var currentSplitPartA = splits.get(j).getA();

                //is 0, if the node is member of convex hull for the "0"-side of the current split,
                //is 1, if the node is member of convex hull for the "1"-side of the current split,
                //is 2, if the node is member of both hulls
                var hulls = graph.newNodeIntArray();

                //here all found "critical" nodes are stored
                final var intersectionNodes = new ArrayList<Node>();

                final var splits1 = new BitSet();
                final var splits0 = new BitSet();

                //find splits, where taxa of side "0" of current split are divided
                for (var i = 1; i <= splits.getNsplits(); i++) {
                    if (!usedSplits.get(i)) continue;    //only splits already used must be regarded
                    if (splits.intersect2(j, false, i, true).cardinality() != 0 &&
                        splits.intersect2(j, false, i, false).cardinality() != 0)
                        splits0.set(i);
                    progress.checkForCancel();
                }

                //find splits, where taxa of side "1" of current split are divided
                for (var i = 1; i <= splits.getNsplits(); i++) {
                    progress.checkForCancel();

                    if (!usedSplits.get(i)) continue;    //only splits already used must be regarded

                    if (splits.intersect2(j, true, i, true).cardinality() != 0 &&
                        splits.intersect2(j, true, i, false).cardinality() != 0)
                        splits1.set(i);
                }

                //find startNodes

                Node start0 = null;
                Node start1 = null;

                for (var i = 1; i <= taxaBlock.getNtax(); i++) {
                    if (!currentSplitPartA.get(i)) {
                        start0 = graph.getTaxon2Node(i);
                    } else {
                        start1 = graph.getTaxon2Node(i);
                    }
                    if (start0 != null && start1 != null) break;
                }

                hulls.put(start0, 0);

                if (start0 == start1) {
                    hulls.put(start1, 2);
                    intersectionNodes.add(start1);
                } else
                    hulls.put(start1, 1);

                //construct the remainder of convex hull for split-side "0" by traversing all allowed (and reachable) edges (i.e. all edges in splits0)


                convexHullPath(graph, start0, graph.newEdgeSet(), hulls, splits0, intersectionNodes, 0);

                //construct the remainder of convex hull for split-side "1" by traversing all allowed (and reachable) edges (i.e. all edges in splits0)

                convexHullPath(graph, start1, graph.newEdgeSet(), hulls, splits1, intersectionNodes, 1);

                //first duplicate the intersection nodes, set an edge between each node and its duplicate and label new edges and nodes
                for (var v : intersectionNodes) {
                    final var v1 = graph.newNode();
                    final var e = graph.newEdge(v1, v);

                    graph.setSplit(e, j);
                    graph.setWeight(e, splits.get(j).getWeight());
                    graph.setLabel(e, "" + j);


                    final var vTaxa = new ArrayList<Integer>();
                    for (var t : graph.getTaxa(v)) {
                        vTaxa.add(t);
                    }
                    graph.clearTaxa(v);
                    for (var taxon : vTaxa) {
                        if (currentSplitPartA.get(taxon)) {
                            graph.addTaxon(v1, taxon);
                        } else {
                            graph.addTaxon(v, taxon);
                        }
                    }

                    //graph.setLabel(v, vlab);
                    //graph.setLabel(v1, v1lab);
                }

                //connect edges accordingly
                for (var v : intersectionNodes) {
                    progress.checkForCancel();
                    //find duplicated node of v (and their edge)
                    Node v1 = null;
                    Edge toV1;

                    for (toV1 = v.getFirstAdjacentEdge(); toV1 != null; toV1 = v.getNextAdjacentEdge(toV1)) {
                        if (graph.getSplit(toV1) == j) {
                            v1 = graph.getOpposite(v, toV1);
                            break;
                        }
                    }

                    //visit all edges of v and move or add edges
                    for (var consider : v.adjacentEdges()) {
                        progress.checkForCancel();

                        if (consider == toV1) continue;

                        Node w = graph.getOpposite(v, consider);

                        if (hulls.get(w) == -1) {
                        } else if (hulls.get(w) == 1) {        //node belongs to other side
                            Edge considerDup = graph.newEdge(v1, w);
                            graph.setLabel(considerDup, "" + graph.getSplit(consider));
                            graph.setSplit(considerDup, graph.getSplit(consider));
                            graph.setWeight(considerDup, graph.getWeight(consider));
                            graph.setAngle(considerDup, graph.getAngle(consider));
                            graph.deleteEdge(consider);
                        } else if (hulls.get(w) == 2) {  //node is in intersection
                            Node w1 = null;

                            for (Edge toW1 : w.adjacentEdges()) {
                                progress.checkForCancel();
                                if (graph.getSplit(toW1) == j) {
                                    w1 = graph.getOpposite(w, toW1);
                                    break;
                                }
                            }

                            if (v1 != null && v1.getCommonEdge(w1) == null) {
                                final var considerDup = graph.newEdge(v1, w1);
                                graph.setLabel(considerDup, "" + graph.getSplit(consider));

                                graph.setWeight(considerDup, graph.getWeight(consider));
                                graph.setSplit(considerDup, graph.getSplit(consider));
                            }
                        }
                    }
                }
                //add split to usedSplits
                usedSplits.set(j, true);
            }

            progress.setProgress(-1);
            graph.nodeStream().forEach(n -> graph.setLabel(n, null));
            PhyloGraphUtils.addLabels(taxaBlock, graph);

            final var seen = new BitSet();
            for (var e : graph.edges()) {
                var s = graph.getSplit(e);
                if (s > 0 && !seen.get(s)) {
                    seen.set(s);
                    graph.setLabel(e, "" + s);
                } else
                    graph.setLabel(e, null);
            }
        } catch (CanceledException ex) {
            graph.clear();
            throw ex;
        }
        graph.edgeStream().forEach(e -> graph.setLabel(e, null));
    }//end apply

    /**
     * convex hull path
     */
    private static void convexHullPath(PhyloSplitsGraph g, Node start, EdgeSet visited, NodeArray<Integer> hulls, BitSet allowedSplits, ArrayList<Node> intersectionNodes, int side) {
        final var todo = new Stack<Node>();
        todo.push(start);

        while (todo.size() > 0) {
            final var v = todo.pop();

            for (var f : v.adjacentEdges()) {
                final var w = g.getOpposite(v, f);

                if (!visited.contains(f) && allowedSplits.get(g.getSplit(f))) {
                    //if(hulls.get(m)==side) continue;
                    visited.add(f);

                    if (hulls.get(w) == null) {
                        hulls.put(w, side);
                        todo.push(w);
                    } else if (hulls.get(w) == Math.abs(side - 1)) {
                        hulls.put(w, 2);
                        intersectionNodes.add(w);
                        todo.push(w);
                    }
                }
            }
        }
    }

    /**
     * computes a good order in which to process the splits.
     * Currently orders splits by increasing size
     *
     * @param splits
     * @param usedSplits
     * @return order
     */
    private static int[] getOrderToProcessSplitsIn(SplitsBlock splits, BitSet usedSplits) {
        final var set = new TreeSet<Integer>();
        for (var s = 1; s <= splits.getNsplits(); s++) {
            if (!usedSplits.get(s)) {
                var pair = 10000 * splits.get(s).size() + s;
                set.add(pair);
            }
        }

        final int[] order = new int[set.size()];
        var i = 0;
        for (var value : set) {
            var size = value / 10000;
            var id = value - size * 10000;
            // System.err.println("pair "+id+" size "+size);
            order[i++] = id;
        }
        return order;
    }
}
