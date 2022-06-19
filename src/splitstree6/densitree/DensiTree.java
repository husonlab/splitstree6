/*
 * DensiTree.java Copyright (C) 2022 Daniel H. Huson
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

import javafx.beans.property.ReadOnlyDoubleProperty;
import javafx.beans.value.ChangeListener;
import javafx.geometry.Point2D;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Line;
import javafx.scene.text.Font;
import jloda.fx.control.RichTextLabel;
import jloda.fx.util.GeometryUtilsFX;
import jloda.graph.NodeArray;
import jloda.graph.NodeDoubleArray;
import jloda.phylo.PhyloTree;
import jloda.util.RandomGaussian;
import org.apache.commons.math3.ml.clustering.*;
import splitstree6.layout.tree.RadialLabelLayout;

import java.io.IOException;
import java.util.*;

/**
 * draw the densi-tree
 */
public class DensiTree {

    public static void clear(Canvas canvas, Pane labelPane, Pane consensusPane, Pane highlightingPane) {
        var gc = canvas.getGraphicsContext2D();
        gc.restore();
        gc.clearRect(0, 0, canvas.getWidth(), canvas.getHeight());
        labelPane.getChildren().clear();
        consensusPane.getChildren().clear();
        highlightingPane.getChildren().clear();
    }

    public static void draw(Parameters parameters, Model model, Canvas canvas, Pane labelPane, Pane consenusPane, Pane highlightingPane, ReadOnlyDoubleProperty scalingFactor) throws IOException {
        if (model.getTreesBlock().getNTrees() > 0) {

            long start = System.currentTimeMillis();

            var gc = canvas.getGraphicsContext2D();
            gc.setFont(Font.font("Courier New", 11));
            gc.clearRect(0, 0, canvas.getWidth(), canvas.getHeight());
            gc.setStroke(Color.BLACK);

            labelPane.getChildren().clear();
            consenusPane.getChildren().clear();
            highlightingPane.getChildren().clear();


            var xmin = (int) (100 * scalingFactor.get());
            var ymin = (int) (100 * scalingFactor.get());
            var xmax = (int) ((canvas.getWidth() - 200) * scalingFactor.get());
            var ymax = (int) ((canvas.getHeight() - 100) * scalingFactor.get());

            boolean jitter = parameters.jitter;
            boolean block = false;
            String labelMethod = parameters.labelMethod;
            DrawingMethod drawingMethod = DrawingMethod.CIRCULAR;
            if (parameters.drawingMethod.contains("toscale")) {
                drawingMethod = DrawingMethod.TOSCALE;
            } else if (parameters.drawingMethod.contains("uniform")) {
                drawingMethod = DrawingMethod.UNIFORM;
            } else if (parameters.drawingMethod.contains("block")) {
                drawingMethod = DrawingMethod.BLOCK;
                block = true;
            } else if (parameters.drawingMethod.contains("rooted")) {
                drawingMethod = DrawingMethod.ROOTED;
            }

            int nTrees = model.getTreesBlock().size();
            int nTaxa = model.getTaxaBlock().getNtax();

            var labelLayout = new RadialLabelLayout();
            labelLayout.setGap(1);

            //DBSCANClusterer dbscanClusterer = new DBSCANClusterer(10, 10);
            KMeansPlusPlusClusterer kmeansClusterer = new KMeansPlusPlusClusterer(2);

            int[] circle = model.getCircularOrdering();
            String[] labels = new String[nTaxa];
            double[][] coords = new double[nTaxa][2];
            double[][][] coords2 = new double[nTaxa][nTrees][2];
            DoublePoint[][] coords3 = new DoublePoint[nTaxa][nTrees];

            RandomGaussian random = new RandomGaussian(0, 3, 187);
            double shiftx;
            double shifty;

            var tree1 = model.getTreesBlock().getTree(1);
            int counter = 0;
            for (int j : circle) {
                if (j > 0) {
                    labels[counter] = tree1.getTaxon2Node(j).getLabel();
                    counter++;
                }
            }

            double[] comT1 = getCenterofMass(model, parameters, canvas, scalingFactor, 1);

            gc.setLineWidth(1.0);

            gc.save();

            gc.setLineWidth(0.2);
            gc.setGlobalAlpha(0.1);
            for (int i = 1; i <= nTrees; i++) {
                var tree = model.getTreesBlock().getTree(i);
                shiftx = random.nextDouble();
                shifty = random.nextDouble();

                NodeArray<Point2D> nodePointMap = tree.newNodeArray();
                var nodeAngleMap = tree.newNodeDoubleArray();
                LayoutAlgorithm.apply(tree, drawingMethod, circle, nodePointMap, nodeAngleMap);
                adjustCoordinatesToBox(drawingMethod == DrawingMethod.CIRCULAR, nodePointMap, xmin, ymin, xmax, ymax);

                centerByMass(tree, nodePointMap, comT1);

                if (labelMethod.contains("kmeans")) {
                    drawEdges2(tree, i - 1, gc, nodePointMap, jitter, block, shiftx, shifty, coords3, labels);
                } else if (labelMethod.contains("mean") || labelMethod.contains("radial")) {
                    drawEdges(tree, gc, nodePointMap, jitter, block, shiftx, shifty, coords, labels);
                } else if (labelMethod.contains("median")) {
                    drawEdges1(tree, i - 1, gc, nodePointMap, jitter, block, shiftx, shifty, coords2, labels);
                }
            }


            drawHighlightedTrees(model, highlightingPane, parameters, canvas, scalingFactor);
            drawConsensus(model, consenusPane, parameters, canvas, scalingFactor);


            NodeArray<Point2D> nodePointMap = tree1.newNodeArray();
            var nodeAngleMap = tree1.newNodeDoubleArray();
            LayoutAlgorithm.apply(tree1, drawingMethod, circle, nodePointMap, nodeAngleMap);
            adjustCoordinatesToBox(drawingMethod == DrawingMethod.CIRCULAR, nodePointMap, xmin, ymin, xmax, ymax);

            if (labelMethod.contains("kmeans")) {
                kmeansLabels(tree1, labelPane, nodeAngleMap, coords3, labels, kmeansClusterer);
            } else if (labelMethod.contains("mean")) {
                meanLabels(tree1, labelPane, nodeAngleMap, coords, labels, nTrees);
            } else if (labelMethod.contains("median")) {
                medianLabels(tree1, labelPane, nodeAngleMap, coords2, labels);
            } else if (labelMethod.contains("radial")) {
                radialLabels(tree1, labelPane, nodeAngleMap, coords, labels, nTrees, labelLayout);
                labelLayout.layoutLabels();
            }


            gc.setStroke(Color.BLACK);
            System.out.println("Drawing");
            System.out.println(System.currentTimeMillis()-start);
        }
    }

