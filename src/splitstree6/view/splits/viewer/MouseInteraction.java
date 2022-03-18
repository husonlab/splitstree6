/*
 * MouseInteraction.java Copyright (C) 2022 Daniel H. Huson
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

package splitstree6.view.splits.viewer;

import javafx.beans.InvalidationListener;
import javafx.beans.WeakInvalidationListener;
import javafx.event.EventHandler;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.input.ContextMenuEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.shape.Shape;
import javafx.stage.Stage;
import jloda.fx.control.RichTextLabel;
import jloda.fx.graph.GraphTraversals;
import jloda.fx.label.EditLabelDialog;
import jloda.fx.selection.SelectionModel;
import jloda.fx.undo.UndoManager;
import jloda.fx.util.SelectionEffectBlue;
import jloda.graph.Node;
import jloda.phylo.PhyloSplitsGraph;
import jloda.util.BitSetUtils;
import jloda.util.Pair;
import jloda.util.Single;
import splitstree6.data.parts.ASplit;
import splitstree6.data.parts.Taxon;

import java.util.ArrayList;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * split network mouse interaction
 * Daniel Huson, 3.2022
 */
public class MouseInteraction {
	private static boolean nodeShapeOrLabelEntered;
	private static boolean edgeShapeEntered;
	private static double mouseDownX;
	private static double mouseDownY;

	private final Stage stage;
	private final UndoManager undoManager;
	private final SelectionModel<Taxon> taxonSelectionModel;
	private final SelectionModel<Integer> splitSelectionModel;

	private InvalidationListener taxonSelectionInvalidationListener;
	private InvalidationListener splitSelectionInvalidationListener;

	/**
	 * constructor
	 */
	public MouseInteraction(Stage stage, UndoManager undoManager, SelectionModel<Taxon> taxonSelectionModel, SelectionModel<Integer> splitSelectionModel) {
		this.stage = stage;
		this.undoManager = undoManager;
		this.taxonSelectionModel = taxonSelectionModel;
		this.splitSelectionModel = splitSelectionModel;
	}

