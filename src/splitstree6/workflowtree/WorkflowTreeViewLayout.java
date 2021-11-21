/*
 *  Copyright (C) 2018. Daniel H. Huson
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

/*
 *  WorkflowTreeViewLayout.java Copyright (C) 2021 Daniel H. Huson
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

package splitstree6.workflowtree;

import javafx.collections.ListChangeListener;
import javafx.scene.control.TreeItem;
import jloda.fx.workflow.WorkflowNode;
import jloda.util.Basic;
import splitstree6.window.MainWindow;
import splitstree6.workflow.AlgorithmNode;
import splitstree6.workflow.DataNode;
import splitstree6.workflow.Workflow;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

/**
 * workflow tree-view layout
 * Daniel Huson, 10.2021
 */
public class WorkflowTreeViewLayout {
	private final Map<WorkflowNode, WorkflowTreeItem> nodeItemMap = new HashMap<>();
	private final Map<WorkflowNode, WorkflowTreeItem> deletedNodeItemMap = new HashMap<>();
	private final Map<WorkflowTreeItem, WorkflowTreeItem> deletedItemParentMap = new HashMap<>();

	private final MainWindow mainWindow;
	private final Workflow workflow;
	private final WorkflowTreeView treeView;

	public WorkflowTreeViewLayout(MainWindow mainWindow, WorkflowTreeView workflowTreeView) {
		this.mainWindow = mainWindow;
		this.workflow = mainWindow.getWorkflow();
		this.treeView = workflowTreeView;

		workflow.nodes().addListener((ListChangeListener<? super WorkflowNode>) e -> {
			while (e.next()) {
				if (e.wasAdded()) {
					for (var node : e.getAddedSubList()) {
						if (deletedNodeItemMap.containsKey(node)) {
							var item = deletedNodeItemMap.get(node);
							nodeItemMap.put(node, item);
							deletedNodeItemMap.remove(node);
							var parentItem = deletedItemParentMap.get(item);
							if (parentItem != null) {
								parentItem.getChildren().add(item);
								deletedItemParentMap.remove(item);
							}
						} else
							addNodeToTreeView(node);
					}
				} else if (e.wasRemoved()) {
					for (var node : e.getRemoved()) {
						var item = nodeItemMap.get(node);
						if (item != null) {
							var parentItem = item.getParent();
							if (parentItem != null) {
								deletedItemParentMap.put(item, (WorkflowTreeItem) parentItem);
								parentItem.getChildren().remove(item);
							}
							deletedNodeItemMap.put(node, item);
							nodeItemMap.remove(node);
						}
					}
				}
			}
		});
	}

	private WorkflowTreeItem inputTaxaItem;
	private WorkflowTreeItem workingDataItem;

