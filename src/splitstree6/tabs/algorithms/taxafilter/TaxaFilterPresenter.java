/*
 * TaxaFilterPresenter.java Copyright (C) 2022 Daniel H. Huson
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

package splitstree6.tabs.algorithms.taxafilter;

import javafx.application.Platform;
import javafx.beans.InvalidationListener;
import javafx.beans.WeakInvalidationListener;
import javafx.beans.binding.Bindings;
import javafx.collections.ListChangeListener;
import javafx.collections.SetChangeListener;
import javafx.scene.control.*;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.util.converter.DefaultStringConverter;
import jloda.fx.control.RichTextLabel;
import jloda.fx.find.FindToolBar;
import jloda.fx.window.MainWindowManager;
import splitstree6.algorithms.taxa.taxa2taxa.TaxaFilter;
import splitstree6.data.TaxaBlock;
import splitstree6.data.parts.Taxon;
import splitstree6.tabs.IDisplayTabPresenter;
import splitstree6.view.findreplace.FindReplaceTaxa;
import splitstree6.window.MainWindow;
import splitstree6.workflow.AlgorithmNode;

import java.util.Objects;

/**
 * taxa edit presenter
 * Daniel Huson, 11.2021
 */
public class TaxaFilterPresenter implements IDisplayTabPresenter {
	private final MainWindow mainWindow;
	private final TaxaFilterTab tab;
	private final TaxaFilterController controller;

	private final FindToolBar findToolBar;

	private final TaxaFilter taxaFilter;

	private boolean inSelection = false;

	private final InvalidationListener activeChangedListener;

