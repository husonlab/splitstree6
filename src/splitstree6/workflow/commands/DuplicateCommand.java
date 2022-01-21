/*
 * DuplicateCommand.java Copyright (C) 2022 Daniel H. Huson
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

import jloda.fx.undo.UndoableRedoableCommand;
import jloda.fx.workflow.WorkflowNode;
import jloda.util.Basic;
import splitstree6.workflow.AlgorithmNode;
import splitstree6.workflow.DataNode;
import splitstree6.workflow.Workflow;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Stack;

/**
 * duplicate an algorithm and all dependent nodes
 * Daniel Huson, 10.2021
 */
public class DuplicateCommand {

	public static UndoableRedoableCommand create(Workflow workflow, AlgorithmNode node) {
		final Collection<WorkflowNode> addedNodes = new ArrayList<>();

		return UndoableRedoableCommand.create("duplicate nodes",
				() -> workflow.deleteNodes(addedNodes),
				() -> {
					try {
						addedNodes.clear();

						var stack = new Stack<DataNode>();
						stack.push(node.getPreferredParent());

						var first = true;

						var dataNode2CopyNodeMap = new HashMap<DataNode, DataNode>();
						dataNode2CopyNodeMap.put(node.getPreferredParent(), node.getPreferredParent());

						AlgorithmNode firstCopyAlgorithmNode = null;

						while (stack.size() > 0) {
							var sourceNode = (DataNode) stack.pop();
							var algorithmNodes = new ArrayList<splitstree6.workflow.AlgorithmNode>();
							if (first) {
								algorithmNodes.add(node);
								first = false;
							} else {
								algorithmNodes.addAll(sourceNode.getChildren().stream().map(v -> (AlgorithmNode) v).toList());
							}
							for (var algorithmNode : algorithmNodes) {
								var targetNode = algorithmNode.getTargetNode();
								var copySourceNode = dataNode2CopyNodeMap.get(sourceNode);
								var copyTargetNode = workflow.newDataNode(targetNode.getDataBlock().newInstance());
								copyTargetNode.setValid(false);
								addedNodes.add(copyTargetNode);
								var copyAlgorithmNode = workflow.newAlgorithmNode(algorithmNode.getAlgorithm().newInstance(), workflow.getWorkingTaxaNode(), copySourceNode, copyTargetNode);
								if (firstCopyAlgorithmNode == null)
									firstCopyAlgorithmNode = copyAlgorithmNode;
								copyAlgorithmNode.setValid(false);
								addedNodes.add(copyAlgorithmNode);
								dataNode2CopyNodeMap.put(targetNode, copyTargetNode);
								stack.add(algorithmNode.getTargetNode());
							}
						}
						if (firstCopyAlgorithmNode != null)
							firstCopyAlgorithmNode.restart();
					} catch (Exception ex) {
						Basic.caught(ex);
					}
				});
	}
}
