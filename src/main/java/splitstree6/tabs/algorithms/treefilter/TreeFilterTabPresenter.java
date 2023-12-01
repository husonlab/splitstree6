/*
 * TreeFilterTabPresenter.java Copyright (C) 2023 Daniel H. Huson
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

package splitstree6.tabs.algorithms.treefilter;

import javafx.beans.binding.Bindings;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.control.cell.PropertyValueFactory;
import jloda.fx.find.FindToolBar;
import splitstree6.algorithms.trees.trees2trees.TreesFilter;
import splitstree6.data.TreesBlock;
import splitstree6.tabs.IDisplayTabPresenter;
import splitstree6.window.MainWindow;
import splitstree6.workflow.AlgorithmNode;

import java.util.HashSet;

import static splitstree6.tabs.algorithms.taxafilter.TaxaFilterPresenter.setupEditMenuButton;
import static splitstree6.tabs.algorithms.taxafilter.TaxaFilterPresenter.updateColumnWidths;

public class TreeFilterTabPresenter implements IDisplayTabPresenter {

	public TreeFilterTabPresenter(MainWindow mainWindow, TreeFilterTab treeFilterTab, AlgorithmNode<TreesBlock, TreesBlock> treesFilterNode, TreesFilter treesFilter) {
		var algorithmController = treeFilterTab.getController();
		var controller = treeFilterTab.getTreeFilterTabController();

		var tableView = controller.getTableView();
		tableView.setEditable(true);
		tableView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);

		controller.getIdColumn().setCellValueFactory(new PropertyValueFactory<>("Id"));

		controller.getActiveColumn().setEditable(true);
		controller.getActiveColumn().setCellValueFactory(item -> item.getValue().activeProperty());
		controller.getActiveColumn().setCellFactory(CheckBoxTableCell.forTableColumn(controller.getActiveColumn()));

		controller.getNameColumn().setCellValueFactory(new PropertyValueFactory<>("Name"));
		tableView.widthProperty().addListener(c -> updateColumnWidths(tableView, controller.getNameColumn()));


		Runnable updateTable = () -> {
			tableView.getItems().clear();
			var parentNode = treesFilterNode.getPreferredParent();
			if (parentNode != null) {
				var treesBlock = ((TreesBlock) parentNode.getDataBlock());
				var disabled = new HashSet<>(treesFilter.getOptionDisabledTrees());
				for (var t = 1; t <= treesBlock.getNTrees(); t++) {
					var item = new TreeFilterTableItem(t, treesBlock.getTree(t));
					item.setActive(!disabled.contains(item.getName()));
					tableView.getItems().add(item);
				}
			}
		};

		treesFilterNode.validProperty().addListener(e -> updateTable.run());

		updateTable.run();

		algorithmController.getApplyButton().setOnAction(e -> {
			var disabled = new HashSet<String>();
			for (var item : controller.getTableView().getItems()) {
				if (!item.isActive())
					disabled.add(item.getName());
			}
			treesFilter.getOptionDisabledTrees().setAll(disabled);
			treesFilterNode.restart();
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
		controller.getActivateSelectedMenuItem().disableProperty().bind(Bindings.isEmpty(tableView.getSelectionModel().getSelectedItems()));

		controller.getDeactivateSelectedMenuItem().setOnAction(e -> {
			for (var item : tableView.getSelectionModel().getSelectedItems()) {
				item.setActive(false);
			}
		});
		controller.getDeactivateSelectedMenuItem().disableProperty().bind(Bindings.isEmpty(tableView.getSelectionModel().getSelectedItems()));

		setupEditMenuButton(algorithmController.getMenuButton(), controller.getActiveColumn().getContextMenu());
	}


	@Override
	public void setupMenuItems() {

	}

	@Override
	public FindToolBar getFindToolBar() {
		return null;
	}

	@Override
	public boolean allowFindReplace() {
		return false;
	}
}
