/*
 * LayoutAlgorithm.java Copyright (C) 2022 Daniel H. Huson
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

package splitstree6.densitree;

import javafx.geometry.Point2D;
import jloda.fx.util.GeometryUtilsFX;
import jloda.graph.NodeArray;
import jloda.graph.NodeDoubleArray;
import jloda.phylo.PhyloTree;
import jloda.util.BitSetUtils;
import jloda.util.IteratorUtils;
import jloda.util.Single;

import java.util.BitSet;
import java.util.stream.Collectors;

/**
 * compute a radial layout for a given tree using a given cycle
 * Daniel Huson, 12.2021
 */
public class LayoutAlgorithm {
    /**
     * compute coordinates for all nodes
     *
     * @param drawingMethod use weights or scale all edges so that leaves are equi-distance from root or have all edges the same length?
     */
    public static void apply(PhyloTree tree, DrawingMethod drawingMethod, int[] cycle, NodeArray<Point2D> nodePointMap, NodeDoubleArray nodeAngleMap) {
        nodePointMap.clear();
        if (cycle.length > 0) {
            final var taxon2pos = new int[cycle.length];
            for (var pos = 1; pos < cycle.length; pos++)
                taxon2pos[cycle[pos]] = pos;

            final var lastTaxon = cycle[cycle.length - 1];
            int numberOfTaxa = cycle.length - 1; // cycle[0] is not used

            if (numberOfTaxa > 0) {
                var alpha = 360.0 / numberOfTaxa;

                try (NodeArray<BitSet> taxaBelow = tree.newNodeArray()) {
                    tree.postorderTraversal(v -> {
                        if (v.isLeaf()) {
                            var taxa = BitSetUtils.asBitSet(tree.getTaxa(v));
                            taxaBelow.put(v, taxa);
                        } else {
                            var taxa = BitSetUtils.union(v.childrenStream().map(taxaBelow::get).collect(Collectors.toList()));
                            taxaBelow.put(v, taxa);
                        }
                    });


                    var treeTaxa = BitSetUtils.asBitSet(tree.getTaxa());
                    tree.postorderTraversal(v -> {
                        var taxa = taxaBelow.get(v);
                        int add;
                        if (taxa.get(lastTaxon)) {
                            taxa = BitSetUtils.minus(treeTaxa, taxa);
                            add = 180;
                        } else
                            add = 0;
                        nodeAngleMap.put(v, BitSetUtils.asStream(taxa).mapToDouble(t -> alpha * taxon2pos[t] + add).average().orElse(0));
                    });


                    if (drawingMethod == DrawingMethod.CIRCULAR) {
                        var maxDepth = computeMaxDepth(tree);
                        try (var nodeRadiusMap = tree.newNodeDoubleArray()) {
                            tree.postorderTraversal(v -> {
                                if (tree.isLeaf(v)) {
                                    nodeRadiusMap.put(v, (double) maxDepth);
                                } else {
                                    nodeRadiusMap.put(v, IteratorUtils.asStream(tree.lsaChildren(v)).mapToDouble(nodeRadiusMap::get).min().orElse(maxDepth) - 1);
                                }
                            });
                            tree.nodeStream().forEach(v -> nodePointMap.put(v, GeometryUtilsFX.computeCartesian(nodeRadiusMap.get(v), nodeAngleMap.get(v))));
                        }
                    } else if (drawingMethod == DrawingMethod.TOSCALE) {
                        tree.preorderTraversal(v -> {
                            if (v.getInDegree() == 0) { // the root
                                nodePointMap.put(v, new Point2D(0, 0));
                            } else {
                                var e = v.getFirstInEdge();
                                var p = e.getSource();
                                nodePointMap.put(v, GeometryUtilsFX.translateByAngle(nodePointMap.get(p), nodeAngleMap.get(v), tree.getWeight(e)));
                            }
                        });
                    } else if (drawingMethod == DrawingMethod.UNIFORM) {
                        tree.preorderTraversal(v -> {
                            if (v.getInDegree() == 0) { // the root
                                nodePointMap.put(v, new Point2D(0, 0));
                            } else {
                                var e = v.getFirstInEdge();
                                var p = e.getSource();
                                nodePointMap.put(v, GeometryUtilsFX.translateByAngle(nodePointMap.get(p), nodeAngleMap.get(v), 1));
                            }
                        });
                    } else if (drawingMethod == DrawingMethod.ROOTED || drawingMethod == DrawingMethod.BLOCK) {
                        tree.postorderTraversal(v -> {
                            if (tree.isLeaf(v)) {
                                nodePointMap.put(v, new Point2D(0, taxon2pos[tree.getTaxon(v)]));
                            } else {
                                var y1 = nodePointMap.get(v.getFirstOutEdge().getTarget()).getY();
                                var y2 = nodePointMap.get(v.getLastOutEdge().getTarget()).getY();

                                nodePointMap.put(v, new Point2D(0, (y1 + y2) / 2));
                            }
                        });

                        tree.preorderTraversal(v -> {
                            var y = nodePointMap.get(v).getY();
                            if (v.getInDegree() == 0) {
                                nodePointMap.put(v, new Point2D(0, y));

                            } else {
                                var e = v.getFirstInEdge();
                                var x = nodePointMap.get(e.getSource()).getX() + tree.getWeight(e);
                                nodePointMap.put(v, new Point2D(x, y));
                            }
                        });
                        nodeAngleMap.putAll(0.0);
                    }
                }
            }
        }
    }

    /**
     * compute the maximum number of edges from the root to a leaf
     *
     * @param tree the tree
     * @return length of longest path
     */
    public static int computeMaxDepth(PhyloTree tree) {
        var max = new Single<>(0);
        tree.breathFirstTraversal((level, v) -> max.set(Math.max(max.get(), level)));
        return max.get();
    }
}
