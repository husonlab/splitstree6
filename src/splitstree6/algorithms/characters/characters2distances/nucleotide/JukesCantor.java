/*
 * JukesCantor.java Copyright (C) 2021. Daniel H. Huson
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
import splitstree6.models.nucleotideModels.JCmodel;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * implements the Jukes Cantor transformation
 * Daniel Huson, 2.2019
 */
public class JukesCantor extends Nucleotides2DistancesBase {
	@Override
	public String getCitation() {
		return "Jukes and Cantor 1969; Jukes TH & Cantor CR (1969). Evolution of Protein Molecules. New York: Academic Press. pp. 21–132";
	}

	@Override
	public List<String> listOptions() {
		return Arrays.asList("PropInvariableSites", "SetSiteVarParams", "UseML_Distances");
	}

	@Override
	public void compute(ProgressListener progress, TaxaBlock taxaBlock, CharactersBlock parent, DistancesBlock child) throws IOException {

		final var model = new JCmodel();

		model.setPropInvariableSites(getOptionPropInvariableSites());
		model.setGamma(DEFAULT_GAMMA);

		model.apply(progress, parent, child, isOptionUseML_Distances());
	}
}
