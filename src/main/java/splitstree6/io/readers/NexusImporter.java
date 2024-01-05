/*
 * NexusImporter.java Copyright (C) 2024 Daniel H. Huson
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

import jloda.util.FileUtils;
import jloda.util.Single;
import jloda.util.parse.NexusStreamParser;
import splitstree6.algorithms.utils.SplitsBlockUtilities;
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
			parse(np, taxaBlock, dataBlock);
		}
	}

	/**
	 * parse the first datablock in a nexus file
	 */
	public static <D extends DataBlock> void parse(NexusStreamParser np, TaxaBlock taxaBlock, D dataBlock) throws IOException {
		np.matchIgnoreCase("#nexus");

		List<String> taxLabels;
		var comments = new Single<String>(null);

		try {
			np.setCollectAllCommentsWithExclamationMark(true);
			np.setEchoCommentsWithExclamationMark(true);
			if (np.isAtBeginOfBlock("SplitsTree6") || np.isAtBeginOfBlock("SplitsTree5")) {
				np.skipBlock();
				comments.setIfCurrentValueIsNull(np.popComments());
				{
					var parser = new TaxaNexusInput();
					parser.parse(np, taxaBlock);
					comments.setIfCurrentValueIsNull(np.popComments());
				}
				while (np.isAtBeginOfBlock("ALGORITHM") || np.isAtBeginOfBlock("TAXA")) {
					np.skipBlock();
					comments.setIfCurrentValueIsNull(np.popComments());
				}
				if (np.isAtBeginOfBlock("TRAITS")) {
					var parser = new TraitsNexusInput();
					var block = new TraitsBlock();
					parser.parse(np, taxaBlock, block);
					comments.setIfCurrentValueIsNull(np.popComments());
					taxaBlock.setTraitsBlock(block);
				}
				if (np.isAtBeginOfBlock("SETS")) {
					var parser = new SetsNexusInput();
					var block = new SetsBlock();
					parser.parse(np, taxaBlock, block);
					comments.setIfCurrentValueIsNull(np.popComments());
					taxaBlock.setSetsBlock(block);
				}
			} else if (np.isAtBeginOfBlock("TAXA")) {
				{
					var parser = new TaxaNexusInput();
					parser.parse(np, taxaBlock);
					comments.setIfCurrentValueIsNull(np.popComments());
				}
				if (np.isAtBeginOfBlock("TRAITS")) {
					var parser = new TraitsNexusInput();
					var block = new TraitsBlock();
					parser.parse(np, taxaBlock, block);
					comments.setIfCurrentValueIsNull(np.popComments());
					taxaBlock.setTraitsBlock(block);
				}
				if (np.isAtBeginOfBlock("SETS")) {
					var parser = new SetsNexusInput();
					var block = new SetsBlock();
					parser.parse(np, taxaBlock, block);
					comments.setIfCurrentValueIsNull(np.popComments());
					taxaBlock.setSetsBlock(block);
				}
			}

			while (!np.isAtBeginOfBlock(dataBlock.getBlockName()) && !(dataBlock instanceof CharactersBlock && np.isAtBeginOfBlock("data"))) {
				np.skipBlock();
				comments.setIfCurrentValueIsNull(np.popComments());
			}
			comments.setIfCurrentValueIsNull(np.popComments());

			if (dataBlock instanceof CharactersBlock charactersBlock) {
				var parser = new CharactersNexusInput();
				taxLabels = parser.parse(np, taxaBlock, charactersBlock);
				comments.setIfCurrentValueIsNull(np.popComments());
			} else if (dataBlock instanceof GenomesBlock genomesBlock) {
				var parser = new GenomesNexusInput();
				taxLabels = parser.parse(np, taxaBlock, genomesBlock);
				comments.setIfCurrentValueIsNull(np.popComments());
			} else if (dataBlock instanceof DistancesBlock distancesBlock) {
				var parser = new DistancesNexusInput();
				taxLabels = parser.parse(np, taxaBlock, distancesBlock);
				comments.setIfCurrentValueIsNull(np.popComments());
			} else if (dataBlock instanceof SplitsBlock splitsBlock) {
				var parser = new SplitsNexusInput();
				taxLabels = parser.parse(np, taxaBlock, splitsBlock);
				if (splitsBlock.getCycle(false) == null) {
					splitsBlock.setCycle(SplitsBlockUtilities.computeCycle(taxaBlock.getNtax(), splitsBlock.getSplits()));
				}
				comments.setIfCurrentValueIsNull(np.popComments());
			} else if (dataBlock instanceof TreesBlock treesBlock) {
				var parser = new TreesNexusInput();
				taxLabels = parser.parse(np, taxaBlock, treesBlock);
				comments.setIfCurrentValueIsNull(np.popComments());
			} else if (dataBlock instanceof NetworkBlock networkBlock) {
				var parser = new NetworkNexusInput();
				taxLabels = parser.parse(np, taxaBlock, networkBlock);
				comments.setIfCurrentValueIsNull(np.popComments());
			} else if (dataBlock instanceof ReportBlock reportBlock) {
				var parser = new ReportNexusInput();
				parser.parse(np, taxaBlock, reportBlock);
				comments.setIfCurrentValueIsNull(np.popComments());
				taxLabels = null;
			} else {
				throw new IOException("Not implemented: import '" + dataBlock.getName());
			}

			if (taxaBlock.getNtax() == 0 || taxaBlock.size() == 0) {
				if (taxLabels != null && (taxLabels.size() == taxaBlock.getNtax() || taxLabels.size() > 0 && taxaBlock.getNtax() == 0))
					taxaBlock.addTaxaByNames(taxLabels);
				else
					throw new IOException("Can't infer taxon names");
			}
			if (np.isAtBeginOfBlock(TraitsBlock.BLOCK_NAME)) {
				var parser = new TraitsNexusInput();
				var block = new TraitsBlock();
				parser.parse(np, taxaBlock, block);
				taxaBlock.setTraitsBlock(block);
				comments.setIfCurrentValueIsNull(np.popComments());
			}
			if (np.isAtBeginOfBlock(SetsBlock.BLOCK_NAME)) {
				var parser = new SetsNexusInput();
				var block = new SetsBlock();
				parser.parse(np, taxaBlock, block);
				comments.setIfCurrentValueIsNull(np.popComments());
				taxaBlock.setSetsBlock(block);
			}
		} finally {
			taxaBlock.setComments(comments.get());
			np.setCollectAllCommentsWithExclamationMark(false);
			np.setEchoCommentsWithExclamationMark(false);
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
				if (np.isAtBeginOfBlock("TRAITS") || np.isAtBeginOfBlock("SETS")) {
					np.skipBlock();
				}
			} else if (np.isAtBeginOfBlock("TAXA")) {
				np.skipBlock();
				if (np.isAtBeginOfBlock("TRAITS") || np.isAtBeginOfBlock("SETS")) {
					np.skipBlock();
				}
			}

			if (np.isAtBeginOfBlock("CHARACTERS") || np.isAtBeginOfBlock("DATA")) {
				return CharactersBlock.class;
			} else if (np.isAtBeginOfBlock("GENOMES")) {
				return GenomesBlock.class;
			} else if (np.isAtBeginOfBlock("DISTANCES")) {
				return DistancesBlock.class;
			} else if (np.isAtBeginOfBlock("SPLITS")) {
				return SplitsBlock.class;
			} else if (np.isAtBeginOfBlock("TREES")) {
				return TreesBlock.class;
			} else if (np.isAtBeginOfBlock("NETWORK")) {
				return NetworkBlock.class;
			} else if (np.isAtBeginOfBlock("TEXT")) {
				return ReportBlock.class;
			}
		} catch (IOException ignored) {
		}
		return null;
	}

}
