/*
 *  SimilarityCalculationTask.java Copyright (C) 2024 Daniel H. Huson
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

package splitstree6.xtra.genetreeview.model;

import javafx.concurrent.Task;
import jloda.phylo.PhyloTree;

import java.util.ArrayList;
import java.util.LinkedHashMap;

public class SimilarityCalculationTask extends Task<LinkedHashMap<Integer, Integer>> {

	private final ArrayList<GeneTree> geneTrees;
	private final PhyloTree referenceTree;

	public SimilarityCalculationTask(ArrayList<GeneTree> geneTrees, PhyloTree referenceTree) {
		this.geneTrees = geneTrees;
		this.referenceTree = referenceTree;
	}

	@Override
	protected LinkedHashMap<Integer, Integer> call() throws Exception {
		LinkedHashMap<Integer, Integer> id2similarities = new LinkedHashMap<>();
		for (GeneTree geneTree : geneTrees) {
			int robinsonFouldsDistance = RobinsonFouldsDistance.calculate(referenceTree, geneTree.getPhyloTree());
			if (robinsonFouldsDistance < 0) {
				System.out.println("Negative distance calculated with tree " + geneTree.getGeneName());
				robinsonFouldsDistance = 0;
			}
			int maximum = referenceTree.getNumberOfEdges() + geneTree.getPhyloTree().getNumberOfEdges();
			int similarity = maximum - robinsonFouldsDistance;
			id2similarities.put(geneTree.getId(), similarity);
		}
		return id2similarities;
	}
}
