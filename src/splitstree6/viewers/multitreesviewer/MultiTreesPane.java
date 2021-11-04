/*
 *  MultiTreesPane.java Copyright (C) 2021 Daniel H. Huson
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

package splitstree6.viewers.multitreesviewer;

import javafx.beans.property.ReadOnlyDoubleProperty;
import javafx.collections.ObservableList;
import javafx.scene.layout.GridPane;
import jloda.phylo.PhyloTree;
import splitstree6.data.TaxaBlock;

public class MultiTreesPane extends GridPane {
	private final MultiTreesViewer multiTreesViewer;
	private final ReadOnlyDoubleProperty boxWidth;
	private final ReadOnlyDoubleProperty boxHeight;

	public MultiTreesPane(MultiTreesViewer multiTreesViewer, ReadOnlyDoubleProperty boxWidth, ReadOnlyDoubleProperty boxHeight) {
		this.multiTreesViewer = multiTreesViewer;

		this.boxWidth = boxWidth;
		this.boxHeight = boxHeight;
	}

	public Runnable addTrees(TaxaBlock taxaBlock, ObservableList<PhyloTree> trees, Integer page) {
		var start = (page - 1) * multiTreesViewer.getRows() * multiTreesViewer.getCols();

		var row = 0;
		var col = 0;
		for (var t = start; t < trees.size(); t++) {
			var treePane = new TreePane(multiTreesViewer.getOptionDiagram(), multiTreesViewer.getOptionRootSide(), multiTreesViewer.isOptionToScale(), multiTreesViewer.fontProperty());
			treePane.prefWidthProperty().bind(boxWidth);
			treePane.prefHeightProperty().bind(boxHeight);
			trees.get(t).setName("tree-" + (t + 1));
			treePane.drawTree(taxaBlock, trees.get(t));
			getChildren().add(treePane);
			GridPane.setRowIndex(treePane, row);
			GridPane.setColumnIndex(treePane, col);
			if (++col == multiTreesViewer.getCols()) {
				if (row + 1 < multiTreesViewer.getRows()) {
					row++;
					col = 0;
				} else
					break;
			}
		}

		return null;
	}
}
