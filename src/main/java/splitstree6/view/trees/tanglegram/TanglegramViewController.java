/*
 *  TanglegramViewController.java Copyright (C) 2024 Daniel H. Huson
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

package splitstree6.view.trees.tanglegram;

import javafx.application.Platform;
import javafx.beans.binding.When;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import jloda.fx.control.CopyableLabel;
import jloda.fx.icons.MaterialIcons;
import jloda.fx.util.BasicFX;
import jloda.fx.util.DraggableLabel;

/**
 * tanglegram view controller
 * Daniel Huson, 12.2021
 */
public class TanglegramViewController {

	@FXML
	private AnchorPane anchorPane;

	@FXML
	ScrollPane scrollPane;

	@FXML
	private VBox vBox;

	@FXML
	private ToolBar toolBar;


	@FXML
	private Button expandVerticallyButton;

	@FXML
	private Button contractVerticallyButton;

	@FXML
	private Button expandHorizontallyButton;

	@FXML
	private Button contractHorizontallyButton;

	@FXML
	private Button expandCollapseVerticallyButton;

	@FXML
	private ChoiceBox<String> tree1CBox;

	@FXML
	private MenuButton diagram1MenuButton;

	@FXML
	private ChoiceBox<String> tree2CBox;
	@FXML
	private MenuButton diagram2MenuButton;

	@FXML
	private Button flipButton;

	@FXML
	private Button previousButton;

	@FXML
	private Button nextButton;

	@FXML
	private ToggleButton showTreeNamesToggleButton;

	@FXML
	private BorderPane borderPane;

	@FXML
	private Pane leftPane;

	@FXML
	private Pane rightPane;

	@FXML
	private Pane middlePane;

	@FXML
	private AnchorPane outerAnchorPane;

	@FXML
	private AnchorPane innerAnchorPane;

	@FXML
	private VBox formatVBox;

	@FXML
	private ToggleButton settingsToggleButton;

	@FXML
	private ToggleButton formatToggleButton;

	@FXML
	private CheckBox taxonDisplacementFirstCBox;

	@FXML
	private CheckBox reticulateDisplacementFirstCBox;

	@FXML
	private CheckBox taxonDisplacementSecondCBox;

	@FXML
	private CheckBox reticulateDisplacementSecondCBox;

	@FXML
	private CheckBox useNNPresortingCBox;

	@FXML
	private MenuButton optimizeMenuButton;


	private final CopyableLabel tree1NameLabel = new CopyableLabel();
	private final CopyableLabel tree2NameLabel = new CopyableLabel();


