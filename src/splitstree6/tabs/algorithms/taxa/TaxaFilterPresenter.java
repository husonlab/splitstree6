/*
 *  TaxaFilterPresenter.java Copyright (C) 2021 Daniel H. Huson
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

package splitstree6.tabs.algorithms.taxa;

import javafx.beans.binding.Bindings;
import javafx.collections.ListChangeListener;
import javafx.scene.control.SelectionMode;
import jloda.fx.find.ListViewTypeSearcher;
import splitstree6.algorithms.taxa.taxa2taxa.TaxaFilter;
import splitstree6.tabs.IDisplayTabPresenter;
import splitstree6.window.MainWindow;

import java.util.ArrayList;

public class TaxaFilterPresenter implements IDisplayTabPresenter {
	private final MainWindow mainWindow;
	private final TaxaFilterTab tab;

	public TaxaFilterPresenter(MainWindow mainWindow, TaxaFilterTab tab) {
		this.mainWindow = mainWindow;
		this.tab = tab;

		var controller = tab.getTaxaFilterController();

		var workflow = mainWindow.getWorkflow();
		var inputTaxa = workflow.getInputTaxaNode().getDataBlock();
		var taxaFilter = (TaxaFilter) tab.getAlgorithmNode().getAlgorithm();

		controller.getActiveListView().getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
		ListViewTypeSearcher.setup(controller.getActiveListView());
		controller.getInactiveListView().getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
		ListViewTypeSearcher.setup(controller.getInactiveListView());

		controller.getActiveListView().getItems().setAll(inputTaxa.getLabels());

		workflow.getInputTaxaNode().validProperty().addListener((v, o, n) -> {
			controller.getActiveListView().getItems().setAll(inputTaxa.getLabels());
			controller.getInactiveListView().getItems().clear();
		});

		controller.getInactiveListView().getItems().addListener((ListChangeListener<? super String>) e -> {
			while (e.next()) {
				if (e.getAddedSize() > 0)
					taxaFilter.setDisabled(e.getAddedSubList(), true);
				if (e.getRemovedSize() > 0)
					taxaFilter.setDisabled(e.getRemoved(), false);
			}
		});

		controller.getMoveAllLeftButton().setOnAction(e -> {
			var list = new ArrayList<>(controller.getInactiveListView().getItems());
			controller.getInactiveListView().getItems().clear();
			controller.getActiveListView().getItems().addAll(list);
		});
		controller.getMoveAllLeftButton().disableProperty().bind(Bindings.isEmpty(controller.getInactiveListView().getItems()));

		controller.getMoveSelectedLeftButton().setOnAction(e -> {
			var list = new ArrayList<>(controller.getInactiveListView().getSelectionModel().getSelectedItems());
			controller.getInactiveListView().getItems().removeAll(list);
			controller.getActiveListView().getItems().addAll(list);

		});
		controller.getMoveSelectedLeftButton().disableProperty().bind(Bindings.isEmpty(controller.getInactiveListView().getSelectionModel().getSelectedItems()));

		controller.getMoveAllRightButton().setOnAction(e -> {
			var list = new ArrayList<>(controller.getActiveListView().getItems());
			controller.getActiveListView().getItems().clear();
			controller.getInactiveListView().getItems().addAll(list);
		});
		controller.getMoveAllRightButton().disableProperty().bind(Bindings.isEmpty(controller.getActiveListView().getItems()));

		controller.getMoveSelectedRightButton().setOnAction(e -> {
			var list = new ArrayList<>(controller.getActiveListView().getSelectionModel().getSelectedItems());
			controller.getActiveListView().getItems().removeAll(list);
			controller.getInactiveListView().getItems().addAll(list);
		});
		controller.getMoveSelectedRightButton().disableProperty().bind(Bindings.isEmpty(controller.getActiveListView().getSelectionModel().getSelectedItems()));

		tab.getController().getReset().setOnAction(controller.getMoveAllLeftButton().getOnAction());
		tab.getController().getReset().disableProperty().bind(controller.getMoveAllLeftButton().disableProperty());

		tab.getController().getApplyButton().disableProperty().bind(Bindings.isEmpty(controller.getActiveListView().getItems()));
	}

	public void setupMenuItems() {

		var controller = mainWindow.getController();

		controller.getCutMenuItem().setOnAction(null);
		controller.getCopyMenuItem().setOnAction(null);

		controller.getUndoMenuItem().setOnAction(e -> tab.getUndoManager().undo());
		controller.getUndoMenuItem().disableProperty().bind(tab.getUndoManager().undoableProperty().not());
		controller.getRedoMenuItem().setOnAction(e -> tab.getUndoManager().redo());
		controller.getRedoMenuItem().disableProperty().bind(tab.getUndoManager().redoableProperty().not());

		controller.getPasteMenuItem().setOnAction(null);

		controller.getFindMenuItem().setOnAction(null);
		controller.getFindAgainMenuItem().setOnAction(null);

		// controller.getReplaceMenuItem().setOnAction(null);

		controller.getSelectAllMenuItem().setOnAction(null);
		controller.getSelectNoneMenuItem().setOnAction(null);

		controller.getIncreaseFontSizeMenuItem().setOnAction(null);
		controller.getDecreaseFontSizeMenuItem().setOnAction(null);

		controller.getZoomInMenuItem().setOnAction(null);
		controller.getZoomOutMenuItem().setOnAction(null);
	}
}
