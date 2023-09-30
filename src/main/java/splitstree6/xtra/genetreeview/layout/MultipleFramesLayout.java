/*
 *  MultipleFramesLayout.java Copyright (C) 2023 Daniel H. Huson
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

import javafx.collections.ObservableList;
import javafx.scene.Node;
import javafx.scene.PerspectiveCamera;
import javafx.scene.control.Slider;

public abstract class MultipleFramesLayout {

    protected LayoutType type;
    protected ObservableList<Node> transformedNodes = null;
    protected ObservableList<Node> transformedSnapshots = null;

    public MultipleFramesLayout() {
    };

    public void updatePosition(double oldSliderValue, double newSliderValue) {}

    public void resetNode(Node node) {
        node.getTransforms().clear();
        node.translateXProperty().unbind();
        node.setTranslateX(0);
        node.setTranslateY(0);
        node.setTranslateZ(0);
        node.setRotate(0);
        if (node.scaleXProperty().isBound()) node.scaleXProperty().unbind();
        node.setScaleX(1);
        if (node.scaleYProperty().isBound()) node.scaleYProperty().unbind();
        node.setScaleY(1);
        node.setScaleZ(1);
        node.layoutXProperty().unbind();
        node.setLayoutX(0);
    }

    public void initializeNode(Node node, int index) {
        resetNode(node);
    };

    void resetCamera(PerspectiveCamera camera) {
        camera.getTransforms().clear();
        camera.translateXProperty().unbind();
        camera.translateZProperty().unbind();
        camera.setTranslateX(0);
        camera.setTranslateY(0);
        camera.setTranslateZ(0);
        camera.setRotate(0);
    }

    void setUpZoomSlider(Slider zoomSlider, double min, double max) {
        zoomSlider.setDisable(false);
        if (zoomSlider.minProperty().isBound()) zoomSlider.minProperty().unbind();
        if (zoomSlider.maxProperty().isBound()) zoomSlider.maxProperty().unbind();
        zoomSlider.setMin(min);
        zoomSlider.setMax(max);
        zoomSlider.setValue(zoomSlider.getMin());
    }

    void setUpZoomSlider(Slider zoomSlider) {
        zoomSlider.setDisable(false);
        if (zoomSlider.minProperty().isBound()) zoomSlider.minProperty().unbind();
        if (zoomSlider.maxProperty().isBound()) zoomSlider.maxProperty().unbind();
    }

    public LayoutType getType() {return type;}

    public int size() {
        return transformedNodes.size();
    }
}
