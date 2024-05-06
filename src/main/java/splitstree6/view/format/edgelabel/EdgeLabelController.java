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

package splitstree6.view.format.edgelabel;

import javafx.fxml.FXML;
import javafx.scene.control.MenuButton;
import javafx.scene.control.RadioMenuItem;
import javafx.scene.control.TitledPane;
import javafx.scene.control.ToggleGroup;

public class EdgeLabelController {

	@FXML
	private RadioMenuItem labelByConfidenceMenuItem;

	@FXML
	private RadioMenuItem labelByConfidenceX100MenuItem;

	@FXML
	private MenuButton labelByMenuButton;

	@FXML
	private RadioMenuItem labelByNoneMenuItem;

	@FXML
	private RadioMenuItem labelByProbabilityMenuItem;

	@FXML
	private RadioMenuItem labelByWeightMenuItem;

	@FXML
	private TitledPane titledPane;

	private final ToggleGroup labelByToggleGroup = new ToggleGroup();

	@FXML
	private void initialize() {
		labelByToggleGroup.getToggles().addAll(labelByNoneMenuItem, labelByWeightMenuItem, labelByConfidenceMenuItem, labelByConfidenceX100MenuItem, labelByProbabilityMenuItem);
		labelByToggleGroup.selectedToggleProperty().addListener((v, o, n) -> {
			if (n != null)
				labelByMenuButton.setText(((RadioMenuItem) n).getText());
		});
	}

	public TitledPane getTitledPane() {
		return titledPane;
	}

	public RadioMenuItem getLabelByConfidenceMenuItem() {
		return labelByConfidenceMenuItem;
	}

	public RadioMenuItem getLabelByConfidenceX100MenuItem() {
		return labelByConfidenceX100MenuItem;
	}

	public RadioMenuItem getLabelByNoneMenuItem() {
		return labelByNoneMenuItem;
	}

	public RadioMenuItem getLabelByProbabilityMenuItem() {
		return labelByProbabilityMenuItem;
	}

	public RadioMenuItem getLabelByWeightMenuItem() {
		return labelByWeightMenuItem;
	}

	public ToggleGroup getLabelByToggleGroup() {
		return labelByToggleGroup;
	}
}
