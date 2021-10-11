/*
 * Jaccard.java Copyright (C) 2021. Daniel H. Huson
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

import jloda.util.ProgressListener;
import splitstree6.algorithms.characters.characters2distances.utils.FixUndefinedDistances;
import splitstree6.algorithms.characters.characters2distances.utils.PairwiseCompare;
import splitstree6.data.CharactersBlock;
import splitstree6.data.DistancesBlock;
import splitstree6.data.TaxaBlock;
import splitstree6.data.parts.CharactersType;

import java.io.IOException;

/**
 * Calculates distances using the Jaccard coefficient distance
 *
 * @author Dave Bryant, 2009
 */
public class Jaccard extends Characters2Distances {
	@Override
	public String getCitation() {
		return "Jaccard 1901; Jaccard, Paul (1901). Étude comparative de la distribution florale dans une portion des Alpes et des Jura, Bulletin de la Société Vaudoise des Sciences Naturelles, 37: 547–579.";
	}

	@Override
	public void compute(ProgressListener progress, TaxaBlock taxaBlock, CharactersBlock charactersBlock, DistancesBlock distancesBlock) throws IOException {
		final int ntax = taxaBlock.getNtax();
		distancesBlock.setNtax(ntax);

		progress.setTasks("Jaccard distance", "Init.");
		progress.setMaximum(ntax);

		double maxDist = 0.0;
		int numUndefined = 0;

		for (int s = 1; s <= ntax; s++) {
			for (int t = s + 1; t <= ntax; t++) {
				//System.err.println(s+","+t);
				final PairwiseCompare seqPair = new PairwiseCompare(charactersBlock, s, t);
				final double[][] F = seqPair.getF();

				var dist = -1.0;

				if (F == null) {
					numUndefined++;
				} else {
					double b = F[1][0];
					double c = F[0][1];
					double a = F[1][1];

					if (a + b + c <= 0.0) {
						numUndefined++;
					} else {
						dist = 1.0 - 2 * a / (2 * a + b + c);
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
	public boolean isApplicable(TaxaBlock taxa, CharactersBlock c) {
		return c.getDataType().equals(CharactersType.Standard);
	}
}
