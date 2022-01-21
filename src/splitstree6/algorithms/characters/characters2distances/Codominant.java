/*
 * Codominant.java Copyright (C) 2022 Daniel H. Huson
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

package splitstree6.algorithms.characters.characters2distances;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import jloda.util.progress.ProgressListener;
import splitstree6.data.CharactersBlock;
import splitstree6.data.DistancesBlock;
import splitstree6.data.TaxaBlock;

import java.io.IOException;

/**
 * Implementation of the Co-dominant genetic distance
 *
 * @author David Bryant, 2009
 */

public class Codominant extends Characters2Distances {
	/**
	 * In Smouse and Peakall, the final distance is the square root of the contribution of the
	 * individual loci. This flag sets whether to use this square root, or just the averages
	 * over the loci.
	 */
	//protected boolean useSquareRoot;
	private final BooleanProperty optionUseSquareRoot = new SimpleBooleanProperty(this, "optionUseSquareRoot", true);

	@Override
	public String getCitation() {
		return "Smouse & Peakall 1999; Smouse PE, Peakall R. Spatial autocorrelation analysis of individual multiallele and multilocus genetic structure. Heredity, 82, 561-573, 1999.";
	}

	@Override
	public String getToolTip(String optionName) {
		if (optionName.equals(optionUseSquareRoot.getName()))
			return "Use the final distance as square root of the loci contribution. Otherwise: loci averages";
		else
			return super.getToolTip(optionName);
	}

	@Override
	public boolean isApplicable(TaxaBlock taxaBlock, CharactersBlock charactersBlock) {
		return !charactersBlock.isUseCharacterWeights() && charactersBlock.isDiploid();
	}

	@Override
	public void compute(ProgressListener progress, TaxaBlock taxaBlock, CharactersBlock charactersBlock, DistancesBlock distancesBlock) throws IOException {

		char missingchar = charactersBlock.getMissingCharacter();
		char gapchar = charactersBlock.getGapCharacter();

		int ntax = taxaBlock.getNtax();
		distancesBlock.setNtax(ntax);

		progress.setTasks("Codominant Genetic Distance", "Init.");
		progress.setMaximum(ntax);

		for (int i = 0; i < ntax; i++) {
			char[] seqi = charactersBlock.getRow0(i);

			for (int j = i + 1; j < ntax; j++) {

				char[] seqj = charactersBlock.getRow0(j);
				double distSquared = 0.0;


				int nchar = charactersBlock.getNchar();
				int nLoci = nchar / 2;
				int nValidLoci = 0;

				for (int k = 0; k < nLoci; k++) {

					char ci1 = seqi[2 * k];
					char ci2 = seqi[2 * k + 1];
					char cj1 = seqj[2 * k];
					char cj2 = seqj[2 * k + 1];

					if (ci1 == missingchar || ci2 == missingchar || cj1 == missingchar || cj2 == missingchar)
						continue;
					if (ci1 == gapchar || ci2 == gapchar || cj1 == gapchar || cj2 == gapchar)
						continue;

					nValidLoci++;

					int diff;

					if (ci1 == ci2) { //AA vs ...
						if (cj1 == cj2) {
							if (ci1 != cj1)
								diff = 4;   //AA vs BB
							else
								diff = 0;  //AA vs AA
						} else {  //AA vs XY
							if (ci1 == cj1 || ci1 == cj2)
								diff = 1; //AA vs AY
							else
								diff = 3; //AA vs BC
						}
					} else {     //AB vs ...
						if (cj1 == cj2) {  //AB vs XX
							if (ci1 == cj1 && ci2 == cj1)
								diff = 1;   //AB vs AA
							else
								diff = 3;   //AB vs CC
						} else {  //AB vs XY
							if ((ci1 == cj1 && ci2 == cj2) || (ci1 == cj2 && ci2 == cj1))
								diff = 0; //AB vs BA or AB vs AB
							else if (ci1 == cj1 || ci2 == cj2 || ci1 == cj2 || ci2 == cj1)
								diff = 1;   //AB vs AC
							else
								diff = 2;   //AB vs CD
						}
					}

					distSquared += diff;
				}

				double dij = nchar / 2.0 * distSquared / (double) nValidLoci;
				if (getOptionUseSquareRoot())
					dij = Math.sqrt(dij);

				distancesBlock.set(i + 1, j + 1, Math.sqrt(dij));
				distancesBlock.set(j + 1, i + 1, Math.sqrt(dij));
			}
			progress.incrementProgress();
		}
		progress.close();
	}

	// GETTER AND SETTER

	/**
	 * Get the flag indicating if the distance computed is the square root of the contributions
	 * of the loci (as in (Smouse and Peakall 99).
	 *
	 * @return boolean flag that is true if we use the square root in the final calculation.
	 */
	public boolean getOptionUseSquareRoot() {
		return optionUseSquareRoot.getValue();
	}

	public BooleanProperty optionUseSquareRootProperty() {
		return optionUseSquareRoot;
	}

	/**
	 * Set the flag indicating if the distance computed is the square root of the contributions
	 * of the loci (as in (Smouse and Peakall 99).
	 *
	 * @param useSquareRoot flag that is true if we use the square root in the final calculation.
	 */
	public void setOptionUseSquareRoot(boolean useSquareRoot) {
		this.optionUseSquareRoot.setValue(useSquareRoot);
	}
}