    public static void drawConsensus(Model model, Pane consensusPane, Parameters parameters, Canvas canvas, ReadOnlyDoubleProperty scalingFactor) throws IOException {
        if (parameters.consensus) {
            var xmin = (int) (100 * scalingFactor.get());
            var ymin = (int) (100 * scalingFactor.get());
            var xmax = (int) ((canvas.getWidth() - 100) * scalingFactor.get());
            var ymax = (int) ((canvas.getHeight() - 100) * scalingFactor.get());

            var drawingMethod = DrawingMethod.CIRCULAR;
            if (parameters.drawingMethod.contains("toscale")) {
                drawingMethod = DrawingMethod.TOSCALE;
            } else if (parameters.drawingMethod.contains("uniform")) {
                drawingMethod = DrawingMethod.UNIFORM;
            } else if (parameters.drawingMethod.contains("rooted")) {
                drawingMethod = DrawingMethod.ROOTED;
            }

            var circle = model.getCircularOrdering();
            var consensusTree = MajorityConsensus.apply(model);

            NodeArray<Point2D> nodePointMap = consensusTree.newNodeArray();
            var nodeAngleMap = consensusTree.newNodeDoubleArray();
            LayoutAlgorithm.apply(consensusTree, drawingMethod, circle, nodePointMap, nodeAngleMap);
            adjustCoordinatesToBox(drawingMethod == DrawingMethod.CIRCULAR, nodePointMap, xmin, ymin, xmax, ymax);

            double[] comT1 = getCenterofMass(model, parameters, canvas, scalingFactor, 1);
            centerByMass(consensusTree, nodePointMap, comT1);

            for (var e : consensusTree.edges()) {
                var v = e.getSource();
                var w = e.getTarget();
                var vPt = nodePointMap.get(v);
                var wPt = nodePointMap.get(w);

                var line = new Line(vPt.getX(), vPt.getY(), wPt.getX(), wPt.getY());
                line.setStroke(Color.FUCHSIA);
                line.setStrokeWidth(0.5);
                consensusPane.getChildren().add(line);
            }
        } else {
            consensusPane.getChildren().clear();
        }
    }

