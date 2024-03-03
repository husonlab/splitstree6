/*
 * TreePagesViewPresenter.java Copyright (C) 2024 Daniel H. Huson
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
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.collections.ObservableSet;
import javafx.geometry.Bounds;
import javafx.geometry.Dimension2D;
import jloda.fx.control.RichTextLabel;
import jloda.fx.find.FindToolBar;
import jloda.fx.util.BasicFX;
import jloda.fx.util.ClipboardUtils;
import jloda.phylo.PhyloTree;
import jloda.util.NumberUtils;
import jloda.util.StringUtils;
import splitstree6.layout.tree.HeightAndAngles;
import splitstree6.layout.tree.TreeDiagramType;
import splitstree6.layout.tree.TreeLabel;
import splitstree6.tabs.IDisplayTabPresenter;
import splitstree6.utils.SwipeUtils;
import splitstree6.view.findreplace.FindReplaceTaxa;
import splitstree6.view.utils.ComboBoxUtils;
import splitstree6.view.utils.ExportUtils;
import splitstree6.window.MainWindow;

import java.util.ArrayList;
import java.util.List;

/**
 * multi tree view presenter
 * Daniel Huson, 11.2021
 */
public class TreePagesViewPresenter implements IDisplayTabPresenter {
	private final static ObservableList<String> gridValues = FXCollections.observableArrayList(List.of("3x3", "2x1", "1x2", "2x2", "4x3", "3x4", "4x3", "5x5", "1x1"));

	private final MainWindow mainWindow;
	private final TreePagesView view;

	private final TreePagesViewController controller;

	private final ObjectProperty<RowsCols> rowsAndCols = new SimpleObjectProperty<>(this, "rowsAndCols", new RowsCols(0, 0));

	private final ObjectProperty<Dimension2D> boxDimensions = new SimpleObjectProperty<>(this, "boxDimensions", new Dimension2D(0, 0));

	private final ObjectProperty<TreePageFactory> treePageFactory = new SimpleObjectProperty<>(this, "treePageFactory", null);

	private final BooleanProperty changingOrientation = new SimpleBooleanProperty(this, "changingOrientation", false);

	private final FindToolBar findToolBar;

