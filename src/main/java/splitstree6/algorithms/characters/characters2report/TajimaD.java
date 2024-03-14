/*
 * EstimateInvariableSites.java Copyright (C) 2024 Daniel H. Huson
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

package splitstree6.algorithms.characters.characters2report;


import jloda.util.CanceledException;
import jloda.util.progress.ProgressListener;
import splitstree6.data.CharactersBlock;
import splitstree6.data.TaxaBlock;
import splitstree6.data.parts.Taxon;

import java.util.Collection;

/**
 * Estimates Tajima's D
 * Daniel Huson and David Bryant, 2024
 */
public class TajimaD extends AnalyzeCharactersBase {

	@Override
	public String getCitation() {
		return "Tajima 1989;F. Tajima,Statistical method for testing the neutral mutation hypothesis by DNA polymorphism. Genetics. 123(3):585–95, 1989";
	}

	@Override
	public String getShortDescription() {
		return "Performs a statistical test for detecting the presence of recombination.";
	}

	@Override
	String runAnalysis(ProgressListener progress, TaxaBlock taxaBlock, CharactersBlock charactersBlock, Collection<Taxon> selectedTaxa) throws CanceledException {
		return "not implemented";

	}

	@Override
	public boolean isApplicable(TaxaBlock taxa, CharactersBlock datablock) {
		return datablock.getDataType().isNucleotides();
	}
}