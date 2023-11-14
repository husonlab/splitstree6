/*
 *  TreeViewController.java Copyright (C) 2023 Daniel H. Huson
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

package splitstree6.view.trees.treeview;

import javafx.beans.binding.When;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import jloda.fx.control.CopyableLabel;
import jloda.fx.control.ZoomableScrollPane;
import jloda.fx.icons.MaterialIcons;
import jloda.fx.util.BasicFX;
import jloda.fx.util.DraggableLabel;
import jloda.fx.util.ScaleBar;
import splitstree6.layout.tree.HeightAndAngles;
import splitstree6.layout.tree.TreeDiagramType;

public class TreeViewController {

	@FXML
	private AnchorPane anchorPane;

	@FXML
	private BorderPane borderPane;

	@FXML
	private VBox vBox;

	@FXML
	private ToolBar toolBar;

	@FXML
	private ToggleButton findToggleButton;

	@FXML
	private StackPane centerPane;

	@FXML
	private ComboBox<TreeDiagramType> diagramCBox;

	@FXML
	private Button rotateLeftButton;

	@FXML
	private Button rotateRightButton;

	@FXML
	private Button flipButton;

	@FXML
	private ComboBox<HeightAndAngles.Averaging> averagingCBox;

	@FXML
	private ComboBox<String> treeCBox;

	@FXML
	private Button previousButton;

	@FXML
	private Button nextButton;

	@FXML
	private ToggleButton showTreeNamesToggleButton;

	@FXML
	private Button expandVerticallyButton;

	@FXML
	private Button contractVerticallyButton;

	@FXML
	private Button expandHorizontallyButton;

	@FXML
	private Button contractHorizontallyButton;

	@FXML
	private Button increaseFontButton;

	@FXML
	private Button decreaseFontButton;

	@FXML
	private AnchorPane outerAnchorPane;

	@FXML
	private AnchorPane innerAnchorPane;

	@FXML
	private ToggleButton settingsToggleButton;

	@FXML
	private ToggleButton formatToggleButton;

	@FXML
	private VBox formatVBox;

	@FXML
	private MenuButton exportMenuButton;

	private final ZoomableScrollPane zoomableScrollPane = new ZoomableScrollPane(null);

	private final ScaleBar scaleBar = new ScaleBar();

	private final CopyableLabel treeNameLabel = new CopyableLabel();

	@FXML
	private void initialize() {
		MaterialIcons.setIcon(exportMenuButton, "ios_share");

		MaterialIcons.setIcon(rotateLeftButton, "rotate_left");
		MaterialIcons.setIcon(rotateRightButton, "rotate_right");
		MaterialIcons.setIcon(flipButton, "flip");
		MaterialIcons.setIcon(previousButton, "arrow_left");
		MaterialIcons.setIcon(nextButton, "arrow_right");
		MaterialIcons.setIcon(findToggleButton, "search");
		MaterialIcons.setIcon(settingsToggleButton, "tune");
		MaterialIcons.setIcon(formatToggleButton, "format_shapes");
		MaterialIcons.setIcon(expandHorizontallyButton, "unfold_more", "-fx-rotate: 90;", true);
		MaterialIcons.setIcon(contractHorizontallyButton, "unfold_less", "-fx-rotate: 90;", true);
		MaterialIcons.setIcon(expandVerticallyButton, "unfold_more");
		MaterialIcons.setIcon(contractVerticallyButton, "unfold_less");
		MaterialIcons.setIcon(increaseFontButton, "text_increase");
		MaterialIcons.setIcon(decreaseFontButton, "text_decrease");

		zoomableScrollPane.setPannable(true);
		zoomableScrollPane.setFitToWidth(true);
		zoomableScrollPane.setFitToHeight(true);
		centerPane.getChildren().add(zoomableScrollPane);

		innerAnchorPane.getChildren().add(scaleBar);

		AnchorPane.setTopAnchor(scaleBar, 2.0);
		AnchorPane.setLeftAnchor(scaleBar, 5.0);
		AnchorPane.setTopAnchor(treeNameLabel, 5.0);
		AnchorPane.setLeftAnchor(treeNameLabel, 180.0);

		innerAnchorPane.getChildren().add(treeNameLabel);

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

		DraggableLabel.makeDraggable(treeNameLabel);
		DraggableLabel.makeDraggable(formatVBox);
	}

	public ZoomableScrollPane getScrollPane() {
		return zoomableScrollPane;
	}

	public AnchorPane getAnchorPane() {
		return anchorPane;
	}

	public BorderPane getBorderPane() {
		return borderPane;
	}

	public VBox getvBox() {
		return vBox;
	}

	public ToolBar getToolBar() {
		return toolBar;
	}

	public ToggleButton getFindToggleButton() {
		return findToggleButton;
	}

	public ComboBox<TreeDiagramType> getDiagramCBox() {
		return diagramCBox;
	}

	public StackPane getCenterPane() {
		return centerPane;
	}

	public ComboBox<String> getTreeCBox() {
		return treeCBox;
	}

	public ComboBox<HeightAndAngles.Averaging> getAveragingCBox() {
		return averagingCBox;
	}

	public Button getPreviousButton() {
		return previousButton;
	}

	public Button getNextButton() {
		return nextButton;
	}

	public ToggleButton getShowTreeNamesToggleButton() {
		return showTreeNamesToggleButton;
	}

	public Button getExpandVerticallyButton() {
		return expandVerticallyButton;
	}

	public Button getContractVerticallyButton() {
		return contractVerticallyButton;
	}

	public Button getExpandHorizontallyButton() {
		return expandHorizontallyButton;
	}

	public Button getContractHorizontallyButton() {
		return contractHorizontallyButton;
	}

	public Button getIncreaseFontButton() {
		return increaseFontButton;
	}

	public Button getDecreaseFontButton() {
		return decreaseFontButton;
	}

	public AnchorPane getInnerAnchorPane() {
		return innerAnchorPane;
	}

	public VBox getFormatVBox() {
		return formatVBox;
	}

	public ScaleBar getScaleBar() {
		return scaleBar;
	}

	public CopyableLabel getTreeNameLabel() {
		return treeNameLabel;
	}

	public ToggleButton getFormatToggleButton() {
		return formatToggleButton;
	}

	public Button getRotateLeftButton() {
		return rotateLeftButton;
	}

	public Button getRotateRightButton() {
		return rotateRightButton;
	}

	public Button getFlipButton() {
		return flipButton;
	}

	public MenuButton getExportMenuButton() {
		return exportMenuButton;
	}
}
