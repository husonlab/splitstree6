/*
 *  WorkflowTabPresenter.java Copyright (C) 2024 Daniel H. Huson
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


import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.SetChangeListener;
import javafx.scene.Group;
import jloda.fx.find.FindToolBar;
import jloda.fx.util.SelectionEffectBlue;
import jloda.fx.util.SwipeUtils;
import jloda.fx.workflow.WorkflowNode;
import splitstree6.data.ViewBlock;
import splitstree6.tabs.IDisplayTabPresenter;
import splitstree6.window.MainWindow;
import splitstree6.workflow.AlgorithmNode;
import splitstree6.workflow.DataNode;
import splitstree6.workflow.commands.DeleteCommand;
import splitstree6.workflow.commands.DuplicateCommand;

import static splitstree6.contextmenus.datanode.DataNodeContextMenuPresenter.createAddAlgorithmMenuItems;

/**
 * workflow tab presenter
 * Daniel Huson, 10.2021
 */
public class WorkflowTabPresenter implements IDisplayTabPresenter {
	private final MainWindow mainWindow;
	private final WorkflowTab workflowTab;
	private final WorkflowTabController controller;
	private final Group nodeItemsGroup = new Group();
	private final WorkflowTabLayout workflowTabLayout;

	private final ObjectProperty<AlgorithmNode> nodeToDuplicateOrDelete = new SimpleObjectProperty<>(null);

	public WorkflowTabPresenter(MainWindow mainWindow, WorkflowTab workflowTab) {
		this.mainWindow = mainWindow;
		this.workflowTab = workflowTab;
		this.controller = workflowTab.getController();
		var workflow = mainWindow.getWorkflow();

		var edgeItemsGroup = new Group();
		controller.getMainPane().getChildren().addAll(edgeItemsGroup, nodeItemsGroup);

		workflowTabLayout = new WorkflowTabLayout(workflowTab, nodeItemsGroup, edgeItemsGroup);

		{
			var scrollPane = controller.getScrollPane();

			scrollPane.prefWidthProperty().bind(controller.getMainPane().widthProperty());
			scrollPane.prefHeightProperty().bind(controller.getMainPane().heightProperty());

			scrollPane.setLockAspectRatio(true);
			scrollPane.setRequireShiftOrControlToZoom(false);
			scrollPane.setPannable(true);
		}

		workflow.getSelectionModel().getSelectedItems().addListener((SetChangeListener<WorkflowNode>) e -> {
			if (e.wasAdded()) {
				var item = workflowTab.getNodeItemMap().get(e.getElementAdded());
				if (item != null)
					item.setEffect(SelectionEffectBlue.getInstance());
			} else if (e.wasRemoved()) {
				var item = workflowTab.getNodeItemMap().get(e.getElementRemoved());
				if (item != null)
					item.setEffect(null);
			}
			if (workflow.getSelectionModel().size() == 1) {
				var node = workflow.getSelectionModel().getSelectedItem();
				nodeToDuplicateOrDelete.set(node instanceof AlgorithmNode && mainWindow.getWorkflow().isDerivedNode(node) ? (AlgorithmNode) node : null);
				if (node instanceof DataNode<?> dataNode) {
					controller.getAddMenuButton().getItems().setAll(createAddAlgorithmMenuItems(mainWindow, workflowTab.getUndoManager(), dataNode));
				}
			} else {
				nodeToDuplicateOrDelete.set(null);
				controller.getAddMenuButton().getItems().clear();
			}
		});

		controller.getAddMenuButton().disableProperty().bind(Bindings.isEmpty(controller.getAddMenuButton().getItems()));

		controller.getMainPane().setOnMouseClicked(e -> workflow.getSelectionModel().clearSelection());

		controller.getProgressIndicator().visibleProperty().bind(mainWindow.getWorkflow().runningProperty());

		controller.getEditButton().setOnAction(e -> {
			if (workflow.getSelectionModel().size() == 1) {
				var workflowNode = workflow.getSelectionModel().getSelectedItem();
				if (workflowNode instanceof DataNode dataNode) {
					if (dataNode.getDataBlock() instanceof ViewBlock viewBlock) {
						mainWindow.getController().getMainTabPane().getSelectionModel().select(viewBlock.getViewTab());
					} else
						mainWindow.getTextTabsManager().showDataNodeTab(dataNode, true);
				} else if (workflowNode instanceof AlgorithmNode algorithmNode)
					mainWindow.getAlgorithmTabsManager().showTab(algorithmNode, true);
			}
		});
		controller.getEditButton().disableProperty().bind(Bindings.size(workflow.getSelectionModel().getSelectedItems()).isNotEqualTo(1));

		controller.getDuplicateButton().setOnAction(e -> workflowTab.getUndoManager().doAndAdd(DuplicateCommand.create(mainWindow.getWorkflow(), nodeToDuplicateOrDelete.get())));
		controller.getDuplicateButton().disableProperty().bind(nodeToDuplicateOrDelete.isNull());

		controller.getDeleteButton().setOnAction(e -> workflowTab.getUndoManager().doAndAdd(DeleteCommand.create(mainWindow.getWorkflow(), nodeToDuplicateOrDelete.get())));
		controller.getDeleteButton().disableProperty().bind(nodeToDuplicateOrDelete.isNull());

		controller.getZoomInButton().setOnAction(e -> controller.getScrollPane().zoomBy(1.1, 1.1));
		controller.getZoomInButton().disableProperty().bind(Bindings.isEmpty(mainWindow.getWorkflow().nodes()));
		controller.getZoomOutButton().setOnAction(e -> controller.getScrollPane().zoomBy(1 / 1.1, 1 / 1.1));
		controller.getZoomOutButton().disableProperty().bind(Bindings.isEmpty(mainWindow.getWorkflow().nodes()));

	}

