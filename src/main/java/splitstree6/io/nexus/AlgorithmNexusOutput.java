/*
 * AlgorithmNexusOutput.java Copyright (C) 2023 Daniel H. Huson
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

import splitstree6.options.OptionIO;
import splitstree6.workflow.Algorithm;

import java.io.IOException;
import java.io.Writer;

/**
 * algorithm nexus output
 * Daniel Huson, 2.2018
 */
public class AlgorithmNexusOutput extends NexusIOBase {
	/**
	 * write a description of the algorithm
	 */
	public void write(Writer w, Algorithm algorithm) throws IOException {
		w.write("\nBEGIN ALGORITHM;\n");
		writeTitleAndLink(w);
		w.write("NAME '" + algorithm.getName() + "';\n");
		OptionIO.writeOptions(w, algorithm);
		w.write("END; [ALGORITHM]\n");
	}
}
