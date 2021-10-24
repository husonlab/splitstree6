/*
 *  AlgorithmItemController.java Copyright (C) 2021 Daniel H. Huson
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

package splitstree6.tabs.workflow.data;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.ImageView;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.BorderPane;

public class DataItemController {
	@FXML
	private AnchorPane anchorPane;

	@FXML
	BorderPane borderPane;

	@FXML
	private Label infoLabel;

	@FXML
	private Label nameLabel;

	@FXML
	private Button editButton;

	@FXML
	private ImageView statusImageView;


	public AnchorPane getAnchorPane() {
		return anchorPane;
	}

	public BorderPane getBorderPane() {
		return borderPane;
	}

	public Label getInfoLabel() {
		return infoLabel;
	}

	public Label getNameLabel() {
		return nameLabel;
	}

	public ImageView getStatusImageView() {
		return statusImageView;
	}

	public Button getEditButton() {
		return editButton;
	}
}
