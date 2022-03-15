/*
 * TreePagesViewPresenter.java Copyright (C) 2022 Daniel H. Huson
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
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.collections.ObservableSet;
import javafx.geometry.Bounds;
import javafx.geometry.Dimension2D;
import jloda.fx.find.FindToolBar;
import jloda.fx.util.BasicFX;
import jloda.fx.util.ResourceManagerFX;
import jloda.phylo.PhyloTree;
import jloda.util.NumberUtils;
import jloda.util.StringUtils;
import splitstree6.tabs.IDisplayTabPresenter;
import splitstree6.view.findreplace.FindReplaceTaxa;
import splitstree6.view.splits.viewer.ComboBoxUtils;
import splitstree6.view.trees.layout.ComputeHeightAndAngles;
import splitstree6.view.trees.layout.TreeDiagramType;
import splitstree6.view.trees.layout.TreeLabel;
import splitstree6.window.MainWindow;

/**
 * multi tree view presenter
 * Daniel Huson, 11.2021
 */
public class TreePagesViewPresenter implements IDisplayTabPresenter {
	private final static ObservableList<String> gridValues = FXCollections.observableArrayList("1 x 1");

	private final MainWindow mainWindow;
	private final TreePagesView treePageView;

	private final TreePagesViewController controller;

	private final ObjectProperty<RowsCols> rowsAndCols = new SimpleObjectProperty<>(this, "rowsAndCols", new RowsCols(0, 0));

	private final ObjectProperty<Dimension2D> boxDimensions = new SimpleObjectProperty<>(this, "boxDimensions", new Dimension2D(0, 0));

	private final ObjectProperty<TreePageFactory> treePageFactory = new SimpleObjectProperty<>(this, "treePageFactory", null);

	private final FindToolBar findToolBar;

