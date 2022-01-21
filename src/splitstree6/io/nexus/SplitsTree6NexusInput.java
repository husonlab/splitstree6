/*
 * SplitsTree6NexusInput.java Copyright (C) 2022 Daniel H. Huson
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

package splitstree6.io.nexus;

import jloda.util.parse.NexusStreamParser;
import splitstree6.data.SplitsTree6Block;

import java.io.IOException;

/**
 * SplitsTree6 nexus input
 * Daniel Huson, 3.2018
 */
public class SplitsTree6NexusInput {
	public static final String SYNTAX = """
			BEGIN SPLITSTREE6;
			\tDIMENSIONS nDataNodes=number nAlgorithms=number;
			\tPROGRAM version=version-string;
			\tWORKFLOW creationDate=long;
			END;
			""";

	public String getSyntax() {
		return SYNTAX;
	}

	/**
	 * parse a SplitsTree6 block
	 */
	public void parse(NexusStreamParser np, SplitsTree6Block splitsTree6Block) throws IOException {
		splitsTree6Block.clear();

		np.matchBeginBlock(SplitsTree6Block.BLOCK_NAME);

		np.matchIgnoreCase("DIMENSIONS nDataNodes=");
		splitsTree6Block.setOptionNumberOfDataNodes(np.getInt());
		np.matchIgnoreCase("nAlgorithms=");
		splitsTree6Block.setOptionNumberOfAlgorithms(np.getInt());
		np.matchIgnoreCase(";");

		np.matchIgnoreCase("PROGRAM version=");
		splitsTree6Block.setOptionVersion(np.getWordRespectCase());
		np.matchIgnoreCase(";");

		np.matchIgnoreCase("WORKFLOW creationDate=");
		splitsTree6Block.setOptionCreationDate(np.getLong());
		np.matchIgnoreCase(";");

		np.matchEndBlock();
	}

	/**
	 * is the parser at the beginning of a block that this class can parse?
	 *
	 * @return true, if can parse from here
	 */
	public boolean atBeginOfBlock(NexusStreamParser np) {
		return np.peekMatchIgnoreCase("begin " + SplitsTree6Block.BLOCK_NAME + ";");
	}
}
