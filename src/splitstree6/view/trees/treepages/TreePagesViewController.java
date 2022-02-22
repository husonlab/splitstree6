/*
 * TreePagesViewController.java Copyright (C) 2022 Daniel H. Huson
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

package splitstree6.view.trees.treepages;

import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import jloda.phylo.PhyloTree;
import splitstree6.view.trees.layout.ComputeHeightAndAngles;
import splitstree6.view.trees.layout.TreeDiagramType;

public class TreePagesViewController {

	@FXML
	private AnchorPane anchorPane;

	@FXML
	private VBox vBox;

	@FXML
	private ToolBar toolBar;

	@FXML
	private Button findButton;

	@FXML
	private ComboBox<TreeDiagramType> diagramCBox;

	@FXML
	private ComboBox<LayoutOrientation> orientationCBox;

	@FXML
	private ComboBox<ComputeHeightAndAngles.Averaging> averagingCBox;

	@FXML
	private ComboBox<String> rowsColsCBox;

	@FXML
	private Pane pane;

	@FXML
	private Pagination pagination;

	@FXML
	private ToggleButton showTreeNamesToggleButton;

	@FXML
	private ToggleButton showInternalLabelsToggleButton;

	@FXML
	private ComboBox<PhyloTree> treeCBox;

	@FXML
	private Button zoomInButton;

	@FXML
	private Button zoomOutButton;

	@FXML
	private Button increaseFontButton;

	@FXML
	private Button decreaseFontButton;

	@FXML
	private VBox formatVBox;

	@FXML
	private TitledPane formatTitledPane;

	@FXML
	private AnchorPane innerAnchorPane;

	@FXML
	private void initialize() {
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

		innerAnchorPane.getChildren().remove(formatVBox);
		innerAnchorPane.getChildren().add(formatVBox);
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

	public Button getFindButton() {
		return findButton;
	}

	public ComboBox<TreeDiagramType> getDiagramCBox() {
		return diagramCBox;
	}

	public ComboBox<LayoutOrientation> getOrientationCBox() {
		return orientationCBox;
	}

	public ComboBox<ComputeHeightAndAngles.Averaging> getAveragingCBox() {
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

	public ToggleButton getShowInternalLabelsToggleButton() {
		return showInternalLabelsToggleButton;
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

	public Button getIncreaseFontButton() {
		return increaseFontButton;
	}

	public Button getDecreaseFontButton() {
		return decreaseFontButton;
	}

	public VBox getFormatVBox() {
		return formatVBox;
	}

	public TitledPane getFormatTitledPane() {
		return formatTitledPane;
	}
}
