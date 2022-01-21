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

import javafx.beans.value.ChangeListener;
import javafx.geometry.Point2D;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.TextAlignment;
import javafx.util.Pair;
import jloda.fx.control.RichTextLabel;
import jloda.fx.util.GeometryUtilsFX;
import jloda.graph.Node;
import jloda.graph.NodeArray;
import jloda.phylo.PhyloTree;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * draw the densi-tree
 */
public class DensiTree {

    public static void clear(Canvas canvas, Pane pane) {
        var gc = canvas.getGraphicsContext2D();
        gc.restore();
        gc.clearRect(0, 0, canvas.getWidth(), canvas.getHeight());
        pane.getChildren().clear();
    }

    public static void draw(Parameters parameters, Model model, Canvas canvas, Pane pane) {
        System.out.println("Canvas:");
        System.err.println("Width: " + canvas.getWidth());
        System.err.println("Height: " + canvas.getHeight());

        var gc = canvas.getGraphicsContext2D();
        gc.setFont(Font.font("Courier New", 11));
        gc.clearRect(0, 0, canvas.getWidth(), canvas.getHeight());

        pane.getChildren().clear();


        System.out.println("Pane:");
        System.out.println("Width: " + pane.getWidth());
        System.out.println("Height: " + pane.getHeight());
//
//        if (model.getTreesBlock().size() > 0) {
//            gc.strokeText("nTax: " + model.getTaxaBlock().getNtax(), 20, 20);
//
//            var cx = 20;
//            var cy = 40;
//
//            for (int value : model.getCircularOrdering()) {
//                if (value > 0) {
//                    gc.strokeText(String.valueOf(value), cx, cy);
//                    cx += 20;
//                    if (cx > canvas.getWidth())
//                        break;
//                }
//            }
//
//            var tree = model.getTreesBlock().getTree(1);
//            var x = 20;
//            var y = 60;
//            for (var node : tree.nodes()) {
//                if (node.getLabel() != null) {
//                    gc.strokeText(StringUtils.toString(tree.getTaxa(node), " ") + ": " + node.getLabel(), x, y);
//                    y += 30;
//                    if (y > canvas.getHeight())
//                        break;
//                }
//            }
//        }


        int xmin = (int) 100;
        int ymin = (int) 100;
        int xmax = (int) (canvas.getWidth() -100);
        int ymax = (int) (canvas.getHeight() -100);

        boolean toScale = parameters.toScale;
        //int rmax = Math.min(x0, y0) - 100;

        int[] circle = model.getCircularOrdering();

        //Pair[] angles = getAngles(model);

        gc.setLineWidth(1.0);
        //drawLabels(model, rmax, x0, y0, gc);

        gc.save();
        //gc.translate(x0, y0);
        gc.setLineWidth(0.01);
        for (int i = 1; i <= model.getTreesBlock().size(); i++) {
            var tree = model.getTreesBlock().getTree(i);
            //drawEdges(angles, rmax, x0, y0, tree, tree.getRoot(), gc, calculateWeightSum(tree, tree.getRoot()));
            if(i == model.getTreesBlock().size()){
                drawLabels1(tree, circle, pane, xmin, ymin, xmax, ymax, toScale);
                gc.setLineWidth(1);
                gc.setStroke(Color.GREENYELLOW);
                drawEdges1(tree, circle, gc, xmin, ymin, xmax, ymax, toScale);
                gc.setLineWidth(0.01);
                gc.setStroke(Color.BLACK);
            }
            drawEdges1(tree, circle, gc, xmin, ymin, xmax, ymax, toScale);
        }
        gc.setLineWidth(1.0);
    }

    public static double calculateWeightSum(PhyloTree tree, Node v) {
        if (v.isLeaf()) {
            return 0;
        }
        return calculateWeightSum(tree, v.getNext()) + tree.getWeight(v.getFirstAdjacentEdge());
    }

    public static void drawLabels(Model model, int rmax, int x0, int y0, GraphicsContext gc) {
        var counter = 0;
        double x;
        double y;
        rmax += 10;
        var tree = model.getTreesBlock().getTree(1);
        //gc.setTextAlign(TextAlignment.CENTER);
        for (int value : model.getCircularOrdering()) {
            if (value > 0) {
                counter++;
                var angle = (2 * Math.PI * counter) / model.getTaxaBlock().getNtax();

                x = x0 + rmax * Math.sin(angle);
                y = y0 + rmax * Math.cos(angle);

                gc.save();
                gc.translate(x, y);
                if (Math.toDegrees(angle) % 360 > 180) {
                    gc.setTextAlign(TextAlignment.RIGHT);
                    gc.rotate(Math.toDegrees(-angle) + 90 + 180);
                } else {
                    gc.rotate(Math.toDegrees(-angle) + 90);
                }
                gc.strokeText(tree.getTaxon2Node(value).getLabel(), 0, 0);
                gc.restore();

            }
        }
    }


    public static Pair[] getAngles(Model model) {
        var counter = 0;
        var tree = model.getTreesBlock().getTree(1);
        Pair[] angles = new Pair[model.getTaxaBlock().getNtax()];
        for (int value : model.getCircularOrdering()) {
            if (value > 0) {
                counter++;
                var angle = (2 * Math.PI * counter) / model.getTaxaBlock().getNtax();
                angles[counter - 1] = new Pair(tree.getTaxon2Node(value).getLabel(), angle);
            }
        }
        return angles;
    }

    public static double getAngle(Pair[] angles, Node v) {
        int pos = 0;
        for (var i : angles) {
            if (Objects.equals(i.getKey(), v.getLabel())) {
                return (double) i.getValue();
            }
        }
        return 0;
    }

