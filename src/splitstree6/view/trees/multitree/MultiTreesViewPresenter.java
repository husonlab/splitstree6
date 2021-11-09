/*
 *  MultiTreesViewPresenter.java Copyright (C) 2021 Daniel H. Huson
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

package splitstree6.view.trees.multitree;

import javafx.application.Platform;
import javafx.beans.InvalidationListener;
import javafx.beans.binding.Bindings;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.TextFormatter;
import javafx.util.converter.IntegerStringConverter;
import jloda.phylo.PhyloTree;
import jloda.util.NumberUtils;
import splitstree6.tabs.IDisplayTabPresenter;
import splitstree6.window.MainWindow;


public class MultiTreesViewPresenter implements IDisplayTabPresenter {
	private static final ObservableList<String> gridValues = FXCollections.observableArrayList();

	public MultiTreesViewPresenter(MainWindow mainWindow, MultiTreesView multiTreesView, ObservableList<PhyloTree> phyloTrees) {
		var controller = multiTreesView.getController();

		controller.getDiagramCBox().getItems().addAll(TreePane.Diagram.values());
		controller.getDiagramCBox().valueProperty().bindBidirectional(multiTreesView.optionDiagramProperty());
		multiTreesView.optionDiagramProperty().addListener(e -> redraw(controller));

		controller.getRootSideCBox().getItems().addAll(TreePane.RootSide.values());
		controller.getRootSideCBox().valueProperty().bindBidirectional(multiTreesView.optionRootSideProperty());
		controller.getRootSideCBox().disableProperty().bind(multiTreesView.optionDiagramProperty().isEqualTo(TreePane.Diagram.Unrooted)
				.or((multiTreesView.optionDiagramProperty().isEqualTo(TreePane.Diagram.Circular))));
		multiTreesView.optionRootSideProperty().addListener(e -> redraw(controller));

		controller.getToScaleToggleButton().selectedProperty().bindBidirectional(multiTreesView.optionToScaleProperty());
		multiTreesView.optionToScaleProperty().addListener(e -> redraw(controller));

		updateRowsColsFromText(multiTreesView.getOptionGrid(), multiTreesView.rowsProperty(), multiTreesView.colsProperty());

		multiTreesView.optionGridProperty().addListener((v, o, n) -> {
			var text = updateRowsColsFromText(n, multiTreesView.rowsProperty(), multiTreesView.colsProperty());
			Platform.runLater(() -> {
				if (!gridValues.contains(text))
					gridValues.add(0, text);
				Platform.runLater(() -> multiTreesView.setOptionGrid(text));
			});
		});

		controller.getRowsColsCBox().valueProperty().bindBidirectional(multiTreesView.optionGridProperty());
		controller.getRowsColsCBox().getItems().setAll(gridValues);
		gridValues.addListener((InvalidationListener) e -> controller.getRowsColsCBox().getItems().setAll(gridValues));

		var numberOfTrees = new SimpleIntegerProperty(0);
		numberOfTrees.bind(Bindings.size(phyloTrees));
		var numberOfPages = new SimpleIntegerProperty(0);

		{
			multiTreesView.optionPageNumberProperty().addListener((v, o, n) -> controller.getPagination().setCurrentPageIndex(n.intValue() - 1));
			controller.getPagination().currentPageIndexProperty().addListener((v, o, n) -> multiTreesView.setOptionPageNumber(n.intValue() + 1));

			var hGap = 10.0;
			var vGap = 10.0;
			var boxWidth = new SimpleDoubleProperty();
			boxWidth.bind((controller.getAnchorPane().widthProperty().divide(multiTreesView.rowsProperty())).subtract(hGap));
			var boxHeight = new SimpleDoubleProperty();
			boxHeight.bind((controller.getAnchorPane().heightProperty().divide(multiTreesView.colsProperty())).subtract(vGap));

			controller.getAnchorPane().widthProperty().addListener((v, o, n) -> System.err.println("anchor width: " + n));
			controller.getAnchorPane().heightProperty().addListener((v, o, n) -> System.err.println("anchor height: " + n));

			controller.getPagination().setPageFactory(page -> {
				var treePane = new MultiTreesPane(multiTreesView, boxWidth, boxHeight);
				treePane.setHgap(hGap);
				treePane.setVgap(vGap);
				treePane.prefWidthProperty().bind(controller.getPagination().widthProperty());
				treePane.prefHeightProperty().bind(controller.getPagination().heightProperty());

				Platform.runLater(() -> treePane.addTrees(mainWindow.getWorkflow().getWorkingTaxaBlock(), multiTreesView.getTrees(), page + 1));

				multiTreesView.setImageNode(treePane);
				return treePane;
			});

			controller.getPagination().pageCountProperty().bind(numberOfPages);

			multiTreesView.tabPaneProperty().addListener((v, o, n) -> {
				if (n != null) {
					controller.getAnchorPane().maxWidthProperty().bind(n.widthProperty());
					controller.getPagination().prefWidthProperty().bind(n.widthProperty());
					controller.getAnchorPane().maxHeightProperty().bind(n.heightProperty());
					controller.getPagination().prefHeightProperty().bind(n.heightProperty());
				}
			});
		}

		{
			InvalidationListener invalidationListener = e -> numberOfPages.set(1 + (phyloTrees.size() - 1) / (multiTreesView.getRows() * multiTreesView.getCols()));
			multiTreesView.rowsProperty().addListener(invalidationListener);
			multiTreesView.colsProperty().addListener(invalidationListener);
			phyloTrees.addListener(invalidationListener);
			invalidationListener.invalidated(null);
		}

		{
			controller.getFirstPageButton().setOnAction(e -> multiTreesView.setOptionPageNumber(1));
			controller.getFirstPageButton().disableProperty().bind(multiTreesView.emptyProperty().or(multiTreesView.optionPageNumberProperty().lessThanOrEqualTo(1)));

			controller.getPreviousPageButton().setOnAction(e -> multiTreesView.setOptionPageNumber(multiTreesView.getOptionPageNumber() - 1));
			controller.getPreviousPageButton().disableProperty().bind(controller.getFirstPageButton().disableProperty());

			multiTreesView.optionPageNumberProperty().addListener((v, o, n) -> controller.getPageTextField().setText(n.toString()));
			controller.getPageTextField().setTextFormatter(new TextFormatter<>(new IntegerStringConverter()));
			controller.getPageTextField().setText(String.valueOf(multiTreesView.getOptionPageNumber()));
			controller.getPageTextField().setOnAction(e -> {
				if (NumberUtils.isInteger(controller.getPageTextField().getText())) {
					var page = NumberUtils.parseInt((controller.getPageTextField().getText()));
					multiTreesView.setOptionPageNumber(page);
				}
			});
			multiTreesView.optionPageNumberProperty().addListener((v, o, n) -> {
				if (n.intValue() < 1)
					Platform.runLater(() -> multiTreesView.setOptionPageNumber(1));
				else if (n.intValue() >= numberOfPages.get())
					Platform.runLater(() -> multiTreesView.setOptionPageNumber((Math.max(1, numberOfPages.get()))));
			});

			controller.getPageTextField().disableProperty().bind(Bindings.size(phyloTrees).lessThanOrEqualTo(1));

			controller.getNextPageButton().setOnAction(e -> multiTreesView.setOptionPageNumber(multiTreesView.getOptionPageNumber() + 1));
			controller.getNextPageButton().disableProperty().bind(multiTreesView.emptyProperty().or(multiTreesView.optionPageNumberProperty().greaterThanOrEqualTo(numberOfPages)));

			controller.getLastPage().setOnAction(e -> multiTreesView.setOptionPageNumber(phyloTrees.size()));
			controller.getLastPage().disableProperty().bind(controller.getNextPageButton().disableProperty());
		}
	}

	private void redraw(MultiTreesViewController controller) {
		var pageFactory = controller.getPagination().getPageFactory();
		var pageIndex = controller.getPagination().getCurrentPageIndex();
		Platform.runLater(() -> {
			controller.getPagination().setPageFactory(null);
			controller.getPagination().setPageFactory(pageFactory);
			controller.getPagination().setCurrentPageIndex(pageIndex);
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
