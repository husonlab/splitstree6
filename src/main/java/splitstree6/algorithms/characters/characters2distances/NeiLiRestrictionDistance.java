/*
 *  NeiLiRestrictionDistance.java Copyright (C) 2024 Daniel H. Huson
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

package splitstree6.algorithms.characters.characters2distances;

import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import jloda.fx.util.ProgramProperties;
import jloda.util.progress.ProgressListener;
import splitstree6.algorithms.characters.characters2distances.utils.FixUndefinedDistances;
import splitstree6.algorithms.characters.characters2distances.utils.PairwiseCompare;
import splitstree6.data.CharactersBlock;
import splitstree6.data.DistancesBlock;
import splitstree6.data.TaxaBlock;
import splitstree6.data.parts.CharactersType;

import java.io.IOException;

/**
 * Implements the NeiLi (1979) distance for restriction site data.
 *
 * @author David Bryant, 2008?
 */

public class NeiLiRestrictionDistance extends Characters2Distances {

	private final DoubleProperty optionRestrictionSiteLength = new SimpleDoubleProperty(this, "optionRestrictionSiteLength");

	{
		ProgramProperties.track(optionRestrictionSiteLength, 1.0);
	}

	@Override
	public String getCitation() {
		return "Nei & Li 1979;M Nei and WH Li. Mathematical model for studying genetic variation in terms of restriction endonucleases, PNAS 79(1):5269-5273, 1979.";
	}

	@Override
	public String getShortDescription() {
		return "Calculates distances for restriction data.";
	}

	@Override
	public String getToolTip(String optionName) {
		if (optionName.equals(optionRestrictionSiteLength.getName()))
			return "Expected length of restriction site (4-8 bp)";
		else
			return optionName;
	}

	@Override
	public void compute(ProgressListener progress, TaxaBlock taxaBlock, CharactersBlock charactersBlock, DistancesBlock distancesBlock) throws IOException {
		final int ntax = taxaBlock.getNtax();
		distancesBlock.setNtax(ntax);

		progress.setTasks("Nei Li (1979) Restriction Site Distance", "Init.");
		progress.setMaximum(ntax);


		for (int s = 1; s <= ntax; s++) {
			for (int t = s + 1; t <= ntax; t++) {

				PairwiseCompare seqPair = new PairwiseCompare(charactersBlock, s, t);
				final double[][] F = seqPair.getF();
				double dist = -1.0;
				if (F != null) {
					final double ns = F[1][0] + F[1][1];
					final double nt = F[0][1] + F[1][1];
					final double nst = F[1][1];

					if (nst == 0) {
						dist = -1;
					} else {
						final double s_hat = 2.0 * nst / (ns + nt);
						final double a = (4.0 * Math.pow(s_hat, 1.0 / (2 * getOptionRestrictionSiteLength())) - 1.0) / 3.0;
						if (a <= 0.0) {
							dist = -1;
						} else
							dist = -1.5 * Math.log(a);
					}
				}
				distancesBlock.set(s, t, dist);
				distancesBlock.set(t, s, dist);

			}
			progress.incrementProgress();
		}
		FixUndefinedDistances.apply(distancesBlock);

		progress.close();
	}

	@Override
	public boolean isApplicable(TaxaBlock taxa, CharactersBlock datablock) {
		return super.isApplicable(taxa, datablock) && datablock.getDataType() == CharactersType.Standard;
	}

	public double getOptionRestrictionSiteLength() {
		return this.optionRestrictionSiteLength.getValue();
	}

	public DoubleProperty optionRestrictionSiteLengthProperty() {
		return this.optionRestrictionSiteLength;
	}

	public void setOptionRestrictionSiteLength(double restrictionSiteLength) {
		this.optionRestrictionSiteLength.setValue(restrictionSiteLength);
	}
}
