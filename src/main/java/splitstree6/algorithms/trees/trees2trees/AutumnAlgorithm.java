/*
 *  AutumnAlgorithm.java Copyright (C) 2024 Daniel H. Huson
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
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleIntegerProperty;
import jloda.phylo.PhyloTree;
import jloda.util.BitSetUtils;
import jloda.util.Single;
import jloda.util.progress.ProgressListener;
import splitstree6.compute.autumn.hybridnetwork.ComputeHybridizationNetwork;
import splitstree6.compute.autumn.hybridnumber.RerootByHybridNumber;
import splitstree6.data.TaxaBlock;
import splitstree6.data.TreesBlock;
import splitstree6.utils.TreesUtils;

import java.io.IOException;
import java.util.List;

/**
 * the autumn algorithm for computing hybridization networks
 */
public class AutumnAlgorithm extends Trees2Trees {

	private final IntegerProperty optionFirstTree = new SimpleIntegerProperty(this, "optionFirstTree", 1);
	private final IntegerProperty optionSecondTree = new SimpleIntegerProperty(this, "optionSecondTree", 2);

	private final BooleanProperty optionOnlyOneNetwork = new SimpleBooleanProperty(this, "optionOnlyOneNetwork");

	private final BooleanProperty optionRerootToMinimize = new SimpleBooleanProperty(this, "optionRerootToMinimize", false);

	@Override
	public List<String> listOptions() {
		return List.of(optionFirstTree.getName(), optionSecondTree.getName(), optionOnlyOneNetwork.getName(),
				optionRerootToMinimize.getName());
	}

	@Override
	public String getToolTip(String optionName) {
		if (!optionName.startsWith("option")) {
			optionName = "option" + optionName;
		}
		return switch (optionName) {
			case "optionOnlyOneNetwork" -> "Report only one network";
			case "optionFirstTree" -> "index of the first tree";
			case "optionSecondTree" -> "index of the second tree";
			case "optionRerootToMinimize" -> "reroot input trees to minimize hybridization number";
			default -> super.getToolTip(optionName);
		};
	}

	@Override
	public String getCitation() {
		return "Huson & Linz 2018; DH Huson and S. Linz. Autumn Algorithm—Computation of Hybridization Networks for Realistic Phylogenetic Trees. "
			   + "IEEE/ACM TCBB: 15:398-420, 2018.";
	}

	@Override
	public String getShortDescription() {
		return "Computes all minimum hybridization networks using the Autumn algorithm";
	}

	@Override
	public boolean isApplicable(TaxaBlock taxa, TreesBlock datablock) {
		return datablock.getNTrees() >= 2 && !datablock.isReticulated();
	}

	@Override
	public void compute(ProgressListener progress, TaxaBlock taxaBlock, TreesBlock treesBlock, TreesBlock outputData) throws IOException {
		TreesUtils.checkTaxonIntersection(treesBlock.getTrees(), 0.25);

		var hybridNumber = new Single<>(0);
		var firstTree = treesBlock.getTree(Math.max(1, Math.min(getOptionFirstTree(), treesBlock.getNTrees())));
		var secondTree = treesBlock.getTree(Math.max(1, Math.min(getOptionSecondTree(), treesBlock.getNTrees())));

		if (isOptionRerootToMinimize()) {
			firstTree = new PhyloTree(firstTree);
			secondTree = new PhyloTree(secondTree);
			RerootByHybridNumber.apply(firstTree, secondTree, progress);
		}
		outputData.getTrees().addAll(ComputeHybridizationNetwork.apply(taxaBlock, firstTree, secondTree, progress, hybridNumber, isOptionOnlyOneNetwork()));
		outputData.setReticulated(hybridNumber.get() > 0);
		var taxa = BitSetUtils.union(BitSetUtils.asBitSet(firstTree.getTaxa()), BitSetUtils.asBitSet(secondTree.getTaxa()));
		outputData.setPartial(!taxa.equals(taxaBlock.getTaxaSet()));
		outputData.setRooted(true);
	}

	public int getOptionFirstTree() {
		return optionFirstTree.get();
	}

	public IntegerProperty optionFirstTreeProperty() {
		return optionFirstTree;
	}

	public void setOptionFirstTree(int optionFirstTree) {
		this.optionFirstTree.set(optionFirstTree);
	}

	public int getOptionSecondTree() {
		return optionSecondTree.get();
	}

	public IntegerProperty optionSecondTreeProperty() {
		return optionSecondTree;
	}

	public void setOptionSecondTree(int optionSecondTree) {
		this.optionSecondTree.set(optionSecondTree);
	}

	public boolean isOptionRerootToMinimize() {
		return optionRerootToMinimize.get();
	}

	public BooleanProperty optionRerootToMinimizeProperty() {
		return optionRerootToMinimize;
	}

	public boolean isOptionOnlyOneNetwork() {
		return optionOnlyOneNetwork.get();
	}

	public BooleanProperty optionOnlyOneNetworkProperty() {
		return optionOnlyOneNetwork;
	}

	public void setOptionOnlyOneNetwork(boolean optionOnlyOneNetwork) {
		this.optionOnlyOneNetwork.set(optionOnlyOneNetwork);
	}
}
