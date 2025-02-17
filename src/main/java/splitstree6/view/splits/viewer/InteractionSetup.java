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

package splitstree6.view.splits.viewer;

import javafx.application.Platform;
import javafx.beans.InvalidationListener;
import javafx.beans.WeakInvalidationListener;
import javafx.event.EventHandler;
import javafx.geometry.Point2D;
import javafx.scene.Group;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TextField;
import javafx.scene.input.ContextMenuEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import javafx.scene.shape.Shape;
import javafx.stage.Stage;
import jloda.fx.control.RichTextLabel;
import jloda.fx.label.EditLabelDialog;
import jloda.fx.selection.SelectionModel;
import jloda.fx.undo.UndoManager;
import jloda.fx.undo.UndoableRedoableCommand;
import jloda.fx.util.BasicFX;
import jloda.fx.util.GeometryUtilsFX;
import jloda.fx.util.SelectionEffectBlue;
import jloda.graph.GraphTraversals;
import jloda.graph.Node;
import jloda.phylo.PhyloSplitsGraph;
import jloda.util.BitSetUtils;
import splitstree6.data.parts.Taxon;
import splitstree6.layout.splits.RotateSplit;
import splitstree6.layout.tree.LabeledNodeShape;
import splitstree6.main.SplitsTree6;
import splitstree6.splits.ASplit;
import splitstree6.view.utils.NodeLabelDialog;
import splitstree6.view.utils.ShapeUtils;

import java.util.ArrayList;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * split network mouse interaction
 * Daniel Huson, 3.2022
 */
public class InteractionSetup {
	private static boolean nodeShapeOrLabelEntered;
	private static boolean edgeShapeEntered;
	private static double mouseDownX;
	private static double mouseDownY;
	private static double totalAngle;

	private final Stage stage;
	private final UndoManager undoManager;
	private final SelectionModel<Taxon> taxonSelectionModel;
	private final SelectionModel<Integer> splitSelectionModel;

	private InvalidationListener taxonSelectionInvalidationListener;
	private InvalidationListener splitSelectionInvalidationListener;

	private static EventHandler<MouseEvent> mousePressedHandler;
	private static EventHandler<MouseEvent> mouseDraggedHandler;
	private static EventHandler<MouseEvent> mouseReleasedHandler;



	/**
	 * constructor
	 */
	public InteractionSetup(Stage stage, Pane pane, UndoManager undoManager, SelectionModel<Taxon> taxonSelectionModel, SelectionModel<Integer> splitSelectionModel) {
		this.stage = stage;
		this.undoManager = undoManager;
		this.taxonSelectionModel = taxonSelectionModel;
		this.splitSelectionModel = splitSelectionModel;

		pane.setOnMouseClicked(e -> {
			if (e.isStillSincePress() && !e.isShiftDown()) {
				Platform.runLater(splitSelectionModel::clearSelection);
				Platform.runLater(taxonSelectionModel::clearSelection);
				for (var textField : BasicFX.getAllRecursively(pane, TextField.class)) {
					if (textField.getParent() instanceof Group group) {
						Platform.runLater(() -> group.getChildren().remove(textField));
					}
				}
				e.consume();
			}
		});
	}