    public static void drawHighlightedTrees(Model model, Pane highlightingPane, Parameters parameters, Canvas canvas, ReadOnlyDoubleProperty scalingFactor) {
        String highlight = parameters.highlight + ",";
        if (parameters.highlight!= null &&highlight.matches("(\\d,)+")) {
            var xmin = (int) (100 * scalingFactor.get());
            var ymin = (int) (100 * scalingFactor.get());
            var xmax = (int) ((canvas.getWidth() - 100) * scalingFactor.get());
            var ymax = (int) ((canvas.getHeight() - 100) * scalingFactor.get());

            var nTrees = model.getTreesBlock().size();

            DrawingMethod drawingMethod = DrawingMethod.CIRCULAR;
            if (parameters.drawingMethod.contains("toscale")) {
                drawingMethod = DrawingMethod.TOSCALE;
            } else if (parameters.drawingMethod.contains("uniform")) {
                drawingMethod = DrawingMethod.UNIFORM;
            } else if (parameters.drawingMethod.contains("rooted")) {
                drawingMethod = DrawingMethod.ROOTED;
            }

            var circle = model.getCircularOrdering();

            double[] comT1 = getCenterofMass(model, parameters, canvas, scalingFactor, 1);

            String[] specTrees = parameters.highlight.split(",");
            for (String specTree : specTrees) {
                int treeNum = Integer.parseInt(specTree);
                if (treeNum > 0 && treeNum <= nTrees) {

                    var tree = model.getTreesBlock().getTree(Integer.parseInt(specTree));

                    NodeArray<Point2D> nodePointMap = tree.newNodeArray();
                    var nodeAngleMap = tree.newNodeDoubleArray();
                    LayoutAlgorithm.apply(tree, drawingMethod, circle, nodePointMap, nodeAngleMap);
                    adjustCoordinatesToBox(drawingMethod == DrawingMethod.CIRCULAR, nodePointMap, xmin, ymin, xmax, ymax);

                    centerByMass(tree, nodePointMap, comT1);

                    for (var e : tree.edges()) {
                        var v = e.getSource();
                        var w = e.getTarget();
                        var vPt = nodePointMap.get(v);
                        var wPt = nodePointMap.get(w);

                        var line = new Line(vPt.getX(), vPt.getY(), wPt.getX(), wPt.getY());
                        line.setStroke(Color.GREENYELLOW);
                        line.setStrokeWidth(0.5);
                        highlightingPane.getChildren().add(line);
                    }
                }
            }
        } else {
            highlightingPane.getChildren().clear();
        }
    }

    public static double[] getCenterofMass(Model model, Parameters parameters, Canvas canvas, ReadOnlyDoubleProperty scalingFactor, int treeNum){
        var xmin = (int) (100 * scalingFactor.get());
        var ymin = (int) (100 * scalingFactor.get());
        var xmax = (int) ((canvas.getWidth() - 100) * scalingFactor.get());
        var ymax = (int) ((canvas.getHeight() - 100) * scalingFactor.get());

        DrawingMethod drawingMethod = DrawingMethod.CIRCULAR;
        if (parameters.drawingMethod.contains("toscale")) {
            drawingMethod = DrawingMethod.TOSCALE;
        } else if (parameters.drawingMethod.contains("uniform")) {
            drawingMethod = DrawingMethod.UNIFORM;
        } else if (parameters.drawingMethod.contains("rooted")) {
            drawingMethod = DrawingMethod.ROOTED;
        }

        var circle = model.getCircularOrdering();

        PhyloTree tree = model.getTreesBlock().getTree(treeNum);

        NodeArray<Point2D> nodePointMap1 = tree.newNodeArray();
        var nodeAngleMap1 = tree.newNodeDoubleArray();
        LayoutAlgorithm.apply(tree, drawingMethod, circle, nodePointMap1, nodeAngleMap1);
        adjustCoordinatesToBox(drawingMethod == DrawingMethod.CIRCULAR, nodePointMap1, xmin, ymin, xmax, ymax);

        double xsum = nodePointMap1.get(tree.getRoot()).getX();
        double ysum = nodePointMap1.get(tree.getRoot()).getY();
        int nodeCount = 1;

        for (var e : tree.edges()) {
            var v = e.getSource();
            var w = e.getTarget();
            var vPt = nodePointMap1.get(v);
            var wPt = nodePointMap1.get(w);

            if (vPt != null & wPt != null) {

                xsum += wPt.getX();
                ysum += wPt.getY();
                nodeCount++;
            }
        }

        double comX = xsum / nodeCount;
        double comY = ysum / nodeCount;
        return new double[]{comX, comY};
    }

