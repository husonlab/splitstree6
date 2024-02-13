/*
 * PathMultiplicityDistance.java Copyright (C) 2023 Daniel H. Huson
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
package splitstree6.utils;

import jloda.graph.Node;
import jloda.graph.NodeArray;
import jloda.phylo.NewickIO;
import jloda.phylo.PhyloTree;
import jloda.util.BitSetUtils;
import jloda.util.IteratorUtils;
import jloda.util.SetUtils;
import jloda.util.StringUtils;

import java.io.IOException;
import java.util.*;

import static splitstree6.splits.TreesUtils.addAdhocTaxonIds;

/**
 * computes the path multiplicity distance between two rooted trees or networks
 * Note that two tree-child networks are isomorphic, if and only if their distance is 0
 * Daniel Huson, 2.2024
 */

public class PathMultiplicityDistance {

    /**
     * computes the path multiplicity distance between two rooted trees or networks
     *
     * @param tree1 rooted tree or network
     * @param tree2 rooted tree or network
     * @return distance
     */
    public static double compute(PhyloTree tree1, PhyloTree tree2) {
        var taxa = BitSetUtils.asBitSet(tree1.getTaxa());
        taxa.or(BitSetUtils.asBitSet(tree2.getTaxa()));

        var n1PathVectors = collectMuVectors(tree1, taxa);
        var n2PathVectors = collectMuVectors(tree2, taxa);

        return IteratorUtils.count(SetUtils.symmetricDifference(n1PathVectors, n2PathVectors)) / 2.0;
    }

    /**
     * computes the matrix of all pairwise path-multiplity distances
     *
     * @param trees input rooted trees and/or networks
     * @return distance matrix
     */
    public static double[][] compute(List<PhyloTree> trees) {
        var taxa = new BitSet();
        for (var tree : trees) {
            taxa.or(BitSetUtils.asBitSet(tree.getTaxa()));
        }

        var pathVectors = new ArrayList<Collection<String>>();
        for (PhyloTree tree : trees) {
            pathVectors.add(collectMuVectors(tree, taxa));
        }

        var distances = new double[trees.size()][trees.size()];
        for (var t1 = 0; t1 < trees.size(); t1++) {
            for (var t2 = t1 + 1; t2 < trees.size(); t2++) {
                distances[t1][t2] = distances[t2][t1] = IteratorUtils.count(SetUtils.symmetricDifference(pathVectors.get(t1), pathVectors.get(t2))) / 2.0;
            }
        }
        return distances;
    }


    private static Collection<String> collectMuVectors(PhyloTree tree, BitSet taxa) {
        var n = BitSetUtils.max(taxa);
        try (NodeArray<int[]> mu = tree.newNodeArray()) {
            collectMuVectorsRec(tree, tree.getRoot(), mu, n);
            var paths = new HashSet<String>();
            for (var v : tree.nodeStream().filter(v -> v.getOutDegree() > 0).toList()) {
                paths.add(StringUtils.toString(mu.get(v), ","));
            }
            return paths;
        }
    }

    private static BitSet collectMuVectorsRec(PhyloTree tree, Node v, NodeArray<int[]> mu, int n) {
        var mu_v = mu.put(v, new int[n + 1]);
        var taxaBelowV = new BitSet();
        for (var t : tree.getTaxa(v)) {
            mu_v[t] = 1;
            taxaBelowV.set(t);
        }

        for (var w : v.children()) {
            var taxaBelowW = collectMuVectorsRec(tree, w, mu, n);
            var mu_w = mu.get(w);
            for (var t : BitSetUtils.members(taxaBelowW)) {
                mu_v[t] += mu_w[t];
            }
            taxaBelowV.or(taxaBelowW);
        }
        return taxaBelowV;
    }

    public static void main(String[] args) throws IOException {
        var input = new String[]{
                "(((((((((((((t4,(t1,t2)),(t5,t11)),(t19,t8)),(((t20,t15),t12),t6)),((t13,(t16)#H2))#H1),((((t14,t10),#H1),#H2))#H3),(((t18,(((((t9,t17),t7),((t3,#H3))#H6),#H3))#H5),#H1))#H4),#H5),#H6),#H5),#H3),#H4));",
                "((((((((((((((t2,t1),t4),(t5,t11)),(t19,t8)),(((t20,t15),t12),t6)),((t13,(t16)#H2))#H1),((((t14,t10),#H1),#H2))#H3),(((t18,(((((t17,t9),t7),((t3,#H3))#H6),#H3))#H5),#H1))#H4),#H5),#H6),#H5),#H3),#H4));",
                "((((((((((((((t1,t2),t4),(t5,t11)),(t19,t8)),(((t20,t15),t12),t6)),((t13,(t16)#H2))#H1),((((t14,t10),#H1),#H2))#H3),(((t18,(((((t17,t9),t7),((t3,#H3))#H6),#H3))#H5),#H1))#H4),#H5),#H6),#H5),#H3),#H4));"};

        var trees = new ArrayList<PhyloTree>();
        var labelTaxonIdMap = new HashMap<String, Integer>();
        for (var line : input) {
            var tree = NewickIO.valueOf(line);
            addAdhocTaxonIds(tree, labelTaxonIdMap);
            trees.add(tree);
        }

        var distances = compute(trees);
        System.out.println("Distances:\n");
        for (var i = 0; i < distances.length; i++) {
            for (var j = 0; j < distances.length; j++) {
                System.out.printf(" %.1f", distances[i][j]);
            }
            System.out.println();
        }
        System.out.println();
    }
}
