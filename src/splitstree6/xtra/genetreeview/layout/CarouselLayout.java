/*
 *  CarouselLayout.java Copyright (C) 2023 Daniel H. Huson
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

import javafx.beans.binding.Bindings;
import javafx.beans.binding.DoubleBinding;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.collections.ObservableList;
import javafx.scene.Node;
import javafx.scene.PerspectiveCamera;
import javafx.scene.control.Slider;
import javafx.scene.transform.Rotate;

public class CarouselLayout extends MultipleFramesLayout {

    private final PerspectiveCamera camera;
    private final Slider slider;
    private final Slider zoomSlider;
    private final double nodeWidth;
    private final double nodeHeight;
    private int realNodeNumber;
    private double thetaDeg;
    private double thetaRad;
    private double layoutRadius;
    private final DoubleProperty cameraRadius = new SimpleDoubleProperty();

    public CarouselLayout(ObservableList<Node> nodes, double nodeWidth, double nodeHeight, PerspectiveCamera camera,
                          double layoutWidth, Slider slider, Slider zoomSlider) {
        type = LayoutType.Carousel;
        // Setting up variables
        this.nodeWidth = nodeWidth;
        this.nodeHeight = nodeHeight;
        this.slider = slider;
        this.zoomSlider = zoomSlider;
        setUpZoomSlider(zoomSlider, -1200, -620);
        this.camera = camera;
        transformedNodes = nodes;

        initializeLayout();
    }

    public void initializeLayout() {
        realNodeNumber = transformedNodes.size();
        // For less than 50 trees, it makes no sense to arrange them in a circle, but in a partial circle
        int layoutNodeNumber = Math.max(realNodeNumber, 50); // assuming at least 50 trees for the carousel size

        layoutRadius = (1.1 * nodeWidth * layoutNodeNumber) / (2 * Math.PI);
        thetaDeg = 360 / (double) layoutNodeNumber;
        thetaRad = Math.toRadians(thetaDeg);

        for (int i=0; i<transformedNodes.size(); i++) {
            Node n = transformedNodes.get(i);
            initializeNode(n,i,slider.getValue());
        }

        setUpCamera(); // for x- and z-translation

        updatePosition(1,slider.getValue(),nodeWidth); // for camera rotation
    }

    // Allows to initialize a node with existing index (0-based) or to add a node with the next index
    public void initializeNode(Node node, int index, double sliderValue) {
        if (index > realNodeNumber) return;
        if (index == realNodeNumber && realNodeNumber > 49) {
            // The layout needs to be expanded for the additional node -> larger carousel
            initializeLayout();
        }
        else {
            resetNode(node);
            node.setTranslateX(layoutRadius * Math.sin(index * thetaRad) - (Math.cos(index * thetaRad) * (nodeWidth / 2.)));
            node.setTranslateY(-nodeHeight / 2.);
            node.setTranslateZ(-layoutRadius * Math.cos(index * thetaRad) - (Math.sin(index * thetaRad) * (nodeWidth / 2.)));
            Rotate rotate = new Rotate(-index * thetaDeg, 0, node.getTranslateY(), 0, Rotate.Y_AXIS);
            node.getTransforms().add(rotate);

            // Show node larger and without rotation when hovered
            node.setOnMouseEntered(e -> {
                // Move node towards camera
                double deltaX = camera.getTranslateX() - node.getTranslateX();
                double deltaZ = camera.getTranslateZ() - node.getTranslateZ();
                double distanceNode2Camera = Math.sqrt((deltaX * deltaX) + (deltaZ * deltaZ));
                double desiredMoveDistance = 140;
                double fraction = desiredMoveDistance / distanceNode2Camera;
                node.setTranslateX(node.getTranslateX() + (deltaX * fraction));
                node.setTranslateZ(node.getTranslateZ() + (deltaZ * fraction));
                rotate.setAngle((-slider.getValue() + 1) * thetaDeg); // angle of tree in focus position
            });
            node.setOnMouseExited(e -> {
                // Reset to standard transformation
                node.setTranslateX(layoutRadius * Math.sin(index * thetaRad) - (Math.cos(index * thetaRad) * (nodeWidth / 2.)));
                node.setTranslateZ(-layoutRadius * Math.cos(index * thetaRad) - (Math.sin(index * thetaRad) * (nodeWidth / 2.)));
                rotate.setAngle(-index * thetaDeg);
            });
        }
    }

    public void updatePosition(double oldSliderValue, double newSliderValue, double nodeWidth) {
        var rotate = new Rotate(-(newSliderValue-oldSliderValue)*thetaDeg,Rotate.Y_AXIS);
        camera.getTransforms().add(rotate);
    }

    // Transforming camera -> moving on a larger circle (cameraRadius) around the nodes, facing inside
    private void setUpCamera() {
        resetCamera(camera);
        if (cameraRadius.isBound()) cameraRadius.unbind();
        camera.setNearClip(0.2);
        camera.setFarClip(1800);
        camera.setTranslateY(0);
        // x and z position of the camera are managed with bindings
        DoubleBinding xTerm = Bindings.createDoubleBinding(
                () -> cameraRadius.get()*Math.sin((slider.getValue()-1)*thetaRad),
                cameraRadius,slider.valueProperty()
        );
        camera.translateXProperty().bind(xTerm);
        DoubleBinding zTerm = Bindings.createDoubleBinding(
                () -> cameraRadius.get()*Math.cos((slider.getValue()-1)*thetaRad)*-1,
                cameraRadius,slider.valueProperty()
        );
        camera.translateZProperty().bind(zTerm);

        cameraRadius.bind(zoomSlider.valueProperty().multiply(-1).add(layoutRadius));
    }

    public PerspectiveCamera getCamera() {
        return camera;
    }
}
