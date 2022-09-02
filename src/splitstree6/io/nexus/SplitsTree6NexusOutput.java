/*
 * SplitsTree6NexusOutput.java Copyright (C) 2022 Daniel H. Huson
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


import splitstree6.data.SplitsTree6Block;

import java.io.IOException;
import java.io.Writer;
import java.util.Date;

/**
 * writes the splitstree6 block in nexus format
 * Daniel Huson, 10.2021
 */
public class SplitsTree6NexusOutput {
	/**
	 * writes the splitstree6 block in nexus format
	 */
	public void write(Writer w, SplitsTree6Block splitsTreeBlock) throws IOException {
		w.write("\nBEGIN " + SplitsTree6Block.BLOCK_NAME + ";\n");
		w.write("DIMENSIONS nDataNodes=" + splitsTreeBlock.getOptionNumberOfDataNodes()
				+ " nAlgorithms=" + splitsTreeBlock.getOptionNumberOfAlgorithms() + ";\n");
		w.write("PROGRAM version='" + splitsTreeBlock.getOptionVersion() + "';\n");
		w.write(String.format("WORKFLOW creationDate='%s'; [%s]\n", splitsTreeBlock.getOptionCreationDate(),
				new Date(splitsTreeBlock.getOptionCreationDate())));
		w.write("END; [" + SplitsTree6Block.BLOCK_NAME + "]\n");
	}
}