    public static double[] drawEdges(Pair[] angles, int rmax, int x0, int y0, PhyloTree tree, Node v, GraphicsContext gc, double weightSum) {
        double r;
        double phi = 0;
        double[] rp = new double[2];
        if (v.isLeaf()) {
            rp[0] = rmax;
            rp[1] = getAngle(angles, v);
            return rp;
        }
        List<Double> rs = new ArrayList<>(v.getOutDegree());
        List<Double> phis = new ArrayList<>(v.getOutDegree());
        double sinphi = 0;
        double cosphi = 0;

        for (var w : v.outEdges()) {
            rp = drawEdges(angles, rmax, x0, y0, tree, w.getTarget(), gc, weightSum);
            rs.add(rp[0]);
            sinphi += Math.sin(rp[1]);
            cosphi += Math.cos(rp[1]);
            phis.add(rp[1]);
        }
        sinphi /= v.getOutDegree();
        cosphi /= v.getOutDegree();

        if ((sinphi > 0) && (cosphi > 0)) {
            phi = Math.atan(sinphi / cosphi);
        } else if (cosphi < 0) {
            phi = Math.atan(sinphi / cosphi) + Math.PI;
        } else if ((sinphi < 0) && (cosphi > 0)) {
            phi = Math.atan(sinphi / cosphi) + Math.PI * 2;
        }

        r = rs.get(0) - (rmax * tree.getWeight(v.getFirstOutEdge()) / weightSum);
        double startx = r * Math.sin(phi);
        double starty = r * Math.cos(phi);
        double endx;
        double endy;
        for (int k = 0; k < v.getOutDegree(); k++) {
            endx = rs.get(k) * Math.sin(phis.get(k));
            endy = rs.get(k) * Math.cos(phis.get(k));
            gc.strokeLine(x0 + startx, y0 + starty, x0 + endx, y0 + endy);
        }
        rp[0] = r;
        rp[1] = phi;
        return rp;

    }

    public static void drawLabels1(PhyloTree tree, int[] circle, Pane pane, int xmin, int ymin, int xmax, int ymax, boolean toScale){
        NodeArray<Point2D> nodePointMap = tree.newNodeArray();
        var nodeAngleMap = tree.newNodeDoubleArray();
        LayoutAlgorithm.apply(tree, toScale, circle, nodePointMap, nodeAngleMap);
        adjustCoordinatesToBox(nodePointMap, xmin, ymin, xmax, ymax);

        for (var e : tree.edges()) {
            var v = e.getSource();
            var w = e.getTarget();
            var vPt = nodePointMap.get(v);
            var wPt = nodePointMap.get(w);
            if (e.getTarget().isLeaf()) {
                var label = new RichTextLabel(tree.getLabel(w));
                ChangeListener<Number> listener = (observableValue, oldValue, newValue) -> { // use a listener because we have to wait until both width and height have been set
                    if (oldValue.doubleValue() == 0 && newValue.doubleValue() > 0 && label.getWidth() > 0 && label.getHeight() > 0) {
                        var angle = nodeAngleMap.get(w);
                        var delta = GeometryUtilsFX.translateByAngle(-0.5 * label.getWidth(), -0.5 * label.getHeight(), angle, 0.5 * label.getWidth() + 5);
                        label.setLayoutX(wPt.getX() + delta.getX());
                        label.setLayoutY(wPt.getY() + delta.getY());
                        label.setRotate(angle);
                        label.ensureUpright();
                        label.setTextFill(Color.RED);
                    }
                };
                label.widthProperty().addListener(listener);
                label.heightProperty().addListener(listener);
                pane.getChildren().add(label);
            }
        }
    }

    public static void drawEdges1(PhyloTree tree, int[] circle, GraphicsContext gc, int xmin, int ymin, int xmax, int ymax, boolean toScale) {
        NodeArray<Point2D> nodePointMap = tree.newNodeArray();
        var nodeAngleMap = tree.newNodeDoubleArray();
        LayoutAlgorithm.apply(tree, toScale, circle, nodePointMap, nodeAngleMap);
        adjustCoordinatesToBox(nodePointMap, xmin, ymin, xmax, ymax);

        for (var e : tree.edges()) {
            var v = e.getSource();
            var w = e.getTarget();
            var vPt = nodePointMap.get(v);
            var wPt = nodePointMap.get(w);

            gc.strokeLine(vPt.getX(), vPt.getY(), wPt.getX(), wPt.getY());
        }

    }

    public static void adjustCoordinatesToBox(NodeArray<Point2D> nodePointMap, double xMinTarget, int yMinTarget, double xMaxTarget, double yMaxTarget) {

        var xMin = nodePointMap.values().stream().mapToDouble(Point2D::getX).min().orElse(0);
        var xMax = nodePointMap.values().stream().mapToDouble(Point2D::getX).max().orElse(0);
        var yMin = nodePointMap.values().stream().mapToDouble(Point2D::getY).min().orElse(0);
        var yMax = nodePointMap.values().stream().mapToDouble(Point2D::getY).max().orElse(0);

        var scaleX = (xMaxTarget - xMinTarget) / (xMax - xMin);
        var scaleY = (yMaxTarget - yMinTarget) / (yMax - yMin);
        var scale = Math.min(scaleX, scaleY);

        for (var v : nodePointMap.keySet()) {
            var point = nodePointMap.get(v);
            nodePointMap.put(v, point.multiply(scale).add(0.5 * (xMinTarget + xMaxTarget), 0.5 * (yMinTarget + yMaxTarget)));
        }
    }

    /**
     * this contains all the parameters used for drawing
     */
    public record Parameters(boolean toScale) {
    }
}