	/**
	 * setup split network mouse interaction
	 */
	public void setup(Map<Node, Pair<Shape, RichTextLabel>> nodeShapeLabelMap, Map<Integer, ArrayList<Shape>> splitShapesMap, Function<Integer, Taxon> idTaxonMap, Function<Taxon, Integer> taxonIdMap, Function<Integer, ASplit> idSplitMap) {

		var graph = new Single<PhyloSplitsGraph>();
		for (var v : nodeShapeLabelMap.keySet()) {
			graph.setIfCurrentValueIsNull((PhyloSplitsGraph) v.getOwner());
			var id = graph.get().getTaxon(v);
			if (id != -1) {
				var taxon = idTaxonMap.apply(graph.get().getTaxon(v));
				if (taxon != null) {
					var shape = nodeShapeLabelMap.get(v).getFirst();
					var label = nodeShapeLabelMap.get(v).getSecond();

					shape.setOnContextMenuRequested(m -> showContextMenu(m, stage, undoManager, label));
					label.setOnContextMenuRequested(m -> showContextMenu(m, stage, undoManager, label));

					shape.setOnMouseEntered(e -> {
						if (!e.isStillSincePress() && !nodeShapeOrLabelEntered) {
							nodeShapeOrLabelEntered = true;
							shape.setScaleX(2 * shape.getScaleX());
							shape.setScaleY(2 * shape.getScaleY());
							label.setScaleX(1.1 * label.getScaleX());
							label.setScaleY(1.1 * label.getScaleY());
							e.consume();
						}
					});
					shape.setOnMouseExited(e -> {
						if (nodeShapeOrLabelEntered) {
							shape.setScaleX(shape.getScaleX() / 2);
							shape.setScaleY(shape.getScaleY() / 2);
							label.setScaleX(label.getScaleX() / 1.1);
							label.setScaleY(label.getScaleY() / 1.1);
							nodeShapeOrLabelEntered = false;
							e.consume();
						}
					});

					final EventHandler<MouseEvent> mouseClickedHandler = e -> {
						if (e.isStillSincePress()) {
							if (!e.isShiftDown())
								taxonSelectionModel.clearSelection();
							taxonSelectionModel.toggleSelection(taxon);
							e.consume();
						}
					};
					shape.setOnMouseClicked(mouseClickedHandler);
					label.setOnMouseClicked(mouseClickedHandler);

					label.setOnMouseEntered(shape.getOnMouseEntered());
					label.setOnMouseExited(shape.getOnMouseExited());

					label.setOnMousePressed(e -> {
						if (taxonSelectionModel.isSelected(taxon)) {
							mouseDownX = e.getScreenX();
							mouseDownY = e.getScreenY();
							e.consume();
						}
					});

					label.setOnMouseDragged(e -> {
						if (taxonSelectionModel.isSelected(taxon)) {
							graph.get().nodeStream().filter(a -> graph.get().getTaxon(a) != -1).forEach(w -> {
								var wTaxon = idTaxonMap.apply(graph.get().getTaxon(w));
								if (wTaxon != null && taxonSelectionModel.isSelected(wTaxon)) {
									var shapeLabel = nodeShapeLabelMap.get(w);
									if (shapeLabel != null) {
										var wLabel = shapeLabel.getSecond();
										var dx = e.getScreenX() - mouseDownX;
										var dy = e.getScreenY() - mouseDownY;
										wLabel.setLayoutX(wLabel.getLayoutX() + dx);
										wLabel.setLayoutY(wLabel.getLayoutY() + dy);
									}
								}
							});
							mouseDownX = e.getScreenX();
							mouseDownY = e.getScreenY();
							e.consume();
						}
					});
				}
			}

			for (var splitId : splitShapesMap.keySet()) {
				for (var shape : splitShapesMap.get(splitId)) {
					shape.setPickOnBounds(false);

					shape.setOnMouseEntered(e -> {
						if (!e.isStillSincePress() && !edgeShapeEntered) {
							edgeShapeEntered = true;
							shape.setUserData(shape.getStrokeWidth());
							shape.setStrokeWidth(shape.getStrokeWidth() + 4);
							e.consume();
						}
					});
					shape.setOnMouseExited(e -> {
						if (edgeShapeEntered) {
							shape.setStrokeWidth(shape.getStrokeWidth() - 4);
							shape.setUserData(null);
							edgeShapeEntered = false;
							e.consume();
						}
					});

					shape.setOnMouseClicked(e -> {
						if (!e.isShiftDown()) {
							taxonSelectionModel.clearSelection();
							splitSelectionModel.clearSelection();
						}
						if (e.getClickCount() == 1) {
							var split = idSplitMap.apply(splitId);
							if (split != null) {
								splitSelectionModel.select(splitId);
								var partA = split.getA();
								var partB = split.getB();
								var whichPart = ((partA.cardinality() < partB.cardinality()) == !e.isAltDown() ? partA : partB);
								var taxa = BitSetUtils.asStream(whichPart).map(idTaxonMap).collect(Collectors.toList());
								taxonSelectionModel.selectAll(taxa);
							}
						} else if (e.getClickCount() == 2) {
							var selectedTaxonIds = BitSetUtils.asBitSet(taxonSelectionModel.getSelectedItems().stream().map(taxonIdMap).collect(Collectors.toList()));
							var start = graph.get().nodeStream().filter(z -> BitSetUtils.intersection(selectedTaxonIds, BitSetUtils.asBitSet(graph.get().getTaxa(z))).cardinality() > 0).findAny();
							if (start.isPresent()) {
								try (var visited = graph.get().newNodeSet()) {
									GraphTraversals.traverseReachable(start.get(), f -> graph.get().getSplit(f) != splitId, visited::add);
									for (var f : graph.get().edges()) {
										if (visited.contains(f.getSource()) && visited.contains(f.getTarget()))
											splitSelectionModel.select(graph.get().getSplit(f));
									}
								}
							}
						}
						e.consume();
					});
				}
			}
		}

		taxonSelectionInvalidationListener = e -> {
			nodeShapeLabelMap.keySet().stream().filter(a -> graph.get().getTaxon(a) != -1).forEach(v -> {
				var t = graph.get().getTaxon(v);
				var taxon = idTaxonMap.apply(t);
				if (taxon != null) {
					var shapeLabel = nodeShapeLabelMap.get(v);
					shapeLabel.getFirst().setEffect(taxonSelectionModel.isSelected(taxon) ? SelectionEffectBlue.getInstance() : null);
					shapeLabel.getSecond().setEffect(taxonSelectionModel.isSelected(taxon) ? SelectionEffectBlue.getInstance() : null);
				}
			});
		};
		taxonSelectionModel.getSelectedItems().addListener(new WeakInvalidationListener(taxonSelectionInvalidationListener));

		splitSelectionInvalidationListener = e -> {
			for (var splitId : splitShapesMap.keySet()) {
				for (var shape : splitShapesMap.get(splitId)) {
					shape.setEffect(splitSelectionModel.isSelected(splitId) ? SelectionEffectBlue.getInstance() : null);
				}
			}
		};
		splitSelectionModel.getSelectedItems().addListener(splitSelectionInvalidationListener);
	}

	private static void showContextMenu(ContextMenuEvent event, Stage stage, UndoManager undoManager, RichTextLabel label) {
		var editLabelMenuItem = new MenuItem("Edit Label...");
		editLabelMenuItem.setOnAction(e -> {
			var oldText = label.getText();
			var editLabelDialog = new EditLabelDialog(stage, label);
			var result = editLabelDialog.showAndWait();
			if (result.isPresent() && !result.get().equals(oldText)) {
				undoManager.doAndAdd("Edit Label", () -> label.setText(oldText), () -> label.setText(result.get()));
			}
		});
		var menu = new ContextMenu();
		menu.getItems().add(editLabelMenuItem);
		menu.show(label, event.getScreenX(), event.getScreenY());
	}
}

