/*
 * BranchFormatter.java Copyright (C) 2022 Daniel H. Huson
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

import javafx.scene.layout.Pane;
import javafx.scene.shape.Shape;
import jloda.fx.selection.SelectionModel;
import jloda.fx.util.ExtendedFXMLLoader;
import splitstree6.window.MainWindow;

import java.util.ArrayList;
import java.util.Map;

public class BranchFormatter extends Pane {
	private final BranchFormatterController controller;
	private final BranchFormatterPresenter presenter;

	public BranchFormatter(MainWindow mainWindow, SelectionModel<Integer> splitSelectionModel, Map<Integer, ArrayList<Shape>> splitShapeMap) {
		var loader = new ExtendedFXMLLoader<BranchFormatterController>(BranchFormatterController.class);
		controller = loader.getController();
		getChildren().add(loader.getRoot());

		presenter = new BranchFormatterPresenter(mainWindow, controller, splitSelectionModel, splitShapeMap);
	}
}
