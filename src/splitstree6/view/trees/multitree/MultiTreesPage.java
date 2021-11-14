/*
 *  MultiTreesPage.java Copyright (C) 2021 Daniel H. Huson
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

package splitstree6.view.trees.multitree;

import javafx.beans.property.ReadOnlyDoubleProperty;
import javafx.collections.ObservableList;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.RowConstraints;
import jloda.fx.selection.SelectionModel;
import jloda.phylo.PhyloTree;
import splitstree6.data.TaxaBlock;
import splitstree6.data.parts.Taxon;

/**
 * a page of trees
 * Daniel Huson, 11.2021
 */
public class MultiTreesPage extends GridPane {
	private final SelectionModel<Taxon> taxonSelectionModel;
	private final MultiTreesView multiTreesView;
	private final ReadOnlyDoubleProperty boxWidth;
	private final ReadOnlyDoubleProperty boxHeight;
	private TaxaBlock taxaBlock;
	private ObservableList<PhyloTree> trees;
	private Integer page;

	public MultiTreesPage(SelectionModel<Taxon> taxonSelectionModel, MultiTreesView multiTreesView, ReadOnlyDoubleProperty boxWidth, ReadOnlyDoubleProperty boxHeight) {
		this.taxonSelectionModel = taxonSelectionModel;
		this.multiTreesView = multiTreesView;
		this.boxWidth = boxWidth;
		this.boxHeight = boxHeight;

		for (var i = 0; i < multiTreesView.getCols(); i++) {
			var column = new ColumnConstraints(boxWidth.get());
			getColumnConstraints().add(column);
		}
		for (var i = 0; i < multiTreesView.getRows(); i++) {
			var row = new RowConstraints(boxHeight.get());
			getRowConstraints().add(row);
		}
		getStyleClass().add("background");
	}

	public void setTrees(TaxaBlock taxaBlock, ObservableList<PhyloTree> trees, Integer page) {
		this.taxaBlock = taxaBlock;
		this.trees = trees;
		this.page = page;

		getChildren().clear();
		var start = (page - 1) * multiTreesView.getRows() * multiTreesView.getCols();

		var row = 0;
		var col = 0;
		for (var t = start; t < trees.size(); t++) {
			var treePane = new TreePane(taxaBlock, trees.get(t), taxonSelectionModel, boxWidth, boxHeight,
					multiTreesView.getOptionDiagram(), multiTreesView.getOptionRootSide(), multiTreesView.optionFontScaleFactorProperty());
			trees.get(t).setName("tree-" + (t + 1));
			treePane.drawTree();
			getChildren().add(treePane);
			GridPane.setRowIndex(treePane, row);
			GridPane.setColumnIndex(treePane, col);

			if (++col == multiTreesView.getCols()) {
				if (row + 1 < multiTreesView.getRows()) {
					row++;
					col = 0;
				} else
					break;
			}
		}
	}

	public void redraw() {
		if (taxaBlock != null && trees != null && page != null) {
			setTrees(taxaBlock, trees, page);
		}
	}
}
