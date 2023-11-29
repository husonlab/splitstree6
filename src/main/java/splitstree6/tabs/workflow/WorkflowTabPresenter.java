/*
 * WorkflowTabPresenter.java Copyright (C) 2023 Daniel H. Huson
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

package splitstree6.tabs.workflow;


import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.SetChangeListener;
import javafx.scene.Group;
import jloda.fx.util.SelectionEffectBlue;
import jloda.fx.workflow.WorkflowNode;
import splitstree6.tabs.IDisplayTabPresenter;
import splitstree6.window.MainWindow;
import splitstree6.workflow.AlgorithmNode;
import splitstree6.workflow.commands.DeleteCommand;
import splitstree6.workflow.commands.DuplicateCommand;

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

			controller.getZoomButton().setOnAction(e -> scrollPane.resetZoom());
			controller.getZoomInButton().setOnAction(e -> scrollPane.zoomBy(1.1, 1.1));
			controller.getZoomOutButton().setOnAction(e -> scrollPane.zoomBy(1 / 1.1, 1 / 1.1));
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
			} else
				nodeToDuplicateOrDelete.set(null);
		});

		controller.getMainPane().setOnMouseClicked(e -> workflow.getSelectionModel().clearSelection());

		controller.getProgressIndicator().visibleProperty().bind(mainWindow.getWorkflow().runningProperty());
	}

	public void setupMenuItems() {
		var mainController = mainWindow.getController();
		var workflow = mainWindow.getWorkflow();

		mainController.getCutMenuItem().setOnAction(null);
		mainController.getCopyMenuItem().setOnAction(null);

		mainController.getCopyImageMenuItem().setOnAction(null);

		mainController.getPasteMenuItem().setOnAction(null);

		mainController.getDuplicateMenuItem().setOnAction(e -> workflowTab.getUndoManager().doAndAdd(DuplicateCommand.create(mainWindow.getWorkflow(), nodeToDuplicateOrDelete.get())));
		mainController.getDuplicateMenuItem().disableProperty().bind(nodeToDuplicateOrDelete.isNull());

		mainController.getDeleteMenuItem().setOnAction(e -> workflowTab.getUndoManager().doAndAdd(DeleteCommand.create(mainWindow.getWorkflow(), nodeToDuplicateOrDelete.get())));
		mainController.getDeleteMenuItem().disableProperty().bind(nodeToDuplicateOrDelete.isNull());

		mainController.getFindMenuItem().setOnAction(null);
		mainController.getFindAgainMenuItem().setOnAction(null);
		mainController.getFindMenuItem().setDisable(false);

		// controller.getReplaceMenuItem().setOnAction(null);

		mainController.getSelectAllMenuItem().setOnAction(e -> workflow.getSelectionModel().selectAll(workflow.nodes()));
		mainController.getSelectNoneMenuItem().setOnAction(e -> workflow.getSelectionModel().clearSelection());
		mainController.getSelectInverseMenuItem().setOnAction(e -> {
			workflow.nodes().forEach(n -> Platform.runLater(() -> workflow.getSelectionModel().toggleSelection(n)));
		});

		mainController.getIncreaseFontSizeMenuItem().setOnAction(null);
		mainController.getDecreaseFontSizeMenuItem().setOnAction(null);

		mainController.getZoomInMenuItem().setOnAction(controller.getZoomInButton().getOnAction());
		mainController.getZoomOutMenuItem().setOnAction(controller.getZoomOutButton().getOnAction());

		mainController.getResetMenuItem().setOnAction(controller.getZoomButton().getOnAction());
	}

	public WorkflowTabLayout getWorkflowTabLayout() {
		return workflowTabLayout;
	}
}
