/*
 *  Utilities.java Copyright (C) 2022 Daniel H. Huson
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
package splitstree6.autumn;


import jloda.graph.Edge;
import jloda.graph.Node;
import jloda.phylo.PhyloTree;
import jloda.util.CanceledException;
import jloda.util.progress.ProgressListener;
import splitstree6.data.TaxaBlock;

import java.io.IOException;
import java.util.BitSet;
import java.util.HashSet;
import java.util.Set;

/**
 * utilities
 * Daniel Huson, 7.2011
 */
public class Utilities {

    /**
     * get the number of reticulations in a cluster network
     *
     * @param tree1
     * @param tree2
     * @param progress
     * @return number of reticulate nodes
     * @throws CanceledException
     */
    public static int getNumberOfReticulationsInClusterNetwork(PhyloTree tree1, PhyloTree tree2, ProgressListener progress) throws IOException {

        var allTaxa = new TaxaBlock();
        AutumnUtilities.extractTaxa(1, tree1, allTaxa);
        AutumnUtilities.extractTaxa(2, tree2, allTaxa);

        var clusters = extractClusters(allTaxa, tree1, tree2);
        PhyloTree hasseDiagram = HasseDiagram.constructHasse(clusters.toArray(new Cluster[0]));

        int count = 0;
        for (Node v = hasseDiagram.getFirstNode(); v != null; v = v.getNext()) {
            if (v.getInDegree() > 1)
                count++;
        }
        return count;
    }

    /**
     * extract the set of clusters from the two given trees
     *
     * @param allTaxa
     * @param tree1
     * @return set of clusters, each cluster a BitSet
     */
    static public Set<Cluster> extractClusters(TaxaBlock allTaxa, PhyloTree tree1, PhyloTree tree2) {
        Set<Cluster> result = new HashSet<Cluster>();

        BitSet taxa = extractClustersRec(tree1, tree1.getRoot(), allTaxa, result);
        taxa.or(extractClustersRec(tree2, tree2.getRoot(), allTaxa, result));
        assert (taxa.cardinality() == allTaxa.size());

        return result;
    }

    /**
     * recursively find all clusters in a tree
     *
     * @param tree
     * @param v
     * @param taxa
     * @param clusters
     * @return all taxa on or below v
     */
    private static Cluster extractClustersRec(PhyloTree tree, Node v, TaxaBlock taxa, Set<Cluster> clusters) {
        Cluster clusterV = new Cluster();
        String label = tree.getLabel(v);
        if (label != null && label.length() > 0 && taxa.indexOf(label) != -1)
            clusterV.set(taxa.indexOf(label));

        for (Edge e = v.getFirstOutEdge(); e != null; e = v.getNextOutEdge(e)) {
            Node w = e.getTarget();
            Cluster clusterW = extractClustersRec(tree, w, taxa, clusters);
            assert (clusterW.cardinality() > 0);
            clusters.add(new Cluster(clusterW, tree.getWeight(e), tree.getConfidence(e), 0));
            clusters.add(clusterW);
            clusterV.or(clusterW);
        }
        return clusterV;
    }
}
