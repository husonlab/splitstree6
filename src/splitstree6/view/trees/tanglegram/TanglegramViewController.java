/*
 *  TanglegramViewController.java Copyright (C) 2021 Daniel H. Huson
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

import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import jloda.fx.util.DraggableLabel;
import jloda.phylo.PhyloTree;
import splitstree6.view.trees.layout.ComputeTreeLayout;
import splitstree6.view.trees.treepages.TreePane;

/**
 * tanglegram view controller
 * Daniel Huson, 12.2021
 */
public class TanglegramViewController {

	@FXML
	private AnchorPane anchorPane;

	@FXML
	private VBox vBox;

	@FXML
	private ToolBar toolBar;

	@FXML
	private Button openButton;

	@FXML
	private Button saveButton;

	@FXML
	private Button printButton;

	@FXML
	private Button findButton;

	@FXML
	private Button expandVerticallyButton;

	@FXML
	private Button contractVerticallyButton;

	@FXML
	private Button expandHorizontallyButton;

	@FXML
	private Button contractHorizontallyButton;

	@FXML
	private ComboBox<PhyloTree> tree1CBox;

	@FXML
	private ComboBox<ComputeTreeLayout.Diagram> diagram1CBox;

	@FXML
	private ComboBox<PhyloTree> tree2CBox;
	@FXML
	private ComboBox<ComputeTreeLayout.Diagram> diagram2CBox;

	@FXML
	private ComboBox<TreePane.Orientation> orientationCBox;

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
	private Label tree1Label;

	@FXML
	private Label tree2Label;


	@FXML
	private void initialize() {
		// draw center first:
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

		DraggableLabel.makeDraggable(tree1Label);
		DraggableLabel.makeDraggable(tree2Label);
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

	public Button getOpenButton() {
		return openButton;
	}

	public Button getSaveButton() {
		return saveButton;
	}

	public Button getPrintButton() {
		return printButton;
	}

	public Button getFindButton() {
		return findButton;
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

	public ComboBox<PhyloTree> getTree1CBox() {
		return tree1CBox;
	}

	public ComboBox<ComputeTreeLayout.Diagram> getDiagram1CBox() {
		return diagram1CBox;
	}

	public ComboBox<PhyloTree> getTree2CBox() {
		return tree2CBox;
	}

	public ComboBox<ComputeTreeLayout.Diagram> getDiagram2CBox() {
		return diagram2CBox;
	}

	public ComboBox<TreePane.Orientation> getOrientationCBox() {
		return orientationCBox;
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

	public Label getTree1Label() {
		return tree1Label;
	}

	public Label getTree2Label() {
		return tree2Label;
	}
}
