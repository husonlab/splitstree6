/*
 *  WorkflowTabPresenter.java Copyright (C) 2021 Daniel H. Huson
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


import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.SetChangeListener;
import javafx.scene.Group;
import javafx.scene.effect.DropShadow;
import javafx.scene.paint.Color;
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
			scrollPane.setRequireShiftOrControlToZoom(true);
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
					item.setEffect(getDropShadow());
			}
			if (workflow.getSelectionModel().size() == 1) {
				var node = workflow.getSelectionModel().getSelectedItem();
				nodeToDuplicateOrDelete.set(node instanceof AlgorithmNode && mainWindow.getWorkflow().isDerivedNode(node) ? (AlgorithmNode) node : null);
			} else
				nodeToDuplicateOrDelete.set(null);
		});

		controller.getMainPane().setOnMouseClicked(e -> workflow.getSelectionModel().clearSelection());

		controller.getProgressIndicator().visibleProperty().bind(mainWindow.getWorkflow().runningProperty());

		controller.getOpenButton().setOnAction(mainWindow.getController().getOpenMenuItem().getOnAction());
		controller.getSaveButton().setOnAction(mainWindow.getController().getSaveAsMenuItem().getOnAction());
		controller.getSaveButton().disableProperty().bind(mainWindow.getController().getSaveAsMenuItem().disableProperty());

		controller.getPrintButton().setOnAction(mainWindow.getController().getPrintMenuItem().getOnAction());
		controller.getPrintButton().disableProperty().bind(mainWindow.getController().getPrintMenuItem().disableProperty());
	}


	public void setupMenuItems() {
		var windowController = mainWindow.getController();
		var workflow = mainWindow.getWorkflow();

		windowController.getCutMenuItem().setOnAction(null);
		windowController.getCopyMenuItem().setOnAction(null);

		windowController.getCopyImageMenuItem().setOnAction(null);

		windowController.getPasteMenuItem().setOnAction(null);

		windowController.getDuplicateMenuItem().setOnAction(e -> {
			workflowTab.getUndoManager().doAndAdd(DuplicateCommand.create(mainWindow.getWorkflow(), nodeToDuplicateOrDelete.get()));
		});
		windowController.getDuplicateMenuItem().disableProperty().bind(nodeToDuplicateOrDelete.isNull());

		windowController.getDeleteMenuItem().setOnAction(e -> workflowTab.getUndoManager().doAndAdd(DeleteCommand.create(mainWindow.getWorkflow(), nodeToDuplicateOrDelete.get())));
		windowController.getDeleteMenuItem().disableProperty().bind(nodeToDuplicateOrDelete.isNull());

		windowController.getFindMenuItem().setOnAction(null);
		windowController.getFindAgainMenuItem().setOnAction(null);

		// controller.getReplaceMenuItem().setOnAction(null);

		windowController.getSelectAllMenuItem().setOnAction(e -> workflow.getSelectionModel().selectAll(workflow.nodes()));
		windowController.getSelectNoneMenuItem().setOnAction(e -> workflow.getSelectionModel().clearSelection());

		windowController.getIncreaseFontSizeMenuItem().setOnAction(null);
		windowController.getDecreaseFontSizeMenuItem().setOnAction(null);

		windowController.getZoomInMenuItem().setOnAction(controller.getZoomInButton().getOnAction());
		windowController.getZoomOutMenuItem().setOnAction(controller.getZoomOutButton().getOnAction());

		windowController.getResetMenuItem().setOnAction(controller.getZoomButton().getOnAction());
	}

	public static DropShadow getDropShadow() {
		var dropShadow = new DropShadow();
		dropShadow.setOffsetX(0.5);
		dropShadow.setOffsetY(0.5);
		dropShadow.setColor(Color.DARKGREY);
		return dropShadow;
	}

	public WorkflowTabLayout getWorkflowTabLayout() {
		return workflowTabLayout;
	}
}
