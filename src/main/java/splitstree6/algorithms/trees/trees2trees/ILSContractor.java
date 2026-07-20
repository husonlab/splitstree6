/*
 * ILSContractor.java Copyright (C) 2026 Daniel H. Huson
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
 *
 */

package splitstree6.algorithms.trees.trees2trees;

import javafx.beans.property.DoubleProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;
import jloda.graph.NodeArray;
import jloda.phylo.PhyloTree;
import jloda.util.progress.ProgressListener;
import splitstree6.algorithms.IFilter;
import splitstree6.data.TaxaBlock;
import splitstree6.data.TreesBlock;

import java.io.IOException;
import java.util.List;

/**
 * contract edges in rooted gene trees whose conflict with the rest of the tree set is
 * explainable by incomplete lineage sorting
 * Daniel Huson, 7.2026
 */
public class ILSContractor extends Trees2Trees implements IFilter {
	private final DoubleProperty optionMinSignalFraction = new SimpleDoubleProperty(this, "optionMinSignalFraction", 0.5);
	private final DoubleProperty optionAlphaResolution = new SimpleDoubleProperty(this, "optionAlphaResolution", 0.05);
	private final DoubleProperty optionAlphaSymmetry = new SimpleDoubleProperty(this, "optionAlphaSymmetry", 0.05);
	private final IntegerProperty optionMinTreesPerTriple = new SimpleIntegerProperty(this, "optionMinTreesPerTriple", 10);
	private final IntegerProperty optionMaxTriplesPerEdge = new SimpleIntegerProperty(this, "optionMaxTriplesPerEdge", 1000);
	private final IntegerProperty optionSeed = new SimpleIntegerProperty(this, "optionSeed", 42);

	@Override
	public List<String> listOptions() {
		return List.of(optionMinSignalFraction.getName(), optionAlphaResolution.getName(), optionAlphaSymmetry.getName(),
				optionMinTreesPerTriple.getName(), optionMaxTriplesPerEdge.getName(), optionSeed.getName());
	}

	@Override
	public String getToolTip(String optionName) {
		return switch (optionName) {
			case "optionMinSignalFraction" ->
					"Contract an edge unless at least this proportion of the rooted triples that it resolves carry signal that ILS alone cannot explain (0: off)";
			case "optionAlphaResolution" ->
					"Significance level for rejecting a species-level polytomy, that is, for concluding that a triple has a real internal branch";
			case "optionAlphaSymmetry" ->
					"Significance level for rejecting equality of the two discordant triple topologies; rejection indicates reticulation rather than ILS, and such a resolution is kept";
			case "optionMinTreesPerTriple" -> "Minimum number of trees that must resolve a triple before it is tested";
			case "optionMaxTriplesPerEdge" -> "Maximum number of triples to sample per edge (0: use all)";
			case "optionSeed" -> "Seed used when sampling triples, for reproducibility";
			default -> optionName;
		};
	}

	@Override
	public String getShortDescription() {
		return "Contract edges in rooted gene trees whose conflict is explainable by incomplete lineage sorting.";
	}

	@Override
	public String getCitation() {
		return "Sayyari and Mirarab 2018;E. Sayyari and S. Mirarab. Testing for polytomies in phylogenetic species trees using quartet frequencies. Genes, 9(3):132, 2018.";
	}

