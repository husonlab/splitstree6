/*
 *  MultiTreesViewerController.java Copyright (C) 2021 Daniel H. Huson
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

package splitstree6.viewers.multitreesviewer;

import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.VBox;

public class MultiTreesViewerController {

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
	private ComboBox<String> rowsColsCBox;

	@FXML
	private Button firstPageButton;

	@FXML
	private Button previousPageButton;

	@FXML
	private TextField pageTextField;

	@FXML
	private Button nextPageButton;

	@FXML
	private Button lastPage;

	@FXML
	private Pagination pagination;

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

	public ComboBox<String> getRowsColsCBox() {
		return rowsColsCBox;
	}

	public Button getFirstPageButton() {
		return firstPageButton;
	}

	public Button getPreviousPageButton() {
		return previousPageButton;
	}

	public TextField getPageTextField() {
		return pageTextField;
	}

	public Button getNextPageButton() {
		return nextPageButton;
	}

	public Button getLastPage() {
		return lastPage;
	}

	public Pagination getPagination() {
		return pagination;
	}
}
