/*
 * AlgorithmNexusOutput.java Copyright (C) 2021. Daniel H. Huson
 *
 * (Some code written by other authors, as named in code.)
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
 *
 */

package splitstree6.io.nexus;

import splitstree6.methods.Option;
import splitstree6.methods.OptionValueType;
import splitstree6.workflow.Algorithm;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;

/**
 * algorithm nexus output
 * Daniel Huson, 2.2018
 */
public class AlgorithmNexusOutput extends NexusIOBase {
	/**
	 * write a description of the algorithm
	 *
	 * @param w
	 * @throws IOException
	 */
	public void write(Writer w, Algorithm algorithm) throws IOException {
		w.write("\nBEGIN ALGORITHM;\n");
		writeTitleAndLink(w);
		w.write("ALGORITHM " + algorithm.getName() + ";\n");

		final ArrayList<Option> options = new ArrayList<>(Option.getAllOptions(algorithm));
		if (options.size() > 0) {
			w.write("OPTIONS\n");
			boolean first = true;
			for (Option option : options) {
				final String valueString = OptionValueType.toStringType(option.getOptionValueType(), option.getProperty().getValue());
				if (valueString.length() > 0) {
					if (first)
						first = false;
					else
						w.write(",\n");
					w.write("\t" + option.getName() + " = " + valueString);
				}
			}
			w.write(";\n");
		}
		w.write("END; [ALGORITHM]\n");
	}
}
