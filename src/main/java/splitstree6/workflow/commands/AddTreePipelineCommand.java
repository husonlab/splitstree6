/*
 *  AddTreePipelineCommand.java Copyright (C) 2024 Daniel H. Huson
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

package splitstree6.workflow.commands;

import jloda.fx.undo.UndoableRedoableCommand;
import jloda.fx.window.NotificationManager;
import jloda.fx.workflow.WorkflowNode;
import splitstree6.algorithms.splits.splits2trees.GreedyTree;
import splitstree6.algorithms.trees.trees2view.ShowTrees;
import splitstree6.data.*;
import splitstree6.workflow.Algorithm;
import splitstree6.workflow.DataBlock;
import splitstree6.workflow.DataNode;
import splitstree6.workflow.Workflow;

import java.util.*;

/**
 * attach a tree pipeline to the datanode
 * Daniel Huson, 10.2021
 */
public class AddTreePipelineCommand {
	/**
	 * creates a command for adding a pipeline to view trees
	 *
	 * @param workflow  the workflow
	 * @param dataNode  an existing data node
	 * @param algorithm the algorithm to be attached to the data node
	 * @return newly created nodes
	 */
	public static UndoableRedoableCommand create(Workflow workflow, DataNode dataNode, Algorithm algorithm) {
		if (dataNode.getDataBlock().getClass() != CharactersBlock.class && dataNode.getDataBlock().getClass() != DistancesBlock.class)
			throw new RuntimeException("Bad node class");

		final Collection<WorkflowNode> addedNodes = new ArrayList<>();
		return UndoableRedoableCommand.create("add " + algorithm.getName(),
				() -> workflow.deleteNodes(addedNodes),
				() -> {
					addedNodes.clear();
					addedNodes.addAll(addPipeline(workflow, dataNode, algorithm));
				});
	}

	/**
	 * create a node for the given algorithm and then add additional nodes to get to a tree visualization and then runs the pipeline
	 *
	 * @param workflow  the workflow
	 * @param dataNode  an existing data node
	 * @param algorithm the algorithm to be attached to the data node
	 * @return newly created nodes
	 */
	public static List<WorkflowNode> addPipeline(Workflow workflow, DataNode dataNode, Algorithm algorithm) {
		try {
			if (workflow.isRunning())
				throw new Exception("Workflow is currently running");

			if (!isApplicable(dataNode, algorithm))
				throw new Exception("Internal error");

			var targetDataNode = workflow.newDataNode((DataBlock) algorithm.getToClass().getConstructor().newInstance());
			var algorithmNode = workflow.newAlgorithmNode(algorithm, workflow.getWorkingTaxaNode(), dataNode, targetDataNode);

			if (algorithm.getToClass() == TreesBlock.class) {
				var targetDataNode2 = workflow.newDataNode(new ViewBlock());
				var algorithmNode2 = workflow.newAlgorithmNode(new ShowTrees(), workflow.getWorkingTaxaNode(), targetDataNode, targetDataNode2);
				algorithmNode.restart();
				NotificationManager.showInformation("Attached algorithm: " + algorithm.getName());
				return Arrays.asList(targetDataNode, algorithmNode, targetDataNode2, algorithmNode2);
			} else if (algorithm.getToClass() == SplitsBlock.class) {
				var targetDataNode2 = workflow.newDataNode(new TreesBlock());
				var algorithmNode2 = workflow.newAlgorithmNode(new GreedyTree(), workflow.getWorkingTaxaNode(), targetDataNode, targetDataNode2);
				var targetDataNode3 = workflow.newDataNode(new ViewBlock());
				var algorithmNode3 = workflow.newAlgorithmNode(new ShowTrees(), workflow.getWorkingTaxaNode(), targetDataNode2, targetDataNode3);
				algorithmNode.restart();
				NotificationManager.showInformation("Attached algorithm: " + algorithm.getName());
				return Arrays.asList(targetDataNode, algorithmNode, targetDataNode2, algorithmNode2, algorithmNode3, targetDataNode3);
			} else {
				throw new Exception("Not implemented: addPipeline() for data type: " + algorithm.getToClass());
			}
		} catch (Exception ex) {
			NotificationManager.showError("Compute algorithm failed: " + ex);
			return Collections.emptyList();
		}
	}

	public static boolean isApplicable(DataNode dataNode, Algorithm algorithm) {
		return (dataNode.getDataBlock().getClass() == CharactersBlock.class || dataNode.getDataBlock().getClass() == DistancesBlock.class)
			   && algorithm.getFromClass() == dataNode.getDataBlock().getClass() && algorithm.getToClass() == TreesBlock.class;
	}
}
