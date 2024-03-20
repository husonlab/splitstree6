/*
 *  WorkflowTreeView.java Copyright (C) 2024 Daniel H. Huson
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

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.scene.Node;
import javafx.scene.layout.AnchorPane;
import jloda.fx.undo.UndoManager;
import jloda.fx.util.ExtendedFXMLLoader;
import splitstree6.tabs.IDisplayTab;
import splitstree6.tabs.IDisplayTabPresenter;
import splitstree6.window.MainWindow;

public class WorkflowTreeView extends AnchorPane implements IDisplayTab {
	private final WorkflowTreeViewController controller;
	private final WorkflowTreeViewPresenter presenter;

	private final UndoManager undoManager;
	private final BooleanProperty empty = new SimpleBooleanProperty(true);

	/**
	 * constructor
	 */
	public WorkflowTreeView(MainWindow mainWindow) {
		this.undoManager = mainWindow.getWorkflowTab().getUndoManager();

		var extendedFXMLLoader = new ExtendedFXMLLoader<WorkflowTreeViewController>(this.getClass());
		controller = extendedFXMLLoader.getController();

		presenter = new WorkflowTreeViewPresenter(mainWindow, this);

		var root = extendedFXMLLoader.getRoot();
		getChildren().add(root);
		AnchorPane.setTopAnchor(root, 0.0);
		AnchorPane.setBottomAnchor(root, 0.0);
		AnchorPane.setLeftAnchor(root, 0.0);
		AnchorPane.setRightAnchor(root, 0.0);

		empty.bind(mainWindow.getWorkflow().numberOfNodesProperty().isEqualTo(0));
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
		return controller.getWorkflowTreeView();
	}

	@Override
	public IDisplayTabPresenter getPresenter() {
		return presenter;
	}

	public WorkflowTreeViewController getController() {
		return controller;
	}

	public WorkflowTreeItem getRoot() {
		return (WorkflowTreeItem) controller.getWorkflowTreeView().getRoot();
	}
}

