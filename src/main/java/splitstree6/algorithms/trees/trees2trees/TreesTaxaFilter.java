/*
 *  TreesTaxaFilter.java Copyright (C) 2024 Daniel H. Huson
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

package splitstree6.algorithms.trees.trees2trees;

import jloda.phylo.PhyloTree;
import jloda.util.BitSetUtils;
import jloda.util.StringUtils;
import jloda.util.progress.ProgressListener;
import splitstree6.data.TaxaBlock;
import splitstree6.data.TreesBlock;
import splitstree6.splits.TreesUtils;
import splitstree6.workflow.DataTaxaFilter;

import java.io.IOException;

public class TreesTaxaFilter extends DataTaxaFilter<TreesBlock, TreesBlock> {
	public TreesTaxaFilter() {
		super(TreesBlock.class, TreesBlock.class);
	}

	@Override
	public void filter(ProgressListener progress, TaxaBlock originalTaxaBlock, TaxaBlock modifiedTaxaBlock, TreesBlock inputData, TreesBlock outputData) throws IOException {
		if (modifiedTaxaBlock == null || originalTaxaBlock.getTaxa().equals(modifiedTaxaBlock.getTaxa())) {
			outputData.copy(inputData);
			setShortDescription("using all " + originalTaxaBlock.size() + " taxa");
		} else {
			final int[] oldTaxonId2NewTaxonId = new int[originalTaxaBlock.getNtax() + 1];
			for (int t = 1; t <= originalTaxaBlock.getNtax(); t++) {
				oldTaxonId2NewTaxonId[t] = modifiedTaxaBlock.indexOf(originalTaxaBlock.get(t).getName());
			}

			progress.setMaximum(inputData.getNTrees());

			for (PhyloTree tree : inputData.getTrees()) {
				// PhyloTree inducedTree = computeInducedTree(tree, modifiedTaxaBlock.getLabels());
				final PhyloTree inducedTree = TreesUtils.computeInducedTree(oldTaxonId2NewTaxonId, tree);
				if (inducedTree != null) {
					outputData.getTrees().add(inducedTree);
					if (false && !BitSetUtils.contains(modifiedTaxaBlock.getTaxaSet(), TreesUtils.getTaxa(inducedTree))) {
						System.err.println("taxa:" + StringUtils.toString(modifiedTaxaBlock.getTaxaSet()));
						System.err.println("tree:" + StringUtils.toString(TreesUtils.getTaxa(inducedTree)));
						throw new RuntimeException("Induce tree failed");
					}
				}
				progress.incrementProgress();
			}

			setShortDescription("using " + modifiedTaxaBlock.size() + " of " + originalTaxaBlock.size() + " taxa");
		}
		outputData.setPartial(inputData.isPartial());
		outputData.setRooted(inputData.isRooted());
	}
}
