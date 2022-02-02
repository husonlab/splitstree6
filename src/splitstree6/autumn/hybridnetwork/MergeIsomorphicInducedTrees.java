/*
 * MergeIsomorphicInducedTrees.java Copyright (C) 2022 Daniel H. Huson
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
package splitstree6.autumn.hybridnetwork;

import jloda.graph.Edge;
import jloda.graph.Graph;
import jloda.util.Pair;
import splitstree6.autumn.Root;

import java.util.LinkedList;
import java.util.List;

/**
 * merge two trees that are isomorphic as induced trees
 * Daniel Huson, 5.2011
 */
public class MergeIsomorphicInducedTrees {
    private static final boolean verbose = false;

    /**
     * perform the merge
     *
     * @return resulting tree
     */
    public static Root apply(Root root1, Root root2) {
        if (verbose) {
            System.err.println("Merge isomorphic trees:");
            System.err.println("tree1: " + root1.toStringFullTreeX());
            System.err.println("tree2: " + root2.toStringFullTreeX());
        }

        var origRoot1 = root1;
        var origRoot2 = root2;

        if (!root1.getTaxa().equals(root2.getTaxa())) // different sets below, can't be isomorphic
            return null;
        if (root1.getTaxa().cardinality() == 0)
            return null;

        //move to next branching node, i.e. node that has two different children that still have taxa on them
        var isBranchingNode1 = false;
        var path1 = new LinkedList<Edge>();

        while (!isBranchingNode1) {
            if (root1.getOutDegree() == 0) {
                if (root1.getTaxa().cardinality() == 0)
                    return null;
                break;
            }
            Edge f1 = null;
            for (var e1 = root1.getFirstOutEdge(); e1 != null && !isBranchingNode1; e1 = root1.getNextOutEdge(e1)) {
                Root w1 = (Root) e1.getTarget();
                if (w1.getTaxa().cardinality() > 0) {
                    if (f1 != null)
                        isBranchingNode1 = true;
                    else
                        f1 = e1;
                }
            }
            if (!isBranchingNode1) {
                if (f1 == null)
                    return null;
                path1.add(f1);
                root1 = (Root) f1.getTarget();
            }
        }

        //move to next branching node, i.e. now that has two different children that still have taxa on them
        var isBranchingNode2 = false;
        var path2 = new LinkedList<Edge>();

        while (!isBranchingNode2) {
            if (root2.getOutDegree() == 0) {
                if (root2.getTaxa().cardinality() == 0)
                    return null;
                break;
            }
            Edge f2 = null;
            for (var e2 = root2.getFirstOutEdge(); e2 != null && !isBranchingNode2; e2 = root2.getNextOutEdge(e2)) {
                var w2 = (Root) e2.getTarget();
                if (w2.getTaxa().cardinality() > 0) {
                    if (f2 != null)
                        isBranchingNode2 = true;
                    else
                        f2 = e2;
                }
            }
            if (!isBranchingNode2) {
                if (f2 == null)
                    return null;
                path2.add(f2);
                root2 = (Root) f2.getTarget();
            }

        }

        if (!root1.getTaxa().equals(root2.getTaxa()))
            return null;

        // note: don't require moved1==moved2

        // root of result
        var origRoot = new Root(new Graph());
        origRoot.setTaxa(origRoot1.getTaxa());
        origRoot.getTaxa().or(origRoot2.getTaxa());
        origRoot.setRemovedTaxa(origRoot1.getRemovedTaxa());
        origRoot.getRemovedTaxa().or(origRoot2.getRemovedTaxa());

        Root root;
        if (path1.size() > 0 || path2.size() > 0) {
            root = mergeAlongInducedEdge(null, path1, root1, null, path2, root2, origRoot);
            if (verbose) {
                System.err.println("Root-reduced1: " + root1.toStringFullTreeX());
                System.err.println("Root-reduced2: " + root2.toStringFullTreeX());
            }
            var v = root;
            while (v.getInDegree() == 1) {
                v = (Root) v.getFirstInEdge().getSource();
                if (v.getInDegree() == 1 && v.getOutDegree() == 1) {
                    var w = (Root) v.getFirstInEdge().getSource();
                    w.newEdge(w, v.getFirstOutEdge().getTarget());
                    v.deleteNode();
                    v = w;
                }
            }
        } else
            root = origRoot;

        // recursively build the merged tree:
        if (!applyRec(root1, root2, root))
            return null;

        var toDelete = new LinkedList<Root>();
        while (origRoot.getOutDegree() == 1 && origRoot.getFirstOutEdge().getTarget().getOutDegree() == 1) {
            toDelete.add(origRoot);
            origRoot = (Root) origRoot.getFirstOutEdge().getTarget();
        }
        for (var v : toDelete) {
            v.deleteNode();
        }

        if (origRoot.getOutDegree() == 1 &&
            (origRoot1.getOutDegree() > 1 && path2.size() == 0 || origRoot2.getOutDegree() > 1 && path1.size() == 0))

            if (verbose) {
                System.err.println("Merge result: " + origRoot.toStringFullTreeX());
            }

        return origRoot;
    }

