/*
 *  DataNodeContextMenuPresenter.java Copyright (C) 2021 Daniel H. Huson
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

package splitstree6.contextmenus.datanode;

import javafx.geometry.Point2D;
import javafx.scene.control.MenuItem;
import javafx.util.Pair;
import jloda.fx.window.NotificationManager;
import jloda.util.PluginClassLoader;
import splitstree6.window.MainWindow;
import splitstree6.workflow.Algorithm;
import splitstree6.workflow.DataBlock;
import splitstree6.workflow.DataNode;
import splitstree6.workflow.Workflow;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * data node context menu
 * Daniel Huson, 10.2021
 */
public class DataNodeContextMenuPresenter {

	public DataNodeContextMenuPresenter(MainWindow mainWindow, Point2D screenLocation, DataNodeContextMenuController controller, DataNode dataNode) {
		var workflow = mainWindow.getWorkflow();

		controller.getEditMenuItem().setOnAction(e -> mainWindow.getTextTabsManager().showTab(dataNode, true));

		controller.getExportMenuItem().setOnAction(e -> {
			System.err.println("Export: not implemented");
		});

		controller.getAttachAlgorithmMenu().getItems().addAll(createAttachAlgorithmMenuItems(workflow, dataNode));
		controller.getAttachAlgorithmMenu().disableProperty().bind(workflow.runningProperty());
	}

	private List<MenuItem> createAttachAlgorithmMenuItems(Workflow workflow, DataNode dataNode) {
		// todo: sort items logically

		var list = new ArrayList<Pair<String, Algorithm>>();
		for (var algorithm : PluginClassLoader.getInstances(Algorithm.class, "splitstree6.algorithms")) {
			if (algorithm.getFromClass() == dataNode.getDataBlock().getClass())
				list.add(new Pair<>(algorithm.getName(), algorithm));
		}
		list.sort(Comparator.comparing(Pair::getKey)); // sort alphabetically

		var result = new ArrayList<MenuItem>();
		for (var pair : list) {
			var menuItem = new MenuItem(pair.getKey());
			menuItem.setOnAction(e -> attachAlgorithm(workflow, dataNode, pair.getValue()));
			result.add(menuItem);
		}
		return result;
	}

	private void attachAlgorithm(Workflow workflow, DataNode dataNode, Algorithm algorithm) {
		try {
			if (workflow.isRunning())
				throw new RuntimeException("Workflow is currently running");
			var targetDataNode = workflow.newDataNode((DataBlock) algorithm.getToClass().getConstructor().newInstance());
			var algorithmNode = workflow.newAlgorithmNode(algorithm, workflow.getWorkingTaxaNode(), dataNode, targetDataNode);
			algorithmNode.restart();
			NotificationManager.showInformation("Attached algorithm: " + algorithm.getName());
		} catch (Exception ex) {
			NotificationManager.showError("Attach algorithm failed: " + ex);
		}
	}
}
