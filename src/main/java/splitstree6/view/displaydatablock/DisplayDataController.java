/*
 *  DisplayDataController.java Copyright (C) 2024 Daniel H. Huson
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

package splitstree6.view.displaydatablock;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.Pane;
import jloda.fx.icons.MaterialIcons;

public class DisplayDataController {

	@FXML
	private TitledPane titledPane;

	@FXML
	private ChoiceBox<String> formatCBox;

	@FXML
	private Button applyButton;

	@FXML
	private Pane mainPane;

	@FXML
	private void initialize() {
		MaterialIcons.setIcon(applyButton, "play_circle");
	}

	public TitledPane getTitledPane() {
		return titledPane;
	}

	public ChoiceBox<String> getFormatCBox() {
		return formatCBox;
	}

	public Button getApplyButton() {
		return applyButton;
	}

	public Pane getMainPane() {
		return mainPane;
	}
}
