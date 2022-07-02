/*
 * AddAlgorithmCommand.java Copyright (C) 2022 Daniel H. Huson
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

package splitstree6.workflow.commands;

import javafx.application.Platform;
import jloda.fx.undo.UndoableRedoableCommand;
import jloda.fx.window.NotificationManager;
import jloda.fx.workflow.WorkflowNode;
import splitstree6.algorithms.splits.splits2view.ShowSplits;
import splitstree6.algorithms.trees.trees2view.ShowTrees;
import splitstree6.data.DistancesBlock;
import splitstree6.data.SplitsBlock;
import splitstree6.data.TreesBlock;
import splitstree6.data.ViewBlock;
import splitstree6.window.MainWindow;
import splitstree6.workflow.Algorithm;
import splitstree6.workflow.DataBlock;
import splitstree6.workflow.DataNode;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * add an algorithm to the given datanode
 * Daniel Huson, 10.2021
 */
public class AddAlgorithmCommand {

	public static UndoableRedoableCommand create(MainWindow mainWindow, DataNode<DistancesBlock> node, Algorithm algorithm) {
		final Collection<WorkflowNode> addedNodes = new ArrayList<>();
		return UndoableRedoableCommand.create("add " + algorithm.getName(),
				() -> mainWindow.getWorkflow().deleteNodes(addedNodes),
				() -> {
					addedNodes.clear();
					addedNodes.addAll(addAlgorithm(mainWindow, node, algorithm));
				});
	}

	public static List<WorkflowNode> addAlgorithm(MainWindow mainWindow, DataNode dataNode, Algorithm algorithm) {
		var workflow = mainWindow.getWorkflow();
		try {
			if (workflow.isRunning())
				throw new RuntimeException("Workflow is currently running");

			if (!isApplicable(dataNode, algorithm))
				throw new Exception("Internal error");

			var targetDataNode = workflow.newDataNode((DataBlock) algorithm.getToClass().getConstructor().newInstance());
			var algorithmNode = workflow.newAlgorithmNode(algorithm, workflow.getWorkingTaxaNode(), dataNode, targetDataNode);

			var list = new ArrayList<WorkflowNode>();
			list.add(targetDataNode);
			list.add(algorithmNode);

			if (algorithm.getToClass() == SplitsBlock.class) {
				var targetDataNode2 = workflow.newDataNode(new ViewBlock());
				var algorithmNode2 = workflow.newAlgorithmNode(new ShowSplits(), workflow.getWorkingTaxaNode(), targetDataNode, targetDataNode2);
				list.add(targetDataNode2);
				list.add(algorithmNode2);

			} else if (algorithm.getToClass() == TreesBlock.class) {
				var targetDataNode2 = workflow.newDataNode(new ViewBlock());
				var algorithmNode2 = workflow.newAlgorithmNode(new ShowTrees(), workflow.getWorkingTaxaNode(), targetDataNode, targetDataNode2);
				list.add(targetDataNode2);
				list.add(algorithmNode2);
			}
			algorithmNode.restart();
			Platform.runLater(() -> mainWindow.getAlgorithmTabsManager().showTab(algorithmNode, true));
			NotificationManager.showInformation("Attached algorithm: " + algorithm.getName());
			return list;
		} catch (Exception ex) {
			NotificationManager.showError("Attach algorithm failed: " + ex);
			return Collections.emptyList();
		}
	}

	public static boolean isApplicable(DataNode dataNode, Algorithm algorithm) {
		return dataNode.getDataBlock().getClass() == algorithm.getFromClass();
	}
}
