/*
 * WorkflowTabController.java Copyright (C) 2024 Daniel H. Huson
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

package splitstree6.tabs.workflow;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.MenuButton;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.ToolBar;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import jloda.fx.control.ZoomableScrollPane;
import jloda.fx.icons.MaterialIcons;

public class WorkflowTabController {

	@FXML
	private AnchorPane anchorPane;

	@FXML
	private BorderPane borderPane;

	@FXML
	private Pane mainPane;

	@FXML
	private VBox topVBox;

	@FXML
	private ToolBar toolBar;

	@FXML
	private MenuButton addMenuButton;

	@FXML
	private Button deleteButton;

	@FXML
	private Button editButton;

	@FXML
	private Button duplicateButton;


	private ZoomableScrollPane scrollPane;

	@FXML
	private ProgressIndicator progressIndicator;

	@FXML
	private void initialize() {
		MaterialIcons.setIcon(editButton, "edit_note");
		MaterialIcons.setIcon(addMenuButton, "add");
		MaterialIcons.setIcon(deleteButton, "delete");
		MaterialIcons.setIcon(duplicateButton, "content_copy");


		borderPane.getChildren().remove(mainPane);
		var anchorPane = new AnchorPane(mainPane);
		AnchorPane.setRightAnchor(mainPane, 20.0);
		AnchorPane.setLeftAnchor(mainPane, 20.0);
		AnchorPane.setTopAnchor(mainPane, 20.0);
		AnchorPane.setBottomAnchor(mainPane, 20.0);

		scrollPane = new ZoomableScrollPane(anchorPane);
		scrollPane.setPannable(true);
		borderPane.setCenter(scrollPane);
	}

	public ZoomableScrollPane getScrollPane() {
		return scrollPane;
	}

	public AnchorPane getAnchorPane() {
		return anchorPane;
	}

	public BorderPane getBorderPane() {
		return borderPane;
	}

	public Pane getMainPane() {
		return mainPane;
	}

	public VBox getTopVBox() {
		return topVBox;
	}

	public ToolBar getToolBar() {
		return toolBar;
	}

	public ProgressIndicator getProgressIndicator() {
		return progressIndicator;
	}

	public MenuButton getAddMenuButton() {
		return addMenuButton;
	}

	public Button getDeleteButton() {
		return deleteButton;
	}

	public Button getEditButton() {
		return editButton;
	}

	public Button getDuplicateButton() {
		return duplicateButton;
	}
}
