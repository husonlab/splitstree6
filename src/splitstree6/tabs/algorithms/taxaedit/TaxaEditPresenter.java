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

import javafx.application.Platform;
import javafx.beans.InvalidationListener;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableSet;
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

	private final ObservableSet<Taxon> selectedTaxa = FXCollections.observableSet();

	private final TaxaEditor taxaEditor;

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
			while (e.next()) {
				for (var item : e.getAddedSubList()) {
					if (item.isActive())
						Platform.runLater(() -> selectedTaxa.add(item.getTaxon()));
				}
				for (var item : e.getRemoved()) {
					if (item.isActive())
						Platform.runLater(() -> selectedTaxa.remove(item.getTaxon()));
				}
			}
		});

		mainWindow.getTaxonSelectionModel().getSelectedItems().addListener((SetChangeListener<? super Taxon>) e -> {
			if (e.wasAdded())
				Platform.runLater(() -> selectedTaxa.add(e.getElementAdded()));
			if (e.wasRemoved())
				Platform.runLater(() -> selectedTaxa.remove(e.getElementRemoved()));
		});

		selectedTaxa.addListener((SetChangeListener<? super Taxon>) e -> {
			if (e.wasAdded()) {
				mainWindow.getTaxonSelectionModel().select(e.getElementAdded());
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
		});

		controller.getActivateAllMenuItem().setOnAction(e -> {
			for (var item : tableView.getItems()) {
				item.setActive(true);
			}
		});

		controller.getActivateNoneMenuItem().setOnAction(e -> {
			for (var item : tableView.getItems()) {
				item.setActive(false);
			}
		});

		controller.getActivateSelectedMenuItem().setOnAction(e -> {
			for (var item : tableView.getSelectionModel().getSelectedItems()) {
				item.setActive(true);
			}
		});

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
		}
		updateColumnWidths(tableView, controller.getDisplayLabelColumn());
	}

	@Override
	public void setupMenuItems() {

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
