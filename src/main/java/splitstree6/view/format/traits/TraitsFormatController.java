/*
 * TraitsFormatController.java Copyright (C) 2023 Daniel H. Huson
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

package splitstree6.view.format.traits;

import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.util.converter.IntegerStringConverter;

public class TraitsFormatController {

	@FXML
	private VBox vBox;

	@FXML
	private MenuButton showMenuButton;

	@FXML
	private MenuItem showAllMenuItem;

	@FXML
	private MenuItem showNoneMenuItem;

	@FXML
	private CheckBox legendCBox;

	@FXML
	private TextField maxSizeField;

	@FXML
	private TitledPane titledPane;

	@FXML
	private void initialize() {
		titledPane.setDisable(true);
		maxSizeField.setTextFormatter(new TextFormatter<>(new IntegerStringConverter()));
		legendCBox.setSelected(true);
	}

	public VBox getvBox() {
		return vBox;
	}

	public MenuButton getShowMenuButton() {
		return showMenuButton;
	}

	public MenuItem getShowAllMenuItem() {
		return showAllMenuItem;
	}

	public MenuItem getShowNoneMenuItem() {
		return showNoneMenuItem;
	}

	public CheckBox getLegendCBox() {
		return legendCBox;
	}

	public TextField getMaxSizeField() {
		return maxSizeField;
	}

	public TitledPane getTitledPane() {
		return titledPane;
	}
}
