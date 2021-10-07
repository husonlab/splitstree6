/*
 * TreesUtilities.java Copyright (C) 2021. Daniel H. Huson
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

package splitstree6.utils;

import jloda.graph.Edge;
import jloda.graph.Node;
import jloda.phylo.PhyloTree;
import jloda.util.Basic;
import jloda.util.BitSetUtils;
import splitstree6.data.TaxaBlock;
import splitstree6.data.TreesBlock;
import splitstree6.data.parts.ASplit;

import java.util.BitSet;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

/**
 * some computations on trees
 *
 * @author huson Date: 29-Feb-2004
 * Daria Evseeva,23.01.2017.
 */
public class TreesUtilities {
    /**
     * gets all taxa in tree, if node to taxa mapping has been set
     *
     * @param tree
     * @return all taxa in tree
     */
    public static BitSet getTaxa(PhyloTree tree) {
        final BitSet taxa = new BitSet();
        for (Node v : tree.nodes()) {
            for (Integer t : tree.getTaxa(v)) {
                taxa.set(t);
            }
        }
        return taxa;
    }

    /**
     * determines whether every pair of taxa occur together in some tree
     *
     * @param taxa
     * @param trees
     * @return returns true, if every pair of taxa occur together in some  tree
     */
    static public boolean hasAllPairs(TaxaBlock taxa, TreesBlock trees) {
        int numPairs = (taxa.getNtax() * (taxa.getNtax() - 1)) / 2;

        BitSet seen = new BitSet();

        for (int which = 1; which <= trees.getNTrees(); which++) {
            BitSet support = //trees.getSupport(taxa, which).getBits();
                    //---
                    new BitSet();
            PhyloTree tree = trees.getTrees().get(which);
            for (var v : tree.nodes()) {
                var label = v.getLabel();
                if (label != null)
                    support.set(taxa.indexOf(label)); //todo test???
            }
            //---
            for (int i = support.nextSetBit(1); i > 0; i = support.nextSetBit(i + 1)) {
                for (int j = support.nextSetBit(i + 1); j > 0; j = support.nextSetBit(j + 1)) {
                    seen.set(i + taxa.getNtax() * j, true);
                    if (seen.cardinality() == numPairs)
                        return true; // seen all possible pairs
                }
            }
        }
        return false;
    }


    /**
     * are there any labeled internal nodes and are all such labels numbers?
     *
     * @param tree
     * @return true, if some internal nodes labeled and all labeled by numbers
     */
    public static boolean hasNumbersOnInternalNodes(PhyloTree tree) {
        boolean hasNumbers = false;
        for (Node v = tree.getFirstNode(); v != null; v = v.getNext()) {
            if (v.getOutDegree() != 0 && v.getInDegree() != 0) {
                String label = tree.getLabel(v);
                if (label != null) {
                    if (Basic.isDouble(label))
                        hasNumbers = true;
                    else
                        return false;
                }
            }
        }
        return hasNumbers;
    }


    /**
     * reinterpret an numerical label of an internal node as the confidence associated with the incoming edge
     *
     * @param tree
     */
    public static void changeNumbersOnInternalNodesToEdgeConfidencies(PhyloTree tree) {
        for (Node v = tree.getFirstNode(); v != null; v = v.getNext()) {
            if (v.getOutDegree() != 0 && v.getInDegree() == 1) {
                String label = tree.getLabel(v);
                if (label != null) {
                    if (Basic.isDouble(label)) {
                        tree.setConfidence(v.getFirstInEdge(), Basic.parseDouble(label));
                        tree.setLabel(v, null);
                    }
                }
            }
        }
    }

    /**
     * are there any labeled leaf nodes and are all such labels numbers?
     *
     * @param tree
     * @return true, if some leaves nodes labeled by numbers
     */
    public static boolean hasNumbersOnLeafNodes(PhyloTree tree) {
        boolean hasNumbers = false;
        for (Node v = tree.getFirstNode(); v != null; v = v.getNext()) {
            if (v.getOutDegree() == 0) {
                final String label = tree.getLabel(v);
                if (label != null) {
                    if (Basic.isDouble(label))
                        hasNumbers = true;
                    else
                        return false;
                }
            }
        }
        return hasNumbers;
    }

    /**
     * change numerical leaf label to string
     *
     * @param tree
     */
    public static void changeNumbersOnLeafNodesToLabels(final TaxaBlock taxaBlock, PhyloTree tree) {
        for (Node v = tree.getFirstNode(); v != null; v = v.getNext()) {
            if (v.getOutDegree() == 0) {
                final String label = tree.getLabel(v);
                if (label != null) {
                    if (Basic.isInteger(label)) {
                        tree.setLabel(v, taxaBlock.getLabel(Basic.parseInt(label)));
                    }
                }
            }
        }
    }


    /**
     * compute all the splits in a tree
     *
     * @param taxaInTree the taxa in the tree, if null, is computed
     * @param tree
     * @param splits     the resulting splits are added here
     * @return bit set of taxa found in tree
     */
    public static BitSet computeSplits(BitSet taxaInTree, final PhyloTree tree, final Collection<ASplit> splits) {
        if (taxaInTree == null)
            taxaInTree = getTaxa(tree);

        if (tree.getRoot() == null) {
            // choose an arbitrary leaf
            for (Node v : tree.nodes()) {
                if (tree.hasTaxa(v) && v.getDegree() == 1) {
                    System.err.println("Internal error: tree not rooted, but should be");
                    tree.setRoot(v);
                    break;
                }
            }
        }

        if (tree.getRoot() != null) // otherwise empty tree
            tree2splitsRec(tree.getRoot(), null, tree, taxaInTree, splits);
        return taxaInTree;
    }

