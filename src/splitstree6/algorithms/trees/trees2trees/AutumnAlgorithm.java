/*
 * AutumnAlgorithm.java Copyright (C) 2022 Daniel H. Huson
 *
 * (Some files contain contributions from other authors, who are then mentioned separately.)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package splitstree6.algorithms.trees.trees2trees;

import jloda.util.BitSetUtils;
import jloda.util.Single;
import jloda.util.progress.ProgressListener;
import splitstree6.autumn.hybridnetwork.ComputeHybridizationNetwork;
import splitstree6.data.TaxaBlock;
import splitstree6.data.TreesBlock;

import java.io.IOException;

/**
 * the autumn algorithm for computing hybridization networks
 */
public class AutumnAlgorithm extends Trees2Trees {

	@Override
	public String getCitation() {
		return "Huson, and Linz 2018; D.H. Huson an S. Linz. Autumn Algorithm—Computation of Hybridization Networks for Realistic Phylogenetic Trees. "
			   + "IEEE/ACM Transactions on Computational Biology and Bioinformatics: 15:398-420, 2018.";
	}

	@Override
	public boolean isApplicable(TaxaBlock taxa, TreesBlock datablock) {
		return datablock.getNTrees() == 2 && datablock.isRooted() && !datablock.isReticulated();
	}

	@Override
	public void compute(ProgressListener progress, TaxaBlock taxaBlock, TreesBlock inputData, TreesBlock outputData) throws IOException {
		var hybridNumber = new Single<>(0);
		outputData.getTrees().addAll(ComputeHybridizationNetwork.apply(taxaBlock, inputData.getTree(1), inputData.getTree(2), progress, hybridNumber));
		outputData.setReticulated(hybridNumber.get() > 0);
		var taxa = BitSetUtils.union(BitSetUtils.asBitSet(inputData.getTree(1).getTaxa()), BitSetUtils.asBitSet(inputData.getTree(2).getTaxa()));
		outputData.setPartial(!taxa.equals(taxaBlock.getTaxaSet()));
		outputData.setRooted(true);
	}
}