	public void setupMenuItems() {
		var mainController = mainWindow.getController();
		var workflow = mainWindow.getWorkflow();

		mainController.getCutMenuItem().setOnAction(null);
		mainController.getCopyMenuItem().setOnAction(null);
		mainController.getPasteMenuItem().setOnAction(null);

		mainController.getCopyImageMenuItem().setOnAction(null);


		mainController.getDuplicateMenuItem().setOnAction(e -> controller.getDuplicateButton().getOnAction().handle(e));
		mainController.getDuplicateMenuItem().disableProperty().bind(controller.getDuplicateButton().disableProperty());

		mainController.getDeleteMenuItem().setOnAction(e -> controller.getDeleteButton().getOnAction().handle(e));
		mainController.getDeleteMenuItem().disableProperty().bind(controller.getDeleteButton().disableProperty());

		mainController.getFindMenuItem().setOnAction(null);
		mainController.getFindAgainMenuItem().setOnAction(null);
		mainController.getFindMenuItem().setDisable(false);

		// controller.getReplaceMenuItem().setOnAction(null);

		mainController.getSelectAllMenuItem().setOnAction(e -> workflow.getSelectionModel().selectAll(workflow.nodes()));
		mainController.getSelectNoneMenuItem().setOnAction(e -> workflow.getSelectionModel().clearSelection());
		mainController.getSelectInverseMenuItem().setOnAction(e -> {
			workflow.nodes().forEach(n -> Platform.runLater(() -> workflow.getSelectionModel().toggleSelection(n)));
		});

		mainController.getIncreaseFontSizeMenuItem().setOnAction(controller.getZoomInButton().getOnAction());
		mainController.getIncreaseFontSizeMenuItem().disableProperty().bind(controller.getZoomInButton().disableProperty());
		mainController.getDecreaseFontSizeMenuItem().setOnAction(controller.getZoomOutButton().getOnAction());
		mainController.getDecreaseFontSizeMenuItem().disableProperty().bind(controller.getZoomOutButton().disableProperty());

		mainController.getZoomInMenuItem().setOnAction(null);
		mainController.getZoomOutMenuItem().setOnAction(null);

		SwipeUtils.setConsumeSwipes(controller.getAnchorPane());
	}

	public WorkflowTabLayout getWorkflowTabLayout() {
		return workflowTabLayout;
	}

	@Override
	public FindToolBar getFindToolBar() {
		return null;
	}

	@Override
	public boolean allowFindReplace() {
		return false;
	}

	public void processSelectButtonPressed() {
		var workflow = mainWindow.getWorkflow();
		if (workflow.getSelectionModel().getSelectedItems().size() < workflow.getNumberOfNodes()) {
			workflow.getSelectionModel().selectAll(workflow.nodes());
		} else workflow.getSelectionModel().clearSelection();
	}
}