    /**
     * recursively does the work
     *
     * @param root1   - a branching node in tree 1
     * @param root2   - corresponding branching node in tree 2
     * @param rootTar - corresponding node in resulting tree
     * @return true, if isomorphic
     */
    private static boolean applyRec(Root root1, Root root2, Root rootTar) {
        if (verbose) {
            System.err.println("Apply rec:");
            System.err.println("tree1: " + root1.toStringFullTreeX());
            System.err.println("tree2: " + root2.toStringFullTreeX());
        }

        if (!root1.getTaxa().equals(root2.getTaxa())) // different sets below, can't be isomorphic
            return false;
        if (root1.getTaxa().cardinality() == 0) // both empty, isomorphic
            return true;

        //move to next branching node, i.e. now that has two different children that still have taxa on them
        var isBranchingNode1 = false;
        var path1 = new LinkedList<Edge>();

        while (!isBranchingNode1) {
            if (root1.getOutDegree() == 0) {
                if (root1.getTaxa().cardinality() == 0)
                    return false;
                break;
            }
            Edge f1 = null;
            for (Edge e1 = root1.getFirstOutEdge(); e1 != null && !isBranchingNode1; e1 = root1.getNextOutEdge(e1)) {
                var w1 = (Root) e1.getTarget();
                if (w1.getTaxa().cardinality() > 0) {
                    if (f1 != null)
                        isBranchingNode1 = true;
                    else
                        f1 = e1;
                }
            }
            if (!isBranchingNode1) {
                if (f1 == null)
                    return false;
                path1.add(f1);
                root1 = (Root) f1.getTarget();
            }
        }

        //move to next branching node, i.e. now that has two different children that still have taxa on them
        var isBranchingNode2 = false;
        var path2 = new LinkedList<Edge>();

        while (!isBranchingNode2) {
            if (root2.getOutDegree() == 0) {
                if (root2.getTaxa().cardinality() == 0)
                    return false;
                break;
            }
            Edge f2 = null;
            for (Edge e2 = root2.getFirstOutEdge(); e2 != null && !isBranchingNode2; e2 = root2.getNextOutEdge(e2)) {
                var w2 = (Root) e2.getTarget();
                if (w2.getTaxa().cardinality() > 0) {
                    if (f2 != null)
                        isBranchingNode2 = true;
                    else
                        f2 = e2;
                }
            }
            if (!isBranchingNode2) {
                if (f2 == null)
                    return false;
                path2.add(f2);
                root2 = (Root) f2.getTarget();
            }
        }

        // attach all dead nodes from below root
        if (path1.size() > 0 || path2.size() > 0) {
            if (rootTar.getInDegree() == 1) {
                var tmp = rootTar;
                rootTar = (Root) rootTar.getFirstInEdge().getSource();
                tmp.deleteNode();
            }
            rootTar = mergeAlongInducedEdge(null, path1, root1, null, path2, root2, rootTar);
        }

        // attach all dead nodes below root1 to rootTar
        for (var f1 : root1.outEdges()) {
            var w1 = (Root) f1.getTarget();
            if (w1.getTaxa().cardinality() == 0) {
                addSubTree(rootTar, f1, 1);
            }
        }

        // attach all dead nodes below root2 to rootTar
        for (var f2 : root2.outEdges()) {
            var w2 = (Root) f2.getTarget();
            if (w2.getTaxa().cardinality() == 0) {
                addSubTree(rootTar, f2, 2);
            }
        }

        // now try to match all children:
        var e1 = root1.getFirstOutEdge();
        var e2 = root2.getFirstOutEdge();

        while (e1 != null || e2 != null) {
            while (e1 != null && ((Root) e1.getTarget()).getTaxa().cardinality() == 0) {
                e1 = root1.getNextOutEdge(e1);
            }
            while (e2 != null && ((Root) e2.getTarget()).getTaxa().cardinality() == 0) {
                e2 = root2.getNextOutEdge(e2);
            }
            if (e1 == null && e2 == null)  // both are null, have matched every thing
                break;
            else if ((e1 == null) != (e2 == null)) // one of the two is null
                return false;
            else // both have taxa below, recurse
            {
                var w1 = (Root) e1.getTarget();
                var w2 = (Root) e2.getTarget();

                var wTar = rootTar.newNode();
                rootTar.newEdge(rootTar, wTar);
                wTar.setTaxa(w1.getTaxa());
                wTar.getTaxa().or(w2.getTaxa());
                wTar.setRemovedTaxa(w1.getRemovedTaxa());
                wTar.getRemovedTaxa().or(w2.getRemovedTaxa());

                if (!applyRec(w1, w2, wTar))
                    return false;
            }
            e1 = root1.getNextOutEdge(e1);
            e2 = root2.getNextOutEdge(e2);
        }
        return true;
    }


