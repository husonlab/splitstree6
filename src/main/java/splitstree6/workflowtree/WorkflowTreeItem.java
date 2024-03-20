/*
 *  WorkflowTreeItem.java Copyright (C) 2024 Daniel H. Huson
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
import javafx.beans.value.ChangeListener;
import javafx.beans.value.WeakChangeListener;
import javafx.concurrent.Worker;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.control.TreeItem;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import jloda.fx.icons.MaterialIcons;
import jloda.fx.workflow.WorkflowNode;
import jloda.util.FileUtils;
import splitstree6.contextmenus.algorithmnode.AlgorithmNodeContextMenu;
import splitstree6.contextmenus.datanode.DataNodeContextMenu;
import splitstree6.data.ViewBlock;
import splitstree6.window.MainWindow;
import splitstree6.workflow.AlgorithmNode;
import splitstree6.workflow.DataNode;

/**
 * work flow node in tree
 * Daniel Huson, 10.21
 */
public class WorkflowTreeItem extends TreeItem<String> {
	private final MainWindow mainWindow;
	private final WorkflowTreeView workflowTreeView;
	private final WorkflowNode workflowNode;
	private final Tooltip tooltip = new Tooltip();
	private final ChangeListener<Worker.State> stateChangeListener;

	public WorkflowTreeItem(MainWindow mainWindow) {
		super("");
		this.mainWindow = mainWindow;
		this.workflowTreeView = mainWindow.getWorkflowTreeView();
		workflowNode = null;
		Label label = new Label();
		label.textProperty().bind(Bindings.createStringBinding(() -> FileUtils.getFileNameWithoutPath(mainWindow.getFileName()), mainWindow.fileNameProperty()));
		label.setGraphic(MaterialIcons.graphic("source"));
		setGraphic(label);

		label.setOnMouseClicked(e -> {
			if (mainWindow.isEmpty() && e.getClickCount() == 2) {
				mainWindow.getPresenter().showInputEditor();
				e.consume();
			}
		});

		stateChangeListener = null;
	}

	public WorkflowTreeItem(MainWindow mainWindow, AlgorithmNode node) {
		super("");
		this.mainWindow = mainWindow;
		this.workflowTreeView = mainWindow.getWorkflowTreeView();
		workflowNode = node;

		final var label = new Label();
		final var vBox = new VBox(label);
		setGraphic(vBox);

		label.textProperty().bind(node.titleProperty());

		tooltip.textProperty().bind(node.shortDescriptionProperty());
		Tooltip.install(getGraphic(), new Tooltip(node.getShortDescription()));

		label.setGraphic(MaterialIcons.graphic("settings"));
		stateChangeListener = (c, o, n) -> {
			// System.err.println("State change: " + workflowNode.getName() + ": " + n);
			switch (n) {
				case SCHEDULED, RUNNING -> {
					label.setTextFill(Color.BLACK);
					label.setStyle("-fx-background-color: LIGHTBLUE;");
				}
				case CANCELLED, FAILED -> {
					label.setStyle("");
					label.setTextFill(Color.DARKRED);
				}
				default -> {
					label.setTextFill(Color.BLACK);
					label.setStyle("");
				}
			}
		};
		node.getService().stateProperty().addListener(new WeakChangeListener<>(stateChangeListener));

		vBox.setOnMouseClicked(e -> {
			if (e.getClickCount() == 2) {
				showView();
				e.consume();
			}
		});

		label.disableProperty().bind(node.validProperty().not());

		vBox.setOnContextMenuRequested(me -> {
			AlgorithmNodeContextMenu.show(mainWindow, workflowTreeView.getUndoManager(), node, label, me.getScreenX(), me.getScreenY());
		});

		expandedProperty().addListener((v, o, n) -> {
			if (n && mainWindow.getWorkflow().getSelectionModel().isSelected(node))
				workflowTreeView.getController().getWorkflowTreeView().getSelectionModel().select(this);
		});
	}

	public WorkflowTreeItem(MainWindow mainWindow, DataNode workflowNode) {
		super("");
		this.mainWindow = mainWindow;
		this.workflowTreeView = mainWindow.getWorkflowTreeView();
		this.workflowNode = workflowNode;

		final var label = new Label();
		final var vBox = new VBox(label);
		setGraphic(vBox);

		label.textProperty().bind(workflowNode.titleProperty());

		if (workflowNode.getDataBlock() instanceof ViewBlock) {
			label.setGraphic(MaterialIcons.graphic("wysiwyg"));
		} else {
			label.setGraphic(MaterialIcons.graphic("dataset"));
		}

		tooltip.textProperty().bind(workflowNode.shortDescriptionProperty());
		Tooltip.install(getGraphic(), new Tooltip(workflowNode.getShortDescription()));

		stateChangeListener = null;

		if (workflowNode.getDataBlock() instanceof ViewBlock viewBlock) {
			vBox.setOnMouseClicked(e -> {
				if (e.getClickCount() == 2) {
					mainWindow.getController().getMainTabPane().getSelectionModel().select(viewBlock.getViewTab());
					e.consume();
				}
			});
		} else {
			vBox.setOnMouseClicked(e -> {
				if (e.getClickCount() == 2) {
					mainWindow.getTextTabsManager().showDataNodeTab(workflowNode, true);
					e.consume();
				}
			});
		}

		label.disableProperty().bind(workflowNode.validProperty().not());

		vBox.setOnContextMenuRequested(me -> DataNodeContextMenu.show(mainWindow, workflowTreeView.getUndoManager(), workflowNode, label, me.getScreenX(), me.getScreenY()));

		expandedProperty().addListener((v, o, n) -> {
			if (n && mainWindow.getWorkflow().getSelectionModel().isSelected(workflowNode))
				workflowTreeView.getController().getWorkflowTreeView().getSelectionModel().select(this);
		});
	}

	/**
	 * show the view for this node
	 */
	public void showView() {
		if (workflowNode instanceof DataNode dataNode) {
			if (dataNode.getDataBlock() instanceof ViewBlock viewBlock) {
				mainWindow.getController().getMainTabPane().getSelectionModel().select(viewBlock.getViewTab());
			} else
				mainWindow.getTextTabsManager().showDataNodeTab(dataNode, true);
		} else if (workflowNode instanceof AlgorithmNode algorithmNode)
			mainWindow.getAlgorithmTabsManager().showTab(algorithmNode, true);
	}

	public String toString() {
		return ((Label) getGraphic()).getText();
	}

	public WorkflowNode getWorkflowNode() {
		return workflowNode;
	}
}
