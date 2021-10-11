/*
 * BaseFreqDistance.java Copyright (C) 2021. Daniel H. Huson
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
import splitstree6.data.CharactersBlock;
import splitstree6.data.DistancesBlock;
import splitstree6.data.TaxaBlock;

import java.io.IOException;

/**
 * Calculates distances from differences in the base composition
 * <p>
 * Created on Sep 2008
 *
 * @author bryant
 */

public class BaseFreqDistance extends Characters2Distances {
	@Override
	public void compute(ProgressListener progress, TaxaBlock taxaBlock, CharactersBlock charactersBlock, DistancesBlock distancesBlock) throws IOException {
		final String symbols = charactersBlock.getSymbols();
		final int nstates = symbols.length();

		final int ntax = taxaBlock.getNtax();
		distancesBlock.setNtax(ntax);

		progress.setTasks("Base Frequency Distance", "Init.");
		progress.setMaximum(ntax);

		final double[][] baseFreqs = new double[ntax + 1][nstates];
		System.err.println("Base Frequencies");

		for (int s = 1; s <= ntax; s++) {
			//System.err.print(taxaBlock.getLabel(s) + "\t");
			double count = 0;
			for (int i = 1; i < charactersBlock.getNchar(); i++) {
				int x = symbols.indexOf(charactersBlock.get(s, i));
				if (x >= 0) {
					double weight = charactersBlock.getCharacterWeight(i);
					baseFreqs[s][x] += weight;
					count += weight;
				}
			}

			for (int x = 0; x < nstates; x++) {
				baseFreqs[s][x] /= count;
				//System.err.print("" + baseFreqs[s][x] + "\t");
			}
			//System.err.println("");
		}

		for (int s = 1; s <= ntax; s++) {
			for (int t = s + 1; t <= ntax; t++) {
				double p = 0.0;
				for (int i = 0; i < nstates; i++) {
					double pi_i = baseFreqs[s][i];
					double pihat_i = baseFreqs[t][i];
					p += Math.abs(pi_i - pihat_i);
				}

				distancesBlock.set(s, t, p);
				distancesBlock.set(t, s, p);
			}
			progress.incrementProgress();
		}
		progress.close();
	}
}
