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

package splitstree6.window.presenter;


import splitstree6.tabs.workflow.WorkflowTab;
import splitstree6.window.MainWindow;

public class WorkflowTabPresenter {
	private final MainWindow mainWindow;

	public WorkflowTabPresenter(MainWindow mainWindow) {
		this.mainWindow = mainWindow;
	}

	public void apply(WorkflowTab workflowTab) {
		var workflowTabController = workflowTab.getController();
		var mainWindowController = mainWindow.getController();

		mainWindowController.getPrintMenuItem().setOnAction(e -> {
		});

		mainWindowController.getUndoMenuItem().setOnAction(e -> workflowTab.getUndoManager().undo());
		mainWindowController.getUndoMenuItem().disableProperty().bind(workflowTab.getUndoManager().undoableProperty().not());

		mainWindowController.getRedoMenuItem().setOnAction(e -> workflowTab.getUndoManager().redo());
		mainWindowController.getRedoMenuItem().disableProperty().bind(workflowTab.getUndoManager().redoableProperty().not());


		mainWindowController.getCutMenuItem().setDisable(false);
		mainWindowController.getCopyMenuItem().setDisable(false);

		mainWindowController.getCopyImageMenuItem().setDisable(false);

		mainWindowController.getPasteMenuItem().setDisable(false);

		mainWindowController.getDuplicateMenuItem().setDisable(false);
		mainWindowController.getDeleteMenuItem().setDisable(false);

		mainWindowController.getFindMenuItem().setDisable(false);
		mainWindowController.getFindAgainMenuItem().setDisable(false);

		// controller.getReplaceMenuItem().setDisable(false);


		mainWindowController.getSelectAllMenuItem().setDisable(false);
		mainWindowController.getSelectNoneMenuItem().setDisable(false);

		mainWindowController.getIncreaseFontSizeMenuItem().setDisable(false);
		mainWindowController.getDecreaseFontSizeMenuItem().setDisable(false);

		mainWindowController.getZoomInMenuItem().setDisable(false);
		mainWindowController.getZoomOutMenuItem().setDisable(false);
	}
}
