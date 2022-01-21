/*
 * SubtreeReduction.java Copyright (C) 2022 Daniel H. Huson
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
package splitstree6.autumn.hybridnumber;

import jloda.graph.Edge;
import jloda.graph.Node;
import jloda.graph.NodeSet;
import jloda.phylo.PhyloTree;
import jloda.util.Pair;
import jloda.util.Single;
import jloda.util.StringUtils;
import splitstree6.autumn.PostProcess;
import splitstree6.autumn.PreProcess;
import splitstree6.autumn.Root;
import splitstree6.data.TaxaBlock;

import java.io.IOException;
import java.util.*;

/**
 * given two multifurcating trees, performs all possible subtree reductions
 * Daniel Huson, 4.2011
 */
public class SubtreeReduction {
    public static final boolean checking = false;

    public static enum ReturnValue {
        ISOMORPHIC, REDUCED, IRREDUCIBLE
    }

    /**
     * refine two trees
     *
     * @param tree1
     * @param tree2
     * @return subtree-reduced trees followed by all reduced subtrees
     */
    public static PhyloTree[] apply(PhyloTree tree1, PhyloTree tree2) throws IOException {
        // setup rooted trees with nodes labeled by taxa ids
        TaxaBlock allTaxa = new TaxaBlock();
        Pair<Root, Root> roots = PreProcess.apply(tree1, tree2, allTaxa);

        Root root1 = roots.getFirst();
        Root root2 = roots.getSecond();

        // reorder should not be necessary
        // root1.reorderSubTree();
        // root2.reorderSubTree();

        List<Root> subTrees = new LinkedList<Root>();

        // run the algorithm
        ReturnValue value = apply(root1, root2, subTrees);
        if (value == ReturnValue.ISOMORPHIC) {
            System.err.println("Trees are isomorphic");
            BitSet taxa = root1.getTaxa();
            Root newRoot1 = root1.newNode();
            newRoot1.setTaxa(taxa);
            root1 = newRoot1;
            Root newRoot2 = root2.newNode();
            newRoot2.setTaxa(taxa);
            root2 = newRoot2;
        } else if (value == ReturnValue.IRREDUCIBLE) {
            System.err.println("Trees are not subtree reducible");
        }

        List<Root> results = new LinkedList<Root>();
        results.add(root1);
        results.add(root2);
        results.addAll(subTrees);

        // convert data-structures to final trees
        List<PhyloTree> result = PostProcess.apply(results.toArray(new Root[results.size()]), allTaxa, false);
        return result.toArray(new PhyloTree[result.size()]);
    }

    /**
     * recursively reduce subtrees
     *
     * @param v1       root of first tree
     * @param v2       root of second tree
     * @param subTrees all reduced subtrees are returned here
     * @return true, if trees rooted at v1 and v2 are isomorphic
     */
    public static ReturnValue apply(Root v1, Root v2, List<Root> subTrees) {
        // two sets of isomorphic nodes
        NodeSet isomorphic1 = new NodeSet(v1.getOwner());
        isomorphic1.addAll(v1.getAllLeaves());
        NodeSet isomorphic2 = new NodeSet(v2.getOwner());
        isomorphic2.addAll(v2.getAllLeaves());

        String string1 = null;
        String string2 = null;
        if (checking) {
            string1 = v1.toStringFullTreeX();
            string2 = v2.toStringFullTreeX();
        }

        Single<Boolean> changed = new Single<Boolean>(false);
        applyRec(v1, isomorphic1, v2, isomorphic2, subTrees, new HashSet<Pair<Node, Node>>(), changed);

        if (!v1.getTaxa().equals(v2.getTaxa()))
            throw new RuntimeException("Unequal taxon sets: " + StringUtils.toString(v1.getTaxa()) + " vs " + StringUtils.toString(v2.getTaxa()));

        if (checking) {
            try {
                v1.checkTree();
            } catch (RuntimeException ex) {
                System.err.println("DATA:");
                System.err.println(string1 + ";");
                System.err.println(v1.toStringFullTreeX() + ";");
                throw ex;
            }

            try {
                v2.checkTree();
            } catch (RuntimeException ex) {
                System.err.println("Orig: " + string2);
                System.err.println("New: " + v2.toStringFullTreeX());
                throw ex;
            }
        }

        if (isomorphic1.contains(v1) && isomorphic2.contains(v2))
            return ReturnValue.ISOMORPHIC;
        else if (changed.get())
            return ReturnValue.REDUCED;
        else
            return ReturnValue.IRREDUCIBLE;
    }

