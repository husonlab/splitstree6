/*
 *  GeneSharingDistance.java Copyright (C) 2024 Daniel H. Huson
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
import splitstree6.algorithms.characters.characters2distances.utils.FixUndefinedDistances;
import splitstree6.algorithms.characters.characters2distances.utils.PairwiseCompare;
import splitstree6.data.CharactersBlock;
import splitstree6.data.DistancesBlock;
import splitstree6.data.TaxaBlock;
import splitstree6.data.parts.CharactersType;

import java.io.IOException;

/**
 * Calculates distances using the DiceDistance coefficient distance
 * <p>
 * Created on Nov 2007
 *
 * @author bryant
 */

public class GeneSharingDistance extends Characters2Distances {
	@Override
	public String getCitation() { // is this the correct citation?
		return "Snel et al 1997; B. Snel, P. Bork and MA Huynen. Genome phylogeny based on gene content, Nature Genetics, 21:108-110, 1997.";
	}

	@Override
	public String getShortDescription() {
		return "Computes distances using the gene-sharing distance.";
	}

	@Override
	public void compute(ProgressListener progress, TaxaBlock taxaBlock, CharactersBlock charactersBlock, DistancesBlock distancesBlock) throws IOException {

		int ntax = taxaBlock.getNtax();
		distancesBlock.setNtax(ntax);

		progress.setTasks("Gene-sharing distance", "Init.");
		progress.setMaximum(ntax);

		for (var s = 1; s <= ntax; s++) {
			for (var t = s + 1; t <= ntax; t++) {
				var seqPair = new PairwiseCompare(charactersBlock, s, t);
				var dist = -1.0;
				var F = seqPair.getF();
				if (F != null) {
					var a = F[1][1];
					var b = F[1][0];
					var c = F[0][1];

					if (2 * a + b + c > 0.0) {
						dist = 1.0 - a / Math.min(a + b, a + c);
					}
				}

				distancesBlock.set(s, t, dist);
				distancesBlock.set(t, s, dist);
			}
			progress.incrementProgress();
		}

		FixUndefinedDistances.apply(distancesBlock);
		progress.reportTaskCompleted();
	}

	@Override
	public boolean isApplicable(TaxaBlock taxa, CharactersBlock datablock) {
		return super.isApplicable(taxa, datablock) && datablock.getDataType() == CharactersType.Standard;
	}
}


