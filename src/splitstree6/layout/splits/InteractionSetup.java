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

package splitstree6.layout.splits;

import javafx.application.Platform;
import javafx.beans.InvalidationListener;
import javafx.beans.WeakInvalidationListener;
import javafx.collections.ObservableMap;
import javafx.event.EventHandler;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import javafx.scene.shape.Shape;
import jloda.fx.control.RichTextLabel;
import jloda.fx.graph.GraphTraversals;
import jloda.fx.selection.SelectionModel;
import jloda.fx.util.SelectionEffectBlue;
import jloda.fx.util.TriConsumer;
import jloda.graph.Edge;
import jloda.graph.Node;
import jloda.phylo.PhyloGraph;
import jloda.phylo.PhyloSplitsGraph;
import jloda.util.BitSetUtils;
import jloda.util.Pair;
import splitstree6.data.SplitsBlock;
import splitstree6.data.TaxaBlock;
import splitstree6.data.parts.Taxon;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

/**
 * set up mouse interaction on labeled nodes and edges
 * Daniel Huson, 12.2021
 */
public class InteractionSetup {
	private final TaxaBlock taxaBlock;
	private final SplitsBlock splitsBlock;
	private final SelectionModel<Taxon> taxonSelectionModel;
	private final SelectionModel<Integer> splitSelectionModel;

	private final Map<Taxon, Pair<Shape, RichTextLabel>> taxonShapeLabelMap = new HashMap<>();
	private final EventHandler<MouseEvent> mousePressedHandler;
	private final EventHandler<MouseEvent> mouseDraggedHandler;

	private final Map<Integer, ArrayList<Shape>> splitShapeMap;

	private final InvalidationListener taxonSelectionInvalidationListener;
	private final InvalidationListener splitSelectionInvalidationListener;


	private static double mouseDownX;
	private static double mouseDownY;

	public InteractionSetup(TaxaBlock taxaBlock, SplitsBlock splitsBlock, SelectionModel<Taxon> taxonSelectionModel, SelectionModel<Integer> splitSelectionModel,
							ObservableMap<Node, Shape> nodeShapeMap, ObservableMap<Integer, ArrayList<Shape>> splitShapeMap) {
		this.taxaBlock = taxaBlock;
		this.splitsBlock = splitsBlock;
		this.taxonSelectionModel = taxonSelectionModel;
		this.splitSelectionModel = splitSelectionModel;

		this.splitShapeMap = splitShapeMap;

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
						label.setLayoutX(label.getLayoutX() + dx);
						label.setLayoutY(label.getLayoutY() + dy);
					}
				}
				mouseDownX = e.getScreenX();
				mouseDownY = e.getScreenY();
			}
		};

		taxonSelectionInvalidationListener = e -> {
			for (var t : taxonShapeLabelMap.keySet()) {
				var selected = taxonSelectionModel.isSelected(t);
				var shapeLabel = taxonShapeLabelMap.get(t);
				shapeLabel.getFirst().setEffect(selected ? SelectionEffectBlue.getInstance() : null);
				shapeLabel.getSecond().setEffect(selected ? SelectionEffectBlue.getInstance() : null);
			}
		};
		taxonSelectionModel.getSelectedItems().addListener(new WeakInvalidationListener(taxonSelectionInvalidationListener));

		splitSelectionInvalidationListener = e -> {
			for (var s : this.splitShapeMap.keySet()) {
				var selected = splitSelectionModel.isSelected(s);
				for (var shape : this.splitShapeMap.get(s)) {
					shape.setEffect(selected ? SelectionEffectBlue.getInstance() : null);
				}
			}
		};
		splitSelectionModel.getSelectedItems().addListener(new WeakInvalidationListener(splitSelectionInvalidationListener));
	}

	private boolean nodeShapeOrLabelEntered = false;  // this prevents flickering

	public TriConsumer<Node, Shape, RichTextLabel> createNodeCallback() {
        return (v, shape, label) -> Platform.runLater(() -> {
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
                        shape.setOnMouseClicked(mouseClickedHandler);
                        label.setOnMouseClicked(mouseClickedHandler);

                        if (taxonSelectionModel.isSelected(taxon)) {
                            shape.setEffect(SelectionEffectBlue.getInstance());
                            label.setEffect(SelectionEffectBlue.getInstance());
                        }
                    }
                }
            }

        });
	}

	private boolean edgeShapeEntered = false;

	public BiConsumer<Edge, Shape> createEdgeCallback() {
        return (edge, shape) -> Platform.runLater(() -> {
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

            if (edge.getOwner() instanceof PhyloSplitsGraph graph) {
                var split = graph.getSplit(edge);

                shape.setPickOnBounds(false);

                shape.setOnMouseClicked(e -> {
                    if (!e.isShiftDown()) {
                        taxonSelectionModel.clearSelection();
                        splitSelectionModel.clearSelection();
                    }
                    if (e.getClickCount() == 1) {
                        if (split >= 1 && split <= splitsBlock.getNsplits()) {
                            splitSelectionModel.select(split);
                            var partA = splitsBlock.get(split).getA();
                            var partB = splitsBlock.get(split).getB();
                            var whichPart = ((partA.cardinality() < partB.cardinality()) == !e.isAltDown() ? partA : partB);
                            var taxa = BitSetUtils.asStream(whichPart).map(taxaBlock::get).collect(Collectors.toList());
                            taxonSelectionModel.selectAll(taxa);
                        }
                    } else if (e.getClickCount() == 2) {
                        var selectedTaxonIds = BitSetUtils.asBitSet(taxonSelectionModel.getSelectedItems().stream().map(taxaBlock::indexOf).collect(Collectors.toList()));
                        var start = graph.nodeStream().filter(v -> BitSetUtils.intersection(selectedTaxonIds, BitSetUtils.asBitSet(graph.getTaxa(v))).cardinality() > 0).findAny().orElse(null);
                        if (start != null) {
                            try (var visited = graph.newNodeSet()) {
                                GraphTraversals.traverseReachable(start, f -> graph.getSplit(f) != split, visited::add);
                                for (var f : graph.edges()) {
                                    if (visited.contains(f.getSource()) && visited.contains(f.getTarget()))
                                        splitSelectionModel.select(graph.getSplit(f));
                                }
                            }
                        }
                    }
                    e.consume();
                });
            }
        });
	}
}
