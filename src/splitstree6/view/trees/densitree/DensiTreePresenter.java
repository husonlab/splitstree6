/*
 * DensiTreePresenter.java Copyright (C) 2023 Daniel H. Huson
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

package splitstree6.view.trees.densitree;

import javafx.beans.InvalidationListener;
import javafx.beans.WeakInvalidationListener;
import javafx.beans.binding.Bindings;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableSet;
import javafx.geometry.Bounds;
import javafx.scene.control.RadioMenuItem;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.DataFormat;
import jloda.fx.control.RichTextLabel;
import jloda.fx.find.FindToolBar;
import jloda.fx.util.ResourceManagerFX;
import jloda.fx.window.MainWindowManager;
import jloda.util.StringUtils;
import splitstree6.layout.tree.HeightAndAngles;
import splitstree6.layout.tree.LayoutOrientation;
import splitstree6.tabs.IDisplayTabPresenter;
import splitstree6.view.findreplace.FindReplaceTaxa;
import splitstree6.view.utils.ComboBoxUtils;
import splitstree6.window.MainWindow;

import java.util.ArrayList;

import static splitstree6.layout.tree.LayoutOrientation.FlipRotate180Deg;
import static splitstree6.layout.tree.LayoutOrientation.Rotate0Deg;

public class DensiTreePresenter implements IDisplayTabPresenter {
	private final MainWindow mainWindow;
	private final DensiTreeView view;
	private final DensiTreeViewController controller;
	private final DensiTreeDrawer drawer;

	private final FindToolBar findToolBar;


	public DensiTreePresenter(MainWindow mainWindow, DensiTreeView view, ObjectProperty<Bounds> targetBounds) {
		this.mainWindow = mainWindow;
		this.view = view;
		this.controller = view.getController();
		this.drawer = new DensiTreeDrawer(mainWindow);

		controller.getDiagramToggleGroup().selectedToggleProperty().addListener((v, o, n) -> {
			if (n instanceof RadioMenuItem radioMenuItem) {
				view.setOptionDiagram(DensiTreeDiagramType.valueOf(radioMenuItem.getText()));
				controller.getMenuButton().setGraphic(view.getOptionDiagram().createNode());
			}
		});
		view.optionDiagramProperty().addListener((v, o, n) -> {
			for (var toggle : controller.getDiagramToggleGroup().getToggles()) {
				if (toggle instanceof RadioMenuItem radioMenuItem) {
					if (radioMenuItem.getText().equals(n.name())) {
						controller.getDiagramToggleGroup().selectToggle(toggle);
						return;
					}
				}
			}
		});
		for (var toggle : controller.getDiagramToggleGroup().getToggles()) {
			if (toggle instanceof RadioMenuItem radioMenuItem) {
				if (radioMenuItem.getText().equals(view.getOptionDiagram().name())) {
					controller.getDiagramToggleGroup().selectToggle(toggle);
				}
			}
		}

		view.optionDiagramProperty().addListener(e -> {
			view.setOptionHorizontalZoomFactor(1.0 / 1.2);
			view.setOptionVerticalZoomFactor(1.0 / 1.2);
		});

		view.optionRerootAndRescaleProperty().bindBidirectional(controller.getRerootAndRescaleChekMenuItem().selectedProperty());
		// todo: RerootAndRescaleTrees.java needs to be finished
		controller.getRerootAndRescaleChekMenuItem().setDisable(true);

		controller.getShowTreesMenuItem().selectedProperty().bindBidirectional(view.optionShowTreesProperty());
		controller.getHideFirst10PercentMenuItem().selectedProperty().bindBidirectional(view.optionHideFirst10PercentTreesProperty());
		controller.getShowConsensusMenuItem().selectedProperty().bindBidirectional(view.optionShowConsensusProperty());

		controller.getExpandHorizontallyButton().setOnAction(e -> view.setOptionHorizontalZoomFactor(1.2 * view.getOptionHorizontalZoomFactor()));
		controller.getExpandHorizontallyButton().disableProperty().bind(Bindings.createBooleanBinding(() -> view.getOptionDiagram().isRadialOrCircular(), view.optionDiagramProperty()));
		controller.getContractHorizontallyButton().setOnAction(e -> view.setOptionHorizontalZoomFactor(1.0 / 1.2 * view.getOptionHorizontalZoomFactor()));
		controller.getContractHorizontallyButton().disableProperty().bind(controller.getExpandHorizontallyButton().disableProperty());

		controller.getExpandVerticallyButton().setOnAction(e -> {
			view.setOptionVerticalZoomFactor(1.2 * view.getOptionVerticalZoomFactor());
			if (view.getOptionDiagram().isRadialOrCircular()) {
				view.setOptionHorizontalZoomFactor(1.2 * view.getOptionHorizontalZoomFactor());
			}
		});
		controller.getContractVerticallyButton().setOnAction(e -> {
			view.setOptionVerticalZoomFactor(1.0 / 1.2 * view.getOptionVerticalZoomFactor());
			if (view.getOptionDiagram().isRadialOrCircular()) {
				view.setOptionHorizontalZoomFactor(1.0 / 1.2 * view.getOptionHorizontalZoomFactor());
			}
		});

		controller.getJitterMenuItem().selectedProperty().bindBidirectional(view.optionJitterProperty());

		controller.getColorIncompatibleTreesMenuItem().selectedProperty().bindBidirectional(view.optionColorIncompatibleEdgesProperty());

		InvalidationListener invalidationListener = e -> {
			var trees = view.isOptionRerootAndRescale() ? RerootAndRescaleTrees.apply(mainWindow.getWorkflow().getWorkingTaxaBlock(), view.getTrees()) : view.getTrees();
			drawer.apply(targetBounds.get(),
					trees, controller.getCenterPane(), view.getOptionDiagram(), view.getOptionAveraging(),
					view.getOptionOrientation() != Rotate0Deg,
					view.isOptionJitter(),
					view.getOptionColorIncompatibleEdges(),
					view.getOptionHorizontalZoomFactor(), view.getOptionVerticalZoomFactor(), view.optionFontScaleFactorProperty(),
					view.optionShowTreesProperty(), view.isOptionHideFirst10PercentTrees(), view.optionShowConsensusProperty(),
					view.getOptionStrokeWidth(), view.getOptionEdgeColor(), view.getOptionOtherColor());
		};

		targetBounds.addListener(invalidationListener);
		view.optionDiagramProperty().addListener(invalidationListener);
		view.optionHorizontalZoomFactorProperty().addListener(invalidationListener);
		view.optionVerticalZoomFactorProperty().addListener(invalidationListener);
		view.optionStrokeWidthProperty().addListener(invalidationListener);
		view.optionEdgeColorProperty().addListener(invalidationListener);
		view.optionOtherColorProperty().addListener(invalidationListener);

		controller.getDecreaseFontButton().setOnAction(e -> view.setOptionFontScaleFactor(1 / 1.1 * view.getOptionFontScaleFactor()));
		controller.getIncreaseFontButton().setOnAction(e -> view.setOptionFontScaleFactor(1.1 * view.getOptionFontScaleFactor()));

		view.optionHideFirst10PercentTreesProperty().addListener(invalidationListener);
		view.optionHorizontalZoomFactorProperty().addListener(invalidationListener);
		view.optionJitterProperty().addListener(invalidationListener);
		view.optionColorIncompatibleEdgesProperty().addListener(invalidationListener);
		view.getTrees().addListener(invalidationListener);
		MainWindowManager.useDarkThemeProperty().addListener(new WeakInvalidationListener(invalidationListener));

		findToolBar = FindReplaceTaxa.create(mainWindow, view.getUndoManager());
		findToolBar.setShowFindToolBar(false);
		controller.getvBox().getChildren().add(findToolBar);
		controller.getFindToggleButton().setOnAction(e -> {
			if (!findToolBar.isShowFindToolBar()) {
				findToolBar.setShowFindToolBar(true);
				controller.getFindToggleButton().setSelected(true);
				controller.getFindToggleButton().setGraphic(ResourceManagerFX.getIconAsImageView("sun/Replace24.gif", 16));
			} else if (!findToolBar.isShowReplaceToolBar()) {
				findToolBar.setShowReplaceToolBar(true);
				controller.getFindToggleButton().setSelected(true);
			} else {
				findToolBar.setShowFindToolBar(false);
				findToolBar.setShowReplaceToolBar(false);
				controller.getFindToggleButton().setSelected(false);
				controller.getFindToggleButton().setGraphic(ResourceManagerFX.getIconAsImageView("sun/Find24.gif", 16));
			}
		});

		view.viewTabProperty().addListener((v, o, n) -> {
			if (n != null) {
				controller.getvBox().getChildren().add(0, n.getAlgorithmBreadCrumbsToolBar());
			}
		});
		view.emptyProperty().addListener(e -> view.getRoot().setDisable(view.emptyProperty().get()));

		final ObservableSet<HeightAndAngles.Averaging> disabledAveraging = FXCollections.observableSet();
		view.optionDiagramProperty().addListener((v, o, n) -> {
			disabledAveraging.clear();
			if (n == DensiTreeDiagramType.RadialPhylogram) {
				disabledAveraging.add(HeightAndAngles.Averaging.ChildAverage);
			}
		});
		controller.getAveragingCBox().setButtonCell(ComboBoxUtils.createButtonCell(disabledAveraging, HeightAndAngles.Averaging::createLabel));
		controller.getAveragingCBox().setCellFactory(ComboBoxUtils.createCellFactory(disabledAveraging, HeightAndAngles.Averaging::createLabel));
		controller.getAveragingCBox().getItems().addAll(HeightAndAngles.Averaging.values());
		controller.getAveragingCBox().valueProperty().bindBidirectional(view.optionAveragingProperty());
		view.optionAveragingProperty().addListener(invalidationListener);


		controller.getOrientationCBox().setButtonCell(ComboBoxUtils.createButtonCell(null, LayoutOrientation::createLabel));
		controller.getOrientationCBox().setCellFactory(ComboBoxUtils.createCellFactory(null, LayoutOrientation::createLabel));
		controller.getOrientationCBox().getItems().addAll(Rotate0Deg, FlipRotate180Deg);
		controller.getOrientationCBox().setValue(view.getOptionOrientation());
		controller.getOrientationCBox().valueProperty().addListener((v, o, n) -> view.optionOrientationProperty().set(n));
		controller.getOrientationCBox().disableProperty().bind(Bindings.createBooleanBinding(() -> view.getOptionDiagram() == DensiTreeDiagramType.RadialPhylogram, view.optionDiagramProperty()));
		view.optionOrientationProperty().addListener(invalidationListener);


		setupMenuItems();

		MainWindowManager.useDarkThemeProperty().addListener((v, o, n) -> {
			if (n && view.getOptionEdgeColor().equals(DensiTreeView.DEFAULT_LIGHTMODE_EDGE_COLOR))
				view.setOptionEdgeColor(DensiTreeView.DEFAULT_DARKMODE_EDGE_COLOR);
			else if (!n && view.getOptionEdgeColor().equals(DensiTreeView.DEFAULT_DARKMODE_EDGE_COLOR))
				view.setOptionEdgeColor(DensiTreeView.DEFAULT_LIGHTMODE_EDGE_COLOR);

		});
	}

	@Override
	public void setupMenuItems() {
		var mainController = mainWindow.getController();

		mainController.getCopyMenuItem().setOnAction(e -> {
			var list = new ArrayList<String>();
			for (var taxon : mainWindow.getTaxonSelectionModel().getSelectedItems()) {
				list.add(RichTextLabel.getRawText(taxon.getDisplayLabelOrName()).trim());
			}
			if (list.size() > 0) {
				var content = new ClipboardContent();
				content.put(DataFormat.PLAIN_TEXT, StringUtils.toString(list, "\n"));
				Clipboard.getSystemClipboard().setContent(content);
			}
		});
		mainController.getCopyMenuItem().disableProperty().bind(mainWindow.getTaxonSelectionModel().sizeProperty().isEqualTo(0));

		mainController.getCutMenuItem().disableProperty().bind(new SimpleBooleanProperty(true));

		mainController.getPasteMenuItem().disableProperty().bind(new SimpleBooleanProperty(true));

		mainWindow.getController().getIncreaseFontSizeMenuItem().setOnAction(controller.getIncreaseFontButton().getOnAction());
		mainWindow.getController().getIncreaseFontSizeMenuItem().disableProperty().bind(controller.getIncreaseFontButton().disableProperty());
		mainWindow.getController().getDecreaseFontSizeMenuItem().setOnAction(controller.getDecreaseFontButton().getOnAction());
		mainWindow.getController().getDecreaseFontSizeMenuItem().disableProperty().bind(controller.getDecreaseFontButton().disableProperty());

		mainController.getZoomInMenuItem().setOnAction(controller.getExpandVerticallyButton().getOnAction());
		mainController.getZoomInMenuItem().disableProperty().bind(controller.getExpandVerticallyButton().disableProperty());

		mainController.getZoomOutMenuItem().setOnAction(controller.getContractVerticallyButton().getOnAction());
		mainController.getZoomOutMenuItem().disableProperty().bind(controller.getContractVerticallyButton().disableProperty());

		mainController.getZoomInHorizontalMenuItem().setOnAction(controller.getExpandHorizontallyButton().getOnAction());
		mainController.getZoomInHorizontalMenuItem().disableProperty().bind(controller.getExpandHorizontallyButton().disableProperty());

		mainController.getZoomOutHorizontalMenuItem().setOnAction(controller.getContractHorizontallyButton().getOnAction());
		mainController.getZoomOutHorizontalMenuItem().disableProperty().bind(controller.getContractHorizontallyButton().disableProperty());

		mainController.getFindMenuItem().setOnAction(e -> findToolBar.setShowFindToolBar(true));

		mainController.getFindAgainMenuItem().setOnAction(e -> findToolBar.findAgain());
		mainController.getFindAgainMenuItem().disableProperty().bind(findToolBar.canFindAgainProperty().not());

		mainController.getReplaceMenuItem().setOnAction(e -> findToolBar.setShowReplaceToolBar(true));

		mainController.getSelectAllMenuItem().setOnAction(e ->
		{
			mainWindow.getTaxonSelectionModel().selectAll(mainWindow.getWorkflow().getWorkingTaxaBlock().getTaxa());
			//view.getSplitSelectionModel().selectAll(IteratorUtils.asList(BitSetUtils.range(1, view.getTreesBlock().getNsplits() + 1)));
		});
		mainController.getSelectAllMenuItem().disableProperty().bind(view.emptyProperty());

		mainController.getSelectNoneMenuItem().setOnAction(e -> {
			mainWindow.getTaxonSelectionModel().clearSelection();
		});
		mainController.getSelectNoneMenuItem().disableProperty().bind(mainWindow.getTaxonSelectionModel().sizeProperty().isEqualTo(0));

		mainController.getSelectInverseMenuItem().setOnAction(e -> mainWindow.getWorkflow().getWorkingTaxaBlock().getTaxa().forEach(t -> mainWindow.getTaxonSelectionModel().toggleSelection(t)));
		mainController.getSelectInverseMenuItem().disableProperty().bind(view.emptyProperty());

		mainController.getLayoutLabelsMenuItem().setOnAction(e -> drawer.getRadialLabelLayout().layoutLabels());
		mainController.getLayoutLabelsMenuItem().disableProperty().bind(view.emptyProperty());
	}
}
