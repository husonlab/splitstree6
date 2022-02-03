/*
 * DataItemPresenter.java Copyright (C) 2022 Daniel H. Huson
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
import javafx.scene.image.ImageView;
import jloda.fx.util.ResourceManagerFX;
import splitstree6.contextmenus.datanode.DataNodeContextMenu;
import splitstree6.tabs.workflow.WorkflowTab;
import splitstree6.tabs.workflow.WorkflowTabPresenter;
import splitstree6.window.MainWindow;
import splitstree6.workflow.DataBlock;

/**
 * data item presenter
 * Daniel Huson, 10.21
 */
public class DataItemPresenter<D extends DataBlock> {

	public DataItemPresenter(MainWindow mainWindow, WorkflowTab workflowTab, DataItem<D> dataItem) {
		var node = dataItem.getWorkflowNode();
		var controller = dataItem.getController();

		dataItem.setEffect(WorkflowTabPresenter.getDropShadow());

		var selected = new SimpleBooleanProperty(false);
		mainWindow.getWorkflow().getSelectionModel().getSelectedItems().addListener((InvalidationListener) e -> selected.set(mainWindow.getWorkflow().getSelectionModel().isSelected(node)));

		controller.getEditButton().setOnAction(e -> mainWindow.getTextTabsManager().showDataNodeTab(node, true));
		controller.getEditButton().disableProperty().bind((selected.and(node.validProperty()).not()));

		controller.getNameLabel().textProperty().bind(node.titleProperty());

		controller.getInfoLabel().setText(String.format("size: %,d", node.getDataBlock().size()));
		controller.getStatusImageView().setImage(node.getDataBlock().size() > 0 ? ResourceManagerFX.getIcon("Done.png") : ResourceManagerFX.getIcon("Scheduled.png"));

		node.allParentsValidProperty().addListener((v, o, n) -> {
			if (n && node.isValid()) {
				controller.getInfoLabel().setText(String.format("size: %,d", node.getDataBlock().size()));
				controller.getStatusImageView().setImage(ResourceManagerFX.getIcon("Done.png"));
			} else {
				controller.getInfoLabel().setText("-");
				controller.getStatusImageView().setImage(ResourceManagerFX.getIcon("Scheduled.png"));

			}
		});

		var icon = ResourceManagerFX.getIcon(node.getName().replaceAll("Input", "").
													 replaceAll("Working", "").replaceAll(".*]", "").trim() + "16.gif");
		if (icon != null) {
			controller.getNameLabel().setGraphic(new ImageView(icon));
		}

		if (!mainWindow.getWorkflow().isDerivedNode(node)) {
			controller.getNameLabel().setStyle("-fx-text-fill: darkgray");
			controller.getInfoLabel().setStyle("-fx-text-fill: darkgray");
			if (controller.getNameLabel().getGraphic() != null)
				controller.getNameLabel().getGraphic().setOpacity(0.5);
		}

		dataItem.setOnContextMenuRequested(e -> {
			if (selected.get())
				DataNodeContextMenu.create(mainWindow, workflowTab.getUndoManager(), node).show(dataItem, e.getScreenX(), e.getScreenY());
		});
	}
}
