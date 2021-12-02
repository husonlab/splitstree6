/*
 *  AlgorithmTab.java Copyright (C) 2021 Daniel H. Huson
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

package splitstree6.tabs.algorithms;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceBox;
import javafx.scene.layout.Pane;
import splitstree6.workflow.Algorithm;
import splitstree6.workflow.DataBlock;

public class AlgorithmTabController<S extends DataBlock, T extends DataBlock> {

	@FXML
	private Pane topPane;
	@FXML
	private Pane mainPane;

	@FXML
	private ChoiceBox<Algorithm<S, T>> algorithmCBox;

	@FXML
	private Button Reset;

	@FXML
	private Button applyButton;

	public ChoiceBox<Algorithm<S, T>> getAlgorithmCBox() {
		return algorithmCBox;
	}

	public Button getReset() {
		return Reset;
	}

	public Button getApplyButton() {
		return applyButton;
	}

	public Pane getMainPane() {
		return mainPane;
	}

	public Pane getTopPane() {
		return topPane;
	}
}
