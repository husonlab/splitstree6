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
import javafx.beans.property.SimpleIntegerProperty;
import javafx.collections.ObservableList;
import javafx.scene.control.TextFormatter;
import javafx.util.converter.IntegerStringConverter;
import jloda.phylo.PhyloTree;
import jloda.util.NumberUtils;
import splitstree6.tabs.IDisplayTabPresenter;
import splitstree6.window.MainWindow;


public class MultiTreesViewerPresenter implements IDisplayTabPresenter {
	private final MultiTreesViewer multiTreesViewer;

	public MultiTreesViewerPresenter(MainWindow mainWindow, MultiTreesViewer multiTreesViewer, ObservableList<PhyloTree> phyloTrees) {
		this.multiTreesViewer = multiTreesViewer;
		var controller = multiTreesViewer.getController();

		controller.getRowsColsCBox().setOnAction(e -> {
			try {
				var tokens = controller.getPageTextField().getText().split("x");
				multiTreesViewer.setRows(Integer.parseInt(tokens[0].trim()));
				multiTreesViewer.setCols(Integer.parseInt(tokens[1].trim()));
				var text = String.format("%d x %d", multiTreesViewer.getRows(), multiTreesViewer.getCols());
				if (!controller.getRowsColsCBox().getItems().contains(text))
					Platform.runLater(() -> controller.getRowsColsCBox().getItems().add(text));
			} catch (Exception ex) {
				var text = String.format("%d x %d", multiTreesViewer.getRows(), multiTreesViewer.getCols());
				Platform.runLater(() -> controller.getRowsColsCBox().setValue(text));
			}
		});

		controller.getRowsColsCBox().valueProperty().bindBidirectional(multiTreesViewer.optionGridProperty());

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
		controller.getPageTextField().textProperty().addListener((v, o, n) -> {
			if (NumberUtils.isInteger(n)) {
				var page = NumberUtils.parseInt(n);
				if (page >= 1 && page <= phyloTrees.size()) {
					multiTreesViewer.setPageNumber(page);
				}
			}
		});
		controller.getPageTextField().disableProperty().bind(Bindings.size(phyloTrees).lessThanOrEqualTo(1));

		controller.getNextPageButton().setOnAction(e -> multiTreesViewer.setPageNumber(multiTreesViewer.getPageNumber() + 1));
		controller.getNextPageButton().disableProperty().bind(multiTreesViewer.isEmptyProperty().or(multiTreesViewer.pageNumberProperty().greaterThanOrEqualTo(numberOfPages)));

		controller.getLastPage().setOnAction(e -> multiTreesViewer.setPageNumber(phyloTrees.size()));
		controller.getLastPage().disableProperty().bind(controller.getNextPageButton().disableProperty());

		{

		}
	}

	@Override
	public void setup() {

	}
}
