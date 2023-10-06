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

import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Slider;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import splitstree6.xtra.genetreeview.model.GeneTreeSet;

import java.util.ArrayList;
import java.util.HashMap;

public class ColorBar extends HBox {

    private final Color backgroundColor = Color.web("#ececec",0.264); // -fx-background in modena.css
    private final HashMap<Integer,ColorBarBox> id2colorBarBox;
    private HashMap<Integer, Color> id2color;
    private final DoubleProperty boxWidth = new SimpleDoubleProperty();

    public ColorBar(GeneTreeSet geneTreeSet, Slider slider) {
        id2colorBarBox = new HashMap<>();
        id2color = new HashMap<>();
        initializeColors(geneTreeSet.getTreeOrder());
        initializeColorBar(geneTreeSet, slider);
    }

    public void initializeColorBar(GeneTreeSet geneTreeSet, Slider slider) {
        this.getChildren().clear();
        this.setPrefHeight(14);

        int nTrees = geneTreeSet.size();

        Node sliderKnob = slider.lookup(".thumb");
        double knobRadius = sliderKnob.getLayoutBounds().getWidth() / 2;

        var leftSpace = new Pane();
        HBox.setMargin(leftSpace, Insets.EMPTY);
        HBox.setHgrow(leftSpace, Priority.NEVER);
        this.getChildren().add(leftSpace);
        if (boxWidth.isBound()) boxWidth.unbind();
        for (int id : geneTreeSet.getTreeOrder()) {
            ColorBarBox colorBarBox = new ColorBarBox(geneTreeSet.getPhyloTree(id).getName(), id2color.get(id));
            this.getChildren().add(colorBarBox);
            id2colorBarBox.put(id, colorBarBox);
        }
        boxWidth.bind(id2colorBarBox.get(geneTreeSet.getTreeOrder().get(nTrees-1)).widthProperty());
        var rightSpace = new Pane();
        HBox.setMargin(rightSpace,Insets.EMPTY);
        HBox.setHgrow(rightSpace, Priority.NEVER);
        this.getChildren().add(rightSpace);

        // Left and right space are adjusted to make the boxes align with the slider values as good as possible
        leftSpace.prefWidthProperty().bind(boxWidth.multiply(-0.5).add(knobRadius));
        rightSpace.prefWidthProperty().bind(boxWidth.multiply(-0.5).add(knobRadius));
        this.setVisible(true);
    }

    private void initializeColors(ArrayList<Integer> treeOrder) {
        id2color.clear();
        for (int treeId : treeOrder) {
            id2color.put(treeId, backgroundColor);
        }
    }

    public void addColorBox(String treeName, int id, int position, Color color, HashMap<String, Object> furtherFeatures) {
        if (color == null) id2color.put(id, backgroundColor);
        else id2color.put(id, color);
        ColorBarBox colorBarBox = new ColorBarBox(treeName, id2color.get(id));
        if (position < 0 | position > this.getChildren().size()-2) position = this.getChildren().size()-1;
        this.getChildren().add(position+1, colorBarBox);
        id2colorBarBox.put(id,colorBarBox);
        if (furtherFeatures == null) return;
        for (var feature : furtherFeatures.keySet()) {
            colorBarBox.addToTooltipOrReplace(feature, furtherFeatures.get(feature).toString());
        }
    }

    public void removeColorBox(int id) {
        ColorBarBox boxToRemove = id2colorBarBox.get(id);
        id2colorBarBox.remove(id);
        id2color.remove(id);
        this.getChildren().remove(boxToRemove);
    }

    public void resetColoring() {
        ArrayList<Integer> array = new ArrayList<>(id2color.keySet());
        initializeColors(array);
        for (var treeId : id2color.keySet())
            id2colorBarBox.get(treeId).setColor(id2color.get(treeId));
    }

    public void setColor(int id, Color color) {
        if (id2colorBarBox.containsKey(id)) {
            id2color.replace(id, color);
            id2colorBarBox.get(id).setColor(color);
        }
    }

    public void setColors(HashMap<Integer,Color> id2color) {
        if (id2color.size() == this.id2color.size()) this.id2color = id2color;
    }

    public void reorder(ArrayList<Integer> treeOrder) {
        if (treeOrder.size() != id2colorBarBox.size()) return;
        this.getChildren().remove(1,treeOrder.size()+1);
        if (boxWidth.isBound()) boxWidth.unbind();
        for (int i = 0; i< treeOrder.size(); i++) {
            this.getChildren().add(i+1, id2colorBarBox.get(treeOrder.get(i)));
        }
        boxWidth.bind(id2colorBarBox.get(treeOrder.get(treeOrder.size()-1)).widthProperty());
    }

    public void setNames(ObservableList<String> orderedGeneNames) {
        for (int i = 1; i < this.getChildren().size()-1; i++) {
            String name = orderedGeneNames.get(i-1);
            ((ColorBarBox)this.getChildren().get(i)).setName(name);
        }
    }

    public HashMap<Integer,ColorBarBox> getId2colorBarBox() {
        return id2colorBarBox;
    }

    public void addValuesToTooltip(String featureName, HashMap<Integer,String> finalValues) {
        if (finalValues.size() == id2colorBarBox.size()) {
            for (int id : finalValues.keySet()) {
                id2colorBarBox.get(id).addToTooltipOrReplace(featureName, finalValues.get(id));
            }
        }
    }

    public Color getBackgroundColor() {
        return backgroundColor;
    }
}
