/*
 * SplitsFilter.java Copyright (C) 2024 Daniel H. Huson
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

package splitstree6.algorithms.splits.splits2splits;

import javafx.beans.property.*;
import jloda.graph.Graph;
import jloda.graph.NodeArray;
import jloda.util.progress.ProgressListener;
import splitstree6.algorithms.IFilter;
import splitstree6.algorithms.utils.GreedyCircular;
import splitstree6.algorithms.utils.GreedyCompatible;
import splitstree6.algorithms.utils.GreedyWeaklyCompatible;
import splitstree6.algorithms.utils.SplitsBlockUtilities;
import splitstree6.data.SplitsBlock;
import splitstree6.data.TaxaBlock;
import splitstree6.splits.ASplit;
import splitstree6.splits.Compatibility;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static splitstree6.splits.SplitUtils.buildIncompatibilityGraph;

/**
 * splits filter
 * Daniel Huson 12/2016
 */
public class SplitsFilter extends Splits2Splits implements IFilter {
	public enum FilterAlgorithm {None, /*ClosestTree,*/ GreedyCompatible, GreedyCircular, GreedyWeaklyCompatible, BlobTree}

	private final ObjectProperty<FilterAlgorithm> optionFilterAlgorithm = new SimpleObjectProperty<>(this, "optionFilterAlgorithm", FilterAlgorithm.None);

	private final FloatProperty optionWeightThreshold = new SimpleFloatProperty(this, "optionWeightThreshold", 0);
	private final FloatProperty optionConfidenceThreshold = new SimpleFloatProperty(this, "optionConfidenceThreshold", 0);
	private final IntegerProperty optionMaximumDimension = new SimpleIntegerProperty(this, "optionMaximumDimension", 4);

	private final BooleanProperty optionRecomputeCycle = new SimpleBooleanProperty(this, "optionRecomputeCycle", false);

	private boolean active = false;

	public List<String> listOptions() {
		return List.of(optionWeightThreshold.getName(), optionConfidenceThreshold.getName(), optionMaximumDimension.getName(), optionFilterAlgorithm.getName(), optionRecomputeCycle.getName());
	}

	public String getShortDescription() {
		return "Provides several ways of filtering splits.";
	}

	@Override
	public String getToolTip(String optionName) {
		return switch (optionName) {
			case "optionFilterAlgorithm" -> "Set the filter algorithm";
			case "optionWeightThreshold" -> "Set minimum split weight threshold";
			case "optionConfidenceThreshold" -> "Set the minimum split confidence threshold";
			case "optionMaximumDimension" ->
					"Set maximum dimension threshold (necessary to avoid computational overload)";
			case "optionRecomputeCycle" -> "Recompute circular ordering";
			default -> optionName;
		};
	}

