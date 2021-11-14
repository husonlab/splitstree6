/*
 *  SetupDragSelectedLabels.java Copyright (C) 2021 Daniel H. Huson
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

package splitstree6.view.trees.multitree;

import javafx.event.EventHandler;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import jloda.fx.selection.SelectionModel;
import splitstree6.data.parts.Taxon;

import java.util.Map;

/**
 * setup dragging of selected taxon labels
 * Daniel Huson, 2021
 */
public class SetupDragSelectedLabels {

	private static double mouseDownX;
	private static double mouseDownY;

	public static void apply(SelectionModel<Taxon> taxonSelectionModel, Map<Taxon, ComputeTreeEmbedding.ShapeAndLabel> taxonShapeAndLabelMap) {
		final EventHandler<MouseEvent> mousePressedHandler = e -> {
			if (e.getSource() instanceof Pane pane && pane.getEffect() != null) { // need a better way to determine whether this label is selected
				mouseDownX = e.getScreenX();
				mouseDownY = e.getScreenY();
				e.consume();
			}
		};

		final EventHandler<MouseEvent> mouseDraggedHandler = e -> {
			if (e.getSource() instanceof Pane pane && pane.getEffect() != null) {
				for (var taxon : taxonSelectionModel.getSelectedItems()) {
					var shapeAndLabel = taxonShapeAndLabelMap.get(taxon);
					if (shapeAndLabel != null) {
						var label = shapeAndLabel.label();
						label.setLayoutX(label.getLayoutX() + e.getScreenX() - mouseDownX);
						label.setLayoutY(label.getLayoutY() + e.getScreenY() - mouseDownY);
					}
				}
				mouseDownX = e.getScreenX();
				mouseDownY = e.getScreenY();
			}
		};
		for (var shapeAndLabel : taxonShapeAndLabelMap.values()) {
			shapeAndLabel.label().setOnMousePressed(mousePressedHandler);
			shapeAndLabel.label().setOnMouseDragged(mouseDraggedHandler);
		}
	}
}
