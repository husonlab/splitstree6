/*
 *  TaxaEditPresenter.java Copyright (C) 2021 Daniel H. Huson
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

package splitstree6.tabs.algorithms.taxaedit;

import javafx.beans.InvalidationListener;
import javafx.beans.binding.Bindings;
import javafx.collections.ListChangeListener;
import javafx.collections.SetChangeListener;
import javafx.scene.control.Label;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.util.converter.DefaultStringConverter;
import jloda.fx.control.RichTextLabel;
import jloda.fx.find.FindToolBar;
import jloda.fx.find.TableColumnSearcher;
import splitstree6.algorithms.taxa.taxa2taxa.TaxaEditor;
import splitstree6.data.TaxaBlock;
import splitstree6.data.parts.Taxon;
import splitstree6.tabs.IDisplayTabPresenter;
import splitstree6.window.MainWindow;
import splitstree6.workflow.AlgorithmNode;

/**
 * taxa edit presenter
 * Daniel Huson, 11.2021
 */
public class TaxaEditPresenter implements IDisplayTabPresenter {
	private final MainWindow mainWindow;
	private final TaxaEditTab tab;
	private final TaxaEditController controller;

	private final FindToolBar findToolBar;

	private final TaxaEditor taxaEditor;

	private boolean inSelection = false;

	/**
	 * constructor
	 */
	public TaxaEditPresenter(MainWindow mainWindow, TaxaEditTab tab, AlgorithmNode<TaxaBlock, TaxaBlock> taxaFilterNode) {
		this.mainWindow = mainWindow;
		this.tab = tab;
		this.taxaEditor = (TaxaEditor) taxaFilterNode.getAlgorithm();
		this.controller = tab.getTaxaFilterController();

		var inputTaxonBlock = mainWindow.getWorkflow().getInputTaxonBlock();

		final InvalidationListener activeChangedListener = e -> {
			if (taxaEditor.getNumberDisabledTaxa() == 0)
				controller.getInfoLabel().setText("Active: " + inputTaxonBlock.getNtax());
			else
				controller.getInfoLabel().setText(String.format("Active: %d (of %d)",
						(inputTaxonBlock.getNtax() - taxaEditor.getNumberDisabledTaxa()), inputTaxonBlock.getNtax()));
		};
		taxaEditor.optionDisabledTaxaProperty().addListener(activeChangedListener);
		activeChangedListener.invalidated(null);

		var tableView = controller.getTableView();

		tableView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);

		controller.getIdColumn().setCellValueFactory(new PropertyValueFactory<>("Id"));

		tableView.setEditable(true);
		controller.getActiveColumn().setEditable(true);
		controller.getActiveColumn().setCellValueFactory(item -> item.getValue().activeProperty());
		controller.getActiveColumn().setCellFactory(CheckBoxTableCell.forTableColumn(controller.getActiveColumn()));

		controller.getNameColumn().setCellValueFactory(new PropertyValueFactory<>("Name"));

		controller.getDisplayLabelColumn().setEditable(true);
		controller.getDisplayLabelColumn().setCellValueFactory(item -> item.getValue().displayLabelProperty());
		controller.getDisplayLabelColumn().setCellFactory(TextFieldTableCell.forTableColumn(new DefaultStringConverter()));
		tableView.widthProperty().addListener(c -> updateColumnWidths(tableView, controller.getDisplayLabelColumn()));

		tableView.getSelectionModel().getSelectedItems().addListener((ListChangeListener<? super TaxaEditTableItem>) e -> {
			if (!inSelection) {
				inSelection = true;
				try {
					while (e.next()) {
						for (var item : e.getAddedSubList()) {
							if (item.isActive())
								mainWindow.getTaxonSelectionModel().select(item.getTaxon());
						}
						for (var item : e.getRemoved()) {
							if (item.isActive())
								mainWindow.getTaxonSelectionModel().clearSelection(item.getTaxon());
						}
					}
				} finally {
					inSelection = false;
				}
			}
		});

		mainWindow.getTaxonSelectionModel().getSelectedItems().addListener((SetChangeListener<? super Taxon>) e -> {
			if (!inSelection) {
				inSelection = true;
				try {
					if (e.wasAdded()) {
						for (var item : controller.getTableView().getItems()) {
							if (item.getTaxon().getName().equals(e.getElementAdded().getName())) {
								controller.getTableView().getSelectionModel().select(controller.getTableView().getItems().indexOf(item));
								break;
							}
						}
					}
					if (e.wasRemoved()) {
						mainWindow.getTaxonSelectionModel().clearSelection(e.getElementRemoved());
						for (var item : controller.getTableView().getItems()) {
							if (item.getTaxon().getName().equals(e.getElementRemoved().getName())) {
								controller.getTableView().getSelectionModel().clearSelection(controller.getTableView().getItems().indexOf(item));
								break;
							}
						}
					}
				} finally {
					inSelection = false;
				}
			}
		});

