/*
 *  EdgeLabelFormat.java Copyright (C) 2024 Daniel H. Huson
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

import javafx.beans.property.ObjectProperty;
import javafx.scene.Group;
import jloda.fx.selection.SelectionModel;
import jloda.fx.undo.UndoManager;
import jloda.fx.util.ExtendedFXMLLoader;
import jloda.graph.Edge;
import splitstree6.layout.tree.LabeledEdgeShape;

import java.util.Map;

/**
 * edge formatter pane
 * Daniel Huson, 5.2022
 */
public class EdgesFormat extends Group {

	private final EdgesFormatPresenter presenter;

	public EdgesFormat(UndoManager undoManager, SelectionModel<Edge> edgeSelectionModel, Map<Edge, LabeledEdgeShape> edgeShapeMap,
					   ObjectProperty<String[]> editsProperty) {
		var loader = new ExtendedFXMLLoader<EdgesFormatController>(EdgesFormatController.class);
		EdgesFormatController controller = loader.getController();
		getChildren().add(loader.getRoot());

		presenter = new EdgesFormatPresenter(undoManager, controller, edgeSelectionModel, edgeShapeMap, editsProperty);
	}

	public EdgesFormatPresenter getPresenter() {
		return presenter;
	}

}
