/*
 *  WorkflowTab.java Copyright (C) 2021 Daniel H. Huson
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

package splitstree6.tabs.workflow;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.scene.Node;
import javafx.scene.control.Tab;
import jloda.fx.undo.UndoManager;
import jloda.fx.util.ExtendedFXMLLoader;
import splitstree6.tabs.IDisplayTab;
import splitstree6.window.MainWindow;
import splitstree6.workflow.Workflow;

public class WorkflowTab extends Tab implements IDisplayTab {
	private final WorkflowTabController controller;

	private final UndoManager undoManager = new UndoManager();
	private final MainWindow mainWindow;
	private final Workflow workflow;
	private final BooleanProperty empty = new SimpleBooleanProperty(true);

	/**
	 * constructor
	 */
	public WorkflowTab(MainWindow mainWindow) {
		this.mainWindow = mainWindow;
		this.workflow = mainWindow.getWorkflow();

		var extendedFXMLLoader = new ExtendedFXMLLoader<WorkflowTabController>(this.getClass());
		controller = extendedFXMLLoader.getController();

		empty.bind(workflow.numberOfNodesProperty().isEqualTo(0));

		setText("Workflow");
		setClosable(false);
	}

	@Override
	public UndoManager getUndoManager() {
		return undoManager;
	}

	@Override
	public ReadOnlyBooleanProperty isEmptyProperty() {
		return empty;
	}

	@Override
	public Node getImageNode() {
		return null;
	}

	public WorkflowTabController getController() {
		return controller;
	}
}

