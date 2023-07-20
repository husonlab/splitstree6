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

    public void updatePosition(double oldSliderValue, double newSliderValue, double nodeWidth) {}

    public void resetNode(Node node) {
        node.getTransforms().clear();
        node.translateXProperty().unbind();
        node.setTranslateX(0);
        node.setTranslateY(0);
        node.setTranslateZ(0);
        node.setRotate(0);
        node.setScaleX(1);
        node.setScaleY(1);
        node.setScaleZ(1);
        node.setLayoutX(0);
    }

    public void initializeNode(Node node, int index, double sliderValue) {
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
        zoomSlider.setMin(min);
        zoomSlider.setMax(max);
        zoomSlider.setValue(zoomSlider.getMin());
    }

    public LayoutType getType() {return type;}

    public int size() {
        return transformedNodes.size();
    }
}
