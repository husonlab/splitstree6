/*
 * DataNodeContextMenuPresenter.java Copyright (C) 2024 Daniel H. Huson
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

package splitstree6.contextmenus.datanode;

import javafx.beans.binding.Bindings;
import javafx.scene.control.MenuItem;
import javafx.util.Pair;
import jloda.fx.undo.UndoManager;
import splitstree6.algorithms.AlgorithmList;
import splitstree6.dialog.exporting.data.ExportDialog;
import splitstree6.window.MainWindow;
import splitstree6.workflow.Algorithm;
import splitstree6.workflow.DataNode;
import splitstree6.workflow.DataTaxaFilter;
import splitstree6.workflow.Workflow;
import splitstree6.workflow.commands.AddAlgorithmCommand;
import splitstree6.workflow.commands.AddNetworkPipelineCommand;
import splitstree6.workflow.commands.AddTreePipelineCommand;
import splitstree6.workflow.interfaces.DoNotLoadThisAlgorithm;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;

/**
 * data node context menu
 * Daniel Huson, 10.2021
 */
public class DataNodeContextMenuPresenter {

	public DataNodeContextMenuPresenter(MainWindow mainWindow, UndoManager undoManager, DataNodeContextMenuController controller, DataNode dataNode) {
		var workflow = mainWindow.getWorkflow();

		controller.getShowTextMenuItem().setOnAction(e -> {
			mainWindow.getTextTabsManager().showDataNodeTab(dataNode, true);
		});
		controller.getShowTextMenuItem().disableProperty().bind(dataNode.validProperty().not().or(dataNode.allParentsValidProperty().not()));

		controller.getExportMenuItem().setOnAction(e -> ExportDialog.show(mainWindow, dataNode));
		controller.getExportMenuItem().disableProperty().bind(dataNode.validProperty().not().or(dataNode.allParentsValidProperty().not()));

		if (workflow.isDerivedNode(dataNode) || workflow.getWorkingDataNode() == dataNode) {
			controller.getAddTreeMenu().getItems().setAll(createAddTreeMenuItems(workflow, undoManager, dataNode));
			controller.getAddTreeMenu().disableProperty().bind(workflow.runningProperty().or(Bindings.isEmpty(controller.getAddTreeMenu().getItems())));

			controller.getAddNetworkMenu().getItems().setAll(createAddNetworkMenuItems(workflow, undoManager, dataNode));
			controller.getAddNetworkMenu().disableProperty().bind(workflow.runningProperty().or(Bindings.isEmpty(controller.getAddNetworkMenu().getItems())));

			controller.getAddAlgorithmMenu().getItems().setAll(createAddAlgorithmMenuItems(mainWindow, undoManager, dataNode));
			controller.getAddAlgorithmMenu().disableProperty().bind(workflow.runningProperty().or(Bindings.isEmpty(controller.getAddAlgorithmMenu().getItems())));
		} else {
			controller.getAddTreeMenu().setDisable(true);
			controller.getAddNetworkMenu().setDisable(true);
			controller.getAddAlgorithmMenu().setDisable(true);
		}
	}

	public static List<MenuItem> createAddTreeMenuItems(Workflow workflow, UndoManager undoManager, DataNode dataNode) {
		// todo: sort items logically

		var list = new ArrayList<Pair<String, Algorithm>>();
		{
			var algorithms = new ArrayList<Algorithm>();

		}
		list.sort(Comparator.comparing(Pair::getKey));

		var result = new ArrayList<MenuItem>();
		var seen = new HashSet<String>();
		for (var pair : list) {
			if (!seen.contains(pair.getKey())) {
				seen.add(pair.getKey());
				var menuItem = new MenuItem(pair.getKey());
				menuItem.setOnAction(e -> undoManager.doAndAdd(AddTreePipelineCommand.create(workflow, dataNode, pair.getValue())));
				result.add(menuItem);
			}
		}
		return result;
	}

	public static List<MenuItem> createAddNetworkMenuItems(Workflow workflow, UndoManager undoManager, DataNode dataNode) {
		// todo: sort items logically

		var list = new ArrayList<Pair<String, Algorithm>>();
		var seen = new HashSet<String>();
		for (var algorithm : AlgorithmList.list()) {
			if (!(algorithm instanceof DoNotLoadThisAlgorithm)) {
				if (AddNetworkPipelineCommand.isApplicable(dataNode, algorithm)) {
					if (!seen.contains(algorithm.getName())) {
						seen.add(algorithm.getName());
						list.add(new Pair<>(algorithm.getName(), algorithm));
					}
				}
			}
		}
		list.sort(Comparator.comparing(Pair::getKey));

		var result = new ArrayList<MenuItem>();
		for (var pair : list) {
			var menuItem = new MenuItem(pair.getKey());
			menuItem.setOnAction(e -> undoManager.doAndAdd(AddNetworkPipelineCommand.create(workflow, dataNode, pair.getValue())));
			result.add(menuItem);
		}
		return result;
	}

	public static List<MenuItem> createAddAlgorithmMenuItems(MainWindow mainWindow, UndoManager undoManager, DataNode dataNode) {
		// todo: sort items logically

		var list = new ArrayList<Pair<String, Algorithm>>();
		var seen = new HashSet<String>();
		for (var algorithm : AlgorithmList.list()) {
			if (!(algorithm instanceof DoNotLoadThisAlgorithm)) {
				if (AddAlgorithmCommand.isApplicable(dataNode, algorithm) && !(algorithm instanceof DataTaxaFilter))
					if (!seen.contains(algorithm.getName())) {
						seen.add(algorithm.getName());
						list.add(new Pair<>(algorithm.getName(), algorithm));
					}
			}
		}
		// list.sort(Comparator.comparing(Pair::getKey));

		var result = new ArrayList<MenuItem>();
		for (var pair : list) {
			var menuItem = new MenuItem(pair.getKey());
			menuItem.setOnAction(e -> undoManager.doAndAdd(AddAlgorithmCommand.create(mainWindow, dataNode, pair.getValue())));
			menuItem.setDisable(mainWindow.getWorkflow().getWorkingTaxaBlock() == null || !pair.getValue().isApplicable(mainWindow.getWorkflow().getWorkingTaxaBlock(), dataNode.getDataBlock()));
			result.add(menuItem);
		}
		return result;
	}
}
