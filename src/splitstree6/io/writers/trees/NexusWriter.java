/*
 * NexusWriter.java Copyright (C) 2023 Daniel H. Huson
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

package splitstree6.io.writers.trees;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import jloda.fx.util.ProgramProperties;
import jloda.util.Pair;
import splitstree6.data.TaxaBlock;
import splitstree6.data.TreesBlock;
import splitstree6.data.TreesFormat;
import splitstree6.io.nexus.TreesNexusOutput;

import java.io.IOException;
import java.io.Writer;
import java.util.List;

/**
 * write block in Nexus format
 * Daniel Huson, 11.2021
 */
public class NexusWriter extends TreesWriterBase {
	private final BooleanProperty optionPrependTaxa = new SimpleBooleanProperty(this, "optionPrependTaxa", false);
	private String title;
	private Pair<String, String> link;
	private boolean asWorkflowOnly;

	private final BooleanProperty optionTranslate = new SimpleBooleanProperty(this, "optionTranslate", ProgramProperties.get("NexusTreeOptionTranslate", true));
	private final BooleanProperty optionWeights = new SimpleBooleanProperty(this, "optionWeights", ProgramProperties.get("NexusTreeOptionWeights", true));

	public NexusWriter() {
		setFileExtensions("nexus", "nex", "nxs");
	}

	public List<String> listOptions() {
		return List.of(optionTranslate.getName(), optionWeights.getName(), optionPrependTaxa.getName());
	}

	@Override
	public void write(Writer w, TaxaBlock taxa, TreesBlock block) throws IOException {
		var saveFormat = new TreesFormat(block.getFormat());
		try {
			block.getFormat().setOptionTranslate(isOptionTranslate());
			block.getFormat().setOptionWeights(isOptionWeights());

			if (isOptionPrependTaxa()) {
				new splitstree6.io.writers.taxa.NexusWriter().write(w, taxa, taxa);
			}
			final TreesNexusOutput output = new TreesNexusOutput();
			output.setTitleAndLink(getTitle(), getLink());
			if (asWorkflowOnly) {
				var newBlock = new TreesBlock();
				newBlock.setFormat(block.getFormat());
				output.write(w, new TaxaBlock(), newBlock);
			} else
				output.write(w, taxa, block);
		} finally {
			block.setFormat(saveFormat);
		}
	}

	public boolean isOptionPrependTaxa() {
		return optionPrependTaxa.get();
	}

	public BooleanProperty optionPrependTaxaProperty() {
		return optionPrependTaxa;
	}

	public boolean isOptionTranslate() {
		return optionTranslate.get();
	}

	public BooleanProperty optionTranslateProperty() {
		return optionTranslate;
	}

	public boolean isOptionWeights() {
		return optionWeights.get();
	}

	public BooleanProperty optionWeightsProperty() {
		return optionWeights;
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
