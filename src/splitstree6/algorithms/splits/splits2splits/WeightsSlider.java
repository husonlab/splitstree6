/*
 * SplitsFilter.java Copyright (C) 2023 Daniel H. Huson
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

import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import jloda.util.progress.ProgressListener;
import splitstree6.algorithms.IFilter;
import splitstree6.algorithms.utils.SplitsUtilities;
import splitstree6.data.SplitsBlock;
import splitstree6.data.TaxaBlock;
import splitstree6.data.parts.ASplit;
import splitstree6.data.parts.Compatibility;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

/**
 * splits weights slider
 * Daniel Huson 5.2022
 */
public class WeightsSlider extends Splits2Splits implements IFilter {
	private final DoubleProperty optionWeightThreshold = new SimpleDoubleProperty(this, "optionWeightThreshold", 0);

	private boolean active = false;

	public List<String> listOptions() {
		return List.of(optionWeightThreshold.getName());
	}

	@Override
	public String getToolTip(String optionName) {
		return switch (optionName) {
			case "optionWeightThreshold" -> "Set minimum split weight threshold";
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
		var splits = parent.getSplits().stream().filter(s -> s.getWeight() >= getOptionWeightThreshold()).collect(Collectors.toCollection(ArrayList::new));

		if (splits.size() != parent.getSplits().size())
			active = true;

		if (active) {
			splits.addAll(SplitsUtilities.createAllMissingTrivial(splits, taxaBlock.getNtax(), 0.0));

			child.getSplits().clear();
			child.getSplits().addAll(splits);
			child.setCycle(SplitsUtilities.computeCycle(taxaBlock.getNtax(), child.getSplits()));

			child.setFit(-1);
			child.setCompatibility(Compatibility.compute(taxaBlock.getNtax(), child.getSplits(), child.getCycle()));
			child.setThreshold(parent.getThreshold());

			if (split2label.size() > 0) {
				for (var s = 1; s <= child.getNsplits(); s++) {
					var label = split2label.get(child.get(s));
					child.getSplitLabels().put(s, label);
				}
			}
			setShortDescription("using " + child.getNsplits() + " of " + parent.getNsplits() + " splits");
		} else {
			child.getSplits().clear();
			child.getSplits().addAll(splits);

			child.setCycle(parent.getCycle());
			child.setFit(parent.getFit());
			child.setCompatibility(parent.getCompatibility());
			child.setThreshold(parent.getThreshold());
			setShortDescription("using all " + parent.getNsplits() + " splits");
			active = true;
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

	public double getOptionWeightThreshold() {
		return optionWeightThreshold.get();
	}

	public DoubleProperty optionWeightThresholdProperty() {
		return optionWeightThreshold;
	}

	public void setOptionWeightThreshold(double optionWeightThreshold) {
		this.optionWeightThreshold.set(optionWeightThreshold);
	}


}