    /**
     * recursively extract all splits
     *
     * @param v
     * @param e
     * @param tree
     * @param taxaInTree
     * @param splits
     * @return
     */
    private static BitSet tree2splitsRec(final Node v, final Edge e, final PhyloTree tree, final BitSet taxaInTree, final Collection<ASplit> splits) {
        final BitSet vAndBelowTaxa = BitSetUtils.asBitSet(tree.getTaxa(v));

        for (Edge f : v.adjacentEdges()) {
            if (f != e) {
                final Node w = tree.getOpposite(v, f);
                final BitSet wAndBelowTaxa = tree2splitsRec(w, f, tree, taxaInTree, splits);

                // take care at root of tree,
                // if root has degree 2, then root will give rise to only
                //  one split, with weight that equals
                // the sum of the two weights.. make sure we only produce
                // one split by using the edge that has lower id
                boolean ok = true;
                double weight = tree.getWeight(f);
                double confidence = tree.getConfidence(f);
                Node root = tree.getRoot();
                if (root != null && (f.getSource() == root || f.getTarget() == root) && root.getDegree() == 2 && !tree.hasTaxa(v)) {
                    // get the other  edge adjacent to root:
                    final Edge g;
                    if (root.getFirstAdjacentEdge() != f)
                        g = root.getFirstAdjacentEdge();
                    else
                        g = root.getLastAdjacentEdge();
                    if (f.getId() < g.getId()) {
                        weight = tree.getWeight(f) + tree.getWeight(g);
                        confidence = 0.5 * (tree.getConfidence(f) + tree.getConfidence(g));
                    } else
                        ok = false;
                }

                if (ok) {
                    final BitSet B = new BitSet();
                    B.or(taxaInTree);
                    B.andNot(wAndBelowTaxa);
                    final ASplit newSplit = new ASplit(wAndBelowTaxa, B, weight, confidence);
                    newSplit.setConfidence((float) confidence);
                    splits.add(newSplit);
                }
                vAndBelowTaxa.or(wAndBelowTaxa);
            }
        }
        return vAndBelowTaxa;
    }

    /**
     * compute the total weight of a tree
     *
     * @param tree
     * @return total weight
     */
    public static double computeTotalWeight(PhyloTree tree) {
        double weight = 0;
        for (Edge e : tree.edges()) {
            weight += tree.getWeight(e);
        }
        return weight;
    }

    /**
     * computes the induced tree
     *
     * @param oldTaxonId2NewTaxonId - old to new mapping, where removed taxa map to 0
     * @param originalTree
     * @return induced tree
     */
    public static PhyloTree computeInducedTree(int[] oldTaxonId2NewTaxonId, PhyloTree originalTree) {
        final PhyloTree inducedTree = new PhyloTree();
        inducedTree.copy(originalTree);

        final List<Node> toRemove = new LinkedList<>(); // initially, set to all leaves that have lost their taxa

        // change taxa:
        for (Node v : inducedTree.nodes()) {
            if (inducedTree.getNumberOfTaxa(v) > 0) {
                BitSet taxa = new BitSet();
                for (Integer t : inducedTree.getTaxa(v)) {
                    if (oldTaxonId2NewTaxonId[t] > 0)
                        taxa.set(oldTaxonId2NewTaxonId[t]);
                }
                inducedTree.clearTaxa(v);
                if (taxa.cardinality() == 0)
                    toRemove.add(v);
                else {
                    for (Integer t : BitSetUtils.members(taxa)) {
                        inducedTree.addTaxon(v, t);
                    }
                }
            }
        }

        // delete all nodes that don't belong to the induced tree
        while (toRemove.size() > 0) {
            final Node v = toRemove.remove(0);
            for (Edge e : v.inEdges()) {
                final Node w = e.getSource();
                if (w.getOutDegree() == 1 && inducedTree.getNumberOfTaxa(w) == 0) {
                    toRemove.add(w);
                }
            }
            if (inducedTree.getRoot() == v) {
                inducedTree.deleteNode(v);
                inducedTree.setRoot(null);
                return null; // tree has completely disappeared...
            }
            inducedTree.deleteNode(v);
        }

        // remove path from original root to new root:

        Node root = inducedTree.getRoot();
        while (inducedTree.getNumberOfTaxa(root) == 0 && root.getOutDegree() == 1) {
            root = root.getFirstOutEdge().getTarget();
            inducedTree.deleteNode(inducedTree.getRoot());
            inducedTree.setRoot(root);
        }

        // remove all divertices
        final List<Node> diVertices = new LinkedList<>();
        for (Node v : inducedTree.nodes()) {
            if (v.getInDegree() == 1 && v.getOutDegree() == 1)
                diVertices.add(v);
        }
        for (Node v : diVertices) {
            inducedTree.delDivertex(v);
        }

        return inducedTree;
    }
}