    public static void centerByMass(PhyloTree tree, NodeArray<Point2D> nodePointMap, double[] comT1) {
        double xsum = nodePointMap.get(tree.getRoot()).getX();
        double ysum = nodePointMap.get(tree.getRoot()).getY();
        int nodeCount = 1;

        for (var e : tree.edges()) {
            var v = e.getSource();
            var w = e.getTarget();
            var vPt = nodePointMap.get(v);
            var wPt = nodePointMap.get(w);

            if (vPt != null & wPt != null) {

                xsum += wPt.getX();
                ysum += wPt.getY();
                nodeCount++;
            }
        }

        double comX = xsum / nodeCount;
        double comY = ysum / nodeCount;

        double centerX = comT1[0] - comX;
        double centerY = comT1[1] - comY;

        for (var k : nodePointMap.keySet()) {
            var point = nodePointMap.get(k);
            nodePointMap.put(k, new Point2D(point.getX() + centerX, point.getY() + centerY));
        }
    }

    public static void meanLabels(PhyloTree tree, Pane labelPane, NodeDoubleArray nodeAngleMap, double[][] coords, String[] labels, int nTrees) {

        for (var e : tree.edges()) {
            var w = e.getTarget();
            if (w.isLeaf()) {
                var label = new RichTextLabel(tree.getLabel(w));
                for (int i = 0; i < labels.length; i++) {
                    if (tree.getLabel(w).equals(labels[i])) {
                        int finalI = i;
                        ChangeListener<Number> listener = (observableValue, oldValue, newValue) -> { // use a listener because we have to wait until both width and height have been set
                            if (oldValue.doubleValue() == 0 && newValue.doubleValue() > 0 && label.getWidth() > 0 && label.getHeight() > 0) {
                                var angle = nodeAngleMap.get(w);
                                var delta = GeometryUtilsFX.translateByAngle(-0.5 * label.getWidth(), -0.5 * label.getHeight(), angle, 0.5 * label.getWidth() + 5);
                                label.setLayoutX(coords[finalI][0] / nTrees + delta.getX());
                                label.setLayoutY(coords[finalI][1] / nTrees + delta.getY());
                                label.setRotate(angle);
                                label.ensureUpright();
                                label.setTextFill(Color.RED);
                            }
                        };
                        label.widthProperty().addListener(listener);
                        label.heightProperty().addListener(listener);
                        labelPane.getChildren().add(label);
                        break;
                    }
                }
            }
        }
    }

    public static void medianLabels(PhyloTree tree, Pane labelPane, NodeDoubleArray nodeAngleMap, double[][][] coords2, String[] labels) {

        for (var e : tree.edges()) {
            var w = e.getTarget();
            if (w.isLeaf()) {
                var label = new RichTextLabel(tree.getLabel(w));
                for (int i = 0; i < labels.length; i++) {
                    if (tree.getLabel(w).equals(labels[i])) {
                        int finalI = i;
                        Arrays.sort(coords2[finalI], Comparator.comparingDouble(a -> a[0]));
                        ChangeListener<Number> listener = (observableValue, oldValue, newValue) -> { // use a listener because we have to wait until both width and height have been set
                            if (oldValue.doubleValue() == 0 && newValue.doubleValue() > 0 && label.getWidth() > 0 && label.getHeight() > 0) {
                                var angle = nodeAngleMap.get(w);
                                var delta = GeometryUtilsFX.translateByAngle(-0.5 * label.getWidth(), -0.5 * label.getHeight(), angle, 0.5 * label.getWidth() + 5);
                                label.setLayoutX(coords2[finalI][coords2[finalI].length / 2][0] + delta.getX());
                                label.setLayoutY(coords2[finalI][coords2[finalI].length / 2][1] + delta.getY());
                                label.setRotate(angle);
                                label.ensureUpright();
                                label.setTextFill(Color.RED);
                            }
                        };
                        label.widthProperty().addListener(listener);
                        label.heightProperty().addListener(listener);
                        labelPane.getChildren().add(label);
                        break;
                    }
                }
            }
        }
    }