	/**
	 * constructor
	 */
	public TreePagesViewPresenter(MainWindow mainWindow, TreePagesView treePagesView, ObjectProperty<Bounds> targetBounds, ObservableList<PhyloTree> phyloTrees) {
		this.mainWindow = mainWindow;
		this.treePageView = treePagesView;

		controller = treePagesView.getController();

		final ObservableSet<TreeDiagramType> disabledDiagrams = FXCollections.observableSet();
		treePagesView.reticulatedProperty().addListener((v, o, n) -> {
			disabledDiagrams.clear();
			if (n) {
				disabledDiagrams.add(TreeDiagramType.TriangularCladogram);
				disabledDiagrams.add(TreeDiagramType.RadialCladogram);
				disabledDiagrams.add(TreeDiagramType.RadialPhylogram);
			}
		});

		controller.getDiagramCBox().setButtonCell(ComboBoxUtils.createButtonCell(disabledDiagrams, TreeDiagramType::createNode));
		controller.getDiagramCBox().setCellFactory(ComboBoxUtils.createCellFactory(disabledDiagrams, TreeDiagramType::createNode));
		controller.getDiagramCBox().getItems().addAll(TreeDiagramType.values());
		controller.getDiagramCBox().valueProperty().bindBidirectional(treePagesView.optionDiagramProperty());

		controller.getOrientationCBox().setButtonCell(ComboBoxUtils.createButtonCell(null, LayoutOrientation::createLabel));
		controller.getOrientationCBox().setCellFactory(ComboBoxUtils.createCellFactory(null, LayoutOrientation::createLabel));
		controller.getOrientationCBox().getItems().addAll(LayoutOrientation.values());
		controller.getOrientationCBox().valueProperty().bindBidirectional(treePagesView.optionOrientationProperty());

		final ObservableSet<ComputeHeightAndAngles.Averaging> disabledAveraging = FXCollections.observableSet();
		treePagesView.optionDiagramProperty().addListener((v, o, n) -> {
			disabledAveraging.clear();
			if (n == TreeDiagramType.RadialPhylogram) {
				disabledAveraging.add(ComputeHeightAndAngles.Averaging.ChildAverage);
			}
		});

		controller.getAveragingCBox().setButtonCell(ComboBoxUtils.createButtonCell(disabledAveraging, ComputeHeightAndAngles.Averaging::createLabel));
		controller.getAveragingCBox().setCellFactory(ComboBoxUtils.createCellFactory(disabledAveraging, ComputeHeightAndAngles.Averaging::createLabel));
		controller.getAveragingCBox().getItems().addAll(ComputeHeightAndAngles.Averaging.values());
		controller.getAveragingCBox().valueProperty().bindBidirectional(treePagesView.optionAveragingProperty());

		controller.getRowsColsCBox().getItems().setAll(gridValues);
		gridValues.addListener((ListChangeListener<? super String>) e -> controller.getRowsColsCBox().getItems().setAll(gridValues));

		treePagesView.optionRowsProperty().addListener((v, o, n) -> {
			if (rowsAndCols.get().rows() != n.intValue())
				rowsAndCols.set(new RowsCols(n.intValue(), rowsAndCols.get().cols()));
		});
		treePagesView.optionColsProperty().addListener((v, o, n) -> {
			if (rowsAndCols.get().cols() != n.intValue())
				rowsAndCols.set(new RowsCols(rowsAndCols.get().rows(), n.intValue()));
		});

		rowsAndCols.addListener((v, o, n) -> {
			if (n != null) {
				treePagesView.setOptionRows(n.rows());
				treePagesView.setOptionCols(n.cols());
				treePagesView.setOptionZoomFactor(1);
				treePagesView.setOptionFontScaleFactor(1);
				controller.getRowsColsCBox().setValue(n.toString());
			}
		});

		controller.getRowsColsCBox().valueProperty().addListener((v, o, n) -> {
			var rowsCols = RowsCols.valueOf(n);
			if (rowsCols != null)
				rowsAndCols.set(rowsCols);
		});

		rowsAndCols.set(new RowsCols(treePagesView.getOptionRows(), treePagesView.getOptionCols()));

		controller.getTreeCBox().valueProperty().addListener((v, o, n) -> {
			if (n != null) {
				var index = phyloTrees.indexOf(n);
				var page = index / (treePagesView.getOptionRows() * treePagesView.getOptionCols()) + 1;
				treePagesView.setPageNumber(page);
			}
		});
		controller.getTreeCBox().setItems(phyloTrees);

		InvalidationListener updateTreeInvalidationListener = e -> {
			var t = (treePagesView.getPageNumber() - 1) * (treePagesView.getOptionRows() * treePagesView.getOptionCols());
			if (t >= 0 && t < phyloTrees.size())
				controller.getTreeCBox().setValue(phyloTrees.get(t));
			else
				controller.getTreeCBox().setValue(null);
		};

		treePagesView.pageNumberProperty().addListener(updateTreeInvalidationListener);
		treePagesView.optionRowsProperty().addListener(updateTreeInvalidationListener);
		treePagesView.optionColsProperty().addListener(updateTreeInvalidationListener);
		treePagesView.getTrees().addListener(updateTreeInvalidationListener);

		targetBounds.addListener((v, o, n) -> {
			var width = n.getWidth();
			var height = Math.max(150, n.getHeight() - 150);
			controller.getPagination().setPrefWidth(width);
			controller.getPagination().setPrefHeight(height);
			boxDimensions.set(new Dimension2D(width / treePagesView.getOptionCols() - 5, height / treePagesView.getOptionRows() - 5));
			if (false)
				Platform.runLater(() -> mainWindow.getController().getLayoutLabelsMenuItem().getOnAction().handle(null));
		});

		rowsAndCols.addListener((v, o, n) -> boxDimensions.set(new Dimension2D(boxDimensions.get().getWidth() * o.cols() / n.cols(), boxDimensions.get().getHeight() * o.rows() / n.rows())));

		var numberOfPages = new SimpleIntegerProperty(0);

		treePagesView.pageNumberProperty().addListener((v, o, n) -> controller.getPagination().setCurrentPageIndex(n.intValue() - 1));
		controller.getPagination().currentPageIndexProperty().addListener((v, o, n) -> treePagesView.setPageNumber(n.intValue() + 1));

		treePageFactory.set(new TreePageFactory(mainWindow, treePagesView, phyloTrees, treePagesView.optionRowsProperty(), treePagesView.optionColsProperty(), boxDimensions));

		controller.getPagination().pageFactoryProperty().bind(treePageFactory);
		controller.getPagination().pageCountProperty().bind(numberOfPages);

		{
			InvalidationListener invalidationListener = e -> numberOfPages.set(1 + (phyloTrees.size() - 1) / (rowsAndCols.get().rows() * rowsAndCols.get().cols()));
			rowsAndCols.addListener(invalidationListener);
			phyloTrees.addListener(invalidationListener);
			invalidationListener.invalidated(null);
		}

		treePagesView.pageNumberProperty().addListener((v, o, n) -> {
			if (n.intValue() < 1)
				Platform.runLater(() -> treePagesView.setPageNumber(1));
			else if (n.intValue() >= numberOfPages.get())
				Platform.runLater(() -> treePagesView.setPageNumber((Math.max(1, numberOfPages.get()))));
		});

		{
			var labelProperty = new SimpleStringProperty();
			BasicFX.makeMultiStateToggle(controller.getShowTreeNamesToggleButton(), treePagesView.getOptionTreeLabels().label(), labelProperty, TreeLabel.labels());
			labelProperty.addListener((v, o, n) -> treePagesView.setOptionTreeLabels(TreeLabel.valueOfLabel(n)));
		}

		findToolBar = FindReplaceTaxa.create(mainWindow, treePagesView.getUndoManager());
		findToolBar.setShowFindToolBar(false);
		controller.getvBox().getChildren().add(findToolBar);
		controller.getFindButton().setOnAction(e -> {
			if (!findToolBar.isShowFindToolBar()) {
				findToolBar.setShowFindToolBar(true);
				controller.getFindButton().setGraphic(ResourceManagerFX.getIconAsImageView("sun/Replace24.gif", 16));
			} else if (!findToolBar.isShowReplaceToolBar()) {
				findToolBar.setShowReplaceToolBar(true);
				controller.getFindButton().setGraphic(ResourceManagerFX.getIconAsImageView("sun/Find24.gif", 16));
			} else {
				findToolBar.setShowFindToolBar(false);
				findToolBar.setShowReplaceToolBar(false);
			}
		});

		controller.getShowInternalLabelsToggleButton().selectedProperty().bindBidirectional(treePagesView.optionShowInternalLabelsProperty());
		controller.getShowInternalLabelsToggleButton().disableProperty().bind(treePageView.emptyProperty());

		controller.getZoomInButton().setOnAction(e -> treePageView.setOptionZoomFactor(1.1 * treePageView.getOptionZoomFactor()));
		controller.getZoomInButton().disableProperty().bind(treePageView.emptyProperty().or(treePageView.optionZoomFactorProperty().greaterThan(4.0 / 1.1)));
		controller.getZoomOutButton().setOnAction(e -> treePageView.setOptionZoomFactor((1.0 / 1.1) * treePageView.getOptionZoomFactor()));
		controller.getZoomOutButton().disableProperty().bind(treePageView.emptyProperty());

		controller.getIncreaseFontButton().setOnAction(e -> treePageView.setOptionFontScaleFactor(1.2 * treePageView.getOptionFontScaleFactor()));
		controller.getIncreaseFontButton().disableProperty().bind(treePageView.emptyProperty());
		controller.getDecreaseFontButton().setOnAction(e -> treePageView.setOptionFontScaleFactor((1.0 / 1.2) * treePageView.getOptionFontScaleFactor()));
		controller.getDecreaseFontButton().disableProperty().bind(treePageView.emptyProperty());

		var undoManager = treePagesView.getUndoManager();
		rowsAndCols.addListener((v, o, n) -> undoManager.add("set grid dimensions", rowsAndCols, o, n));
		treePagesView.pageNumberProperty().addListener((c, o, n) -> undoManager.add("set page", treePagesView.pageNumberProperty(), o, n));
		treePagesView.optionDiagramProperty().addListener((v, o, n) -> undoManager.add("set diagram type", treePagesView.optionDiagramProperty(), o, n));
		treePagesView.optionOrientationProperty().addListener((v, o, n) -> undoManager.add("set layout orientation", treePagesView.optionOrientationProperty(), o, n));
		treePagesView.optionTreeLabelsProperty().addListener((v, o, n) -> undoManager.add("set show tree names", treePagesView.optionTreeLabelsProperty(), o, n));
		treePagesView.optionFontScaleFactorProperty().addListener((v, o, n) -> undoManager.add("set font size", treePagesView.optionFontScaleFactorProperty(), o, n));
		treePagesView.optionZoomFactorProperty().addListener((v, o, n) -> undoManager.add("set zoom", treePagesView.optionZoomFactorProperty(), o, n));

		treePagesView.viewTabProperty().addListener((v, o, n) -> {
			if (n != null) {
				Platform.runLater(() -> controller.getvBox().getChildren().add(0, n.getAlgorithmBreadCrumbsToolBar()));
			}
		});
		treePagesView.emptyProperty().addListener(e -> treePagesView.getRoot().setDisable(treePagesView.emptyProperty().get()));

		Platform.runLater(this::setupMenuItems);
	}

