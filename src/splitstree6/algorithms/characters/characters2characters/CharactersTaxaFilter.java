/*
 * CharactersTaxaFilter.java Copyright (C) 2022 Daniel H. Huson
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

package splitstree6.algorithms.characters.characters2characters;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import jloda.util.progress.ProgressListener;
import splitstree6.data.CharactersBlock;
import splitstree6.data.TaxaBlock;
import splitstree6.data.parts.Taxon;
import splitstree6.workflow.DataTaxaFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * characters taxa filter
 * Daniel Huson, 2020
 */
public class CharactersTaxaFilter extends DataTaxaFilter<CharactersBlock, CharactersBlock> {
	private final BooleanProperty optionExcludeGapSites = new SimpleBooleanProperty(this, "optionExcludeGapSites", false);
	private final BooleanProperty optionExcludeParsimonyUninformativeSites = new SimpleBooleanProperty(this, "optionExcludeParsimonyUninformativeSites", false);
	private final BooleanProperty optionExcludeConstantSites = new SimpleBooleanProperty(this, "optionExcludeConstantSites", false);

	private final BooleanProperty optionExcludeFirstCodonPosition = new SimpleBooleanProperty(this, "optionExcludeFirstCodonPosition", false);
	private final BooleanProperty optionExcludeSecondCodonPosition = new SimpleBooleanProperty(this, "optionExcludeSecondCodonPosition", false);
	private final BooleanProperty optionExcludeThirdCodonPosition = new SimpleBooleanProperty(this, "optionExcludeThirdCodonPosition", false);

	public List<String> listOptions() {
		return Arrays.asList(optionExcludeGapSites.getName(), optionExcludeParsimonyUninformativeSites.getName(), optionExcludeConstantSites.getName(),
				optionExcludeFirstCodonPosition.getName(), optionExcludeSecondCodonPosition.getName(), optionExcludeThirdCodonPosition.getName());
	}

	public CharactersTaxaFilter() {
		super(CharactersBlock.class, CharactersBlock.class);
	}

	@Override
	public void filter(ProgressListener progress, TaxaBlock originalTaxaBlock, TaxaBlock modifiedTaxaBlock, CharactersBlock inputData, CharactersBlock outputData) throws IOException {
		// todo: implement direct copy?
		{
			//System.err.println("******* CharactersTaxaFilter");
			progress.setMaximum(modifiedTaxaBlock.size());
			/*
			final StringWriter w = new StringWriter();
			try {
				final CharactersNexusOutput charactersNexusOutput = new CharactersNexusOutput();
				charactersNexusOutput.setIgnoreMatrix(true);
				charactersNexusOutput.write(w, originalTaxaBlock, parent);
				final CharactersNexusInput charactersNexusInput = new CharactersNexusInput();
				charactersNexusInput.setIgnoreMatrix(true);
				charactersNexusInput.parse(new NexusStreamParser(new StringReader(w.toString())), originalTaxaBlock, outputData);
			} catch (IOException e) {
				Basic.caught(e);
			}
			 */
		}
		outputData.setDimension(modifiedTaxaBlock.getNtax(), 0);

		for (Taxon a : modifiedTaxaBlock.getTaxa()) {
			final int originalI = originalTaxaBlock.indexOf(a);
			final int modifiedI = modifiedTaxaBlock.indexOf(a);
			outputData.copyRow(inputData, originalI, modifiedI);
			progress.incrementProgress();
		}

		outputData.setDataType(inputData.getDataType());

		outputData.setMissingCharacter(inputData.getMissingCharacter());
		outputData.setGapCharacter(inputData.getGapCharacter());
		outputData.setHasAmbiguityCodes(inputData.isHasAmbiguityCodes());
		outputData.setSymbols(inputData.getSymbols());
		outputData.setCharacterWeights(inputData.getCharacterWeights());
		outputData.setStateLabeler(inputData.getStateLabeler());
		outputData.setCharLabeler(inputData.getCharLabeler());
		outputData.setRespectCase(inputData.isRespectCase());
		outputData.setUseCharacterWeights(inputData.isUseCharacterWeights());

		if (isOptionExcludeConstantSites() || isOptionExcludeGapSites() || isOptionExcludeFirstCodonPosition() || isOptionExcludeSecondCodonPosition() || isOptionExcludeThirdCodonPosition()
			|| isOptionExcludeParsimonyUninformativeSites()) {
			var charactersFilter = new CharactersFilter();
			charactersFilter.setOptionExcludeConstantSites(isOptionExcludeConstantSites());
			charactersFilter.setOptionExcludeFirstCodonPosition(isOptionExcludeFirstCodonPosition());
			charactersFilter.setOptionExcludeSecondCodonPosition(isOptionExcludeSecondCodonPosition());
			charactersFilter.setOptionExcludeThirdCodonPosition(isOptionExcludeThirdCodonPosition());
			charactersFilter.setOptionExcludeGapSites(isOptionExcludeGapSites());
			charactersFilter.setOptionExcludeParsimonyUninformativeSites(isOptionExcludeParsimonyUninformativeSites());

			var charactersBlock = new CharactersBlock(outputData);
			charactersFilter.compute(progress, modifiedTaxaBlock, charactersBlock, outputData);
		}

		if (modifiedTaxaBlock.size() == originalTaxaBlock.size())
			setShortDescription("using all " + modifiedTaxaBlock.size() + " sequences");
		else
			setShortDescription("using " + modifiedTaxaBlock.size() + " of " + originalTaxaBlock.size() + " sequences");
	}

