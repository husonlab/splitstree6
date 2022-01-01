/*
 *  TreePagesViewPresenter.java Copyright (C) 2021 Daniel H. Huson
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
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.collections.ObservableSet;
import javafx.geometry.Bounds;
import javafx.geometry.Dimension2D;
import javafx.scene.control.SelectionMode;
import jloda.fx.find.FindToolBar;
import jloda.fx.find.Searcher;
import jloda.phylo.PhyloTree;
import jloda.util.NumberUtils;
import jloda.util.Pair;
import jloda.util.StringUtils;
import splitstree6.data.parts.Taxon;
import splitstree6.tabs.IDisplayTabPresenter;
import splitstree6.view.splits.viewer.ComboBoxUtils;
import splitstree6.view.trees.layout.TreeDiagramType;
import splitstree6.window.MainWindow;

import java.util.function.Function;

/**
 * multi tree view presenter
 * Daniel Huson, 11.2021
 */
public class TreePagesViewPresenter implements IDisplayTabPresenter {
	private final static ObservableList<String> gridValues = FXCollections.observableArrayList("1 x 1");


	private final MainWindow mainWindow;
	private final TreePagesView treePageView;

	private final TreePagesViewController controller;

	private final ObjectProperty<RowsCols> rowsAndCols = new SimpleObjectProperty<>(new RowsCols(0, 0));

	private final ObjectProperty<Dimension2D> boxDimensions = new SimpleObjectProperty<>(new Dimension2D(0, 0));

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

		controller.getOrientationCBox().setButtonCell(ComboBoxUtils.createButtonCell(null, LayoutOrientation::createIconLabel));
		controller.getOrientationCBox().setCellFactory(ComboBoxUtils.createCellFactory(null, LayoutOrientation::createIconLabel));
		controller.getOrientationCBox().getItems().addAll(LayoutOrientation.values());
		controller.getOrientationCBox().valueProperty().bindBidirectional(treePagesView.optionOrientationProperty());

