/*
 *  TreesFilterMore.java Copyright (C) 2024 Daniel H. Huson
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
import jloda.graph.Edge;
import jloda.phylo.PhyloTree;
import jloda.phylo.algorithms.RootedNetworkProperties;
import jloda.util.ExecuteInParallel;
import jloda.util.ProgramExecutorService;
import jloda.util.progress.ProgressListener;
import splitstree6.algorithms.IFilter;
import splitstree6.data.TaxaBlock;
import splitstree6.data.TreesBlock;

import java.util.List;
import java.util.stream.Collectors;

/**
 * methods for contracting edges in trees
 * Daniel Huson, 3.2024
 */
public class TreesEdgesFilter extends Trees2Trees implements IFilter {
	private final DoubleProperty optionMinEdgeLength = new SimpleDoubleProperty(this, "optionMinEdgeLength", 0);
	private final DoubleProperty optionMinConfidence = new SimpleDoubleProperty(this, "optionMinConfidence", 0);
	private final BooleanProperty optionUniformEdgeLengths = new SimpleBooleanProperty(this, "optionUniformEdgeLengths", false);

	private final BooleanProperty optionRescale = new SimpleBooleanProperty(this, "optionRescale", false);


	public List<String> listOptions() {
		return List.of(optionMinConfidence.getName(), optionMinEdgeLength.getName(), optionUniformEdgeLengths.getName(), optionRescale.getName());
	}

	@Override
	public String getToolTip(String optionName) {
		if (!optionName.startsWith("option"))
			optionName = "option" + optionName;
		return switch (optionName) {
			case "optionMinEdgeLength" -> "Keep only edges that have this minimum length";
			case "optionMinConfidence" -> "Keep only edges that have this minimum confidence value";
			case "optionUniformEdgeLengths" -> "Change all edge weights to 1";
			case "optionRescale" -> "rescale each tree to total length of 100";

			default -> optionName;
		};
	}

	@Override
	public String getShortDescription() {
		return "Provides several options for filtering trees.";
	}

	@Override
	public void compute(ProgressListener progress, TaxaBlock taxaBlock, TreesBlock inputData, TreesBlock outputData) {
		if (!isActive()) {
			outputData.getTrees().addAll(inputData.getTrees());
			outputData.setPartial(inputData.isPartial());
		} else {
			var trees = outputData.getTrees();
			trees.clear();
			for (var tree : inputData.getTrees()) {
				trees.add(new PhyloTree(tree));
			}
			outputData.setRooted(inputData.isRooted());
			outputData.setPartial(inputData.isPartial());
			outputData.setReticulated(inputData.isReticulated());

			try {
				ExecuteInParallel.apply(trees, tree -> {
					if (getOptionMinEdgeLength() > 0 || getOptionMinConfidence() > 0) {
						contractShortOLowConfidenceEdgesKeepLeafEdges(tree, getOptionMinEdgeLength(), getOptionMinConfidence());
					}
					if (isOptionUniformEdgeLengths()) {
						makeEdgesUnitWeight(tree);
					}
					if (isOptionRescale()) {
						var totalWeight = tree.edgeStream().mapToDouble(tree::getWeight).filter(w -> w > 0).sum();
						if (totalWeight > 0) {
							var factor = 100.0 / totalWeight;
							for (var e : tree.edges()) {
								tree.setWeight(e, Math.max(0.0, factor * tree.getWeight(e)));
							}
						}
					}
				}, ProgramExecutorService.getNumberOfCoresToUse());
			} catch (Exception ignored) {
			}
		}


		if (false) {
			// see whether length and support may have been confused, and if so, correct:
			for (var tree : outputData.getTrees()) {
				var avWeight = tree.edgeStream().filter(e -> !e.getTarget().isLeaf()).mapToDouble(tree::getWeight).average().orElse(0);
				var maxWeight = tree.edgeStream().filter(e -> !e.getTarget().isLeaf()).mapToDouble(tree::getWeight).max().orElse(0);
				var avConfidence = tree.edgeStream().filter(e -> !e.getTarget().isLeaf()).mapToDouble(tree::getConfidence).average().orElse(0);
				var maxConfidence = tree.edgeStream().filter(e -> !e.getTarget().isLeaf()).mapToDouble(tree::getConfidence).max().orElse(0);

				if (maxConfidence < avWeight && avWeight <= 100) {
					System.err.println("Fixing tree: " + tree.getName());
					tree.edgeStream().filter(e -> !e.getTarget().isLeaf()).forEach(e -> {
						var tmp = tree.getWeight(e);
						tree.setWeight(e, tree.getConfidence(e));
						tree.setConfidence(e, tmp);
					});
				}
			}
		}
		outputData.setRooted(inputData.isRooted());

		if (outputData.getNTrees() == inputData.getNTrees())
			setShortDescription("using all " + inputData.size() + " trees");
		else
			setShortDescription("using " + outputData.size() + " of " + inputData.size() + " trees");
	}

	@Override
	public void clear() {
	}

	@Override
	public boolean isActive() {
		return getOptionMinEdgeLength() > 0 || getOptionMinConfidence() > 0 || isOptionUniformEdgeLengths() || isOptionRescale();
	}

	public double getOptionMinEdgeLength() {
		return optionMinEdgeLength.get();
	}

	public DoubleProperty optionMinEdgeLengthProperty() {
		return optionMinEdgeLength;
	}

	public void setOptionMinEdgeLength(double optionMinEdgeLength) {
		this.optionMinEdgeLength.set(optionMinEdgeLength);
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

	public boolean isOptionUniformEdgeLengths() {
		return optionUniformEdgeLengths.get();
	}

	public BooleanProperty optionUniformEdgeLengthsProperty() {
		return optionUniformEdgeLengths;
	}

	public void setOptionUniformEdgeLengths(boolean optionUniformEdgeLengths) {
		this.optionUniformEdgeLengths.set(optionUniformEdgeLengths);
	}

	public boolean isOptionRescale() {
		return optionRescale.get();
	}

	public BooleanProperty optionRescaleProperty() {
		return optionRescale;
	}

	public void setOptionRescale(boolean optionRescale) {
		this.optionRescale.set(optionRescale);
	}

	/**
	 * give all adjacentEdges unit weight
	 */
	public static boolean makeEdgesUnitWeight(PhyloTree tree) {
		boolean changed = false;
		for (Edge e : tree.edges()) {
			if (tree.getWeight(e) != 1) {
				tree.setWeight(e, 1.0);
				changed = true;
			}
		}
		return changed;
	}

	/**
	 * contracts all edges below min length
	 *
	 * @return true, if anything contracted
	 */
	public static boolean contractShortOLowConfidenceEdgesKeepLeafEdges(PhyloTree tree, double minLength, double minConfidence) {
		return RootedNetworkProperties.contractEdges(tree, tree.edgeStream().filter(e -> !e.getTarget().isLeaf() && (tree.getWeight(e) < minLength || tree.getConfidence(e) < minConfidence)).collect(Collectors.toSet()), null);
	}


}
