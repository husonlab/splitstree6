/*
 *  WorkflowTreeViewController.java Copyright (C) 2024 Daniel H. Huson
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

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.MenuButton;
import javafx.scene.control.TreeView;
import jloda.fx.icons.MaterialIcons;

public class WorkflowTreeViewController {

	@FXML
	private MenuButton addMenuButton;

	@FXML
	private Button deleteButton;

	@FXML
	private Button copyButton;

	@FXML
	private Button editButton;
	@FXML
	private TreeView<String> workflowTreeView;

	@FXML
	private void initialize() {
		MaterialIcons.setIcon(editButton, "edit_note");
		MaterialIcons.setIcon(addMenuButton, "add");
		MaterialIcons.setIcon(deleteButton, "delete");
		MaterialIcons.setIcon(copyButton, "copy");

		workflowTreeView.addEventFilter(javafx.scene.input.TouchEvent.TOUCH_PRESSED, e -> {
			var target = e.getTouchPoint().getPickResult().getIntersectedNode();
			while (target != null && !(target instanceof javafx.scene.control.TreeCell)) {
				target = target.getParent();
			}
			if (target instanceof javafx.scene.control.TreeCell<?> cell) {
				int index = cell.getIndex();
				if (index >= 0) {
					workflowTreeView.getSelectionModel().select(index);
					workflowTreeView.getFocusModel().focus(index);
					e.consume();
				}
			}
		});
	}

	public TreeView<String> getWorkflowTreeView() {
		return workflowTreeView;
	}

	public MenuButton getAddMenuButton() {
		return addMenuButton;
	}

	public Button getDeleteButton() {
		return deleteButton;
	}

	public Button getEditButton() {
		return editButton;
	}

	public Button getCopyButton() {
		return copyButton;
	}
}
