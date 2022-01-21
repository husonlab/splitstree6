/*
 *  GapDist.java Copyright (C) 2022 Daniel H. Huson
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

import jloda.util.progress.ProgressListener;
import splitstree6.data.CharactersBlock;
import splitstree6.data.DistancesBlock;
import splitstree6.data.TaxaBlock;

import java.io.IOException;

/**
 * Computes the gap distance from a set of sequences
 * <p>
 * created on Nov 2009
 *
 * @author bryant
 */
public class GapDist extends Characters2Distances {
	// todo: citation?

	@Override
	public void compute(ProgressListener progress, TaxaBlock taxaBlock, CharactersBlock charactersBlock, DistancesBlock distancesBlock) throws IOException {

		final int nchar = charactersBlock.getNchar();
		final int ntax = charactersBlock.getNtax();

		distancesBlock.setNtax(ntax);
		//final char missingChar = charactersBlock.getMissingCharacter();
		final char gapChar = charactersBlock.getGapCharacter();
		int c, s, t;

		progress.setTasks("Gap distance", "Init.");
		progress.setMaximum(ntax);

		//todo get row
		for (t = 0; t < ntax; t++) {
			final char[] row_t = charactersBlock.getRow0(t);
			//char[] row_t = new char[charactersBlock.getMatrix()[t].length];
			//System.arraycopy(charactersBlock.getMatrix()[t], 0, row_t, 0, charactersBlock.getMatrix()[t].length);

			for (s = t + 1; s < ntax; s++) {
				char[] row_s = charactersBlock.getRow0(s);
				//char[] row_s = new char[charactersBlock.getMatrix()[s].length];
				//System.arraycopy(charactersBlock.getMatrix()[s], 0, row_s, 0, charactersBlock.getMatrix()[s].length);

				double sim = 0;
				double len = 0;
				char sc, tc;
				for (c = 0; c < nchar; c++) {
					sc = row_s[c];
					tc = row_t[c];

					double weight = charactersBlock.getCharacterWeight(c);
					len += weight;
					if (((sc == gapChar && tc == gapChar) ||
						 (sc != gapChar && tc != gapChar)))
						sim += weight;
				}
				double v = 1.0;
				if (sim != 0 && len != 0) v = (1.0 - sim / len);
				distancesBlock.set(s + 1, t + 1, v);
				distancesBlock.set(t + 1, s + 1, v);
			}
			progress.incrementProgress();
		}
		progress.close();
	}
}
