/*
 *  TreePane.java Copyright (C) 2024 Daniel H. Huson
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
import javafx.collections.ObservableMap;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import jloda.fx.control.CopyableLabel;
import jloda.fx.control.RichTextLabel;
import jloda.fx.selection.SelectionModel;
import jloda.fx.selection.SetSelectionModel;
import jloda.fx.undo.UndoManager;
import jloda.fx.util.AService;
import jloda.fx.util.BasicFX;
import jloda.graph.Edge;
import jloda.phylo.PhyloTree;
import jloda.phylo.algorithms.RootedNetworkProperties;
import jloda.phylogeny.layout.Averaging;
import jloda.util.ProgramExecutorService;
import jloda.util.StringUtils;
import splitstree6.data.TaxaBlock;
import splitstree6.data.parts.Taxon;
import splitstree6.layout.tree.*;
import splitstree6.view.format.edgelabel.LabelEdgesBy;
import splitstree6.view.trees.InteractionSetup;
import splitstree6.window.MainWindow;

import java.util.OptionalDouble;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * display an individual phylogenetic tree
 * Daniel Huson, 11.2021
 */
public class TreePane extends StackPane {

	private Runnable runAfterUpdate;

	private Pane pane;

	private final InteractionSetup interactionSetup;

	private final ChangeListener<Number> fontScaleChangeListener;

	private Consumer<LayoutOrientation> orientationConsumer;

	private final StringProperty infoString = new SimpleStringProperty("");

	private final BooleanProperty changingOrientation = new SimpleBooleanProperty(this, "changingOrientation", false);

	private final AService<ComputeTreeLayout.Result> service;

	private final SelectionModel<Edge> edgeSelectionModel = new SetSelectionModel<>();

	private ComputeTreeLayout.Result result;

