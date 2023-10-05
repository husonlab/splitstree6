/*
 *  ColorBarBox.java Copyright (C) 2023 Daniel H. Huson
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

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.geometry.Insets;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;

import java.util.HashMap;

public class ColorBarBox extends Pane implements Selectable {

    private Color color;
    private final Tooltip tooltip;
    private String name;
    private final HashMap<String,String> attachedValues = new HashMap<>();
    private final BooleanProperty isSelectedProperty = new SimpleBooleanProperty();

    public ColorBarBox(String name, Color color) {
        this.color = color;
        this.setBackground(new Background(new BackgroundFill(color,null,null)));
        this.setStyle("-fx-border-color: -fx-box-border");
        HBox.setMargin(this, Insets.EMPTY);
        HBox.setHgrow(this, Priority.ALWAYS);
        this.name = name;
        tooltip = new Tooltip(name);
        Tooltip.install(this, tooltip);
        isSelectedProperty.addListener((observable, wasSelected, isSelected) -> {
            if (isSelected) this.setStyle("-fx-border-color: -fx-accent");
            else this.setStyle("-fx-border-color: -fx-box-border");
        });
    }

    void setColor(Color color) {
        this.color = color;
        this.setBackground(new Background(new BackgroundFill(color,null,null)));
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        {
            this.name = name;
            updateTooltip();
        }
    }

    public Color getColor() {
        return color;
    }

    public void setSelectedProperty(boolean selected) {
        isSelectedProperty.set(selected);
    };

    public void setSelectedProperty() {
        setSelectedProperty(!isSelectedProperty.get());
    }

    public BooleanProperty isSelectedProperty() {
        return isSelectedProperty;
    }

    public void addToTooltipOrReplace(String featureName, String value) {
        attachedValues.put(featureName, value);
        updateTooltip();
    }

    private void updateTooltip() {
        StringBuilder sb = new StringBuilder(name);
        for (var feature : attachedValues.keySet()) {
            sb.append("\n").append(feature).append(": ").append(attachedValues.get(feature));
        }
        tooltip.setText(sb.toString());
    }
}
