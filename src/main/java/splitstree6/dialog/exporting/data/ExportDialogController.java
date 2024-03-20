/*
 *  ExportDialogController.java Copyright (C) 2024 Daniel H. Huson
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

package splitstree6.dialog.exporting.data;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import jloda.fx.icons.MaterialIcons;

public class ExportDialogController {

	@FXML
	private ChoiceBox<String> formatCBox;

	@FXML
	private VBox mainPane;

	@FXML
	private TextField fileTextField;

	@FXML
	private Button browseButton;

	@FXML
	private Button cancelButton;

	@FXML
	private Button applyButton;

	@FXML
	private Label titleLabel;

	@FXML
	private void initialize() {
		MaterialIcons.setIcon(browseButton, "file_open");
	}

	public ChoiceBox<String> getFormatCBox() {
		return formatCBox;
	}

	public VBox getMainPane() {
		return mainPane;
	}

	public TextField getFileTextField() {
		return fileTextField;
	}

	public Button getBrowseButton() {
		return browseButton;
	}

	public Button getCancelButton() {
		return cancelButton;
	}

	public Button getApplyButton() {
		return applyButton;
	}

	public Label getTitleLabel() {
		return titleLabel;
	}
}