	/**
	 * constructor
	 */
	public TaxaFilterPresenter(MainWindow mainWindow, TaxaFilterTab tab, AlgorithmNode<TaxaBlock, TaxaBlock> taxaEditorNode) {
		this.mainWindow = mainWindow;
		this.tab = tab;
		this.taxaFilter = (TaxaFilter) taxaEditorNode.getAlgorithm();
		this.controller = tab.getTaxaFilterController();

		var inputTaxonBlock = mainWindow.getWorkflow().getInputTaxaBlock();
		var workingTaxonBlock = mainWindow.getWorkflow().getWorkingTaxaBlock();

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

		tableView.getSelectionModel().getSelectedItems().addListener((ListChangeListener<? super TaxaFilterTableItem>) e -> {
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
								var index = controller.getTableView().getItems().indexOf(item);
								if (index >= 0) {
									controller.getTableView().getSelectionModel().select(index);
									Platform.runLater(() -> tableView.scrollTo(index));
									break;
								}
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

		findToolBar = FindReplaceTaxa.create(mainWindow, tab.getUndoManager());
		findToolBar.setShowFindToolBar(false);
		controller.getFindAndReplaceRadioMenuItem().setOnAction(e -> findToolBar.setShowReplaceToolBar(!findToolBar.isShowReplaceToolBar()));

		controller.getTopVBox().getChildren().add(0, findToolBar);

		//controller.getActivateAllMenuItem().setDisable(taxaFilter.getNumberDisabledTaxa()==0);
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
		controller.getActivateSelectedMenuItem().disableProperty().bind(Bindings.isEmpty(tableView.getSelectionModel().getSelectedItems()));

		controller.getDeactivateSelectedMenuItem().setOnAction(e -> {
			for (var item : tableView.getSelectionModel().getSelectedItems()) {
				item.setActive(false);
			}
		});
		controller.getDeactivateSelectedMenuItem().disableProperty().bind(Bindings.isEmpty(tableView.getSelectionModel().getSelectedItems()));

		controller.getSelectCurrentlyActiveMenuItem().setOnAction(e -> tableView.getItems().stream()
				.filter(item -> workingTaxonBlock.getTaxa().contains(item.getTaxon()))
				.forEach(item -> tableView.getSelectionModel().select(item)));

		controller.getSelectActivatedMenuItem().setOnAction(e -> tableView.getItems().stream()
				.filter(TaxaFilterTableItem::isActive).forEach(item -> tableView.getSelectionModel().select(item)));

		controller.getShowHTMLInfoMenuItem().selectedProperty().addListener((v, o, n) -> {
			controller.getHtmlInfoFlowPane().getChildren().clear();
			if (n) {
				var firstLabel = new Label("Display label HTML tags:");
				firstLabel.setStyle("-fx-text-fill: darkgray;");
				controller.getHtmlInfoFlowPane().getChildren().add(firstLabel);
				for (var word : RichTextLabel.getSupportedHTMLTags().split("\s+")) {
					var label = new Label(word);
					label.setStyle("-fx-text-fill: darkgray;");
					controller.getHtmlInfoFlowPane().getChildren().add(label);
				}
			}
			controller.getHtmlInfoFlowPane().requestLayout();
		});
		controller.getShowHTMLInfoMenuItem().setSelected(false);

		updateView();

		mainWindow.getWorkflow().getInputTaxaNode().validProperty().addListener((v, o, n) -> updateView());

		activeChangedListener = e -> {
			var label = (tableView.getSelectionModel().getSelectedItems().size() > 0 ? String.format("Selected: %d, ", tableView.getSelectionModel().getSelectedItems().size()) : "");
			label += String.format("Active: %d (%d in use), total: %d", (inputTaxonBlock.getNtax() - taxaFilter.getNumberDisabledTaxa()), workingTaxonBlock.getNtax(), inputTaxonBlock.getNtax());
			controller.getInfoLabel().setText(label);
		};

		tableView.getSelectionModel().getSelectedItems().addListener(new WeakInvalidationListener(activeChangedListener));
		taxaFilter.optionDisabledTaxaProperty().addListener(new WeakInvalidationListener(activeChangedListener));
		taxaEditorNode.validProperty().addListener(new WeakInvalidationListener(activeChangedListener));
		activeChangedListener.invalidated(null);

		setupEditMenuButton(tab.getController().getMenuButton(), controller.getActiveColumn().getContextMenu(), controller.getDisplayLabelColumn().getContextMenu());

	}

	public static void setupEditMenuButton(MenuButton menuButton, ContextMenu... sourceMenus) {
		for (var contextMenu : sourceMenus) {
			if (menuButton.getItems().size() > 0)
				menuButton.getItems().add(new SeparatorMenuItem());

			for (var menuItem : contextMenu.getItems()) {
				if (menuItem instanceof SeparatorMenuItem) {
					menuButton.getItems().add(new SeparatorMenuItem());
				} else if (menuItem instanceof RadioMenuItem radioMenuItem) {
					var newMenuItem = new RadioMenuItem(menuItem.getText());
					newMenuItem.selectedProperty().bindBidirectional(radioMenuItem.selectedProperty());
					newMenuItem.disableProperty().bind(menuItem.disableProperty());
					menuButton.getItems().add(newMenuItem);
				} else {
					var newMenuItem = new MenuItem(menuItem.getText());
					newMenuItem.setOnAction(menuItem.getOnAction());
					newMenuItem.disableProperty().bind(menuItem.disableProperty());
					menuButton.getItems().add(newMenuItem);
				}
			}
		}
		menuButton.setVisible(true);
	}

	private boolean updatingActive = false;

	public void updateView() {

		var tableView = controller.getTableView();
		var inputTaxa = mainWindow.getWorkflow().getInputTaxaNode().getDataBlock();
		tableView.getItems().clear();
		for (int t = 1; t <= inputTaxa.getNtax(); t++) {
			var taxon = inputTaxa.get(t);
			var item = new TaxaFilterTableItem(t, taxon);
			item.setActive(!taxaFilter.isDisabled(taxon.getName()));
			item.activeProperty().addListener((v, o, n) -> {
				taxaFilter.setDisabled(taxon.getName(), !n);
				if (!updatingActive && mainWindow.getTaxonSelectionModel().isSelected(taxon)) {
					updatingActive = true;
					if (n)
						controller.getActivateSelectedMenuItem().getOnAction().handle(null);
					else
						controller.getDeactivateSelectedMenuItem().getOnAction().handle(null);
					updatingActive = false;
				}
				controller.getTableView().refresh();
			});
			tableView.getItems().add(item);

			item.displayLabelProperty().addListener((v, o, n) -> {
				tab.getUndoManager().add("edit label", item.displayLabelProperty(), o, n);
				mainWindow.setDirty(true);
			});
		}
		updateColumnWidths(tableView, controller.getDisplayLabelColumn());
	}

	@Override
	public void setupMenuItems() {
		var mainController = mainWindow.getController();

		mainController.getUndoMenuItem().textProperty().bind(tab.getUndoManager().undoNameProperty());
		mainController.getUndoMenuItem().setOnAction(e -> tab.getUndoManager().undo());
		mainController.getUndoMenuItem().disableProperty().bind(tab.getUndoManager().undoableProperty().not());

		mainController.getRedoMenuItem().textProperty().bind(tab.getUndoManager().redoNameProperty());
		mainController.getRedoMenuItem().setOnAction(e -> tab.getUndoManager().redo());
		mainController.getRedoMenuItem().disableProperty().bind(tab.getUndoManager().redoableProperty().not());

		mainController.getSelectAllMenuItem().setOnAction(e -> controller.getTableView().getSelectionModel().selectAll());
		mainController.getSelectAllMenuItem().disableProperty().bind(Bindings.size(controller.getTableView().getSelectionModel().getSelectedItems()).isEqualTo(
				Bindings.size(controller.getTableView().getItems())));
		mainController.getSelectNoneMenuItem().setOnAction(e -> controller.getTableView().getSelectionModel().clearSelection());
		mainController.getSelectNoneMenuItem().disableProperty().bind(Bindings.isEmpty(controller.getTableView().getSelectionModel().getSelectedItems()));

		mainController.getSelectInverseMenuItem().setOnAction(e -> {
			var tableView = controller.getTableView();
			for (var i = 0; i < tableView.getItems().size(); i++) {
				if (tableView.getSelectionModel().isSelected(i))
					tableView.getSelectionModel().clearSelection(i);
				else
					tableView.getSelectionModel().select(i);
			}
		});
		mainController.getSelectInverseMenuItem().disableProperty().bind(mainWindow.emptyProperty());

		mainController.getSelectFromPreviousMenuItem().setOnAction(e -> {
			var taxonBlock = mainWindow.getWorkflow().getWorkingTaxaBlock();
			if (taxonBlock != null) {
				MainWindowManager.getPreviousSelection().stream().map(taxonBlock::get).filter(Objects::nonNull).forEach(t -> mainWindow.getTaxonSelectionModel().select(t));
			}
		});
		mainController.getSelectFromPreviousMenuItem().disableProperty().bind(Bindings.isEmpty(MainWindowManager.getPreviousSelection()));

		mainController.getFindMenuItem().setOnAction(e -> findToolBar.setShowFindToolBar(!findToolBar.isShowFindToolBar()));
		mainController.getFindAgainMenuItem().setOnAction(e -> findToolBar.findAgain());
		mainController.getFindAgainMenuItem().disableProperty().bind(findToolBar.canFindAgainProperty().not());
		mainController.getReplaceMenuItem().setOnAction(e -> findToolBar.setShowReplaceToolBar(!findToolBar.isShowReplaceToolBar()));
	}

	public static <T, S> void updateColumnWidths(TableView<T> tableView, TableColumn<T, S> displayCol) {
		double newLayoutTableColumnWidth = tableView.getWidth() - 20;
		for (var col : tableView.getColumns()) {
			if (col != displayCol && col.isVisible())
				newLayoutTableColumnWidth -= (col.getWidth() + 5);
		}
		if (newLayoutTableColumnWidth > displayCol.getWidth())
			displayCol.setPrefWidth(newLayoutTableColumnWidth);
	}
}