    public static void radialLabels(PhyloTree tree, Pane labelPane, NodeDoubleArray nodeAngleMap,
                                    double[][] coords, String[] labels, int nTrees, RadialLabelLayout labelLayout) {

        for (var e : tree.edges()) {
            var w = e.getTarget();
            if (w.isLeaf()) {
                var label = new RichTextLabel(tree.getLabel(w));
                for (int i = 0; i < labels.length; i++) {
                    if (tree.getLabel(w).equals(labels[i])) {
                        int finalI = i;
                        ChangeListener<Number> listener = (observableValue, oldValue, newValue) -> { // use a listener because we have to wait until both width and height have been set
                            if (oldValue.doubleValue() == 0 && newValue.doubleValue() > 0 && label.getWidth() > 0 && label.getHeight() > 0) {
                                var angle = nodeAngleMap.get(w);
                                var delta = GeometryUtilsFX.translateByAngle(-0.5 * label.getWidth(), -0.5 * label.getHeight(), angle, 0.5 * label.getWidth() + 5);
                                label.setLayoutX(coords[finalI][0] / nTrees + delta.getX());
                                label.setLayoutY(coords[finalI][1] / nTrees + delta.getY());
                                //label.setRotate(angle);
                                label.ensureUpright();
                                label.setTextFill(Color.RED);
                            }
                        };
                        label.widthProperty().addListener(listener);
                        label.heightProperty().addListener(listener);
                        labelPane.getChildren().add(label);
                        labelLayout.addItem(label.translateXProperty(), label.translateYProperty(), nodeAngleMap.get(w), label.widthProperty(), label.heightProperty(),
                                xOffset -> {
                                    label.setTranslateX(xOffset);
                                },
                                yOffset -> {
                                    label.setTranslateY(yOffset);
                                });
                        break;
                    }
                }
            }
        }
    }

    public static void radialItems(
            PhyloTree tree, NodeArray<Point2D> nodePointMap, RadialLabelLayout labelLayout
    ) {

        for (var e : tree.edges()) {
            var w = e.getTarget();
            var wPt = nodePointMap.get(w);

            if (w.isLeaf()) {
                labelLayout.addAvoidable(wPt::getX, wPt::getY, () -> 1.0, () -> 1.0);
            }
        }
    }

