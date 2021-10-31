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
	private final Group nodeItemsGroup = new Group();
	private final WorkflowTabLayout workflowTabLayout;

	private final ObjectProperty<AlgorithmNode> nodeToDuplicateOrDelete = new SimpleObjectProperty<>(null);

	public WorkflowTabPresenter(MainWindow mainWindow, WorkflowTab workflowTab) {
		this.mainWindow = mainWindow;
		this.workflowTab = workflowTab;
		var tabController = workflowTab.getController();
		var workflow = mainWindow.getWorkflow();

		var edgeItemsGroup = new Group();
		tabController.getMainPane().getChildren().addAll(edgeItemsGroup, nodeItemsGroup);

		workflowTabLayout = new WorkflowTabLayout(workflowTab, nodeItemsGroup, edgeItemsGroup);

		{
			var scrollPane = tabController.getScrollPane();

			scrollPane.prefWidthProperty().bind(tabController.getMainPane().widthProperty());
			scrollPane.prefHeightProperty().bind(tabController.getMainPane().heightProperty());

			scrollPane.setLockAspectRatio(true);
			scrollPane.setRequireShiftOrControlToZoom(true);
			tabController.getZoomButton().setOnAction(e -> scrollPane.resetZoom());
			tabController.getZoomInButton().setOnAction(e -> scrollPane.zoomBy(1.1, 1.1));
			tabController.getZoomOutButton().setOnAction(e -> scrollPane.zoomBy(1 / 1.1, 1 / 1.1));
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

		tabController.getMainPane().setOnMouseClicked(e -> workflow.getSelectionModel().clearSelection());

		tabController.getOpenButton().setOnAction(e -> mainWindow.getController().getOpenMenuItem().getOnAction().handle(e));
		tabController.getSaveButton().setOnAction(e -> mainWindow.getController().getSaveAsMenuItem().getOnAction().handle(e));
		tabController.getPrintButton().setOnAction(e -> mainWindow.getController().getPrintMenuItem().getOnAction().handle(e));

		tabController.getProgressIndicator().visibleProperty().bind(mainWindow.getWorkflow().runningProperty());
	}


	public void setup() {
		var controller = mainWindow.getController();
		var tabController = workflowTab.getController();
		var workflow = mainWindow.getWorkflow();

		controller.getCutMenuItem().setOnAction(null);
		controller.getCopyMenuItem().setOnAction(null);

		controller.getCopyImageMenuItem().setOnAction(null);

		controller.getPasteMenuItem().setOnAction(null);

		controller.getUndoMenuItem().setOnAction(e -> workflowTab.getUndoManager().undo());
		controller.getUndoMenuItem().disableProperty().bind(workflowTab.getUndoManager().undoableProperty().not());
		controller.getRedoMenuItem().setOnAction(e -> workflowTab.getUndoManager().redo());
		controller.getRedoMenuItem().disableProperty().bind(workflowTab.getUndoManager().redoableProperty().not());

		controller.getDuplicateMenuItem().setOnAction(e -> {
			workflowTab.getUndoManager().doAndAdd(DuplicateCommand.create(mainWindow.getWorkflow(), nodeToDuplicateOrDelete.get()));
		});
		controller.getDuplicateMenuItem().disableProperty().bind(nodeToDuplicateOrDelete.isNull());

		controller.getDeleteMenuItem().setOnAction(e -> workflowTab.getUndoManager().doAndAdd(DeleteCommand.create(mainWindow.getWorkflow(), nodeToDuplicateOrDelete.get())));
		controller.getDeleteMenuItem().disableProperty().bind(nodeToDuplicateOrDelete.isNull());

		controller.getFindMenuItem().setOnAction(null);
		controller.getFindAgainMenuItem().setOnAction(null);

		// controller.getReplaceMenuItem().setOnAction(null);

		controller.getSelectAllMenuItem().setOnAction(e -> workflow.getSelectionModel().selectAll(workflow.nodes()));
		controller.getSelectNoneMenuItem().setOnAction(e -> workflow.getSelectionModel().clearSelection());

		controller.getIncreaseFontSizeMenuItem().setOnAction(null);
		controller.getDecreaseFontSizeMenuItem().setOnAction(null);

		controller.getZoomInMenuItem().setOnAction(tabController.getZoomInButton().getOnAction());
		controller.getZoomOutMenuItem().setOnAction(tabController.getZoomOutButton().getOnAction());

		controller.getResetMenuItem().setOnAction(tabController.getZoomButton().getOnAction());
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
