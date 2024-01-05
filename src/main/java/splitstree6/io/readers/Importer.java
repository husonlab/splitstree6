/*
 * Importer.java Copyright (C) 2024 Daniel H. Huson
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

package splitstree6.io.readers;

import jloda.util.Basic;
import jloda.util.Pair;
import jloda.util.progress.ProgressListener;
import splitstree6.data.*;
import splitstree6.io.readers.characters.CharactersReader;
import splitstree6.io.readers.distances.DistancesReader;
import splitstree6.io.readers.genomes.GenomesReader;
import splitstree6.io.readers.network.NetworkReader;
import splitstree6.io.readers.splits.SplitsReader;
import splitstree6.io.readers.trees.TreesReader;
import splitstree6.io.readers.view.ViewReader;
import splitstree6.io.utils.DataReaderBase;
import splitstree6.workflow.DataBlock;

import java.io.IOException;

/**
 * performs import
 * Daniel Huson, 1.2018
 */
public class Importer {
	/**
	 * import from a file
	 *
	 * @return taxa block and data block, or null
	 */
	public static Pair<TaxaBlock, DataBlock> apply(ProgressListener progress, DataReaderBase<?> reader, String fileName) throws IOException {
		if (reader == null)
			throw new IOException("No suitable importer found");
		TaxaBlock taxaBlock = new TaxaBlock();
		DataBlock dataBlock;

		if (reader instanceof CharactersReader) {
			dataBlock = new CharactersBlock();
			((CharactersReader) reader).read(progress, fileName, taxaBlock, (CharactersBlock) dataBlock);
		} else if (reader instanceof GenomesReader) {
			dataBlock = new GenomesBlock();
			((GenomesReader) reader).read(progress, fileName, taxaBlock, (GenomesBlock) dataBlock);
		} else if (reader instanceof DistancesReader) {
			dataBlock = new DistancesBlock();
			((DistancesReader) reader).read(progress, fileName, taxaBlock, (DistancesBlock) dataBlock);
		} else if (reader instanceof TreesReader) {
			dataBlock = new TreesBlock();
			((TreesReader) reader).read(progress, fileName, taxaBlock, (TreesBlock) dataBlock);
		} else if (reader instanceof SplitsReader) {
			dataBlock = new SplitsBlock();
			((SplitsReader) reader).read(progress, fileName, taxaBlock, (SplitsBlock) dataBlock);
		} else if (reader instanceof NetworkReader) {
			dataBlock = new NetworkBlock();
			((NetworkReader) reader).read(progress, fileName, taxaBlock, (NetworkBlock) dataBlock);
		} else if (reader instanceof ViewReader) {
			dataBlock = new ViewBlock();
			((ViewReader) reader).read(progress, fileName, taxaBlock, (ViewBlock) dataBlock);
		} else
			throw new IOException("Import not implemented for: " + Basic.getShortName(reader.getClass()));
        /*
        if (new TraitsNexusInput().isApplicable(fileName)) {
            final TraitsBlock traitsBlock = new TraitsBlock();
            taxaBlock.setTraitsBlock(traitsBlock);
            new TraitsNexusImporter().parse(progress, fileName, taxaBlock, traitsBlock);
        }
         */
		return new Pair<>(taxaBlock, dataBlock);
	}
}