    public static void dbscanLabels(
            PhyloTree tree, Pane labelPane, NodeDoubleArray nodeAngleMap,
            DoublePoint[][] coords3, String[] labels, DBSCANClusterer dbscanClusterer
    ) {


        for (var e : tree.edges()) {
            var w = e.getTarget();
            if (e.getTarget().isLeaf()) {
                var label = new RichTextLabel(tree.getLabel(w));
                for (int i = 0; i < labels.length; i++) {
                    if (tree.getLabel(w).equals(labels[i])) {

                        var clusters = dbscanClusterer.cluster(Arrays.asList(coords3[i]));
                        Cluster cluster1 = (Cluster) clusters.get(0);
                        List<DoublePoint> cluster11 = cluster1.getPoints();


                        List<List<DoublePoint>> sort = new ArrayList<List<DoublePoint>>();

                        for (int j = 0; j < clusters.size(); j++) {
                            Cluster clusterJ = (Cluster) clusters.get(j);
                            sort.add(clusterJ.getPoints());
                        }

                        Collections.sort(sort, (Comparator<List>) (a1, a2) -> a2.size() - a1.size());

                        List<DoublePoint> big = sort.get(0);

                        double xsumB = 0;
                        double ysumB = 0;
                        for (DoublePoint p : big) {
                            var point = p.getPoint();
                            xsumB += point[0];
                            ysumB += point[1];
                        }


                        double xCenterB = xsumB / big.size();
                        double yCenterB = ysumB / big.size();

                        ChangeListener<Number> listener = (observableValue, oldValue, newValue) -> { // use a listener because we have to wait until both width and height have been set
                            if (oldValue.doubleValue() == 0 && newValue.doubleValue() > 0 && label.getWidth() > 0 && label.getHeight() > 0) {
                                var angle = nodeAngleMap.get(w);
                                var delta = GeometryUtilsFX.translateByAngle(-0.5 * label.getWidth(), -0.5 * label.getHeight(), angle, 0.5 * label.getWidth() + 5);
                                label.setLayoutX(xCenterB + delta.getX());
                                label.setLayoutY(yCenterB + delta.getY());
                                label.setRotate(angle);
                                label.ensureUpright();
                                label.setTextFill(Color.RED);
                            }
                        };
                        label.widthProperty().addListener(listener);
                        label.heightProperty().addListener(listener);
                        labelPane.getChildren().add(label);

                        if (sort.size() > 1) {
                            List<DoublePoint> small = sort.get(1);
                            double xsumS = 0;
                            double ysumS = 0;
                            for (DoublePoint p : small) {
                                var point = p.getPoint();
                                xsumS += point[0];
                                ysumS += point[1];
                            }

                            double xCenterS = xsumS / small.size();
                            double yCenterS = ysumS / small.size();

                            double xdist = xCenterB - xCenterS;
                            double ydist = yCenterB - yCenterS;
                            double distance = Math.sqrt(xdist * xdist + ydist * ydist);
                            if (tree.getLabel(w).equals("t33")) {
                                System.out.println();
                            }
                            if (small.size() > big.size() * 0.7 && distance > 50) {

                                var label1 = new RichTextLabel(tree.getLabel(w));

                                ChangeListener<Number> listener1 = (observableValue, oldValue, newValue) -> { // use a listener because we have to wait until both width and height have been set
                                    if (oldValue.doubleValue() == 0 && newValue.doubleValue() > 0 && label1.getWidth() > 0 && label1.getHeight() > 0) {
                                        var angle = nodeAngleMap.get(w);
                                        var delta = GeometryUtilsFX.translateByAngle(-0.5 * label1.getWidth(), -0.5 * label1.getHeight(), angle, 0.5 * label1.getWidth() + 5);
                                        label1.setLayoutX(xCenterS + delta.getX());
                                        label1.setLayoutY(yCenterS + delta.getY());
                                        label1.setRotate(angle);
                                        label1.ensureUpright();
                                        label1.setTextFill(Color.BLUE);
                                    }
                                };
                                label1.widthProperty().addListener(listener1);
                                label1.heightProperty().addListener(listener1);
                                labelPane.getChildren().add(label1);
                            }
                        }
                        break;
                    }
                }
            }
        }
    }

