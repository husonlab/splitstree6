/*
 * BranchFormatterController.java Copyright (C) 2022 Daniel H. Huson
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

package splitstree6.view.format.branches;

import javafx.fxml.FXML;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.ComboBox;
import javafx.util.converter.FormatStringConverter;

import java.text.NumberFormat;

public class BranchFormatterController {

	@FXML
	private ComboBox<Double> widthCBox;

	@FXML
	private ColorPicker colorPicker;

	@FXML
	private void initialize() {
		widthCBox.setConverter(new FormatStringConverter<Double>(NumberFormat.getInstance()));
	}

	public ComboBox<Double> getWidthCBox() {
		return widthCBox;
	}

	public ColorPicker getColorPicker() {
		return colorPicker;
	}
}
