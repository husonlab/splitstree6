/*
 *  TextWriter.java Copyright (C) 2021 Daniel H. Huson
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

package splitstree6.io.writers.characters;

import splitstree6.data.CharactersBlock;
import splitstree6.data.TaxaBlock;

import java.io.IOException;
import java.io.Writer;

/**
 * writes characters in text format
 * Daniel Huson, 11.2021
 */
public class TextWriter extends CharactersWriterBase {
	public TextWriter() {
		setFileExtensions("text", "txt");
	}

	@Override
	public void write(Writer w, TaxaBlock taxa, CharactersBlock characters) throws IOException {
		w.write("Characters\n");
		for (int i = 1; i <= taxa.getNtax(); i++)
			w.write(taxa.getLabel(i) + "\t");
		w.write("\n");

		for (int j = 1; j <= characters.getNchar(); j++) {
			w.write(j + "");
			for (int i = 1; i <= taxa.getNtax(); i++) {
				w.write("\t" + characters.get(i, j));
			}
			w.write("\n");
		}

		w.write("\n");
		w.flush();

	}
}
