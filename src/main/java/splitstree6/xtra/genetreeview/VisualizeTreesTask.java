/*
 *  VisualizeTreesTask.java Copyright (C) 2023 Daniel H. Huson
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

package splitstree6.xtra.genetreeview;

import javafx.concurrent.Task;
import javafx.scene.Group;
import jloda.phylo.PhyloTree;
import splitstree6.layout.tree.*;
import splitstree6.xtra.genetreeview.layout.TreeSheet;
import splitstree6.xtra.genetreeview.model.Model;
import splitstree6.xtra.genetreeview.util.SelectionModelSet;

import java.util.HashMap;

public class VisualizeTreesTask extends Task<Group> {

    private final Model model;
    private final double treeWidth;
    private final double treeHeight;
    private final TreeDiagramType diagram;
    private final SelectionModelSet<Integer> taxaSelectionModel;
    private final HashMap<Integer,SelectionModelSet<Integer>> edgeSelectionModels;

    public VisualizeTreesTask(Model model, double treeWidth, double treeHeight, TreeDiagramType diagram,
                              SelectionModelSet<Integer> taxaSelectionModel,
                              HashMap<Integer,SelectionModelSet<Integer>> edgeSelectionModels) {
        this.model = model;
        this.treeWidth = treeWidth;
        this.treeHeight = treeHeight;
        this.diagram = diagram;
        this.taxaSelectionModel = taxaSelectionModel;
        this.edgeSelectionModels = edgeSelectionModels;
    }

    @Override
    protected Group call() throws Exception {
        Group trees = new Group();
        int treeIndex = 0;
        for (int treeId : model.getGeneTreeSet().getTreeOrder()) {
            PhyloTree phyloTree = model.getGeneTreeSet().getPhyloTree(treeId);
            if (!edgeSelectionModels.containsKey(treeId)) edgeSelectionModels.put(treeId, new SelectionModelSet<>());
            TreeSheet treeSheet = new TreeSheet(phyloTree, treeId, treeWidth, treeHeight, diagram, model.getTaxaBlock(),
                    taxaSelectionModel, edgeSelectionModels.get(treeId));
            trees.getChildren().add(treeIndex, treeSheet);
            treeIndex++;
            updateProgress(treeIndex, model.getGeneTreeSet().size());
        }
        return trees;
    }
}
