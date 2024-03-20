/*
 *  SelectTraitsController.java Copyright (C) 2024 Daniel H. Huson
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

package splitstree6.view.format.selecttraits;

import javafx.fxml.FXML;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TitledPane;

public class SelectTraitsController {
	@FXML
	private TitledPane titledPane;

	@FXML
	private MenuItem allValuesMenuItem;

	@FXML
	private MenuItem noneValueMenuItem;

	@FXML
	private MenuButton traitMenuButton;

	@FXML
	private MenuButton traitValuesMenuButton;

	@FXML
	private void initialize() {
		titledPane.setDisable(true);
	}

	public TitledPane getTitledPane() {
		return titledPane;
	}

	public MenuItem getAllValuesMenuItem() {
		return allValuesMenuItem;
	}

	public MenuItem getNoneValueMenuItem() {
		return noneValueMenuItem;
	}

	public MenuButton getTraitMenuButton() {
		return traitMenuButton;
	}

	public MenuButton getTraitValuesMenuButton() {
		return traitValuesMenuButton;
	}
}
