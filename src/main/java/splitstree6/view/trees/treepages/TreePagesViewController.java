/*
 *  TreePagesViewController.java Copyright (C) 2024 Daniel H. Huson
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

package splitstree6.view.trees.treepages;

import javafx.beans.binding.When;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import jloda.fx.icons.MaterialIcons;
import jloda.fx.util.BasicFX;
import jloda.fx.util.DraggableLabel;
import jloda.phylo.PhyloTree;
import splitstree6.layout.tree.HeightAndAngles;
import splitstree6.layout.tree.TreeDiagramType;

public class TreePagesViewController {

	@FXML
	private AnchorPane anchorPane;

	@FXML
	private VBox vBox;

	@FXML
	private ToolBar toolBar;

	@FXML
	private ComboBox<TreeDiagramType> diagramCBox;

	@FXML
	private Button rotateLeftButton;

	@FXML
	private Button rotateRightButton;

	@FXML
	private Button flipHorizontalButton;

	@FXML
	private Button flipVerticalButton;

	@FXML
	private ComboBox<HeightAndAngles.Averaging> averagingCBox;

	@FXML
	private ComboBox<String> rowsColsCBox;

	@FXML
	private Pane pane;

	@FXML
	private Pagination pagination;

	@FXML
	private ToggleButton showTreeNamesToggleButton;

	@FXML
	private ComboBox<PhyloTree> treeCBox;

	@FXML
	private Button zoomInButton;

	@FXML
	private Button zoomOutButton;

	@FXML
	private VBox formatVBox;

	@FXML
	private ToggleButton settingsToggleButton;

	@FXML
	private ToggleButton formatToggleButton;

	@FXML
	private AnchorPane innerAnchorPane;


	@FXML
	private void initialize() {
		MaterialIcons.setIcon(rotateLeftButton, "rotate_left");
		MaterialIcons.setIcon(rotateRightButton, "rotate_right");
		MaterialIcons.setIcon(flipHorizontalButton, "flip");
		MaterialIcons.setIcon(flipVerticalButton, "flip", "-fx-rotate: 90;", true);

		MaterialIcons.setIcon(zoomInButton, "zoom_in");
		MaterialIcons.setIcon(zoomOutButton, "zoom_out");
		MaterialIcons.setIcon(settingsToggleButton, "tune");
		MaterialIcons.setIcon(formatToggleButton, "format_shapes");
		innerAnchorPane.getChildren().remove(formatVBox);
		innerAnchorPane.getChildren().add(formatVBox);

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

	public VBox getvBox() {
		return vBox;
	}

	public ToolBar getToolBar() {
		return toolBar;
	}

	public ComboBox<TreeDiagramType> getDiagramCBox() {
		return diagramCBox;
	}

	public Button getRotateLeftButton() {
		return rotateLeftButton;
	}

	public Button getRotateRightButton() {
		return rotateRightButton;
	}

	public Button getFlipHorizontalButton() {
		return flipHorizontalButton;
	}

	public Button getFlipVerticalButton() {
		return flipVerticalButton;
	}

	public ComboBox<HeightAndAngles.Averaging> getAveragingCBox() {
		return averagingCBox;
	}

	public ComboBox<String> getRowsColsCBox() {
		return rowsColsCBox;
	}

	public Pane getPane() {
		return pane;
	}

	public Pagination getPagination() {
		return pagination;
	}

	public ToggleButton getShowTreeNamesToggleButton() {
		return showTreeNamesToggleButton;
	}

	public ComboBox<PhyloTree> getTreeCBox() {
		return treeCBox;
	}

	public Button getZoomInButton() {
		return zoomInButton;
	}

	public Button getZoomOutButton() {
		return zoomOutButton;
	}

	public VBox getFormatVBox() {
		return formatVBox;
	}
}
