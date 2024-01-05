/*
 * PlainTextWriter.java Copyright (C) 2024 Daniel H. Huson
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

package splitstree6.io.writers.splits;

import splitstree6.data.SplitsBlock;
import splitstree6.data.TaxaBlock;

import java.io.IOException;
import java.io.Writer;

/**
 * write as text
 * Daniel Huson, 11.2021
 */
public class PlainTextWriter extends SplitsWriterBase {
	public PlainTextWriter() {
		setFileExtensions("txt", "text");
	}

	@Override
	public void write(Writer w, TaxaBlock taxa, SplitsBlock splits) throws IOException {
		w.write("Splits\tWeights");

		for (var i = 1; i <= taxa.getNtax(); i++)
			w.write("\t" + taxa.getLabel(i));
		w.write("\n");

		//Now we loop through the splits, one split per row.
		final var ntax = taxa.getNtax();
		final var nsplits = splits.getNsplits();
		for (int s = 1; s <= nsplits; s++) {

			//Split number
			w.write(Integer.toString(s));
			w.write("\t" + splits.get(s).getWeight());
			var A = splits.get(s).getA();
			for (int j = 1; j <= ntax; j++) {
				char ch = A.get(j) ? '1' : '0';
				w.write("\t" + ch);
			}

			w.write("\n");
		}
		w.write("\n");
	}
}
