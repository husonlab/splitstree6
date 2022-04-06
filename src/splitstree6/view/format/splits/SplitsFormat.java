/*
 * SplitsFormat.java Copyright (C) 2022 Daniel H. Huson
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

import javafx.beans.property.ObjectProperty;
import javafx.scene.Group;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Shape;
import jloda.fx.selection.SelectionModel;
import jloda.fx.undo.UndoManager;
import jloda.fx.util.ExtendedFXMLLoader;
import jloda.graph.Node;
import splitstree6.layout.splits.SplitsDiagramType;

import java.util.ArrayList;
import java.util.Map;

/**
 * splits formatter pane
 * Daniel Huson, 1.2022
 */
public class SplitsFormat extends Pane {
	private final SplitsFormatController controller;
	private final SplitsFormatPresenter presenter;

	public SplitsFormat(UndoManager undoManager, SelectionModel<Integer> splitSelectionModel, Map<Node, Group> nodeShapeMap,
						Map<Integer, ArrayList<Shape>> splitShapeMap, ObjectProperty<SplitsDiagramType> optionDiagram,
						ObjectProperty<Color> optionOutlineFill, ObjectProperty<String[]> editsProperty) {
		var loader = new ExtendedFXMLLoader<SplitsFormatController>(SplitsFormatController.class);
		controller = loader.getController();
		getChildren().add(loader.getRoot());

		presenter = new SplitsFormatPresenter(undoManager, controller, splitSelectionModel, nodeShapeMap, splitShapeMap, optionDiagram,
				optionOutlineFill, editsProperty);
	}

	public SplitsFormatPresenter getPresenter() {
		return presenter;
	}
}
