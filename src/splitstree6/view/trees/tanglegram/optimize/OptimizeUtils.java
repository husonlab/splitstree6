/*
 * OptimizeUtils.java Copyright (C) 2022 Daniel H. Huson
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

package splitstree6.view.trees.tanglegram.optimize;


import jloda.graph.Edge;
import jloda.graph.Node;
import jloda.graph.NodeIntArray;
import jloda.graph.NodeSet;
import jloda.graph.algorithms.Dijkstra;
import jloda.phylo.PhyloTree;
import jloda.util.BitSetUtils;
import splitstree6.data.parts.ASplit;

import java.util.*;

/**
 * stores methods that are of general use and not only applicable for tanglegrams
 * <p/>
 * Celine Scornavacca, Franziska Zickmann 7.2010
 */
public class OptimizeUtils {
    /**
     * computes the number of crossings
     *
     * @return crossingNum
     */

    public static int computeCrossingNum(List<String> v1, List<String> v2) {
        int crossingNum = 0;

        Map<String, Integer> v2_m = new HashMap<>();
        for (int i = 0; i < v2.size(); i++)
            v2_m.put(v2.get(i), i);

        for (int i = 0; i < v1.size() - 1; i++) {
            String first_v1 = v1.get(i);
            if (v2_m.get(first_v1) != null) {    // otherwise null pointer exc with if taxa sets unequal
                int first_v2 = v2_m.get(first_v1);
                for (int j = i + 1; j < v1.size(); j++) {
                    String second_v1 = v1.get(j);
                    if (v2_m.get(second_v1) != null) {
                        int second_v2 = v2_m.get(second_v1);
                        if ((i < j && first_v2 > second_v2) || (i > j && first_v2 < second_v2))
                            crossingNum++;
                    }

                }
            }

        }
        return crossingNum;

    }


    /**
     * compute the crossings in case of many-to-many connections and/or different taxon sets
     *
     * @param v1
     * @param v2
     * @param taxCon
     * @return
     */

