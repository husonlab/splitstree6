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

import javafx.application.Platform;
import javafx.beans.InvalidationListener;
import javafx.beans.binding.Bindings;
import javafx.collections.*;
import javafx.scene.control.ListView;
import jloda.fx.find.ListViewTypeSearcher;
import jloda.util.Basic;
import splitstree6.algorithms.taxa.taxa2taxa.TaxaFilter;
import splitstree6.data.TaxaBlock;
import splitstree6.data.parts.Taxon;
import splitstree6.tabs.IDisplayTabPresenter;
import splitstree6.window.MainWindow;
import splitstree6.workflow.AlgorithmNode;

import java.util.HashSet;
import java.util.Random;

/**
 * taxa filter presenter
 * Daniel Huson, 11.2021
 */
public class TaxaFilterPresenter implements IDisplayTabPresenter {
	private final MainWindow mainWindow;
	private final TaxaFilterTab tab;
	private final TaxaFilterController controller;

	private final ObservableSet<Taxon> selectedTaxa = FXCollections.observableSet();

	private final TaxaFilter taxaFilter;

	/**
	 * constructor
	 */
	public TaxaFilterPresenter(MainWindow mainWindow, TaxaFilterTab tab, AlgorithmNode<TaxaBlock, TaxaBlock> taxaFilterNode) {
		this.mainWindow = mainWindow;
		this.tab = tab;
		this.taxaFilter = (TaxaFilter) taxaFilterNode.getAlgorithm();
		this.controller = tab.getTaxaFilterController();

		// todo: this addresses the bug that list views don't always reflect change of list content:
		controller.getActiveListView().getItems().addListener((InvalidationListener) e -> {
			var pos = mainWindow.getController().getLeftSplitPane().getDividerPositions()[0];
			var reverseDir = new Random().nextBoolean();
			Platform.runLater(() -> mainWindow.getController().getLeftSplitPane().setDividerPositions(pos + (reverseDir ? -1 : 1) * 0.0001));
		});

		var workflow = mainWindow.getWorkflow();
		var inputTaxa = workflow.getInputTaxaNode().getDataBlock();

		ListViewTypeSearcher.setup(controller.getActiveListView());
		ListViewTypeSearcher.setup(controller.getInactiveListView());

		controller.getMoveSelectedRightButton().setOnAction(e -> {
			var set = new HashSet<>(controller.getActiveListView().getSelectionModel().getSelectedItems());
			controller.getActiveListView().getItems().removeAll(set);
			controller.getInactiveListView().getItems().forEach(set::remove);
			controller.getInactiveListView().getItems().addAll(set);
			sort(controller.getInactiveListView().getItems(), inputTaxa);
			Platform.runLater(() -> {
				controller.getActiveListView().getSelectionModel().clearSelection();
				controller.getInactiveListView().getSelectionModel().clearSelection();
				set.forEach(label -> controller.getInactiveListView().getSelectionModel().select(label));
			});
			Platform.runLater(() -> {
				controller.getInactiveListView().refresh();
			});

		});
		controller.getMoveSelectedRightButton().disableProperty().bind(Bindings.isEmpty(controller.getActiveListView().getSelectionModel().getSelectedItems()));

		controller.getMoveUnselectedRightButton().setOnAction(e -> {
			try {
				var set = new HashSet<>(controller.getActiveListView().getItems());
				selectedTaxa.stream().map(Taxon::getName).forEach(set::remove);
				controller.getActiveListView().getItems().removeAll(set);
				controller.getInactiveListView().getItems().forEach(set::remove);
				controller.getInactiveListView().getItems().addAll(set);
				sort(controller.getInactiveListView().getItems(), inputTaxa);
				controller.getInactiveListView().refresh();
			} catch (Exception ex) {
				Basic.caught(ex);
			}
		});
		controller.getMoveUnselectedRightButton().disableProperty().bind(Bindings.createBooleanBinding(() ->
						controller.getActiveListView().getSelectionModel().getSelectedItems().size() == controller.getActiveListView().getItems().size(),
				controller.getActiveListView().getItems(), controller.getActiveListView().getSelectionModel().getSelectedItems()));

		controller.getMoveSelectedLeftButton().setOnAction(e -> {
			var set = new HashSet<>(controller.getInactiveListView().getSelectionModel().getSelectedItems());
			controller.getInactiveListView().getItems().removeAll(set);
			controller.getActiveListView().getItems().forEach(set::remove);
			controller.getActiveListView().getItems().addAll(set);
			sort(controller.getActiveListView().getItems(), inputTaxa);
			controller.getActiveListView().refresh();
			Platform.runLater(() -> {
				controller.getActiveListView().getSelectionModel().clearSelection();
				set.forEach(label -> controller.getActiveListView().getSelectionModel().select(label));
				controller.getInactiveListView().getSelectionModel().clearSelection();
			});
		});
		controller.getMoveSelectedLeftButton().disableProperty().bind(Bindings.isEmpty(controller.getInactiveListView().getSelectionModel().getSelectedItems()));

		controller.getMoveUnselectedLeftButton().setOnAction(e -> {
			var set = new HashSet<>(controller.getInactiveListView().getItems());
			selectedTaxa.stream().map(Taxon::getName).forEach(set::remove);
			controller.getInactiveListView().getItems().removeAll(set);
			controller.getActiveListView().getItems().forEach(set::remove);
			controller.getActiveListView().getItems().addAll(set);
			sort(controller.getActiveListView().getItems(), inputTaxa);
		});
		controller.getMoveUnselectedLeftButton().disableProperty().bind(Bindings.createBooleanBinding(() ->
						controller.getInactiveListView().getSelectionModel().getSelectedItems().size() == controller.getInactiveListView().getItems().size(),
				controller.getInactiveListView().getItems(), controller.getInactiveListView().getSelectionModel().getSelectedItems()));

		tab.getController().getReset().setOnAction(e -> {
			var set = new HashSet<>(controller.getInactiveListView().getItems());
			controller.getInactiveListView().getItems().clear();
			controller.getActiveListView().getItems().forEach(set::remove);
			controller.getActiveListView().getItems().addAll(set);
			sort(controller.getActiveListView().getItems(), inputTaxa);
		});
		tab.getController().getReset().disableProperty().bind(Bindings.isEmpty(controller.getInactiveListView().getItems()));

		tab.getController().getApplyButton().disableProperty().bind(Bindings.isEmpty(controller.getActiveListView().getItems()));

		setupSelection(mainWindow, controller.getActiveListView(), controller.getInactiveListView());

		controller.getActiveListView().getItems().addListener((InvalidationListener) e -> {
			controller.getActiveLabel().setText(String.format("Active (%d)", controller.getActiveListView().getItems().size()));
		});

		controller.getInactiveListView().getItems().addListener((ListChangeListener<? super String>) e -> {
			while (e.next()) {
				if (e.getAddedSize() > 0)
					taxaFilter.setDisabled(e.getAddedSubList(), true);
				if (e.getRemovedSize() > 0)
					taxaFilter.setDisabled(e.getRemoved(), false);
			}
			controller.getInactiveLabel().setText(String.format("Inactive (%d)", controller.getInactiveListView().getItems().size()));
		});

		updateView();

		workflow.getInputTaxaNode().validProperty().addListener((v, o, n) -> {
			updateView();
		});

		taxaFilterNode.validProperty().addListener((v, o, n) -> {
			if (n) { // make sure taxa selection is applied
				controller.getActiveListView().getSelectionModel().getSelectedItems().stream().map(inputTaxa::get).filter(t -> !selectedTaxa.contains(t)).forEach(selectedTaxa::add);
			}
		});
	}

