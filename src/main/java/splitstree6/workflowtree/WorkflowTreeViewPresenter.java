/*
 * WorkflowTreeViewPresenter.java Copyright (C) 2024 Daniel H. Huson
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
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.ListChangeListener;
import javafx.collections.SetChangeListener;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TreeItem;
import jloda.fx.find.FindToolBar;
import jloda.fx.workflow.WorkflowNode;
import splitstree6.tabs.IDisplayTabPresenter;
import splitstree6.window.MainWindow;
import splitstree6.workflow.AlgorithmNode;
import splitstree6.workflow.DataNode;
import splitstree6.workflow.commands.DeleteCommand;

import java.util.Objects;
import java.util.stream.Collectors;

import static splitstree6.contextmenus.datanode.DataNodeContextMenuPresenter.createAddAlgorithmMenuItems;

public class WorkflowTreeViewPresenter implements IDisplayTabPresenter {
	private final MainWindow mainWindow;
	private final WorkflowTreeView workflowTreeView;

	public WorkflowTreeViewPresenter(MainWindow mainWindow, WorkflowTreeView workflowTreeView) {
		this.mainWindow = mainWindow;
		this.workflowTreeView = workflowTreeView;
		var controller = workflowTreeView.getController();
		var workflow = mainWindow.getWorkflow();
		var treeView = controller.getWorkflowTreeView();

		treeView.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);

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

		var algorithmNodeSelected = new SimpleBooleanProperty(this, "algorithmSelected", false);
		treeView.getSelectionModel().selectedItemProperty().addListener((v, o, n) ->
				algorithmNodeSelected.set(n instanceof WorkflowTreeItem item && item.getWorkflowNode() instanceof AlgorithmNode<?, ?>));
		var dataNodeSelected = new SimpleBooleanProperty(this, "dataSelected", false);
		treeView.getSelectionModel().selectedItemProperty().addListener((v, o, n) -> {
			if (n instanceof WorkflowTreeItem item && item.getWorkflowNode() instanceof DataNode<?> dataNode) {
				dataNodeSelected.set(true);
				controller.getAddMenuButton().getItems().setAll(createAddAlgorithmMenuItems(mainWindow, workflowTreeView.getUndoManager(), dataNode));
			} else {
				dataNodeSelected.set(false);
				controller.getAddMenuButton().getItems().clear();
			}
		});

		controller.getEditButton().setOnAction(e -> {
			if (treeView.getSelectionModel().getSelectedItem() instanceof WorkflowTreeItem item) {
				item.showView();
			}
		});
		controller.getEditButton().disableProperty().bind((algorithmNodeSelected.or(dataNodeSelected)).not());

		controller.getAddMenuButton().disableProperty().bind(Bindings.isEmpty(controller.getAddMenuButton().getItems()));

		controller.getDeleteButton().setOnAction(e -> {
			if (treeView.getSelectionModel().getSelectedItem() instanceof WorkflowTreeItem item
				&& item.getWorkflowNode() instanceof AlgorithmNode<?, ?> algorithmNode) {
				workflowTreeView.getUndoManager().doAndAdd(DeleteCommand.create(workflow, algorithmNode));
			}
		});
		controller.getDeleteButton().disableProperty().bind(algorithmNodeSelected.not());
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
