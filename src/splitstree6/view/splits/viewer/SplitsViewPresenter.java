/*
 *  SplitsViewPresenter.java Copyright (C) 2021 Daniel H. Huson
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
import javafx.beans.InvalidationListener;
import javafx.beans.WeakInvalidationListener;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableSet;
import javafx.geometry.Bounds;
import javafx.geometry.Dimension2D;
import javafx.scene.control.SelectionMode;
import jloda.fx.find.FindToolBar;
import jloda.fx.find.Searcher;
import jloda.fx.util.ProgramExecutorService;
import splitstree6.data.SplitsBlock;
import splitstree6.data.parts.Compatibility;
import splitstree6.data.parts.Taxon;
import splitstree6.tabs.IDisplayTabPresenter;
import splitstree6.view.trees.treepages.LayoutOrientation;
import splitstree6.window.MainWindow;

import java.util.List;
import java.util.function.Function;

/**
 * splits network presenter
 */
public class SplitsViewPresenter implements IDisplayTabPresenter {
	private final MainWindow mainWindow;
	private final SplitsView splitsView;
	private final SplitsViewController controller;

	private final FindToolBar findToolBar;

	private final InvalidationListener selectionListener;

	private final ObjectProperty<SplitNetworkPane> splitNetworkPane = new SimpleObjectProperty<>();
	private final InvalidationListener updateListener;

	public SplitsViewPresenter(MainWindow mainWindow, SplitsView splitsView, ObjectProperty<Bounds> targetBounds, ObjectProperty<SplitsBlock> splitsBlock) {
		this.mainWindow = mainWindow;
		this.splitsView = splitsView;
		this.controller = splitsView.getController();

		splitNetworkPane.addListener((v, o, n) -> {
			controller.getScrollPane().setContent(n);
		});

		controller.getScrollPane().setLockAspectRatio(true);
		controller.getScrollPane().setRequireShiftOrControlToZoom(true);
		controller.getScrollPane().setUpdateScaleMethod(() -> splitsView.setOptionZoomFactor(controller.getScrollPane().getZoomFactorY() * splitsView.getOptionZoomFactor()));

		if (false) {
			controller.getScrollPane().viewportBoundsProperty().addListener(e -> {
				var scrollPane = controller.getScrollPane();
				var pane = splitNetworkPane.get();
				if (pane != null) {
					var newWidth = scrollPane.getViewportBounds().getWidth();
					var oldWidth = pane.getMinWidth();
					if (Math.abs(oldWidth - newWidth) > 20)
						pane.setMinWidth(newWidth);
					var newHeight = scrollPane.getViewportBounds().getHeight();
					var oldHeight = pane.getMinHeight();
					if (Math.abs(oldHeight - newHeight) > 20)
						pane.setMinHeight(newHeight);
				}
			});
		}

		final ObservableSet<SplitsDiagramType> disabledDiagramTypes = FXCollections.observableSet();

		disabledDiagramTypes.add(SplitsDiagramType.Outline);

		splitsBlock.addListener((v, o, n) -> {
			disabledDiagramTypes.clear();
			if (n == null)
				disabledDiagramTypes.addAll(List.of(SplitsDiagramType.values()));
			else if (n.getCompatibility() != Compatibility.compatible && n.getCompatibility() != Compatibility.cyclic) {
				disabledDiagramTypes.add(SplitsDiagramType.Outline);
				if (splitsView.getOptionDiagram() == SplitsDiagramType.Outline)
					Platform.runLater(() -> splitsView.setOptionDiagram(SplitsDiagramType.Splits));
			}
		});

		controller.getDiagramCBox().setButtonCell(ComboBoxUtils.createButtonCell(disabledDiagramTypes, null));
		controller.getDiagramCBox().setCellFactory(ComboBoxUtils.createCellFactory(disabledDiagramTypes, null));
		controller.getDiagramCBox().getItems().addAll(SplitsDiagramType.values());
		controller.getDiagramCBox().valueProperty().bindBidirectional(splitsView.optionDiagramProperty());

		final ObservableSet<SplitsRooting> disabledRootings = FXCollections.observableSet();

		selectionListener = e -> {
			if (mainWindow.getTaxonSelectionModel().getSelectedItems().size() == 0) {
				disabledRootings.add(SplitsRooting.OutGroup);
				disabledRootings.add(SplitsRooting.OutGroupAlt);
			} else
				disabledRootings.clear();
		};
		if (mainWindow.getTaxonSelectionModel().getSelectedItems().size() == 0) {
			disabledRootings.add(SplitsRooting.OutGroup);
			disabledRootings.add(SplitsRooting.OutGroupAlt);
		}
		mainWindow.getTaxonSelectionModel().getSelectedItems().addListener(new WeakInvalidationListener(selectionListener));

		controller.getRootingCBox().setButtonCell(ComboBoxUtils.createButtonCell(disabledRootings, null));
		controller.getRootingCBox().setCellFactory(ComboBoxUtils.createCellFactory(disabledRootings, null));
		controller.getRootingCBox().getItems().addAll(SplitsRooting.values());
		controller.getRootingCBox().valueProperty().bindBidirectional(splitsView.optionRootingProperty());

		controller.getOrientationCBox().setButtonCell(ComboBoxUtils.createButtonCell(null, LayoutOrientation::createNode));
		controller.getOrientationCBox().setCellFactory(ComboBoxUtils.createCellFactory(null, LayoutOrientation::createNode));
		controller.getOrientationCBox().getItems().addAll(LayoutOrientation.values());
		controller.getOrientationCBox().valueProperty().bindBidirectional(splitsView.optionOrientationProperty());

		controller.getUseWeightsToggleButton().selectedProperty().bindBidirectional(splitsView.optionUseWeightsProperty());
		controller.getScaleBar().visibleProperty().bind(controller.getUseWeightsToggleButton().selectedProperty());
		controller.getScaleBar().factorXProperty().bind(splitsView.optionZoomFactorProperty());

		var boxDimension = new SimpleObjectProperty<Dimension2D>();
		targetBounds.addListener((v, o, n) -> {
			boxDimension.set(new Dimension2D(n.getWidth() - 20, n.getHeight() - 40));
		});

		updateListener = e -> {
			var pane = new SplitNetworkPane(mainWindow, mainWindow.getWorkflow().getWorkingTaxaBlock(), splitsBlock.get(), mainWindow.getTaxonSelectionModel(),
					splitsView.getSplitSelectionModel(),
					boxDimension.get().getWidth(), boxDimension.get().getHeight(), splitsView.getOptionDiagram(), splitsView.optionOrientationProperty(),
					splitsView.getOptionRooting(), splitsView.isOptionUseWeights(), splitsView.optionZoomFactorProperty(), splitsView.optionFontScaleFactorProperty(),
					controller.getScaleBar().unitLengthXProperty());
			splitNetworkPane.set(pane);
			pane.drawNetwork();
		};

		splitsView.optionFontScaleFactorProperty().addListener(e -> {
			if (splitNetworkPane.get() != null)
				ProgramExecutorService.submit(100, () -> Platform.runLater(() -> splitNetworkPane.get().layoutLabels()));
		});

		splitsBlock.addListener(updateListener);
		splitsView.optionDiagramProperty().addListener(updateListener);
		splitsView.optionOrientationProperty().addListener(updateListener);
		splitsView.optionRootingProperty().addListener(updateListener);
		splitsView.optionUseWeightsProperty().addListener(updateListener);

		controller.getPrintButton().setOnAction(mainWindow.getController().getPrintMenuItem().getOnAction());
		controller.getPrintButton().disableProperty().bind(mainWindow.getController().getPrintMenuItem().disableProperty());

		controller.getZoomInButton().setOnAction(e -> splitsView.setOptionZoomFactor(1.1 * splitsView.getOptionZoomFactor()));
		controller.getZoomInButton().disableProperty().bind(splitsView.emptyProperty().or(splitsView.optionZoomFactorProperty().greaterThan(8.0 / 1.1)));
		controller.getZoomOutButton().setOnAction(e -> splitsView.setOptionZoomFactor((1.0 / 1.1) * splitsView.getOptionZoomFactor()));
		controller.getZoomOutButton().disableProperty().bind(splitsView.emptyProperty());

		Function<Integer, Taxon> t2taxon = t -> mainWindow.getActiveTaxa().get(t);

		findToolBar = new FindToolBar(mainWindow.getStage(), new Searcher<>(mainWindow.getActiveTaxa(),
				t -> mainWindow.getTaxonSelectionModel().isSelected(t2taxon.apply(t)),
				(t, s) -> mainWindow.getTaxonSelectionModel().setSelected(t2taxon.apply(t), s),
				new SimpleObjectProperty<>(SelectionMode.MULTIPLE),
				t -> t2taxon.apply(t).getNameAndDisplayLabel("===="),
				label -> label.replaceAll(".*====", ""),
				null));
		findToolBar.setShowFindToolBar(false);
		controller.getvBox().getChildren().add(findToolBar);
		controller.getFindButton().setOnAction(e -> findToolBar.setShowFindToolBar(!findToolBar.isShowFindToolBar()));
	}

