/*
 *  SingleTreeViewController.java Copyright (C) 2022 Daniel H. Huson
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

package splitstree6.view.trees.singletree;

import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import jloda.fx.control.CopyableLabel;
import jloda.fx.control.ZoomableScrollPane;
import jloda.fx.util.DraggableLabel;
import jloda.fx.util.ScaleBar;
import splitstree6.view.trees.layout.ComputeHeightAndAngles;
import splitstree6.view.trees.layout.TreeDiagramType;
import splitstree6.view.trees.treepages.LayoutOrientation;

public class SingleTreeViewController {

	@FXML
	private AnchorPane anchorPane;

	@FXML
	private BorderPane borderPane;

	@FXML
	private VBox vBox;

	@FXML
	private ToolBar toolBar;

	@FXML
	private Button findButton;

	@FXML
	private StackPane centerPane;

	@FXML
	private ComboBox<TreeDiagramType> diagramCBox;

	@FXML
	private ComboBox<LayoutOrientation> orientationCBox;

	@FXML
	private ComboBox<ComputeHeightAndAngles.Averaging> averagingCBox;

	@FXML
	private ComboBox<String> treeCBox;

	@FXML
	private Button previousButton;

	@FXML
	private Button nextButton;

	@FXML
	private ToggleButton showTreeNamesToggleButton;

	@FXML
	private ToggleButton showInternalLabelsToggleButton;

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
	private AnchorPane innerAnchorPane;

	@FXML
	private TitledPane formatTitledPane;

	@FXML
	private VBox formatVBox;

	private final ZoomableScrollPane zoomableScrollPane = new ZoomableScrollPane(null);

	private final ScaleBar scaleBar = new ScaleBar();

	private final CopyableLabel treeNameLabel = new CopyableLabel();

	@FXML
	private void initialize() {
		centerPane.getChildren().add(zoomableScrollPane);
		innerAnchorPane.getChildren().add(scaleBar);
		AnchorPane.setTopAnchor(scaleBar, 25.0);
		AnchorPane.setLeftAnchor(scaleBar, 5.0);

		AnchorPane.setTopAnchor(treeNameLabel, 2.0);
		AnchorPane.setLeftAnchor(treeNameLabel, 2.0);
		innerAnchorPane.getChildren().add(treeNameLabel);

		formatVBox.setMinHeight(0);
		formatVBox.setMaxHeight(formatVBox.getPrefHeight());

		if (!formatTitledPane.isExpanded()) {
			formatVBox.setVisible(false);
			formatVBox.setMaxHeight(0);
		} else {
			formatVBox.setVisible(true);
			formatVBox.setMaxHeight(formatVBox.getPrefHeight());
		}

		formatTitledPane.expandedProperty().addListener((v, o, n) -> {
			formatVBox.setVisible(n);
			formatVBox.setMaxHeight(n ? formatVBox.getPrefHeight() : 0);
		});

		AnchorPane.setTopAnchor(formatTitledPane, AnchorPane.getTopAnchor(formatTitledPane) + 30);

		innerAnchorPane.getChildren().remove(formatVBox);
		innerAnchorPane.getChildren().add(formatVBox);

		DraggableLabel.makeDraggable(treeNameLabel);
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

	public Button getFindButton() {
		return findButton;
	}

	public ComboBox<TreeDiagramType> getDiagramCBox() {
		return diagramCBox;
	}

	public StackPane getCenterPane() {
		return centerPane;
	}

	public ComboBox<LayoutOrientation> getOrientationCBox() {
		return orientationCBox;
	}

	public ComboBox<String> getTreeCBox() {
		return treeCBox;
	}

	public ComboBox<ComputeHeightAndAngles.Averaging> getAveragingCBox() {
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

	public ToggleButton getShowInternalLabelsToggleButton() {
		return showInternalLabelsToggleButton;
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

	public TitledPane getFormatTitledPane() {
		return formatTitledPane;
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
}