    public static void kmeansLabels(
            PhyloTree tree, Pane labelPane, NodeDoubleArray nodeAngleMap,
            DoublePoint[][] coords3, String[] labels, KMeansPlusPlusClusterer kmeansClusterer
    ) {

        for (var e : tree.edges()) {
            var w = e.getTarget();
            if (w.isLeaf()) {
                var label = new RichTextLabel(tree.getLabel(w));
                for (int i = 0; i < labels.length; i++) {
                    if (tree.getLabel(w).equals(labels[i])) {

                        var clusters = kmeansClusterer.cluster(Arrays.asList(coords3[i]));
                        CentroidCluster cluster1 = (CentroidCluster) clusters.get(0);
                        CentroidCluster cluster2 = (CentroidCluster) clusters.get(1);

                        List<DoublePoint> cluster11 = cluster1.getPoints();
                        List<DoublePoint> cluster21 = cluster2.getPoints();

                        CentroidCluster bigC;
                        CentroidCluster smallC;
                        List bigP;
                        List smallP;
                        if (cluster11.size() > cluster21.size()) {
                            bigC = cluster1;
                            smallC = cluster2;
                            bigP = cluster11;
                            smallP = cluster21;
                        } else {
                            bigC = cluster2;
                            smallC = cluster1;
                            bigP = cluster21;
                            smallP = cluster11;
                        }

                        var center = bigC.getCenter().getPoint();

                        double xcenter = center[0];
                        double ycenter = center[1];


                        ChangeListener<Number> listener = (observableValue, oldValue, newValue) -> { // use a listener because we have to wait until both width and height have been set
                            if (oldValue.doubleValue() == 0 && newValue.doubleValue() > 0 && label.getWidth() > 0 && label.getHeight() > 0) {
                                var angle = nodeAngleMap.get(w);
                                var delta = GeometryUtilsFX.translateByAngle(-0.5 * label.getWidth(), -0.5 * label.getHeight(), angle, 0.5 * label.getWidth() + 5);
                                label.setLayoutX(xcenter + delta.getX());
                                label.setLayoutY(ycenter + delta.getY());
                                label.setRotate(angle);
                                label.ensureUpright();
                                label.setTextFill(Color.RED);
                            }
                        };

                        label.widthProperty().addListener(listener);
                        label.heightProperty().addListener(listener);
                        labelPane.getChildren().add(label);


                        var center2 = smallC.getCenter().getPoint();

                        double xcenter2 = center2[0];
                        double ycenter2 = center2[1];

                        double xdist = xcenter - xcenter2;
                        double ydist = ycenter - ycenter2;
                        double distance = Math.sqrt(xdist * xdist + ydist * ydist);

                        if (smallP.size() > bigP.size() * 0.9 && distance > 50) {

                            var label1 = new RichTextLabel(tree.getLabel(w));

                            ChangeListener<Number> listener1 = (observableValue, oldValue, newValue) -> { // use a listener because we have to wait until both width and height have been set
                                if (oldValue.doubleValue() == 0 && newValue.doubleValue() > 0 && label1.getWidth() > 0 && label1.getHeight() > 0) {
                                    var angle = nodeAngleMap.get(w);
                                    var delta = GeometryUtilsFX.translateByAngle(-0.5 * label1.getWidth(), -0.5 * label1.getHeight(), angle, 0.5 * label1.getWidth() + 5);
                                    label1.setLayoutX(xcenter2 + delta.getX());
                                    label1.setLayoutY(ycenter2 + delta.getY());
                                    label1.setRotate(angle);
                                    label1.ensureUpright();
                                    label1.setTextFill(Color.BLUE);
                                }
                            };
                            label1.widthProperty().addListener(listener1);
                            label1.heightProperty().addListener(listener1);
                            labelPane.getChildren().add(label1);
                        }
                        break;
                    }
                }
            }
        }
    }

    public static void drawEdges(
            PhyloTree tree, GraphicsContext gc, NodeArray<Point2D> nodePointMap,
            boolean jitter, boolean block, double shiftx, double shifty, double[][] coords, String[] labels
    ) {

        for (var e : tree.edges()) {
            var v = e.getSource();
            var w = e.getTarget();
            var vPt = nodePointMap.get(v);
            var wPt = nodePointMap.get(w);

            if (vPt != null & wPt != null) {

                if (w.isLeaf()) {
                    for (int i = 0; i < labels.length; i++) {
                        if (tree.getLabel(w).equals(labels[i])) {
                            coords[i][0] += wPt.getX();
                            coords[i][1] += wPt.getY();
                            break;
                        }
                    }
                }

                double x1 = vPt.getX();
                double y1 = vPt.getY();
                double x2 = wPt.getX();
                double y2 = wPt.getY();

                if (jitter) {
                    x1 += shiftx;
                    y1 += shifty;
                    x2 += shiftx;
                    y2 += shifty;
                }

                if (block) {
                    gc.strokeLine(x1, y1, x1, y2);
                    gc.strokeLine(x1, y2, x2, y2);
                    if (v.getInDegree() == 0) {
                        gc.strokeLine(x1, y1, x1 - 25, y1);
                    }
                } else {
                    gc.strokeLine(x1, y1, x2, y2);
                }
            }
        }
    }