	@Override
	public void compute(ProgressListener progress, TaxaBlock taxaBlock, TreesBlock inputData, TreesBlock outputData) throws IOException {
		var trees = outputData.getTrees();
		trees.clear();
		outputData.setRooted(inputData.isRooted());
		outputData.setPartial(inputData.isPartial());
		outputData.setReticulated(inputData.isReticulated());

		if (!isActive()) {
			trees.addAll(inputData.getTrees());
			return;
		}

		if (!inputData.isRooted())
			throw new IOException("ILSContractor: rooted trees required");
		if (inputData.isReticulated())
			throw new IOException("ILSContractor: trees required, got reticulate networks");
		if (inputData.getNTrees() < getOptionMinTreesPerTriple())
			throw new IOException("ILSContractor: too few trees: " + inputData.getNTrees() + " < " + getOptionMinTreesPerTriple());

		progress.setTasks("ILS contractor", "copying trees");
		for (var tree : inputData.getTrees()) {
			var newTree = new PhyloTree(tree);
			newTree.clearLsaChildrenMap();
			trees.add(newTree);
		}

		var internalEdges = trees.stream()
				.mapToLong(t -> t.nodeStream().filter(v -> !v.isLeaf() && v.getInDegree() > 0).count()).sum();

		var algorithm = new ILSContractorImplementation()
				.setAlphaResolution(getOptionAlphaResolution())
				.setAlphaSymmetry(getOptionAlphaSymmetry())
				.setMinTreesPerTriple(getOptionMinTreesPerTriple())
				.setMaxTriplesPerEdge(getOptionMaxTriplesPerEdge())
				.setMinSignalFraction(getOptionMinSignalFraction())
				.setSeed(getOptionSeed());

		// the expensive part: count all rooted triples over all trees, then score every edge
		progress.setSubtask("scoring edges");
		progress.setMaximum(-1);
		progress.setProgress(-1);
		final List<NodeArray<Double>> scores;
		try {
			scores = algorithm.scoreEdges(trees, taxaBlock.getNtax(), progress);
		} catch (IllegalArgumentException ex) {
			throw new IOException("ILSContractor: " + ex.getMessage());
		}

		progress.setSubtask("contracting edges");
		progress.setMaximum(trees.size());
		progress.setProgress(0);
		var contracted = 0L;
		for (var i = 0; i < trees.size(); i++) {
			contracted += ILSContractorImplementation.contract(trees.get(i), scores.get(i), getOptionMinSignalFraction());
			progress.incrementProgress();
		}
		progress.reportTaskCompleted();

		System.err.printf("ILSContractor: contracted %,d of %,d internal edges (%.1f%%)%n",
				contracted, internalEdges, internalEdges > 0 ? 100.0 * contracted / internalEdges : 0.0);

		if (false) {
			for (var tree : trees) {
				System.err.println(tree.toBracketString(true) + ";");
			}
		}

	}

	@Override
	public boolean isApplicable(TaxaBlock taxaBlock, TreesBlock parent) {
		return parent.getNTrees() > 0 && !parent.isReticulated();
	}

	@Override
	public void clear() {
	}

	@Override
	public boolean isActive() {
		return getOptionMinSignalFraction() > 0;
	}

	public double getOptionMinSignalFraction() {
		return optionMinSignalFraction.get();
	}

	public DoubleProperty optionMinSignalFractionProperty() {
		return optionMinSignalFraction;
	}

	public void setOptionMinSignalFraction(double optionMinSignalFraction) {
		this.optionMinSignalFraction.set(optionMinSignalFraction);
	}

	public double getOptionAlphaResolution() {
		return optionAlphaResolution.get();
	}

	public DoubleProperty optionAlphaResolutionProperty() {
		return optionAlphaResolution;
	}

	public void setOptionAlphaResolution(double optionAlphaResolution) {
		this.optionAlphaResolution.set(optionAlphaResolution);
	}

	public double getOptionAlphaSymmetry() {
		return optionAlphaSymmetry.get();
	}

	public DoubleProperty optionAlphaSymmetryProperty() {
		return optionAlphaSymmetry;
	}

	public void setOptionAlphaSymmetry(double optionAlphaSymmetry) {
		this.optionAlphaSymmetry.set(optionAlphaSymmetry);
	}

	public int getOptionMinTreesPerTriple() {
		return optionMinTreesPerTriple.get();
	}

	public IntegerProperty optionMinTreesPerTripleProperty() {
		return optionMinTreesPerTriple;
	}

	public void setOptionMinTreesPerTriple(int optionMinTreesPerTriple) {
		this.optionMinTreesPerTriple.set(optionMinTreesPerTriple);
	}

	public int getOptionMaxTriplesPerEdge() {
		return optionMaxTriplesPerEdge.get();
	}

	public IntegerProperty optionMaxTriplesPerEdgeProperty() {
		return optionMaxTriplesPerEdge;
	}

	public void setOptionMaxTriplesPerEdge(int optionMaxTriplesPerEdge) {
		this.optionMaxTriplesPerEdge.set(optionMaxTriplesPerEdge);
	}

	public int getOptionSeed() {
		return optionSeed.get();
	}

	public IntegerProperty optionSeedProperty() {
		return optionSeed;
	}

	public void setOptionSeed(int optionSeed) {
		this.optionSeed.set(optionSeed);
	}
}
