/*
 * SplitsFormatController.java Copyright (C) 2022 Daniel H. Huson
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

package splitstree6.view.format.splits;

import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.util.converter.FormatStringConverter;

import java.text.NumberFormat;

public class SplitsFormatController {

	@FXML
	private Label outlineFillLabel;

	@FXML
	private ComboBox<Number> widthCBox;

	@FXML
	private ColorPicker lineColorPicker;

	@FXML
	private Button rotateLeftButton;

	@FXML
	private Button rotateRightButton;

	@FXML
	private ColorPicker outlineFillColorPicker;

	@FXML
	private Button resetLineColorButton;

	@FXML
	private Button resetOutlineFillColorButton;

	@FXML
	private Button resetWidthButton;

	@FXML
	private TitledPane titledPane;

	@FXML
	private void initialize() {
		widthCBox.setConverter(new FormatStringConverter<>(NumberFormat.getInstance()));
		outlineFillLabel.disableProperty().bind(outlineFillColorPicker.disableProperty());
	}


	public ComboBox<Number> getWidthCBox() {
		return widthCBox;
	}

	public ColorPicker getLineColorPicker() {
		return lineColorPicker;
	}

	public Button getRotateLeftButton() {
		return rotateLeftButton;
	}

	public Button getRotateRightButton() {
		return rotateRightButton;
	}

	public ColorPicker getOutlineFillColorPicker() {
		return outlineFillColorPicker;
	}

	public TitledPane getTitledPane() {
		return titledPane;
	}

	public Button getResetLineColorButton() {
		return resetLineColorButton;
	}

	public Button getResetOutlineFillColorButton() {
		return resetOutlineFillColorButton;
	}

	public Button getResetWidthButton() {
		return resetWidthButton;
	}
}