    /**
     * create a new edge below v that contains all disabled subtrees along the induced edge (v1,w1) and (v2,w2)
     *
	 */
    private static Root mergeAlongInducedEdge(Root v1, List<Edge> path1, Root w1, Root v2, List<Edge> path2, Root w2, Root vTar) {
        // order the subtrees nodes along the induced edge:
        var order = new LinkedList<Pair<Edge, Integer>>();
        for (var e1 : path1) {
            if (e1.getSource() != v1)
                order.add(new Pair<>(e1, 1));
        }
        for (var e2 : path2) {
            if (e2.getSource() != v2)
                order.add(new Pair<>(e2, 2));
        }

        // add all the disabled subtrees
        var prevTar = vTar;
        var above = new LinkedList<Root>();
        for (var pair : order) {
            var e = pair.getFirst();
            var treeId = pair.getSecond();
            var a = (Root) e.getSource();
            var wTar = prevTar.newNode();
            wTar.setTaxa(a.getTaxa());
            wTar.setRemovedTaxa(a.getRemovedTaxa());
            prevTar.newEdge(prevTar, wTar);

            addSubTree(wTar, a, e, treeId);

            prevTar = wTar;
            for (var b : above) {
                b.getRemovedTaxa().or(prevTar.getRemovedTaxa());
            }
            above.add(prevTar);
        }

        // create the branching node:
        var wTar = vTar.newNode();
        wTar.setTaxa(w1.getTaxa());
        wTar.getTaxa().or(w1.getTaxa());
        wTar.setRemovedTaxa(w1.getRemovedTaxa());
        wTar.getRemovedTaxa().or(w2.getRemovedTaxa());
        wTar.newEdge(prevTar, wTar);

        // move stuff up:
        var up = wTar;
        while (up.getInDegree() == 1) {
            var w = (Root) up.getFirstInEdge().getSource();
            w.getTaxa().or(up.getTaxa());
            w.getRemovedTaxa().or(up.getRemovedTaxa());
            up = w;
        }

        return wTar;
    }

    /**
     * the subtree below vSrc copied below vTar
     *
     * @param taboo don't use this edge (leads down along induced edge...)
     */
    private static void addSubTree(Root vTar, Root vSrc, Edge taboo, int treeId) {
        for (var e : vSrc.outEdges()) {
            if (e != taboo) {
                var wSrc = (Root) e.getTarget();
                var wTar = vTar.newNode();
                var f = wTar.newEdge(vTar, wTar);
                f.setInfo(treeId);
                wTar.setTaxa(wSrc.getTaxa());
                wTar.setRemovedTaxa(wSrc.getRemovedTaxa());
                addSubTree(wTar, wSrc, null, treeId);
            }
        }
    }


    /**
     * the subtree below vSrc copied below vTar
     *
	 */
    private static void addSubTree(Root vTar, Edge eSrc, int treeId) {
        var wSrc = (Root) eSrc.getTarget();
        var wTar = vTar.newNode();
        var f = wTar.newEdge(vTar, wTar);
        f.setInfo(treeId);
        wTar.setTaxa(wSrc.getTaxa());
        wTar.setRemovedTaxa(wSrc.getRemovedTaxa());
        addSubTree(wTar, wSrc, null, treeId);
    }
}