		findToolBar = new FindToolBar(mainWindow.getStage(), new TableColumnSearcher<>(controller.getDisplayLabelColumn(), TaxaEditTableItem::setDisplayLabel));
		findToolBar.showReplaceToolBarProperty().bindBidirectional(controller.getFindAndReplaceRadioMenuItem().selectedProperty());

		controller.getTopVBox().getChildren().add(0, findToolBar);

		//controller.getActivateAllMenuItem().setDisable(taxaEditor.getNumberDisabledTaxa()==0);
		controller.getActivateAllMenuItem().setOnAction(e -> {
			for (var item : tableView.getItems()) {
				item.setActive(true);
			}
		});
		controller.getActivateAllMenuItem().disableProperty().bind(Bindings.createObjectBinding(
				() -> taxaEditor.getNumberDisabledTaxa() == 0, taxaEditor.optionDisabledTaxaProperty()));

		controller.getActivateNoneMenuItem().setOnAction(e -> {
			for (var item : tableView.getItems()) {
				item.setActive(false);
			}
		});
		controller.getActivateNoneMenuItem().disableProperty().bind(Bindings.createObjectBinding(
				() -> taxaEditor.getNumberDisabledTaxa() == inputTaxonBlock.getNtax(), taxaEditor.optionDisabledTaxaProperty()));


		controller.getActivateSelectedMenuItem().setOnAction(e -> {
			for (var item : tableView.getSelectionModel().getSelectedItems()) {
				item.setActive(true);
			}
		});
		controller.getActivateSelectedMenuItem().disableProperty().bind(Bindings.isEmpty(tableView.getSelectionModel().getSelectedItems()));

		controller.getShowHTMLInfoMenuItem().selectedProperty().addListener((v, o, n) -> {
			controller.getHtmlInfoFlowPane().getChildren().clear();
			if (n) {
				controller.getHtmlInfoFlowPane().getChildren().add(new Label("Supported HTML tags:"));
				for (var word : RichTextLabel.getSupportedHTMLTags().split("\s+")) {
					controller.getHtmlInfoFlowPane().getChildren().add(new Label(word));
				}
			}
			controller.getHtmlInfoFlowPane().requestLayout();
		});
		controller.getShowHTMLInfoMenuItem().setSelected(false);

		updateView();

		mainWindow.getWorkflow().getInputTaxaNode().validProperty().addListener((v, o, n) -> {
			updateView();
		});
	}


	public void updateView() {
		var tableView = controller.getTableView();
		var inputTaxa = mainWindow.getWorkflow().getInputTaxaNode().getDataBlock();
		tableView.getItems().clear();
		for (int t = 1; t <= inputTaxa.getNtax(); t++) {
			var taxon = inputTaxa.get(t);
			var item = new TaxaEditTableItem(t, taxon);
			item.setActive(!taxaEditor.isDisabled(taxon.getName()));
			item.activeProperty().addListener((v, o, n) -> {
				taxaEditor.setDisabled(taxon.getName(), !n);
				controller.getTableView().refresh();
			});
			tableView.getItems().add(item);

			item.displayLabelProperty().addListener((v, o, n) ->
					tab.getUndoManager().add("edit label", item.displayLabelProperty(), o, n));
		}
		updateColumnWidths(tableView, controller.getDisplayLabelColumn());
	}

	@Override
	public void setupMenuItems() {
		var mainWindowController = mainWindow.getController();

		mainWindowController.getUndoMenuItem().textProperty().bind(tab.getUndoManager().undoNameProperty());
		mainWindowController.getUndoMenuItem().setOnAction(e -> tab.getUndoManager().undo());
		mainWindowController.getUndoMenuItem().disableProperty().bind(tab.getUndoManager().undoableProperty().not());

		mainWindowController.getRedoMenuItem().textProperty().bind(tab.getUndoManager().redoNameProperty());
		mainWindowController.getRedoMenuItem().setOnAction(e -> tab.getUndoManager().redo());
		mainWindowController.getRedoMenuItem().disableProperty().bind(tab.getUndoManager().redoableProperty().not());
	}

	private static void updateColumnWidths(TableView<TaxaEditTableItem> tableView, TableColumn<TaxaEditTableItem, String> displayCol) {
		double newLayoutTableColumnWidth = tableView.getWidth() - 20;
		for (var col : tableView.getColumns()) {
			if (col != displayCol && col.isVisible())
				newLayoutTableColumnWidth -= (col.getWidth() + 5);
		}
		if (newLayoutTableColumnWidth > displayCol.getWidth())
			displayCol.setPrefWidth(newLayoutTableColumnWidth);
	}
}
