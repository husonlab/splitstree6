/*
 * NexusWriter.java Copyright (C) 2022 Daniel H. Huson
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

package splitstree6.io.writers.splits;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import jloda.util.Pair;
import splitstree6.data.SplitsBlock;
import splitstree6.data.SplitsFormat;
import splitstree6.data.TaxaBlock;
import splitstree6.io.nexus.SplitsNexusOutput;

import java.io.IOException;
import java.io.Writer;
import java.util.List;

/**
 * write block in Nexus format
 * Daniel Huson, 11.2021
 */
public class NexusWriter extends SplitsWriterBase {
	private final BooleanProperty optionPrependTaxa = new SimpleBooleanProperty(this, "optionPrependTaxa", false);
	private String title;
	private Pair<String, String> link;
	private boolean asWorkflowOnly;

	private final BooleanProperty optionWeights = new SimpleBooleanProperty(this, "optionWeights", true);
	private final BooleanProperty optionLabels = new SimpleBooleanProperty(this, "optionLabels", false);
	private final BooleanProperty optionShowBothSides = new SimpleBooleanProperty(this, "optionShowBothSides", false);
	private final BooleanProperty optionConfidences = new SimpleBooleanProperty(this, "optionConfidences", true);

	public NexusWriter() {
		setFileExtensions("nexus", "nex", "nxs");
	}

	public List<String> listOptions() {
		return List.of(optionWeights.getName(),/* optionLabels.getName(),*/optionShowBothSides.getName(), optionConfidences.getName(), optionPrependTaxa.getName());
	}

	@Override
	public void write(Writer w, TaxaBlock taxa, SplitsBlock splits) throws IOException {
		var saveFormat = new SplitsFormat(splits.getFormat());
		try {
			splits.getFormat().setOptionLabels(isOptionLabels());
			splits.getFormat().setOptionWeights(isOptionWeights());
			splits.getFormat().setOptionShowBothSides(isOptionShowBothSides());
			splits.getFormat().setOptionConfidences(isOptionConfidences());

			if (isOptionPrependTaxa())
				new splitstree6.io.writers.taxa.NexusWriter().write(w, taxa, taxa);
			final var output = new SplitsNexusOutput();
			output.setTitleAndLink(getTitle(), getLink());
			if (asWorkflowOnly) {
				var newBlock = new SplitsBlock();
				newBlock.setFormat(splits.getFormat());
				output.write(w, new TaxaBlock(), newBlock);
			} else
				output.write(w, taxa, splits);
			w.flush();
		} finally {
			splits.setFormat(saveFormat);
		}
	}

	public boolean isOptionPrependTaxa() {
		return optionPrependTaxa.get();
	}

	public BooleanProperty optionPrependTaxaProperty() {
		return optionPrependTaxa;
	}

	public boolean isOptionLabels() {
		return optionLabels.get();
	}

	public BooleanProperty optionLabelsProperty() {
		return optionLabels;
	}

	public boolean isOptionWeights() {
		return optionWeights.get();
	}

	public BooleanProperty optionWeightsProperty() {
		return optionWeights;
	}

	public boolean isOptionShowBothSides() {
		return optionShowBothSides.get();
	}

	public BooleanProperty optionShowBothSidesProperty() {
		return optionShowBothSides;
	}

	public boolean isOptionConfidences() {
		return optionConfidences.get();
	}

	public BooleanProperty optionConfidencesProperty() {
		return optionConfidences;
	}

	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	public Pair<String, String> getLink() {
		return link;
	}

	public void setLink(Pair<String, String> link) {
		this.link = link;
	}

	public boolean isAsWorkflowOnly() {
		return asWorkflowOnly;
	}

	public void setAsWorkflowOnly(boolean asWorkflowOnly) {
		this.asWorkflowOnly = asWorkflowOnly;
	}
}
