/*
 *  NewickWriter.java Copyright (C) 2024 Daniel H. Huson
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

package splitstree6.io.writers.splits;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import splitstree6.data.SplitsBlock;
import splitstree6.data.TaxaBlock;
import splitstree6.splits.SplitNewick;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;

/**
 * write as SplitNewick
 * Daniel Huson, 3.2023
 */
public class NewickWriter extends SplitsWriterBase {
	private final BooleanProperty optionEdgeWeights = new SimpleBooleanProperty(this, "optionEdgeWeights", true);

	private final BooleanProperty optionEdgeConfidences = new SimpleBooleanProperty(this, "optionEdgeConfidences", false);

	public NewickWriter() {
		setFileExtensions("tree", "tre", "trees", "new", "nwk", "treefile");
	}

	@Override
	public void write(Writer w, TaxaBlock taxa, SplitsBlock splits) throws IOException {
		var ordering = new ArrayList<Integer>();
		for (var i = 1; i < splits.getCycle().length; i++)
			ordering.add(splits.getCycle()[i]);
		w.write(SplitNewick.toString(taxa::getLabel, splits.getSplits(), isOptionEdgeWeights(), isOptionEdgeConfidences(), ordering) + ";\n");
	}


	public boolean isOptionEdgeWeights() {
		return optionEdgeWeights.get();
	}

	public BooleanProperty optionEdgeWeightsProperty() {
		return optionEdgeWeights;
	}

	public boolean isOptionEdgeConfidences() {
		return optionEdgeConfidences.get();
	}

	public BooleanProperty optionEdgeConfidencesProperty() {
		return optionEdgeConfidences;
	}
}
