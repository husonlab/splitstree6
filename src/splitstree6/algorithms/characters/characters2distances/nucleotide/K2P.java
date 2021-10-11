/*
 * K2P.java Copyright (C) 2021. Daniel H. Huson
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
import splitstree6.models.nucleotideModels.K2Pmodel;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * Computes the Kimura two parameter distance for a set of characters
 * <p>
 * Created on 12-Jun-2004
 *
 * @author DJB
 */

public class K2P extends Nucleotides2DistancesBase {
	@Override
	public String getCitation() {
		return "Kimura 1980; Kimura M (1980). A simple method for estimating evolutionary rates of base substitutions through comparative studies of nucleotide sequences. Journal of Molecular Evolution. 16 (2): 111–120.";
	}

	@Override
	public List<String> listOptions() {
		return Arrays.asList("TsTvRatio", "Gamma", "PropInvariableSites", "SetSiteVarParams", "UseML_Distances");
	}

	@Override
	public void compute(ProgressListener progress, TaxaBlock taxaBlock, CharactersBlock charactersBlock, DistancesBlock distancesBlock) throws IOException {
		progress.setTasks("K2P Distance", "Init.");
		progress.setMaximum(taxaBlock.getNtax());

		var model = new K2Pmodel(getOptionTsTvRatio());

		model.setPropInvariableSites(getOptionPropInvariableSites());
		model.setGamma(getOptionGamma());

		model.apply(progress, charactersBlock, distancesBlock, isOptionUseML_Distances());
	}
}
