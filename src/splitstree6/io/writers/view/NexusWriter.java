/*
 *  NewickWriter.java Copyright (C) 2021 Daniel H. Huson
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

package splitstree6.io.writers.view;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import jloda.util.Pair;
import splitstree6.data.TaxaBlock;
import splitstree6.data.ViewBlock;
import splitstree6.io.nexus.ViewNexusOutput;

import java.io.IOException;
import java.io.Writer;

/**
 * write block in Nexus format
 * Daniel Huson, 11.2021
 */
public class NexusWriter extends ViewWriter {
	private final BooleanProperty optionPrependTaxa = new SimpleBooleanProperty(false);
	private String title;
	private Pair<String, String> link;
	private boolean asWorkflowOnly;

	public NexusWriter() {
		setFileExtensions("nexus", "nex", "nxs");
	}

	@Override
	public void write(Writer w, TaxaBlock taxa, ViewBlock block) throws IOException {
		if (isOptionPrependTaxa())
			new splitstree6.io.writers.taxa.NexusWriter().write(w, taxa, taxa);
		final var output = new ViewNexusOutput();
		output.setTitleAndLink(getTitle(), getLink());
		if (asWorkflowOnly) {
			var newBlock = new ViewBlock();
			output.write(w, taxa, newBlock);
		} else
			output.write(w, taxa, block);
	}

	public boolean isOptionPrependTaxa() {
		return optionPrependTaxa.get();
	}

	public BooleanProperty optionPrependTaxaProperty() {
		return optionPrependTaxa;
	}

	public void setOptionPrependTaxa(boolean optionPrependTaxa) {
		this.optionPrependTaxa.set(optionPrependTaxa);
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
