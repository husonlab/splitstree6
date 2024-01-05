/*
 * FastAWriter.java Copyright (C) 2024 Daniel H. Huson
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

import jloda.seq.FastA;
import splitstree6.data.SplitsBlock;
import splitstree6.data.TaxaBlock;

import java.io.IOException;
import java.io.Writer;

/**
 * write splits in FastA format
 */
public class FastAWriter extends SplitsWriterBase {

	public FastAWriter() {
		setFileExtensions("fasta", "fa", "binary");
	}

	public void write(Writer w, TaxaBlock taxa, SplitsBlock splits) throws IOException {
		final var fasta = new FastA();
		for (var t = 1; t <= taxa.getNtax(); t++) {
			var seq = new char[splits.getNsplits()];
			for (var s = 1; s <= splits.getNsplits(); s++) {
				if (splits.get(s).getA().get(t))
					seq[s - 1] = '1';
				else
					seq[s - 1] = '0';
			}
			fasta.add(taxa.getLabel(t), String.valueOf(seq));
		}
		fasta.write(w);
	}
}
