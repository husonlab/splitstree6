/*
 * AlgorithmItem.java Copyright (C) 2022 Daniel H. Huson
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

import jloda.fx.util.ExtendedFXMLLoader;
import splitstree6.tabs.workflow.WorkflowNodeItem;
import splitstree6.tabs.workflow.WorkflowTab;
import splitstree6.window.MainWindow;
import splitstree6.workflow.AlgorithmNode;

public class AlgorithmItem extends WorkflowNodeItem {
	private final AlgorithmItemController controller;

	public AlgorithmItem(MainWindow mainWindow, WorkflowTab workflowTab, AlgorithmNode node) {
		super(mainWindow.getWorkflow(), workflowTab, node);

		var loader = new ExtendedFXMLLoader<AlgorithmItemController>(this.getClass());
		controller = loader.getController();

		getChildren().add(loader.getRoot());

		new AlgorithmItemPresenter(mainWindow, workflowTab, this);
	}


	public AlgorithmNode getWorkflowNode() {
		return (AlgorithmNode) node;
	}

	public AlgorithmItemController getController() {
		return controller;
	}
}
