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

package splitstree6.xtra.genetreeview;

import javafx.beans.property.*;
import javafx.collections.ObservableList;
import javafx.scene.Node;
import javafx.scene.PerspectiveCamera;
import javafx.scene.control.Slider;
import javafx.scene.transform.Rotate;

public class StackLayout extends MultipleFramesLayout{

    private final LayoutType type = LayoutType.Stack;
    private final PerspectiveCamera camera;
    private final BooleanProperty isSnapshot = new SimpleBooleanProperty(false);
    private final double layoutWidth;
    private final double nodeWidth;
    private final double nodeHeight;

    public StackLayout(ObservableList<Node> nodes, ObservableList<Node> snapshots, double nodeWidth, double nodeHeight,
                       PerspectiveCamera camera, double layoutWidth, Slider slider, Slider zoomSlider) {
        this.layoutWidth = layoutWidth;
        this.nodeWidth = nodeWidth;
        this.nodeHeight = nodeHeight;

        // Transforming nodes
        assert nodes.size() == snapshots.size();
        initializeNodes(nodes, slider);
        initializeNodes(snapshots, slider);
        transformedNodes = nodes;
        transformedSnapshots = snapshots;

        // Setting up zoomSlider
        setUpZoomSlider(zoomSlider, -700, -480);

        // Transforming camera
        resetCamera(camera);
        camera.setFarClip(1000);
        camera.setNearClip(100);
        camera.setTranslateY(0);
        camera.translateZProperty().bind(zoomSlider.valueProperty());
        this.camera = camera;
        updatePosition(1,slider.getValue(),layoutWidth,nodeWidth);
    }

    public void updatePosition(double oldSliderValue, double newSliderValue, double layoutWidth, double nodeWidth) {
        ObservableList<Node> nodesToTransform;
        if (isSnapshot.get()) nodesToTransform = transformedSnapshots;
        else nodesToTransform = transformedNodes;
        for (Node node : nodesToTransform) {
            // The positionalDistance is the desired distance between node and focus-position (in the middle),
            // assuming distance of 1 between neighboring nodes and slider values starting with 1
            double positionalDistance = nodesToTransform.indexOf(node)-newSliderValue+1;
            transformNode(node, positionalDistance, nodeWidth);
        }
    }

    private void initializeNodes(ObservableList<Node> nodes, Slider slider) {
        for (int i=0; i<nodes.size(); i++) {
            Node node = nodes.get(i);
            initializeNode(node,i,slider.getValue());
        }
    }

    public void initializeNode(Node node, int index, double sliderValue) {
        resetNode(node);
        node.setRotationAxis(Rotate.Y_AXIS);
        node.setTranslateY(-nodeHeight/2.);
        double positionalDistance = index-sliderValue+1;
        transformNode(node, positionalDistance, nodeWidth);
    }

    private void transformNode(Node node, double x, double nodeWidth) {
        // Translate X
        //var functionForX = (1.045/(1.+Math.exp(-1.028*x))-0.522);
        var functionForX = (1.285/(1+Math.exp(-0.767*x))-0.642);
        node.setTranslateX((layoutWidth) * functionForX - (nodeWidth/2.));

        // Rotation
        node.setRotate((179./(1+Math.exp(-1.15*x)))-89.); // layout draft 4
        //node.setRotate((190./(1.+Math.exp(0.2*x)))-102.); // layout draft 3

        // Scaling size
        final var scalingFunction = 0.9 / (Math.exp(x) + Math.exp(-x)) + 0.7;
        node.setScaleX(scalingFunction);
        node.setScaleY(scalingFunction);

        //node.setOpacity(1-(Math.abs(x)/5)); // works only for snapshots
    }

    public PerspectiveCamera getCamera() {
        return camera;
    }

    void setSliderDragged(boolean isDragged) {
        isSnapshot.set(isDragged);
    }

    public LayoutType getType() {return type;}
}
