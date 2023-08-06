/*
 *  SimilarityCalculationTask.java Copyright (C) 2023 Daniel H. Huson
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
import jloda.phylo.PhyloTree;
import splitstree6.data.TreesBlock;

import java.util.ArrayList;
import java.util.Collections;
import java.util.TreeMap;

public class SimilarityCalculationTask extends Task<ArrayList<Integer>> {

    private final TreesBlock treesBlock;
    private final PhyloTree referenceTree;

    public SimilarityCalculationTask(TreesBlock treesBlock, PhyloTree referenceTree) {
        this.treesBlock = treesBlock;
        this.referenceTree = referenceTree;
    }

    @Override
    protected ArrayList<Integer> call() throws Exception {
        ArrayList<Integer> similarities = new ArrayList<>();
        for (PhyloTree tree : treesBlock.getTrees()) {
            int robinsonFouldsDistance = RobinsonFouldsDistance.calculate(referenceTree, tree);
            if (robinsonFouldsDistance < 0) { // can happen with partial trees
                System.out.println("Negative distance calculated with tree "+tree.getName());
                robinsonFouldsDistance = 0;
            }
            System.out.println("Distance of "+referenceTree.getName()+" and "+tree.getName()+": "+robinsonFouldsDistance);
            int maximum = referenceTree.getNumberOfEdges() + tree.getNumberOfEdges();
            int similarity = maximum - robinsonFouldsDistance;
            System.out.println("Similarity of "+referenceTree.getName()+" and "+tree.getName()+": "+similarity);
            similarities.add(similarity);
        }
        return similarities;
    }
}
