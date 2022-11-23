/*
 * SplitsFormatPresenter.java Copyright (C) 2022 Daniel H. Huson
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

package splitstree6.view.format.splits;

import javafx.application.Platform;
import javafx.beans.InvalidationListener;
import javafx.beans.WeakInvalidationListener;
import javafx.beans.binding.Bindings;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import javafx.scene.shape.Shape;
import jloda.fx.selection.SelectionModel;
import jloda.fx.undo.UndoManager;
import jloda.fx.undo.UndoableRedoableCommandList;
import jloda.graph.Node;
import splitstree6.layout.splits.RotateSplit;
import splitstree6.layout.splits.SplitsDiagramType;
import splitstree6.layout.tree.LabeledNodeShape;
import splitstree6.view.splits.viewer.SplitNetworkEdits;
import splitstree6.view.splits.viewer.SplitsView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Map;

/**
 * splits formatter presenter
 * Daniel Huson, 1.2022
 */
public class SplitsFormatPresenter {
	private final InvalidationListener selectionListener;

	final private SelectionModel<Integer> splitSelectionModel;
	final private UndoManager undoManager;
	final private ObjectProperty<String[]> editsProperty;
	final private Map<Node, LabeledNodeShape> nodeShapeMap;

	private boolean inUpdatingDefaults = false;

