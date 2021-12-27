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

import javafx.application.Platform;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyDoubleProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.WeakChangeListener;
import javafx.scene.Group;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import jloda.fx.selection.SelectionModel;
import jloda.fx.util.AService;
import jloda.fx.util.ProgramExecutorService;
import splitstree6.data.SplitsBlock;
import splitstree6.data.TaxaBlock;
import splitstree6.data.parts.Taxon;
import splitstree6.view.splits.layout.ComputeSplitNetworkLayout;
import splitstree6.view.trees.layout.LayoutUtils;
import splitstree6.view.trees.treepages.LayoutOrientation;
import splitstree6.window.MainWindow;

public class SplitNetworkPane extends StackPane {

	private Runnable runAfterUpdate;

	private final Group group = new Group();

	private final ChangeListener<Number> zoomChangedListener;
	private final ChangeListener<Number> fontScaleChangeListener;

	private final AService<Group> service;

	/**
	 * single tree pane
	 */
	public SplitNetworkPane(MainWindow mainWindow, TaxaBlock taxaBlock, SplitsBlock splitsBlock, SelectionModel<Taxon> taxonSelectionModel,
							SelectionModel<Integer> splitSelectionModel,
							double boxWidth, double boxHeight,
							SplitsDiagramType diagram, ObjectProperty<LayoutOrientation> orientation, SplitsRooting rooting,
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

		// compute the tree in a separate thread:
		service = new AService<>(mainWindow.getController().getBottomFlowPane());
		service.setExecutor(ProgramExecutorService.getInstance());

		service.setCallable(() -> {
			if (taxaBlock == null || splitsBlock == null)
				return new Group();

			double width;
			double height;
			if (orientation.get().isWidthHeightSwitched()) {
				height = getPrefWidth();
				width = getPrefHeight() - 12;
			} else {
				width = getPrefWidth();
				height = getPrefHeight() - 12;
			}

			var result = ComputeSplitNetworkLayout.apply(service.getProgressListener(), taxaBlock, splitsBlock, diagram, rooting, useWeights,
					taxonSelectionModel, unitLength, width - 4, height - 4, splitSelectionModel, orientation);

			result.setId("networkGroup");
			LayoutUtils.applyLabelScaleFactor(result, labelScaleFactor.get());
			LayoutUtils.applOrientation(result, orientation.get());
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


}