	public void updateView() {
		var inputTaxa = mainWindow.getWorkflow().getInputTaxaNode().getDataBlock();
		controller.getActiveListView().getItems().setAll(inputTaxa.getLabels());
		for (var label : taxaFilter.getOptionDisabledTaxa()) {
			controller.getActiveListView().getItems().remove(label);
			controller.getInactiveListView().getItems().add(label);
		}
	}

	// todo: need to ensure that this gets called
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

		mainWindow.getController().getSelectAllMenuItem().setOnAction(e -> mainWindow.getTaxonSelectionModel().selectAll(mainWindow.getWorkflow().getWorkingTaxaBlock().getTaxa()));
		mainWindow.getController().getSelectNoneMenuItem().setOnAction(e -> mainWindow.getTaxonSelectionModel().clearSelection());

		controller.getIncreaseFontSizeMenuItem().setOnAction(null);
		controller.getDecreaseFontSizeMenuItem().setOnAction(null);

		controller.getZoomInMenuItem().setOnAction(null);
		controller.getZoomOutMenuItem().setOnAction(null);
	}

	private ListChangeListener<? super String> listSelectionChangeListener;
	private SetChangeListener<? super Taxon> taxonSelectionChangeListener;

	private boolean changing = false;

	private void setupSelection(MainWindow mainWindow, ListView<String> activeListView, ListView<String> inactiveListView) {
		// setup initially selected taxa:
		for (var taxon : mainWindow.getTaxonSelectionModel().getSelectedItems()) {
			if (activeListView.getItems().contains(taxon.getName()))
				activeListView.getSelectionModel().select(taxon.getName());
			else if (inactiveListView.getItems().contains(taxon.getName()))
				inactiveListView.getSelectionModel().select(taxon.getName());
		}

		selectedTaxa.addListener((SetChangeListener<Taxon>) e -> {
			if (e.wasAdded()) {
				var taxon = e.getElementAdded();
				mainWindow.getTaxonSelectionModel().select(taxon);
				var name = taxon.getName();
				if (activeListView.getItems().contains(name))
					activeListView.getSelectionModel().select(name);
				else if (inactiveListView.getItems().contains(name))
					inactiveListView.getSelectionModel().select(name);
			} else if (e.wasRemoved()) {
				var taxon = e.getElementRemoved();
				mainWindow.getTaxonSelectionModel().setSelected(taxon, false);
				var name = taxon.getName();
				if (activeListView.getItems().contains(name)) {
					var index = activeListView.getItems().indexOf(name);
					activeListView.getSelectionModel().clearSelection(index);
				} else if (inactiveListView.getItems().contains(name)) {
					var index = inactiveListView.getItems().indexOf(name);
					inactiveListView.getSelectionModel().clearSelection(index);
				}
			}
		});

		listSelectionChangeListener = e -> {
			if (!changing) {
				changing = true;
				try {
					var taxaBlock = mainWindow.getWorkflow().getWorkingTaxaBlock();
					while (e.next()) {
						if (e.wasAdded()) {
							for (var label : e.getAddedSubList()) {
								var taxon = taxaBlock.get(label);
								if (taxon != null)
									selectedTaxa.add(taxon);
							}
						}
						if (e.wasRemoved()) {
							for (var label : e.getRemoved()) {
								var taxon = taxaBlock.get(label);
								if (taxon != null)
									selectedTaxa.remove(taxon);
							}
						}
					}
				} finally {
					changing = false;
				}
			}
		};
		activeListView.getSelectionModel().getSelectedItems().addListener(new WeakListChangeListener<>(listSelectionChangeListener));
		inactiveListView.getSelectionModel().getSelectedItems().addListener(new WeakListChangeListener<>(listSelectionChangeListener));

		taxonSelectionChangeListener = e -> {
			if (!changing) {
				changing = true;
				try {
					if (e.wasAdded()) {
						selectedTaxa.add(e.getElementAdded());
					} else if (e.wasRemoved()) {
						selectedTaxa.remove(e.getElementRemoved());
					}
				} finally {
					changing = false;
				}
			}
		};
		mainWindow.getTaxonSelectionModel().getSelectedItems().addListener(new WeakSetChangeListener<>(taxonSelectionChangeListener));
	}

	private void sort(ObservableList<String> list, TaxaBlock taxaBlock) {
		list.sort(taxaBlock::compare);
	}
}
