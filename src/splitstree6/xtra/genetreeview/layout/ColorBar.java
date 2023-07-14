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

package splitstree6.xtra.genetreeview.layout;

import javafx.beans.property.SimpleDoubleProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Slider;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import splitstree6.data.TreesBlock;

import java.util.ArrayList;
import java.util.HashMap;

public class ColorBar extends HBox {

    private final ObservableList<Color> colors = FXCollections.observableArrayList();
    private final Color backgroundColor = Color.web("#ececec",0.264); // -fx-background in modena.css
    private final HashMap<Integer,ColorBarBox> id2colorBarBox;

    public ColorBar(TreesBlock treesBlock, Slider slider, ArrayList<Integer> treeOrder) {
        id2colorBarBox = new HashMap<>();
        initializeColors(treesBlock.getNTrees());
        initializeColorBar(treesBlock,slider, treeOrder);
    }

    public void initializeColorBar(TreesBlock treesBlock, Slider slider, ArrayList<Integer> treeOrder) {
        this.getChildren().clear();
        this.setPrefHeight(14);

        int nTrees = treesBlock.getNTrees();

        Node sliderKnob = slider.lookup(".thumb");
        double knobRadius = sliderKnob.getLayoutBounds().getWidth() / 2;


        var leftSpace = new Pane();
        HBox.setMargin(leftSpace, Insets.EMPTY);
        HBox.setHgrow(leftSpace, Priority.NEVER);
        this.getChildren().add(leftSpace);
        var boxWidth = new SimpleDoubleProperty();
        for (int i = 0; i<nTrees; i++) {
            ColorBarBox colorBarBox = new ColorBarBox(treesBlock.getTree(treeOrder.get(i)).getName(),colors.get(i));
            this.getChildren().add(colorBarBox);
            id2colorBarBox.put(treeOrder.get(i),colorBarBox);
            if (i==nTrees-1) boxWidth.bind(colorBarBox.widthProperty());
        }
        var rightSpace = new Pane();
        HBox.setMargin(rightSpace,Insets.EMPTY);
        HBox.setHgrow(rightSpace, Priority.NEVER);
        this.getChildren().add(rightSpace);

        // Left and right space are adjusted to make the boxes align with the slider values as good as possible
        leftSpace.prefWidthProperty().bind(boxWidth.multiply(-0.5).add(knobRadius));
        rightSpace.prefWidthProperty().bind(boxWidth.multiply(-0.5).add(knobRadius));
        this.setVisible(true);
    }

    private void initializeColors(int nTrees) {
        Color[] colorList = new Color[nTrees];
        for (int i = 0; i<nTrees; i++) {
            colorList[i] = backgroundColor;
        }
        assert false;
        colors.addAll(colorList);
    }

    public void addColorBox(String treeName, int id) {
        colors.add(backgroundColor);
        ColorBarBox colorBarBox = new ColorBarBox(treeName,colors.get(colors.size()-1));
        this.getChildren().add(this.getChildren().size()-1,colorBarBox);
        id2colorBarBox.put(id,colorBarBox);
    }

    public void removeColorBox(int id) {
        ColorBarBox boxToRemove = id2colorBarBox.get(id);
        id2colorBarBox.remove(id);
        colors.remove(boxToRemove.getColor());
        this.getChildren().remove(boxToRemove);
    }

    public ObservableList<Color> getColors() {
        return colors;
    }

    public HashMap<Integer,ColorBarBox> getId2colorBarBox() {
        return id2colorBarBox;
    }
}
