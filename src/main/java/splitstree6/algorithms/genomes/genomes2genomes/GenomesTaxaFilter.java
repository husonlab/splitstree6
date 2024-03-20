/*
 *  GenomesTaxaFilter.java Copyright (C) 2024 Daniel H. Huson
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

package splitstree6.algorithms.genomes.genomes2genomes;

import jloda.util.progress.ProgressListener;
import splitstree6.data.GenomesBlock;
import splitstree6.data.TaxaBlock;
import splitstree6.data.parts.Taxon;
import splitstree6.workflow.DataTaxaFilter;

import java.io.IOException;

public class GenomesTaxaFilter extends DataTaxaFilter<GenomesBlock, GenomesBlock> {

	public GenomesTaxaFilter() {
		super(GenomesBlock.class, GenomesBlock.class);
	}

	@Override
	public void filter(ProgressListener progress, TaxaBlock originalTaxaBlock, TaxaBlock modifiedTaxaBlock, GenomesBlock inputData, GenomesBlock outputData) throws IOException {
		if (originalTaxaBlock.getTaxa().equals(modifiedTaxaBlock.getTaxa())) {
			outputData.copy(inputData);
			setShortDescription("using all " + modifiedTaxaBlock.size() + " taxa");

		} else {
			progress.setMaximum(modifiedTaxaBlock.getNtax());
			//outputData.setNtax(modifiedTaxaBlock.getNtax());
			outputData.clear();

			for (Taxon a : modifiedTaxaBlock.getTaxa()) {
				final int originalI = originalTaxaBlock.indexOf(a);
				outputData.getGenomes().add(inputData.getGenome(originalI));
				progress.incrementProgress();
			}
			setShortDescription("using " + modifiedTaxaBlock.size() + " of " + originalTaxaBlock.size() + " taxa");
		}
	}
}
