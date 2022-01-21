/*
 *  WorkflowTabLayout.java Copyright (C) 2022 Daniel H. Huson
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

package splitstree6.tabs.workflow;

import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.geometry.Point2D;
import javafx.scene.Group;
import javafx.scene.Node;
import jloda.fx.workflow.WorkflowNode;
import jloda.util.Basic;
import jloda.util.Table;
import splitstree6.workflow.AlgorithmNode;
import splitstree6.workflow.DataNode;
import splitstree6.workflow.Workflow;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * workflow tab presenter
 * Daniel Huson, 11.2021
 */
public class WorkflowTabLayout {
	private static final double dx = 185;
	private static final double dy = 75;
	private static final double NOT_SET = -1.0;

	private final WorkflowTab workflowTab;
	private final Workflow workflow;
	private final ObservableList<Node> nodeItems;
	private final ObservableList<Node> edgeItems;

	private final Map<WorkflowNode, WorkflowNodeItem> nodeItemMap = new HashMap<>();
	private final Map<WorkflowNode, WorkflowNodeItem> deletedNodeItemMap = new HashMap<>();

	private final Table<WorkflowNode, WorkflowNode, WorkflowEdgeItem> edgeItemTable = new Table<>();
	private final Table<WorkflowNode, WorkflowNode, WorkflowEdgeItem> deletedEdgeItemTable = new Table<>();

	private final Set<Point2D> usedLocations = new HashSet<>();

	public WorkflowTabLayout(WorkflowTab workflowTab, Group nodeGroup, Group edgeGroup) {
		this.workflowTab = workflowTab;
		this.workflow = workflowTab.getMainWindow().getWorkflow();
		this.nodeItems = nodeGroup.getChildren();
		this.edgeItems = edgeGroup.getChildren();

		nodeGroup.setTranslateX(-dx);
		nodeGroup.setTranslateY(-dy);
		edgeGroup.setTranslateX(-dx);
		edgeGroup.setTranslateY(-dy);

		workflow.nodes().addListener((ListChangeListener<? super WorkflowNode>) e -> {
			while (e.next()) {
				if (e.wasAdded()) {
					for (var node : e.getAddedSubList()) {
						if (deletedNodeItemMap.containsKey(node)) {
							var item = deletedNodeItemMap.get(node);
							nodeItemMap.put(node, item);
							deletedNodeItemMap.remove(node);
							nodeItems.add(item);

							if (deletedEdgeItemTable.rowKeySet().contains(node)) {
								var row = deletedEdgeItemTable.row(node);
								edgeItemTable.putRow(node, row);
								for (var edgeItem : row.values()) {
									if (!edgeItems.contains(edgeItem))
										edgeItems.add(edgeItem);
								}
								deletedEdgeItemTable.removeRow(node);
							}

							if (deletedEdgeItemTable.columnKeySet().contains(node)) {
								var column = deletedEdgeItemTable.column(node);
								edgeItemTable.putColumn(node, column);
								for (var edgeItem : column.values()) {
									if (!edgeItems.contains(edgeItem))
										edgeItems.add(edgeItem);
								}
								deletedEdgeItemTable.removeColumn(node);
							}
						} else
							addNodeToTreeView(node);
					}
				} else if (e.wasRemoved()) {
					for (var node : e.getRemoved()) {
						var item = nodeItemMap.get(node);
						if (item != null) {
							usedLocations.remove(new Point2D(item.getTranslateX(), item.getTranslateY()));
							nodeItems.remove(item);
							nodeItemMap.remove(node);
							deletedNodeItemMap.put(node, item);

							if (edgeItemTable.rowKeySet().contains(node)) {
								edgeItems.removeAll(edgeItemTable.row(node).values());
								deletedEdgeItemTable.putRow(node, edgeItemTable.row(node));
								edgeItemTable.removeRow(node);
							}
							if (edgeItemTable.columnKeySet().contains(node)) {
								edgeItems.removeAll(edgeItemTable.column(node).values());
								deletedEdgeItemTable.putColumn(node, edgeItemTable.column(node));
								edgeItemTable.removeColumn(node);
							}
						}
					}
				}
			}
		});
	}

