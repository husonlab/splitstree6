/*
 *  AlgorithmNodeContextMenuPresenter.java Copyright (C) 2021 Daniel H. Huson
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

package splitstree6.contextmenus.algorithmnode;

import jloda.fx.undo.UndoManager;
import splitstree6.window.MainWindow;
import splitstree6.workflow.AlgorithmNode;
import splitstree6.workflow.commands.DeleteCommand;
import splitstree6.workflow.commands.DuplicateCommand;

public class AlgorithmNodeContextMenuPresenter {
	public AlgorithmNodeContextMenuPresenter(MainWindow mainWindow, UndoManager undoManager, AlgorithmNodeContextMenuController controller, AlgorithmNode algorithmNode) {
		var workflow = mainWindow.getWorkflow();

		controller.getEditMenuItem().setOnAction(e -> mainWindow.getAlgorithmTabsManager().showTab(algorithmNode, true));

		controller.getRunMenuItem().setOnAction(e -> algorithmNode.restart());
		controller.getRunMenuItem().disableProperty().bind((algorithmNode.getService().runningProperty().not().and(algorithmNode.allParentsValidProperty()).not()));

		controller.getStopMenuItem().setOnAction(e -> algorithmNode.getService().cancel());
		controller.getStopMenuItem().disableProperty().bind(algorithmNode.getService().runningProperty().not());

		controller.getDuplicateMenuItem().setOnAction(e -> undoManager.doAndAdd(DuplicateCommand.create(workflow, algorithmNode)));
		if (workflow.isDerivedNode(algorithmNode))
			controller.getDuplicateMenuItem().disableProperty().bind(workflow.runningProperty());
		else
			controller.getDuplicateMenuItem().setDisable(true);

		controller.getDeleteMenuItem().setOnAction(e -> undoManager.doAndAdd(DeleteCommand.create(workflow, algorithmNode)));
		if (workflow.isDerivedNode(algorithmNode))
			controller.getDeleteMenuItem().disableProperty().bind(workflow.runningProperty());
		else
			controller.getDeleteMenuItem().setDisable(true);


	}
}