	public SplitsFormatPresenter(UndoManager undoManager, SplitsFormatController controller, SelectionModel<Integer> splitSelectionModel,
								 Map<Node, LabeledNodeShape> nodeShapeMap, Map<Integer, ArrayList<Shape>> splitShapeMap, ObjectProperty<SplitsDiagramType> optionDiagram,
								 ObjectProperty<Color> outlineFill, ObjectProperty<String[]> editsProperty) {

		this.splitSelectionModel = splitSelectionModel;
		this.undoManager = undoManager;
		this.editsProperty = editsProperty;
		this.nodeShapeMap = nodeShapeMap;

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
						var edits = new ArrayList<SplitNetworkEdits.Edit>();

						for (var split : splitSelectionModel.getSelectedItems()) {
							if (splitShapeMap.containsKey(split)) {
								edits.add(new SplitNetworkEdits.Edit('w', split, width));
								for (var shape : splitShapeMap.get(split)) {
									var oldWidth = shape.getStrokeWidth();
									if (n.doubleValue() != oldWidth) {
										undoList.add(shape.strokeWidthProperty(), oldWidth, width);
									}
								}
							}
						}

						if (undoList.size() > 0) {
							var oldEdits = editsProperty.get();
							var newEdits = SplitNetworkEdits.addEdits(editsProperty.get(), edits);
							undoList.add(editsProperty, oldEdits, newEdits);
							undoManager.doAndAdd(undoList);
						}
						Platform.runLater(() -> controller.getWidthCBox().setValue(n));
					}
				}
			}
		});

		controller.getLineColorPicker().setOnAction(e -> {
			if (!inUpdatingDefaults) {
				var undoList = new UndoableRedoableCommandList("line color");
				var color = controller.getLineColorPicker().getValue();
				var edits = new ArrayList<SplitNetworkEdits.Edit>();

				for (var split : splitSelectionModel.getSelectedItems()) {
					if (splitShapeMap.containsKey(split)) {
						edits.add(new SplitNetworkEdits.Edit('c', split, color));

						for (var shape : splitShapeMap.get(split)) {
							var oldColor = shape.getStroke();
							if (!color.equals(oldColor)) {
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
				}
				if (undoList.size() > 0) {
					var oldEdits = editsProperty.get();
					var newEdits = SplitNetworkEdits.addEdits(editsProperty.get(), edits);
					undoList.add(editsProperty, oldEdits, newEdits);
					undoManager.doAndAdd(undoList);
				}
			}
		});

		selectionListener = e -> {
			inUpdatingDefaults = true;
			try {
				controller.getWidthCBox().setDisable(splitSelectionModel.size() == 0);
				controller.getLineColorPicker().setDisable(splitSelectionModel.size() == 0);

				var widths = new HashSet<Double>();
				var colors = new HashSet<Paint>();
				for (var split : splitSelectionModel.getSelectedItems()) {
					if (splitShapeMap.containsKey(split)) {
						for (var shape : splitShapeMap.get(split)) {
							if (shape.getUserData() instanceof Double width) // temporarily store width in user data when user is hovering over edge
								widths.add(width);
							else
								widths.add(shape.getStrokeWidth());
							colors.add(shape.getStroke());
						}
					}
				}
				var width = (widths.size() == 1 ? widths.iterator().next() : null);
				controller.getWidthCBox().setValue(width);
				strokeWidth.setValue(null);
				controller.getLineColorPicker().setValue(colors.size() == 1 ? (Color) colors.iterator().next() : null);
			} finally {
				inUpdatingDefaults = false;
			}
		};

		controller.getRotateLeftButton().setOnAction(e -> {
			rotateSplitsLeft();
		});
		controller.getRotateLeftButton().disableProperty().bind(splitSelectionModel.sizeProperty().isEqualTo(0));

		controller.getRotateRightButton().setOnAction(e -> {
			rotateSplitsRight();
		});
		controller.getRotateRightButton().disableProperty().bind(splitSelectionModel.sizeProperty().isEqualTo(0));

		controller.getOutlineFillColorPicker().valueProperty().bindBidirectional(outlineFill);
		controller.getOutlineFillColorPicker().disableProperty().bind(optionDiagram.isNotEqualTo(SplitsDiagramType.Outline));

		controller.getResetWidthButton().setOnAction(a -> controller.getWidthCBox().setValue(1.0));
		controller.getResetWidthButton().disableProperty().bind(Bindings.isEmpty(splitSelectionModel.getSelectedItems()).or(controller.getWidthCBox().valueProperty().isEqualTo(1.0)));

		controller.getResetOutlineFillColorButton().setOnAction(e -> {
			controller.getOutlineFillColorPicker().setValue(SplitsView.OUTLINE_FILL_COLOR);
		});
		controller.getResetOutlineFillColorButton().disableProperty().bind(controller.getOutlineFillColorPicker().disableProperty().or(controller.getOutlineFillColorPicker().valueProperty().isEqualTo(SplitsView.OUTLINE_FILL_COLOR)));


		controller.getResetLineColorButton().setOnAction(e -> {
			controller.getLineColorPicker().setValue(null);
		});
		controller.getResetLineColorButton().disableProperty().bind(Bindings.isEmpty(splitSelectionModel.getSelectedItems()).or(controller.getLineColorPicker().valueProperty().isNull()));


		//selectionModel.getSelectedItems().addListener(selectionListener);
		splitSelectionModel.getSelectedItems().addListener(new WeakInvalidationListener(selectionListener));
		selectionListener.invalidated(null);

	}

	public void rotateSplitsLeft() {
		var splits = new ArrayList<>(splitSelectionModel.getSelectedItems());
		var oldEdits = editsProperty.get();

		undoManager.doAndAdd("rotate splits", () -> {
					RotateSplit.apply(splits, -5, nodeShapeMap);
					editsProperty.set(oldEdits);
				}
				, () -> {
					RotateSplit.apply(splits, 5, nodeShapeMap);
					editsProperty.set(SplitNetworkEdits.addAngles(oldEdits, splits, 5));
				});
	}

	public void rotateSplitsRight() {
		var splits = new ArrayList<>(splitSelectionModel.getSelectedItems());
		var oldEdits = editsProperty.get();
		undoManager.doAndAdd("rotate splits", () -> {
			RotateSplit.apply(splits, 5, nodeShapeMap);
			editsProperty.set(oldEdits);
		}, () -> {
			RotateSplit.apply(splits, -5, nodeShapeMap);
			editsProperty.set(SplitNetworkEdits.addAngles(oldEdits, splits, -5));
		});
	}
}
