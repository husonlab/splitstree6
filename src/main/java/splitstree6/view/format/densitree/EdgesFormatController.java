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

package splitstree6.view.format.densitree;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.TitledPane;
import javafx.util.converter.FormatStringConverter;

import java.text.NumberFormat;

public class EdgesFormatController {

	@FXML
	private ColorPicker colorPicker;

	@FXML
	private ColorPicker otherColorPicker;

	@FXML
	private TitledPane titledPane;

	@FXML
	private ChoiceBox<Number> widthCBox;


	@FXML
	private Button resetColorButton;

	@FXML
	private Button resetOtherColorButton;

	@FXML
	private Button resetWidthButton;


	@FXML
	private void initialize() {
		widthCBox.setConverter(new FormatStringConverter<>(NumberFormat.getInstance()));
	}

	public ColorPicker getColorPicker() {
		return colorPicker;
	}

	public ColorPicker getOtherColorPicker() {
		return otherColorPicker;
	}

	public TitledPane getTitledPane() {
		return titledPane;
	}

	public ChoiceBox<Number> getWidthCBox() {
		return widthCBox;
	}

	public Button getResetColorButton() {
		return resetColorButton;
	}

	public Button getResetOtherColorButton() {
		return resetOtherColorButton;
	}

	public Button getResetWidthButton() {
		return resetWidthButton;
	}
}