	public void setupMenuItems() {
		var mainController = mainWindow.getController();

		mainController.getCutMenuItem().disableProperty().bind(new SimpleBooleanProperty(true));
		mainController.getPasteMenuItem().disableProperty().bind(new SimpleBooleanProperty(true));

		mainWindow.getController().getIncreaseFontSizeMenuItem().setOnAction(e -> splitsView.setOptionFontScaleFactor(1.2 * splitsView.getOptionFontScaleFactor()));
		mainWindow.getController().getIncreaseFontSizeMenuItem().disableProperty().bind(splitsView.emptyProperty());
		mainWindow.getController().getDecreaseFontSizeMenuItem().setOnAction(e -> splitsView.setOptionFontScaleFactor((1.0 / 1.2) * splitsView.getOptionFontScaleFactor()));
		mainWindow.getController().getDecreaseFontSizeMenuItem().disableProperty().bind(splitsView.emptyProperty());

		mainController.getZoomInMenuItem().setOnAction(controller.getZoomInButton().getOnAction());
		mainController.getZoomInMenuItem().disableProperty().bind(controller.getZoomOutButton().disableProperty());

		mainController.getZoomOutMenuItem().setOnAction(controller.getZoomOutButton().getOnAction());
		mainController.getZoomOutMenuItem().disableProperty().bind(controller.getZoomOutButton().disableProperty());

		mainController.getFindMenuItem().setOnAction(controller.getFindButton().getOnAction());
		mainController.getFindAgainMenuItem().setOnAction(e -> findToolBar.findAgain());
		mainController.getFindAgainMenuItem().disableProperty().bind(findToolBar.canFindAgainProperty().not());

		mainController.getSelectAllMenuItem().setOnAction(e -> mainWindow.getTaxonSelectionModel().selectAll(mainWindow.getWorkflow().getWorkingTaxaBlock().getTaxa()));
		mainController.getSelectNoneMenuItem().setOnAction(e -> mainWindow.getTaxonSelectionModel().clearSelection());
		mainController.getSelectNoneMenuItem().disableProperty().bind(mainWindow.getTaxonSelectionModel().sizeProperty().isEqualTo(0));
	}
}
