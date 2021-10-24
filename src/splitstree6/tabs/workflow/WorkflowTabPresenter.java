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


import javafx.collections.SetChangeListener;
import javafx.scene.Group;
import javafx.scene.effect.DropShadow;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import jloda.fx.util.SelectionEffectBlue;
import splitstree6.tabs.IDisplayTabPresenter;
import splitstree6.window.MainWindow;

import java.util.stream.Collectors;

/**
 * workflow tab presenter
 * Daniel Huson, 10.2021
 */
public class WorkflowTabPresenter implements IDisplayTabPresenter {
	private final MainWindow mainWindow;
	private final WorkflowTab workflowTab;
	private final Group nodeItemsGroup = new Group();
	private final WorkflowTabLayout workflowTabLayout;

	public WorkflowTabPresenter(MainWindow mainWindow, WorkflowTab workflowTab) {
		this.mainWindow = mainWindow;
		this.workflowTab = workflowTab;
		var tabController = workflowTab.getController();

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

		workflowTab.getSelectionModel().getSelectedItems().addListener((SetChangeListener<? super Pane>) e -> {
			if (e.wasAdded()) {
				e.getElementAdded().setEffect(SelectionEffectBlue.getInstance());
			} else if (e.wasRemoved()) {
				e.getElementRemoved().setEffect(getDropShadow());
			}
		});

		tabController.getMainPane().setOnMouseClicked(e -> workflowTab.getSelectionModel().clearSelection());
	}


	public void setup() {
		var controller = mainWindow.getController();
		var tabController = workflowTab.getController();

		controller.getCutMenuItem().setOnAction(null);
		controller.getCopyMenuItem().setOnAction(null);

		controller.getCopyImageMenuItem().setOnAction(null);

		controller.getPasteMenuItem().setOnAction(null);

		controller.getUndoMenuItem().setOnAction(e -> workflowTab.getUndoManager().undo());
		controller.getUndoMenuItem().disableProperty().bind(workflowTab.getUndoManager().undoableProperty().not());
		controller.getRedoMenuItem().setOnAction(e -> workflowTab.getUndoManager().redo());
		controller.getRedoMenuItem().disableProperty().bind(workflowTab.getUndoManager().redoableProperty().not());

		controller.getDuplicateMenuItem().setOnAction(null);
		controller.getDeleteMenuItem().setOnAction(null);

		controller.getFindMenuItem().setOnAction(null);
		controller.getFindAgainMenuItem().setOnAction(null);

		// controller.getReplaceMenuItem().setOnAction(null);

		controller.getSelectAllMenuItem().setOnAction(e -> workflowTab.getSelectionModel().selectAll(nodeItemsGroup.getChildren().stream().map(a -> (WorkflowNodeItem) a).collect(Collectors.toList())));
		controller.getSelectNoneMenuItem().setOnAction(e -> workflowTab.getSelectionModel().clearSelection());

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
