/*
 * EdgesFormatPresenter.java Copyright (C) 2022 Daniel H. Huson
 *
 * (Some files contain contributions from other authors, who are then mentioned separately.)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package splitstree6.view.format.edges;

import javafx.application.Platform;
import javafx.beans.InvalidationListener;
import javafx.beans.WeakInvalidationListener;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import javafx.scene.shape.Shape;
import jloda.fx.selection.SelectionModel;
import jloda.fx.undo.UndoManager;
import jloda.fx.undo.UndoableRedoableCommandList;
import jloda.graph.Edge;
import splitstree6.view.trees.treeview.TreeEdits;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Map;

/**
 * edge formatter presenter
 * Daniel Huson, 5.2022
 */
public class EdgesFormatPresenter {
	private final InvalidationListener selectionListener;

	private boolean inUpdatingDefaults = false;

	public EdgesFormatPresenter(UndoManager undoManager, EdgesFormatController controller, SelectionModel<Edge> edgeSelectionModel,
								Map<Edge, Shape> edgeShapeMap, ObjectProperty<String[]> editsProperty) {

		var strokeWidth = new SimpleDoubleProperty(1.0);
		controller.getWidthCBox().getItems().addAll(0.1, 0.5, 1, 2, 3, 4, 5, 6, 8, 10, 20);
		controller.getWidthCBox().valueProperty().addListener((v, o, n) -> {
			if (n != null)
				strokeWidth.set(n.doubleValue());
		});

		strokeWidth.addListener((v, o, n) -> {
			if (!inUpdatingDefaults) {
				if (n != null) {
					if (n.doubleValue() < 0)
						strokeWidth.set(0);
					else {
						var undoList = new UndoableRedoableCommandList("line width");
						var width = n.doubleValue();
						var edits = new ArrayList<TreeEdits.Edit>();

						for (var edge : edgeSelectionModel.getSelectedItems()) {
							var shape = edgeShapeMap.get(edge);
							if (shape != null) {
								edits.add(new TreeEdits.Edit('w', edge.getId(), width));
								var oldWidth = shape.getStrokeWidth();
								undoList.add(shape.strokeWidthProperty(), oldWidth, width);
							}

						}
						if (undoList.size() > 0) {
							var oldEdits = editsProperty.get();
							var newEdits = TreeEdits.addEdits(editsProperty.get(), edits);
							undoList.add(editsProperty, oldEdits, newEdits);
							undoManager.doAndAdd(undoList);
						}
						Platform.runLater(() -> controller.getWidthCBox().setValue(n));
					}
				}
			}
		});

		controller.getColorPicker().setOnAction(e -> {
			if (!inUpdatingDefaults) {
				var undoList = new UndoableRedoableCommandList("line color");
				var color = controller.getColorPicker().getValue();
				var edits = new ArrayList<TreeEdits.Edit>();

				for (var edge : edgeSelectionModel.getSelectedItems()) {
					var shape = edgeShapeMap.get(edge);
					if (shape != null) {
						var oldColor = shape.getStroke();
						if (!color.equals(oldColor)) {
							edits.add(new TreeEdits.Edit('c', edge.getId(), color));

							var hasGraphEdgeStyleClass = shape.getStyleClass().contains("graph-edge");
							undoList.add(() -> {
								if (hasGraphEdgeStyleClass)
									shape.getStyleClass().add("graph-edge");
								shape.setStroke(oldColor);

							}, () -> {
								if (hasGraphEdgeStyleClass)
									shape.getStyleClass().remove("graph-edge");
								shape.setStroke(color);
							});
						}
					}
				}
				if (undoList.size() > 0) {
					var oldEdits = editsProperty.get();
					var newEdits = TreeEdits.addEdits(editsProperty.get(), edits);
					undoList.add(editsProperty, oldEdits, newEdits);
					undoManager.doAndAdd(undoList);
				}
			}
		});


		selectionListener = e -> {
			inUpdatingDefaults = true;
			try {
				controller.getWidthCBox().setDisable(edgeSelectionModel.size() == 0);
				controller.getColorPicker().setDisable(edgeSelectionModel.size() == 0);

				var widths = new HashSet<Double>();
				var colors = new HashSet<Paint>();
				for (var edge : edgeSelectionModel.getSelectedItems()) {
					var shape = edgeShapeMap.get(edge);
					if (shape != null) {
						if (shape.getUserData() instanceof Double width) // temporarily store width in user data when user is hovering over edge
							widths.add(width);
						else
							widths.add(shape.getStrokeWidth());
						colors.add(shape.getStroke());
					}
				}
				var width = (widths.size() == 1 ? widths.iterator().next() : null);
				controller.getWidthCBox().setValue(width);
				strokeWidth.setValue(null);
				controller.getColorPicker().setValue(colors.size() == 1 ? (Color) colors.iterator().next() : null);
			} finally {
				inUpdatingDefaults = false;
			}
		};

		//selectionModel.getSelectedItems().addListener(selectionListener);
		edgeSelectionModel.getSelectedItems().addListener(new WeakInvalidationListener(selectionListener));
		selectionListener.invalidated(null);
	}
}
