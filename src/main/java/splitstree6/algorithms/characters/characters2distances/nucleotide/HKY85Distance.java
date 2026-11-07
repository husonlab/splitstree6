/*
 *  HKY85Distance.java Copyright (C) 2026 Daniel H. Huson
 *
 *  (Some files contain contributions from other authors, who are then mentioned separately.)
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
 */

package splitstree6.algorithms.characters.characters2distances.nucleotide;

import jloda.util.progress.ProgressListener;
import splitstree6.data.CharactersBlock;
import splitstree6.data.DistancesBlock;
import splitstree6.data.TaxaBlock;
import splitstree6.models.nucleotideModels.HKY85model;

import java.io.IOException;
import java.util.List;

/**
 * computes maximum-likelihood distances under the Hasegawa-Kishino-Yano (1985) model
 * Daniel Huson, 11.2026
 */
public class HKY85Distance extends Nucleotides2DistancesBase {
	@Override
	public String getCitation() {
		return "Hasegawa et al 1985;" +
			   " M. Hasegawa, H. Kishino and T. Yano. Dating of human-ape splitting by a molecular clock of mitochondrial DNA." +
			   " Journal of Molecular Evolution. 22(2):160–174, 1985.";
	}

	@Override
	public String getShortDescription() {
		return "Calculates distances under the Hasegawa-Kishino-Yano model.";
	}

	@Override
	public List<String> listOptions() {
		return List.of("optionTsTvRatio", "optionBaseFrequencies", "optionSetBaseFrequencies", "optionPropInvariableSites", "optionSetSiteVarParams");
	}

	public HKY85Distance() {
		setOptionUseML_Distances(true);
	}

	@Override
	public void compute(ProgressListener progress, TaxaBlock taxaBlock, CharactersBlock charactersBlock, DistancesBlock distancesBlock) throws IOException {
		progress.setTasks("HKY85 distance", "Computing...");

		var model = new HKY85model(getOptionBaseFrequencies(), getOptionTsTvRatio());
		model.setPropInvariableSites(getOptionPropInvariableSites());
		model.setGamma(DEFAULT_GAMMA);

		// the model has no closed-form distance, so distances are always estimated by maximum likelihood
		setOptionUseML_Distances(true);
		model.apply(progress, charactersBlock, distancesBlock, true);
	}
}
