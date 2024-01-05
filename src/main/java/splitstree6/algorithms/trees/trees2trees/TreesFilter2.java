/*
 * TreesFilter2.java Copyright (C) 2024 Daniel H. Huson
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

import javafx.beans.property.*;
import jloda.graph.Edge;
import jloda.phylo.PhyloTree;
import jloda.phylo.algorithms.RootedNetworkProperties;
import jloda.util.progress.ProgressListener;
import splitstree6.algorithms.IFilter;
import splitstree6.data.TaxaBlock;
import splitstree6.data.TreesBlock;

import java.util.List;
import java.util.stream.Collectors;

/**
 * additional tree filtering
 * Daniel Huson, 1/2019
 */
public class TreesFilter2 extends Trees2Trees implements IFilter {
	private final BooleanProperty optionRequireAllTaxa = new SimpleBooleanProperty(this, "optionRequireAllTaxa", false);
	private final IntegerProperty optionMinNumberOfTaxa = new SimpleIntegerProperty(this, "optionMinNumberOfTaxa", 1);
	private final DoubleProperty optionMinTotalTreeLength = new SimpleDoubleProperty(this, "optionMinNumberOfTaxa", 0);
	private final DoubleProperty optionMinEdgeLength = new SimpleDoubleProperty(this, "optionMinEdgeLength", 0);
	private final DoubleProperty optionMinConfidence = new SimpleDoubleProperty(this, "optionMinConfidence", 0);
	private final BooleanProperty optionUniformEdgeLengths = new SimpleBooleanProperty(this, "optionUniformEdgeLengths", false);


	public List<String> listOptions() {
		return List.of(optionRequireAllTaxa.getName(), optionMinNumberOfTaxa.getName(), optionMinTotalTreeLength.getName(), optionMinEdgeLength.getName(), optionMinConfidence.getName(), optionUniformEdgeLengths.getName());
	}

	@Override
	public String getToolTip(String optionName) {
		return switch (optionName) {
			case "optionRequireAllTaxa" -> "Keep only trees that have the full set of taxa";
			case "optionMinNumberOfTaxa" -> "Keep only trees that have at least this number of taxa";
			case "optionMinTotalTreeLength" -> "Keep only trees that have at least this total length";
			case "optionMinEdgeLength" -> "Keep only edges that have this minimum length";
			case "optionMinConfidence" -> "Keep only edges that have this minimum confidence value";
			case "optionUniformEdgeLengths" -> "Change all edge weights to 1";
			default -> optionName;
		};
	}

	@Override
	public void compute(ProgressListener progress, TaxaBlock taxaBlock, TreesBlock parent, TreesBlock child) {
		if (!isActive()) {
			child.getTrees().addAll(parent.getTrees());
			child.setPartial(parent.isPartial());
		} else {
			var isPartial = false;
			for (var t = 1; t <= parent.getNTrees(); t++) {
				PhyloTree tree = parent.getTree(t);

				if (isOptionRequireAllTaxa() && tree.getNumberOfTaxa() < taxaBlock.getNtax())
					continue;
				if (tree.getNumberOfTaxa() < getOptionMinNumberOfTaxa())
					continue;
				if (getOptionMinTotalTreeLength() > 0) {
					final var treeLength = tree.computeTotalWeight();
					if (treeLength < getOptionMinTotalTreeLength())
						continue;
				}
				var isCopy = false;
				if (getOptionMinEdgeLength() > 0 || getOptionMinConfidence() > 0) {
					tree = new PhyloTree(tree);
					if (contractShortOLowConfidenceEdgesKeepLeafEdges(tree, getOptionMinEdgeLength(), getOptionMinConfidence()))
						isCopy = true;
					else
						tree = parent.getTree(t); // nothing changed, use original
				}
				if (isOptionUniformEdgeLengths()) {
					if (!isCopy)
						tree = new PhyloTree(tree);

					if (!makeEdgesUnitWeight(tree))
						tree = parent.getTree(t); // nothing changed, use original
				}
				child.getTrees().add(tree);
				if (!isPartial && tree.getNumberOfTaxa() < taxaBlock.getNtax()) {
					isPartial = true;
				}
			}
			child.setPartial(isPartial);
		}

		if (false) {
			// see whether length and support may have been confused, and if so, correct:
			for (var tree : child.getTrees()) {
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
		child.setRooted(parent.isRooted());

		if (child.getNTrees() == parent.getNTrees())
			setShortDescription("using all " + parent.size() + " trees");
		else
			setShortDescription("using " + child.size() + " of " + parent.size() + " trees");
	}

	@Override
	public void clear() {
	}

	@Override
	public boolean isActive() {
		return isOptionRequireAllTaxa() || getOptionMinNumberOfTaxa() > 1 || getOptionMinEdgeLength() > 0 || getOptionMinConfidence() > 0 || isOptionUniformEdgeLengths();
	}

	public boolean isOptionRequireAllTaxa() {
		return optionRequireAllTaxa.get();
	}

	public BooleanProperty optionRequireAllTaxaProperty() {
		return optionRequireAllTaxa;
	}

	public void setOptionRequireAllTaxa(boolean optionRequireAllTaxa) {
		this.optionRequireAllTaxa.set(optionRequireAllTaxa);
	}

	public int getOptionMinNumberOfTaxa() {
		return optionMinNumberOfTaxa.get();
	}

	public IntegerProperty optionMinNumberOfTaxaProperty() {
		return optionMinNumberOfTaxa;
	}

	public void setOptionMinNumberOfTaxa(int optionMinNumberOfTaxa) {
		this.optionMinNumberOfTaxa.set(optionMinNumberOfTaxa);
	}

	public double getOptionMinTotalTreeLength() {
		return optionMinTotalTreeLength.get();
	}

	public DoubleProperty optionMinTotalTreeLengthProperty() {
		return optionMinTotalTreeLength;
	}

	public void setOptionMinTotalTreeLength(double optionMinTotalTreeLength) {
		this.optionMinTotalTreeLength.set(optionMinTotalTreeLength);
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
