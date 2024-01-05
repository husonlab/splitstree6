/*
 * WorkflowTab.java Copyright (C) 2024 Daniel H. Huson
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

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableMap;
import javafx.scene.Node;
import javafx.scene.control.Tab;
import jloda.fx.undo.UndoManager;
import jloda.fx.util.ExtendedFXMLLoader;
import jloda.fx.workflow.WorkflowNode;
import splitstree6.tabs.IDisplayTab;
import splitstree6.tabs.workflow.algorithm.AlgorithmItem;
import splitstree6.tabs.workflow.data.DataItem;
import splitstree6.window.MainWindow;
import splitstree6.workflow.AlgorithmNode;
import splitstree6.workflow.DataNode;
import splitstree6.workflow.Workflow;

import java.util.Map;

public class WorkflowTab extends Tab implements IDisplayTab {
	private final MainWindow mainWindow;
	private final WorkflowTabController controller;
	private final WorkflowTabPresenter presenter;

	private final UndoManager undoManager = new UndoManager();
	private final BooleanProperty empty = new SimpleBooleanProperty(true);

	private final ObservableMap<WorkflowNode, WorkflowNodeItem> nodeItemMap = FXCollections.observableHashMap();

	/**
	 * constructor
	 */
	public WorkflowTab(MainWindow mainWindow) {
		this.mainWindow = mainWindow;

		Workflow workflow = mainWindow.getWorkflow();
		var loader = new ExtendedFXMLLoader<WorkflowTabController>(this.getClass());
		controller = loader.getController();
		setContent(loader.getRoot());

		presenter = new WorkflowTabPresenter(mainWindow, this);

		empty.bind(workflow.numberOfNodesProperty().isEqualTo(0));

		setText("Workflow");
		setClosable(false);
	}

	@Override
	public UndoManager getUndoManager() {
		return undoManager;
	}

	@Override
	public ReadOnlyBooleanProperty emptyProperty() {
		return empty;
	}

	@Override
	public Node getMainNode() {
		return controller.getMainPane();
	}

	@Override
	public WorkflowTabPresenter getPresenter() {
		return presenter;
	}

	public WorkflowTabController getController() {
		return controller;
	}

	public AlgorithmItem newAlgorithmItem(AlgorithmNode algorithmNode) {
		var item = new AlgorithmItem(mainWindow, this, algorithmNode);
		nodeItemMap.put(algorithmNode, item);
		return item;
	}

	public DataItem newDataItem(DataNode dataNode) {
		var item = new DataItem(mainWindow, this, dataNode);
		nodeItemMap.put(dataNode, item);
		return item;
	}

	public MainWindow getMainWindow() {
		return mainWindow;
	}

	public Map<WorkflowNode, WorkflowNodeItem> getNodeItemMap() {
		return nodeItemMap;
	}
}

