/*
 *  DensiTreeViewController.java Copyright (C) 2024 Daniel H. Huson
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

package splitstree6.view.trees.densitree;

import javafx.beans.binding.When;
import javafx.fxml.FXML;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import jloda.fx.icons.MaterialIcons;
import jloda.fx.util.BasicFX;
import jloda.fx.util.DraggableLabel;
import splitstree6.layout.tree.HeightAndAngles;
import splitstree6.layout.tree.TreeDiagramType;
import splitstree6.main.SplitsTree6;

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
	private Button expandHorizontallyButton;

	@FXML
	private Button expandVerticallyButton;

	@FXML
	private ToggleButton formatToggleButton;

	@FXML
	private VBox formatVBox;

	@FXML
	private AnchorPane innerAnchorPane;

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
	private RadioMenuItem trianglDendrogramToggleItem;

	@FXML
	private RadioMenuItem radialPhylogramToggleItem;

	@FXML
	private RadioMenuItem rectangularPhylogramToggleItem;

	@FXML
	private RadioMenuItem roundedPhylogramToggleItem;

	@FXML
	private CheckMenuItem rerootAndRescaleCheckMenuItem;

	@FXML
	private MenuButton menuButton;

	@FXML
	private CheckMenuItem showTreesMenuItem;

	@FXML
	private CheckMenuItem hideFirst10PercentMenuItem;

	@FXML
	private CheckMenuItem showConsensusMenuItem;

	@FXML
	private CheckMenuItem jitterMenuItem;

	@FXML
	private CheckMenuItem colorIncompatibleTreesMenuItem;

	@FXML
	private Button flipButton;

	@FXML
	private ComboBox<HeightAndAngles.Averaging> averagingCBox;


	private final ToggleGroup diagramToggleGroup = new ToggleGroup();

	@FXML
	private void initialize() {
		MaterialIcons.setIcon(flipButton, "swap_vert");
		MaterialIcons.setIcon(settingsToggleButton, "tune");
		MaterialIcons.setIcon(formatToggleButton, "format_shapes");
		MaterialIcons.setIcon(expandHorizontallyButton, "unfold_more", "-fx-rotate: 90;", true);
		MaterialIcons.setIcon(contractHorizontallyButton, "unfold_less", "-fx-rotate: 90;", true);
		MaterialIcons.setIcon(expandVerticallyButton, "unfold_more");
		MaterialIcons.setIcon(contractVerticallyButton, "unfold_less");

		centerPane.getStyleClass().add("viewer-background");

		diagramToggleGroup.getToggles().addAll(trianglDendrogramToggleItem, radialPhylogramToggleItem, rectangularPhylogramToggleItem, roundedPhylogramToggleItem);

		diagramToggleGroup.selectedToggleProperty().addListener((v, o, n) -> {
			if (n == trianglDendrogramToggleItem) {
				menuButton.setGraphic(TreeDiagramType.TriangularCladogram.icon());
			} else if (n == radialPhylogramToggleItem) {
				menuButton.setGraphic(TreeDiagramType.RadialPhylogram.icon());
			} else if (n == rectangularPhylogramToggleItem) {
				menuButton.setGraphic(TreeDiagramType.RectangularPhylogram.icon());
			} else if (n == roundedPhylogramToggleItem) {
				menuButton.setGraphic(TreeDiagramType.iconForRoundedPhylogram());
			}
		});
		trianglDendrogramToggleItem.setGraphic(TreeDiagramType.TriangularCladogram.icon());
		radialPhylogramToggleItem.setGraphic(TreeDiagramType.RadialPhylogram.icon());
		rectangularPhylogramToggleItem.setGraphic(TreeDiagramType.RectangularPhylogram.icon());
		roundedPhylogramToggleItem.setGraphic(TreeDiagramType.iconForRoundedPhylogram());

		if (!SplitsTree6.isDesktop())
			menuButton.getItems().remove(roundedPhylogramToggleItem);

		diagramToggleGroup.selectToggle(trianglDendrogramToggleItem);

		outerAnchorPane.getChildren().remove(formatVBox);
		outerAnchorPane.getChildren().add(formatVBox);

		settingsToggleButton.setSelected(false);

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

	public Button getExpandHorizontallyButton() {
		return expandHorizontallyButton;
	}

	public Button getExpandVerticallyButton() {
		return expandVerticallyButton;
	}

	public ToggleButton getFormatToggleButton() {
		return formatToggleButton;
	}

	public VBox getFormatVBox() {
		return formatVBox;
	}

	public AnchorPane getInnerAnchorPane() {
		return innerAnchorPane;
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

	public CheckMenuItem getShowTreesMenuItem() {
		return showTreesMenuItem;
	}

	public CheckMenuItem getHideFirst10PercentMenuItem() {
		return hideFirst10PercentMenuItem;
	}

	public CheckMenuItem getShowConsensusMenuItem() {
		return showConsensusMenuItem;
	}

	public ToggleGroup getDiagramToggleGroup() {
		return diagramToggleGroup;
	}

	public MenuButton getMenuButton() {
		return menuButton;
	}

	public CheckMenuItem getJitterMenuItem() {
		return jitterMenuItem;
	}

	public CheckMenuItem getColorIncompatibleTreesMenuItem() {
		return colorIncompatibleTreesMenuItem;
	}

	public CheckMenuItem getRerootAndRescaleCheckMenuItem() {
		return rerootAndRescaleCheckMenuItem;
	}

	public Button getFlipButton() {
		return flipButton;
	}

	public ComboBox<HeightAndAngles.Averaging> getAveragingCBox() {
		return averagingCBox;
	}
}
