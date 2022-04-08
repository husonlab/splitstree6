/*
 * TaxaFilterController.java Copyright (C) 2022 Daniel H. Huson
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

package splitstree6.tabs.algorithms.taxafilter;

import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;

public class TaxaFilterController {

	@FXML
	private AnchorPane anchorPane;

	@FXML
	private VBox topVBox;

	@FXML
	private TableView<TaxaFilterTableItem> tableView;

	@FXML
	private TableColumn<TaxaFilterTableItem, Boolean> activeColumn;

	@FXML
	private MenuItem activateAllMenuItem;

	@FXML
	private MenuItem activateNoneMenuItem;

	@FXML
	private MenuItem activateSelectedMenuItem;

	@FXML
	private MenuItem deactivateSelectedMenuItem;
	@FXML
	private MenuItem selectCurrentlyActiveMenuItem;

	@FXML
	private MenuItem selectActivatedMenuItem;

	@FXML
	private TableColumn<TaxaFilterTableItem, Integer> idColumn;

	@FXML
	private TableColumn<TaxaFilterTableItem, String> nameColumn;

	@FXML
	private TableColumn<TaxaFilterTableItem, String> displayLabelColumn;

	@FXML
	private MenuItem findAndReplaceRadioMenuItem;

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

	public TableView<TaxaFilterTableItem> getTableView() {
		return tableView;
	}

	public TableColumn<TaxaFilterTableItem, Boolean> getActiveColumn() {
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

	public MenuItem getDeactivateSelectedMenuItem() {
		return deactivateSelectedMenuItem;
	}

	public MenuItem getSelectCurrentlyActiveMenuItem() {
		return selectCurrentlyActiveMenuItem;
	}

	public MenuItem getSelectActivatedMenuItem() {
		return selectActivatedMenuItem;
	}

	public TableColumn<TaxaFilterTableItem, Integer> getIdColumn() {
		return idColumn;
	}

	public TableColumn<TaxaFilterTableItem, String> getNameColumn() {
		return nameColumn;
	}

	public TableColumn<TaxaFilterTableItem, String> getDisplayLabelColumn() {
		return displayLabelColumn;
	}

	public MenuItem getFindAndReplaceRadioMenuItem() {
		return findAndReplaceRadioMenuItem;
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
