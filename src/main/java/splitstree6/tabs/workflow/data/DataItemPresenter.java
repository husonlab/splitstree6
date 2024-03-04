/*
 * DataItemPresenter.java Copyright (C) 2024 Daniel H. Huson
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

package splitstree6.tabs.workflow.data;

import javafx.beans.InvalidationListener;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.scene.layout.Pane;
import jloda.fx.icons.MaterialIcons;
import splitstree6.contextmenus.datanode.DataNodeContextMenu;
import splitstree6.data.ReportBlock;
import splitstree6.data.ViewBlock;
import splitstree6.tabs.workflow.WorkflowTab;
import splitstree6.window.MainWindow;
import splitstree6.workflow.DataBlock;

import static splitstree6.contextmenus.datanode.DataNodeContextMenuPresenter.createAddAlgorithmMenuItems;

/**
 * data item presenter
 * Daniel Huson, 10.21
 */
public class DataItemPresenter<D extends DataBlock> {

	public DataItemPresenter(MainWindow mainWindow, WorkflowTab workflowTab, DataItem<D> dataItem) {
		var dataNode = dataItem.getWorkflowNode();
		var controller = dataItem.getController();

		var selected = new SimpleBooleanProperty(false);
		mainWindow.getWorkflow().getSelectionModel().getSelectedItems().addListener((InvalidationListener) e -> selected.set(mainWindow.getWorkflow().getSelectionModel().isSelected(dataNode)));

		controller.getEditButton().setOnAction(e -> {
			if (dataItem.getWorkflowNode().getDataBlock() instanceof ViewBlock block) {
				block.getViewTab().getTabPane().getSelectionModel().select(block.getViewTab());
			} else if (dataItem.getWorkflowNode().getDataBlock() instanceof ReportBlock block) {
				block.getViewTab().getTabPane().getSelectionModel().select(block.getViewTab());
			} else {
				mainWindow.getTextTabsManager().showDataNodeTab(dataNode, true);
			}
		});
		controller.getEditButton().disableProperty().bind((selected.and(dataNode.validProperty()).not()));

		controller.getNameLabel().textProperty().addListener((v, o, n) -> {
			if ("Alignment".equals(n)) {
				if (controller.getAddMenuButton().getParent() instanceof Pane pane) {
					pane.getChildren().remove(controller.getAddMenuButton());
				}
				if (controller.getStatusPane().getParent() instanceof Pane pane) {
					pane.getChildren().remove(controller.getStatusPane());
				}
			}
		});

		controller.getNameLabel().textProperty().bind(dataNode.titleProperty());

		controller.getInfoLabel().setText(String.format("size: %,d", dataNode.getDataBlock().size()));
		if (dataNode.getDataBlock().size() > 0)
			controller.getStatusPane().getChildren().setAll(MaterialIcons.graphic("done", "-fx-text-fill: green;"));
		else
			controller.getStatusPane().getChildren().setAll(MaterialIcons.graphic("schedule", "-fx-text-fill: yellow;"));

		dataNode.allParentsValidProperty().addListener((v, o, n) -> {
			if (n && dataNode.isValid()) {
				controller.getInfoLabel().setText(String.format("size: %,d", dataNode.getDataBlock().size()));
				controller.getStatusPane().getChildren().setAll(MaterialIcons.graphic("done", "-fx-text-fill: green;"));
			} else {
				controller.getInfoLabel().setText("-");
				controller.getStatusPane().getChildren().setAll(MaterialIcons.graphic("schedule", "-fx-text-fill: yellow;"));
			}
		});

		controller.getNameLabel().setGraphic(MaterialIcons.graphic("dataset"));

		if (!mainWindow.getWorkflow().isDerivedNode(dataNode)) {
			controller.getNameLabel().setStyle("-fx-text-fill: darkgray");
			controller.getInfoLabel().setStyle("-fx-text-fill: darkgray");
			if (controller.getNameLabel().getGraphic() != null)
				controller.getNameLabel().getGraphic().setOpacity(0.5);
		}

		dataItem.setOnContextMenuRequested(e -> {
			if (selected.get()) {
				DataNodeContextMenu.show(mainWindow, workflowTab.getUndoManager(), dataNode, dataItem, e.getScreenX(), e.getScreenY());
			}
		});

		controller.getAddMenuButton().getItems().setAll(createAddAlgorithmMenuItems(mainWindow, workflowTab.getUndoManager(), dataNode));

	}
}
