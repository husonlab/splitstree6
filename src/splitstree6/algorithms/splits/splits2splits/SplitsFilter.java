/*
 * SplitsFilter.java Copyright (C) 2021. Daniel H. Huson
 *
 * (Some code written by other authors, as named in code.)
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

package splitstree6.algorithms.splits.splits2splits;

import javafx.beans.property.*;
import jloda.fx.window.NotificationManager;
import jloda.util.progress.ProgressListener;
import splitstree6.algorithms.IFilter;
import splitstree6.data.SplitsBlock;
import splitstree6.data.TaxaBlock;
import splitstree6.data.parts.ASplit;
import splitstree6.data.parts.Compatibility;
import splitstree6.utils.ClosestTree;
import splitstree6.utils.GreedyCompatible;
import splitstree6.utils.GreedyWeaklyCompatible;
import splitstree6.utils.SplitsUtilities;

import java.io.IOException;
import java.util.*;

/**
 * splits filter
 * Daniel Huson 12/2016
 */
public class SplitsFilter extends Splits2Splits implements IFilter {
	public enum FilterAlgorithm {DimensionFilter, ClosestTree, GreedyCompatible, GreedyWeaklyCompatible, None}

	private final ObjectProperty<FilterAlgorithm> optionFilterAlgorithm = new SimpleObjectProperty<>(this, "FilterAlgorithm", FilterAlgorithm.DimensionFilter);

	private final FloatProperty optionWeightThreshold = new SimpleFloatProperty(this, "WeightThreshold", 0);
	private final FloatProperty optionConfidenceThreshold = new SimpleFloatProperty(this, "ConfidenceThreshold", 0);
	private final IntegerProperty optionMaximumDimension = new SimpleIntegerProperty(this, "MaximumDimension", 4);

	private final BooleanProperty optionModifyWeightsUsingLeastSquares = new SimpleBooleanProperty(this, "ModifyWeightsUsingLeastSquares", false);

	private boolean active = false;

	public List<String> listOptions() {
		return Arrays.asList(optionFilterAlgorithm.getName(), optionWeightThreshold.getName(),
				optionConfidenceThreshold.getName(), optionMaximumDimension.getName());
	}

	@Override
	public String getToolTip(String optionName) {
		return switch (optionName) {
			case "FilterAlgorithm" -> "Set the filter algorithm";
			case "WeightThreshold" -> "Set minimum split weight threshold";
			case "ConfidenceThreshold" -> "Set the minimum split confidence threshold";
			case "MaximumDimension" -> "Set the maximum threshold used by the dimension filter";
			default -> optionName;
		};
	}

	/**
	 * do the computation
	 */
	public void compute(ProgressListener progress, TaxaBlock taxaBlock, SplitsBlock parent, SplitsBlock child) throws IOException {
		active = false;

		ArrayList<ASplit> splits = new ArrayList<>(parent.getSplits());

		if (isOptionModifyWeightsUsingLeastSquares()) {
			NotificationManager.showWarning("optionModifyWeightsUsingLeastSquares: not implemented");
			// modify weights least squares
			//active = true;
		}

		final Map<ASplit, String> split2label = new HashMap<>();
		for (int s = 1; s <= parent.getSplitLabels().size(); s++) {
			split2label.put(parent.get(s), parent.getSplitLabels().get(s));
		}

		Compatibility compatibility = Compatibility.unknown;

		switch (getOptionFilterAlgorithm()) {
			case GreedyCompatible -> {
				final int oldSize = splits.size();
				splits = GreedyCompatible.apply(progress, splits);
				compatibility = Compatibility.compatible;
				if (splits.size() != oldSize)
					active = true;
			}
			case ClosestTree -> {
				final int oldSize = splits.size();
				splits = ClosestTree.apply(progress, taxaBlock.getNtax(), splits, parent.getCycle());
				compatibility = Compatibility.compatible;
				if (splits.size() != oldSize)
					active = true;
			}
			case GreedyWeaklyCompatible -> {
				final int oldSize = splits.size();
				splits = GreedyWeaklyCompatible.apply(progress, splits);
				if (splits.size() != oldSize)
					active = true;
			}
		}
		if (getOptionWeightThreshold() > 0) {
			final int oldSize = splits.size();
			ArrayList<ASplit> tmp = new ArrayList<>(splits.size());
			for (ASplit split : splits) {
				if (split.getWeight() >= getOptionWeightThreshold())
					tmp.add(split);
			}
			splits = tmp;
			if (splits.size() != oldSize)
				active = true;
		}

		if (getOptionConfidenceThreshold() > 0) {
			final int oldSize = splits.size();
			final ArrayList<ASplit> tmp = new ArrayList<>(splits.size());
			for (ASplit split : splits) {
				if (split.getConfidence() >= getOptionConfidenceThreshold())
					tmp.add(split);
			}
			splits = tmp;
			if (splits.size() != oldSize)
				active = true;
		}

		if (getOptionMaximumDimension() > 0 && getOptionFilterAlgorithm() == FilterAlgorithm.GreedyCompatible && parent.getCompatibility() != Compatibility.compatible && parent.getCompatibility() != Compatibility.cyclic && parent.getCompatibility() != Compatibility.weaklyCompatible) {
			final int oldSize = splits.size();

			final DimensionFilter dimensionFilter = new DimensionFilter();
			ArrayList<ASplit> existing = new ArrayList<>(splits);
			splits.clear();
			dimensionFilter.apply(progress, getOptionMaximumDimension(), existing, splits);
			if (splits.size() != oldSize)
				active = true;
		}

		child.getSplits().addAll(splits);
		if (split2label.size() > 0) {
			for (int s = 1; s <= child.getNsplits(); s++) {
				final String label = split2label.get(child.get(s));
				child.getSplitLabels().put(s, label);
			}
		}

		if (!active) {
			child.setCycle(parent.getCycle());
			child.setFit(parent.getFit());
			child.setCompatibility(parent.getCompatibility());
			child.setThreshold(parent.getThreshold());
			setShortDescription("using all " + parent.getNsplits() + " splits");
		} else {
			child.setCycle(SplitsUtilities.computeCycle(taxaBlock.getNtax(), child.getSplits()));

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

	public boolean isOptionModifyWeightsUsingLeastSquares() {
		return optionModifyWeightsUsingLeastSquares.get();
	}

	public BooleanProperty optionModifyWeightsUsingLeastSquaresProperty() {
		return optionModifyWeightsUsingLeastSquares;
	}

	public void setOptionModifyWeightsUsingLeastSquares(boolean optionModifyWeightsUsingLeastSquares) {
		this.optionModifyWeightsUsingLeastSquares.set(optionModifyWeightsUsingLeastSquares);
	}
}
