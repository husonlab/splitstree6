/*
 * TreeFilterTabController.java Copyright (C) 2022 Daniel H. Huson
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
package splitstree6.tabs.algorithms.treefilter;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.VBox;

public class TreeFilterTabController {

	@FXML
	private AnchorPane anchorPane;

	@FXML
	private VBox topVBox;

	@FXML
	private TableView<TreeFilterTableItem> tableView;

	@FXML
	private TableColumn<TreeFilterTableItem, Integer> idColumn;

	@FXML
	private TableColumn<TreeFilterTableItem, Boolean> activeColumn;

	@FXML
	private MenuItem activateAllMenuItem;

	@FXML
	private MenuItem activateNoneMenuItem;

	@FXML
	private MenuItem activateSelectedMenuItem;

	@FXML
	private MenuItem deactivateSelectedMenuItem;

	@FXML
	private TableColumn<TreeFilterTableItem, String> nameColumn;

	@FXML
	private Label infoLabel;

	public AnchorPane getAnchorPane() {
		return anchorPane;
	}

	public VBox getTopVBox() {
		return topVBox;
	}

	public TableView<TreeFilterTableItem> getTableView() {
		return tableView;
	}

	public TableColumn<TreeFilterTableItem, Integer> getIdColumn() {
		return idColumn;
	}

	public TableColumn<TreeFilterTableItem, Boolean> getActiveColumn() {
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

	public TableColumn<TreeFilterTableItem, String> getNameColumn() {
		return nameColumn;
	}

	public Label getInfoLabel() {
		return infoLabel;
	}
}
