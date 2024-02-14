/*
 *  ALTSExternal.java Copyright (C) 2024 Daniel H. Huson
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

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import jloda.fx.window.NotificationManager;
import jloda.phylo.PhyloTree;
import jloda.util.progress.ProgressListener;
import splitstree6.data.TaxaBlock;
import splitstree6.data.TreesBlock;
import splitstree6.utils.ProgressMover;
import splitstree6.xtra.alts.AltsNonBinary;
import splitstree6.xtra.kernelize.Kernelize;

import java.io.IOException;
import java.util.Collection;
import java.util.List;

/**
 * this runs the non-binary ALTSNetwork network algorithm
 * Daniel Huson, 2.2024
 */
public class ALTSNetwork extends Trees2Trees {
	private static boolean warned = false;

	private final BooleanProperty optionUseKernelization = new SimpleBooleanProperty(this, "optionUseKernelization", false);
	private final BooleanProperty optionUseMutualRefinement = new SimpleBooleanProperty(this, "optionUseMutualRefinement", false);

	@Override
	public String getCitation() {
		return "Zhang et al 2023; Louxin Zhang, Niloufar Niloufar Abhari, Caroline Colijn and Yufeng Wu3." +
			   " A fast and scalable method for inferring phylogenetic networks from trees by aligning lineage taxon strings. Genome Res. 2023;" +
			   "Zhang et al, 2024; Louxin Zhang, Banu Cetinkaya and Daniel H Huson. Hybrization networks from multiple trees, in preparation.";
	}

	@Override
	public List<String> listOptions() {
		return List.of(optionUseKernelization.getName(), optionUseMutualRefinement.getName());
	}

	@Override
	public void compute(ProgressListener progress, TaxaBlock taxaBlock, TreesBlock treesBlock, TreesBlock outputBlock) throws IOException {
		if (!warned) {
			NotificationManager.showWarning("This is experimental code, under development");
			warned = true;
		}
		progress.setTasks("Computing hybridization networks", "(Unknown how long this will really take)");
		try (var progressMover = new ProgressMover(progress)) {
			Collection<PhyloTree> result;
			if (!isOptionUseKernelization()) {
				result = AltsNonBinary.apply(treesBlock.getTrees(), progress);
			} else {
				result = Kernelize.apply(progress, taxaBlock, treesBlock.getTrees(), AltsNonBinary::apply, 100000, isOptionUseMutualRefinement());
			}
			for (var tree : result) {
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

	public boolean isOptionUseKernelization() {
		return optionUseKernelization.get();
	}

	public BooleanProperty optionUseKernelizationProperty() {
		return optionUseKernelization;
	}

	public void setOptionUseKernelization(boolean optionUseKernelization) {
		this.optionUseKernelization.set(optionUseKernelization);
	}

	public boolean isOptionUseMutualRefinement() {
		return optionUseMutualRefinement.get();
	}

	public BooleanProperty optionUseMutualRefinementProperty() {
		return optionUseMutualRefinement;
	}

	public void setOptionUseMutualRefinement(boolean optionUseMutualRefinement) {
		this.optionUseMutualRefinement.set(optionUseMutualRefinement);
	}
}