	@Override
	public void setupMenuItems() {
		var mainController = mainWindow.getController();

		mainController.getCopyNewickMenuItem().setOnAction(e -> {
			var page = treePageView.getPageNumber();
			var count = treePageView.getOptionCols() * treePageView.getOptionRows();
			var bot = (page - 1) * count;
			var top = Math.min(treePageView.getTrees().size(), page * count);
			var buf = new StringBuilder();
			for (var t = bot; t < top; t++) {
				buf.append(treePageView.getTrees().get(t).toBracketString(true)).append(";\n");
			}
			BasicFX.putTextOnClipBoard(buf.toString());
		});
		mainController.getCopyNewickMenuItem().disableProperty().bind(treePageView.emptyProperty());

		mainController.getIncreaseFontSizeMenuItem().setOnAction(controller.getIncreaseFontButton().getOnAction());
		mainController.getIncreaseFontSizeMenuItem().disableProperty().bind(controller.getIncreaseFontButton().disableProperty());
		mainController.getDecreaseFontSizeMenuItem().setOnAction(controller.getDecreaseFontButton().getOnAction());
		mainController.getDecreaseFontSizeMenuItem().disableProperty().bind(controller.getDecreaseFontButton().disableProperty());

		mainController.getZoomInMenuItem().setOnAction(controller.getZoomInButton().getOnAction());
		mainController.getZoomInMenuItem().disableProperty().bind(controller.getZoomInButton().disableProperty());
		mainController.getZoomOutMenuItem().setOnAction(controller.getZoomOutButton().getOnAction());
		mainController.getZoomOutMenuItem().disableProperty().bind(controller.getZoomOutButton().disableProperty());

		mainController.getFindMenuItem().setOnAction(e -> findToolBar.setShowFindToolBar(true));
		mainController.getFindAgainMenuItem().setOnAction(e -> findToolBar.findAgain());
		mainController.getFindAgainMenuItem().disableProperty().bind(findToolBar.canFindAgainProperty().not());
		mainController.getReplaceMenuItem().setOnAction(e -> findToolBar.setShowReplaceToolBar(true));

		mainController.getSelectAllMenuItem().setOnAction(e -> mainWindow.getTaxonSelectionModel().selectAll(mainWindow.getWorkflow().getWorkingTaxaBlock().getTaxa()));
		mainController.getSelectNoneMenuItem().setOnAction(e -> mainWindow.getTaxonSelectionModel().clearSelection());
		mainController.getSelectNoneMenuItem().disableProperty().bind(mainWindow.getTaxonSelectionModel().sizeProperty().isEqualTo(0));

		mainController.getLayoutLabelsMenuItem().setOnAction(e -> treePageFactory.get().updateLabelLayout(treePageView.getOptionOrientation()));
		mainController.getLayoutLabelsMenuItem().disableProperty().bind(treePageView.emptyProperty().or(treePageView.optionDiagramProperty().isNotEqualTo(TreeDiagramType.RadialPhylogram)));
	}

    private record RowsCols(int rows, int cols) {
        public static RowsCols valueOf(String str) {
            if (str != null) {
                var tokens = StringUtils.split(str, 'x');
                if (tokens.length == 2 && NumberUtils.isInteger(tokens[0]) && NumberUtils.isInteger(tokens[1]))
                    return new RowsCols(NumberUtils.parseInt(tokens[0]), NumberUtils.parseInt(tokens[1]));
            }
            return null;
        }

        public String toString() {
			return rows + " x " + cols;
		}
	}
}
