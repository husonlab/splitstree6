/*
 * HaplotypeController.java Copyright (C) 2025 Daniel H. Huson
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
 *
 */

package splitstree6.dialog.haplotype;

import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import jloda.fx.util.ProgramProperties;

public class HaplotypeController {

	@FXML
	private BorderPane root;

	@FXML
	private TextField sequencesField;
	@FXML
	private Button browseSequencesBtn;

	@FXML
	private TextField traitsField;
	@FXML
	private Button browseTraitsBtn;

	@FXML
	private ChoiceBox<String> distanceChoice;
	@FXML
	private ChoiceBox<String> methodChoice;

	@FXML
	private Button cancelBtn;
	@FXML
	private Button applyBtn;

	@FXML
	private void initialize() {
		// Populate choices with initial selections pre-selected
		distanceChoice.getItems().setAll("Hamming", "TN93");
		distanceChoice.getSelectionModel().selectFirst();

		methodChoice.getItems().setAll("RazorNet", "MedianJoining", "MinSpanningNetwork");
		methodChoice.getSelectionModel().selectFirst();

		ProgramProperties.track("HaplotypeSequencesFile", sequencesField.textProperty(), "");
		ProgramProperties.track("HaplotypeTraitsFile", traitsField.textProperty(), "");
	}

	public Node getRoot() {
		return root;
	}

	public TextField getSequencesField() {
		return sequencesField;
	}

	public Button getBrowseSequencesBtn() {
		return browseSequencesBtn;
	}

	public TextField getTraitsField() {
		return traitsField;
	}

	public Button getBrowseTraitsBtn() {
		return browseTraitsBtn;
	}

	public ChoiceBox<String> getDistanceChoice() {
		return distanceChoice;
	}

	public ChoiceBox<String> getMethodChoice() {
		return methodChoice;
	}

	public Button getCancelBtn() {
		return cancelBtn;
	}

	public Button getApplyBtn() {
		return applyBtn;
	}
}