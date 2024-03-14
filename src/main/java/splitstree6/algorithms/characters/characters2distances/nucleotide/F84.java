/*
 * F84.java Copyright (C) 2024 Daniel H. Huson
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

package splitstree6.algorithms.characters.characters2distances.nucleotide;

import jloda.util.progress.ProgressListener;
import splitstree6.data.CharactersBlock;
import splitstree6.data.DistancesBlock;
import splitstree6.data.TaxaBlock;
import splitstree6.models.nucleotideModels.F84Model;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * Implements the Felsenstein84 DNA distance model
 * David Bryant and Daniel Huson, 2004
 */

public class F84 extends Nucleotides2DistancesBase {
	@Override
	public String getCitation() {
		return "Felsenstein & Churchill 1996; J. Felsenstein and GA Churchill. A Hidden Markov Model approach to variation among sites in rate of evolution, and the branching order in hominoidea. Molecular Biology and Evolution. 13 (1): 93–104, 1996.";
	}

	@Override
	public String getShortDescription() {
		return "Calculates distances under the Felsenstein-84 model.";
	}

	@Override
	public List<String> listOptions() {
		return Arrays.asList("optionBaseFrequencies", "optionSetBaseFrequencies", "optionPropInvariableSites", "optionSetSiteVarParams", "optionUseML_Distances");
	}


	@Override
	public void compute(ProgressListener progress, TaxaBlock taxaBlock, CharactersBlock charactersBlock, DistancesBlock distancesBlock) throws IOException {

		progress.setTasks("Felsenstein 1984 distance", "Computing...");
		progress.setMaximum(taxaBlock.getNtax());

		final F84Model model = new F84Model(getOptionBaseFrequencies(), getOptionTsTvRatio());
		model.setPropInvariableSites(getOptionPropInvariableSites());
		model.setGamma(DEFAULT_GAMMA);

		model.apply(progress, charactersBlock, distancesBlock, isOptionUseML_Distances());
	}
}
