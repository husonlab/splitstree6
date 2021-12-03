/*
 *  WorkflowNodeItem.java Copyright (C) 2021 Daniel H. Huson
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

import javafx.scene.layout.Pane;
import jloda.fx.selection.SelectionModel;
import jloda.fx.workflow.WorkflowNode;
import splitstree6.workflow.Workflow;

import java.util.HashSet;
import java.util.Objects;
import java.util.stream.Collectors;

abstract public class WorkflowNodeItem extends Pane {
	static private double mouseDownX;
	static private double mouseDownY;
	static private double mouseMovedX;
	static private double mouseMovedY;
	protected final WorkflowNode node;

	private final Workflow workflow;
	private final WorkflowTab workflowTab;


	public WorkflowNodeItem(Workflow workflow, WorkflowTab workflowTab, WorkflowNode node) {
		this.workflow = workflow;
		this.workflowTab = workflowTab;
		setupMouseInteraction(workflow.getSelectionModel());
		this.node = node;
	}

	public void move(double dx, double dy) {
		setTranslateX(getTranslateX() + dx);
		setTranslateY(getTranslateY() + dy);
	}

	public void setupMouseInteraction(SelectionModel<WorkflowNode> selectionModel) {
		var nodeItemMap = workflowTab.getPresenter().getWorkflowTabLayout().getNodeItemMap();

		setOnMousePressed(e -> {
			mouseDownX = mouseMovedX = e.getScreenX();
			mouseDownY = mouseMovedY = e.getScreenY();
		});

		setOnMouseClicked(e -> {
			if (e.getClickCount() == 1) {
				if (e.isStillSincePress()) {
					if (!e.isShiftDown() && !e.isShortcutDown())
						selectionModel.clearSelection();
					if (e.isShiftDown()) {
						selectionModel.setSelected(getWorkflowNode(), true);
						var toSelect = new HashSet<WorkflowNode>();
						collectAllToSelectRec(false, this, selectionModel, new HashSet<>(), toSelect);
						collectAllToSelectRec(true, this, selectionModel, new HashSet<>(), toSelect);
						selectionModel.selectAll(toSelect);
					} else
						selectionModel.toggleSelection(getWorkflowNode());
				}
				e.consume();
			}
		});
		setOnMouseDragged(e -> {
			if (selectionModel.isSelected(getWorkflowNode())) {
				var deltaX = e.getScreenX() - mouseMovedX;
				var deltaY = e.getScreenY() - mouseMovedY;
				for (var other : selectionModel.getSelectedItems()) {
					var otherItem = nodeItemMap.get(other);
					if (otherItem != null)
						otherItem.move(deltaX, deltaY);
				}
				mouseMovedX = e.getScreenX();
				mouseMovedY = e.getScreenY();
			}
			e.consume();
		});
		setOnMouseReleased(e -> {
			if (!e.isStillSincePress() && selectionModel.isSelected(getWorkflowNode())) {
				var deltaX = e.getScreenX() - mouseDownX;
				var deltaY = e.getScreenY() - mouseDownY;
				var items = selectionModel.getSelectedItems().stream()
						.map(nodeItemMap::get).filter(Objects::nonNull).collect(Collectors.toList());
				workflowTab.getUndoManager().add("move",
						() -> items.forEach(item -> item.move(-deltaX, -deltaY)),
						() -> items.forEach(item -> item.move(deltaX, deltaY)));
				e.consume();
			}
		});
	}

	/**
	 * collect all other nodes to select above or below
	 */
	private void collectAllToSelectRec(boolean up, WorkflowNodeItem item, SelectionModel<WorkflowNode> selectionModel, HashSet<WorkflowNode> path, HashSet<WorkflowNode> toSelect) {
		for (var other : up ? item.getWorkflowNode().getParents() : item.getWorkflowNode().getChildren()) {
			var otherItem = workflowTab.getPresenter().getWorkflowTabLayout().getNodeItemMap().get(other);
			if (otherItem != null) {
				if (selectionModel.isSelected(otherItem.getWorkflowNode()))
					toSelect.addAll(path);
				path.add(otherItem.getWorkflowNode());
				collectAllToSelectRec(up, otherItem, selectionModel, path, toSelect);
				path.remove(otherItem.getWorkflowNode());
			}
		}
	}


	abstract public WorkflowNode getWorkflowNode();
}
