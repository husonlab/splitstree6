/*
 *  MultiTreesViewerPresenter.java Copyright (C) 2021 Daniel H. Huson
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

package splitstree6.viewers.multitreesviewer;

import javafx.application.Platform;
import javafx.beans.InvalidationListener;
import javafx.beans.binding.Bindings;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.TextFormatter;
import javafx.util.converter.IntegerStringConverter;
import jloda.phylo.PhyloTree;
import jloda.util.NumberUtils;
import splitstree6.tabs.IDisplayTabPresenter;
import splitstree6.window.MainWindow;


public class MultiTreesViewerPresenter implements IDisplayTabPresenter {
	private static final ObservableList<String> gridValues = FXCollections.observableArrayList();

	public MultiTreesViewerPresenter(MainWindow mainWindow, MultiTreesViewer multiTreesViewer, ObservableList<PhyloTree> phyloTrees) {
		var controller = multiTreesViewer.getController();

		updateRowsColsFromText(multiTreesViewer.getOptionGrid(), multiTreesViewer.rowsProperty(), multiTreesViewer.colsProperty());

		multiTreesViewer.optionGridProperty().addListener((v, o, n) -> {
			var text = updateRowsColsFromText(n, multiTreesViewer.rowsProperty(), multiTreesViewer.colsProperty());
			Platform.runLater(() -> {
				if (!gridValues.contains(text))
					gridValues.add(0, text);
				Platform.runLater(() -> multiTreesViewer.setOptionGrid(text));
			});
		});

		controller.getRowsColsCBox().valueProperty().bindBidirectional(multiTreesViewer.optionGridProperty());
		controller.getRowsColsCBox().getItems().setAll(gridValues);
		gridValues.addListener((InvalidationListener) e -> controller.getRowsColsCBox().getItems().setAll(gridValues));


		var numberOfTrees = new SimpleIntegerProperty(0);
		numberOfTrees.bind(Bindings.size(phyloTrees));

		var numberOfPages = new SimpleIntegerProperty(0);
		{
			InvalidationListener invalidationListener = e -> numberOfPages.set(1 + (phyloTrees.size() - 1) / (multiTreesViewer.getRows() * multiTreesViewer.getCols()));
			multiTreesViewer.rowsProperty().addListener(invalidationListener);
			multiTreesViewer.colsProperty().addListener(invalidationListener);
			phyloTrees.addListener(invalidationListener);
			invalidationListener.invalidated(null);
		}

		controller.getFirstPageButton().setOnAction(e -> multiTreesViewer.setPageNumber(1));
		controller.getFirstPageButton().disableProperty().bind(multiTreesViewer.isEmptyProperty().or(multiTreesViewer.pageNumberProperty().lessThanOrEqualTo(1)));

		controller.getPreviousPageButton().setOnAction(e -> multiTreesViewer.setPageNumber(multiTreesViewer.getPageNumber() - 1));
		controller.getPreviousPageButton().disableProperty().bind(controller.getFirstPageButton().disableProperty());

		multiTreesViewer.pageNumberProperty().addListener((v, o, n) -> controller.getPageTextField().setText(n.toString()));
		controller.getPageTextField().setTextFormatter(new TextFormatter<>(new IntegerStringConverter()));
		controller.getPageTextField().setOnAction(e -> {
			if (NumberUtils.isInteger(controller.getPageTextField().getText())) {
				var page = NumberUtils.parseInt((controller.getPageTextField().getText()));
				multiTreesViewer.setPageNumber(page);
			}
		});
		multiTreesViewer.pageNumberProperty().addListener((v, o, n) -> {
			if (n.intValue() < 1)
				Platform.runLater(() -> multiTreesViewer.setPageNumber(1));
			else if (n.intValue() >= numberOfPages.get())
				Platform.runLater(() -> multiTreesViewer.setPageNumber((Math.max(1, numberOfPages.get()))));
		});

		controller.getPageTextField().disableProperty().bind(Bindings.size(phyloTrees).lessThanOrEqualTo(1));

		controller.getNextPageButton().setOnAction(e -> multiTreesViewer.setPageNumber(multiTreesViewer.getPageNumber() + 1));
		controller.getNextPageButton().disableProperty().bind(multiTreesViewer.isEmptyProperty().or(multiTreesViewer.pageNumberProperty().greaterThanOrEqualTo(numberOfPages)));

		controller.getLastPage().setOnAction(e -> multiTreesViewer.setPageNumber(phyloTrees.size()));
		controller.getLastPage().disableProperty().bind(controller.getNextPageButton().disableProperty());

		{
			multiTreesViewer.pageNumberProperty().addListener((v, o, n) -> controller.getPagination().setCurrentPageIndex(n.intValue() - 1));
			controller.getPagination().currentPageIndexProperty().addListener((v, o, n) -> multiTreesViewer.setPageNumber(n.intValue() + 1));

			controller.getPagination().setPageFactory(page -> {
				var treePane = new MultiTreesPane(multiTreesViewer);

				Platform.runLater(() -> treePane.addTrees(multiTreesViewer.getTrees(), page + 1));
				multiTreesViewer.setImageNode(treePane);
				return treePane;
			});

			controller.getPagination().pageCountProperty().bind(numberOfPages);
		}

		multiTreesViewer.tabPaneProperty().addListener((v, o, n) -> {
			if (n != null) {
				controller.getPagination().prefWidthProperty().unbind();
				controller.getPagination().prefHeightProperty().unbind();

				controller.getPagination().setPrefWidth(multiTreesViewer.getTabPane().getWidth());
				controller.getPagination().setPrefHeight(multiTreesViewer.getTabPane().getHeight());

				controller.getPagination().prefWidthProperty().bind(n.widthProperty());
				controller.getPagination().prefHeightProperty().bind(n.heightProperty());
			}
		});
	}

	/**
	 * updates the rows and cols from the given text and returns the text nicely formatted
	 *
	 * @param text text to be parsed
	 * @param rows rows to update
	 * @param cols cols to update
	 * @return nicely formatted text
	 */
	private static String updateRowsColsFromText(String text, IntegerProperty rows, IntegerProperty cols) {
		try {
			var tokens = text.split("x");
			rows.set(Integer.parseInt(tokens[0].trim()));
			cols.set(Integer.parseInt(tokens[1].trim()));
		} catch (Exception ignored) {
		}
		return String.format("%d x %d", rows.get(), cols.get());
	}


	@Override
	public void setupMenuItems() {

	}
}
