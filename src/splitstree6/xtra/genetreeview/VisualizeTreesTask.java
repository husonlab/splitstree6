/*
 *  VisualizeTreesTask.java Copyright (C) 2023 Daniel H. Huson
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

package splitstree6.xtra.genetreeview;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.concurrent.Task;
import javafx.geometry.Point2D;
import javafx.scene.Group;
import javafx.scene.control.Label;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import jloda.fx.control.RichTextLabel;
import jloda.graph.NodeArray;
import jloda.phylo.PhyloTree;
import splitstree6.data.TreesBlock;
import splitstree6.layout.tree.*;

import java.util.HashMap;
import java.util.function.Function;

public class VisualizeTreesTask extends Task<Group> {

    private final TreesBlock treesBlock;
    private final double treeWidth;
    private final double treeHeight;
    private final TreeDiagramType diagram;

    public VisualizeTreesTask(TreesBlock treesBlock, double treeWidth, double treeHeight, TreeDiagramType diagram) {
        this.treesBlock = treesBlock;
        this.treeWidth = treeWidth;
        this.treeHeight = treeHeight;
        this.diagram = diagram;
    }

    @Override
    protected Group call() throws Exception {
        Group trees = new Group();
        int treeIndex = 0;
        for (PhyloTree tree : treesBlock.getTrees()) {
            Function<Integer, StringProperty> taxonLabelMap = (taxonId) ->
                    new SimpleStringProperty(tree.getTaxon2Node(taxonId).getLabel());

            Group treeVis = createTreeBackground(tree.getName());

            Group layoutedTree = ComputeTreeLayout.apply(tree, tree.getNumberOfTaxa(), taxonLabelMap, diagram,
                    HeightAndAngles.Averaging.ChildAverage,treeWidth,treeHeight-15,false,
                    new HashMap<>(),new HashMap<>()).getAllAsGroup();

            if (diagram.isRadialOrCircular()) {
                layoutedTree.setTranslateX(treeWidth/2);
                layoutedTree.setTranslateY(treeHeight/2);
            }
            else if (diagram.isPhylogram()) { // for rectangular phylogram
                layoutedTree.setTranslateX(5);
            }
            else { // for rectangular and triangular cladogram
                layoutedTree.setTranslateX(treeWidth-33);
            }
            // TODO: adjust/debug font sizes and visibility of labels
            treeVis.getChildren().addAll(layoutedTree);


            // Most of the following code is not used as almost everything is done with ComputeTreeLayout.apply() now
            /*final NodeArray<Point2D> treeNodes = switch (diagram) {
                case RectangularCladogram -> LayoutTreeRectangular.apply(tree, false, HeightAndAngles.Averaging.ChildAverage);
                case RectangularPhylogram -> LayoutTreeRectangular.apply(tree, true, HeightAndAngles.Averaging.ChildAverage);
                case CircularCladogram -> null;
                case CircularPhylogram -> null;
                case TriangularCladogram -> LayoutTreeTriangular.apply(tree);
                case RadialCladogram -> null;
                case RadialPhylogram -> LayoutTreeRadial.apply(tree);
                //case RadialCladogram, CircularCladogram -> LayoutTreeCircular.apply(tree, nodeAngleMap, false, averaging);
                //case CircularPhylogram -> LayoutTreeCircular.apply(tree, nodeAngleMap, true, averaging);
            };

            assert treeNodes != null;
            double[] factors = calculateScalingFactors(treeNodes);
            double scalingFactorX = factors[0];
            double scalingFactorY = factors[1];
            double offsetX = factors[2];
            double offsetY = factors[3];

            var treeVis = createTreeBackground(tree.getName());
            Map<jloda.graph.Node,LabeledNodeShape> nodeShapeMap = new HashMap<>();

            // Circles for nodes of the tree
            for (jloda.graph.Node node : tree.nodes()) {
                var point2D = treeNodes.get(node);
                var nodeName = node.getLabel();
                var labeledNodeShape = drawNode(point2D,nodeName,scalingFactorX,scalingFactorY,offsetX,offsetY);
                nodeShapeMap.put(node,labeledNodeShape);
                treeVis.getChildren().addAll(labeledNodeShape,labeledNodeShape.getLabel());
                var children = node.children();
                for (jloda.graph.Node child : children) {
                    var point2Child = treeNodes.get(child);
                    var edge = drawEdge(point2D, point2Child, scalingFactorX, scalingFactorY, offsetX, offsetY);
                    treeVis.getChildren().addAll(edge);
                }
                //if (node.isLeaf()) treeVis.getChildren().addAll(drawLeaf(point2D, node.getLabel(), scalingFactorX, scalingFactorY, offsetX, offsetY));
            }
            if (diagram == TreeDiagramType.RectangularCladogram | diagram == TreeDiagramType.RectangularPhylogram) {
                LayoutLabelsRectangular.apply(tree,nodeShapeMap,3,null);
            }*/
            trees.getChildren().add(treeIndex, treeVis);
            treeIndex++;
            updateProgress(treeIndex,treesBlock.getNTrees());
        }
        return trees;
    }

    private double[] calculateScalingFactors(NodeArray<Point2D> treeNodes) {
        // Finding the maximal height and width of the unscaled tree
        double minX = 0;
        double maxX = 0;
        double minY = 0;
        double maxY = 0;
        for (Point2D point : treeNodes) {
            if (point.getX() < minX) minX = point.getX();
            else if (point.getX() > maxX) maxX = point.getX();
            if (point.getY() < minY) minY = point.getY();
            else if (point.getY() > maxY) maxY = point.getY();
        }
        double unscaledWidth = maxX-minX;
        double unscaledHeight = maxY-minY;

        var borderWidth = 5.;
        var spaceForTreeLabel = 10.;

        // Code for images of equal width and height (quadratic)
        double acceptableLeafLabelLength = 130;
        double scalingFactorX = (treeWidth -borderWidth- acceptableLeafLabelLength)/unscaledWidth;
        double scalingFactorY = (treeHeight -borderWidth-spaceForTreeLabel)/unscaledHeight;
        if (diagram.isRadialOrCircular()) {
            scalingFactorX *= 0.5;
            scalingFactorY *= 0.5;
        }
        double treeWidthWithoutLabels = unscaledWidth*scalingFactorX;
        double treeHeightWithoutLabels = unscaledHeight*scalingFactorY;
        var offsetX = treeWidthWithoutLabels+borderWidth;
        var offsetY = -borderWidth+spaceForTreeLabel;
        if (diagram.isRadialOrCircular()) {
            offsetY = treeHeightWithoutLabels;
        }

		/*// Code for images of equal width and variable height
		double scalingFactor = (treeSizeX-borderWidth-acceptableLeafLabelLength)/unscaledWidth;
		if (diagram.isRadialOrCircular()) scalingFactor*= 0.5;
		double treeWidthWithoutLabels = unscaledWidth*scalingFactor;
		double treeHeightWithoutLabels = unscaledHeight*scalingFactor;
		treeSizeY = treeHeightWithoutLabels+spaceForTreeLabel;
		var offsetX = treeWidthWithoutLabels+borderWidth;
		var offsetY = -borderWidth+spaceForTreeLabel;
		if (diagram.isRadialOrCircular()) {
			offsetY = treeHeightWithoutLabels;
		}*/

		/* // Code for images of equal height and variable width -> sometimes too broad
		double scalingFactor = (treeSizeY-borderWidth-spaceForTreeLabel)/unscaledHeight;
		if (diagram.isRadialOrCircular()) scalingFactor*= 0.5;
		double treeWidthWithoutLabels = unscaledWidth*scalingFactor;
		double treeHeightWithoutLabels = unscaledHeight*scalingFactor;
		treeSizeX = treeWidthWithoutLabels+acceptableLeafLabelLength-3;
		var offsetX = treeWidthWithoutLabels+borderWidth;
		var offsetY = -borderWidth+spaceForTreeLabel; // additional space for tree label;
		if (diagram.isRadialOrCircular()) {
			offsetY = treeHeightWithoutLabels;
		}*/
        return new double[]{scalingFactorX,scalingFactorY,offsetX,offsetY};
    }

    private Group createTreeBackground(String treeName) {
        var treeBackground = new Group();
        var backgroundRectangle = new Rectangle(treeWidth, treeHeight, Color.WHITE);
        //backgroundRectangle.setOpacity(0.5); // not working in 3D
        //backgroundRectangle.setStroke(Color.BLACK);
        //backgroundRectangle.setStrokeWidth(1);
        backgroundRectangle.setTranslateZ(2); // to avoid conflicts with the on top drawn tree later
        var textLabel = new Text(treeName);
        textLabel.setFont(new Font(10));
        textLabel.setY(10);
        var label = new Label(treeName);
        label.setFont(new Font(10));
        treeBackground.getChildren().addAll(backgroundRectangle,textLabel);
        return treeBackground;
    }

    private LabeledNodeShape drawNode(Point2D point2D, String nodeName, double scalingFactorX, double scalingFactorY, double offsetX, double offsetY) {
        var circle = new Circle(point2D.getX()*scalingFactorX+offsetX,
                point2D.getY()*scalingFactorY+offsetY,1,Color.BLACK);
        var nodeLabel = new RichTextLabel(nodeName);
        nodeLabel.setFontSize(7);
        return new LabeledNodeShape(new RichTextLabel(nodeName),circle);
    }

    private LabeledNodeShape drawLeaf(Point2D point2D, String leafName, double scalingFactorX, double scalingFactorY, double offsetX, double offsetY) {
        var leaf = new Group();
        var circle = drawNode(point2D, leafName,scalingFactorX,scalingFactorY,offsetX,offsetY);
        var leafTextLabel = new Text(leafName);
        leafTextLabel.setFont(new Font(7));
        var leafLabel = new Label(leafName);
        leafLabel.setFont(new Font(7));
        var leafLabelRich = new RichTextLabel(leafName);
        double x = point2D.getX()*scalingFactorX+offsetX;
        double y = point2D.getY()*scalingFactorY+offsetY;
        if (diagram.isRadialOrCircular()) {
            System.out.println("radial with leaf width "+leafLabel.getText().length()*3.8);
            if (x < 0.5* treeWidth) {
                x -= (leafLabel.getText().length()*3.8);
            }
            if (y < 0.4* treeHeight) {
                y -= (8);
                //if (x < 0.7*treeSizeX) x-= (leafLabel.getText().length()*3*0.5);
            }
            else if (y > 0.6* treeHeight) y += (7);
        }
        leafTextLabel.setTranslateX(x+3);
        leafTextLabel.setTranslateY(y+2);
        leafLabel.setTranslateX(x+3);
        leafLabel.setTranslateY(y-5);
        leaf.getChildren().addAll(circle,leafTextLabel);
        return new LabeledNodeShape(leafLabelRich,circle);
    }

    private LabeledEdgeShape drawEdge(Point2D point1, Point2D point2, double scalingFactorX, double scalingFactorY, double offsetX, double offsetY) {
        LabeledEdgeShape edgeGroup = new LabeledEdgeShape();
        var line = new Line(point1.getX()*scalingFactorX+offsetX,point1.getY()*scalingFactorY+offsetY,
                point2.getX()*scalingFactorX+offsetX,point2.getY()*scalingFactorY+offsetY);
        line.setFill(Color.BLACK);
        if (diagram == TreeDiagramType.RectangularCladogram | diagram == TreeDiagramType.RectangularPhylogram) {
            var line2 = new Line(line.getStartX(), line.getEndY(),line.getEndX(),line.getEndY());
            line2.setFill(Color.BLACK);
            line.setEndX(line.getStartX());
            edgeGroup.getChildren().add(line2);
        }
        edgeGroup.getChildren().add(line);
        return edgeGroup;
    }
}
