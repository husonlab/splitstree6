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

package splitstree6.view.trees.treepages;

import javafx.application.Platform;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyDoubleProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.WeakChangeListener;
import javafx.scene.Group;
import javafx.scene.Parent;
import javafx.scene.control.Label;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import jloda.fx.control.RichTextLabel;
import jloda.fx.selection.SelectionModel;
import jloda.fx.util.AService;
import jloda.fx.util.ProgramExecutorService;
import jloda.phylo.PhyloTree;
import splitstree6.data.TaxaBlock;
import splitstree6.data.parts.Taxon;
import splitstree6.view.trees.layout.ComputeTreeLayout;
import splitstree6.view.trees.layout.TreeDiagramType;

import java.util.LinkedList;

/**
 * display an individual phylogenetic tree
 * Daniel Huson, 11.2021
 */
public class TreePane extends StackPane {

	private Runnable runAfterUpdate;

	private Pane treePane;

	private final InteractionSetup interactionSetup;

	private final ChangeListener<Number> zoomChangedListener;
	private final ChangeListener<Number> fontScaleChangeListener;

	private final AService<Group> service;

	/**
	 * single tree pane
	 */
	public TreePane(TaxaBlock taxaBlock, PhyloTree phyloTree, String name, int[] taxonOrdering, SelectionModel<Taxon> taxonSelectionModel, double boxWidth, double boxHeight,
					TreeDiagramType diagram, LayoutOrientation orientation, ReadOnlyDoubleProperty zoomFactor, ReadOnlyDoubleProperty labelScaleFactor, ReadOnlyBooleanProperty showTreeName) {

		this.interactionSetup = new InteractionSetup(taxaBlock, phyloTree, taxonSelectionModel, orientation);
		// setStyle("-fx-border-color: lightgray;");

		setStyle("-fx-background-color: transparent");

		setPrefWidth(boxWidth);
		setPrefHeight(boxHeight);
		setMinWidth(Pane.USE_PREF_SIZE);
		setMinHeight(Pane.USE_PREF_SIZE);
		setMaxWidth(Pane.USE_PREF_SIZE);
		setMaxHeight(Pane.USE_PREF_SIZE);

		fontScaleChangeListener = (v, o, n) -> applyLabelScaleFactor(this, n.doubleValue() / o.doubleValue());
		labelScaleFactor.addListener(new WeakChangeListener<>(fontScaleChangeListener));

		zoomChangedListener = (v, o, n) -> {
			if (treePane != null) {
				treePane.setScaleX(treePane.getScaleX() / o.doubleValue() * n.doubleValue());
				treePane.setScaleY(treePane.getScaleY() / o.doubleValue() * n.doubleValue());
			}
		};
		zoomFactor.addListener(new WeakChangeListener<>(zoomChangedListener));

		// compute the tree in a separate thread:
		service = new AService<>();
		service.setExecutor(ProgramExecutorService.getInstance());

		service.setCallable(() -> {
			double width;
			double height;
			if (orientation.isWidthHeightSwitched()) {
				height = getPrefWidth();
				width = getPrefHeight() - 12;
			} else {
				width = getPrefWidth();
				height = getPrefHeight() - 12;
			}

			var group = ComputeTreeLayout.apply(taxaBlock, phyloTree, taxonOrdering, diagram, width - 4, height - 4, interactionSetup.createNodeCallback(), interactionSetup.createEdgeCallback(), false, true);
			group.setId("treeGroup");
			applyLabelScaleFactor(group, labelScaleFactor.get());

			switch (orientation) {
				case Rotate90Deg -> {
					group.setRotate(-90);
				}
				case Rotate180Deg -> {
					group.setRotate(180);
				}
				case Rotate270Deg -> {
					group.setRotate(90);
				}
				case FlipRotate0Deg -> {
					group.setScaleX(-1);
				}
				case FlipRotate90Deg -> {
					group.setScaleX(-1);
					group.setRotate(-90);
				}
				case FlipRotate180Deg -> {
					group.setScaleX(-1);
					group.setRotate(180);
				}
				case FlipRotate270Deg -> {
					group.setScaleX(-1);
					group.setRotate(90);
				}
			}
			return group;
		});

		service.setOnSucceeded(a -> {
			treePane = new StackPane();
			treePane.setId("treeView");
			if (zoomFactor.get() != 1) {
				treePane.setScaleX(zoomFactor.get());
				treePane.setScaleY(zoomFactor.get());
			}

			treePane.setMinHeight(getPrefHeight() - 12);
			treePane.setMinWidth(getPrefWidth());

			treePane.getChildren().setAll(service.getValue());
			var label = new Label(name);
			label.visibleProperty().bind(showTreeName);
			getChildren().setAll(new VBox(label, treePane));

			treePane.addEventHandler(MouseEvent.MOUSE_CLICKED, e -> {
				if (e.isStillSincePress() && !e.isShiftDown()) {
					Platform.runLater(taxonSelectionModel::clearSelection);
				}
				e.consume();
			});
			if (getRunAfterUpdate() != null) {
				Platform.runLater(() -> getRunAfterUpdate().run());
			}
		});

		service.setOnFailed(a -> System.err.println("Draw tree failed: " + service.getException()));
	}

	public void drawTree() {
		service.restart();
	}

	private static void applyLabelScaleFactor(Parent root, double factor) {
		if (factor > 0 && factor != 1) {
			var queue = new LinkedList<>(root.getChildrenUnmodifiable());
			while (queue.size() > 0) {
				var node = queue.pop();
				if (node instanceof RichTextLabel richTextLabel) {
					richTextLabel.setScale(factor * richTextLabel.getScale());
				} else if (node instanceof Parent parent)
					queue.addAll(parent.getChildrenUnmodifiable());
			}
		}
	}

	public AService<Group> getService() {
		return service;
	}


	public Runnable getRunAfterUpdate() {
		return runAfterUpdate;
	}

	public void setRunAfterUpdate(Runnable runAfterUpdate) {
		this.runAfterUpdate = runAfterUpdate;
	}
}
