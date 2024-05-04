/*
 *  MaltsNetwork.java Copyright (C) 2024 Daniel H. Huson
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
import jloda.phylo.NewickIO;
import jloda.phylo.PhyloTree;
import jloda.util.StringUtils;
import jloda.util.progress.ProgressListener;
import splitstree6.algorithms.trees.trees2trees.malts.MaltsAlgorithm;
import splitstree6.algorithms.utils.MutualRefinement;
import splitstree6.data.TaxaBlock;
import splitstree6.data.TreesBlock;
import splitstree6.splits.TreesUtils;
import splitstree6.xtra.kernelize.Kernelize;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class MaltsNetwork extends Trees2Trees {
	private final BooleanProperty optionMutualRefinement = new SimpleBooleanProperty(this, "optionMutualRefinement", false);

	private final BooleanProperty optionKernelization = new SimpleBooleanProperty(this, "optionKernelization", false);

	private final BooleanProperty optionRemoveDuplicates = new SimpleBooleanProperty(this, "optionRemoveDuplicates", false);

	private final IntegerProperty optionMaxNetworks = new SimpleIntegerProperty(this, "optionMaxNetworks", 1);

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
	public List<String> listOptions() {
		if (true) // don't need these at present
			return List.of();
		else
			return List.of(optionMutualRefinement.getName(), optionRemoveDuplicates.getName(), optionMaxNetworks.getName(), optionKernelization.getName());
	}

	@Override
	public String getToolTip(String optionName) {
		if (!optionName.startsWith("option")) {
			optionName = "option" + optionName;
		}
		return switch (optionName) {
			case "optionMaxNetworks" -> "maximum number of networks to report";
			case "optionMutualRefinement" -> "mutually refine trees during preprocessing";
			case "optionKernelization" -> "perform kernelization during preprocessing";
			case "optionRemoveDuplicates" -> "remove duplicate networks in output";
			default -> super.getToolTip(optionName);
		};
	}

	/**
	 * run the MALTS algorithm
	 */
	@Override
	public void compute(ProgressListener progress, TaxaBlock taxaBlock, TreesBlock treesBlock, TreesBlock outputBlock) throws IOException {
		progress.setTasks("Computing hybridization result", "(Unknown how long this will really take)");

		Collection<PhyloTree> inputTrees;
		if (isOptionMutualRefinement()) {
			inputTrees = MutualRefinement.apply(treesBlock.getTrees(), MutualRefinement.Strategy.All, true);
			if (false)
				System.err.println("Refined:\n" + NewickIO.toString(inputTrees, false));
		} else {
			inputTrees = new ArrayList<>();
			for (var tree : treesBlock.getTrees()) {
				inputTrees.add(new PhyloTree(tree));
			}
		}
		Collection<PhyloTree> result;
		if (inputTrees.size() <= 1) {
			result = inputTrees;
		} else if (!isOptionKernelization()) {
			result = MaltsAlgorithm.apply(inputTrees, progress, getOptionMaxNetworks());
		} else { // kernelization is broken
			result = Kernelize.apply(progress, taxaBlock, inputTrees, MaltsAlgorithm::apply, getOptionMaxNetworks());
		}

		// todo: use the input weights...
		for (var network : result) {
			for (var e : network.edges()) {
				if (e.getTarget().getInDegree() > 1) {
					network.setReticulate(e, true);
					network.setWeight(e, 0.1);
				} else {
					network.setReticulate(e, false);
					network.setWeight(e, 1.0);
				}
			}
		}

		outputBlock.setPartial(false);
		outputBlock.setRooted(true);

		var count = 0;
		for (var network : result) {
			network.setName("N" + (++count));
			TreesUtils.addLabels(network, taxaBlock::getLabel);
			outputBlock.getTrees().add(network);
			if (network.nodeStream().anyMatch(v -> v.getInDegree() > 1)) {
				outputBlock.setReticulated(true);
			}
			if (false) { // this takes too long when number of reticulations is large
				var networkClusters = TreesUtils.collectAllSoftwiredClusters(network);
				for (var t = 1; t <= treesBlock.getNTrees(); t++) {
					var tree = treesBlock.getTree(t);
					if (!networkClusters.containsAll(TreesUtils.collectAllHardwiredClusters(tree))) {
						System.err.println("ERROR: Network does not contain tree: " + t);
						for (var cluster : TreesUtils.collectAllHardwiredClusters(tree)) {
							if (!networkClusters.contains(cluster))
								System.err.println("ERROR: missing cluster: " + StringUtils.toString(cluster));
						}
					}
				}
			}
		}
	}

	public boolean isOptionMutualRefinement() {
		return optionMutualRefinement.get();
	}

	public BooleanProperty optionMutualRefinementProperty() {
		return optionMutualRefinement;
	}

	public void setOptionMutualRefinement(boolean optionMutualRefinement) {
		this.optionMutualRefinement.set(optionMutualRefinement);
	}

	public boolean isOptionKernelization() {
		return optionKernelization.get();
	}

	public BooleanProperty optionKernelizationProperty() {
		return optionKernelization;
	}

	public void setOptionKernelization(boolean optionKernelization) {
		this.optionKernelization.set(optionKernelization);
	}

	public boolean isOptionRemoveDuplicates() {
		return optionRemoveDuplicates.get();
	}

	public BooleanProperty optionRemoveDuplicatesProperty() {
		return optionRemoveDuplicates;
	}

	public void setOptionRemoveDuplicates(boolean optionRemoveDuplicates) {
		this.optionRemoveDuplicates.set(optionRemoveDuplicates);
	}

	public int getOptionMaxNetworks() {
		return optionMaxNetworks.get();
	}

	public IntegerProperty optionMaxNetworksProperty() {
		return optionMaxNetworks;
	}

	public void setOptionMaxNetworks(int optionMaxNetworks) {
		this.optionMaxNetworks.set(optionMaxNetworks);
	}

	@Override
	public boolean isApplicable(TaxaBlock taxa, TreesBlock datablock) {
		return !datablock.isReticulated() && datablock.getNTrees() > 1;
	}
}
