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

import java.util.ArrayList;
import java.util.HashSet;

abstract public class WorkflowNodeItem extends Pane {
	static private double mouseDownX;
	static private double mouseDownY;
	static private double mouseMovedX;
	static private double mouseMovedY;

	private final Workflow workflow;
	private final WorkflowTab workflowTab;


	public WorkflowNodeItem(Workflow workflow, WorkflowTab workflowTab) {
		this.workflow = workflow;
		this.workflowTab = workflowTab;
		setupMouseInteraction(workflowTab.getSelectionModel());
	}

	public void move(double dx, double dy) {
		setTranslateX(getTranslateX() + dx);
		setTranslateY(getTranslateY() + dy);
	}

	public void setupMouseInteraction(SelectionModel<WorkflowNodeItem> selectionModel) {
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
						selectionModel.setSelected(this, true);
						var toSelect = new HashSet<WorkflowNodeItem>();
						collectAllToSelectRec(false, this, selectionModel, new HashSet<>(), toSelect);
						collectAllToSelectRec(true, this, selectionModel, new HashSet<>(), toSelect);
						selectionModel.selectAll(toSelect);
					} else
						selectionModel.toggleSelection(this);
				}
				e.consume();
			}
		});
		setOnMouseDragged(e -> {
			if (selectionModel.isSelected(this)) {
				var deltaX = e.getScreenX() - mouseMovedX;
				var deltaY = e.getScreenY() - mouseMovedY;
				for (var other : selectionModel.getSelectedItems()) {
					other.move(deltaX, deltaY);
				}
				mouseMovedX = e.getScreenX();
				mouseMovedY = e.getScreenY();
			}
			e.consume();
		});
		setOnMouseReleased(e -> {
			if (!e.isStillSincePress() && selectionModel.isSelected(this)) {
				var deltaX = e.getScreenX() - mouseDownX;
				var deltaY = e.getScreenY() - mouseDownY;
				var items = new ArrayList<>(selectionModel.getSelectedItems());
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
	private void collectAllToSelectRec(boolean up, WorkflowNodeItem item, SelectionModel<WorkflowNodeItem> selectionModel, HashSet<WorkflowNodeItem> path, HashSet<WorkflowNodeItem> toSelect) {
		for (var other : up ? item.getWorkflowNode().getParents() : item.getWorkflowNode().getChildren()) {
			var otherItem = workflowTab.getPresenter().getWorkflowTabLayout().getNodeItemMap().get(other);
			if (otherItem != null) {
				if (selectionModel.isSelected(otherItem))
					toSelect.addAll(path);
				path.add(otherItem);
				collectAllToSelectRec(up, otherItem, selectionModel, path, toSelect);
				path.remove(otherItem);
			}
		}
	}


	abstract public WorkflowNode getWorkflowNode();
}
