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
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import jloda.fx.util.ProgramProperties;
import jloda.phylo.NewickIO;
import jloda.phylo.PhyloTree;
import jloda.util.progress.ProgressListener;
import splitstree6.algorithms.utils.MutualRefinement;
import splitstree6.compute.phylofusion.NetworkUtils;
import splitstree6.compute.phylofusion.PhyloFusionAlgorithm;
import splitstree6.data.TaxaBlock;
import splitstree6.data.TreesBlock;
import splitstree6.utils.PathMultiplicityDistance;
import splitstree6.utils.TreesUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class PhyloFusion extends Trees2Trees {
	public enum Search {Thorough, Medium, Fast}
	private final BooleanProperty optionMutualRefinement = new SimpleBooleanProperty(this, "optionMutualRefinement", true);

	private final BooleanProperty optionNormalizeEdgeWeights = new SimpleBooleanProperty(this, "optionNormalizeEdgeWeights", true);

	private final BooleanProperty optionCalculateWeights = new SimpleBooleanProperty(this, "optionCalculateWeights", true);

	private final ObjectProperty<Search> optionSearchHeuristic = new SimpleObjectProperty<>(this, "optionSearchHeuristic");

	{
		ProgramProperties.track(optionMutualRefinement, true);
		ProgramProperties.track(optionSearchHeuristic, Search::valueOf, Search.Thorough);
	}

	@Override
	public String getCitation() {
		return "Zhang et al 2023; L. Zhang, N. Abhari, C. Colijn and Y Wu." +
			   " A fast and scalable method for inferring phylogenetic networks from trees by aligning lineage taxon strings. Genome Res. 2023.;"
			   + "Zhang et al 2024; L. Zhang, B. Cetinkaya and D.H. Huson. PhyloFusion- Fast and easy fusion of rooted phylogenetic trees into a network, in preparation.";
	}

	@Override
	public String getShortDescription() {
		return "Combines multiple rooted phylogenetic trees into a rooted network.";
	}

	@Override
	public List<String> listOptions() {
		return List.of(optionMutualRefinement.getName(), optionNormalizeEdgeWeights.getName(), optionSearchHeuristic.getName()); //, optionCalculateWeights.getName());
	}

	@Override
	public String getToolTip(String optionName) {
		if (!optionName.startsWith("option")) {
			optionName = "option" + optionName;
		}
		return switch (optionName) {
			case "optionSearch" ->
					"Fast, Medium or Thorough search: 10, 150 or 300 random orderings per taxon, respectively";
			case "optionCalculateWeights" -> "Calculate edge weights using brute-force algorithm";
			case "optionMutualRefinement" -> "mutually refine input trees";
			case "optionNormalizeEdgeWeights" -> "normalize input edge weights";
			default -> super.getToolTip(optionName);
		};
	}

	/**
	 * run the algorithm
	 */
	@Override
	public void compute(ProgressListener progress, TaxaBlock taxaBlock, TreesBlock treesBlock, TreesBlock outputBlock) throws IOException {
		progress.setTasks("PhyloFusion", "init");

			TreesUtils.checkTaxonIntersection(treesBlock.getTrees(), 0.25);
			var inputTrees = new ArrayList<>(treesBlock.getTrees().stream().map(PhyloTree::new).toList());

			if (isOptionMutualRefinement()) {
				var refined = MutualRefinement.apply(inputTrees, MutualRefinement.Strategy.All, false);
				inputTrees.clear();
				inputTrees.addAll(refined);
				if (false)
					System.err.println("Refined:\n" + NewickIO.toString(inputTrees, false));
			}
			List<PhyloTree> result;
			if (inputTrees.size() <= 1) {
				result = inputTrees;
			} else {
				var ntax = taxaBlock.getNtax();
				var numberOfRandomOrderings = computeNumberOfRandomOrderings(ntax, getOptionSearchHeuristic());
				result = PhyloFusionAlgorithm.apply(numberOfRandomOrderings, inputTrees, progress);
			}

			for (var network : result) {
				for (var e : network.edges()) {
					network.setReticulate(e, e.getTarget().getInDegree() > 1);
				}
				if (isOptionCalculateWeights())
					if (!NetworkUtils.setEdgeWeights(inputTrees, network, isOptionNormalizeEdgeWeights(), 1500))
						break;
			}

			outputBlock.setPartial(false);
			outputBlock.setRooted(true);

			var count = 0;
			for (var network : result) {
				network.setName("N" + (++count));
				TreesUtils.addLabels(network, taxaBlock::getLabel);
				outputBlock.getTrees().add(network);
				if (!outputBlock.isReticulated() && network.nodeStream().anyMatch(v -> v.getInDegree() > 1)) {
					outputBlock.setReticulated(true);
				}
				NetworkUtils.check(network);
				if (true) {
					for (var t = 1; t <= treesBlock.getNTrees(); t++) {
						var tree = treesBlock.getTree(t);
						if (!PathMultiplicityDistance.contains(taxaBlock.getTaxaSet(), network, tree)) {
							System.err.println("Internal error: Network does not appear to contain tree: " + t);
						}
					}
				}
			}
	}

	private long computeNumberOfRandomOrderings(int ntax, Search optionSearch) {
		return switch (optionSearch) {
			case Fast -> Math.max(100, 10L * ntax);
			case Medium -> 150L * ntax;
			case Thorough -> 300L * ntax;
		};
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

	public boolean isOptionNormalizeEdgeWeights() {
		return optionNormalizeEdgeWeights.get();
	}

	public BooleanProperty optionNormalizeEdgeWeightsProperty() {
		return optionNormalizeEdgeWeights;
	}

	public void setOptionNormalizeEdgeWeights(boolean optionNormalizeEdgeWeights) {
		this.optionNormalizeEdgeWeights.set(optionNormalizeEdgeWeights);
	}

	@Override
	public boolean isApplicable(TaxaBlock taxa, TreesBlock datablock) {
		return !datablock.isReticulated() && datablock.getNTrees() > 1;
	}

	public boolean isOptionCalculateWeights() {
		return optionCalculateWeights.get();
	}

	public BooleanProperty optionCalculateWeightsProperty() {
		return optionCalculateWeights;
	}

	public void setOptionCalculateWeights(boolean optionCalculateWeights) {
		this.optionCalculateWeights.set(optionCalculateWeights);
	}

	public void setOptionSearchHeuristic(Search optionSearchHeuristic) {
		this.optionSearchHeuristic.set(optionSearchHeuristic);
	}

	public Search getOptionSearchHeuristic() {
		return optionSearchHeuristic.get();
	}

	public ObjectProperty<Search> optionSearchHeuristicProperty() {
		return optionSearchHeuristic;
	}
}