		controller.getShowTreeNamesToggleButton().selectedProperty().bindBidirectional(treePagesView.optionShowTreeNamesProperty());

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
			var height = n.getHeight() - 120;
			controller.getPagination().setPrefWidth(width);
			controller.getPagination().setPrefHeight(height);
			boxDimensions.set(new Dimension2D(width / treePagesView.getOptionCols() - 5, height / treePagesView.getOptionRows() - 5));
		});

		rowsAndCols.addListener((v, o, n) -> boxDimensions.set(new Dimension2D(boxDimensions.get().getWidth() * o.cols() / n.cols(), boxDimensions.get().getHeight() * o.rows() / n.rows())));

		var numberOfPages = new SimpleIntegerProperty(0);

		treePagesView.pageNumberProperty().addListener((v, o, n) -> controller.getPagination().setCurrentPageIndex(n.intValue() - 1));
		controller.getPagination().currentPageIndexProperty().addListener((v, o, n) -> treePagesView.setPageNumber(n.intValue() + 1));

		ObjectProperty<TreePageFactory> treePageFactory = new SimpleObjectProperty<>(null);
		treePageFactory.set(new TreePageFactory(mainWindow, treePagesView, phyloTrees, treePagesView.optionRowsProperty(), treePagesView.optionColsProperty(), boxDimensions));

		controller.getPagination().pageFactoryProperty().bind(treePageFactory);
		controller.getPagination().pageCountProperty().bind(numberOfPages);

		{
			InvalidationListener invalidationListener = e -> numberOfPages.set(1 + (phyloTrees.size() - 1) / (treePagesView.getOptionRows() * treePagesView.getOptionCols()));
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

		controller.getOpenButton().setOnAction(mainWindow.getController().getOpenMenuItem().getOnAction());
		controller.getSaveButton().setOnAction(mainWindow.getController().getSaveAsMenuItem().getOnAction());
		controller.getSaveButton().disableProperty().bind(mainWindow.getController().getSaveAsMenuItem().disableProperty());

		controller.getPrintButton().setOnAction(mainWindow.getController().getPrintMenuItem().getOnAction());
		controller.getPrintButton().disableProperty().bind(mainWindow.getController().getPrintMenuItem().disableProperty());

		Function<Integer, Taxon> t2taxon = t -> mainWindow.getActiveTaxa().get(t);

		findToolBar = new FindToolBar(mainWindow.getStage(), new Searcher<>(mainWindow.getActiveTaxa(), t -> mainWindow.getTaxonSelectionModel().isSelected(t2taxon.apply(t)),
				(t, s) -> mainWindow.getTaxonSelectionModel().setSelected(t2taxon.apply(t), s), new SimpleObjectProperty<>(SelectionMode.MULTIPLE), t -> t2taxon.apply(t).getNameAndDisplayLabel("===="),
				label -> label.replaceAll(".*====", ""), null));
		findToolBar.setShowFindToolBar(false);

		controller.getvBox().getChildren().add(findToolBar);
		controller.getFindButton().setOnAction(e -> {
			if (findToolBar.isShowFindToolBar())
				findToolBar.setShowFindToolBar(false);
			else
				findToolBar.setShowFindToolBar(true);
		});


		controller.getZoomInButton().setOnAction(e -> treePageView.setOptionZoomFactor(1.1 * treePageView.getOptionZoomFactor()));
		controller.getZoomInButton().disableProperty().bind(treePageView.emptyProperty().or(treePageView.optionZoomFactorProperty().greaterThan(4.0 / 1.1)));
		controller.getZoomOutButton().setOnAction(e -> treePageView.setOptionZoomFactor((1.0 / 1.1) * treePageView.getOptionZoomFactor()));
		controller.getZoomOutButton().disableProperty().bind(treePageView.emptyProperty());


		var undoManager = treePagesView.getUndoManager();
		rowsAndCols.addListener((v, o, n) -> undoManager.add("Set Grid", rowsAndCols, o, n));
		treePagesView.pageNumberProperty().addListener((c, o, n) -> undoManager.add("Change Page", treePagesView.pageNumberProperty(), o, n));
		treePagesView.optionDiagramProperty().addListener((v, o, n) -> undoManager.add(" Set TreeDiagramType", treePagesView.optionDiagramProperty(), o, n));
		treePagesView.optionOrientationProperty().addListener((v, o, n) -> undoManager.add(" Set LayoutOrientation", treePagesView.optionOrientationProperty(), o, n));
		treePagesView.optionShowTreeNamesProperty().addListener((v, o, n) -> undoManager.add((n ? "Show" : "Hide") + " Tree Names", treePagesView.optionShowTreeNamesProperty(), o, n));
		treePagesView.optionFontScaleFactorProperty().addListener((v, o, n) -> undoManager.add((n.doubleValue() > 1 ? "Increase" : "Decrease ") + " Font Size", treePagesView.optionFontScaleFactorProperty(), o, n));
		treePagesView.optionZoomFactorProperty().addListener((v, o, n) -> undoManager.add((n.doubleValue() > 1 ? "Increase" : "Decrease ") + " Zoom", treePagesView.optionZoomFactorProperty(), o, n));
		treePagesView.optionShowTreeNamesProperty().addListener((v, o, n) -> undoManager.add((n ? "Show" : "Hide") + " Tree names", treePagesView.optionShowTreeNamesProperty(), o, n));

		Platform.runLater(this::setupMenuItems);
	}

	private Pair<Integer, Integer> parseRowsColsText(String text) {
		var tokens = text.split("x");
		if (tokens.length == 2 && NumberUtils.isInteger(tokens[0].trim()) && NumberUtils.isInteger(tokens[1].trim()))
			return new Pair<>(Math.max(1, Integer.parseInt(tokens[0].trim())), Math.max(1, Integer.parseInt(tokens[1].trim())));
		else
			return null;
	}

	@Override
	public void setupMenuItems() {
		mainWindow.getController().getIncreaseFontSizeMenuItem().setOnAction(e -> treePageView.setOptionFontScaleFactor(1.2 * treePageView.getOptionFontScaleFactor()));
		mainWindow.getController().getIncreaseFontSizeMenuItem().disableProperty().bind(treePageView.emptyProperty());
		mainWindow.getController().getDecreaseFontSizeMenuItem().setOnAction(e -> treePageView.setOptionFontScaleFactor((1.0 / 1.2) * treePageView.getOptionFontScaleFactor()));
		mainWindow.getController().getDecreaseFontSizeMenuItem().disableProperty().bind(treePageView.emptyProperty());

		mainWindow.getController().getZoomInMenuItem().setOnAction(controller.getZoomInButton().getOnAction());
		mainWindow.getController().getZoomInMenuItem().disableProperty().bind(controller.getZoomInButton().disableProperty());
		mainWindow.getController().getZoomOutMenuItem().setOnAction(controller.getZoomOutButton().getOnAction());
		mainWindow.getController().getZoomOutMenuItem().disableProperty().bind(controller.getZoomOutButton().disableProperty());

		mainWindow.getController().getFindMenuItem().setOnAction(controller.getFindButton().getOnAction());
		mainWindow.getController().getFindAgainMenuItem().setOnAction(e -> findToolBar.findAgain());
		mainWindow.getController().getFindAgainMenuItem().disableProperty().bind(findToolBar.canFindAgainProperty().not());

		mainWindow.getController().getSelectAllMenuItem().setOnAction(e -> mainWindow.getTaxonSelectionModel().selectAll(mainWindow.getWorkflow().getWorkingTaxaBlock().getTaxa()));
		mainWindow.getController().getSelectNoneMenuItem().setOnAction(e -> mainWindow.getTaxonSelectionModel().clearSelection());
		mainWindow.getController().getSelectNoneMenuItem().disableProperty().bind(mainWindow.getTaxonSelectionModel().sizeProperty().isEqualTo(0));
	}

	private static record RowsCols(int rows, int cols) {
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
