/*
 * InteractionSetup.java Copyright (C) 2022 Daniel H. Huson
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

package splitstree6.view.trees;

import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.collections.MapChangeListener;
import javafx.collections.ObservableMap;
import javafx.collections.SetChangeListener;
import javafx.collections.WeakSetChangeListener;
import javafx.event.EventHandler;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.input.ContextMenuEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.shape.Shape;
import javafx.stage.Stage;
import jloda.fx.control.RichTextLabel;
import jloda.fx.label.EditLabelDialog;
import jloda.fx.selection.SelectionModel;
import jloda.fx.util.RunAfterAWhile;
import jloda.fx.util.SelectionEffectBlue;
import jloda.graph.Edge;
import jloda.graph.Node;
import jloda.phylo.PhyloGraph;
import jloda.phylo.PhyloTree;
import splitstree6.data.TaxaBlock;
import splitstree6.data.parts.Taxon;
import splitstree6.layout.tree.LabeledEdgeShape;
import splitstree6.layout.tree.LabeledNodeShape;
import splitstree6.layout.tree.LayoutOrientation;
import splitstree6.layout.tree.TreeDiagramType;

import java.util.BitSet;
import java.util.stream.Collectors;

/**
 * sets up node and edge interaction
 * Daniel Huson, 7.2022
 */
public class InteractionSetup {
	private final SetChangeListener<Taxon> taxonSelectionChangeListener;
	private final SetChangeListener<Edge> edgeSelectionChangeListener;

	private double mouseDownX;
	private double mouseDownY;

	public InteractionSetup(Stage stage, Pane pane, TaxaBlock taxaBlock, TreeDiagramType diagram, ObjectProperty<LayoutOrientation> orientation,
							SelectionModel<Taxon> taxonSelectionModel, SelectionModel<Edge> edgeSelectionModel,
							ObservableMap<Node, LabeledNodeShape> nodeShapeMap, ObservableMap<Edge, LabeledEdgeShape> edgeShapeMap) {

		pane.addEventHandler(MouseEvent.MOUSE_CLICKED, e -> {
			if (e.isStillSincePress()) {
				if (!e.isShiftDown() && !e.isAltDown()) {
					Platform.runLater(edgeSelectionModel::clearSelection);
					Platform.runLater(taxonSelectionModel::clearSelection);
				}
				e.consume();
			}
		});

		taxonSelectionChangeListener = e -> {
			if (nodeShapeMap.size() > 0) {
				if (nodeShapeMap.keySet().iterator().next().getOwner() instanceof PhyloTree tree) {
					if (e.wasAdded()) {
						var t = taxaBlock.indexOf(e.getElementAdded());
						if (t != -1) {
							var v = tree.getTaxon2Node(t);
							if (v != null) {
								var labeledShape = nodeShapeMap.get(v);
								for (var node : labeledShape.all()) {
									node.setEffect(SelectionEffectBlue.getInstance());
								}
							}

						}
					}
					if (e.wasRemoved()) {
						var t = taxaBlock.indexOf(e.getElementRemoved());
						if (t != -1) {
							var v = tree.getTaxon2Node(t);
							if (v != null) {
								var labeledShape = nodeShapeMap.get(v);
								for (var node : labeledShape.all()) {
									node.setEffect(null);
								}
							}
						}
					}

					RunAfterAWhile.apply(this, () -> Platform.runLater(() -> updateEdgeSelection(tree, taxaBlock, taxonSelectionModel, edgeSelectionModel)));
				}
			}
		};
		taxonSelectionModel.getSelectedItems().addListener(new WeakSetChangeListener<>(taxonSelectionChangeListener));

		edgeSelectionChangeListener = e -> {
			if (e.wasAdded()) {
				var edgeShape = edgeShapeMap.get(e.getElementAdded());
				if (edgeShape != null) {
					for (var node : edgeShape.all()) {
						node.setEffect(SelectionEffectBlue.getInstance());
					}
				}
			} else if (e.wasRemoved()) {
				var labeledShape = edgeShapeMap.get(e.getElementRemoved());
				if (labeledShape != null) {
					for (var node : labeledShape.all()) {
						node.setEffect(null);
					}
				}
			}
		};
		edgeSelectionModel.getSelectedItems().addListener(new WeakSetChangeListener<>(edgeSelectionChangeListener));

		// setup mouse interaction for new created node shapes
		{
			var mousePressedOnTaxonLabelHandler = createMousePressedOnTaxonLabelHandler();
			var mouseDraggedOnTaxonLabelHandler = createMouseDraggedOnTaxonLabelHandler(taxaBlock, taxonSelectionModel, diagram, orientation, nodeShapeMap);

			var mouseEnteredHandler = createMouseEnteredNodeHandler();
			var mouseExitedHandler = createMouseExitedNodeHandler();
			nodeShapeMap.addListener((MapChangeListener<? super Node, ? super LabeledNodeShape>) ce -> {
				if (ce.wasAdded()) {
					var v = ce.getKey();
					var labeledShape = nodeShapeMap.get(v);
					if (labeledShape.hasLabel()) {
						var handler = createMouseClickedOnNodeHandler(taxaBlock, taxonSelectionModel, v);
						var contextMenuHander = createNodeContextMenuHandler(stage, labeledShape.getLabel());

						for (var node : labeledShape.all()) {
							node.setOnMouseClicked(handler);
							node.setOnContextMenuRequested(contextMenuHander);
							node.setOnMouseEntered(mouseEnteredHandler);
							node.setOnMouseExited(mouseExitedHandler);
						}
						labeledShape.getLabel().setOnMousePressed(mousePressedOnTaxonLabelHandler);
						labeledShape.getLabel().setOnMouseDragged(mouseDraggedOnTaxonLabelHandler);
					}
				}
			});
		}

		// setup mouse interaction for new created edges shapes
		{
			var mouseEnteredHandler = createMouseEnteredEdgeHandler();
			var mouseExitedHandler = createMouseExitedEdgeHandler();

			edgeShapeMap.addListener((MapChangeListener<? super Edge, ? super LabeledEdgeShape>) ce -> {
				if (ce.wasAdded()) {
					var e = ce.getKey();
					var clickedHandler = createMouseClickedOnEdgeHandler(taxaBlock, taxonSelectionModel, edgeSelectionModel, e);
					for (var node : ce.getValueAdded().all()) {
						node.setOnMouseClicked(clickedHandler);
						node.setOnMouseEntered(mouseEnteredHandler);
						node.setOnMouseExited(mouseExitedHandler);
					}
				}
			});
		}
	}