	/**
	 * single tree pane
	 */
	public TreePane(MainWindow mainWindow, UndoManager undoManager, TaxaBlock taxaBlock, PhyloTree phyloTree, SelectionModel<Taxon> taxonSelectionModel, double boxWidth, double boxHeight,
					TreeDiagramType diagram, LabelEdgesBy labelEdgesBy, Averaging averaging, StringProperty orientationLabel, ReadOnlyDoubleProperty fontScaleFactor,
					ReadOnlyObjectProperty<PaneLabel> showTreeLabels, DoubleProperty unitLengthX,
					ObservableMap<jloda.graph.Node, LabeledNodeShape> nodeShapeMap, ObservableMap<Edge, LabeledEdgeShape> edgeShapeMap, boolean optimizeReticulationEdges) {
		nodeShapeMap.clear();
		edgeShapeMap.clear();

		setPrefWidth(boxWidth);
		setPrefHeight(boxHeight);
		setMinWidth(Pane.USE_PREF_SIZE);
		setMinHeight(Pane.USE_PREF_SIZE);
		setMaxWidth(Pane.USE_PREF_SIZE);
		setMaxHeight(Pane.USE_PREF_SIZE);

		interactionSetup = new InteractionSetup(mainWindow.getStage(), this, undoManager, taxaBlock, diagram, orientationLabel, taxonSelectionModel, edgeSelectionModel, nodeShapeMap, edgeShapeMap);

		fontScaleChangeListener = (v, o, n) -> {
			if (result != null) {
				LayoutUtils.applyLabelScaleFactor(result.taxonLabels(), n.doubleValue() / o.doubleValue());
				updateLabelLayout(LayoutOrientation.valueOf(orientationLabel.get()));
			}
		};
		fontScaleFactor.addListener(new WeakChangeListener<>(fontScaleChangeListener));

		// compute the tree in a separate thread:
		service = new AService<>();
		service.setProgressParentPane(mainWindow.getController().getBottomFlowPane());
		service.setProgressBarShowStopButton(false);
		service.setExecutor(ProgramExecutorService.getInstance());

		orientationLabel.addListener((v, o, n) -> {
			Predicate<Node> keepLabelUnrotated = label -> {
				if ("edge-label".equals(label.getId()))
					return true;
				return switch (diagram) {
					case RectangularPhylogram, RectangularCladogram, TriangularCladogram -> false;
					case CircularPhylogram, CircularCladogram -> false;
					case RadialPhylogram, RadialCladogram -> true;
				};
			};
			LayoutUtils.applyOrientation(pane, LayoutOrientation.valueOf(n), LayoutOrientation.valueOf(o), keepLabelUnrotated, changingOrientation, true);
		});

		service.setCallable(() -> {
			service.getProgressListener().setTasks("Layout", "");

			edgeSelectionModel.clearSelection();
			double width;
			double height;
			if (LayoutOrientation.valueOf(orientationLabel.get()).isWidthHeightSwitched()) {
				height = getPrefWidth();
				width = getPrefHeight() - 12;
			} else {
				width = getPrefWidth();
				height = getPrefHeight() - 12;
			}

			var info = RootedNetworkProperties.computeInfoString(phyloTree);

			Platform.runLater(() -> infoString.set(info));

			switch (labelEdgesBy) {
				case None -> {
					phyloTree.edgeStream().forEach(e -> phyloTree.setLabel(e, null));
				}
				case Weight -> {
					phyloTree.edgeStream().forEach(e -> phyloTree.setLabel(e, null));
					if (phyloTree.hasEdgeWeights())
						phyloTree.edgeStream().forEach(e -> phyloTree.setLabel(e, phyloTree.getEdgeWeights().containsKey(e) ? StringUtils.removeTrailingZerosAfterDot("%.3f", phyloTree.getWeight(e)) : null));
				}
				case Confidence -> {
					phyloTree.edgeStream().forEach(e -> phyloTree.setLabel(e, null));
					if (phyloTree.hasEdgeConfidences())
						phyloTree.edgeStream().filter(e -> !e.getTarget().isLeaf()).forEach(e -> phyloTree.setLabel(e, phyloTree.getEdgeConfidences().containsKey(e) ? StringUtils.removeTrailingZerosAfterDot("%.3f", phyloTree.getConfidence(e)) : null));
				}
				case ConfidenceX100 -> {
					phyloTree.edgeStream().forEach(e -> phyloTree.setLabel(e, null));
					if (phyloTree.hasEdgeConfidences())
						phyloTree.edgeStream().filter(e -> !e.getTarget().isLeaf()).forEach(e -> phyloTree.setLabel(e, phyloTree.getEdgeConfidences().containsKey(e) ? StringUtils.removeTrailingZerosAfterDot("%.1f", 100d * phyloTree.getConfidence(e)) : null));
				}
				case Probability -> {
					phyloTree.edgeStream().forEach(e -> phyloTree.setLabel(e, null));
					if (phyloTree.hasEdgeProbabilities())
						phyloTree.edgeStream().filter(e -> !e.getTarget().isLeaf()).forEach(e -> phyloTree.setLabel(e, phyloTree.getEdgeProbabilities().containsKey(e) ? StringUtils.removeTrailingZerosAfterDot("%.3f", phyloTree.getProbability(e)) : null));
				}
			}
			return ComputeTreeLayout.apply(phyloTree, taxaBlock.getNtax(), t -> taxaBlock.get(t).displayLabelProperty(), diagram, averaging, width - 4, height - 4, true, nodeShapeMap, edgeShapeMap, optimizeReticulationEdges);
		});

		service.setOnSucceeded(a -> {
			result = service.getValue();
			var group = result.getAllAsGroup();

			interactionSetup.initializeSelection(taxaBlock, taxonSelectionModel, edgeSelectionModel, nodeShapeMap);

			if (unitLengthX != null)
				unitLengthX.set(result.unitLengthX());

			orientationConsumer = result.layoutOrientationConsumer();

			pane = new StackPane(group);
			pane.setId("treeView");

			pane.setMinHeight(getPrefHeight() - 12);
			pane.setMinWidth(getPrefWidth());

			LayoutUtils.applyLabelScaleFactor(group, fontScaleFactor.get());
			if (true) {
				Predicate<Node> keepLabelUnrotated = label -> {
					if ("edge-label".equals(label.getId()))
						return true;
					return switch (diagram) {
						case RectangularPhylogram, RectangularCladogram, TriangularCladogram -> false;
						case CircularPhylogram, CircularCladogram -> false;
						case RadialPhylogram, RadialCladogram -> true;
					};
				};
				LayoutUtils.applyOrientation(pane, LayoutOrientation.valueOf(orientationLabel.get()), LayoutOrientation.valueOf("Rotate0"), keepLabelUnrotated, changingOrientation, false);
			}

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

	public boolean isChangingOrientation() {
		return changingOrientation.get();
	}

	public BooleanProperty changingOrientationProperty() {
		return changingOrientation;
	}

	public void updateLabelLayout(LayoutOrientation orientation) {
		if (orientationConsumer != null)
			ProgramExecutorService.submit(100, () -> Platform.runLater(() -> orientationConsumer.accept(orientation)));
	}

	public SelectionModel<Edge> getEdgeSelectionModel() {
		return edgeSelectionModel;
	}

	public OptionalDouble getMinLabelHeight() {
		var minHeight = Double.MAX_VALUE;
		for (var label : BasicFX.getAllRecursively(result.taxonLabels(), RichTextLabel.class)) {
			var height = label.getLayoutBounds().getHeight();
			if (height > 0)
				minHeight = Math.min(minHeight, height);
		}
		return (minHeight < Double.MAX_VALUE ? OptionalDouble.of(minHeight) : OptionalDouble.empty());
	}
}
