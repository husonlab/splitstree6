/*
 *  AnalyzeGenomesController.java Copyright (C) 2024 Daniel H. Huson
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
package splitstree6.dialog.analyzegenomes;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.control.*;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.util.converter.DoubleStringConverter;
import javafx.util.converter.IntegerStringConverter;
import jloda.fx.icons.MaterialIcons;

public class AnalyzeGenomesController {

	@FXML
	private AnchorPane rootPane;

	@FXML
	private TextArea inputTextArea;

	@FXML
	private Button inputBrowseButton;

	@FXML
	private ChoiceBox<AnalyzeGenomesDialog.TaxonIdentification> taxaChoiceBox;

	@FXML
	private ChoiceBox<AnalyzeGenomesDialog.SequenceType> sequenceTypeChoiceBox;

	@FXML
	private TextField minLengthTextField;

	@FXML
	private TextField outputFileTextField;

	@FXML
	private Button outputBrowseButton;

	@FXML
	private Tab filesTab;
	@FXML
	private Tab taxonLabelsTab;

	@FXML
	private ListView<String> displayLabelsListView;

	@FXML
	private VBox displayLabelsVBox;

	@FXML
	private FlowPane statusFlowPane;

	@FXML
	private Button cancelButton;

	@FXML
	private Button applyButton;

	@FXML
	private Button labelsUndoButton;

	@FXML
	private Button labelsRedoButton;

	@FXML
	private CheckBox storeOnlyReferencesCheckBox;

	@FXML
	private HBox additionalButtonsHBox;

	@FXML
	private Button replaceButton;

	@FXML
	private Button clearInputButton;

	@FXML
	private ToggleButton htmlInfoButton;
	@FXML
	private TextArea supportedHTMLTextArea;

	@FXML
	private Tab relatedTab;

	@FXML
	private TextField referencesDatabaseTextField;

	@FXML
	private Button referencesDatabaseButton;

	@FXML
	private Slider maxDistanceSlider;

	@FXML
	private TextField maxToAddTextField;

	@FXML
	private Label addedReferencesLabel;

	@FXML
	private LineChart<Double, Integer> mashDistancesChart;

	@FXML
	private NumberAxis mashDistancesXAxis;

	@FXML
	private NumberAxis mashDistancesYAxis;

	@FXML
	private Button findReferencesButton;

	@FXML
	private Button addReferencesButton;

	@FXML
	private Button removeAllReferencesButton;

	@FXML
	private Label foundReferencesLabel;

	@FXML
	private TextField maxDistToSearchTextField;

	@FXML
	private CheckBox includeStrainsCB;

	@FXML
	private Button cacheButton;

	@FXML
	private TabPane mainTabPane;

	@FXML
	private void initialize() {
		Platform.runLater(() -> {
			MaterialIcons.setIcon(inputBrowseButton, "file_open");
			MaterialIcons.setIcon(outputBrowseButton, "file_open");
			MaterialIcons.setIcon(referencesDatabaseButton, "file_open");
			//MaterialIcons.setIcon(findReferencesButton, "search","",false);
			MaterialIcons.setIcon(replaceButton, "find_replace");
			MaterialIcons.setIcon(cacheButton, "folder_open");
			MaterialIcons.setIcon(htmlInfoButton, "info", "-fx-font-size: 13", true);
		});

		//MaterialIcons.setIcon(addReferencesButton, "add");
		// MaterialIcons.setIcon(removeAllReferencesButton, "remove");
		// MaterialIcons.setIcon(applyButton, "play_circle","",false);
		MaterialIcons.setIcon(labelsUndoButton, "undo");
		MaterialIcons.setIcon(labelsRedoButton, "redo");

		mashDistancesChart.setLegendVisible(false);
		mashDistancesChart.setAnimated(false);
		mashDistancesXAxis.setLabel("Mash distance");
		mashDistancesXAxis.setLowerBound(0);
		mashDistancesXAxis.setUpperBound(0.9);
		mashDistancesXAxis.setTickUnit(0.1);

		mashDistancesYAxis.setForceZeroInRange(true);
		mashDistancesYAxis.setAutoRanging(true);
		mashDistancesYAxis.setLabel("Count");

		maxDistToSearchTextField.setTextFormatter(new TextFormatter<>(new DoubleStringConverter()));
		minLengthTextField.setTextFormatter(new TextFormatter<>(new IntegerStringConverter()));
	}

	public AnchorPane getRootPane() {
		return rootPane;
	}

	public TextArea getInputTextArea() {
		return inputTextArea;
	}

	public Button getInputBrowseButton() {
		return inputBrowseButton;
	}

	public ChoiceBox<AnalyzeGenomesDialog.TaxonIdentification> getTaxaChoiceBox() {
		return taxaChoiceBox;
	}

	public TextField getOutputFileTextField() {
		return outputFileTextField;
	}

	public TextField getMinLengthTextField() {
		return minLengthTextField;
	}

	public Button getOutputBrowseButton() {
		return outputBrowseButton;
	}

	public ChoiceBox<AnalyzeGenomesDialog.SequenceType> getSequenceTypeChoiceBox() {
		return sequenceTypeChoiceBox;
	}

	public FlowPane getStatusFlowPane() {
		return statusFlowPane;
	}

	public Tab getFilesTab() {
		return filesTab;
	}

	public Tab getTaxonLabelsTab() {
		return taxonLabelsTab;
	}

	public ListView<String> getDisplayLabelsListView() {
		return displayLabelsListView;
	}

	public VBox getDisplayLabelsVBox() {
		return displayLabelsVBox;
	}

	public Button getCancelButton() {
		return cancelButton;
	}

	public Button getApplyButton() {
		return applyButton;
	}

	public Button getLabelsUndoButton() {
		return labelsUndoButton;
	}

	public Button getLabelsRedoButton() {
		return labelsRedoButton;
	}

	public CheckBox getStoreOnlyReferencesCheckBox() {
		return storeOnlyReferencesCheckBox;
	}

	public HBox getAdditionalButtonsHBox() {
		return additionalButtonsHBox;
	}

	public Button getReplaceButton() {
		return replaceButton;
	}

	public Button getClearInputButton() {
		return clearInputButton;
	}

	public ToggleButton getHtmlInfoButton() {
		return htmlInfoButton;
	}

	public TextArea getSupportedHTMLTextArea() {
		return supportedHTMLTextArea;
	}

	public Tab getRelatedTab() {
		return relatedTab;
	}

	public TextField getReferencesDatabaseTextField() {
		return referencesDatabaseTextField;
	}

	public Button getReferencesDatabaseButton() {
		return referencesDatabaseButton;
	}

	public Slider getMaxDistanceSlider() {
		return maxDistanceSlider;
	}

	public TextField getMaxToAddTextField() {
		return maxToAddTextField;
	}

	public LineChart<Double, Integer> getMashDistancesChart() {
		return mashDistancesChart;
	}

	public NumberAxis getMashDistancesXAxis() {
		return mashDistancesXAxis;
	}

	public NumberAxis getMashDistancesYAxis() {
		return mashDistancesYAxis;
	}

	public Button getFindReferencesButton() {
		return findReferencesButton;
	}

	public Button getAddReferencesButton() {
		return addReferencesButton;
	}

	public Button getRemoveAllReferencesButton() {
		return removeAllReferencesButton;
	}

	public Label getAddedReferencesLabel() {
		return addedReferencesLabel;
	}

	public Label getFoundReferencesLabel() {
		return foundReferencesLabel;
	}

	public TextField getMaxDistToSearchTextField() {
		return maxDistToSearchTextField;
	}

	public CheckBox getIncludeStrainsCB() {
		return includeStrainsCB;
	}

	public Button getCacheButton() {
		return cacheButton;
	}

	public TabPane getMainTabPane() {
		return mainTabPane;
	}
}