	public void initializeSelection(TaxaBlock taxaBlock, SelectionModel<Taxon> taxonSelectionModel, SelectionModel<Edge> edgeSelectionModel, ObservableMap<Node, LabeledNodeShape> nodeShapeMap) {
		if (taxonSelectionModel.size() > 0 && nodeShapeMap.size() > 0) {
			if (nodeShapeMap.keySet().iterator().next().getOwner() instanceof PhyloTree tree) {
				for (var v : tree.nodes()) {
					for (var t : tree.getTaxa(v)) {
						var taxon = taxaBlock.get(t);
						if (taxonSelectionModel.isSelected(taxon)) {
							for (var node : nodeShapeMap.get(v).all()) {
								node.setEffect(SelectionEffectBlue.getInstance());
							}
						}
					}
				}
				updateEdgeSelection(tree, taxaBlock, taxonSelectionModel, edgeSelectionModel);
			}
		}
	}

	private void updateEdgeSelection(PhyloTree tree, TaxaBlock taxaBlock, SelectionModel<Taxon> taxonSelectionModel, SelectionModel<Edge> edgeSelectionModel) {
		edgeSelectionModel.clearSelection();
		var nSelected = taxonSelectionModel.size();
		try (var selectedBelow = tree.newNodeIntArray(); var edgesToSelect = tree.newEdgeSet()) {
			tree.postorderTraversal(v -> {
				var below = 0;
				for (var t : tree.getTaxa(v)) {
					var taxon = taxaBlock.get(t);
					if (taxon != null && taxonSelectionModel.isSelected(taxon))
						below++;
				}
				var hasChildWithNone = false;
				for (var w : v.children()) {
					var belowChild = selectedBelow.get(w);
					if (belowChild == 0)
						hasChildWithNone = true;
					else
						below += belowChild;
				}
				if (v.getInDegree() == 1 && below > 0 && (below < nSelected || !hasChildWithNone)) {
					edgesToSelect.add(v.getFirstInEdge());
				}
				selectedBelow.set(v, below);
			});
			edgeSelectionModel.selectAll(edgesToSelect);
		}
	}

	private EventHandler<MouseEvent> createMousePressedOnTaxonLabelHandler() {
		return me -> {
			if (me.getSource() instanceof RichTextLabel clickedLabel && clickedLabel.getEffect() != null) {
				mouseDownX = me.getScreenX();
				mouseDownY = me.getScreenY();
				//me.consume();
			}
		};
	}

