/*
 * ClustalExporter.java Copyright (C) 2021. Daniel H. Huson
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

package splitstree6.io.writers.characters;

import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import splitstree6.data.CharactersBlock;
import splitstree6.data.TaxaBlock;

import java.io.IOException;
import java.io.Writer;

public class ClustalWriter extends CharactersWriter {

	public ClustalWriter() {
		setFileExtensions("aln", "clustal");
	}

	private final IntegerProperty optionLineLength = new SimpleIntegerProperty(40);

	public void write(Writer w, TaxaBlock taxa, CharactersBlock characters) throws IOException {
		w.write("CLUSTAL multiple sequence alignment (Produced by SplitsTree 6)\n\n\n");

		var ntax = taxa.getNtax();
		var nchar = characters.getNchar();

		var lineLength = getOptionLineLength();
		int iterations;
		if (nchar % getOptionLineLength() == 0)
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

	public int getOptionLineLength() {
		return optionLineLength.get();
	}

	public IntegerProperty optionLineLengthProperty() {
		return optionLineLength;
	}

	public void setOptionLineLength(int optionLineLength) {
		this.optionLineLength.set(optionLineLength);
	}
}
