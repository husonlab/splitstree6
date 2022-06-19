/*
 * ClustalWriter.java Copyright (C) 2022 Daniel H. Huson
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

package splitstree6.io.writers.characters;

import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import splitstree6.data.CharactersBlock;
import splitstree6.data.TaxaBlock;

import java.io.IOException;
import java.io.Writer;

/**
 * writes data in clustal format
 * Daniel Huson, 11.2021
 */
public class ClustalWriter extends CharactersWriterBase {
	private final IntegerProperty optionLineLength = new SimpleIntegerProperty(this, "optionLineLength", 40);

	public ClustalWriter() {
		setFileExtensions("aln", "clustal");
	}

	public void write(Writer w, TaxaBlock taxa, CharactersBlock characters) throws IOException {
		w.write("CLUSTAL multiple sequence alignment (written by SplitsTree 6)\n\n\n");

		final var ntax = taxa.getNtax();
		final var nchar = characters.getNchar();

		final var lineLength = optionLineLength.get();
		final int iterations;
		if (nchar % lineLength == 0)
			iterations = nchar / lineLength;
		else
			iterations = nchar / lineLength + 1;

		for (var i = 1; i <= iterations; i++) {
			var startIndex = lineLength * (i - 1) + 1;
			for (var t = 1; t <= ntax; t++) {
				var buf = new StringBuilder();
				int stopIndex = lineLength;
				for (int j = startIndex; j <= lineLength * i && j <= nchar; j++) {
					buf.append(characters.get(t, j));
					stopIndex = j;
				}
				w.write(taxa.get(t) + " \t" + buf.toString().toUpperCase() + " \t" + stopIndex + "\n");
			}
			w.write("\n");
		}
	}

	public IntegerProperty optionLineLengthProperty() {
		return optionLineLength;
	}
}