	public void addNodeToTreeView(WorkflowNode node) {
		try {
			if (node instanceof DataNode dataNode) {
				if (workflow.isInputSourceNode(dataNode)) {
					treeView.getRoot().getChildren().clear();
					// todo: add input source
					treeView.getRoot().setExpanded(true);
				} else if (workflow.isInputTaxaNode(dataNode)) {
					var item = new WorkflowTreeItem(mainWindow, dataNode);
					nodeItemMap.put(dataNode, item);
					treeView.getRoot().getChildren().add(item);
					inputTaxaItem = item;
					treeView.getRoot().setExpanded(true);
				} else if (workflow.isInputDataNode(dataNode)) {
					var item = new WorkflowTreeItem(mainWindow, dataNode);
					nodeItemMap.put(dataNode, item);
					inputTaxaItem.getChildren().add(0, item);
					treeView.getRoot().setExpanded(true);
				} else if (workflow.isWorkingTaxaNode(dataNode)) {
					var item = new WorkflowTreeItem(mainWindow, dataNode);
					nodeItemMap.put(dataNode, item);
					inputTaxaItem.getChildren().add(item);
					treeView.getRoot().setExpanded(true);
				} else if (workflow.isWorkingDataNode(dataNode)) {
					var item = new WorkflowTreeItem(mainWindow, dataNode);
					nodeItemMap.put(dataNode, item);
					treeView.getRoot().getChildren().add(item);
					workingDataItem = item;
					treeView.getRoot().setExpanded(true);
				} else {
					// if (dataNode.getDataBlock() instanceof ViewBlock) return; // todo: what to do with sink blocks?

					var item = new WorkflowTreeItem(mainWindow, dataNode);
					nodeItemMap.put(dataNode, item);
					dataNode.getParents().addListener(createParentsChangeListener(dataNode));
				}
			} else if (node instanceof AlgorithmNode algorithmNode) {
				if (workflow.isInputDataLoader(algorithmNode) || workflow.isInputTaxaDataFilter(algorithmNode)) {
					//System.err.println("Skipped: " + algorithmNode);
					// ignore
				} else if (workflow.isInputTaxaFilter(algorithmNode)) {
					var item = new WorkflowTreeItem(mainWindow, algorithmNode);
					nodeItemMap.put(algorithmNode, item);
					if (inputTaxaItem.getChildren().size() > 1)
						inputTaxaItem.getChildren().add(1, item);
					else
						inputTaxaItem.getChildren().add(item);
				} else {
					var item = new WorkflowTreeItem(mainWindow, algorithmNode);
					nodeItemMap.put(algorithmNode, item);
					algorithmNode.getParents().addListener(createParentsChangeListener(algorithmNode));
				}
			}
		} catch (Exception ex) {
			Basic.caught(ex);
		}
	}

	private ListChangeListener<WorkflowNode> createParentsChangeListener(WorkflowNode node) {
		return e -> {
			try {
				var item = nodeItemMap.get(node);

				while (e.next()) {
					if (e.wasAdded()) {
						if (isNotContainedInTreeView(item)) {
							for (var parentNode : e.getAddedSubList()) {
								if (parentNode instanceof AlgorithmNode && node instanceof DataNode dataNode && parentNode == dataNode.getPreferredParent()) {
									var parentItem = nodeItemMap.get(parentNode);
									parentItem.getChildren().add(item);
									compress(parentItem);
									break;

								} else if (parentNode instanceof DataNode && node instanceof AlgorithmNode algorithmNode && parentNode == algorithmNode.getPreferredParent()) {
									var parentItem = nodeItemMap.get(parentNode);
									parentItem.getChildren().add(item);
									compress(parentItem);
									break;
								}
							}
						}

						if (workingDataItem != null)
							workingDataItem.setExpanded(true);
					} else if (e.wasRemoved()) {
						for (var parentNode : e.getRemoved()) {
							var parentItem = nodeItemMap.get(parentNode);
							if (parentItem != null) {
								parentItem.getChildren().remove(item);
								deletedItemParentMap.put(item, parentItem);
							}
						}
					}
				}
			} catch (Exception ex) {
				Basic.caught(ex);
			}
		};
	}

	private void compress(WorkflowTreeItem item) {
		var changed = true;
		do {
			changed = false;
			if (item.getWorkflowNode().getPreferredParent() != null && item.getWorkflowNode().getPreferredParent().getChildren().size() == 1 && item.getChildren().size() == 1) {
				var parent = (WorkflowTreeItem) item.getParent();
				if (parent != null && parent.getChildren().size() > 0) {
					if (parent.getChildren().indexOf(item) == parent.getChildren().size() - 1) {
						var childOfItem = (WorkflowTreeItem) item.getChildren().remove(0);
						parent.getChildren().add(childOfItem);
						item = childOfItem;
						changed = true;
					}
				}
			}
		}
		while (changed);
	}

	private boolean isNotContainedInTreeView(WorkflowTreeItem item) {
		var queue = new LinkedList<TreeItem<String>>();
		queue.add(treeView.getRoot());
		while (queue.size() > 0) {
			var other = queue.pop();
			if (other.getChildren().contains(item))
				return false;
			queue.addAll(other.getChildren());
		}
		return true;
	}

	public Map<WorkflowNode, WorkflowTreeItem> getNodeItemMap() {
		return nodeItemMap;
	}
}
