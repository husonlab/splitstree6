/*
 *  ColorBar.java Copyright (C) 2023 Daniel H. Huson
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

import javafx.beans.property.SimpleDoubleProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Slider;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import splitstree6.data.TreesBlock;

// Not used at the moment
public class ColorBar extends HBox {

    //private ObservableList<Color> colors = new SimpleListProperty<>();
    private final ObservableList<Color> colors = FXCollections.observableArrayList();

    public ColorBar() {

    }

    public ColorBar(TreesBlock treesBlock, Slider slider) {
        initializeColorBar(treesBlock,slider);
    }

    public void initializeColorBar(TreesBlock treesBlock, Slider slider) {
        this.getChildren().clear();

        int nTrees = treesBlock.getNTrees();
        Color[] colorList = new Color[nTrees];
        for (int i = 0; i<nTrees; i++) {
            colorList[i] = Color.LIGHTGOLDENRODYELLOW;
        }
        assert false;
        colors.addAll(colorList);

        Node sliderKnob = slider.lookup(".thumb");
        double knobRadius = sliderKnob.getLayoutBounds().getWidth() / 2;

        var leftPane = new Pane();
        HBox.setMargin(leftPane, Insets.EMPTY);
        HBox.setHgrow(leftPane, Priority.NEVER);
        this.getChildren().add(leftPane);
        var boxWidth = new SimpleDoubleProperty();
        for (int i = 0; i<nTrees; i++) {
            ColorBarBox colorBarBox = new ColorBarBox(treesBlock.getTree(i+1).getName(),colors.get(i));
            this.getChildren().add(colorBarBox);
            if (i==nTrees-1) boxWidth.bind(colorBarBox.widthProperty());
        }
        var rightPane = new Pane();
        HBox.setMargin(rightPane,Insets.EMPTY);
        HBox.setHgrow(rightPane, Priority.NEVER);
        this.getChildren().add(rightPane);

        leftPane.prefWidthProperty().bind(boxWidth.multiply(-0.5).add(knobRadius));
        rightPane.prefWidthProperty().bind(boxWidth.multiply(-0.5).add(knobRadius));
        this.setVisible(true);
    }

    public ObservableList<Color> getColors() {
        return colors;
    }
}