	private void addNodeToTreeView(WorkflowNode node) {
		try {
			if (node instanceof DataNode dataNode) {
				if (workflow.isInputSourceNode(dataNode)) {
					nodeItems.clear();
					usedLocations.clear();
					// todo: add input source
				} else if (workflow.isInputTaxaNode(dataNode)) {
					var item = workflowTab.newDataItem(dataNode);
					nodeItemMap.put(dataNode, item);
					item.setTranslateX(dx);
					item.setTranslateY(dy);
					nodeItems.add(item);
					usedLocations.add(new Point2D(item.getTranslateX(), item.getTranslateY()));
					dataNode.getParents().addListener(createParentsChangeListenerForInputAndWorkingNodes(dataNode));
				} else if (workflow.isInputDataNode(dataNode)) {
					var item = workflowTab.newDataItem(dataNode);
					nodeItemMap.put(dataNode, item);
					item.setTranslateX(2 * dx);
					item.setTranslateY(dy);
					nodeItems.add(item);
					usedLocations.add(new Point2D(item.getTranslateX(), item.getTranslateY()));
					dataNode.getParents().addListener(createParentsChangeListenerForInputAndWorkingNodes(dataNode));
				} else if (workflow.isWorkingTaxaNode(dataNode)) {
					var item = workflowTab.newDataItem(dataNode);
					nodeItemMap.put(dataNode, item);
					item.setTranslateX(dx);
					item.setTranslateY(3 * dy);
					nodeItems.add(item);
					usedLocations.add(new Point2D(item.getTranslateX(), item.getTranslateY()));
					dataNode.getParents().addListener(createParentsChangeListenerForInputAndWorkingNodes(dataNode));
				} else if (workflow.isWorkingDataNode(dataNode)) {
					var item = workflowTab.newDataItem(dataNode);
					nodeItemMap.put(dataNode, item);
					nodeItems.add(item);
					item.setTranslateX(3 * dx);
					item.setTranslateY(3 * dy);
					usedLocations.add(new Point2D(item.getTranslateX(), item.getTranslateY()));
					dataNode.getParents().addListener(createParentsChangeListenerForInputAndWorkingNodes(dataNode));
				} else {
					//	if (dataNode.getDataBlock() instanceof ViewBlock) return; // todo: what to do with sink blocks?

					var item = nodeItemMap.get(dataNode);
					if (item == null) {
						item = workflowTab.newDataItem(dataNode);
						nodeItemMap.put(dataNode, item);
						dataNode.getParents().addListener(createParentsChangeListener(dataNode));
						nodeItems.add(item);
					}
					item.setTranslateX(NOT_SET);
					item.setTranslateY(NOT_SET);
				}
			} else if (node instanceof AlgorithmNode algorithmNode) {
				if (workflow.isInputDataLoader(algorithmNode)) {
					System.err.println("Skipped: " + algorithmNode.getTitle());
					// ignore
				} else if (workflow.isInputTaxaDataFilter(algorithmNode)) {
					var item = workflowTab.newAlgorithmItem(algorithmNode);
					nodeItemMap.put(algorithmNode, item);
					nodeItems.add(item);
					item.setTranslateX(2 * dx);
					item.setTranslateY(3 * dy);
					usedLocations.add(new Point2D(item.getTranslateX(), item.getTranslateY()));
					algorithmNode.getParents().addListener(createParentsChangeListenerForInputAndWorkingNodes(algorithmNode));
				} else if (workflow.isInputTaxaEditor(algorithmNode)) {
					var item = workflowTab.newAlgorithmItem(algorithmNode);
					nodeItemMap.put(algorithmNode, item);
					algorithmNode.getParents().addListener(createParentsChangeListenerForInputAndWorkingNodes(algorithmNode));
					nodeItems.add(item);
					item.setTranslateX(dx);
					item.setTranslateY(2 * dy);
					usedLocations.add(new Point2D(item.getTranslateX(), item.getTranslateY()));
				} else {
					if (!nodeItemMap.containsKey(algorithmNode)) {
						var item = nodeItemMap.get(algorithmNode);
						if (item == null) {
							item = workflowTab.newAlgorithmItem(algorithmNode);
							nodeItemMap.put(algorithmNode, item);
							algorithmNode.getParents().addListener(createParentsChangeListener(algorithmNode));
							nodeItems.add(item);
						}
						item.setTranslateX(NOT_SET);
						item.setTranslateY(NOT_SET);
						usedLocations.add(new Point2D(item.getTranslateX(), item.getTranslateY()));
					}
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
				if (item != null) {
					while (e.next()) {
						if (e.wasAdded()) {
							for (var parentNode : e.getAddedSubList()) {
								if (!workflow.isWorkingTaxaNode(parentNode)) {
									var parentItem = nodeItemMap.get(parentNode);
									var edgeItem = new WorkflowEdgeItem(parentItem, item);
									edgeItemTable.put(parentNode, node, edgeItem);
									edgeItems.add(edgeItem);
									updateAllBelow(parentNode);
								}
							}
						} else if (e.wasRemoved()) {
							for (var parentNode : e.getRemoved()) {
								var parentItem = nodeItemMap.get(parentNode);
								if (parentItem != null) {
									parentItem.getChildren().remove(item);
									var edgeItem = edgeItemTable.get(parentNode, node);
									if (edgeItem != null) {
										edgeItemTable.remove(parentNode, node);
										edgeItems.remove(edgeItem);
										deletedEdgeItemTable.put(parentNode, node, edgeItem);
									}
								}
							}
						}
					}
				}
			} catch (Exception ex) {
				Basic.caught(ex);
			}
		};
	}

	private ListChangeListener<WorkflowNode> createParentsChangeListenerForInputAndWorkingNodes(WorkflowNode node) {
		return e -> {
			try {
				var item = nodeItemMap.get(node);
				if (item != null) {
					while (e.next()) {
						if (e.wasAdded()) {
							for (var parent : e.getAddedSubList()) {
								var parentItem = nodeItemMap.get(parent);
								if (parentItem != null) {
									if (!workflow.isDerivedNode(node)) {
										var edgeItem = getEdgeItemTable().get(parent, node);
										if (edgeItem == null) {
											edgeItem = new WorkflowEdgeItem(parentItem, item);
											edgeItemTable.put(parent, node, edgeItem);
											edgeItems.add(edgeItem);
										} else if (!edgeItems.contains(edgeItem))
											edgeItems.add(edgeItem);
									}
								}
							}
						} else if (e.wasRemoved()) {
							for (var parentNode : e.getRemoved()) {
								var parentItem = nodeItemMap.get(parentNode);
								if (parentItem != null)
									parentItem.getChildren().remove(item);
							}
						}
					}
				}
			} catch (Exception ex) {
				Basic.caught(ex);
			}
		};
	}

	private void updateAllBelow(WorkflowNode parent) {
		var parentItem = nodeItemMap.get(parent);
		if (parentItem != null && parentItem.getTranslateX() != NOT_SET) {
			for (var node : parent.getChildren()) {
				if (parent == node.getPreferredParent()) {
					var item = nodeItemMap.get(node);
					if (item != null && item.getTranslateX() == NOT_SET) {
						var x = parentItem.getTranslateX() + dx;
						var y = parentItem.getTranslateY();
						if (usedLocations.contains(new Point2D(x, y))) {
							x = parentItem.getTranslateX();
							y = parentItem.getTranslateY() + dy;
							while (usedLocations.contains(new Point2D(x, y))) {
								y += dy;
							}
						}
						item.setTranslateX(x);
						item.setTranslateY(y);
						var edgeItem = edgeItemTable.get(parent, node);
						if (edgeItem == null) {
							edgeItem = new WorkflowEdgeItem(parentItem, item);
							edgeItemTable.put(parent, node, edgeItem);
							edgeItems.add(edgeItem);
						} else if (!edgeItems.contains(edgeItem))
							edgeItems.add(edgeItem);
						usedLocations.add(new Point2D(item.getTranslateX(), item.getTranslateY()));
						updateAllBelow(node);
					}
				}
			}
		}
	}

	public Map<WorkflowNode, WorkflowNodeItem> getNodeItemMap() {
		return nodeItemMap;
	}

	public Table<WorkflowNode, WorkflowNode, WorkflowEdgeItem> getEdgeItemTable() {
		return edgeItemTable;
	}
}
