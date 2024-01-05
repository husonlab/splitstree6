/*
 * CharactersTaxaFilter.java Copyright (C) 2024 Daniel H. Huson
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

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import jloda.util.BitSetUtils;
import jloda.util.progress.ProgressListener;
import splitstree6.data.CharactersBlock;
import splitstree6.data.CharactersFormat;
import splitstree6.data.TaxaBlock;
import splitstree6.workflow.DataTaxaFilter;

import java.io.IOException;
import java.util.BitSet;
import java.util.List;

/**
 * characters taxa filter
 * Daniel Huson, 2020
 */
public class CharactersTaxaFilter extends DataTaxaFilter<CharactersBlock, CharactersBlock> {
	private final ObjectProperty<int[]> optionDisabledCharacters = new SimpleObjectProperty<>(this, "optionDisabledCharacters", new int[0]);

	public List<String> listOptions() {
		return List.of(optionDisabledCharacters.getName());
	}

	public CharactersTaxaFilter() {
		super(CharactersBlock.class, CharactersBlock.class);
	}

	@Override
	public void filter(ProgressListener progress, TaxaBlock inputTaxa, TaxaBlock workingTaxa, CharactersBlock inputCharacters, CharactersBlock workingCharacters) throws IOException {
		progress.setMaximum(workingTaxa.size());
		// todo: implement direct copy?

		var enabledSites = new BitSet();
		if (getOptionDisabledCharacters().length > 0) {
			enabledSites.set(1, inputCharacters.getNchar() + 1);
			for (var site : getOptionDisabledCharacters()) {
				enabledSites.clear(site);
			}
		}

		if (enabledSites.cardinality() == 0 || enabledSites.cardinality() >= inputCharacters.getNchar()) {
			workingCharacters.setDimension(workingTaxa.getNtax(), 0);
			for (var taxon : workingTaxa.getTaxa()) {
				var tIn = inputTaxa.indexOf(taxon);
				var tOut = workingTaxa.indexOf(taxon);
				workingCharacters.copyRow(inputCharacters, tIn, tOut);
				progress.incrementProgress();
			}
		} else {
			workingCharacters.setDimension(workingTaxa.getNtax(), enabledSites.cardinality());
			for (var taxon : workingTaxa.getTaxa()) {
				var tIn = inputTaxa.indexOf(taxon);
				var tOut = workingTaxa.indexOf(taxon);
				var cOut = 1;
				for (var cIn : BitSetUtils.members(enabledSites)) {
					workingCharacters.set(tOut, cOut++, inputCharacters.get(tIn, cIn));
				}
				progress.incrementProgress();
			}

		}
		workingCharacters.setDataType(inputCharacters.getDataType());

		workingCharacters.setFormat(new CharactersFormat(inputCharacters.getFormat()));

		workingCharacters.setMissingCharacter(inputCharacters.getMissingCharacter());
		workingCharacters.setGapCharacter(inputCharacters.getGapCharacter());
		workingCharacters.setHasAmbiguityCodes(inputCharacters.isHasAmbiguityCodes());
		workingCharacters.setSymbols(inputCharacters.getSymbols());
		workingCharacters.setCharacterWeights(inputCharacters.getCharacterWeights());
		workingCharacters.setStateLabeler(inputCharacters.getStateLabeler());
		workingCharacters.setCharLabeler(inputCharacters.getCharLabeler());
		workingCharacters.setRespectCase(inputCharacters.isRespectCase());
		workingCharacters.setUseCharacterWeights(inputCharacters.isUseCharacterWeights());

		if (workingTaxa.size() == inputTaxa.size())
			setShortDescription("using all " + workingTaxa.size() + " sequences");
		else
			setShortDescription("using " + workingTaxa.size() + " of " + inputTaxa.size() + " sequences");
	}

	public int[] getOptionDisabledCharacters() {
		return optionDisabledCharacters.get();
	}

	public ObjectProperty<int[]> optionDisabledCharactersProperty() {
		return optionDisabledCharacters;
	}

	public void setOptionDisabledCharacters(int[] optionDisabledCharacters) {
		this.optionDisabledCharacters.set(optionDisabledCharacters);
	}
}
