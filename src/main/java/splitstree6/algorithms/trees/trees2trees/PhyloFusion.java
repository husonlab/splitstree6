/*
 *  PhyloFusion.java Copyright (C) 2024 Daniel H. Huson
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
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import jloda.fx.util.ProgramProperties;
import jloda.phylo.NewickIO;
import jloda.phylo.PhyloTree;
import jloda.util.StringUtils;
import jloda.util.progress.ProgressListener;
import splitstree6.algorithms.utils.MutualRefinement;
import splitstree6.compute.treenetmerge.NetworkUtils;
import splitstree6.compute.treenetmerge.PhyloFusionAlgorithm;
import splitstree6.data.TaxaBlock;
import splitstree6.data.TreesBlock;
import splitstree6.splits.TreesUtils;
import splitstree6.xtra.kernelize.Kernelize;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class PhyloFusion extends Trees2Trees {
	private final DoubleProperty optionMinConfidence = new SimpleDoubleProperty(this, "optionMinConfidence", 70.0);

	private final BooleanProperty optionMutualRefinement = new SimpleBooleanProperty(this, "optionMutualRefinement", true);

	private final BooleanProperty optionKernelization = new SimpleBooleanProperty(this, "optionKernelization", false);

	{
		ProgramProperties.track(optionMinConfidence, 70.0);
		ProgramProperties.track(optionMutualRefinement, true);
	}

	@Override
	public String getCitation() {
		return "Zhang et al 2023; L. Zhang, N. Abhari, C. Colijn and Y Wu." +
			   " A fast and scalable method for inferring phylogenetic networks from trees by aligning lineage taxon strings. Genome Res. 2023.;"
			   + "Zhang et al 2024; L. Zhang, B. Cetinkaya and D.H. Huson. PhyloFusion- Fast and easy fusion of rooted phylogenetic trees into a network, in preparation.";
	}

	@Override
	public String getShortDescription() {
		return "Combines multiple rooted phylogenetic trees into a rooted netwok.";
	}

	@Override
	public List<String> listOptions() {
		return List.of(optionMinConfidence.getName(), optionMutualRefinement.getName());//, optionKernelization.getName());
	}

	@Override
	public String getToolTip(String optionName) {
		if (!optionName.startsWith("option")) {
			optionName = "option" + optionName;
		}
		return switch (optionName) {
			case "optionMinConfidence" -> "minimum input tree-edge confidence";
			case "optionMutualRefinement" -> "mutually refine input trees";
			case "optionKernelization" -> "uses kernelization";
			default -> super.getToolTip(optionName);
		};
	}

	/**
	 * run the algorithm
	 */
	@Override
	public void compute(ProgressListener progress, TaxaBlock taxaBlock, TreesBlock treesBlock, TreesBlock outputBlock) throws IOException {
		progress.setTasks("Computing network", "(Unknown how long this will really take)");

		var inputTrees = new ArrayList<>(treesBlock.getTrees().stream().map(PhyloTree::new).toList());

		if (getOptionMinConfidence() > 0) {
			for (var tree : inputTrees) {
				TreesEdgesFilter.contractShortOLowConfidenceEdgesKeepLeafEdges(tree, 0, getOptionMinConfidence());
			}
		}
		if (isOptionMutualRefinement()) {
			var refined = MutualRefinement.apply(inputTrees, MutualRefinement.Strategy.All, false);
			inputTrees.clear();
			inputTrees.addAll(refined);
			if (false)
				System.err.println("Refined:\n" + NewickIO.toString(inputTrees, false));
		}
		Collection<PhyloTree> result;
		if (inputTrees.size() <= 1) {
			result = inputTrees;
		} else if (!isOptionKernelization()) {
			result = PhyloFusionAlgorithm.apply(inputTrees, progress);
		} else { // kernelization is broken
			result = Kernelize.apply(progress, taxaBlock, inputTrees, PhyloFusionAlgorithm::apply, Integer.MAX_VALUE);
		}

		for (var network : result) {
			for (var e : network.edges()) {
				network.setReticulate(e, e.getTarget().getInDegree() > 1);
			}
			NetworkUtils.setEdgeWeights(inputTrees, network, 1000);
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

	public double getOptionMinConfidence() {
		return optionMinConfidence.get();
	}

	public DoubleProperty optionMinConfidenceProperty() {
		return optionMinConfidence;
	}

	public void setOptionMinConfidence(double optionMinConfidence) {
		this.optionMinConfidence.set(optionMinConfidence);
	}

	@Override
	public boolean isApplicable(TaxaBlock taxa, TreesBlock datablock) {
		return !datablock.isReticulated() && datablock.getNTrees() > 1;
	}
}
