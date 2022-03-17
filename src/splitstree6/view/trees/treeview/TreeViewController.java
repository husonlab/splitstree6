/*
 *  TreeViewController.java Copyright (C) 2022 Daniel H. Huson
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
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToolBar;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import jloda.fx.control.CopyableLabel;
import jloda.fx.control.ZoomableScrollPane;
import jloda.fx.util.DraggableLabel;
import jloda.fx.util.ScaleBar;
import splitstree6.layout.tree.HeightAndAngles;
import splitstree6.layout.tree.LayoutOrientation;
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
	private ComboBox<LayoutOrientation> orientationCBox;

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
	private ToggleButton settingsToggleButton;

	@FXML
	private ToggleButton formatToggleButton;

	@FXML
	private VBox formatVBox;

	private final ZoomableScrollPane zoomableScrollPane = new ZoomableScrollPane(null);

	private final ScaleBar scaleBar = new ScaleBar();

	private final CopyableLabel treeNameLabel = new CopyableLabel();

	@FXML
	private void initialize() {
		zoomableScrollPane.setFitToWidth(true);
		zoomableScrollPane.setFitToHeight(true);
		centerPane.getChildren().add(zoomableScrollPane);

		innerAnchorPane.getChildren().add(scaleBar);

		AnchorPane.setTopAnchor(scaleBar, 2.0);
		AnchorPane.setLeftAnchor(scaleBar, 5.0);
		AnchorPane.setTopAnchor(treeNameLabel, 5.0);
		AnchorPane.setLeftAnchor(treeNameLabel, 180.0);

		innerAnchorPane.getChildren().add(treeNameLabel);

		innerAnchorPane.getChildren().remove(formatVBox);
		innerAnchorPane.getChildren().add(formatVBox);

		settingsToggleButton.setSelected(true);
		toolBar.setMinHeight(ToolBar.USE_PREF_SIZE);
		toolBar.setMaxHeight(ToolBar.USE_COMPUTED_SIZE);
		toolBar.visibleProperty().bind(settingsToggleButton.selectedProperty());
		toolBar.prefHeightProperty().bind(new When(settingsToggleButton.selectedProperty()).then(32.0).otherwise(0.0));

		formatToggleButton.setSelected(false);
		formatVBox.visibleProperty().bind(formatToggleButton.selectedProperty());

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

	public ToggleButton getFindToggleButton() {
		return findToggleButton;
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
