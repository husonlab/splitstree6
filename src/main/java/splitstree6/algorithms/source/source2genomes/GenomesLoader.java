/*
 *  GenomesLoader.java Copyright (C) 2024 Daniel H. Huson
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

package splitstree6.algorithms.source.source2genomes;

import jloda.util.progress.ProgressListener;
import splitstree6.data.GenomesBlock;
import splitstree6.data.SourceBlock;
import splitstree6.data.TaxaBlock;
import splitstree6.workflow.DataLoader;

import java.io.IOException;

public class GenomesLoader extends DataLoader<SourceBlock, GenomesBlock> {

	public GenomesLoader() {
		super(SourceBlock.class, GenomesBlock.class);
	}

	@Override
	public void load(ProgressListener progress, SourceBlock inputData, TaxaBlock outputTaxa, GenomesBlock outputBlock) throws IOException {
		var file = inputData.getSources().get(0);
		for (var reader : getReaders()) {
			if (reader.accepts(file)) {
				reader.read(progress, file, outputTaxa, outputBlock);
				System.err.println("Loaded: Taxa: " + outputTaxa.getShortDescription() + " Characters: " + outputBlock.getShortDescription());
				break;
			}
		}
	}

	@Override
	public boolean isApplicable(TaxaBlock taxa, SourceBlock sourceBlock) {
		return super.isApplicable(taxa, sourceBlock) && sourceBlock.getSources().size() == 1;
	}
}
