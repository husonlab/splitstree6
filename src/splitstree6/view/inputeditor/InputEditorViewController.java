/*
 * InputEditorViewController.java Copyright (C) 2022 Daniel H. Huson
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

package splitstree6.view.inputeditor;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ToolBar;

public class InputEditorViewController {
	@FXML
	private ToolBar firstToolBar;

	@FXML
	private Button openButton;

	@FXML
	private Button saveButton;

	@FXML
	private ToolBar lastToolBar;

	@FXML
	private Button parseAndLoadButton;

	public ToolBar getFirstToolBar() {
		return firstToolBar;
	}

	public Button getOpenButton() {
		return openButton;
	}

	public Button getSaveButton() {
		return saveButton;
	}

	public ToolBar getLastToolBar() {
		return lastToolBar;
	}

	public Button getParseAndLoadButton() {
		return parseAndLoadButton;
	}
}
