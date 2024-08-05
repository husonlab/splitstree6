/*
 *  SelectionManager.java Copyright (C) 2024 Daniel H. Huson
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

package splitstree6.xtra.draw;

import javafx.scene.layout.Pane;
import javafx.scene.shape.Path;
import javafx.scene.shape.Shape;
import jloda.fx.selection.SelectionModel;
import jloda.graph.Edge;
import jloda.graph.Node;
import splitstree6.main.SplitsTree6;

public class SelectionManager {
	public static void setupNodeSelection(Node v, Shape shape, SelectionModel<Node> nodeSelectionModel, SelectionModel<Edge> edgeSelectionModel) {
		shape.setOnMouseClicked(me -> {
			if (me.getClickCount() == 1 && me.isStillSincePress()) {
				if (SplitsTree6.isDesktop() && !me.isShiftDown()) {
					nodeSelectionModel.clearSelection();
					edgeSelectionModel.clearSelection();
				}
				nodeSelectionModel.toggleSelection(v);
				me.consume();
			}
		});
	}

	public static void setupEdgeSelection(Edge edge, Path path, SelectionModel<Node> nodeSelectionModel, SelectionModel<Edge> edgeSelectionModel) {
		path.setOnMouseClicked(me -> {
			if (me.getClickCount() == 1 && me.isStillSincePress()) {
				if (SplitsTree6.isDesktop() && !me.isShiftDown()) {
					nodeSelectionModel.clearSelection();
					edgeSelectionModel.clearSelection();
				}
				edgeSelectionModel.toggleSelection(edge);
				me.consume();
			}
		});
	}

	public static void setupPaneSelection(Pane pane, SelectionModel<Node> nodeSelectionModel, SelectionModel<Edge> edgeSelectionModel) {
		pane.setOnMouseClicked(me -> {
			if ((me.getClickCount() == 2 || !SplitsTree6.isDesktop() && me.getClickCount() == 1) && me.isStillSincePress()) {
				nodeSelectionModel.clearSelection();
				edgeSelectionModel.clearSelection();
				me.consume();
			}
		});
	}
}