    public static void drawEdges1(
            PhyloTree tree, int treeNum, GraphicsContext gc, NodeArray<Point2D> nodePointMap,
            boolean jitter, boolean block, double shiftx, double shifty, double[][][] coords2, String[] labels
    ) {

        for (var e : tree.edges()) {
            var v = e.getSource();
            var w = e.getTarget();
            var vPt = nodePointMap.get(v);
            var wPt = nodePointMap.get(w);

            if (vPt != null & wPt != null) {

                if (w.isLeaf()) {
                    for (int i = 0; i < labels.length; i++) {
                        if (tree.getLabel(w).equals(labels[i])) {
                            coords2[i][treeNum][0] += wPt.getX();
                            coords2[i][treeNum][1] += wPt.getY();
                            break;
                        }
                    }
                }

                double x1 = vPt.getX();
                double y1 = vPt.getY();
                double x2 = wPt.getX();
                double y2 = wPt.getY();

                if (jitter) {
                    x1 += shiftx;
                    y1 += shifty;
                    x2 += shiftx;
                    y2 += shifty;
                }

                if (block) {
                    gc.strokeLine(x1, y1, x1, y2);
                    gc.strokeLine(x1, y2, x2, y2);
                    if (v.getInDegree() == 0) {
                        gc.strokeLine(x1, y1, x1 - 25, y1);
                    }
                } else {
                    gc.strokeLine(x1, y1, x2, y2);
                }
            }
        }
    }

    public static void drawEdges2(
            PhyloTree tree, int treeNum, GraphicsContext gc, NodeArray<Point2D> nodePointMap,
            boolean jitter, boolean block, double shiftx, double shifty, DoublePoint[][] coords3, String[] labels
    ) {

        for (var e : tree.edges()) {
            var v = e.getSource();
            var w = e.getTarget();
            var vPt = nodePointMap.get(v);
            var wPt = nodePointMap.get(w);

            if (vPt != null & wPt != null) {

                if (w.isLeaf()) {
                    for (int i = 0; i < labels.length; i++) {
                        if (tree.getLabel(w).equals(labels[i])) {
                            double[] points = {wPt.getX(), wPt.getY()};
                            DoublePoint point = new DoublePoint(points);
                            coords3[i][treeNum] = point;
                            break;
                        }
                    }
                }

                double x1 = vPt.getX();
                double y1 = vPt.getY();
                double x2 = wPt.getX();
                double y2 = wPt.getY();

                if (jitter) {
                    x1 += shiftx;
                    y1 += shifty;
                    x2 += shiftx;
                    y2 += shifty;
                }

                if (block) {
                    gc.strokeLine(x1, y1, x1, y2);
                    gc.strokeLine(x1, y2, x2, y2);
                    if (v.getInDegree() == 0) {
                        gc.strokeLine(x1, y1, x1 - 25, y1);
                    }
                } else {
                    gc.strokeLine(x1, y1, x2, y2);
                }
            }
        }
    }

    public static void adjustCoordinatesToBox(boolean maintainAspectRatio, NodeArray<Point2D> nodePointMap, double xMinTarget, int yMinTarget, double xMaxTarget, double yMaxTarget) {

        var xMin = nodePointMap.values().stream().mapToDouble(Point2D::getX).min().orElse(0);
        var xMax = nodePointMap.values().stream().mapToDouble(Point2D::getX).max().orElse(0);
        var yMin = nodePointMap.values().stream().mapToDouble(Point2D::getY).min().orElse(0);
        var yMax = nodePointMap.values().stream().mapToDouble(Point2D::getY).max().orElse(0);

        var scaleX = (xMaxTarget - xMinTarget) / (xMax - xMin);
        var scaleY = (yMaxTarget - yMinTarget) / (yMax - yMin);

        if (maintainAspectRatio)
            scaleX = scaleY = Math.min(scaleX, scaleY);

        for (var v : nodePointMap.keySet()) {
            var point = nodePointMap.get(v);
            nodePointMap.replace(v,
                    new Point2D((point.getX() - 0.5 * (xMin + xMax)) * scaleX, (point.getY() - 0.5 * (yMin + yMax)) * scaleY)
                            .add(0.5 * (xMinTarget + xMaxTarget), 0.5 * (yMinTarget + yMaxTarget)));
        }
    }

    /**
     * this contains all the parameters used for drawing
     */
    public record Parameters(boolean jitter,
                             boolean consensus,
                             String highlight,
                             String labelMethod,
                             String drawingMethod) {
    }
}