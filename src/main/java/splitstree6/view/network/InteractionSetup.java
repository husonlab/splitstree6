/*
 *  InteractionSetup.java Copyright (C) 2024 Daniel H. Huson
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

package splitstree6.view.network;

import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.collections.SetChangeListener;
import javafx.geometry.Point2D;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.input.ContextMenuEvent;
import javafx.scene.layout.Pane;
import javafx.stage.Stage;
import jloda.fx.control.RichTextLabel;
import jloda.fx.label.EditLabelDialog;
import jloda.fx.selection.SelectionModel;
import jloda.fx.undo.UndoManager;
import jloda.fx.util.RunAfterAWhile;
import jloda.fx.util.SelectionEffectBlue;
import jloda.graph.Node;
import jloda.phylo.PhyloGraph;
import jloda.util.BitSetUtils;
import jloda.util.Single;
import splitstree6.data.parts.Taxon;
import splitstree6.layout.tree.LabeledNodeShape;
import splitstree6.main.SplitsTree6;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * network mouse interaction setup
 * Daniel Huson, 4.2022
 */
public class InteractionSetup {

	private final Stage stage;

	private final UndoManager undoManager;
	private final ObjectProperty<String[]> edits;

	private static boolean nodeShapeOrLabelEntered;
	private static boolean edgeShapeEntered;
	private static double mouseDownX;
	private static double mouseDownY;

	private final SelectionModel<Taxon> taxonSelectionModel;

	private final SelectionModel<LabeledNodeShape> networkNodeSelectionModel;

	private final SetChangeListener<Taxon> taxonSelectionListener;

	private Map<Node, LabeledNodeShape> nodeShapeMap;

	/**
	 * constructor
	 */
	public InteractionSetup(Stage stage, Pane pane, UndoManager undoManager, ObjectProperty<String[]> edits,
							Function<Integer, Taxon> idTaxonMap,
							SelectionModel<LabeledNodeShape> networkNodeSelectionModel, SelectionModel<Taxon> taxonSelectionModel) {
		this.stage = stage;
		this.edits = edits;
		this.undoManager = undoManager;
		this.networkNodeSelectionModel = networkNodeSelectionModel;
		this.taxonSelectionModel = taxonSelectionModel;

		pane.setOnMouseClicked(e -> {
			if (e.isStillSincePress() && !e.isShiftDown()) {
				Platform.runLater(taxonSelectionModel::clearSelection);
				e.consume();
			}
		});

		var inUpdate = new Single<>(false);

		networkNodeSelectionModel.getSelectedItems().addListener((SetChangeListener<? super LabeledNodeShape>) e -> {
			if (!inUpdate.get()) {
				inUpdate.set(true);
				try {
					if (e.wasAdded()) {
						var shape = e.getElementAdded();
						shape.setEffect(SelectionEffectBlue.getInstance());
						if (shape.getTaxa() != null) {
							Platform.runLater(() -> {
								if (shape.getTaxa() != null)
									taxonSelectionModel.getSelectedItems().addAll(BitSetUtils.asList(shape.getTaxa()).stream().map(idTaxonMap).toList());
							});
						}
					} else if (e.wasRemoved()) {
						var shape = e.getElementRemoved();
						shape.setEffect(null);
						if (shape.getTaxa() != null) {
							Platform.runLater(() -> {
								if (shape.getTaxa() != null)
									taxonSelectionModel.getSelectedItems().removeAll(BitSetUtils.asList(shape.getTaxa()).stream().map(idTaxonMap).toList());
							});
						}
					}
				} finally {
					inUpdate.set(false);
				}
			}
		});

		taxonSelectionListener = e -> {
			if (!inUpdate.get()) {
				inUpdate.set(true);
				try {
					if (e.wasAdded()) {
						var taxon = e.getElementAdded();
						if (nodeShapeMap != null) {
							for (var shape : nodeShapeMap.values()) {
								if (shape.getTaxa() != null) {
									for (var t : BitSetUtils.members(shape.getTaxa())) {
										if (idTaxonMap.apply(t) == taxon) {
											// not sure why this is necessary, without, selection can flicker
											RunAfterAWhile.applyInFXThread(shape, () -> networkNodeSelectionModel.select(shape));
											return;
										}
									}
								}
							}
						}
					} else if (e.wasRemoved()) {
						var taxon = e.getElementRemoved();
						if (nodeShapeMap != null) {
							for (var shape : nodeShapeMap.values()) {
								if (shape.getTaxa() != null) {
									for (var t : BitSetUtils.members(shape.getTaxa())) {
										if (idTaxonMap.apply(t) == taxon) {
											// not sure why this is necessary, without, selection can flicker
											RunAfterAWhile.applyInFXThread(shape, () -> networkNodeSelectionModel.clearSelection(shape));
											return;
										}
									}
								}
							}
						}
					}
				} finally {
					inUpdate.set(false);
				}
			}
		};
		taxonSelectionModel.getSelectedItems().addListener(taxonSelectionListener);
	}

