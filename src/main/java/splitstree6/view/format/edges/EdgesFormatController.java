/*
 *  EdgeLabelController.java Copyright (C) 2024 Daniel H. Huson
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

package splitstree6.view.format.edges;

import javafx.fxml.FXML;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.MenuButton;
import javafx.scene.control.TitledPane;
import javafx.scene.control.ToggleGroup;

public class EdgesFormatController {

	@FXML
	private MenuButton widthMenuButton;


	@FXML
	private ColorPicker colorPicker;

	@FXML
	private TitledPane titledPane;

	private final ToggleGroup labelByToggleGroup = new ToggleGroup();

	@FXML
	private void initialize() {

	}

	public MenuButton getWidthMenuButton() {
		return widthMenuButton;
	}

	public ColorPicker getColorPicker() {
		return colorPicker;
	}

	public TitledPane getTitledPane() {
		return titledPane;
	}

	public ToggleGroup getLabelByToggleGroup() {
		return labelByToggleGroup;
	}
}