	public boolean isOptionExcludeGapSites() {
		return optionExcludeGapSites.get();
	}

	public BooleanProperty optionExcludeGapSitesProperty() {
		return optionExcludeGapSites;
	}

	public void setOptionExcludeGapSites(boolean optionExcludeGapSites) {
		this.optionExcludeGapSites.set(optionExcludeGapSites);
	}

	public boolean isOptionExcludeParsimonyUninformativeSites() {
		return optionExcludeParsimonyUninformativeSites.get();
	}

	public BooleanProperty optionExcludeParsimonyUninformativeSitesProperty() {
		return optionExcludeParsimonyUninformativeSites;
	}

	public void setOptionExcludeParsimonyUninformativeSites(boolean optionExcludeParsimonyUninformativeSites) {
		this.optionExcludeParsimonyUninformativeSites.set(optionExcludeParsimonyUninformativeSites);
	}

	public boolean isOptionExcludeConstantSites() {
		return optionExcludeConstantSites.get();
	}

	public BooleanProperty optionExcludeConstantSitesProperty() {
		return optionExcludeConstantSites;
	}

	public void setOptionExcludeConstantSites(boolean optionExcludeConstantSites) {
		this.optionExcludeConstantSites.set(optionExcludeConstantSites);
	}

	public boolean isOptionExcludeFirstCodonPosition() {
		return optionExcludeFirstCodonPosition.get();
	}

	public BooleanProperty optionExcludeFirstCodonPositionProperty() {
		return optionExcludeFirstCodonPosition;
	}

	public void setOptionExcludeFirstCodonPosition(boolean optionExcludeFirstCodonPosition) {
		this.optionExcludeFirstCodonPosition.set(optionExcludeFirstCodonPosition);
	}

	public boolean isOptionExcludeSecondCodonPosition() {
		return optionExcludeSecondCodonPosition.get();
	}

	public BooleanProperty optionExcludeSecondCodonPositionProperty() {
		return optionExcludeSecondCodonPosition;
	}

	public void setOptionExcludeSecondCodonPosition(boolean optionExcludeSecondCodonPosition) {
		this.optionExcludeSecondCodonPosition.set(optionExcludeSecondCodonPosition);
	}

	public boolean isOptionExcludeThirdCodonPosition() {
		return optionExcludeThirdCodonPosition.get();
	}

	public BooleanProperty optionExcludeThirdCodonPositionProperty() {
		return optionExcludeThirdCodonPosition;
	}

	public void setOptionExcludeThirdCodonPosition(boolean optionExcludeThirdCodonPosition) {
		this.optionExcludeThirdCodonPosition.set(optionExcludeThirdCodonPosition);
	}
}
