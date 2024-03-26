/*
 *  EdgesFormatPresenter.java Copyright (C) 2024 Daniel H. Huson
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
import jloda.phylo.PhyloTree;
import splitstree6.layout.tree.LabeledEdgeShape;
import splitstree6.view.trees.treeview.TreeEdits;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Map;
import java.util.function.Consumer;

/**
 * edge formatter presenter
 * Daniel Huson, 5.2022
 */
public class EdgesFormatPresenter {
	private final InvalidationListener selectionListener;

	private boolean inUpdatingDefaults = false;

	private final UndoManager undoManager;
	private final EdgesFormatController controller;

	private Consumer<LabelEdgesBy> updateLabelsConsumer;

	public EdgesFormatPresenter(UndoManager undoManager, EdgesFormatController controller, ObjectProperty<LabelEdgesBy> optionLabelEdgesBy, SelectionModel<Edge> edgeSelectionModel,
								Map<Edge, LabeledEdgeShape> edgeShapeMap, ObjectProperty<String[]> editsProperty) {
		this.undoManager = undoManager;
		this.controller = controller;

		controller.getLabelByNoneMenuItem().selectedProperty().addListener(e -> optionLabelEdgesBy.set(LabelEdgesBy.None));
		controller.getLabelByWeightMenuItem().selectedProperty().addListener(e -> optionLabelEdgesBy.set(LabelEdgesBy.Weight));
		controller.getLabelByConfidenceMenuItem().selectedProperty().addListener(e -> optionLabelEdgesBy.set(LabelEdgesBy.Confidence));
		controller.getLabelByConfidenceX100MenuItem().selectedProperty().addListener(e -> optionLabelEdgesBy.set(LabelEdgesBy.ConfidenceX100));
		controller.getLabelByProbabilityMenuItem().selectedProperty().addListener(e -> optionLabelEdgesBy.set(LabelEdgesBy.Probability));
		optionLabelEdgesBy.addListener((v, o, n) -> {
			if (n != null) {
				switch (n) {
					case None -> controller.getLabelByToggleGroup().selectToggle(controller.getLabelByNoneMenuItem());
					case Weight ->
							controller.getLabelByToggleGroup().selectToggle(controller.getLabelByWeightMenuItem());
					case Confidence ->
							controller.getLabelByToggleGroup().selectToggle(controller.getLabelByConfidenceMenuItem());
					case ConfidenceX100 ->
							controller.getLabelByToggleGroup().selectToggle(controller.getLabelByConfidenceX100MenuItem());
					case Probability ->
							controller.getLabelByToggleGroup().selectToggle(controller.getLabelByProbabilityMenuItem());
				}
			}
		});

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
							if (edgeShapeMap.get(edge).getShape() instanceof Shape shape) {
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
					if (edgeShapeMap.get(edge).getShape() instanceof Shape shape) {
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
					if (edgeShapeMap.get(edge) != null && edgeShapeMap.get(edge).getShape() instanceof Shape shape) {
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

	public void setUpdateLabelsConsumer(Consumer<LabelEdgesBy> updateLabelsConsumer) {
		this.updateLabelsConsumer = updateLabelsConsumer;


	}

	public void updateMenus(PhyloTree tree) {
		controller.getLabelByWeightMenuItem().setDisable(tree == null || !tree.hasEdgeWeights());
		controller.getLabelByConfidenceMenuItem().setDisable(tree == null || !tree.hasEdgeConfidences());
		controller.getLabelByConfidenceX100MenuItem().setDisable(tree == null || !tree.hasEdgeConfidences());
		controller.getLabelByProbabilityMenuItem().setDisable(tree == null || !tree.hasEdgeProbabilities());

		if (controller.getLabelByWeightMenuItem().isSelected() && controller.getLabelByWeightMenuItem().isDisable()
			|| controller.getLabelByConfidenceMenuItem().isSelected() && controller.getLabelByConfidenceMenuItem().isDisable()
			|| controller.getLabelByConfidenceX100MenuItem().isSelected() && controller.getLabelByConfidenceMenuItem().isDisable()
			|| controller.getLabelByProbabilityMenuItem().isSelected() && controller.getLabelByProbabilityMenuItem().isDisable()) {
			Platform.runLater(() -> controller.getLabelByNoneMenuItem().setSelected(true));
		}
	}
}
