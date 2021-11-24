/*
 * Dice.java Copyright (C) 2021. Daniel H. Huson
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

package splitstree6.algorithms.characters.characters2distances;

import jloda.util.progress.ProgressListener;
import splitstree6.algorithms.characters.characters2distances.utils.FixUndefinedDistances;
import splitstree6.algorithms.characters.characters2distances.utils.PairwiseCompare;
import splitstree6.data.CharactersBlock;
import splitstree6.data.DistancesBlock;
import splitstree6.data.TaxaBlock;
import splitstree6.data.parts.CharactersType;

import java.io.IOException;

/**
 * Calculates distances using the Dice coefficient distance
 * <p>
 * Created on Nov 2007
 *
 * @author bryant
 */

public class Dice extends Characters2Distances {
	@Override
	public String getCitation() { // is this the correct citation?
		return "Dice 1945; Dice, Lee R. (1945). Measures of the Amount of Ecologic Association Between Species. Ecology. 26 (3): 297–302.";
	}

	@Override
	public void compute(ProgressListener progress, TaxaBlock taxaBlock, CharactersBlock charactersBlock, DistancesBlock distancesBlock) throws IOException {

		int ntax = taxaBlock.getNtax();
		distancesBlock.setNtax(ntax);

		progress.setTasks("Dice distance", "Init.");
		progress.setMaximum(ntax);

		double maxDist = 0.0;
		int numUndefined = 0;

		for (int s = 1; s <= ntax; s++) {
			for (int t = s + 1; t <= ntax; t++) {
				var seqPair = new PairwiseCompare(charactersBlock, s, t);
				double dist;

				double[][] F = seqPair.getF();
				if (F == null) {
					numUndefined++;
					dist = -1;
				} else {

					double b = F[1][0];
					double c = F[0][1];
					double a = F[1][1];

					if (2 * a + b + c <= 0.0) {
						numUndefined++;
						dist = -1;
					} else {
						dist = 1.0 - 2.0 * a / (2.0 * a + b + c);
					}
				}

				distancesBlock.set(s, t, dist);
				distancesBlock.set(t, s, dist);
				if (dist > maxDist)
					maxDist = dist;
			}
			progress.incrementProgress();
		}

		if (numUndefined > 0)
			FixUndefinedDistances.apply(ntax, maxDist, distancesBlock);

		progress.close();
	}

	@Override
	public boolean isApplicable(TaxaBlock taxaBlock, CharactersBlock parent) {
		return parent.getDataType().equals(CharactersType.Standard);
	}
}

