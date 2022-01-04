/*
 * K3ST.java Copyright (C) 2021. Daniel H. Huson
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

import jloda.util.progress.ProgressListener;
import splitstree6.data.CharactersBlock;
import splitstree6.data.DistancesBlock;
import splitstree6.data.TaxaBlock;
import splitstree6.models.nucleotideModels.K3STmodel;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * Calculates distances using the Kimura-3ST model
 * <p>
 * Created on 12-Jun-2004
 *
 * @author DJB
 */

public class K3ST extends Nucleotides2DistancesBase {

	//private double[][] QMatrix; //Q Matrix provided by user for ML estimation. //todo not used?

	// ACGT transversions vs ATGC transversions

	@Override
	public String getCitation() {
		return "Kimura 1981; M. Kimura, Estimation of evolutionary sequences between homologous nucleotide sequences, " +
			   "Proc. Natl. Acad. Sci. USA 78 (1981) 454–45";
	}

	public List<String> listOptions() {
		return Arrays.asList("optionTsTvRatio", "optionACvATRatio", "optionGamma", "optionPropInvariableSites", "optionSetSiteVarParams", "optionUseML_Distances");
	}

	@Override
	public void compute(ProgressListener progress, TaxaBlock taxaBlock, CharactersBlock charactersBlock, DistancesBlock distancesBlock) throws IOException {
		progress.setTasks("K3ST Distance", "Init.");
		progress.setMaximum(taxaBlock.getNtax());

		var model = new K3STmodel(getOptionTsTvRatio(), getOptionACvATRatio());
		model.setPropInvariableSites(getOptionPropInvariableSites());
		model.setGamma(getOptionGamma());

		model.apply(progress, charactersBlock, distancesBlock, isOptionUseML_Distances());
	}
	// GETTER AND SETTER
}
