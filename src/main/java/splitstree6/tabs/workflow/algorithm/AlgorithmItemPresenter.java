/*
 * AlgorithmItemPresenter.java Copyright (C) 2023 Daniel H. Huson
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

package splitstree6.tabs.workflow.algorithm;

import javafx.beans.InvalidationListener;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.scene.control.ProgressIndicator;
import jloda.fx.icons.MaterialIcons;
import splitstree6.contextmenus.algorithmnode.AlgorithmNodeContextMenu;
import splitstree6.tabs.workflow.WorkflowTab;
import splitstree6.tabs.workflow.WorkflowTabPresenter;
import splitstree6.window.MainWindow;
import splitstree6.workflow.DataTaxaFilter;
import splitstree6.workflow.Workflow;

/**
 * Algorithm Item presenter
 * Daniel Huson, 2021
 */
public class AlgorithmItemPresenter {
	public AlgorithmItemPresenter(MainWindow mainWindow, WorkflowTab workflowTab, AlgorithmItem algorithmItem) {

		var node = algorithmItem.getWorkflowNode();
		var controller = algorithmItem.getController();

		algorithmItem.setEffect(WorkflowTabPresenter.getDropShadow());

		controller.getNameLabel().textProperty().bind(node.titleProperty());

		var selected = new SimpleBooleanProperty(false);
		mainWindow.getWorkflow().getSelectionModel().getSelectedItems().addListener((InvalidationListener) e -> selected.set(mainWindow.getWorkflow().getSelectionModel().isSelected(node)));

		controller.getEditButton().setOnAction(e -> mainWindow.getAlgorithmTabsManager().showTab(node, true));
		if (algorithmItem.getWorkflowNode().getAlgorithm() instanceof DataTaxaFilter)
			controller.getEditButton().setDisable(true);
		else
			controller.getEditButton().disableProperty().bind(selected.not());

		controller.getPlayButton().setOnAction(e -> node.restart());
		controller.getPlayButton().disableProperty().bind((node.getService().runningProperty().not().and(node.allParentsValidProperty()).and(selected)).not());

		node.getService().runningProperty().addListener((v, o, n) -> {
			if (n) {
				MaterialIcons.setIcon(controller.getPlayButton(), "cancel_outlined");
				controller.getPlayButton().setOnAction(e -> node.getService().cancel());
				controller.getPlayButton().disableProperty().bind((node.getService().runningProperty().and(node.allParentsValidProperty()).and(selected)).not());
				controller.getPlayButton().getTooltip().setText("Stop this algorithm");
			} else {
				MaterialIcons.setIcon(controller.getPlayButton(), "play_circle");
				controller.getPlayButton().setOnAction(e -> node.restart());
				controller.getPlayButton().disableProperty().bind((node.getService().runningProperty().not().and(node.allParentsValidProperty()).and(selected)).not());
				controller.getPlayButton().getTooltip().setText("Run this algorithm");
			}
		});

		controller.getNameLabel().setGraphic(node.getName().endsWith("Filter") ? MaterialIcons.graphic("filter_alt") : MaterialIcons.graphic("settings"));

		if (!mainWindow.getWorkflow().isDerivedNode(node)) {
			controller.getNameLabel().setStyle("-fx-text-fill: darkgray");
			if (controller.getNameLabel().getGraphic() != null)
				controller.getNameLabel().getGraphic().setOpacity(0.5);
		}

		node.validProperty().addListener((v, o, n) -> {
			if (!n)
				controller.getIconPane().getChildren().setAll(MaterialIcons.graphic("schedule", "-fx-text-fill: yellow;"));
		});

		var progressIndicator = new ProgressIndicator();
		// progressIndicator.progressProperty().bind(algorithmNode.getService().progressProperty());
		progressIndicator.setPrefHeight(16);
		progressIndicator.setPrefWidth(16);

		controller.getIconPane().getChildren().setAll(MaterialIcons.graphic("done"));

		node.getService().stateProperty().addListener((v, o, n) -> {
			switch (n) {
				case CANCELLED, FAILED ->
						controller.getIconPane().getChildren().setAll(MaterialIcons.graphic("close", "-fx-text-fill: red;"));
				case READY, SCHEDULED ->
						controller.getIconPane().getChildren().setAll(MaterialIcons.graphic("schedule", "-fx-text-fill: yellow;"));
				case SUCCEEDED ->
						controller.getIconPane().getChildren().setAll(MaterialIcons.graphic("done", "-fx-text-fill: green;"));
				case RUNNING -> controller.getIconPane().getChildren().setAll(progressIndicator);
			}
		});

		if (node.getName().equals(Workflow.INPUT_TAXA_DATA_FILTER)) {
			controller.getEditButton().setVisible(false);
			controller.getPlayButton().setVisible(false);
		}

		algorithmItem.setOnContextMenuRequested(e -> {
					if (selected.get())
						AlgorithmNodeContextMenu.create(mainWindow, workflowTab.getUndoManager(), node).show(algorithmItem, e.getScreenX(), e.getScreenY());
				}
		);
	}
}
