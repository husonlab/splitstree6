/*
 *  StackLayout.java Copyright (C) 2023 Daniel H. Huson
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

package splitstree6.xtra.genetreeview.layout;

import javafx.beans.property.*;
import javafx.collections.ObservableList;
import javafx.scene.Node;
import javafx.scene.PerspectiveCamera;
import javafx.scene.control.Slider;
import javafx.scene.transform.Rotate;

public class StackLayout extends MultipleFramesLayout{

    private final PerspectiveCamera camera;
    private final BooleanProperty isSnapshot = new SimpleBooleanProperty(false);
    private final Slider slider;
    private final Slider zoomSlider;
    private final ReadOnlyDoubleProperty layoutWidthProperty;
    private final ReadOnlyDoubleProperty layoutHeightProperty;
    private final double nodeWidth;
    private final double nodeHeight;
    private int focusedNodeIndex;

    public StackLayout(ObservableList<Node> nodes, ObservableList<Node> snapshots, double nodeWidth, double nodeHeight,
                       ReadOnlyDoubleProperty layoutWidthProperty, ReadOnlyDoubleProperty layoutHeightProperty,
                       PerspectiveCamera camera, Slider slider, Slider zoomSlider) {
        type = LayoutType.Stack;
        this.layoutWidthProperty = layoutWidthProperty;
        this.layoutHeightProperty = layoutHeightProperty;
        this.nodeWidth = nodeWidth;
        this.nodeHeight = nodeHeight;
        this.slider = slider;
        this.zoomSlider = zoomSlider;
        focusedNodeIndex = -1;

        // Transforming nodes
        initializeNodes(nodes);
        if (snapshots!=null) initializeNodes(snapshots); // initial layout possible without snapshots
        transformedNodes = nodes;
        transformedSnapshots = snapshots;

        // Setting up zoomSlider
        setUpZoomSlider(zoomSlider, 1, 1.2);

        // Transforming camera
        resetCamera(camera);
        camera.setFarClip(3000);
        camera.setNearClip(0.1);
        camera.setTranslateY(0);
        camera.translateZProperty().bind(layoutHeightProperty.multiply(-2.6));
        this.camera = camera;
        updatePosition(1,slider.getValue());
    }

    public void updatePosition(double oldSliderValue, double newSliderValue) {
        ObservableList<Node> nodesToTransform;
        if (isSnapshot.get()) nodesToTransform = transformedSnapshots;
        else nodesToTransform = transformedNodes;
        for (Node node : nodesToTransform) {
            // The positionalDistance is the desired distance between node and focus-position (in the middle),
            // assuming distance of 1 between neighboring nodes and slider values starting with 1
            double positionalDistance = nodesToTransform.indexOf(node)-newSliderValue+1;
            transformNode(node, positionalDistance);
        }
    }

    private void initializeNodes(ObservableList<Node> nodes) {
        for (int i=0; i<nodes.size(); i++) {
            Node node = nodes.get(i);
            initializeNode(node, i);
        }
    }

    public void initializeNode(Node node, int index) {
        resetNode(node);
        node.setRotationAxis(Rotate.Y_AXIS);
        node.setTranslateY(-nodeHeight/2.);
        double positionalDistance = index-slider.getValue()+1;
        transformNode(node, positionalDistance);
    }

    private void transformNode(Node node, double x) {
        // Translate X
        var functionForX = (1.285/(1+Math.exp(-0.767*x))-0.642); // returns a value between 0 and 1
        node.translateXProperty().unbind();
        node.translateXProperty().bind(layoutWidthProperty.multiply(functionForX).subtract(nodeWidth/2.));

        // Rotation
        var rotate = (179.925/(1+Math.exp(-1.148*x)))-89.463;
        node.setRotate(rotate);

        // Scaling size: larger nodes in the center
        final var scalingFunction = 0.9 / (Math.exp(x) + Math.exp(-x)) + 0.7;
        node.scaleXProperty().unbind();
        node.scaleXProperty().bind(layoutWidthProperty.multiply(0.24).divide(nodeWidth).multiply(scalingFunction));
        node.scaleYProperty().unbind();
        node.scaleYProperty().bind(layoutHeightProperty.divide(nodeHeight).multiply(scalingFunction).multiply(zoomSlider.valueProperty()));

        int index = -1;
        if (transformedNodes != null && transformedNodes.contains(node)) index = transformedNodes.indexOf(node);
        else if (transformedSnapshots != null && transformedSnapshots.contains(node)) index = transformedSnapshots.indexOf(node);

        if (focusedNodeIndex == index & focusedNodeIndex != -1) {
            node.setTranslateZ(-100);
            node.setRotate(0);
        }

        // Show node closer and without rotation when hovered
        int finalIndex = index;
        node.setOnMouseEntered(e -> {
            node.setTranslateZ(-100);
            node.setRotate(0);
            focusedNodeIndex = finalIndex;
        });
        node.setOnMouseExited(e -> {
            node.setTranslateZ(0);
            node.setRotate(rotate);
            focusedNodeIndex = -1;
        });
    }

    public PerspectiveCamera getCamera() {
        return camera;
    }

    public void setSliderDragged(boolean isDragged) {
        isSnapshot.set(isDragged);
    }
}
