/*
 * WorkflowTreeViewPresenter.java Copyright (C) 2022 Daniel H. Huson
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

package splitstree6.workflowtree;

import javafx.beans.binding.Bindings;
import javafx.collections.ListChangeListener;
import javafx.collections.SetChangeListener;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TreeItem;
import jloda.fx.workflow.WorkflowNode;
import splitstree6.tabs.IDisplayTabPresenter;
import splitstree6.window.MainWindow;

import java.util.LinkedList;
import java.util.stream.Collectors;

public class WorkflowTreeViewPresenter implements IDisplayTabPresenter {
	private final MainWindow mainWindow;
	private final WorkflowTreeView workflowTreeView;

	public WorkflowTreeViewPresenter(MainWindow mainWindow, WorkflowTreeView workflowTreeView) {
		this.mainWindow = mainWindow;
		this.workflowTreeView = workflowTreeView;
		var workflow = mainWindow.getWorkflow();
		var treeView = workflowTreeView.getController().getWorkflowTreeView();

		treeView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);

		treeView.setRoot(new WorkflowTreeItem(mainWindow));

		var layout = new WorkflowTreeViewLayout(mainWindow, workflowTreeView);

		treeView.getSelectionModel().getSelectedItems().addListener((ListChangeListener<TreeItem<String>>) e -> {
			while (e.next()) {
				if (e.wasAdded()) {
					workflow.getSelectionModel().selectAll(e.getAddedSubList().stream()
							.map(a -> ((WorkflowTreeItem) a).getWorkflowNode()).collect(Collectors.toList()));
				}
				if (e.wasRemoved()) {
					workflow.getSelectionModel().clearSelection(e.getRemoved().stream()
							.map(a -> ((WorkflowTreeItem) a).getWorkflowNode()).collect(Collectors.toList()));
				}
			}
		});

		workflow.getSelectionModel().getSelectedItems().addListener((SetChangeListener<? super WorkflowNode>) e -> {
			if (e.wasAdded()) {
				var item = layout.getNodeItemMap().get(e.getElementAdded());
				if (item != null)
					treeView.getSelectionModel().select(item);
			}
			if (e.wasRemoved()) {
				var item = layout.getNodeItemMap().get(e.getElementRemoved());
				if (item != null) {
					var index = treeView.getRow(item);
					treeView.getSelectionModel().clearSelection(index);
				}
			}
		});
	}

	public void setupMenuItems() {
		var controller = mainWindow.getController();
		var tabController = workflowTreeView.getController();

		controller.getCopyMenuItem().setOnAction(null);

		controller.getFindMenuItem().setOnAction(null);
		controller.getFindAgainMenuItem().setOnAction(null);

		controller.getSelectAllMenuItem().setOnAction(null);
		controller.getSelectNoneMenuItem().setOnAction(null);

		controller.getIncreaseFontSizeMenuItem().setOnAction(null);
		controller.getDecreaseFontSizeMenuItem().setOnAction(null);

		controller.getZoomInMenuItem().setOnAction(null);
		controller.getZoomOutMenuItem().setOnAction(null);

		var treeView = tabController.getWorkflowTreeView();
		treeView.getStyleClass().add("viewer-background");

		tabController.getCollapseAllButton().setOnAction((e) -> treeView.getRoot().setExpanded(false));

		tabController.getExpandAllButton().setOnAction((e) -> {
			final var queue = new LinkedList<TreeItem<String>>();
			queue.add(treeView.getRoot());
			while (queue.size() > 0) {
				final var item = queue.poll();
				item.setExpanded(true);
				queue.addAll(item.getChildren());
			}
		});

		tabController.getShowButton().setOnAction((e) -> {
			for (var item : treeView.getSelectionModel().getSelectedItems()) {
				//final Point2D point2D = item.getGraphic().localToScreen(item.getGraphic().getLayoutX(), item.getGraphic().getLayoutY());
				((WorkflowTreeItem) item).showView();
			}
		});
		tabController.getShowButton().disableProperty().bind(Bindings.isEmpty(treeView.getSelectionModel().getSelectedItems()));
	}
}
