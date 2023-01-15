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

package splitstree6.io.writers.characters;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleIntegerProperty;
import jloda.util.Pair;
import splitstree6.data.CharactersBlock;
import splitstree6.data.CharactersFormat;
import splitstree6.data.TaxaBlock;
import splitstree6.io.nexus.CharactersNexusOutput;
import splitstree6.io.writers.IHasPrependTaxa;

import java.io.IOException;
import java.io.Writer;
import java.util.List;

/**
 * write block in Nexus format
 * Daniel Huson, 11.2021
 */
public class NexusWriter extends CharactersWriterBase implements IHasPrependTaxa {
	private final BooleanProperty optionPrependTaxa = new SimpleBooleanProperty(this, "optionPrependTaxa", false);
	private String title;
	private Pair<String, String> link;
	private boolean asWorkflowOnly;

	private final BooleanProperty optionTranspose = new SimpleBooleanProperty(this, "optionTranspose", false);
	private final BooleanProperty optionInterleave = new SimpleBooleanProperty(this, "optionInterleave", true);
	private final BooleanProperty optionLabels = new SimpleBooleanProperty(this, "optionLabels", true);
	private final BooleanProperty optionTokens = new SimpleBooleanProperty(this, "optionTokens", false);
	private final BooleanProperty optionUseDotAsMatch = new SimpleBooleanProperty(this, "optionUseDotAsMatch", false);
	private final IntegerProperty optionColumnsPerBlock = new SimpleIntegerProperty(this, "optionColumnsPerBlock", 80);


	public NexusWriter() {
		setFileExtensions("nexus", "nex", "nxs");
	}

	public List<String> listOptions() {
		return List.of(optionLabels.getName(), optionInterleave.getName(), optionColumnsPerBlock.getName(), optionTranspose.getName(),
				optionUseDotAsMatch.getName(), optionTokens.getName(), optionPrependTaxa.getName());
	}

	@Override
	public void write(Writer w, TaxaBlock taxa, CharactersBlock characters) throws IOException {
		var saveFormat = new CharactersFormat(characters.getFormat());
		try {
			characters.getFormat().setOptionLabels(isOptionLabels());
			characters.getFormat().setOptionMatchCharacter(isOptionUseDotAsMatch() ? '.' : 0);
			characters.getFormat().setOptionInterleave(isOptionInterleave());
			characters.getFormat().setOptionTranspose(isOptionTranspose());
			characters.getFormat().setOptionColumnsPerBlock(getOptionColumnsPerBlock());
			characters.getFormat().setOptionTokens(isOptionTokens());

			if (isOptionPrependTaxa())
				new splitstree6.io.writers.taxa.NexusWriter(true).write(w, taxa, taxa);

			final var output = new CharactersNexusOutput();
			output.setTitleAndLink(getTitle(), getLink());
			if (asWorkflowOnly) {
				var newBlock = new CharactersBlock();
				newBlock.setDataType(characters.getDataType());
				newBlock.setFormat(characters.getFormat());
				output.write(w, new TaxaBlock(), new CharactersBlock());
			} else
				output.write(w, taxa, characters);
			w.flush();
		} finally {
			characters.setFormat(saveFormat);
		}
	}

	public boolean isOptionPrependTaxa() {
		return optionPrependTaxa.get();
	}

	public BooleanProperty optionPrependTaxaProperty() {
		return optionPrependTaxa;
	}

	public boolean isOptionTranspose() {
		return optionTranspose.get();
	}

	public BooleanProperty optionTransposeProperty() {
		return optionTranspose;
	}

	public boolean isOptionInterleave() {
		return optionInterleave.get();
	}

	public BooleanProperty optionInterleaveProperty() {
		return optionInterleave;
	}

	public boolean isOptionLabels() {
		return optionLabels.get();
	}

	public BooleanProperty optionLabelsProperty() {
		return optionLabels;
	}

	public boolean isOptionTokens() {
		return optionTokens.get();
	}

	public BooleanProperty optionTokensProperty() {
		return optionTokens;
	}

	public boolean isOptionUseDotAsMatch() {
		return optionUseDotAsMatch.get();
	}

	public BooleanProperty optionUseDotAsMatchProperty() {
		return optionUseDotAsMatch;
	}

	public int getOptionColumnsPerBlock() {
		return optionColumnsPerBlock.get();
	}

	public IntegerProperty optionColumnsPerBlockProperty() {
		return optionColumnsPerBlock;
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
