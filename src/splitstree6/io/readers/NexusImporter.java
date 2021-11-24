/*
 *  Copyright (C) 2018. Daniel H. Huson
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

/*
 *  NexusImporter.java Copyright (C) 2021 Daniel H. Huson
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

package splitstree6.io.readers;

import jloda.util.FileUtils;
import jloda.util.parse.NexusStreamParser;
import splitstree6.data.*;
import splitstree6.io.nexus.*;
import splitstree6.workflow.DataBlock;

import java.io.IOException;
import java.util.List;

/**
 * imports the first occurrence of the provided data-block in the provided nexus file
 * Daniel Huson, 10.2021
 */
public class NexusImporter {

	/**
	 * parse the first datablock in a nexus file
	 */
	public static <D extends DataBlock> void parse(String fileName, TaxaBlock taxaBlock, D dataBlock) throws IOException {
		try (var np = new NexusStreamParser(FileUtils.getReaderPossiblyZIPorGZIP(fileName))) {
			np.matchIgnoreCase("#nexus");

			List<String> taxLabels = null;

			if (np.isAtBeginOfBlock("SplitsTree6") || np.isAtBeginOfBlock("SplitsTree5")) {
				np.skipBlock();
				{
					var parser = new TaxaNexusInput();
					parser.parse(np, taxaBlock);
				}
				while (np.isAtBeginOfBlock("ALGORITHM") || np.isAtBeginOfBlock("TAXA")) {
					np.skipBlock();
				}
				if (np.isAtBeginOfBlock("TRAITS")) {
					np.skipBlock();
				}
			} else if (np.isAtBeginOfBlock("TAXA")) {
				{
					var parser = new TaxaNexusInput();
					parser.parse(np, taxaBlock);
				}
				if (np.isAtBeginOfBlock("TRAITS")) {
					np.skipBlock();
				}
			}

			while (!np.isAtBeginOfBlock(dataBlock.getBlockName()))
				np.skipBlock();

			if (dataBlock instanceof DistancesBlock distancesBlock) {
				var parser = new DistancesNexusInput();
				taxLabels = parser.parse(np, taxaBlock, distancesBlock);
			} else if (dataBlock instanceof CharactersBlock charactersBlock) {
				var parser = new CharactersNexusInput();
				taxLabels = parser.parse(np, taxaBlock, charactersBlock);
			} else if (dataBlock instanceof SplitsBlock splitsBlock) {
				var parser = new SplitsNexusInput();
				taxLabels = parser.parse(np, taxaBlock, splitsBlock);
			} else if (dataBlock instanceof TreesBlock treesBlock) {
				var parser = new TreesNexusInput();
				taxLabels = parser.parse(np, taxaBlock, treesBlock);
			} else if (dataBlock instanceof NetworkBlock networkBlock) {
				var parser = new NetworkNexusInput();
				taxLabels = parser.parse(np, taxaBlock, networkBlock);
			} else {
				throw new IOException("Not implemented: import '" + dataBlock.getName());
			}

			if (taxaBlock.getNtax() > 0 && taxaBlock.size() == 0) {
				if (taxLabels != null && taxLabels.size() == taxaBlock.getNtax())
					taxaBlock.addTaxaByNames(taxLabels);
				else
					throw new IOException("Can't infer taxon names");
			}
		}
	}

	public static Class<? extends DataBlock> determineInputData(String fileName) {

		try (var np = new NexusStreamParser(FileUtils.getReaderPossiblyZIPorGZIP(fileName))) {
			np.matchIgnoreCase("#nexus");

			if (np.isAtBeginOfBlock("SplitsTree6") || np.isAtBeginOfBlock("SplitsTree5")) {
				np.skipBlock(); //
				while (np.isAtBeginOfBlock("ALGORITHM") || np.isAtBeginOfBlock("TAXA")) {
					np.skipBlock();
				}
				if (np.isAtBeginOfBlock("TRAITS")) {
					np.skipBlock();
				}
			} else if (np.isAtBeginOfBlock("TAXA")) {
				np.skipBlock();
			}

			if (np.isAtBeginOfBlock("DISTANCES")) {
				return DistancesBlock.class;
			} else if (np.isAtBeginOfBlock("CHARACTERS") || np.isAtBeginOfBlock("DATA")) {
				return CharactersBlock.class;
			} else if (np.isAtBeginOfBlock("SPLITS")) {
				return SplitsBlock.class;
			} else if (np.isAtBeginOfBlock("TREES")) {
				return TreesBlock.class;
			} else if (np.isAtBeginOfBlock("NETWORK")) {
				return NetworkBlock.class;
			}
		} catch (IOException ignored) {
		}
		return null;
	}

}