	@FXML
	private void initialize() {
		Platform.runLater(() -> {
			MaterialIcons.setIcon(flipButton, "flip", "-fx-rotate: 90;", true);
			MaterialIcons.setIcon(previousButton, MaterialIcons.arrow_left);
			MaterialIcons.setIcon(nextButton, MaterialIcons.arrow_right);
			MaterialIcons.setIcon(settingsToggleButton, MaterialIcons.more_vert);
			MaterialIcons.setIcon(formatToggleButton, MaterialIcons.tune);
			MaterialIcons.setIcon(expandHorizontallyButton, MaterialIcons.unfold_more, "-fx-rotate: 90;", true);
			MaterialIcons.setIcon(contractHorizontallyButton, MaterialIcons.unfold_less, "-fx-rotate: 90;", true);
			MaterialIcons.setIcon(expandCollapseVerticallyButton, MaterialIcons.sync_alt, "-fx-rotate: 90;", true);
			MaterialIcons.setIcon(expandVerticallyButton, MaterialIcons.unfold_more);
			MaterialIcons.setIcon(contractVerticallyButton, MaterialIcons.unfold_less);
			MaterialIcons.setIcon(optimizeMenuButton, MaterialIcons.rule);
		});


		// draw applyCentering first:
		var left = borderPane.getLeft();
		var right = borderPane.getRight();
		var bottom = borderPane.getBottom();
		var top = borderPane.getTop();
		var center = borderPane.getCenter();
		borderPane.getChildren().clear();
		borderPane.setCenter(center);
		borderPane.setLeft(left);
		borderPane.setRight(right);
		borderPane.setTop(top);
		borderPane.setBottom(bottom);

		scrollPane.setFitToWidth(true);
		scrollPane.setFitToHeight(true);

		leftPane.getStyleClass().add("viewer-background");
		middlePane.getStyleClass().add("viewer-background");
		rightPane.getStyleClass().add("viewer-background");


		innerAnchorPane.getChildren().add(tree1NameLabel);
		AnchorPane.setTopAnchor(tree1NameLabel, 5.0);
		AnchorPane.setLeftAnchor(tree1NameLabel, 10.0);

		innerAnchorPane.getChildren().add(tree2NameLabel);
		AnchorPane.setTopAnchor(tree2NameLabel, 5.0);
		AnchorPane.setRightAnchor(tree2NameLabel, 20.0);

		DraggableLabel.makeDraggable(tree1NameLabel);
		DraggableLabel.makeDraggable(tree2NameLabel);

		settingsToggleButton.setSelected(false);

		toolBar.setMinHeight(ToolBar.USE_PREF_SIZE);
		toolBar.setMaxHeight(ToolBar.USE_COMPUTED_SIZE);
		toolBar.visibleProperty().bind(settingsToggleButton.selectedProperty());
		toolBar.prefHeightProperty().bind(new When(settingsToggleButton.selectedProperty()).then(32.0).otherwise(0.0));

		outerAnchorPane.getChildren().remove(formatVBox);
		outerAnchorPane.getChildren().add(formatVBox);

		formatToggleButton.setSelected(false);
		formatVBox.visibleProperty().bind(formatToggleButton.selectedProperty());
		formatVBox.visibleProperty().addListener((v, o, n) -> {
			for (var titledPane : BasicFX.getAllRecursively(formatVBox, TitledPane.class)) {
				if (!titledPane.isDisable())
					titledPane.setExpanded(n);
			}
		});

		DraggableLabel.makeDraggable(formatVBox);

		// todo: flip is broken, disable for now:

		toolBar.getItems().remove(flipButton);
	}

	public AnchorPane getAnchorPane() {
		return anchorPane;
	}

	public VBox getvBox() {
		return vBox;
	}

	public ToolBar getToolBar() {
		return toolBar;
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

	public Button getExpandCollapseVerticallyButton() {
		return expandCollapseVerticallyButton;
	}

	public ChoiceBox<String> getTree1CBox() {
		return tree1CBox;
	}

	public MenuButton getDiagram1MenuButton() {
		return diagram1MenuButton;
	}

	public ChoiceBox<String> getTree2CBox() {
		return tree2CBox;
	}

	public MenuButton getDiagram2MenuButton() {
		return diagram2MenuButton;
	}

	public Button getFlipButton() {
		return flipButton;
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

	public Pane getLeftPane() {
		return leftPane;
	}

	public Pane getRightPane() {
		return rightPane;
	}

	public Pane getMiddlePane() {
		return middlePane;
	}

	public BorderPane getBorderPane() {
		return borderPane;
	}

	public CopyableLabel getTree1NameLabel() {
		return tree1NameLabel;
	}

	public CopyableLabel getTree2NameLabel() {
		return tree2NameLabel;
	}

	public AnchorPane getInnerAnchorPane() {
		return innerAnchorPane;
	}

	public VBox getFormatVBox() {
		return formatVBox;
	}

	public ScrollPane getScrollPane() {
		return scrollPane;
	}

	public CheckBox getTaxonDisplacementFirstCBox() {
		return taxonDisplacementFirstCBox;
	}

	public CheckBox getReticulateDisplacementFirstCBox() {
		return reticulateDisplacementFirstCBox;
	}

	public CheckBox getTaxonDisplacementSecondCBox() {
		return taxonDisplacementSecondCBox;
	}

	public CheckBox getReticulateDisplacementSecondCBox() {
		return reticulateDisplacementSecondCBox;
	}

	public CheckBox getUseNNPresortingCBox() {
		return useNNPresortingCBox;
	}
}
