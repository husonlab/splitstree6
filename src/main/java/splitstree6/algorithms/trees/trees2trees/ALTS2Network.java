/*
 *  ALTSNetwork.java Copyright (C) 2023 Daniel H. Huson
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
import jloda.util.progress.ProgressListener;
import splitstree6.data.TaxaBlock;
import splitstree6.data.TreesBlock;
import splitstree6.workflow.interfaces.DoNotLoadThisAlgorithm;

import java.io.IOException;
import java.util.List;

/**
 * this runs the non-binary ALTS Network algorithm
 * Banu Cetinkaya and Daniel Huson, 10.2023
 */
public class ALTS2Network extends Trees2Trees implements DoNotLoadThisAlgorithm { // comment DoNotLoadThisAlgorithm to use

	public String getCitation() {
		return "Zhang et al 2023; L. Zhang, N. Abhari, C. Colijn and Y. Wu3." +
			   " A fast and scalable method for inferring phylogenetic networks from trees by aligning lineage taxon strings. Genome Res. 2023;" +
			   "Cetinkaya et al 2023. B. Cetinkaya, D.H. Huson, L. Zhang. Inferring phylogenetic network from non-binary gene trees, in preparation;";
	}

	public List<String> listOptions() {
		return List.of();
	}

	public String getToolTip(String optionName) {
		return optionName;
	}

	public void compute(ProgressListener progress, TaxaBlock taxaBlock, TreesBlock treesBlock, TreesBlock outputBlock) throws IOException {
		outputBlock.setRooted(true);
		// just copying trees to output
		for (var t = 1; t <= treesBlock.getNTrees(); t++) {
			var treeCopy = new PhyloTree(treesBlock.getTree(t));
			outputBlock.getTrees().add(treeCopy);
		}
	}

	public boolean isApplicable(TaxaBlock taxa, TreesBlock datablock) {
		return !datablock.isReticulated() && datablock.isRooted();
	}
}
