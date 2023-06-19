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

package splitstree6.xtra.genetreeview;

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

    private final LayoutType type = LayoutType.Carousel;
    private final PerspectiveCamera camera;
    private final double nodeWidth;
    private final double nodeHeight;
    private final double thetaDeg;
    private final double thetaRad;
    private final double layoutRadius;
    private final DoubleProperty cameraRadius = new SimpleDoubleProperty();

    public CarouselLayout(ObservableList<Node> nodes, double nodeWidth, double nodeHeight, PerspectiveCamera camera,
                          double layoutWidth, Slider slider, Slider zoomSlider) {
        // Setting up variables
        this.nodeWidth = nodeWidth;
        this.nodeHeight = nodeHeight;
        // For less than 50 trees, it makes no sense to arrange them in a circle, but in a partial circle
        int realNodeNumber = nodes.size();
        int layoutNodeNumber = Math.max(realNodeNumber, 50); // assuming at least 50 trees for the carousel size
        layoutRadius = (1.1 * nodeWidth * layoutNodeNumber) / (2 * Math.PI);
        thetaDeg = 360 / (double) layoutNodeNumber;
        thetaRad = Math.toRadians(thetaDeg);

        // Transforming nodes -> circular layout (with layoutRadius) in x-z-plane with nodes facing outside
        for (int i=0; i<realNodeNumber; i++) {
            Node node = nodes.get(i);
            initializeNode(node,i,slider.getValue());
        }
        transformedNodes = nodes;

        // Setting up zoomSlider
        setUpZoomSlider(zoomSlider, -1200, -500);
        cameraRadius.bind(zoomSlider.valueProperty().multiply(-1).add(layoutRadius));

        // Transforming camera -> moving on a larger circle (cameraRadius) around the nodes, facing inside
        resetCamera(camera);
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
        this.camera = camera;
        // Rotation of the camera is managed with updatePosition(),
        // called by the listener of the slider in GeneTreeViewPresenter
        updatePosition(1,slider.getValue(),layoutWidth,nodeWidth);
    }

    public void initializeNode(Node node, int index, double sliderValue) {
        resetNode(node);
        node.setTranslateX(layoutRadius*Math.sin(index*thetaRad)-(Math.cos(index*thetaRad)*(nodeWidth/2.)));
        node.setTranslateY(-nodeHeight/2.);
        node.setTranslateZ(-layoutRadius*Math.cos(index*thetaRad)-(Math.sin(index*thetaRad)*(nodeWidth/2.)));
        Rotate rotate = new Rotate(-index*thetaDeg,0,node.getTranslateY(),0,Rotate.Y_AXIS);
        node.getTransforms().add(rotate);
    }

    void updatePosition(double oldSliderValue, double newSliderValue, double layoutWidth, double nodeWidth) {
        var rotate = new Rotate(-(newSliderValue-oldSliderValue)*thetaDeg,Rotate.Y_AXIS);
        camera.getTransforms().add(rotate);
    }

    public PerspectiveCamera getCamera() {
        return camera;
    }

    public LayoutType getType() {return type;}
}