    public static int compCrossingsMany2Many(List<String> v1, List<String> v2, Map<String, List<String>> taxCon) {
        int crossingNum = 0;

        Map<String, Integer> v2_m = new HashMap<>();
        for (int i = 0; i < v2.size(); i++) {
            v2_m.put(v2.get(i), i);
        }

        for (int i = 0; i < v1.size() - 1; i++) {
            String first_v1 = v1.get(i);
            List<String> connectList1 = taxCon.get(first_v1);
            if (connectList1 != null) {
                for (String partner1 : connectList1) {
                    if (v2_m.get(partner1) != null) {
                        int first_v2 = v2_m.get(partner1);
                        for (int j = i + 1; j < v1.size(); j++) {
                            String second_v1 = v1.get(j);
                            List<String> connectList2 = taxCon.get(second_v1);
                            if (connectList2 != null) {
                                for (String partner2 : connectList2) {
                                    if (v2_m.get(partner2) != null) {
                                        int second_v2 = v2_m.get(partner2);
                                        if ((i < j && first_v2 > second_v2) || (i > j && first_v2 < second_v2)) {
                                            crossingNum++;
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        return crossingNum;
    }

    /**
     * get the order of taxa concerning the lsa tree
     *
     * @param tree
     * @param v
     * @param leavesList
     * @return
     */

    public static void getLsaOrderRec(PhyloTree tree, Node v, List<String> leavesList) {
        if (tree.computeSetOfLeaves().contains(v)) {
            leavesList.add(tree.getLabel(v));
        } else {
            List<Node> lsaChildren = tree.getLSAChildrenMap().get(v);
            for (Node w : lsaChildren) {
                getLsaOrderRec(tree, w, leavesList);
            }
        }
    }


    public static List<String> adaptLSAorder(Node v, Node w, List<String> originalOrder, Map<Node, List<String>> node2leavesBelow) {

        final List<String> taxaBelowV = new LinkedList<>();
        OptimizeUtils.getLsaOrderRec((PhyloTree) v.getOwner(), v, taxaBelowV);

        //System.err.println("            taxOrderGen v" + taxaBelowV.toString());

        final List<String> taxaBelowW = new LinkedList<>();
        OptimizeUtils.getLsaOrderRec((PhyloTree) w.getOwner(), w, taxaBelowW);

        if ((taxaBelowV.size() != 0) && (taxaBelowW.size() != 0)) {
            //System.err.println("            taxOrderGen w" + taxOrderGen.toString());


            //System.err.println("originalOrder.size() " + originalOrder.size());

            List<String> newOrder = new LinkedList<>();
            List<String> list1 = node2leavesBelow.get(v);
            List<String> list2 = node2leavesBelow.get(w);

            // now fill the new list with the old one, but change places of the taxa below v and w

            boolean foundFirstSet = false;
            boolean foundSecondSet = false;
            boolean firstEncounter1 = true;
            boolean firstEncounter2 = true;

            for (String currTax : originalOrder) {
                if (list1.contains(currTax)) {
                    foundFirstSet = true;
                } else if (list2.contains(currTax)) {
                    foundSecondSet = true;
                } else {
                    newOrder.add(currTax);
                }

                if (foundFirstSet && firstEncounter1) {
                    // now add all taxa of the second and instead of the first set
                    newOrder.addAll(list2);
                    firstEncounter1 = false;
                }
                if (foundSecondSet && firstEncounter2) {
                    // now add all taxa of the first and instead of the second set
                    newOrder.addAll(list1);
                    firstEncounter2 = false;
                }
            }
            return newOrder;
            //System.err.println("newOrder.size() " + newOrder.size());
        } else
            return originalOrder;

    }

    /**
     * get the split system for a tanglegram, use taxon IDs of the Map from all trees
     *
     * @return split system for this tanglegram
     */
    public static ArrayList<ASplit> getSplitSystem(Set<Set<String>> clusters, Map<String, Integer> taxon2ID) {
		var splits = new ArrayList<ASplit>();
		final var activeTaxa = BitSetUtils.asBitSet(taxon2ID.values());
		for (var currCluster : clusters) {
			//System.err.println("currCluster " + currCluster);

			final var sideA = new BitSet();

			for (var taxonLabel : currCluster) {
				sideA.set(taxon2ID.get(taxonLabel));
			}

			if (sideA.cardinality() > 0) {
				final var sideB = BitSetUtils.minus(activeTaxa, sideA);
				if (sideB.cardinality() > 0) {
					final var split = new ASplit(sideA, sideB);

					//System.err.println("split " + split);

					if (!splits.contains(split)) {
						splits.add(split);
					}
				}
			}
		}
        return splits;
    }

    /**
     * initialize new matrix in first call, after that only add distances when called with new split system
     *
     * @param D
     * @param ntax
     * @param splits
     * @return new distance matrix
     */
    public static double[][] setMatForDiffSys(double[][] D, int ntax, ArrayList<ASplit> splits, boolean firstTree) {
        if (D == null) {
            D = new double[ntax][ntax];
            for (int i = 0; i < ntax; i++)
                for (int j = i + 1; j < ntax; j++) {
                    double weight = 0;
                    for (ASplit split : splits) {
                        if (split.separates(i + 1, j + 1))
                            weight++;
                    }
                    if (firstTree) {
                        D[i][j] = D[j][i] = (1000 * weight);
                    } else {
                        D[i][j] = D[j][i] = weight;
                    }
                }
        } else {
            for (int i = 0; i < ntax; i++)
                for (int j = i + 1; j < ntax; j++) {
                    double weight = 0;
                    for (ASplit split : splits) {
                        if (split.separates(i + 1, j + 1))
                            weight++;
                    }
                    D[i][j] = D[j][i] = D[i][j] + weight;
                }
        }
        return D;
    }

    /**
     * calculates all descending nodes of v, including v.
     */

    public static NodeSet getDescendingNodes(PhyloTree tree, Node v) {
        NodeSet nodes = new NodeSet(tree);
        nodes.add(v);
        getDescendingNodesRec(tree, v, nodes);
        return nodes;
    }

    public static NodeSet getDescendingNodesRec(PhyloTree tree, Node v, NodeSet nodes) {
        for (Edge f = v.getFirstOutEdge(); f != null; f = v.getNextOutEdge(f)) {
            Node w = f.getTarget();
            nodes.add(w);
            NodeSet tmpSet = getDescendingNodesRec(tree, w, nodes);
            for (Node aTmpSet : tmpSet) {
                nodes.add(aTmpSet);
            }
        }
        return nodes;
    }

    /**
     * get the taxon set of a tree/network plus fake taxa for DC
     *
     * @param tree
     * @return
     */
    public static Taxa getTaxaForTanglegram(PhyloTree tree) {
        var labels = new TreeSet<String>();
        for (Node v = tree.getFirstNode(); v != null; v = v.getNext()) {
            if (v.getOutDegree() == 0 && tree.getLabel(v) != null) {
                labels.add(tree.getLabel(v));
            } else if (v.getOutDegree() == 0 && tree.getLabel(v) == null) {
                tree.setLabel(v, "null" + tree.getId(v) + tree.getId(v.getFirstInEdge()));
            }
        }
        var taxa = new Taxa();
        for (var label : labels)
            taxa.add(label);
        return taxa;
    }

    /**
     * optimizes layout along the LSA tree
     *
     * @param tree
     * @param otherOrder
     * @param treeNum
     * @param taxConMap1
     * @param taxConMap2
     */
    public static void lsaOptimization(PhyloTree tree, List<String> otherOrder, int treeNum, Map<String, List<String>> taxConMap1, Map<String, List<String>> taxConMap2) {
        final Map<Node, List<String>> node2LsaLeavesBelow = new HashMap<>();
        final List<String> lsaOrderInLastOpti = new LinkedList<>();
        lsaOptimizationRec(tree, tree.getRoot(), otherOrder, treeNum, taxConMap1, taxConMap2, node2LsaLeavesBelow, lsaOrderInLastOpti);
    }

    /**
     * does swaps along the lsa tree (but only adapts order when doing a swap to reduce time consumption)
     *
     * @param tree
     * @param v
     * @param otherOrder
     * @param treeNum
     * @param taxConMap1 can be simply assigned null, only important for host parasite
     * @param taxConMap2
     */


    private static void lsaOptimizationRec(PhyloTree tree, Node v, List<String> otherOrder, int treeNum, Map<String, List<String>> taxConMap1,
                                           Map<String, List<String>> taxConMap2, Map<Node, List<String>> node2LsaLeavesBelow, List<String> lsaOrderInLastOpti) {
        for (Edge e : v.outEdges()) {
            Node next = e.getOpposite(v);
            lsaOptimizationRec(tree, next, otherOrder, treeNum, taxConMap1, taxConMap2, node2LsaLeavesBelow, lsaOrderInLastOpti);
        }

        // this is only to make it accessible by everyone:

        if (tree.getRoot().equals(v)) {
            lsaOrderInLastOpti.clear();
            getLsaOrderRec(tree, v, lsaOrderInLastOpti);
        }

        lsaOrderInLastOpti.clear();
        OptimizeUtils.getLsaOrderRec(tree, v, lsaOrderInLastOpti);


        // now check if we should swap somewhere to optimize

        List<Node> lsaChildren = tree.getLSAChildrenMap().get(v);

        if (lsaChildren.size() > 1) {

            //System.err.println("lsaChildren " + lsaChildren.size());

            final List<String> currentBestOrder = new LinkedList<>(lsaOrderInLastOpti);

            boolean stopBecauseLoop = false;
            boolean swapped;
            do {
                swapped = false;

                for (int o = 0; o < lsaChildren.size() - 1; o++) {
                    //System.err.println("o " + o);
                    //System.err.println("currentBestOrder " + currentBestOrder.toString());
                    int crossingBefore;

                    if (taxConMap1 == null) {
                        crossingBefore = OptimizeUtils.computeCrossingNum(lsaOrderInLastOpti, otherOrder);
                    } else {
                        if (treeNum == 0) {
                            crossingBefore = OptimizeUtils.compCrossingsMany2Many(lsaOrderInLastOpti, otherOrder, taxConMap1);
                        } else {
                            crossingBefore = OptimizeUtils.compCrossingsMany2Many(lsaOrderInLastOpti, otherOrder, taxConMap2);
                        }
                    }

                    Node temp1 = lsaChildren.get(o);
                    Node temp2 = lsaChildren.get(o + 1);
                    lsaChildren.set(o, temp2);
                    lsaChildren.set(o + 1, temp1);

                    lsaOrderInLastOpti = OptimizeUtils.adaptLSAorder(temp1, temp2, lsaOrderInLastOpti, node2LsaLeavesBelow);

                    //System.err.println("lsaOrderInLastOpti " + lsaOrderInLastOpti.toString());


                    if (currentBestOrder.equals(lsaOrderInLastOpti)) {
                        stopBecauseLoop = true;
                        break;
                    }

                    int crossingAfter;

                    if (taxConMap1 == null) {
                        crossingAfter = OptimizeUtils.computeCrossingNum(lsaOrderInLastOpti, otherOrder);
                    } else {
                        if (treeNum == 0) {
                            crossingAfter = OptimizeUtils.compCrossingsMany2Many(lsaOrderInLastOpti, otherOrder, taxConMap1);
                        } else {
                            crossingAfter = OptimizeUtils.compCrossingsMany2Many(lsaOrderInLastOpti, otherOrder, taxConMap2);
                        }
                    }


                    if (crossingBefore < crossingAfter) {
                        temp1 = lsaChildren.get(o);
                        temp2 = lsaChildren.get(o + 1);
                        lsaChildren.set(o, temp2);
                        lsaChildren.set(o + 1, temp1);

                        lsaOrderInLastOpti = OptimizeUtils.adaptLSAorder(temp1, temp2, lsaOrderInLastOpti, node2LsaLeavesBelow);

                    } else {
                        swapped = true;
                        if (crossingBefore > crossingAfter) {

                            currentBestOrder.clear();
                            currentBestOrder.addAll(lsaOrderInLastOpti);
                        }
                    }
                }

            } while (lsaChildren.size() != 2 && !stopBecauseLoop && swapped);
        }

        final List<String> newOrderTaxList = new LinkedList<>();
        getLsaOrderRec(tree, v, newOrderTaxList);

        if (newOrderTaxList.size() == 0) {                   // can happen if two reti children that assigned to other nodes
            for (Edge e : v.outEdges()) {
                Node next = e.getOpposite(v);
                final List<String> tempOrder = new LinkedList<>();
                getLsaOrderRec(tree, next, tempOrder);
                newOrderTaxList.addAll(tempOrder);
            }
            int newOrdSize = newOrderTaxList.size();
            int encounterOrd = 0;
            for (int i = 0; i < lsaOrderInLastOpti.size(); i++) {      // obtained newOrdList via network structure, have to adapt it to lsa ordering
                if (encounterOrd != 1) {
                    String tax = lsaOrderInLastOpti.get(i);
                    if (newOrderTaxList.contains(tax)) {
                        encounterOrd++;
                    }
                    if (encounterOrd == 1) {
                        newOrderTaxList.clear();
                        for (int j = 0; j < newOrdSize; j++) {
                            newOrderTaxList.add(lsaOrderInLastOpti.get(i + j));
                        }
                    }
                }
            }
        }

        node2LsaLeavesBelow.put(v, newOrderTaxList);   // assigns the order of taxa below v
    }

    /**
     * collects all clusters contained in the tree.
     */
    public static Set<Set<String>> collectAllHardwiredClusters(PhyloTree tree) {
        var clusters = new HashSet<Set<String>>();
        collectAllHardwiredClustersRec(tree, tree.getRoot(), clusters);
        return clusters;
    }

    public static Set<String> collectAllHardwiredClustersRec(PhyloTree tree, Node v, Set<Set<String>> clusters) {
        //reached a leave
        if (v.getOutDegree() == 0) {
            var set = new HashSet<String>();
            set.add(tree.getLabel(v));
            clusters.add(set);
            return set;
        }
        //recursion
        else {
            TreeSet<String> set = new TreeSet<String>();
            for (Edge f : v.outEdges()) {
                var w = f.getTarget();
                set.addAll(collectAllHardwiredClustersRec(tree, w, clusters));
            }
            clusters.add(set);
            return set;
        }
    }

    /**
     * compute the number of nodes present in the shortest path for each pair of leaves, considering the graph undirected
     *
     * @param graph
     * @return dist of shortest paths for each pairs of leaves
     */
    public static void computeNumberNodesInTheShortestPath(final PhyloTree graph, Map<String, Integer> taxon2ID, double[][] distMatrix) {
        var leaves = graph.computeSetOfLeaves();
        var distMatrixTemp = new int[graph.getNumberOfNodes()][graph.getNumberOfNodes()];   // temp matrix

        var idTemp = new NodeIntArray(graph);   //temp id used to fill distMatrixTemp
        var nN = 0;

        for (var tempNode : graph.nodes()) {
            idTemp.set(tempNode, nN);     //set temp id
            nN++;

        }

            /* Find the shortest path between source and and all other nodes of the DiRECTED PN.
            The values are saved in distMatrixTemp, dist is used to order the priorityQueue.
            */

        for (var source : graph.nodes()) {
            var idS = idTemp.getInt(source);
            //System.out.println("idS" + idS);

            //NodeArray predecessor = new NodeArray(graph);
            var dist = graph.newNodeDoubleArray();

            for (var v : graph.nodes()) {
                dist.put(v, 10000.0);
                var idV = idTemp.getInt(v);
                distMatrixTemp[idS][idV] = 10000;
                //predecessor.set(v, null);
            }
            dist.put(source, 0.0);
            distMatrixTemp[idS][idS] = 0;

            var priorityQueue = Dijkstra.newFullQueue(graph, dist);

            while (priorityQueue.isEmpty() == false) {
                var size = priorityQueue.size();
                var u = priorityQueue.first();

                priorityQueue.remove(u);

                if (priorityQueue.size() != size - 1)
                    throw new RuntimeException("remove u=" + u + " failed: size=" + size);

                var idU = idTemp.getInt(u);
                for (Edge e : u.outEdges()) {
                    var v = graph.getOpposite(u, e);
                    var idV = idTemp.getInt(v);
                    if (distMatrixTemp[idS][idV] > distMatrixTemp[idS][idU] + 1) {
                        //System.out.println("enter");
                        // priorty of v changes, so must re-and to queue:
                        priorityQueue.remove(v);
                        distMatrixTemp[idS][idV] = distMatrixTemp[idS][idU] + 1;
                        dist.put(v, distMatrixTemp[idS][idU] + 1.0);
                        priorityQueue.add(v);
                        //predecessor.set(v, u);
                    }
                }
            }
        }


            /* Find the shortest path between each pair of leaves (l1,l2) as the shortest
            dist(l1,intNode)+ dist(l2,intNode), where intNode is an internal node.
            The values are saved in distMatrix.
            */

        for (var source : leaves) {
            var idS = idTemp.getInt(source);
            int labelS = taxon2ID.get(graph.getLabel(source)); // Franzi uses values from 1 to n, me from 0 to n-1
            leaves.remove(source);
            for (Node target : leaves) {
                if (source != target) {
                    var idT = idTemp.getInt(target);
                    var labelT = taxon2ID.get(graph.getLabel(target)); // Same as above

                    //System.out.println("labelS " + labelS+ " labelT " + labelT);
                    var min = 1000000;
                    for (var node : graph.nodes()) {
                        var nodeID = idTemp.getInt(node);
                        if (distMatrixTemp[nodeID][idS] + distMatrixTemp[nodeID][idT] < min)
                            min = distMatrixTemp[nodeID][idS] + distMatrixTemp[nodeID][idT];
                    }

                    distMatrix[labelS][labelT] += min;   //update distMatrix
                    distMatrix[labelT][labelS] += min;
                }
            }
        }
    }
}


