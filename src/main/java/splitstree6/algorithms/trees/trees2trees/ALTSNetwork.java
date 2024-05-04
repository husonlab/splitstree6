/*
 *  ALTSNetwork.java Copyright (C) 2024 Daniel H. Huson
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
import splitstree6.utils.ProgressMover;
import splitstree6.xtra.alts.AltsNonBinary;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;

/**
 * this runs the non-binary ALTSNetwork network algorithm
 * Daniel Huson, 2.2024
 */
public class ALTSNetwork extends Trees2Trees {

	@Override
	public String getCitation() {
		return "Zhang et al 2023; L. Zhang, N. Abhari, C. Colijn and Y Wu." +
			   " A fast and scalable method for inferring phylogenetic networks from trees by aligning lineage taxon strings. Genome Res. 2023.;"
			   + "Zhang et al 2024; L. Zhang, B. Cetinkaya and DH Huson. Hybridization networks from phylogenetic trees, in preparation.";
	}

	@Override
	public String getShortDescription() {
		return "Computes one or more rooted networks that contain all input trees using the M-ALTS algorithm.";
	}


	@Override
	public void compute(ProgressListener progress, TaxaBlock taxaBlock, TreesBlock treesBlock, TreesBlock outputBlock) throws IOException {
		progress.setTasks("Computing hybridization networks", "(Unknown how long this will really take)");
		try (var progressMover = new ProgressMover(progress)) {
			Collection<PhyloTree> result;

			Collection<PhyloTree> inputTrees = new ArrayList<>();
				for (var tree : treesBlock.getTrees()) {
					inputTrees.add(new PhyloTree(tree));
				}

			if (inputTrees.size() <= 1) {
				result = inputTrees;
			} else {
				result = AltsNonBinary.apply(inputTrees, progress);
			}
			var count = 0;
			for (var tree : result) {
				tree.setName("N" + (++count));
				for (var v : tree.nodeStream().filter(v -> tree.getLabel(v) != null).toList()) {
					tree.addTaxon(v, taxaBlock.indexOf(tree.getLabel(v)));
				}
			}
			outputBlock.getTrees().addAll(result);
			outputBlock.setReticulated(result.stream().anyMatch(PhyloTree::isReticulated));
		}
	}

	@Override
	public boolean isApplicable(TaxaBlock taxa, TreesBlock datablock) {
		return !datablock.isReticulated() && datablock.getNTrees() > 1;
	}
}
