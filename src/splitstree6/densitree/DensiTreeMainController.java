/*
 * DensiTreeMainController.java Copyright (C) 2022 Daniel H. Huson
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

package splitstree6.densitree;

import javafx.fxml.FXML;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.*;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;

public class DensiTreeMainController {

	@FXML
	private Pane mainPane;

	@FXML
	private StackPane stackPane;

	@FXML
	private Canvas canvas;

	@FXML
	private Pane pane;

	@FXML
	private MenuItem openMenuItem;

	@FXML
	private MenuItem quitMenuItem;

	@FXML
	private MenuItem copyMenuItem;

	@FXML
	private CheckMenuItem consensusMenuItem;

	@FXML
	private MenuItem specificTreesMenuItem;

	@FXML
	private RadioMenuItem meanMenuItem;

	@FXML
	private RadioMenuItem medianMenuItem;

	@FXML
	private RadioMenuItem radialMenuItem;

	@FXML
	private ToggleGroup labelsGroup;

	@FXML
	private ToolBar toolBar;

	@FXML
	private Button drawButton;

	@FXML
	private Button clearButton;

	@FXML
	private CheckBox scaleCheckBox;

	@FXML
	private CheckBox jitterCheckBox;

	@FXML
	private FlowPane bottomFlowPane;

	@FXML
	private Label messageLabel;

	public Pane getMainPane() {
		return mainPane;
	}

	public StackPane getStackPane(){
		return stackPane;
	}

	public Canvas getCanvas() {
		return canvas;
	}

	public Pane getPane(){
		return pane;
	}

	public MenuItem getOpenMenuItem() {
		return openMenuItem;
	}

	public MenuItem getQuitMenuItem() {
		return quitMenuItem;
	}

	public MenuItem getCopyMenuItem() {
		return copyMenuItem;
	}

	public CheckMenuItem getConsensusMenuItem(){
		return consensusMenuItem;
	}

	public MenuItem getSpecificTreesMenuItem() {
		return specificTreesMenuItem;
	}

	public RadioMenuItem getMeanMenuItem() {
		return meanMenuItem;
	}

	public RadioMenuItem getMedianMenuItem() {
		return medianMenuItem;
	}

	public RadioMenuItem getRadialMenuItem() {
		return radialMenuItem;
	}

	public ToggleGroup getLabelsGroup() {
		return labelsGroup;
	}

	public ToolBar getToolBar() {
		return toolBar;
	}

	public Button getDrawButton() {
		return drawButton;
	}

	public Button getClearButton() {
		return clearButton;
	}

	public CheckBox getScaleCheckBox() {
		return scaleCheckBox;
	}

	public CheckBox getJitterCheckBox() {
		return jitterCheckBox;
	}

	public FlowPane getBottomFlowPane() {
		return bottomFlowPane;
	}

	public Label getMessageLabel() {
		return messageLabel;
	}

}

