/*
 *  ImportDialogController.java Copyright (C) 2023 Daniel H. Huson
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
import splitstree6.workflow.DataBlock;

public class ImportDialogController {

	@FXML
	private Button browseButton;

	@FXML
	private Tab charactersTab;

	@FXML
	private ComboBox<?> charactersTypeCBox;

	@FXML
	private Button closeButton;

	@FXML
	private ComboBox<Class<? extends DataBlock>> dataTypeComboBox;

	@FXML
	private Tab distancesTab;

	@FXML
	private ComboBox<String> fileFormatComboBox;

	@FXML
	private TextField fileTextField;

	@FXML
	private TextField gapInput;

	@FXML
	private Button importButton;

	@FXML
	private CheckBox innerNodesLabeling;

	@FXML
	private VBox mainVBox;

	@FXML
	private TextField matchInput;

	@FXML
	private TextField missingInput;

	@FXML
	private FlowPane progressBarPane;

	@FXML
	private ComboBox<?> similarityCalculation;

	@FXML
	private CheckBox similarityValues;

	@FXML
	private Tab splitsTab;

	@FXML
	private Tab treesTab;

	@FXML
	private void initialize() {
		// Set the cell factory to display the short name
		dataTypeComboBox.setCellFactory(param -> new ListCell<>() {
			@Override
			protected void updateItem(Class<? extends DataBlock> item, boolean empty) {
				super.updateItem(item, empty);
				if (empty || item == null) {
					setText(null);
				} else {
					setText(item.getSimpleName());
				}
			}
		});
		dataTypeComboBox.setButtonCell(new ListCell<>() {
			@Override
			protected void updateItem(Class<? extends DataBlock> item, boolean empty) {
				super.updateItem(item, empty);
				if (empty || item == null) {
					setText(null);
				} else {
					setText(item.getSimpleName());
				}
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

	public ComboBox<?> getCharactersTypeCBox() {
		return charactersTypeCBox;
	}

	public Button getCloseButton() {
		return closeButton;
	}

	public ComboBox<Class<? extends DataBlock>> getDataTypeComboBox() {
		return dataTypeComboBox;
	}

	public Tab getDistancesTab() {
		return distancesTab;
	}

	public ComboBox<String> getFileFormatComboBox() {
		return fileFormatComboBox;
	}

	public TextField getFileTextField() {
		return fileTextField;
	}

	public TextField getGapInput() {
		return gapInput;
	}

	public Button getImportButton() {
		return importButton;
	}

	public CheckBox getInnerNodesLabeling() {
		return innerNodesLabeling;
	}

	public VBox getMainVBox() {
		return mainVBox;
	}

	public TextField getMatchInput() {
		return matchInput;
	}

	public TextField getMissingInput() {
		return missingInput;
	}

	public FlowPane getProgressBarPane() {
		return progressBarPane;
	}

	public ComboBox<?> getSimilarityCalculation() {
		return similarityCalculation;
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
