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

package splitstree6.io.writers.sets;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import jloda.util.Pair;
import splitstree6.data.SetsBlock;
import splitstree6.data.TaxaBlock;
import splitstree6.io.nexus.SetsNexusOutput;

import java.io.IOException;
import java.io.Writer;

/**
 * write block in Nexus format
 * Daniel Huson, 11.2021
 */
public class NexusWriter extends SetsWriterBase {
	private final BooleanProperty optionPrependNexus = new SimpleBooleanProperty(this, "optionPrependNexus", true);
	private String title;
	private Pair<String, String> link;
	private boolean asWorkflowOnly;

	public NexusWriter() {
		setFileExtensions("nxs", "nex", "nexus");
	}

	@Override
	public void write(Writer w, TaxaBlock taxaBlock, SetsBlock block) throws IOException {
		if (isOptionPrependNexus())
			w.write("#nexus\n");
		var output = new SetsNexusOutput();
		output.setTitleAndLink(getTitle(), getLink());
		if (asWorkflowOnly)
			output.write(w, new TaxaBlock(), new SetsBlock());
		else
			output.write(w, taxaBlock, block);
		w.flush();
	}

	public boolean isOptionPrependNexus() {
		return optionPrependNexus.get();
	}

	public BooleanProperty optionPrependNexusProperty() {
		return optionPrependNexus;
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
