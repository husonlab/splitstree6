/*
 *  AlgorithmNodeContextMenuController.java Copyright (C) 2024 Daniel H. Huson
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

import javafx.fxml.FXML;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;

public class AlgorithmNodeContextMenuController {

	@FXML
	private ContextMenu contextMenu;

	@FXML
	private MenuItem editMenuItem;

	@FXML
	private MenuItem runMenuItem;

	@FXML
	private MenuItem stopMenuItem;

	@FXML
	private MenuItem duplicateMenuItem;

	@FXML
	private MenuItem deleteMenuItem;

	public ContextMenu getContextMenu() {
		return contextMenu;
	}

	public MenuItem getEditMenuItem() {
		return editMenuItem;
	}

	public MenuItem getRunMenuItem() {
		return runMenuItem;
	}

	public MenuItem getStopMenuItem() {
		return stopMenuItem;
	}

	public MenuItem getDuplicateMenuItem() {
		return duplicateMenuItem;
	}

	public MenuItem getDeleteMenuItem() {
		return deleteMenuItem;
	}
}
