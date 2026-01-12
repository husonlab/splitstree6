/*
 *  DensiTreeViewPresenter.java Copyright (C) 2024 Daniel H. Huson
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

package splitstree6.view.trees.densitree;

import javafx.beans.InvalidationListener;
import javafx.beans.WeakInvalidationListener;
import javafx.beans.binding.Bindings;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableSet;
import javafx.geometry.Bounds;
import javafx.scene.control.RadioMenuItem;
import javafx.scene.image.ImageView;
import javafx.util.StringConverter;
import jloda.fx.control.RichTextLabel;
import jloda.fx.find.FindToolBar;
import jloda.fx.qr.QRViewUtils;
import jloda.fx.qr.TreeNewickQR;
import jloda.fx.util.ClipboardUtils;
import jloda.fx.util.RunAfterAWhile;
import jloda.fx.util.SwipeUtils;
import jloda.fx.window.MainWindowManager;
import jloda.fx.window.NotificationManager;
import jloda.phylo.PhyloTree;
import jloda.phylogeny.layout.Averaging;
import jloda.util.StringUtils;
import splitstree6.layout.tree.LayoutOrientation;
import splitstree6.tabs.IDisplayTabPresenter;
import splitstree6.view.findreplace.FindReplaceTaxa;
import splitstree6.view.utils.ExportUtils;
import splitstree6.window.MainWindow;

import java.util.ArrayList;

public class DensiTreeViewPresenter implements IDisplayTabPresenter {
	private final MainWindow mainWindow;
	private final DensiTreeView view;
	private final DensiTreeViewController controller;
	private final DensiTreeDrawer drawer;

	private final FindToolBar findToolBar;

	private final ObjectProperty<PhyloTree> consensusTree = new SimpleObjectProperty<>(null);

	public DensiTreeViewPresenter(MainWindow mainWindow, DensiTreeView view, ObjectProperty<Bounds> targetBounds) {
		this.mainWindow = mainWindow;
		this.view = view;
		this.controller = view.getController();
		this.drawer = new DensiTreeDrawer(mainWindow);

		controller.getDiagramToggleGroup().selectedToggleProperty().addListener((v, o, n) -> {
			if (n instanceof RadioMenuItem radioMenuItem) {
				view.setOptionDiagram(DensiTreeDiagramType.valueOf(radioMenuItem.getText()));
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

		view.optionRerootAndRescaleProperty().bindBidirectional(controller.getRerootAndRescaleCheckMenuItem().selectedProperty());
		view.optionRerootAndRescaleProperty().addListener((v, o, n) -> {
			if (n)
				NotificationManager.showWarning("Option reroot and rescale under development");
		});
		// todo: RerootAndRescaleTrees.java needs to be finished
		//controller.getRerootAndRescaleChekMenuItem().setDisable(true);

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

		controller.getCenterPane().setOnZoom(e -> {
			if ((e.getZoomFactor() < 1 && view.getOptionHorizontalZoomFactor() * e.getZoomFactor() > 0.2)
				|| (e.getZoomFactor() > 1 && view.getOptionHorizontalZoomFactor() * e.getZoomFactor() < 5)) {
				view.setOptionHorizontalZoomFactor(e.getZoomFactor() * view.getOptionHorizontalZoomFactor());
				if (view.getOptionDiagram().isRadialOrCircular()) {
					view.setOptionVerticalZoomFactor(e.getZoomFactor() * view.getOptionVerticalZoomFactor());
				}
			}
		});

		controller.getJitterMenuItem().selectedProperty().bindBidirectional(view.optionJitterProperty());

		controller.getColorIncompatibleTreesMenuItem().selectedProperty().bindBidirectional(view.optionColorIncompatibleEdgesProperty());

		InvalidationListener invalidationListener = e -> {
			RunAfterAWhile.applyInFXThread(drawer, () -> {
				var trees = view.isOptionRerootAndRescale() ? RerootAndRescaleTrees.apply(mainWindow.getWorkflow().getWorkingTaxaBlock(), view.getTrees()) : view.getTrees();
				drawer.apply(targetBounds.get(),
						trees, controller.getCenterPane(), view.getOptionDiagram(), view.getOptionAveraging(),
						view.getOptionOrientation().startsWith("Flip"),
						view.isOptionJitter(), view.isOptionRerootAndRescale(),
						view.getOptionColorIncompatibleEdges(),
						view.getOptionHorizontalZoomFactor(), view.getOptionVerticalZoomFactor(), view.optionFontScaleFactorProperty(),
						view.optionShowTreesProperty(), view.isOptionHideFirst10PercentTrees(), view.optionShowConsensusProperty(),
						view.getOptionStrokeWidth(), view.getOptionEdgeColor(), view.getOptionOtherColor());
				consensusTree.set(drawer.getConsensusTree());
			});
		};

		targetBounds.addListener(invalidationListener);
		view.optionDiagramProperty().addListener(invalidationListener);
		view.optionHorizontalZoomFactorProperty().addListener(invalidationListener);
		view.optionVerticalZoomFactorProperty().addListener(invalidationListener);
		view.optionStrokeWidthProperty().addListener(invalidationListener);
		view.optionEdgeColorProperty().addListener(invalidationListener);
		view.optionOtherColorProperty().addListener(invalidationListener);
		view.optionRerootAndRescaleProperty().addListener(invalidationListener);

		view.optionHideFirst10PercentTreesProperty().addListener(invalidationListener);
		view.optionHorizontalZoomFactorProperty().addListener(invalidationListener);
		view.optionJitterProperty().addListener(invalidationListener);
		view.optionColorIncompatibleEdgesProperty().addListener(invalidationListener);
		view.getTrees().addListener(invalidationListener);
		MainWindowManager.useDarkThemeProperty().addListener(new WeakInvalidationListener(invalidationListener));

		findToolBar = FindReplaceTaxa.create(mainWindow, view.getUndoManager());
		findToolBar.setShowFindToolBar(false);
		controller.getvBox().getChildren().add(findToolBar);

		// FindReplaceUtils.setup(findToolBar, controller.getFindToggleButton(), true);

		view.viewTabProperty().addListener((v, o, n) -> {
			if (n != null) {
				controller.getvBox().getChildren().add(0, n.getAlgorithmBreadCrumbsToolBar());
			}
		});
		view.emptyProperty().addListener(e -> view.getRoot().setDisable(view.emptyProperty().get()));

		final ObservableSet<Averaging> disabledAveraging = FXCollections.observableSet();
		view.optionDiagramProperty().addListener((v, o, n) -> {
			disabledAveraging.clear();
			if (n == DensiTreeDiagramType.RadialPhylogram) {
				disabledAveraging.add(Averaging.ChildAverage);
			}
		});
		controller.getAveragingCBox().getItems().addAll(Averaging.values());
		controller.getAveragingCBox().valueProperty().bindBidirectional(view.optionAveragingProperty());
		view.optionAveragingProperty().addListener(invalidationListener);
		controller.getAveragingCBox().setConverter(new StringConverter<>() {
			@Override
			public String toString(Averaging value) {
				return value == null ? "" : Averaging.createLabel(value);
			}

			@Override
			public Averaging fromString(String string) {
				return null; // not used for non-editable choice box
			}
		});

		controller.getFlipButton().setOnAction(e -> {
			view.setOptionOrientation(LayoutOrientation.valueOf(view.getOptionOrientation()).getFlipVertical().toString());
		});
		controller.getFlipButton().disableProperty().bind(view.emptyProperty());
		view.optionOrientationProperty().addListener(invalidationListener);

		setupMenuItems();

		MainWindowManager.useDarkThemeProperty().addListener((v, o, n) -> {
			if (n && view.getOptionEdgeColor().equals(DensiTreeView.DEFAULT_LIGHTMODE_EDGE_COLOR))
				view.setOptionEdgeColor(DensiTreeView.DEFAULT_DARKMODE_EDGE_COLOR);
			else if (!n && view.getOptionEdgeColor().equals(DensiTreeView.DEFAULT_DARKMODE_EDGE_COLOR))
				view.setOptionEdgeColor(DensiTreeView.DEFAULT_LIGHTMODE_EDGE_COLOR);
		});

		SwipeUtils.setConsumeSwipeLeft(controller.getAnchorPane());
		SwipeUtils.setConsumeSwipeRight(controller.getAnchorPane());
		SwipeUtils.setOnSwipeUp(controller.getAnchorPane(), () -> controller.getFlipButton().fire());
		SwipeUtils.setOnSwipeDown(controller.getAnchorPane(), () -> controller.getFlipButton().fire());

		var qrImageView = new SimpleObjectProperty<ImageView>();
		QRViewUtils.setup(controller.getAnchorPane(), consensusTree, () -> TreeNewickQR.apply(consensusTree.get(), true, false, false, 4296), qrImageView, view.optionShowQRCodeProperty());

	}

	@Override
	public void setupMenuItems() {
		var mainController = mainWindow.getController();

		mainController.getCutMenuItem().disableProperty().bind(new SimpleBooleanProperty(true));
		mainController.getPasteMenuItem().disableProperty().bind(new SimpleBooleanProperty(true));

		mainController.getCopyMenuItem().setOnAction(e -> {
			var list = new ArrayList<String>();
			for (var taxon : mainWindow.getTaxonSelectionModel().getSelectedItems()) {
				list.add(RichTextLabel.getRawText(taxon.getDisplayLabelOrName()).trim());
			}
			if (!list.isEmpty()) {
				ClipboardUtils.putString(StringUtils.toString(list, "\n"));
			} else {
				mainWindow.getController().getCopyNewickMenuItem().fire();
			}
		});
		mainController.getCopyMenuItem().disableProperty().bind(view.emptyProperty());

		mainWindow.getController().getCopyNewickMenuItem().setOnAction(e -> {
			var tree = this.drawer.getConsensusTree();
			if (tree != null)
				ClipboardUtils.putString(tree.toBracketString(true) + ";\n");
		});
		mainWindow.getController().getCopyNewickMenuItem().disableProperty().bind(view.emptyProperty().or(controller.getShowConsensusMenuItem().selectedProperty().not()));

		mainWindow.getController().getIncreaseFontSizeMenuItem().setOnAction(e -> view.setOptionFontScaleFactor(1.2 * view.getOptionFontScaleFactor()));
		mainWindow.getController().getIncreaseFontSizeMenuItem().disableProperty().bind(view.emptyProperty());
		mainWindow.getController().getDecreaseFontSizeMenuItem().setOnAction(e -> view.setOptionFontScaleFactor((1.0 / 1.2) * view.getOptionFontScaleFactor()));
		mainWindow.getController().getDecreaseFontSizeMenuItem().disableProperty().bind(view.emptyProperty());

		mainController.getZoomInMenuItem().setOnAction(controller.getExpandVerticallyButton().getOnAction());
		mainController.getZoomInMenuItem().disableProperty().bind(controller.getExpandVerticallyButton().disableProperty());

		mainController.getZoomOutMenuItem().setOnAction(controller.getContractVerticallyButton().getOnAction());
		mainController.getZoomOutMenuItem().disableProperty().bind(controller.getContractVerticallyButton().disableProperty());

		mainController.getZoomInHorizontalMenuItem().setOnAction(controller.getExpandHorizontallyButton().getOnAction());
		mainController.getZoomInHorizontalMenuItem().disableProperty().bind(controller.getExpandHorizontallyButton().disableProperty());

		mainController.getZoomOutHorizontalMenuItem().setOnAction(controller.getContractHorizontallyButton().getOnAction());
		mainController.getZoomOutHorizontalMenuItem().disableProperty().bind(controller.getContractHorizontallyButton().disableProperty());

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

		mainController.getFlipMenuItem().setOnAction(controller.getFlipButton().getOnAction());
		mainController.getFlipMenuItem().disableProperty().bind(controller.getFlipButton().disableProperty());

		mainController.setupSingleBidirectionalBinding(mainController.getShowQRCodeMenuItem().selectedProperty(), view.optionShowQRCodeProperty());
		mainController.getShowQRCodeMenuItem().disableProperty().bind(view.emptyProperty());

		mainController.getLayoutLabelsMenuItem().setOnAction(e -> drawer.getRadialLabelLayout().layoutLabels());
		mainController.getLayoutLabelsMenuItem().disableProperty().bind(view.emptyProperty());

		view.viewTabProperty().addListener((v, o, n) -> {
			if (n != null)
				ExportUtils.setup(mainWindow, n.getDataNode(), view.emptyProperty());
		});
	}

	public FindToolBar getFindToolBar() {
		return findToolBar;
	}

	@Override
	public boolean allowFindReplace() {
		return true;
	}
}
