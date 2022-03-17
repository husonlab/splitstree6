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
import javafx.beans.InvalidationListener;
import javafx.beans.WeakInvalidationListener;
import javafx.beans.property.ObjectProperty;
import javafx.event.EventHandler;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.input.ContextMenuEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import javafx.scene.shape.Shape;
import javafx.stage.Stage;
import jloda.fx.control.RichTextLabel;
import jloda.fx.label.EditLabelDialog;
import jloda.fx.selection.SelectionModel;
import jloda.fx.util.SelectionEffectBlue;
import jloda.fx.util.TriConsumer;
import jloda.graph.Edge;
import jloda.phylo.PhyloGraph;
import jloda.phylo.PhyloTree;
import jloda.util.Pair;
import splitstree6.data.TaxaBlock;
import splitstree6.data.parts.Taxon;
import splitstree6.layout.tree.LayoutOrientation;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * create dragging of selected taxon labels
 * Daniel Huson, 2021
 */
public class InteractionSetup {
	private final Stage stage;
	private final TaxaBlock taxaBlock;
	private final SelectionModel<Taxon> taxonSelectionModel;

	private final Map<Taxon, Pair<Shape, RichTextLabel>> taxonShapeLabelMap;
	private final EventHandler<MouseEvent> mousePressedHandler;
	private final EventHandler<MouseEvent> mouseDraggedHandler;

	private final InvalidationListener invalidationListener;

	private static double mouseDownX;
	private static double mouseDownY;

	public InteractionSetup(Stage stage, TaxaBlock taxaBlock, SelectionModel<Taxon> taxonSelectionModel, ObjectProperty<LayoutOrientation> orientation) {
		this.stage = stage;
		this.taxaBlock = taxaBlock;
		this.taxonSelectionModel = taxonSelectionModel;
		taxonShapeLabelMap = new HashMap<>();

		mousePressedHandler = e -> {
			if (e.getSource() instanceof Pane pane && pane.getEffect() != null) { // need a better way to determine whether this label is selected
				mouseDownX = e.getScreenX();
				mouseDownY = e.getScreenY();
				e.consume();
			}
		};

		mouseDraggedHandler = e -> {
			if (e.getSource() instanceof Pane pane && pane.getEffect() != null) {
				for (var taxon : taxonSelectionModel.getSelectedItems()) {
					var shapeLabel = taxonShapeLabelMap.get(taxon);
					if (shapeLabel != null) {
						var label = shapeLabel.getSecond();
						var dx = e.getScreenX() - mouseDownX;
						var dy = e.getScreenY() - mouseDownY;

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

						label.setLayoutX(label.getLayoutX() + dx);
						label.setLayoutY(label.getLayoutY() + dy);
					}
				}
				mouseDownX = e.getScreenX();
				mouseDownY = e.getScreenY();
			}
		};

		invalidationListener = e -> {
			for (var t : taxonShapeLabelMap.keySet()) {
				var shapeLabel = taxonShapeLabelMap.get(t);
				shapeLabel.getFirst().setEffect(taxonSelectionModel.isSelected(t) ? SelectionEffectBlue.getInstance() : null);
				shapeLabel.getSecond().setEffect(taxonSelectionModel.isSelected(t) ? SelectionEffectBlue.getInstance() : null);
			}
		};
		taxonSelectionModel.getSelectedItems().addListener(new WeakInvalidationListener(invalidationListener));
	}

	private boolean nodeShapeOrLabelEntered;

	public TriConsumer<jloda.graph.Node, Shape, RichTextLabel> createNodeCallback() {
		return (v, shape, label) -> Platform.runLater(() -> {

			if (v.getOwner() instanceof PhyloGraph phyloGraph) {
				for (var t : phyloGraph.getTaxa(v)) {
					if (t <= taxaBlock.getNtax()) {
						var taxon = taxaBlock.get(t);
						taxonShapeLabelMap.put(taxaBlock.get(t), new Pair<>(shape, label));
						label.setOnMousePressed(mousePressedHandler);
                        label.setOnMouseDragged(mouseDraggedHandler);
                        final EventHandler<MouseEvent> mouseClickedHandler = e -> {
                            if (e.isStillSincePress()) {
                                if (!e.isShiftDown())
                                    taxonSelectionModel.clearSelection();
                                taxonSelectionModel.toggleSelection(taxon);
                                e.consume();
                            }
                        };
						label.setOnContextMenuRequested(m -> showContextMenu(stage, m, taxon, label));
						shape.setOnContextMenuRequested(m -> showContextMenu(stage, m, taxon, label));
						shape.setOnMouseClicked(mouseClickedHandler);
						label.setOnMouseClicked(mouseClickedHandler);
						if (taxonSelectionModel.isSelected(taxon)) {
							shape.setEffect(SelectionEffectBlue.getInstance());
							label.setEffect(SelectionEffectBlue.getInstance());
						}
					}
				}
			}

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
			label.setOnMouseEntered(shape.getOnMouseEntered());
			label.setOnMouseExited(shape.getOnMouseExited());

		});
	}

	private void showContextMenu(Stage stage, ContextMenuEvent event, Taxon taxon, RichTextLabel label) {
		var editLabelMenuItem = new MenuItem("Edit Label...");
		editLabelMenuItem.setOnAction(e -> {
			var editLabelDialog = new EditLabelDialog(stage, label);
			var result = editLabelDialog.showAndWait();
			result.ifPresent(label::setText);
		});
		var menu = new ContextMenu();
		menu.getItems().add(editLabelMenuItem);
		menu.show(label, event.getScreenX(), event.getScreenY());
	}

	public BiConsumer<Edge, Shape> createEdgeCallback() {
		return (edge, shape) -> {
			shape.setPickOnBounds(false);

			shape.setOnMouseEntered(e ->
			{
				shape.setUserData(shape.getStrokeWidth());
				shape.setStrokeWidth(shape.getStrokeWidth() + 4);
			});
			shape.setOnMouseExited(e -> {
				shape.setStrokeWidth(shape.getStrokeWidth() - 4);
				shape.setUserData(null);
			});

			if (edge.getOwner() instanceof PhyloTree tree) {
				shape.setOnMouseClicked(e -> {
					if (e.getClickCount() >= 1) {
						if (!e.isShiftDown())
							taxonSelectionModel.clearSelection();
						if (!e.isAltDown()) {
							tree.preorderTraversal(edge.getTarget(), v -> {
								for (var t : tree.getTaxa(v)) {
									taxonSelectionModel.select(taxaBlock.get(t));
								}
							});
						} else {
							tree.preorderTraversal(tree.getRoot(), v -> v != edge.getTarget(), v -> {
								for (var t : tree.getTaxa(v)) {
									taxonSelectionModel.select(taxaBlock.get(t));
								}
							});
						}
						e.consume();
					}
				});
			}
		};
	}
}