	/**
	 * setup split network mouse interaction
	 */
	public void setup(Map<Integer, RichTextLabel> taxonLabelMap, Map<Node, LabeledNodeShape> nodeShapeMap, Map<Integer, ArrayList<Shape>> splitShapesMap,
					  Function<Integer, Taxon> idTaxonMap, Function<Taxon, Integer> taxonIdMap, Function<Integer, ASplit> idSplitMap, SplitsView view) {
		var graphOptional = nodeShapeMap.keySet().stream().map(v -> (PhyloSplitsGraph) v.getOwner()).findAny();

		mousePressedHandler = e -> {
			mouseDownX = e.getScreenX();
			mouseDownY = e.getScreenY();
			totalAngle = 0.0;
			e.consume();
		};

		mouseDraggedHandler = e -> {
			var oldLocation = new Point2D(mouseDownX, mouseDownY);
			var newLocation = new Point2D(e.getScreenX(), e.getScreenY());
			var shapes = new ArrayList<Shape>();
			splitSelectionModel.getSelectedItems().stream().map(splitShapesMap::get).forEach(shapes::addAll);
			var center = ShapeUtils.getCenterScreenCoordinates(shapes);
			if (center.distance(oldLocation) > 5 && center.distance(newLocation) > 5) {
				var angle = GeometryUtilsFX.computeObservedAngle(center, newLocation, oldLocation);
				totalAngle += angle;
				RotateSplit.apply(splitSelectionModel.getSelectedItems(), angle, nodeShapeMap);
			}
			mouseDownX = e.getScreenX();
			mouseDownY = e.getScreenY();
			e.consume();
		};

		mouseReleasedHandler = e -> {
			if (totalAngle != 0) {
				var splits = new ArrayList<>(splitSelectionModel.getSelectedItems());
				var oldEdits = view.getOptionEdits();
				view.setOptionEdits(SplitNetworkEdits.addAngles(oldEdits, splits, totalAngle));
				undoManager.add(
						UndoableRedoableCommand.create("Rotate split",
								() -> {
									RotateSplit.apply(splits, -totalAngle, nodeShapeMap);
									view.setOptionEdits(oldEdits);
								},
								() -> {
									RotateSplit.apply(splits, totalAngle, nodeShapeMap);
									view.setOptionEdits(SplitNetworkEdits.addAngles(oldEdits, splits, totalAngle));
								}
						)
				);
				if (e.isStillSincePress())
					e.consume();
			}
		};

		if (graphOptional.isPresent()) {
			var graph = graphOptional.get();

			for (var id : taxonLabelMap.keySet()) {
				try {
					var label = taxonLabelMap.get(id);
					var v = graph.getTaxon2Node(id);
					var nodeShape = nodeShapeMap.get(v);
					var taxon = idTaxonMap.apply(id);
					if (taxon != null && nodeShape != null) {
						nodeShape.setOnContextMenuRequested(m -> showContextMenu(m, stage, undoManager, label));
						label.setOnContextMenuRequested(m -> showContextMenu(m, stage, undoManager, label));

						if (SplitsTree6.nodeZoomOnMouseOver) {
							nodeShape.setOnMouseEntered(e -> {
								if (!e.isStillSincePress() && !nodeShapeOrLabelEntered) {
									nodeShapeOrLabelEntered = true;
									nodeShape.setScaleX(1.2 * nodeShape.getScaleX());
									nodeShape.setScaleY(1.2 * nodeShape.getScaleY());
									label.setScaleX(1.1 * label.getScaleX());
									label.setScaleY(1.1 * label.getScaleY());
									e.consume();
								}
							});
							nodeShape.setOnMouseExited(e -> {
								if (nodeShapeOrLabelEntered) {
									nodeShape.setScaleX(nodeShape.getScaleX() / 1.2);
									nodeShape.setScaleY(nodeShape.getScaleY() / 1.2);
									label.setScaleX(label.getScaleX() / 1.1);
									label.setScaleY(label.getScaleY() / 1.1);
									nodeShapeOrLabelEntered = false;
									e.consume();
								}
							});
						}

						final EventHandler<MouseEvent> mouseClickedHandler = e -> {
							if (e.isStillSincePress()) {
								if (!e.isShiftDown() && SplitsTree6.isDesktop())
									taxonSelectionModel.clearSelection();
								taxonSelectionModel.toggleSelection(taxon);
								e.consume();
							}
						};
						nodeShape.setOnMouseClicked(mouseClickedHandler);
						label.setOnMouseClicked(mouseClickedHandler);

						if (SplitsTree6.nodeZoomOnMouseOver) {
							label.setOnMouseEntered(nodeShape.getOnMouseEntered());
							label.setOnMouseExited(nodeShape.getOnMouseExited());
						}

						nodeShape.setOnMousePressed(mousePressedHandler);

						nodeShape.setOnMouseDragged(mouseDraggedHandler);

						nodeShape.setOnMouseReleased(mouseReleasedHandler);

						label.setOnMousePressed(e -> {
							if (taxonSelectionModel.isSelected(taxon)) {
								mouseDownX = e.getScreenX();
								mouseDownY = e.getScreenY();
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
								e.consume();
							}
						});
					}
				} catch (Exception ignored) {
				}
			}

			for (var splitId : splitShapesMap.keySet()) {
				for (var shape : splitShapesMap.get(splitId)) {
					shape.setPickOnBounds(false);

					if (SplitsTree6.nodeZoomOnMouseOver) {
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
					}

					shape.setOnMouseClicked(e -> {
						if (e.isStillSincePress() && idSplitMap.apply(splitId) != null) {
								if (e.getClickCount() == 1) {
									if (!e.isShiftDown() && !e.isShortcutDown() && SplitsTree6.isDesktop())
										splitSelectionModel.clearSelection();
									splitSelectionModel.toggleSelection(splitId);

									var split = idSplitMap.apply(splitId);
									var partA = split.getA();
									var partB = split.getB();
									var whichPart = ((partA.cardinality() < partB.cardinality()) == !e.isAltDown() ? partA : partB);
									var taxa = BitSetUtils.asStream(whichPart).map(idTaxonMap).collect(Collectors.toList());
									if (splitSelectionModel.isSelected(splitId)) {
										if (!e.isShiftDown() && SplitsTree6.isDesktop())
											taxonSelectionModel.clearSelection();
										taxonSelectionModel.selectAll(taxa);
									} else
										taxonSelectionModel.clearSelection(taxa);
								} else if (e.getClickCount() == 2) {
									splitSelectionModel.select(splitId);

									var selectedTaxonIds = BitSetUtils.asBitSet(taxonSelectionModel.getSelectedItems().stream().map(taxonIdMap).collect(Collectors.toList()));
									var start = graph.nodeStream().filter(z -> BitSetUtils.intersection(selectedTaxonIds, BitSetUtils.asBitSet(graph.getTaxa(z))).cardinality() > 0).findAny();
									if (start.isPresent()) {
										try (var visited = graph.newNodeSet()) {
											GraphTraversals.traverseReachable(start.get(), f -> graph.getSplit(f) != splitId, visited::add);
											for (var f : graph.edges()) {
												if (visited.contains(f.getSource()) && visited.contains(f.getTarget()))
													splitSelectionModel.select(graph.getSplit(f));
											}
										}
									}
								}
							e.consume();
						}
					});

					shape.setOnMousePressed(mousePressedHandler);
					shape.setOnMouseDragged(mouseDraggedHandler);
					shape.setOnMouseReleased(mouseReleasedHandler);
				}
			}

			taxonSelectionInvalidationListener = e -> {
				try {
					for (var t : taxonLabelMap.keySet()) {
						var taxon = idTaxonMap.apply(t);
						if (taxon != null) {
							var label = taxonLabelMap.get(t);
							label.setEffect(taxonSelectionModel.isSelected(taxon) ? SelectionEffectBlue.getInstance() : null);
							var shape = nodeShapeMap.get(graph.getTaxon2Node(t));
							if (shape != null)
								shape.setEffect(taxonSelectionModel.isSelected(taxon) ? SelectionEffectBlue.getInstance() : null);
						}
					}
				} catch (Exception ignored) {
				}
			};
			taxonSelectionModel.getSelectedItems().addListener(new WeakInvalidationListener(taxonSelectionInvalidationListener));
			taxonSelectionInvalidationListener.invalidated(null);

			splitSelectionInvalidationListener = e -> {
				for (var splitId : splitShapesMap.keySet()) {
					for (var shape : splitShapesMap.get(splitId)) {
						shape.setEffect(splitSelectionModel.isSelected(splitId) ? SelectionEffectBlue.getInstance() : null);
					}
				}
			};
			splitSelectionModel.getSelectedItems().addListener(splitSelectionInvalidationListener);
		}
	}

	private static void showContextMenu(ContextMenuEvent event, Stage stage, UndoManager undoManager, RichTextLabel label) {
		var editLabelMenuItem = new MenuItem("Edit Label...");
		editLabelMenuItem.setOnAction(e -> {
			if (SplitsTree6.isDesktop()) {
				NodeLabelDialog.apply(undoManager, stage, label);
			} else {
				NodeLabelDialog.apply(undoManager, label, null);
			}
		});
		var menu = new ContextMenu();
		menu.getItems().add(editLabelMenuItem);
		menu.show(label, event.getScreenX(), event.getScreenY());
	}

}

