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

package splitstree6.xtra.densitree_old;

import javafx.fxml.FXML;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.*;
import javafx.scene.layout.*;

@Deprecated
public class DensiTreeMainController {
	@FXML
	private AnchorPane anchorPane;

	@FXML
	private BorderPane borderPane;

	@FXML
	private StackPane stackPane;

	@FXML
	private Canvas canvas;

	@FXML
	private Pane labelPane;

	@FXML
	private Pane highlightingPane;

	@FXML
	private Pane consensusPane;

	@FXML
	private MenuItem openMenuItem;

	@FXML
	private MenuItem printMenuItem;

	@FXML
	private MenuItem quitMenuItem;

	@FXML
	private MenuItem copyMenuItem;

	@FXML
	private RadioMenuItem circularMenuItem;

	@FXML
	private RadioMenuItem toscaleMenuItem;

	@FXML
	private RadioMenuItem uniformMenuItem;

	@FXML
	private RadioMenuItem rootedMenuItem;

	@FXML
	private RadioMenuItem blockMenuItem;

	@FXML
	private ToggleGroup drawingGroup;

	@FXML
	private CheckMenuItem consensusMenuItem;

	@FXML
	private MenuItem specificTreesMenuItem;

	@FXML
	private RadioMenuItem meanMenuItem;

	@FXML
	private RadioMenuItem medianMenuItem;

	@FXML
	private RadioMenuItem dbscanMenuItem;

	@FXML
	private RadioMenuItem kmeansMenuItem;

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
	private CheckBox jitterCheckBox;

	@FXML
	private FlowPane bottomFlowPane;

	@FXML
	private Label messageLabel;

	@FXML
	private Slider scaleSlider;

	@FXML
	private void initialize() {
		stackPane.setStyle("-fx-background-color: white;");
	}

	public AnchorPane getAnchorPane() {
		return anchorPane;
	}

	public StackPane getStackPane() {
		return stackPane;
	}

	public Canvas getCanvas() {
		return canvas;
	}

	public Pane getLabelPane() {
		return labelPane;
	}

	public Pane getHighlightingPane() {
		return highlightingPane;
	}

	public Pane getConsensusPane() {
		return consensusPane;
	}

	public MenuItem getOpenMenuItem() {
		return openMenuItem;
	}

	public MenuItem getPrintMenuItem() {
		return printMenuItem;
	}

	public MenuItem getQuitMenuItem() {
		return quitMenuItem;
	}

	public MenuItem getCopyMenuItem() {
		return copyMenuItem;
	}

	public RadioMenuItem getCircularMenuItem() {
		return circularMenuItem;
	}

	public RadioMenuItem getToscaleMenuItem() {
		return toscaleMenuItem;
	}

	public RadioMenuItem getUniformMenuItem() {
		return uniformMenuItem;
	}

	public RadioMenuItem getRootedMenuItem() {
		return rootedMenuItem;
	}

	public RadioMenuItem getBlockMenuItem(){return blockMenuItem;}

	public ToggleGroup getDrawingGroup() {
		return drawingGroup;
	}

	public CheckMenuItem getConsensusMenuItem() {
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

	public RadioMenuItem getDbscanMenuItem() {
		return dbscanMenuItem;
	}

	public RadioMenuItem getKmeansMenuItem() {
		return kmeansMenuItem;
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

	public CheckBox getJitterCheckBox() {
		return jitterCheckBox;
	}

	public FlowPane getBottomFlowPane() {
		return bottomFlowPane;
	}

	public Label getMessageLabel() {
		return messageLabel;
	}

	public Slider getScaleSlider() {
		return scaleSlider;
	}
}

