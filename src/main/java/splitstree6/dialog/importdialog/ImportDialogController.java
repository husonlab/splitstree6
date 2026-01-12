/*
 *  ImportDialogController.java Copyright (C) 2024 Daniel H. Huson
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

package splitstree6.dialog.importdialog;

import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;
import splitstree6.data.parts.CharactersType;
import splitstree6.io.utils.SimilaritiesToDistances;
import splitstree6.workflow.DataBlock;

public class ImportDialogController {

	@FXML
	private Button browseButton;

	@FXML
	private Tab charactersTab;

	@FXML
	private ChoiceBox<CharactersType> charactersTypeCBox;

	@FXML
	private Button closeButton;

	@FXML
	private ChoiceBox<Class<? extends DataBlock>> dataTypeCBox;

	@FXML
	private Tab distancesTab;

	@FXML
	private ChoiceBox<String> fileFormatCBox;

	@FXML
	private TextField fileTextField;

	@FXML
	private TextField gapInputTextField;

	@FXML
	private Button importButton;

	@FXML
	private CheckBox innerNodesLabelingCheckBox;

	@FXML
	private VBox mainVBox;

	@FXML
	private TextField matchInputTextField;

	@FXML
	private TextField missingInputTextField;

	@FXML
	private FlowPane progressBarPane;

	@FXML
	private ChoiceBox<SimilaritiesToDistances.Method> similarityToDistanceMethodCBox;

	@FXML
	private CheckBox similarityValues;

	@FXML
	private Tab splitsTab;

	@FXML
	private Tab treesTab;

	@FXML
	private void initialize() {
		similarityToDistanceMethodCBox.getItems().addAll(SimilaritiesToDistances.Method.values());
		similarityToDistanceMethodCBox.setValue(SimilaritiesToDistances.Method.log);

		// Set the cell factory to display the short name
		dataTypeCBox.setConverter(new StringConverter<>() {
			@Override
			public String toString(Class<? extends DataBlock> item) {
				return item == null ? "" : item.getSimpleName();
			}

			@Override
			public Class<? extends DataBlock> fromString(String s) {
				return null;
			}
		});

		charactersTab.getTabPane().setVisible(false);
	}

	public Button getBrowseButton() {
		return browseButton;
	}

	public Tab getCharactersTab() {
		return charactersTab;
	}

	public ChoiceBox<CharactersType> getCharactersTypeCBox() {
		return charactersTypeCBox;
	}

	public Button getCloseButton() {
		return closeButton;
	}

	public ChoiceBox<Class<? extends DataBlock>> getDataTypeCBox() {
		return dataTypeCBox;
	}

	public Tab getDistancesTab() {
		return distancesTab;
	}

	public ChoiceBox<String> getFileFormatCBox() {
		return fileFormatCBox;
	}

	public TextField getFileTextField() {
		return fileTextField;
	}

	public TextField getGapInputTextField() {
		return gapInputTextField;
	}

	public Button getImportButton() {
		return importButton;
	}

	public VBox getMainVBox() {
		return mainVBox;
	}

	public TextField getMatchInputTextField() {
		return matchInputTextField;
	}

	public TextField getMissingInputTextField() {
		return missingInputTextField;
	}

	public FlowPane getProgressBarPane() {
		return progressBarPane;
	}

	public ChoiceBox<SimilaritiesToDistances.Method> getSimilarityToDistanceMethodCBox() {
		return similarityToDistanceMethodCBox;
	}

	public CheckBox getSimilarityValues() {
		return similarityValues;
	}

	public Tab getSplitsTab() {
		return splitsTab;
	}

	public Tab getTreesTab() {
		return treesTab;
	}
}
