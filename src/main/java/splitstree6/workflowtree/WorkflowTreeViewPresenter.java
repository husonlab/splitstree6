/*
 *  WorkflowTreeViewPresenter.java Copyright (C) 2024 Daniel H. Huson
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

import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.ListChangeListener;
import javafx.collections.SetChangeListener;
import javafx.scene.SnapshotParameters;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TreeItem;
import jloda.fx.find.FindToolBar;
import jloda.fx.util.ClipboardUtils;
import jloda.fx.util.ProgramProperties;
import jloda.fx.workflow.WorkflowNode;
import splitstree6.data.SplitsBlock;
import splitstree6.data.TreesBlock;
import splitstree6.data.ViewBlock;
import splitstree6.io.nexus.NexusExporter;
import splitstree6.io.nexus.workflow.WorkflowNexusOutput;
import splitstree6.tabs.IDisplayTabPresenter;
import splitstree6.window.MainWindow;
import splitstree6.workflow.AlgorithmNode;
import splitstree6.workflow.DataNode;
import splitstree6.workflow.commands.DeleteCommand;

import java.io.IOException;
import java.io.StringWriter;
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

		var algorithmNodeSelected = new SimpleBooleanProperty(this, "algorithmSelected", false);
		var dataNodeSelected = new SimpleBooleanProperty(this, "dataSelected", false);
		var rootNodeSelected = new SimpleBooleanProperty(this, "rootNodeSelected", false);

		treeView.getSelectionModel().selectedItemProperty().addListener((v, o, n) -> {
			algorithmNodeSelected.set(n instanceof WorkflowTreeItem item && item.getWorkflowNode() instanceof AlgorithmNode<?, ?>);
			dataNodeSelected.set(n instanceof WorkflowTreeItem item && item.getWorkflowNode() instanceof DataNode<?>);
			rootNodeSelected.set(n == treeView.getRoot());
		});

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

		dataNodeSelected.addListener((v, o, n) -> {
			if (n && treeView.getSelectionModel().getSelectedItem() instanceof WorkflowTreeItem item && item.getWorkflowNode() instanceof DataNode<?> dataNode) {
				controller.getAddMenuButton().getItems().setAll(createAddAlgorithmMenuItems(mainWindow, workflowTreeView.getUndoManager(), dataNode));
			} else {
				controller.getAddMenuButton().getItems().clear();
			}
		});

		treeView.getSelectionModel().selectedItemProperty().addListener((v, o, n) -> {
			if (n instanceof WorkflowTreeItem item && item.getWorkflowNode() instanceof DataNode<?> dataNode) {
				dataNodeSelected.set(true);
			} else {
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

		controller.getCopyButton().setOnAction(e -> {
			try (var w = new StringWriter()) {
				if (treeView.getSelectionModel().getSelectedItem() instanceof WorkflowTreeItem item
					&& item.getWorkflowNode() instanceof DataNode<?> dataNode) {
					if (dataNode.getDataBlock() instanceof ViewBlock viewBlock) {
						if (ProgramProperties.isDesktop()) { // todo: fix problem with pasting image when not running on desktop
							var image = viewBlock.getViewTab().getMainNode().snapshot(new SnapshotParameters(), null);
							ClipboardUtils.put(viewBlock.getName(), image, null);
							return;
						} else
							w.write(viewBlock.getName());
					} else if (dataNode.getDataBlock() instanceof TreesBlock treesBlock) {
						(new splitstree6.io.writers.trees.NewickWriter()).write(w, mainWindow.getWorkingTaxa(), treesBlock);
					} else if (dataNode.getDataBlock() instanceof SplitsBlock splitsBlock) {
						(new splitstree6.io.writers.splits.NewickWriter()).write(w, mainWindow.getWorkingTaxa(), splitsBlock);
					} else {
						w.write("#nexus\n");
						(new NexusExporter()).export(w, mainWindow.getWorkingTaxa(), dataNode.getDataBlock());
					}
				} else if (treeView.getSelectionModel().getSelectedItem() == treeView.getRoot()) {
					(new WorkflowNexusOutput()).save(mainWindow.getWorkflow(), w, false);
				}
				ClipboardUtils.putString(w.toString());
			} catch (IOException ignored) {
			}
		});
		controller.getCopyButton().disableProperty().bind(dataNodeSelected.not().and(rootNodeSelected.not()));
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
