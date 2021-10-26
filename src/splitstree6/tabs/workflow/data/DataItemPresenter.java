/*
 *  AlgorithmItemPresenter.java Copyright (C) 2021 Daniel H. Huson
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

package splitstree6.tabs.workflow.data;

import javafx.beans.InvalidationListener;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.image.ImageView;
import jloda.fx.util.ResourceManagerFX;
import splitstree6.dialog.attachnode.AttachNodeDialog;
import splitstree6.tabs.workflow.WorkflowTab;
import splitstree6.tabs.workflow.WorkflowTabPresenter;
import splitstree6.window.MainWindow;
import splitstree6.workflow.DataBlock;
import splitstree6.workflow.Workflow;

/**
 * datat
 *
 * @param <D>
 */
public class DataItemPresenter<D extends DataBlock> {

	public DataItemPresenter(MainWindow mainWindow, WorkflowTab workflowTab, DataItem<D> dataItem) {
		var dataNode = dataItem.getWorkflowNode();
		var controller = dataItem.getController();

		dataItem.setEffect(WorkflowTabPresenter.getDropShadow());

		var selected = new SimpleBooleanProperty(false);
		workflowTab.getSelectionModel().getSelectedItems().addListener((InvalidationListener) e -> selected.set(workflowTab.getSelectionModel().isSelected(dataItem)));

		controller.getEditButton().setOnAction(e -> mainWindow.getTextTabsManager().showTab(dataNode, true));
		controller.getEditButton().disableProperty().bind(selected.not());

		controller.getNameLabel().textProperty().bind(dataNode.nameProperty());

		dataNode.validProperty().addListener((v, o, n) -> {
			if (n) {
				controller.getInfoLabel().setText(String.format("size: %,d", dataNode.getDataBlock().size()));
				controller.getStatusImageView().setImage(ResourceManagerFX.getIcon("Done.png"));
			} else {
				controller.getInfoLabel().setText("-");
				controller.getStatusImageView().setImage(ResourceManagerFX.getIcon("Scheduled.png"));

			}
		});

		var icon = ResourceManagerFX.getIcon(dataNode.getName().replaceAll("Input", "").
													 replaceAll("Working", "").replaceAll(".*]", "").trim() + "16.gif");
		if (icon != null) {
			controller.getNameLabel().setGraphic(new ImageView(icon));
		}

		controller.getAnchorPane().getStyleClass().add("background");
		if (!mainWindow.getWorkflow().isDerivedNode(dataNode)) {
			controller.getNameLabel().setStyle("-fx-text-fill: darkgray");
			controller.getInfoLabel().setStyle("-fx-text-fill: darkgray");
			if (controller.getNameLabel().getGraphic() != null)
				controller.getNameLabel().getGraphic().setOpacity(0.5);
		}

		dataItem.setOnContextMenuRequested(e -> createContextMenu(mainWindow.getWorkflow(), workflowTab, dataItem).show(dataItem, e.getScreenX(), e.getScreenY()));
	}

	private ContextMenu createContextMenu(Workflow workflow, WorkflowTab workflowTab, DataItem<D> item) {
		var attachAlgorithmMenuItem = new MenuItem("Attach Algorithm...");
		attachAlgorithmMenuItem.setOnAction(e -> {
			var screenLocation = item.localToScreen(item.getLayoutX(), item.getLayoutY());
			new AttachNodeDialog(workflow, item.getWorkflowNode(), screenLocation);
		});
		attachAlgorithmMenuItem.disableProperty().bind(workflow.runningProperty());

		return new ContextMenu(attachAlgorithmMenuItem);

	}
}
