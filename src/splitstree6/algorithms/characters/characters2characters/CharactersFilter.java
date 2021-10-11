/*
 * CharactersFilter.java Copyright (C) 2021. Daniel H. Huson
 *
 * (Some code written by other authors, as named in code.)
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
 *
 */

package splitstree6.algorithms.characters.characters2characters;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import jloda.util.ProgressListener;
import splitstree6.algorithms.IFilter;
import splitstree6.data.CharactersBlock;
import splitstree6.data.TaxaBlock;

import java.io.IOException;
import java.util.*;

/**
 * removes columns from a character alignment
 * Daniel Huson, 1.2018
 */
public class CharactersFilter extends Characters2Characters implements IFilter {
	private final BitSet columnMask = new BitSet(); // positions set here are ignored

	private final BooleanProperty optionExcludeGapSites = new SimpleBooleanProperty(false);
	private final BooleanProperty optionExcludeParsimonyUninformativeSites = new SimpleBooleanProperty(false);
	private final BooleanProperty optionExcludeConstantSites = new SimpleBooleanProperty(false);

	private final BooleanProperty optionExcludeFirstCodonPosition = new SimpleBooleanProperty(false);
	private final BooleanProperty optionExcludeSecondCodonPosition = new SimpleBooleanProperty(false);
	private final BooleanProperty optionExcludeThirdCodonPosition = new SimpleBooleanProperty(false);

	public List<String> listOptions() {
		return Arrays.asList("ExcludeGapSites", "ExcludeConstantSites", "ExcludeParsimonyUninformativeSites", "ExcludeFirstCodonPosition", "ExcludeSecondCodonPosition", "ExcludeThirdCodonPosition");
	}

	@Override
	public String getToolTip(String optionName) {
		return switch (optionName) {
			case "ExcludeGapSites" -> "Exclude all sites that contain a gap";
			case "ExcludeConstantSites" -> "Exclude all sites that are constant";
			case "ExcludeParsimonyUninformativeSites" -> "Exclude all sites that are parsimony uninformative";
			case "ExcludeFirstCodonPosition" -> "Exclude first and then every third site";
			case "ExcludeSecondCodonPosition" -> "Exclude second and then every third site";
			case "ExcludeThirdCodonPosition" -> "Exclude third and then every third site";
			default -> optionName;
		};
	}

	@Override
	public void compute(ProgressListener progress, TaxaBlock taxa, CharactersBlock parent, CharactersBlock child) throws IOException {
		child.clear();

		int totalColumns = parent.getNchar();

		final Map<Integer, Integer> ch2count = new HashMap<>();

		columnMask.clear();

		for (int col = 1; col <= parent.getNchar(); col++) {
			if (isOptionExcludeFirstCodonPosition() && (col % 3) == 1)
				columnMask.set(col);
			else if (isOptionExcludeSecondCodonPosition() && (col % 3) == 2)
				columnMask.set(col);
			else if (isOptionExcludeThirdCodonPosition() && (col % 3) == 0)
				columnMask.set(col);
			else if (isOptionExcludeConstantSites() || isOptionExcludeParsimonyUninformativeSites()) {
				ch2count.clear();
				for (int tax = 1; tax <= taxa.getNtax(); tax++) {
					int ch = parent.get(tax, col);
					if (ch == parent.getGapCharacter()) {
						if (isOptionExcludeGapSites()) {
							columnMask.set(col);
							break;
						}
					}
					if (ch2count.containsKey(ch))
						ch2count.put(ch, ch2count.get(ch) + 1);
					else
						ch2count.put(ch, 1);
				}
				if (isOptionExcludeConstantSites() && ch2count.size() == 1) {
					columnMask.set(col);
				} else if (isOptionExcludeParsimonyUninformativeSites() && ch2count.size() == 2 && ch2count.values().contains(1)) {
					columnMask.set(col);
				}
			}
		}
		System.err.println("Mask size: " + columnMask.cardinality());

		child.setDimension(taxa.getNtax(), parent.getNchar() - columnMask.cardinality());
		// set this after setting the dimension:
		child.setStateLabeler(parent.getStateLabeler());
		child.setDataType(parent.getDataType());
		child.setDiploid(parent.isDiploid());
		child.setGapCharacter(parent.getGapCharacter());
		child.setMissingCharacter(parent.getMissingCharacter());
		child.setSymbols(parent.getSymbols());
		child.setRespectCase(parent.isRespectCase());
		child.setUseCharacterWeights(parent.isUseCharacterWeights());

		if (parent.getCharLabeler() != null)
			child.setCharLabeler(new HashMap<>());

		int pos = 1;
		for (int c = 1; c <= parent.getNchar(); c++) {
			if (!columnMask.get(c)) {
				for (int tax = 1; tax <= taxa.getNtax(); tax++) {
					child.set(tax, pos, parent.get(tax, c));
				}
				if (parent.isUseCharacterWeights())
					child.setCharacterWeight(pos, parent.getCharacterWeight(c));
				if (parent.getCharLabeler() != null) {
					final String label = parent.getCharLabeler().get(c);
					if (label != null)
						child.getCharLabeler().put(pos, label);
				}
				pos++;
			}
		}

		if (totalColumns == 0)
			setShortDescription(null);
		else if (columnMask.cardinality() > 0) {
			setShortDescription("using " + (totalColumns - columnMask.cardinality()) + " (of " + totalColumns + ") chars");
		} else
			setShortDescription("using all " + totalColumns + " chars");
	}

	@Override
	public void clear() {
		super.clear();
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

	@Override
	public boolean isActive() {
		return columnMask.cardinality() > 0;
	}
}