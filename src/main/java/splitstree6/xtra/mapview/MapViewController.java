/*
 *  MapViewController.java Copyright (C) 2024 Daniel H. Huson
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

package splitstree6.xtra.mapview;

import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.*;

public class MapViewController {

	@FXML
	private MenuItem closeMenuItem;

	@FXML
	private MenuItem copyMenuItem;

	@FXML
	private Pane mainPane;

	@FXML
	private MenuBar menuBar;

	@FXML
	private MenuItem openMenuItem;

	@FXML
	private StackPane stackPane;

	@FXML
	private ToolBar tooBar;

	@FXML
	private VBox topPane;

	@FXML
	private ProgressBar progressBar;

	@FXML
	private Label label;

	@FXML
	private Button redrawButton;

	@FXML
	private Slider chartSizeSlider;


	@FXML
	private HBox hBoxMiddle;

	@FXML
	private StackPane mainStackPane;


	@FXML
	private Label labelChartSize;

	@FXML
	private Label infoLabel;


	@FXML
	private ChoiceBox<String> choiceBoxColorScheme;


	public ChoiceBox<String> getChoiceBoxColorScheme() {
		return choiceBoxColorScheme;
	}

	public Label getInfoLabel() {
		return infoLabel;
	}

	public Label getLabelChartSize() {
		return labelChartSize;
	}

	public StackPane getMainStackPane() {
		return mainStackPane;
	}

	public HBox gethBoxMiddle() {
		return hBoxMiddle;
	}


	@FXML
	private void initialize() {
		progressBar.setVisible(false);
	}

	public MenuItem getCloseMenuItem() {
		return closeMenuItem;
	}

	public Slider getChartSizeSlider() {
		return chartSizeSlider;
	}

	public MenuItem getCopyMenuItem() {
		return copyMenuItem;
	}

	public Pane getMainPane() {
		return mainPane;
	}

	public Label getLabel() {
		return label;
	}

	public MenuBar getMenuBar() {
		return menuBar;
	}

	public MenuItem getOpenMenuItem() {
		return openMenuItem;
	}

	public StackPane getStackPane() {
		return stackPane;
	}

	public ToolBar getTooBar() {
		return tooBar;
	}

	public VBox getTopPane() {
		return topPane;
	}

	public ProgressBar getProgressBar() {
		return progressBar;
	}

	public Button getRedrawButton() {
		return redrawButton;
	}
}