	/**
	 * setup network mouse interaction
	 */
	public void apply(Map<Integer, RichTextLabel> taxonLabelMap, Map<Node, LabeledNodeShape> nodeShapeMap, Function<Integer, Taxon> idTaxonMap, Function<Taxon, Integer> taxonIdMap) {
		this.nodeShapeMap = nodeShapeMap;
		for (var node : nodeShapeMap.keySet()) {
			var shape = nodeShapeMap.get(node);
			shape.translateXProperty().addListener((v, o, n) -> {
				edits.set(NetworkEdits.addTranslateNodeEdits(edits.get(), List.of(node), shape.getTranslateX(), shape.getTranslateY()));
			});
			shape.translateYProperty().addListener((v, o, n) -> {
				edits.set(NetworkEdits.addTranslateNodeEdits(edits.get(), List.of(node), shape.getTranslateX(), shape.getTranslateY()));
			});

			var start = new Single<Point2D>();
			var end = new Single<Point2D>();

			var selectedShapes = new ArrayList<LabeledNodeShape>();
			shape.setOnMousePressed(e -> {
				mouseDownX = e.getScreenX();
				mouseDownY = e.getScreenY();
				start.set(new Point2D(mouseDownX, mouseDownY));
				end.set(start.get());
				selectedShapes.clear();
				selectedShapes.addAll(networkNodeSelectionModel.getSelectedItems());
				e.consume();
			});
			shape.setOnMouseDragged(e -> {
				if (selectedShapes.contains(shape)) {
					var dx = e.getScreenX() - mouseDownX;
					var dy = e.getScreenY() - mouseDownY;
					for (var selected : selectedShapes) {
						selected.setTranslateX(selected.getTranslateX() + dx);
						selected.setTranslateY(selected.getTranslateY() + dy);
					}
					mouseDownX = e.getScreenX();
					mouseDownY = e.getScreenY();
					end.set(new Point2D(mouseDownX, mouseDownY));
				}
				e.consume();
			});
			shape.setOnMouseReleased(e -> {
				if (selectedShapes.contains(shape)) {
					if (!e.isStillSincePress()) {
						undoManager.add("move nodes",
								() -> {
									for (var selected : selectedShapes) {
										selected.setTranslateX(selected.getTranslateX() - (end.get().getX() - start.get().getX()));
										selected.setTranslateY(selected.getTranslateY() - (end.get().getY() - start.get().getY()));
									}
								},
								() -> {
									for (var selected : selectedShapes) {
										selected.setTranslateX(selected.getTranslateX() + (end.get().getX() - start.get().getX()));
										selected.setTranslateY(selected.getTranslateY() + (end.get().getY() - start.get().getY()));
									}
								});
					}
				}
				e.consume();
			});
		}

		for (var shape : nodeShapeMap.values()) {
			shape.setOnMouseClicked(e -> {
				if (e.isStillSincePress()) {
					if (!e.isShiftDown() && !e.isShortcutDown() && SplitsTree6.isDesktop())
						networkNodeSelectionModel.clearSelection();
					networkNodeSelectionModel.toggleSelection(shape);
					e.consume();
				}
			});
		}

		var graphOptional = nodeShapeMap.keySet().stream().filter(v -> v.getOwner() != null).map(v -> (PhyloGraph) v.getOwner()).findAny();

		if (graphOptional.isPresent()) {
			var graph = graphOptional.get();

			for (var id : taxonLabelMap.keySet()) {
				try {
					var label = taxonLabelMap.get(id);
					if (label != null) {
						var node = graph.getTaxon2Node(id);
						label.layoutXProperty().addListener((v, o, n) -> {
							edits.set(NetworkEdits.addLayoutNodeLabelEdits(edits.get(), List.of(node), label.getLayoutX(), label.getLayoutY()));
						});
						label.layoutYProperty().addListener((v, o, n) -> {
							edits.set(NetworkEdits.addLayoutNodeLabelEdits(edits.get(), List.of(node), label.getLayoutX(), label.getLayoutY()));
						});

						var shape = nodeShapeMap.get(node);
						var taxon = idTaxonMap.apply(id);
						if (taxon != null && shape != null) {
							shape.setOnContextMenuRequested(m -> showContextMenu(m, stage, undoManager, label));
							label.setOnContextMenuRequested(m -> showContextMenu(m, stage, undoManager, label));

							label.setOnMouseClicked(e -> {
								if (e.isStillSincePress()) {
									if (!e.isShiftDown() && !e.isShortcutDown() && SplitsTree6.isDesktop())
										taxonSelectionModel.clearSelection();
									taxonSelectionModel.toggleSelection(taxon);
									e.consume();
								}
							});

							label.setOnMouseEntered(shape.getOnMouseEntered());
							label.setOnMouseExited(shape.getOnMouseExited());

							var start = new Single<Point2D>();
							var end = new Single<Point2D>();

							label.setOnMousePressed(e -> {
								if (taxonSelectionModel.isSelected(taxon)) {
									mouseDownX = e.getScreenX();
									mouseDownY = e.getScreenY();
									start.set(new Point2D(mouseDownX, mouseDownY));
									end.set(start.get());
									e.consume();
								}
							});

							label.setOnMouseDragged(e -> {
								if (taxonSelectionModel.isSelected(taxon)) {
									for (var wTaxon : taxonSelectionModel.getSelectedItems()) {
										var wLabel = taxonLabelMap.get(taxonIdMap.apply(wTaxon));

										var dx = e.getScreenX() - mouseDownX;
										var dy = e.getScreenY() - mouseDownY;
										wLabel.setLayoutX(wLabel.getLayoutX() + dx);
										wLabel.setLayoutY(wLabel.getLayoutY() + dy);
									}
									mouseDownX = e.getScreenX();
									mouseDownY = e.getScreenY();
									end.set(new Point2D(mouseDownX, mouseDownY));
									e.consume();
								}
							});

							label.setOnMouseReleased(e -> {
								if (!e.isStillSincePress()) {
									undoManager.add("move label",
											() -> {
												label.setLayoutX(label.getLayoutX() - (end.get().getX() - start.get().getX()));
												label.setLayoutY(label.getLayoutY() - (end.get().getY() - start.get().getY()));
											},
											() -> {
												label.setLayoutX(label.getLayoutX() + (end.get().getX() - start.get().getX()));
												label.setLayoutY(label.getLayoutY() + (end.get().getY() - start.get().getY()));
											});
								}
							});
						}
					}
				} catch (Exception ignored) {
				}
			}
		}
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

