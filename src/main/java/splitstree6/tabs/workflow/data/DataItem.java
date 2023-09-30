/*
 * DataItem.java Copyright (C) 2023 Daniel H. Huson
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

import jloda.fx.util.ExtendedFXMLLoader;
import splitstree6.tabs.workflow.WorkflowNodeItem;
import splitstree6.tabs.workflow.WorkflowTab;
import splitstree6.window.MainWindow;
import splitstree6.workflow.DataBlock;
import splitstree6.workflow.DataNode;

public class DataItem<D extends DataBlock> extends WorkflowNodeItem {
	private final DataItemController controller;

	public DataItem(MainWindow mainWindow, WorkflowTab workflowTab, DataNode<D> dataNode) {
		super(mainWindow.getWorkflow(), workflowTab, dataNode);

		var loader = new ExtendedFXMLLoader<DataItemController>(this.getClass());
		controller = loader.getController();

		getChildren().add(loader.getRoot());

		new DataItemPresenter<>(mainWindow, workflowTab, this);
	}

	public DataItemController getController() {
		return controller;
	}

	public DataNode<D> getWorkflowNode() {
		return (DataNode<D>) node;
	}
}
