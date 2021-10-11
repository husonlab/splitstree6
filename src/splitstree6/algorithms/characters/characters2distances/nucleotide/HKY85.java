/*
 * HKY85.java Copyright (C) 2021. Daniel H. Huson
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

package splitstree6.algorithms.characters.characters2distances.nucleotide;

import jloda.util.ProgressListener;
import splitstree6.data.CharactersBlock;
import splitstree6.data.DistancesBlock;
import splitstree6.data.TaxaBlock;
import splitstree6.models.nucleotideModels.HKY85model;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * Computes the Hasegawa, Kishino and Yano distance for a set of characters.
 * <p>
 * Created on 12-Jun-2004
 *
 * @author Mig
 */

public class HKY85 extends Nucleotides2DistancesBase {
	@Override
	public String getCitation() {
		return "Hasegawa, Kishino, Yano 1985;" +
				" Hasegawa M, Kishino H, Yano T. Dating of human-ape splitting by a molecular clock of mitochondrial DNA." +
				" Journal of Molecular Evolution. 22 (2): 160–174. PMID 3934395. doi:10.1007/BF02101694, 1985.";
	}

	@Override
	public List<String> listOptions() {
		return Arrays.asList("TsTvRatio", "optionBaseFrequencies", "SetBaseFrequencies", "PropInvariableSites", "SetSiteVarParams");
	}

	public HKY85() {
		super();
		setOptionUseML_Distances(true);
	}

	@Override
	public void compute(ProgressListener progress, TaxaBlock taxaBlock, CharactersBlock charactersBlock, DistancesBlock distancesBlock) throws IOException {

		progress.setTasks("HKY85 distance", "Computing...");

		var model = new HKY85model(getOptionBaseFrequencies(), getOptionTsTvRatio());
		model.setPropInvariableSites(getOptionPropInvariableSites());
		model.setGamma(DEFAULT_GAMMA);

		setOptionUseML_Distances(true);
		model.apply(progress, charactersBlock, distancesBlock, isOptionUseML_Distances());
		// there is no exact formular
	}
}
