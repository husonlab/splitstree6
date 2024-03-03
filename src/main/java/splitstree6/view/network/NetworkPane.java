/*
 *  NetworkPane.java Copyright (C) 2024 Daniel H. Huson
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

package splitstree6.view.network;

import javafx.application.Platform;
import javafx.beans.InvalidationListener;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ReadOnlyDoubleProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.WeakChangeListener;
import javafx.collections.ObservableMap;
import javafx.geometry.Point2D;
import javafx.scene.Group;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import jloda.fx.control.RichTextLabel;
import jloda.fx.selection.SelectionModel;
import jloda.fx.util.AService;
import jloda.fx.util.BasicFX;
import jloda.fx.util.GeometryUtilsFX;
import jloda.fx.util.RunAfterAWhile;
import jloda.graph.Edge;
import jloda.graph.Node;
import jloda.util.ProgramExecutorService;
import splitstree6.data.NetworkBlock;
import splitstree6.data.TaxaBlock;
import splitstree6.data.parts.Taxon;
import splitstree6.layout.network.DiagramType;
import splitstree6.layout.network.NetworkLayout;
import splitstree6.layout.tree.LabeledEdgeShape;
import splitstree6.layout.tree.LabeledNodeShape;
import splitstree6.layout.tree.LayoutOrientation;
import splitstree6.layout.tree.LayoutUtils;
import splitstree6.window.MainWindow;

/**
 * network pane
 * Daniel Huson, 4.2022
 */
public class NetworkPane extends StackPane {
	private final Group group = new Group();
	private final ChangeListener<Number> zoomChangedListener;
	private final ChangeListener<Number> fontScaleChangeListener;
	private final ChangeListener<LayoutOrientation> orientChangeListener;
	private final InvalidationListener layoutLabelsListener;

	private final BooleanProperty changingOrientation = new SimpleBooleanProperty(this, "changingOrientation", false);

	private final AService<Group> service;
	private final NetworkLayout networkLayout = new NetworkLayout();
	private Runnable runAfterUpdate;

	/**
	 * network pane
	 */
	public NetworkPane(MainWindow mainWindow, ReadOnlyObjectProperty<TaxaBlock> taxaBlock, ReadOnlyObjectProperty<NetworkBlock> networkBlock, SelectionModel<Taxon> taxonSelectionModel,
					   ReadOnlyDoubleProperty boxWidth, ReadOnlyDoubleProperty boxHeight,
					   ReadOnlyObjectProperty<DiagramType> diagram, ReadOnlyObjectProperty<LayoutOrientation> orientation, ReadOnlyDoubleProperty zoomFactor, ReadOnlyDoubleProperty labelScaleFactor,
					   ObservableMap<Integer, RichTextLabel> taxonLabelMap, ObservableMap<Node, LabeledNodeShape> nodeShapeMap, ObservableMap<Edge, LabeledEdgeShape> edgeShapeMap) {
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

		zoomChangedListener = (v, o, n) -> {
			setScaleX(getScaleX() / o.doubleValue() * n.doubleValue());
			setScaleY(getScaleY() / o.doubleValue() * n.doubleValue());
		};
		zoomFactor.addListener(new WeakChangeListener<>(zoomChangedListener));

		orientChangeListener = (v, o, n) -> splitstree6.layout.LayoutUtils.applyOrientation(nodeShapeMap.values(), o, n,
				or -> networkLayout.getLabelLayout().layoutLabels(or), changingOrientation);
		orientation.addListener(new WeakChangeListener<>(orientChangeListener));

		layoutLabelsListener = e -> layoutLabels(orientation.get());

		// compute the network in a separate thread:
		service = new AService<>(mainWindow.getController().getBottomFlowPane());
		service.setExecutor(ProgramExecutorService.getInstance());

		service.setCallable(() -> {
			if (taxaBlock.get() == null || networkBlock.get() == null)
				return new Group();

			var result = networkLayout.apply(service.getProgressListener(), taxaBlock.get(), networkBlock.get(), diagram.get(),
					getPrefWidth() - 4, getPrefHeight() - 16, taxonLabelMap, nodeShapeMap, edgeShapeMap);

			result.setId("networkGroup");
			LayoutUtils.applyLabelScaleFactor(result, labelScaleFactor.get());

			return result;
		});

		service.setOnSucceeded(a -> {
			if (zoomFactor.get() != 1) {
				setScaleX(zoomFactor.get());
				setScaleY(zoomFactor.get());
			}

			setMinHeight(getPrefHeight() - 12);
			setMinWidth(getPrefWidth());
			group.getChildren().setAll(service.getValue());

			Platform.runLater(() -> applyOrientation(orientation.get()));

			if (getRunAfterUpdate() != null) {
				Platform.runLater(() -> getRunAfterUpdate().run());
			}
		});

		service.setOnFailed(a -> System.err.println("Draw network failed: " + service.getException()));
	}

	public void drawNetwork() {
		RunAfterAWhile.applyInFXThread(this, service::restart);
	}

	public Runnable getRunAfterUpdate() {
		return runAfterUpdate;
	}

	public void setRunAfterUpdate(Runnable runAfterUpdate) {
		this.runAfterUpdate = runAfterUpdate;
	}

	public void layoutLabels(LayoutOrientation orientation) {
		networkLayout.getLabelLayout().layoutLabels(orientation);
	}

	private void applyOrientation(LayoutOrientation orientation) {
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
		ProgramExecutorService.submit(100, () -> Platform.runLater(() -> layoutLabels(orientation)));
	}

	public boolean isChangingOrientation() {
		return changingOrientation.get();
	}

	public BooleanProperty changingOrientationProperty() {
		return changingOrientation;
	}
}
