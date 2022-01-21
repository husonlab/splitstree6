/*
 *  TreePane.java Copyright (C) 2022 Daniel H. Huson
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
import javafx.beans.InvalidationListener;
import javafx.beans.property.*;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.WeakChangeListener;
import javafx.scene.Group;
import javafx.scene.control.Label;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import jloda.fx.selection.SelectionModel;
import jloda.fx.util.AService;
import jloda.fx.util.ProgramExecutorService;
import jloda.phylo.PhyloTree;
import jloda.phylo.algorithms.RootedNetworkProperties;
import jloda.util.Pair;
import splitstree6.data.TaxaBlock;
import splitstree6.data.parts.Taxon;
import splitstree6.view.trees.layout.ComputeHeightAndAngles;
import splitstree6.view.trees.layout.ComputeTreeLayout;
import splitstree6.view.trees.layout.LayoutUtils;
import splitstree6.view.trees.layout.TreeDiagramType;

import java.util.function.Consumer;

/**
 * display an individual phylogenetic tree
 * Daniel Huson, 11.2021
 */
public class TreePane extends StackPane {

	private Runnable runAfterUpdate;

	private Pane pane;

	private final ChangeListener<Number> zoomChangedListener;
	private final ChangeListener<Number> fontScaleChangeListener;
	private Consumer<LayoutOrientation> orientationConsumer;

	private final StringProperty infoString = new SimpleStringProperty("");

	private final AService<Pair<Group, Consumer<LayoutOrientation>>> service;

	/**
	 * single tree pane
	 */
	public TreePane(TaxaBlock taxaBlock, PhyloTree phyloTree, String name, SelectionModel<Taxon> taxonSelectionModel, double boxWidth, double boxHeight,
					TreeDiagramType diagram, ComputeHeightAndAngles.Averaging averaging, ObjectProperty<LayoutOrientation> orientation, ReadOnlyDoubleProperty zoomFactor, ReadOnlyDoubleProperty labelScaleFactor,
					ReadOnlyObjectProperty<TreePagesView.TreeLabels> showTreeLabels) {

		var interactionSetup = new InteractionSetup(taxaBlock, taxonSelectionModel, orientation);
		// setStyle("-fx-border-color: lightgray;");

		setStyle("-fx-background-color: transparent");

		setPrefWidth(boxWidth);
		setPrefHeight(boxHeight);
		setMinWidth(Pane.USE_PREF_SIZE);
		setMinHeight(Pane.USE_PREF_SIZE);
		setMaxWidth(Pane.USE_PREF_SIZE);
		setMaxHeight(Pane.USE_PREF_SIZE);

		fontScaleChangeListener = (v, o, n) -> LayoutUtils.applyLabelScaleFactor(this, n.doubleValue() / o.doubleValue());
		labelScaleFactor.addListener(new WeakChangeListener<>(fontScaleChangeListener));

		zoomChangedListener = (v, o, n) -> {
			if (pane != null) {
				pane.setScaleX(pane.getScaleX() / o.doubleValue() * n.doubleValue());
				pane.setScaleY(pane.getScaleY() / o.doubleValue() * n.doubleValue());
			}
		};
		zoomFactor.addListener(new WeakChangeListener<>(zoomChangedListener));


		// compute the tree in a separate thread:
		service = new AService<>();
		service.setExecutor(ProgramExecutorService.getInstance());

		orientation.addListener((v, o, n) -> {
			if (diagram == TreeDiagramType.RadialPhylogram)
				splitstree6.view.splits.layout.LayoutUtils.applyOrientation(pane, o, n, orientationConsumer);
			else
				LayoutUtils.applyOrientation(pane, n, o, false);
		});

		service.setCallable(() -> {
			double width;
			double height;
			if (orientation.get().isWidthHeightSwitched()) {
				height = getPrefWidth();
				width = getPrefHeight() - 12;
			} else {
				width = getPrefWidth();
				height = getPrefHeight() - 12;
			}

			var info = RootedNetworkProperties.computeInfoString(phyloTree);

			Platform.runLater(() -> infoString.set(info));

			var pair = ComputeTreeLayout.apply(taxaBlock, phyloTree, diagram, averaging, width - 4, height - 4, interactionSetup.createNodeCallback(), interactionSetup.createEdgeCallback(), false, true);
			pair.getFirst().setId("treeGroup");
			return pair;
		});

		service.setOnSucceeded(a -> {
			var pair = service.getValue();
			var group = pair.getFirst();
			orientationConsumer = pair.getSecond();

			pane = new StackPane(group);
			pane.setStyle("-fx-background-color: transparent");
			pane.setId("treeView");
			if (zoomFactor.get() > 0 && zoomFactor.get() != 1) {
				pane.setScaleX(zoomFactor.get());
				pane.setScaleY(zoomFactor.get());
			}

			pane.setMinHeight(getPrefHeight() - 12);
			pane.setMinWidth(getPrefWidth());

			LayoutUtils.applyLabelScaleFactor(group, labelScaleFactor.get());
			Platform.runLater(() -> {
				LayoutUtils.applyOrientation(orientation.get(), pane, false);
				if (orientationConsumer != null)
					orientationConsumer.accept(orientation.get());
			});

			{
				final var treeLabel = new Label(name);
				final InvalidationListener listener = e -> {
					switch (showTreeLabels.get()) {
						case None -> {
							treeLabel.setText("");
							treeLabel.setVisible(false);
						}
						case Name -> {
							treeLabel.setText(phyloTree.getName());
							treeLabel.setVisible(true);
						}
						case Info -> {
							treeLabel.setText(phyloTree.getName() + " : " + getInfoString());
							treeLabel.setVisible(true);
						}
					}
				};
				showTreeLabels.addListener(listener);
				listener.invalidated(null);
				getChildren().setAll(new VBox(treeLabel, pane));
			}

			pane.addEventHandler(MouseEvent.MOUSE_CLICKED, e -> {
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


	public AService<Pair<Group, Consumer<LayoutOrientation>>> getService() {
		return service;
	}

	public Runnable getRunAfterUpdate() {
		return runAfterUpdate;
	}

	public void setRunAfterUpdate(Runnable runAfterUpdate) {
		this.runAfterUpdate = runAfterUpdate;
	}

	public String getInfoString() {
		return infoString.get();
	}

	public StringProperty infoStringProperty() {
		return infoString;
	}
}
