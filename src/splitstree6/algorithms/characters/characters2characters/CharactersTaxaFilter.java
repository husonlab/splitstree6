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

import jloda.util.progress.ProgressListener;
import splitstree6.data.CharactersBlock;
import splitstree6.data.TaxaBlock;
import splitstree6.data.parts.Taxon;
import splitstree6.workflow.DataTaxaFilter;

import java.io.IOException;

public class CharactersTaxaFilter extends DataTaxaFilter<CharactersBlock, CharactersBlock> {

	public CharactersTaxaFilter() {
		super(CharactersBlock.class, CharactersBlock.class);
	}

	@Override
	public void filter(ProgressListener progress, TaxaBlock originalTaxaBlock, TaxaBlock modifiedTaxaBlock, CharactersBlock inputData, CharactersBlock outputData) throws IOException {
		// todo: implement direct copy?
		{
			System.err.println("******* CharactersTaxaFilter");
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
		outputData.setMissingCharacter(inputData.getMissingCharacter());
		outputData.setGapCharacter(inputData.getGapCharacter());
		outputData.setHasAmbiguityCodes(inputData.isHasAmbiguityCodes());
		outputData.setDataType(inputData.getDataType());
		outputData.setSymbols(inputData.getSymbols());
		outputData.setCharacterWeights(inputData.getCharacterWeights());
		outputData.setStateLabeler(inputData.getStateLabeler());
		outputData.setCharLabeler(inputData.getCharLabeler());
		outputData.setRespectCase(inputData.isRespectCase());
		outputData.setUseCharacterWeights(inputData.isUseCharacterWeights());

		if (modifiedTaxaBlock.size() == originalTaxaBlock.size())
			setShortDescription("using all " + modifiedTaxaBlock.size() + " sequences");
		else
			setShortDescription("using " + modifiedTaxaBlock.size() + " of " + originalTaxaBlock.size() + " sequences");
	}
}
