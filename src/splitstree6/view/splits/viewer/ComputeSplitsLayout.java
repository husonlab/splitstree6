/*
 *  ComputeSplitsLayout.java Copyright (C) 2021 Daniel H. Huson
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

package splitstree6.view.splits.viewer;

import javafx.scene.Group;
import javafx.scene.shape.Shape;
import jloda.fx.control.RichTextLabel;
import jloda.fx.util.AService;
import jloda.fx.util.TriConsumer;
import jloda.fx.window.NotificationManager;
import jloda.graph.Edge;
import jloda.graph.Node;
import splitstree6.data.SplitsBlock;
import splitstree6.data.TaxaBlock;
import splitstree6.window.MainWindow;

import java.util.function.BiConsumer;

public class ComputeSplitsLayout {
	public static void apply(MainWindow mainWindow, TaxaBlock taxaBlock, SplitsBlock splitsBlock, SplitsDiagramType diagramType, SplitsRooting root, double width, double height,
							 TriConsumer<Node, Shape, RichTextLabel> nodeCallback, BiConsumer<Edge, Shape> edgeCallback, boolean linkNodesEdgesLabels, Group group) {

		var service = new AService<Group>(mainWindow.getController().getBottomFlowPane());

		service.setCallable(() -> {
			var result = new Group();
			if (diagramType == SplitsDiagramType.Outline) {

			} else if (diagramType == SplitsDiagramType.Splits) {

			}
			return result;
		});

		service.setOnFailed(e -> NotificationManager.showError("Compute splits layout failed: " + service.getException()));
		service.setOnSucceeded(e -> group.getChildren().setAll(service.getValue()));
		service.restart();
	}
}
