/*
 * NexusDataBlockInput.java Copyright (C) 2023 Daniel H. Huson
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

package splitstree6.io.nexus.workflow;

import jloda.util.Basic;
import jloda.util.IOExceptionWithLineNumber;
import jloda.util.Pair;
import jloda.util.parse.NexusStreamParser;
import splitstree6.data.*;
import splitstree6.io.nexus.*;
import splitstree6.workflow.DataBlock;

import java.io.IOException;

/**
 * inputs a nexus block
 * Daniel Huson, 3.2018
 */
public class NexusDataBlockInput {
	private String title;
	private Pair<String, String> link;

	/**
	 * parse a nexus datablock
	 */
	public DataBlock parse(NexusStreamParser np, TaxaBlock taxa) throws IOException {
		try {
			if (np.peekMatchBeginBlock(TaxaBlock.BLOCK_NAME)) {
				final var input = new TaxaNexusInput();
				final var dataBlock = new TaxaBlock();
				input.parse(np, dataBlock);
				dataBlock.updateShortDescription();
				title = input.getTitle();
				link = input.getLink();
				return dataBlock;
			} else if (np.peekMatchBeginBlock(TraitsBlock.BLOCK_NAME)) {
				final var input = new TraitsNexusInput();
				final var dataBlock = new TraitsBlock();
				input.parse(np, taxa, dataBlock);
				title = input.getTitle();
				link = input.getLink();
				return dataBlock;
			} else if (np.peekMatchBeginBlock(SetsBlock.BLOCK_NAME)) {
				final var input = new SetsNexusInput();
				final var dataBlock = new SetsBlock();
				input.parse(np, taxa, dataBlock);
				title = input.getTitle();
				link = input.getLink();
				return dataBlock;
			} else if (np.peekMatchBeginBlock(CharactersBlock.BLOCK_NAME)) {
				final var input = new CharactersNexusInput();
				final var dataBlock = new CharactersBlock();
				input.parse(np, taxa, dataBlock);
				dataBlock.updateShortDescription();
				title = input.getTitle();
				link = input.getLink();
				return dataBlock;
			} else if (np.peekMatchBeginBlock(GenomesBlock.BLOCK_NAME)) {
				final var input = new GenomesNexusInput();
				final var dataBlock = new GenomesBlock();
				input.parse(np, taxa, dataBlock);
				title = input.getTitle();
				link = input.getLink();
				return dataBlock;
			} else if (np.peekMatchBeginBlock(DistancesBlock.BLOCK_NAME)) {
				final var input = new DistancesNexusInput();
				final var dataBlock = new DistancesBlock();
				input.parse(np, taxa, dataBlock);
				dataBlock.updateShortDescription();
				title = input.getTitle();
				link = input.getLink();
				return dataBlock;
			} else if (np.peekMatchBeginBlock(SplitsBlock.BLOCK_NAME)) {
				final var input = new SplitsNexusInput();
				final var dataBlock = new SplitsBlock();
				input.parse(np, taxa, dataBlock);
				dataBlock.updateShortDescription();
				title = input.getTitle();
				link = input.getLink();
				return dataBlock;
			} else if (np.peekMatchBeginBlock(TreesBlock.BLOCK_NAME)) {
				final var input = new TreesNexusInput();
				final var dataBlock = new TreesBlock();
				input.parse(np, taxa, dataBlock);
				dataBlock.updateShortDescription();
				title = input.getTitle();
				link = input.getLink();
				return dataBlock;
			} else if (np.peekMatchBeginBlock(NetworkBlock.BLOCK_NAME)) {
				final var input = new NetworkNexusInput();
				final var dataBlock = new NetworkBlock();
				input.parse(np, taxa, dataBlock);
				dataBlock.updateShortDescription();
				title = input.getTitle();
				link = input.getLink();
				return dataBlock;
			} else if (np.peekMatchBeginBlock(ViewBlock.BLOCK_NAME)) {
				final var input = new ViewNexusInput();
				final var dataBlock = new ViewBlock();
				input.parse(np, taxa, dataBlock);
				dataBlock.updateShortDescription();
				title = input.getTitle();
				link = input.getLink();
				return dataBlock;
			} else if (np.peekMatchBeginBlock(ReportBlock.BLOCK_NAME)) {
				final var input = new ReportNexusInput();
				final var dataBlock = new ReportBlock();
				input.parse(np, taxa, dataBlock);
				dataBlock.updateShortDescription();
				title = input.getTitle();
				link = input.getLink();
				return dataBlock;
			}
		} catch (Exception ex) {
			throw new IOExceptionWithLineNumber("Input failed: " + Basic.getShortName(ex.getClass()) + ": " + ex.getMessage(), np.lineno());

		}
		throw new IOExceptionWithLineNumber("Unknown block type", np.lineno());
	}

	public String getTitle() {
		return title;
	}

	public Pair<String, String> getLink() {
		return link;
	}

	public TaxaBlock parse(NexusStreamParser np) throws IOException {
		var taxaBlock = new TaxaBlock();
		var input = new TaxaNexusInput();
		input.parse(np, taxaBlock);
		taxaBlock.updateShortDescription();
		title = input.getTitle();
		link = input.getLink();
		return taxaBlock;
	}
}