    /**
     * recursively reduce subtrees
     *
     * @param v1          root of first tree
     * @param isomorphic1 set of isomorphic subtrees of v1, initially containing all leaves
     * @param v2          root of second tree
     * @param isomorphic2 set of isomorphic subtrees of v2, initially containing all leaves
     * @param subTrees    all reduced subtrees are returned here
     * @param compared
     * @param changed
     */
    private static void applyRec(Root v1, NodeSet isomorphic1, Root v2, NodeSet isomorphic2, List<Root> subTrees, Set<Pair<Node, Node>> compared, Single<Boolean> changed) {
        Pair<Node, Node> key = new Pair<Node, Node>(v1, v2);
        if (compared.contains(key))
            return;
        else
            compared.add(key);

        // System.err.println("reduceSubtreesRec v1=" + Basic.toString(v1.getTaxa()) + " v2=" + Basic.toString(v2.getTaxa()));
        //System.err.println("Isomorphic1: "+isomorphic1);
        //System.err.println("Isomorphic2: "+isomorphic2);

        // 1. recursively process all children:
        for (Edge e1 = v1.getFirstOutEdge(); e1 != null; e1 = v1.getNextOutEdge(e1)) {
            Root u1 = (Root) e1.getTarget();
            if (u1.getOutDegree() > 0 && (u1.getTaxa()).intersects(v2.getTaxa()))
                applyRec(u1, isomorphic1, v2, isomorphic2, subTrees, compared, changed);
        }
        for (Edge e2 = v2.getFirstOutEdge(); e2 != null; e2 = v2.getNextOutEdge(e2)) {
            Root u2 = (Root) e2.getTarget();
            if (u2.getOutDegree() > 0 && v1.getTaxa().intersects(u2.getTaxa()))
                applyRec(v1, isomorphic1, u2, isomorphic2, subTrees, compared, changed);
        }

        // now process v1 and v2:

        // 2. determine isomorphic pairs of children below v1 and v2:
        Set<Pair<Root, Root>> isomorphicPairs = new HashSet<Pair<Root, Root>>();
        BitSet taxa = new BitSet();

        for (Edge e1 = v1.getFirstOutEdge(); e1 != null; e1 = v1.getNextOutEdge(e1)) {
            Root u1 = (Root) e1.getTarget();
            if (isomorphic1.contains(u1)) {
                for (Edge e2 = v2.getFirstOutEdge(); e2 != null; e2 = v2.getNextOutEdge(e2)) {
                    Root u2 = (Root) e2.getTarget();
                    if (isomorphic2.contains(u2)) {
                        if (u1.getTaxa().equals(u2.getTaxa())) {
                            // System.err.println("Isomorphic: " + u1 + " and " + u2);
                            isomorphicPairs.add(new Pair<Root, Root>(u1, u2));
                            taxa.or(u1.getTaxa());
                        }
                    }
                }
            }
        }

        // 3. if all children of both nodes match up and are all isomorphic, and v1 and v2 have the same taxa, then so are v1 and v2
        if (isomorphicPairs.size() == v1.getOutDegree() && isomorphicPairs.size() == v2.getOutDegree() && v1.getTaxa().equals(v2.getTaxa())) {
            if (checking && !v1.getTaxa().equals(v2.getTaxa())) {
                System.err.println("v1: " + v1.toStringFullTreeX());
                System.err.println("v2: " + v2.toStringFullTreeX());
                throw new RuntimeException("Trees deemed isomorphic have different taxon sets: " + v1.toString() + " vs " + v2.toString());
            }
            isomorphic1.add(v1);
            isomorphic2.add(v2);
            return;
        }

        // 4. if there is a bunch of matching isomorphic nodes below   v1 and v2 (but not a single pair of leaves), reduce them:
        if (isomorphicPairs.size() > 1 || (isomorphicPairs.size() == 1 && isomorphicPairs.iterator().next().getFirst().getOutDegree() > 1)) {
            // System.err.println("Reducing: " + Basic.toString(taxa));
            // replace bunch of isomorphic nodes by single representative
            Root n1 = v1.newNode();
            v1.newEdge(v1, n1);
            n1.setTaxa(taxa);
            v1.reorderChildren();

            Root n2 = v2.newNode();
            v2.newEdge(v2, n2);
            n2.setTaxa(taxa);
            v2.reorderChildren();

            for (Pair<Root, Root> pair : isomorphicPairs) {
                // save one copy of the subtree and delete the other one:
                Root subtreeRoot = pair.getFirst();
                subtreeRoot.deleteEdge(subtreeRoot.getFirstInEdge());
                if (subTrees != null)
                    subTrees.add(subtreeRoot);
                pair.getSecond().deleteSubTree();
            }
            changed.set(true);
        }
    }
}
