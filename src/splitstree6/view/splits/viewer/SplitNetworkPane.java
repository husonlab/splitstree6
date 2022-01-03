/*
 *  SplitNetworkPane.java Copyright (C) 2021 Daniel H. Huson
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

package splitstree6.view.splits.viewer;

import javafx.animation.ParallelTransition;
import javafx.animation.Transition;
import javafx.animation.TranslateTransition;
import javafx.application.Platform;
import javafx.beans.InvalidationListener;
import javafx.beans.WeakInvalidationListener;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ReadOnlyDoubleProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.WeakChangeListener;
import javafx.geometry.Point2D;
import javafx.scene.Group;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Shape;
import javafx.util.Duration;
import jloda.fx.selection.SelectionModel;
import jloda.fx.util.AService;
import jloda.fx.util.BasicFX;
import jloda.fx.util.GeometryUtilsFX;
import jloda.fx.util.ProgramExecutorService;
import jloda.fx.window.MainWindowManager;
import splitstree6.data.SplitsBlock;
import splitstree6.data.TaxaBlock;
import splitstree6.data.parts.Taxon;
import splitstree6.view.splits.layout.SplitNetworkLayout;
import splitstree6.view.trees.layout.LayoutUtils;
import splitstree6.view.trees.treepages.LayoutOrientation;
import splitstree6.window.MainWindow;

import java.util.ArrayList;

public class SplitNetworkPane extends StackPane {

	private final Group group = new Group();
	private final ChangeListener<Number> zoomChangedListener;
	private final ChangeListener<Number> fontScaleChangeListener;
	private final ChangeListener<LayoutOrientation> orientChangeListener;
	private final InvalidationListener redrawListener;

	private final AService<Group> service;
	private final SplitNetworkLayout splitNetworkLayout = new SplitNetworkLayout();
	private Runnable runAfterUpdate;

	/**
	 * single tree pane
	 */
	public SplitNetworkPane(MainWindow mainWindow, TaxaBlock taxaBlock, SplitsBlock splitsBlock, SelectionModel<Taxon> taxonSelectionModel,
							SelectionModel<Integer> splitSelectionModel,
							double boxWidth, double boxHeight,
							SplitsDiagramType diagram, ReadOnlyObjectProperty<LayoutOrientation> orientation, SplitsRooting rooting,
							double rootAngle,
							boolean useWeights, ReadOnlyDoubleProperty zoomFactor, ReadOnlyDoubleProperty labelScaleFactor,
							DoubleProperty unitLength) {
		getStyleClass().add("background");
		getChildren().setAll(group);

		setPrefWidth(boxWidth);
		setPrefHeight(boxHeight);
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

		orientChangeListener = (v, o, n) -> applyOrientation(o, n);
		orientation.addListener(new WeakChangeListener<>(orientChangeListener));

		redrawListener = e -> drawNetwork();
		MainWindowManager.useDarkThemeProperty().addListener(new WeakInvalidationListener(redrawListener));


		// compute the tree in a separate thread:
		service = new AService<>(mainWindow.getController().getBottomFlowPane());
		service.setExecutor(ProgramExecutorService.getInstance());

		service.setCallable(() -> {
			if (taxaBlock == null || splitsBlock == null)
				return new Group();

			var result = splitNetworkLayout.apply(service.getProgressListener(), taxaBlock, splitsBlock, diagram, rooting,
					rootAngle, useWeights, taxonSelectionModel, unitLength, getPrefWidth() - 4, getPrefHeight() - 16, splitSelectionModel);

			result.setId("networkGroup");
			LayoutUtils.applyLabelScaleFactor(result, labelScaleFactor.get());
			return result;
		});

		service.setOnScheduled(a -> unitLength.set(0));

		service.setOnSucceeded(a -> {
			if (zoomFactor.get() != 1) {
				setScaleX(zoomFactor.get());
				setScaleY(zoomFactor.get());
			}

			setMinHeight(getPrefHeight() - 12);
			setMinWidth(getPrefWidth());
			group.getChildren().setAll(service.getValue());

			Platform.runLater(() -> applyOrientation(orientation.get()));

			addEventHandler(MouseEvent.MOUSE_CLICKED, e -> {
				if (e.isStillSincePress() && !e.isShiftDown()) {
					Platform.runLater(taxonSelectionModel::clearSelection);
				}
				e.consume();
			});
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

	private void applyOrientation(LayoutOrientation orientation) {
		BasicFX.preorderTraversal(getChildren().get(0), n -> {
			if (n instanceof Shape shape) {
				var point = new Point2D(shape.getTranslateX(), shape.getTranslateY());
				if (orientation.flip())
					point = new Point2D(-point.getX(), point.getY());
				if (orientation.angle() != 0)
					point = GeometryUtilsFX.rotate(point, -orientation.angle());
				shape.setTranslateX(point.getX());
				shape.setTranslateY(point.getY());
			}
		});
		Platform.runLater(() -> splitNetworkLayout.getLabelLayout().layoutLabels(orientation));
	}

	public void applyOrientation(LayoutOrientation oldOrientation, LayoutOrientation newOrientation) {
		var transitions = new ArrayList<Transition>();

		BasicFX.preorderTraversal(getChildren().get(0), n -> {
			if (n instanceof Shape shape) {
				var translate = new TranslateTransition(Duration.seconds(1));
				translate.setNode(shape);
				var point = new Point2D(shape.getTranslateX(), shape.getTranslateY());

				if (oldOrientation.angle() != 0)
					point = GeometryUtilsFX.rotate(point, oldOrientation.angle());
				if (oldOrientation.flip())
					point = new Point2D(-point.getX(), point.getY());

				if (newOrientation.flip())
					point = new Point2D(-point.getX(), point.getY());
				if (newOrientation.angle() != 0)
					point = GeometryUtilsFX.rotate(point, -newOrientation.angle());
				translate.setToX(point.getX());
				translate.setToY(point.getY());
				transitions.add(translate);
			}
		});
		var parallel = new ParallelTransition(transitions.toArray(new Transition[0]));
		parallel.setOnFinished(e -> {
			Platform.runLater(() -> splitNetworkLayout.getLabelLayout().layoutLabels(newOrientation));
		});
		parallel.play();
	}
}
