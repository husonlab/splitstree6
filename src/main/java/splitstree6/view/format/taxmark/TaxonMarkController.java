/*
 *  TaxonMarkController.java Copyright (C) 2024 Daniel H. Huson
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

package splitstree6.view.format.taxmark;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.VBox;
import jloda.fx.shapes.NodeShape;

public class TaxonMarkController {

	@FXML
	private Button clearColorButton;

	@FXML
	private ColorPicker fillColorPicker;

	@FXML
	private TitledPane titledPane;

	@FXML
	private VBox vBox;

	@FXML
	private Button addButton;

	@FXML
	private ChoiceBox<NodeShape> shapeCBox;

	@FXML
	private void initialize() {
		shapeCBox.getItems().addAll(NodeShape.values());
	}

	public Button getClearColorButton() {
		return clearColorButton;
	}

	public ColorPicker getFillColorPicker() {
		return fillColorPicker;
	}

	public TitledPane getTitledPane() {
		return titledPane;
	}

	public VBox getvBox() {
		return vBox;
	}

	public ChoiceBox<NodeShape> getShapeCBox() {
		return shapeCBox;
	}

	public Button getAddButton() {
		return addButton;
	}
}
