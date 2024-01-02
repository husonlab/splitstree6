/*
 * SplitNetworkPane.java Copyright (C) 2023 Daniel H. Huson
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

package splitstree6.view.splits.viewer;

import javafx.application.Platform;
import javafx.beans.InvalidationListener;
import javafx.beans.property.*;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.WeakChangeListener;
import javafx.collections.ObservableList;
import javafx.collections.ObservableMap;
import javafx.geometry.Point2D;
import javafx.scene.Group;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Shape;
import jloda.fx.control.RichTextLabel;
import jloda.fx.selection.SelectionModel;
import jloda.fx.util.AService;
import jloda.fx.util.BasicFX;
import jloda.fx.util.GeometryUtilsFX;
import jloda.fx.util.ProgramExecutorService;
import jloda.graph.Node;
import splitstree6.data.SplitsBlock;
import splitstree6.data.TaxaBlock;
import splitstree6.data.parts.Taxon;
import splitstree6.layout.splits.LoopView;
import splitstree6.layout.splits.SplitNetworkLayout;
import splitstree6.layout.splits.SplitsDiagramType;
import splitstree6.layout.splits.SplitsRooting;
import splitstree6.layout.tree.LabeledNodeShape;
import splitstree6.layout.tree.LayoutOrientation;
import splitstree6.layout.tree.LayoutUtils;
import splitstree6.window.MainWindow;

import java.util.ArrayList;
import java.util.stream.Collectors;

/**
 * split network pane
 * Daniel Huson, 3.2022
 */
public class SplitNetworkPane extends StackPane {
	private final Group group = new Group();
	private final ChangeListener<Number> fontScaleChangeListener;
	private final ChangeListener<LayoutOrientation> orientChangeListener;
	private final InvalidationListener layoutLabelsListener;
	private final InvalidationListener redrawListener;

	private final BooleanProperty changingOrientation = new SimpleBooleanProperty(this, "changingOrientation", false);

	private final AService<Group> service;
	private final SplitNetworkLayout splitNetworkLayout = new SplitNetworkLayout();
	private Runnable runAfterUpdate;

	/**
	 * split network pane
	 */
	public SplitNetworkPane(MainWindow mainWindow, ReadOnlyObjectProperty<TaxaBlock> taxaBlock, ReadOnlyObjectProperty<SplitsBlock> splitsBlock,
							SelectionModel<Taxon> taxonSelectionModel, SelectionModel<Integer> splitSelectionModel,
							ReadOnlyDoubleProperty boxWidth, ReadOnlyDoubleProperty boxHeight, ReadOnlyObjectProperty<SplitsDiagramType> diagram,
							ReadOnlyObjectProperty<LayoutOrientation> orientation,
							ReadOnlyObjectProperty<SplitsRooting> rooting, ReadOnlyDoubleProperty rootAngle, ReadOnlyDoubleProperty labelScaleFactor,
							ReadOnlyBooleanProperty showConfidence, DoubleProperty unitLength,
							ObservableMap<Integer, RichTextLabel> taxonLabelMap,
							ObservableMap<Node, LabeledNodeShape> nodeLabeledShapeMap,
							ObservableMap<Integer, ArrayList<Shape>> splitShapeMap,
							ObservableList<LoopView> loopViews) {
		getStyleClass().add("viewer-background");
		getChildren().setAll(group);

		prefWidthProperty().bind(boxWidth);
		prefHeightProperty().bind(boxHeight);
		setMinWidth(Pane.USE_PREF_SIZE);
		setMinHeight(Pane.USE_PREF_SIZE);
		setMaxWidth(Pane.USE_PREF_SIZE);
		setMaxHeight(Pane.USE_PREF_SIZE);

		fontScaleChangeListener = (v, o, n) -> LayoutUtils.applyLabelScaleFactor(this, n.doubleValue() / o.doubleValue());
		labelScaleFactor.addListener(new WeakChangeListener<>(fontScaleChangeListener));

		layoutLabelsListener = e -> layoutLabels(orientation.get());

		orientChangeListener = (v, o, n) -> {
			var shapes = nodeLabeledShapeMap.values().stream().filter(LabeledNodeShape::hasShape).collect(Collectors.toList());
			splitstree6.layout.LayoutUtils.applyOrientation(shapes, o, n, or -> splitNetworkLayout.getLabelLayout().layoutLabels(or), changingOrientation);
		};
		orientation.addListener(new WeakChangeListener<>(orientChangeListener));

		redrawListener = e -> drawNetwork();

		// compute the network in a separate thread:
		service = new AService<>(mainWindow.getController().getBottomFlowPane());
		service.setExecutor(ProgramExecutorService.getInstance());

		service.setCallable(() -> {
			if (taxaBlock == null || splitsBlock == null)
				return new Group();

			var result = splitNetworkLayout.apply(service.getProgressListener(), taxaBlock.get(), splitsBlock.get(), diagram.get(),
					rooting.get(), rootAngle.get(), taxonSelectionModel, splitSelectionModel, showConfidence, unitLength, getPrefWidth() - 4, getPrefHeight() - 16,
					taxonLabelMap, nodeLabeledShapeMap, splitShapeMap, loopViews);

			result.setId("networkGroup");
			LayoutUtils.applyLabelScaleFactor(result, labelScaleFactor.get());

			return result;
		});

		service.setOnScheduled(a -> unitLength.set(0));

		service.setOnSucceeded(a -> {
			setMinHeight(getPrefHeight() - 12);
			setMinWidth(getPrefWidth());
			group.getChildren().setAll(service.getValue());

			Platform.runLater(() -> applyOrientation(orientation.get()));

			System.err.printf("Nodes: %,d, Edges: %,d%n", splitNetworkLayout.getGraph().getNumberOfNodes(), splitNetworkLayout.getGraph().getNumberOfEdges());

			if (getRunAfterUpdate() != null) {
				Platform.runLater(() -> getRunAfterUpdate().run());
			}
		});

		service.setOnFailed(a -> System.err.println("Draw network failed: " + service.getException()));
	}

	public void drawNetwork() {
		service.restart();
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

	public void layoutLabels(LayoutOrientation orientation) {
		splitNetworkLayout.getLabelLayout().layoutLabels(orientation);
	}

	public boolean isChangingOrientation() {
		return changingOrientation.get();
	}

	public ReadOnlyBooleanProperty changingOrientationProperty() {
		return changingOrientation;
	}

	private void applyOrientation(LayoutOrientation orientation) {
		if (!isChangingOrientation()) {
			changingOrientation.set(true);
			BasicFX.preorderTraversal(getChildren().get(0), n -> {
				if ("graph-node".equals(n.getId())) {
					var point = new Point2D(n.getTranslateX(), n.getTranslateY());
					if (orientation.flip())
						point = new Point2D(-point.getX(), point.getY());
					if (orientation.angle() != 0)
						point = GeometryUtilsFX.rotate(point, -orientation.angle());
					n.setTranslateX(point.getX());
					n.setTranslateY(point.getY());
				}
			});
			ProgramExecutorService.submit(100, () -> Platform.runLater(() -> {
				layoutLabels(orientation);
				changingOrientation.set(false);
			}));
		}
	}

	public Group getGroup() {
		return group;
	}

	public SplitNetworkLayout getSplitNetworkLayout() {
		return splitNetworkLayout;
	}
}
