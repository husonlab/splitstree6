/*
 * DensiTreeViewController.java Copyright (C) 2022 Daniel H. Huson
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

package splitstree6.view.trees.densitree;

import javafx.beans.binding.When;
import javafx.fxml.FXML;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import jloda.fx.util.BasicFX;
import jloda.fx.util.DraggableLabel;
import splitstree6.layout.tree.TreeDiagramType;

public class DensiTreeViewController {

	@FXML
	private AnchorPane anchorPane;

	@FXML
	private BorderPane borderPane;

	@FXML
	private StackPane centerPane;

	@FXML
	private Button contractHorizontallyButton;

	@FXML
	private Button contractVerticallyButton;

	@FXML
	private Button decreaseFontButton;

	@FXML
	private ComboBox<TreeDiagramType> diagramCBox;

	@FXML
	private Button expandHorizontallyButton;

	@FXML
	private Button expandVerticallyButton;

	@FXML
	private ToggleButton findToggleButton;

	@FXML
	private ToggleButton formatToggleButton;

	@FXML
	private VBox formatVBox;

	@FXML
	private Button increaseFontButton;

	@FXML
	private AnchorPane innerAnchorPane;

	@FXML
	private ToggleButton jitterToggleButton;

	@FXML
	private ToggleButton antiConsensusToggleBox;

	@FXML
	private AnchorPane outerAnchorPane;

	@FXML
	private ToggleButton settingsToggleButton;

	@FXML
	private ToolBar toolBar;

	@FXML
	private VBox vBox;

	@FXML
	private Canvas mainCanvas;

	@FXML
	private Pane mainPane;

	@FXML
	private void initialize() {
		centerPane.getStyleClass().add("viewer-background");

		outerAnchorPane.getChildren().remove(formatVBox);
		outerAnchorPane.getChildren().add(formatVBox);

		settingsToggleButton.setSelected(true);
		toolBar.setMinHeight(ToolBar.USE_PREF_SIZE);
		toolBar.setMaxHeight(ToolBar.USE_COMPUTED_SIZE);
		toolBar.visibleProperty().bind(settingsToggleButton.selectedProperty());
		toolBar.prefHeightProperty().bind(new When(settingsToggleButton.selectedProperty()).then(32.0).otherwise(0.0));

		formatToggleButton.setSelected(false);
		formatVBox.visibleProperty().bind(formatToggleButton.selectedProperty());
		formatVBox.visibleProperty().addListener((v, o, n) -> {
			for (var titledPane : BasicFX.getAllRecursively(formatVBox, TitledPane.class)) {
				if (!titledPane.isDisable())
					titledPane.setExpanded(n);
			}
		});

		DraggableLabel.makeDraggable(formatVBox);
	}


	public AnchorPane getAnchorPane() {
		return anchorPane;
	}

	public BorderPane getBorderPane() {
		return borderPane;
	}

	public StackPane getCenterPane() {
		return centerPane;
	}

	public Button getContractHorizontallyButton() {
		return contractHorizontallyButton;
	}

	public Button getContractVerticallyButton() {
		return contractVerticallyButton;
	}

	public Button getDecreaseFontButton() {
		return decreaseFontButton;
	}

	public ComboBox<TreeDiagramType> getDiagramCBox() {
		return diagramCBox;
	}

	public Button getExpandHorizontallyButton() {
		return expandHorizontallyButton;
	}

	public Button getExpandVerticallyButton() {
		return expandVerticallyButton;
	}

	public ToggleButton getFindToggleButton() {
		return findToggleButton;
	}

	public ToggleButton getFormatToggleButton() {
		return formatToggleButton;
	}

	public VBox getFormatVBox() {
		return formatVBox;
	}

	public Button getIncreaseFontButton() {
		return increaseFontButton;
	}

	public AnchorPane getInnerAnchorPane() {
		return innerAnchorPane;
	}

	public ToggleButton getJitterToggleButton() {
		return jitterToggleButton;
	}

	public ToggleButton getAntiConsensusToggleBox() {
		return antiConsensusToggleBox;
	}

	public AnchorPane getOuterAnchorPane() {
		return outerAnchorPane;
	}

	public ToggleButton getSettingsToggleButton() {
		return settingsToggleButton;
	}

	public ToolBar getToolBar() {
		return toolBar;
	}

	public VBox getvBox() {
		return vBox;
	}

	public Canvas getMainCanvas() {
		return mainCanvas;
	}

	public Pane getMainPane() {
		return mainPane;
	}
}
