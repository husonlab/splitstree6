/*
 * WorkflowTreeViewPresenter.java Copyright (C) 2023 Daniel H. Huson
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

import javafx.collections.ListChangeListener;
import javafx.collections.SetChangeListener;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TreeItem;
import jloda.fx.find.FindToolBar;
import jloda.fx.workflow.WorkflowNode;
import splitstree6.tabs.IDisplayTabPresenter;
import splitstree6.window.MainWindow;

import java.util.Objects;
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
					workflow.getSelectionModel().selectAll(e.getAddedSubList().stream().filter(Objects::nonNull)
							.map(a -> ((WorkflowTreeItem) a).getWorkflowNode()).collect(Collectors.toList()));
				}
				if (e.wasRemoved()) {
					workflow.getSelectionModel().clearSelection(e.getRemoved().stream().filter(Objects::nonNull)
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
		var mainController = mainWindow.getController();
		var tabController = workflowTreeView.getController();

		mainController.getCopyMenuItem().setOnAction(null);

		mainController.getIncreaseFontSizeMenuItem().setOnAction(null);
		mainController.getDecreaseFontSizeMenuItem().setOnAction(null);

		mainController.getZoomInMenuItem().setOnAction(null);
		mainController.getZoomOutMenuItem().setOnAction(null);

		var treeView = tabController.getWorkflowTreeView();
		treeView.getStyleClass().add("viewer-background");
	}

	@Override
	public FindToolBar getFindToolBar() {
		return null;
	}

	@Override
	public boolean allowFindReplace() {
		return false;
	}
}
