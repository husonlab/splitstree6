/*
 * TreePane.java Copyright (C) 2022 Daniel H. Huson
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

package splitstree6.view.trees.treepages;

import javafx.application.Platform;
import javafx.beans.InvalidationListener;
import javafx.beans.property.*;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.WeakChangeListener;
import javafx.collections.ObservableMap;
import javafx.geometry.Insets;
import javafx.scene.Group;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import jloda.fx.control.CopyableLabel;
import jloda.fx.selection.SelectionModel;
import jloda.fx.util.AService;
import jloda.fx.util.BasicFX;
import jloda.fx.util.ProgramExecutorService;
import jloda.phylo.PhyloTree;
import jloda.phylo.algorithms.RootedNetworkProperties;
import splitstree6.data.TaxaBlock;
import splitstree6.data.parts.Taxon;
import splitstree6.layout.tree.*;
import splitstree6.view.trees.InteractionSetup;

import java.util.function.Consumer;

/**
 * display an individual phylogenetic tree
 * Daniel Huson, 11.2021
 */
public class TreePane extends StackPane {

	private Runnable runAfterUpdate;

	private Pane pane;

	private final ChangeListener<Number> fontScaleChangeListener;

	private final BooleanProperty showInternalLabels = new SimpleBooleanProperty();
	private final ChangeListener<Boolean> internalLabelsListener;

	private Consumer<LayoutOrientation> orientationConsumer;

	private final StringProperty infoString = new SimpleStringProperty("");

	private final AService<ComputeTreeLayout.Result> service;

	private ComputeTreeLayout.Result result;

	/**
	 * single tree pane
	 */
	public TreePane(Stage stage, TaxaBlock taxaBlock, PhyloTree phyloTree, SelectionModel<Taxon> taxonSelectionModel, double boxWidth, double boxHeight,
					TreeDiagramType diagram, HeightAndAngles.Averaging averaging, ObjectProperty<LayoutOrientation> orientation, ReadOnlyDoubleProperty fontScaleFactor,
					ReadOnlyObjectProperty<TreeLabel> showTreeLabels, ReadOnlyBooleanProperty showInternalLabels, DoubleProperty unitLengthX, ObservableMap<jloda.graph.Node, Group> nodeShapeMap) {

		var interactionSetup = new InteractionSetup(stage, taxaBlock, taxonSelectionModel, diagram, orientation);

		setPrefWidth(boxWidth);
		setPrefHeight(boxHeight);
		setMinWidth(Pane.USE_PREF_SIZE);
		setMinHeight(Pane.USE_PREF_SIZE);
		setMaxWidth(Pane.USE_PREF_SIZE);
		setMaxHeight(Pane.USE_PREF_SIZE);

		fontScaleChangeListener = (v, o, n) -> {
			if (result != null) {
				LayoutUtils.applyLabelScaleFactor(result.taxonLabels(), n.doubleValue() / o.doubleValue());
				updateLabelLayout(orientation.get());
			}
		};
		fontScaleFactor.addListener(new WeakChangeListener<>(fontScaleChangeListener));

		this.showInternalLabels.set(showInternalLabels.get());
		internalLabelsListener = (v, o, n) -> {
			this.showInternalLabels.set(n);
		};
		showInternalLabels.addListener(new WeakChangeListener<>(internalLabelsListener));

		// compute the tree in a separate thread:
		service = new AService<>();
		service.setExecutor(ProgramExecutorService.getInstance());

		orientation.addListener((v, o, n) -> {
			if (diagram == TreeDiagramType.RadialPhylogram) {
				var shapes = BasicFX.getAllRecursively(pane, a -> "graph-node".equals(a.getId()));
				splitstree6.layout.splits.LayoutUtils.applyOrientation(shapes, o, n, orientationConsumer);
			} else
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

			return ComputeTreeLayout.apply(phyloTree, taxaBlock.getNtax(), t -> taxaBlock.get(t).displayLabelProperty(), diagram, averaging, width - 4, height - 4,
					interactionSetup.createNodeCallback(), interactionSetup.createEdgeCallback(), false, true, nodeShapeMap);
		});

		service.setOnSucceeded(a -> {
			result = service.getValue();
			var group = result.getAllAsGroup();

			if (result.internalLabels() != null)
				result.internalLabels().visibleProperty().bind(this.showInternalLabels);

			if (unitLengthX != null)
				unitLengthX.set(result.unitLengthX());

			orientationConsumer = result.layoutOrientationConsumer();

			pane = new StackPane(group);
			pane.setId("treeView");

			pane.setMinHeight(getPrefHeight() - 12);
			pane.setMinWidth(getPrefWidth());

			LayoutUtils.applyLabelScaleFactor(group, fontScaleFactor.get());
			Platform.runLater(() -> {
				if (diagram == TreeDiagramType.RadialPhylogram && orientation.get() != LayoutOrientation.Rotate0Deg) {
					var shapes = BasicFX.getAllRecursively(pane, Group.class);
					splitstree6.layout.splits.LayoutUtils.applyOrientation(shapes, LayoutOrientation.Rotate0Deg, orientation.get(), orientationConsumer);
				} else {
					LayoutUtils.applyOrientation(orientation.get(), pane, false);
					updateLabelLayout(orientation.get());
				}
			});

			if (showTreeLabels != null) {
				final var treeLabel = new CopyableLabel();
				treeLabel.setPadding(new Insets(0, 0, 0, 5));
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
						case Details -> {
							treeLabel.setText(phyloTree.getName() + " : " + getInfoString());
							treeLabel.setVisible(true);
						}
					}
				};
				showTreeLabels.addListener(listener);
				listener.invalidated(null);
				getChildren().setAll(new VBox(treeLabel, pane));
			} else
				getChildren().setAll(pane);

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


	public AService<ComputeTreeLayout.Result> getService() {
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

	public void updateLabelLayout(LayoutOrientation orientation) {
		if (orientationConsumer != null)
			ProgramExecutorService.submit(100, () -> Platform.runLater(() -> orientationConsumer.accept(orientation)));
	}
}
