/*
 *  TaxaEditController.java Copyright (C) 2021 Daniel H. Huson
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

package splitstree6.tabs.algorithms.taxaedit;

import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;

public class TaxaEditController {

	@FXML
	private AnchorPane anchorPane;

	@FXML
	private VBox topVBox;

	@FXML
	private TableView<TaxaEditTableItem> tableView;

	@FXML
	private TableColumn<TaxaEditTableItem, Boolean> activeColumn;

	@FXML
	private MenuItem activateAllMenuItem;

	@FXML
	private MenuItem activateNoneMenuItem;

	@FXML
	private MenuItem activateSelectedMenuItem;

	@FXML
	private TableColumn<TaxaEditTableItem, Integer> idColumn;

	@FXML
	private TableColumn<TaxaEditTableItem, String> nameColumn;

	@FXML
	private TableColumn<TaxaEditTableItem, String> displayLabelColumn;

	@FXML
	private MenuItem findAndReplaceMenuItem;

	@FXML
	private MenuItem clearSelectedMenuItem;

	@FXML
	private MenuItem clearAllMenuItem;

	@FXML
	private RadioMenuItem showHTMLInfoMenuItem;

	@FXML
	private Label infoLabel;

	@FXML
	private FlowPane htmlInfoFlowPane;

	public AnchorPane getAnchorPane() {
		return anchorPane;
	}

	public VBox getTopVBox() {
		return topVBox;
	}

	public TableView<TaxaEditTableItem> getTableView() {
		return tableView;
	}

	public TableColumn<TaxaEditTableItem, Boolean> getActiveColumn() {
		return activeColumn;
	}

	public MenuItem getActivateAllMenuItem() {
		return activateAllMenuItem;
	}

	public MenuItem getActivateNoneMenuItem() {
		return activateNoneMenuItem;
	}

	public MenuItem getActivateSelectedMenuItem() {
		return activateSelectedMenuItem;
	}

	public TableColumn<TaxaEditTableItem, Integer> getIdColumn() {
		return idColumn;
	}

	public TableColumn<TaxaEditTableItem, String> getNameColumn() {
		return nameColumn;
	}

	public TableColumn<TaxaEditTableItem, String> getDisplayLabelColumn() {
		return displayLabelColumn;
	}

	public MenuItem getFindAndReplaceMenuItem() {
		return findAndReplaceMenuItem;
	}

	public MenuItem getClearSelectedMenuItem() {
		return clearSelectedMenuItem;
	}

	public MenuItem getClearAllMenuItem() {
		return clearAllMenuItem;
	}

	public RadioMenuItem getShowHTMLInfoMenuItem() {
		return showHTMLInfoMenuItem;
	}

	public Label getInfoLabel() {
		return infoLabel;
	}

	public FlowPane getHtmlInfoFlowPane() {
		return htmlInfoFlowPane;
	}
}