	private EventHandler<MouseEvent> createMouseDraggedOnTaxonLabelHandler(TaxaBlock taxaBlock, SelectionModel<Taxon> taxonSelectionModel,
																		   TreeDiagramType diagram, ObjectProperty<LayoutOrientation> orientation,
																		   ObservableMap<Node, LabeledNodeShape> nodeShapeMap) {
		return me -> {
			if (nodeShapeMap.keySet().iterator().next().getOwner() instanceof PhyloGraph graph) {
				if (me.getSource() instanceof RichTextLabel clickedLabel && clickedLabel.getEffect() != null) {
					for (var taxon : taxonSelectionModel.getSelectedItems()) {
						var v = graph.getTaxon2Node(taxaBlock.indexOf(taxon));
						if (v != null) {
							var nodeShape = nodeShapeMap.get(v);
							if (nodeShape != null && nodeShape.hasLabel()) {
								var label = nodeShape.getLabel();
								var dx = me.getScreenX() - mouseDownX;
								var dy = me.getScreenY() - mouseDownY;

								if (diagram != TreeDiagramType.RadialPhylogram) {
									switch (orientation.get()) {
										case Rotate90Deg -> {
											var tmp = dx;
											dx = -dy;
											dy = tmp;
										}
										case Rotate180Deg -> {
											dx = -dx;
											dy = -dy;
										}
										case Rotate270Deg -> {
											var tmp = dx;
											dx = dy;
											dy = -tmp;
										}
										case FlipRotate0Deg -> dx = -dx;
										case FlipRotate90Deg -> {
											var tmp = dx;
											dx = dy;
											dy = tmp;
										}
										case FlipRotate180Deg -> dy = -dy;
										case FlipRotate270Deg -> {
											var tmp = dx;
											dx = -dy;
											dy = -tmp;
										}
									}
								}

								label.setLayoutX(label.getLayoutX() + dx);
								label.setLayoutY(label.getLayoutY() + dy);
							}
						}
					}
					mouseDownX = me.getScreenX();
					mouseDownY = me.getScreenY();
					me.consume();
				}
			}
		};
	}

	private EventHandler<MouseEvent> createMouseClickedOnNodeHandler(TaxaBlock taxaBlock, SelectionModel<Taxon> taxonSelectionModel, Node v) {
		return me -> {
			if (v.getOwner() instanceof PhyloGraph graph) {
				if (!me.isShiftDown()) {
					taxonSelectionModel.clearSelection();
				}
				for (var t : graph.getTaxa(v)) {
					var taxon = taxaBlock.get(t);
					if (taxon != null)
						taxonSelectionModel.toggleSelection(taxon);
				}
				me.consume();
			}
		};
	}

	private EventHandler<MouseEvent> createMouseClickedOnEdgeHandler(TaxaBlock taxaBlock, SelectionModel<Taxon> taxonSelectionModel, SelectionModel<Edge> edgeSelectionModel, Edge e) {
		return me -> {
			if (e.getOwner() instanceof PhyloTree tree) {
				if (me.getClickCount() == 1) {
					if (me.isAltDown()) {
						edgeSelectionModel.toggleSelection(e);
					} else {
						if (!me.isShiftDown()) {
							taxonSelectionModel.clearSelection();
						}
						edgeSelectionModel.toggleSelection(e);
						var bits = new BitSet();
						tree.preorderTraversal(e.getTarget(), v -> {
							for (var t : tree.getTaxa(v)) {
								bits.set(t);
							}
						});
						if (me.isAltDown()) {
							bits.flip(1, taxaBlock.getNtax() + 1);
						}
						var taxa = bits.stream().mapToObj(taxaBlock::get).collect(Collectors.toList());
						if (edgeSelectionModel.isSelected(e)) {
							taxonSelectionModel.selectAll(taxa);
						} else {
							taxonSelectionModel.clearSelection(taxa);
						}
					}
					me.consume();
				}
			}
		};
	}

	private EventHandler<MouseEvent> createMouseEnteredNodeHandler() {
		return me -> {
			if (me.getSource() instanceof Region region) {
				region.setScaleX(1.1 * region.getScaleX());
				region.setScaleY(1.1 * region.getScaleY());
			}
		};
	}

	private EventHandler<MouseEvent> createMouseExitedNodeHandler() {
		return me -> {
			if (me.getSource() instanceof Region region) {
				region.setScaleX(1 / 1.1 * region.getScaleX());
				region.setScaleY(1 / 1.1 * region.getScaleY());
			}
		};
	}


	private EventHandler<MouseEvent> createMouseEnteredEdgeHandler() {
		return me -> {
			if (me.getSource() instanceof Shape shape) {
				shape.setUserData(shape.getStrokeWidth());
				shape.setStrokeWidth(shape.getStrokeWidth() + 4);
			} else if (me.getSource() instanceof RichTextLabel label) {
				label.setScaleX(1.1 * label.getScaleX());
				label.setScaleY(1.1 * label.getScaleY());
			}
		};
	}

	private EventHandler<MouseEvent> createMouseExitedEdgeHandler() {
		return me -> {
			if (me.getSource() instanceof Shape shape) {
				shape.setStrokeWidth(shape.getStrokeWidth() - 4);
			} else if (me.getSource() instanceof RichTextLabel label) {
				label.setScaleX(1 / 1.1 * label.getScaleX());
				label.setScaleY(1 / 1.1 * label.getScaleY());
			}
		};
	}

	public static EventHandler<? super ContextMenuEvent> createNodeContextMenuHandler(Stage stage, RichTextLabel label) {
		return event -> {
			var editLabelMenuItem = new MenuItem("Edit Label...");
			editLabelMenuItem.setOnAction(e -> {
				var editLabelDialog = new EditLabelDialog(stage, label);
				var result = editLabelDialog.showAndWait();
				result.ifPresent(label::setText);
			});
			var menu = new ContextMenu();
			menu.getItems().add(editLabelMenuItem);
			menu.show(label, event.getScreenX(), event.getScreenY());
		};
	}
}