	/**
	 * constructor
	 */
	public TreePagesViewPresenter(MainWindow mainWindow, TreePagesView view, ObjectProperty<Bounds> targetBounds, ObservableList<PhyloTree> phyloTrees) {
		this.mainWindow = mainWindow;
		this.view = view;

		controller = view.getController();

		final ObservableSet<TreeDiagramType> disabledDiagrams = FXCollections.observableSet();
		view.reticulatedProperty().addListener((v, o, n) -> {
			disabledDiagrams.clear();
			if (n) {
				disabledDiagrams.add(TreeDiagramType.TriangularCladogram);
				disabledDiagrams.add(TreeDiagramType.RadialCladogram);
				disabledDiagrams.add(TreeDiagramType.RadialPhylogram);
			}
		});

		controller.getDiagramCBox().setButtonCell(ComboBoxUtils.createButtonCell(disabledDiagrams, TreeDiagramType::icon));
		controller.getDiagramCBox().setCellFactory(ComboBoxUtils.createCellFactory(disabledDiagrams, TreeDiagramType::icon));
		controller.getDiagramCBox().getItems().addAll(TreeDiagramType.values());
		controller.getDiagramCBox().valueProperty().bindBidirectional(view.optionDiagramProperty());

		controller.getRotateLeftButton().setOnAction(e -> view.setOptionOrientation(view.getOptionOrientation().getRotateLeft()));
		controller.getRotateLeftButton().disableProperty().bind(view.emptyProperty().or(view.emptyProperty()));
		controller.getRotateRightButton().setOnAction(e -> view.setOptionOrientation(view.getOptionOrientation().getRotateRight()));
		controller.getRotateRightButton().disableProperty().bind(controller.getRotateLeftButton().disableProperty());
		controller.getFlipHorizontalButton().setOnAction(e -> view.setOptionOrientation(view.getOptionOrientation().getFlipHorizontal()));
		controller.getFlipHorizontalButton().disableProperty().bind(controller.getRotateLeftButton().disableProperty());

		controller.getFlipVerticalButton().setOnAction(e -> view.setOptionOrientation(view.getOptionOrientation().getFlipVertical()));
		controller.getFlipVerticalButton().disableProperty().bind(controller.getRotateLeftButton().disableProperty());


		final ObservableSet<HeightAndAngles.Averaging> disabledAveraging = FXCollections.observableSet();
		view.optionDiagramProperty().addListener((v, o, n) -> {
			disabledAveraging.clear();
			if (n == TreeDiagramType.RadialPhylogram) {
				disabledAveraging.add(HeightAndAngles.Averaging.ChildAverage);
			}
		});

		controller.getAveragingCBox().setButtonCell(ComboBoxUtils.createButtonCell(disabledAveraging, HeightAndAngles.Averaging::createLabel));
		controller.getAveragingCBox().setCellFactory(ComboBoxUtils.createCellFactory(disabledAveraging, HeightAndAngles.Averaging::createLabel));
		controller.getAveragingCBox().getItems().addAll(HeightAndAngles.Averaging.values());
		controller.getAveragingCBox().valueProperty().bindBidirectional(view.optionAveragingProperty());

		controller.getRowsColsCBox().getItems().setAll(gridValues);
		controller.getRowsColsCBox().setValue(gridValues.get(0));
		gridValues.addListener((ListChangeListener<? super String>) e -> controller.getRowsColsCBox().getItems().setAll(gridValues));

		view.optionRowsProperty().addListener((v, o, n) -> {
			if (rowsAndCols.get().rows() != n.intValue())
				rowsAndCols.set(new RowsCols(n.intValue(), rowsAndCols.get().cols()));
		});
		view.optionColsProperty().addListener((v, o, n) -> {
			if (rowsAndCols.get().cols() != n.intValue())
				rowsAndCols.set(new RowsCols(rowsAndCols.get().rows(), n.intValue()));
		});

		rowsAndCols.addListener((v, o, n) -> {
			if (n != null) {
				view.setOptionRows(n.rows());
				view.setOptionCols(n.cols());
				view.setOptionZoomFactor(1);
				view.setOptionFontScaleFactor(1);
				Platform.runLater(() -> controller.getRowsColsCBox().setValue(n.toString()));
			}
		});

		controller.getRowsColsCBox().valueProperty().addListener((v, o, n) -> {
			var rowsCols = RowsCols.valueOf(n);
			if (rowsCols != null)
				rowsAndCols.set(rowsCols);
		});

		rowsAndCols.set(new RowsCols(view.getOptionRows(), view.getOptionCols()));

		controller.getTreeCBox().valueProperty().addListener((v, o, n) -> {
			if (n != null) {
				var index = phyloTrees.indexOf(n);
				var page = index / (view.getOptionRows() * view.getOptionCols()) + 1;
				view.setPageNumber(page);
			}
		});
		controller.getTreeCBox().setItems(phyloTrees);

		InvalidationListener updateTreeInvalidationListener = e -> {
			var t = (view.getPageNumber() - 1) * (view.getOptionRows() * view.getOptionCols());
			if (t >= 0 && t < phyloTrees.size())
				controller.getTreeCBox().setValue(phyloTrees.get(t));
			else
				controller.getTreeCBox().setValue(null);
		};

		view.pageNumberProperty().addListener(updateTreeInvalidationListener);
		view.optionRowsProperty().addListener(updateTreeInvalidationListener);
		view.optionColsProperty().addListener(updateTreeInvalidationListener);
		view.getTrees().addListener(updateTreeInvalidationListener);

		targetBounds.addListener((v, o, n) -> {
			var width = n.getWidth();
			var height = Math.max(150, n.getHeight() - 150);
			controller.getPagination().setPrefWidth(width);
			controller.getPagination().setPrefHeight(height);
			boxDimensions.set(new Dimension2D(width / view.getOptionCols() - 5, height / view.getOptionRows() - 5));
			if (false)
				Platform.runLater(() -> mainWindow.getController().getLayoutLabelsMenuItem().getOnAction().handle(null));
		});

		rowsAndCols.addListener((v, o, n) -> boxDimensions.set(new Dimension2D(boxDimensions.get().getWidth() * o.cols() / n.cols(), boxDimensions.get().getHeight() * o.rows() / n.rows())));

		var numberOfPages = new SimpleIntegerProperty(0);

		view.pageNumberProperty().addListener((v, o, n) -> controller.getPagination().setCurrentPageIndex(n.intValue() - 1));
		controller.getPagination().currentPageIndexProperty().addListener((v, o, n) -> view.setPageNumber(n.intValue() + 1));

		treePageFactory.set(new TreePageFactory(mainWindow, view, phyloTrees, view.optionRowsProperty(), view.optionColsProperty(), boxDimensions));
		changingOrientation.bind(treePageFactory.get().changingOrientationBinding());

		controller.getPagination().pageFactoryProperty().bind(treePageFactory);
		controller.getPagination().pageCountProperty().bind(numberOfPages);

		{
			InvalidationListener invalidationListener = e -> numberOfPages.set(1 + (phyloTrees.size() - 1) / (rowsAndCols.get().rows() * rowsAndCols.get().cols()));
			rowsAndCols.addListener(invalidationListener);
			phyloTrees.addListener(invalidationListener);
			invalidationListener.invalidated(null);
		}

		view.pageNumberProperty().addListener((v, o, n) -> {
			if (n.intValue() < 1)
				Platform.runLater(() -> view.setPageNumber(1));
			else if (n.intValue() >= numberOfPages.get())
				Platform.runLater(() -> view.setPageNumber((Math.max(1, numberOfPages.get()))));
		});

		{
			var labelProperty = new SimpleStringProperty();
			BasicFX.makeMultiStateToggle(controller.getShowTreeNamesToggleButton(), view.getOptionTreeLabels().label(), labelProperty, TreeLabel.labels());
			labelProperty.addListener((v, o, n) -> view.setOptionTreeLabels(TreeLabel.valueOfLabel(n)));
		}

		findToolBar = FindReplaceTaxa.create(mainWindow, view.getUndoManager());
		findToolBar.setShowFindToolBar(false);
		controller.getvBox().getChildren().add(findToolBar);

		// FindReplaceUtils.setup(findToolBar, controller.getFindToggleButton(), true);

		controller.getZoomInButton().setOnAction(e -> this.view.setOptionZoomFactor(1.1 * this.view.getOptionZoomFactor()));
		controller.getZoomInButton().disableProperty().bind(this.view.emptyProperty().or(this.view.optionZoomFactorProperty().greaterThan(4.0 / 1.1)));
		controller.getZoomOutButton().setOnAction(e -> this.view.setOptionZoomFactor((1.0 / 1.1) * this.view.getOptionZoomFactor()));
		controller.getZoomOutButton().disableProperty().bind(this.view.emptyProperty());

		var undoManager = view.getUndoManager();
		rowsAndCols.addListener((v, o, n) -> undoManager.add("grid dimensions", rowsAndCols, o, n));
		view.pageNumberProperty().addListener((c, o, n) -> undoManager.add("page", view.pageNumberProperty(), o, n));
		view.optionDiagramProperty().addListener((v, o, n) -> undoManager.add("diagram type", view.optionDiagramProperty(), o, n));
		view.optionOrientationProperty().addListener((v, o, n) -> undoManager.add("layout orientation", view.optionOrientationProperty(), o, n));
		view.optionTreeLabelsProperty().addListener((v, o, n) -> undoManager.add("show tree names", view.optionTreeLabelsProperty(), o, n));
		view.optionFontScaleFactorProperty().addListener((v, o, n) -> undoManager.add("font size", view.optionFontScaleFactorProperty(), o, n));
		view.optionZoomFactorProperty().addListener((v, o, n) -> undoManager.add("zoom", view.optionZoomFactorProperty(), o, n));

		view.viewTabProperty().addListener((v, o, n) -> {
			if (n != null) {
				Platform.runLater(() -> controller.getvBox().getChildren().add(0, n.getAlgorithmBreadCrumbsToolBar()));
			}
		});
		view.emptyProperty().addListener(e -> view.getRoot().setDisable(view.emptyProperty().get()));

		SwipeUtils.setConsumeSwipes(controller.getAnchorPane());

		Platform.runLater(this::setupMenuItems);
	}