	/**
	 * do the computation
	 */
	public void compute(ProgressListener progress, TaxaBlock taxaBlock, SplitsBlock parent, SplitsBlock child) throws IOException {
		active = false;

		final var split2label = new HashMap<ASplit, String>();
		for (var s = 1; s <= parent.getSplitLabels().size(); s++) {
			split2label.put(parent.get(s), parent.getSplitLabels().get(s));
		}

		var compatibility = Compatibility.unknown;

		final var splits = switch (getOptionFilterAlgorithm()) {
			case GreedyCompatible -> {
				compatibility = Compatibility.compatible;
				yield GreedyCompatible.apply(progress, parent.getSplits(), ASplit::getWeight);
			}
			/*
			case ClosestTree -> {
				compatibility = Compatibility.compatible;
				yield ClosestTree.apply(progress, taxaBlock.getNtax(), parent.getSplits(), parent.getCycle());
			}
			 */
			case GreedyWeaklyCompatible ->
					GreedyWeaklyCompatible.apply(progress, parent.getSplits(), ASplit::getWeight);
			case GreedyCircular ->
					GreedyCircular.apply(progress, taxaBlock.getTaxaSet(), parent.getSplits(), ASplit::getWeight);
			case BlobTree -> {
				compatibility = Compatibility.compatible;
				var result = new ArrayList<ASplit>();
				var graph = new Graph();
				try (NodeArray<ASplit> nodeSplitMap = buildIncompatibilityGraph(parent.getSplits(), graph)) {
					for (var v : graph.nodes()) {
						if (v.getDegree() == 0) {// compatible with all
							result.add(nodeSplitMap.get(v));
						}
					}
				}
				yield result;
			}
			case None -> new ArrayList<>(parent.getSplits());
		};

		if (getOptionWeightThreshold() > 0) {
			var filtered = splits.stream().filter(s -> s.getWeight() >= getOptionWeightThreshold()).toList();
			splits.clear();
			splits.addAll(filtered);
		}

		if (getOptionConfidenceThreshold() > 0) {
			var filtered = splits.stream().filter(s -> s.getConfidence() >= getOptionConfidenceThreshold()).toList();
			splits.clear();
			splits.addAll(filtered);
		}

		if (getOptionMaximumDimension() > 0 && getOptionFilterAlgorithm() == FilterAlgorithm.GreedyCompatible && parent.getCompatibility() != Compatibility.compatible && parent.getCompatibility() != Compatibility.cyclic && parent.getCompatibility() != Compatibility.weaklyCompatible) {
			var filtered = new ArrayList<ASplit>();
			DimensionFilter.apply(progress, getOptionMaximumDimension(), splits, filtered);
		}

		child.getSplits().addAll(splits);
		if (!split2label.isEmpty()) {
			for (var s = 1; s <= child.getNsplits(); s++) {
				var label = split2label.get(child.get(s));
				child.getSplitLabels().put(s, label);
			}
		}

		if (splits.size() != parent.getSplits().size())
			active = true;

		if (!active) {
			child.setCycle(parent.getCycle());
			child.setFit(parent.getFit());
			child.setCompatibility(parent.getCompatibility());
			child.setThreshold(parent.getThreshold());
			setShortDescription("using all " + parent.getNsplits() + " splits");
		} else {
			if (getOptionRecomputeCycle()) {
				child.setCycle(SplitsBlockUtilities.computeCycle(taxaBlock.getNtax(), child.getSplits()));
				compatibility = Compatibility.unknown;
			}
			else
				child.setCycle(parent.getCycle());

			child.setFit(-1);
			if (compatibility == Compatibility.unknown)
				compatibility = Compatibility.compute(taxaBlock.getNtax(), child.getSplits(), child.getCycle());
			child.setCompatibility(compatibility);
			child.setThreshold(parent.getThreshold());
			setShortDescription("using " + child.getNsplits() + " of " + parent.getNsplits() + " splits");
		}
	}

	@Override
	public void clear() {
		super.clear();
	}

	@Override
	public boolean isApplicable(TaxaBlock taxaBlock, SplitsBlock parent) {
		return !parent.isPartial();
	}

	@Override
	public boolean isActive() {
		return active;
	}

	public FilterAlgorithm getOptionFilterAlgorithm() {
		return optionFilterAlgorithm.get();
	}

	public ObjectProperty<FilterAlgorithm> optionFilterAlgorithmProperty() {
		return optionFilterAlgorithm;
	}

	public void setOptionFilterAlgorithm(FilterAlgorithm optionFilterAlgorithm) {
		this.optionFilterAlgorithm.set(optionFilterAlgorithm);
	}

	public float getOptionWeightThreshold() {
		return optionWeightThreshold.get();
	}

	public FloatProperty optionWeightThresholdProperty() {
		return optionWeightThreshold;
	}

	public void setOptionWeightThreshold(float optionWeightThreshold) {
		this.optionWeightThreshold.set(optionWeightThreshold);
	}

	public float getOptionConfidenceThreshold() {
		return optionConfidenceThreshold.get();
	}

	public FloatProperty optionConfidenceThresholdProperty() {
		return optionConfidenceThreshold;
	}

	public void setOptionConfidenceThreshold(float optionConfidenceThreshold) {
		this.optionConfidenceThreshold.set(optionConfidenceThreshold);
	}

	public int getOptionMaximumDimension() {
		return optionMaximumDimension.get();
	}

	public IntegerProperty optionMaximumDimensionProperty() {
		return optionMaximumDimension;
	}

	public void setOptionMaximumDimension(int optionMaximumDimension) {
		this.optionMaximumDimension.set(optionMaximumDimension);
	}

	public boolean getOptionRecomputeCycle() {
		return optionRecomputeCycle.get();
	}

	public BooleanProperty optionRecomputeCycleProperty() {
		return optionRecomputeCycle;
	}

	public void setOptionRecomputeCycle(boolean optionRecomputeCycle) {
		this.optionRecomputeCycle.set(optionRecomputeCycle);
	}
}
