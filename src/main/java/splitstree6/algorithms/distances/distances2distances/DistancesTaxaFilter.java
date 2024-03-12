/*
 * DistancesTaxaFilter.java Copyright (C) 2024 Daniel H. Huson
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

package splitstree6.algorithms.distances.distances2distances;

import jloda.util.progress.ProgressListener;
import splitstree6.data.DistancesBlock;
import splitstree6.data.TaxaBlock;
import splitstree6.data.parts.Taxon;
import splitstree6.workflow.DataTaxaFilter;

import java.io.IOException;

public class DistancesTaxaFilter extends DataTaxaFilter<DistancesBlock, DistancesBlock> {

	public DistancesTaxaFilter() {
		super(DistancesBlock.class, DistancesBlock.class);
	}

	@Override
	public void filter(ProgressListener progress, TaxaBlock originalTaxaBlock, TaxaBlock modifiedTaxaBlock, DistancesBlock inputData, DistancesBlock outputData) throws IOException {
		if (originalTaxaBlock.getTaxa().equals(modifiedTaxaBlock.getTaxa())) {
			outputData.copy(inputData);
			setShortDescription("using all " + modifiedTaxaBlock.size() + " taxa");

		} else {
			progress.setMaximum(modifiedTaxaBlock.getNtax());
			outputData.setNtax(modifiedTaxaBlock.getNtax());

			for (Taxon a : modifiedTaxaBlock.getTaxa()) {
				final int originalI = originalTaxaBlock.indexOf(a);
				final int modifiedI = modifiedTaxaBlock.indexOf(a);
				for (Taxon b : modifiedTaxaBlock.getTaxa()) {
					final int originalJ = originalTaxaBlock.indexOf(b);
					final int modifiedJ = modifiedTaxaBlock.indexOf(b);
					outputData.set(modifiedI, modifiedJ, inputData.get(originalI, originalJ));
				}
				progress.incrementProgress();
			}
			setShortDescription("using " + modifiedTaxaBlock.size() + " of " + originalTaxaBlock.size() + " taxa");
		}
	}
}