	@Override
	public void setupMenuItems() {
		var mainController = mainWindow.getController();

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

		mainController.getCopyNewickMenuItem().setOnAction(e -> {
			var page = view.getPageNumber();
			var count = view.getOptionCols() * view.getOptionRows();
			var bot = (page - 1) * count;
			var top = Math.min(view.getTrees().size(), page * count);
			var buf = new StringBuilder();
			for (var t = bot; t < top; t++) {
				buf.append(view.getTrees().get(t).toBracketString(true)).append(";\n");
			}
			ClipboardUtils.putString(buf.toString());
		});
		mainController.getCopyNewickMenuItem().disableProperty().bind(view.emptyProperty());

		mainWindow.getController().getIncreaseFontSizeMenuItem().setOnAction(e -> view.setOptionFontScaleFactor(1.2 * view.getOptionFontScaleFactor()));
		mainWindow.getController().getIncreaseFontSizeMenuItem().disableProperty().bind(view.emptyProperty());
		mainWindow.getController().getDecreaseFontSizeMenuItem().setOnAction(e -> view.setOptionFontScaleFactor((1.0 / 1.2) * view.getOptionFontScaleFactor()));
		mainWindow.getController().getDecreaseFontSizeMenuItem().disableProperty().bind(view.emptyProperty());

		mainController.getLayoutLabelsMenuItem().setOnAction(e -> treePageFactory.get().updateLabelLayout(view.getOptionOrientation()));
		mainController.getLayoutLabelsMenuItem().disableProperty().bind(view.emptyProperty().or(view.optionDiagramProperty().isNotEqualTo(TreeDiagramType.RadialPhylogram)));

		mainController.getRotateLeftMenuItem().setOnAction(controller.getRotateLeftButton().getOnAction());
		mainController.getRotateLeftMenuItem().disableProperty().bind(controller.getRotateLeftButton().disableProperty());
		mainController.getRotateRightMenuItem().setOnAction(controller.getRotateRightButton().getOnAction());
		mainController.getRotateRightMenuItem().disableProperty().bind(controller.getRotateRightButton().disableProperty());
		mainController.getFlipMenuItem().setOnAction(controller.getFlipHorizontalButton().getOnAction());
		mainController.getFlipMenuItem().disableProperty().bind(controller.getFlipHorizontalButton().disableProperty());

		ExportUtils.setup(mainWindow, view.getViewTab().getDataNode(), view.emptyProperty());
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

	public FindToolBar getFindToolBar() {
		return findToolBar;
	}

	@Override
	public boolean allowFindReplace() {
		return true;
	}
}
