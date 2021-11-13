/*
 *  TreePane.java Copyright (C) 2021 Daniel H. Huson
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

import javafx.application.Platform;
import javafx.beans.InvalidationListener;
import javafx.beans.WeakInvalidationListener;
import javafx.beans.property.ReadOnlyDoubleProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.WeakChangeListener;
import javafx.collections.SetChangeListener;
import javafx.collections.WeakSetChangeListener;
import javafx.geometry.Point3D;
import javafx.scene.Parent;
import javafx.scene.control.Label;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import jloda.fx.control.RichTextLabel;
import jloda.fx.selection.SelectionModel;
import jloda.fx.util.AService;
import jloda.fx.util.SelectionEffectBlue;
import jloda.phylo.PhyloTree;
import jloda.util.ProgramProperties;
import splitstree6.data.TaxaBlock;
import splitstree6.data.parts.Taxon;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

public class TreePane extends StackPane {

	public enum RootSide {
		Left, Right, Down, Up;

		public static RootSide getDefault() {
			return valueOf(ProgramProperties.get("DefaultTreeRootSide", Left.name()));
		}

		public static void setDefault(RootSide rootSide) {
			ProgramProperties.put("DefaultTreeRootSide", rootSide.name());
		}
	}

	private final TaxaBlock taxaBlock;
	private final PhyloTree phyloTree;
	private final SelectionModel<Taxon> taxonSelectionModel;
	private final Map<Taxon, TreeEmbedding.ShapeAndLabel> taxonNodesMap = new HashMap<>();
	private final ReadOnlyDoubleProperty fontScaleFactor;

	private final TreeEmbedding.TreeDiagram treeDiagram;
	private final RootSide rootSide;

	private final AService<Boolean> redrawService;

	private final InvalidationListener dimensionsChangeListener;
	private final SetChangeListener<Taxon> selectionChangeListener;
	private final ChangeListener<Number> fontScaleChangeListener;

	/**
	 * single tree pane
	 */
	public TreePane(TaxaBlock taxaBlock, PhyloTree phyloTree, SelectionModel<Taxon> taxonSelectionModel, ReadOnlyDoubleProperty boxWidth, ReadOnlyDoubleProperty boxHeight,
					TreeEmbedding.TreeDiagram diagram, RootSide rootSide, ReadOnlyDoubleProperty fontScaleFactor) {
		this.taxaBlock = taxaBlock;
		this.phyloTree = phyloTree;
		this.taxonSelectionModel = taxonSelectionModel;
		this.treeDiagram = diagram;
		this.rootSide = rootSide;
		this.fontScaleFactor = fontScaleFactor;
		// setStyle("-fx-border-color: lightgray;");

		redrawService = new AService<>();

		getStyleClass().add("background");

		setPrefWidth(boxWidth.get());
		setPrefHeight(boxHeight.get());

		dimensionsChangeListener = e -> {
			setPrefWidth(boxWidth.get());
			setPrefHeight(boxHeight.get());
			if (redrawService.getCallable() != null)
				redrawService.restart();
		};
		boxWidth.addListener(new WeakInvalidationListener(dimensionsChangeListener));
		boxHeight.addListener(new WeakInvalidationListener(dimensionsChangeListener));

		selectionChangeListener = e -> {
			if (e.wasAdded()) {
				var nodes = taxonNodesMap.get(e.getElementAdded());
				if (nodes != null) {
					for (var node : nodes) {
						node.setEffect(SelectionEffectBlue.getInstance());
					}
				}
			} else if (e.wasRemoved()) {
				var nodes = taxonNodesMap.get(e.getElementRemoved());
				if (nodes != null) {
					for (var node : nodes) {
						node.setEffect(null);
					}
				}
			}
		};
		taxonSelectionModel.getSelectedItems().addListener(new WeakSetChangeListener<>(selectionChangeListener));

		fontScaleChangeListener = (v, o, n) -> applyFontScaleFactor(this, n.doubleValue() / o.doubleValue());
		fontScaleFactor.addListener(new WeakChangeListener<>(fontScaleChangeListener));
	}

	public void drawTree() {
		Runnable redraw = () -> {
			var pane = new StackPane();
			double width;
			double height;
			if (rootSide == RootSide.Left || rootSide == RootSide.Right) {
				width = getPrefWidth();
				height = getPrefHeight() - 12;
			} else {
				height = getPrefWidth();
				width = getPrefHeight() - 12;

			}

			var group = TreeEmbedding.apply(taxaBlock, phyloTree, treeDiagram, width - 4, height - 4, taxonNodesMap);

			applyFontScaleFactor(group, fontScaleFactor.get());

			switch (rootSide) {
				case Right -> {
					group.setRotate(180);
				}
				case Up -> {
					group.setRotate(90);
				}
				case Down -> {
					group.setRotate(-90);
				}
			}

			pane.setMinHeight(getPrefHeight() - 12);
			pane.setMinWidth(getPrefWidth());

			pane.getChildren().setAll(group);

			if (rootSide == RootSide.Right || rootSide == RootSide.Up) {
				var queue = new LinkedList<>(group.getChildren());
				while (queue.size() > 0) {
					var node = queue.pop();
					if (node instanceof RichTextLabel) {
						node.setRotationAxis(new Point3D(0, 0, 1));
						node.setRotate(180);
					} else if (node instanceof Parent parent) {
						queue.addAll(parent.getChildrenUnmodifiable());
					}
				}
			}

			var label = new Label(phyloTree.getName());
			getChildren().setAll(new VBox(label, pane));

			setupSelection(taxonSelectionModel, taxonNodesMap);

			pane.addEventHandler(MouseEvent.MOUSE_CLICKED, e -> {
				if (e.isStillSincePress() && !e.isShiftDown()) {
					Platform.runLater(taxonSelectionModel::clearSelection);
				}
				e.consume();
			});

			SetupDragSelectedLabels.apply(taxonSelectionModel, taxonNodesMap);
		};

		redraw.run();

		redrawService.setCallable(() -> {
			Thread.sleep(10);
			redraw.run();
			return true;
		});
	}

	private static void setupSelection(SelectionModel<Taxon> selectionModel, Map<Taxon, TreeEmbedding.ShapeAndLabel> taxonNodesMap) {
		for (var taxon : taxonNodesMap.keySet()) {
			var nodes = taxonNodesMap.get(taxon);
			for (var node : nodes) {
				if (selectionModel.isSelected(taxon)) {
					node.setEffect(SelectionEffectBlue.getInstance());
				}
				node.setOnMouseClicked(e -> {
					if (e.isStillSincePress()) {
						if (!e.isShiftDown())
							selectionModel.clearSelection();
						selectionModel.toggleSelection(taxon);
						e.consume();
					}
				});
			}
		}
	}

	private static void applyFontScaleFactor(Parent root, double factor) {
		if (factor > 0 && factor != 1) {
			var queue = new LinkedList<>(root.getChildrenUnmodifiable());
			while (queue.size() > 0) {
				var node = queue.pop();
				if (node instanceof RichTextLabel richTextLabel) {
					var newSize = factor * richTextLabel.getFont().getSize();
					richTextLabel.setFont(new Font(richTextLabel.getFont().getName(), newSize));
				} else if (node instanceof Parent parent)
					queue.addAll(parent.getChildrenUnmodifiable());
			}
		}
	}
}
