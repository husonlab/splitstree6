/*
 *  PlainTextWriter.java Copyright (C) 2024 Daniel H. Huson
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

package splitstree6.io.writers.sets;

import splitstree6.data.SetsBlock;
import splitstree6.data.TaxaBlock;
import splitstree6.io.nexus.SetsNexusOutput;

import java.io.IOException;
import java.io.Writer;

/**
 * write as text
 * Daniel Huson, 4.2022
 */
public class PlainTextWriter extends SetsWriterBase {
	public PlainTextWriter() {
		setFileExtensions("txt", "text");
	}

	@Override
	public void write(Writer w, TaxaBlock taxa, SetsBlock block) throws IOException {
		w.write("SETS\n");
		for (var taxSet : block.getTaxSets()) {
			w.write("\tTaxSet %s = %s \n".formatted(taxSet.getName(), SetsNexusOutput.getRange(taxSet, false)));
		}
		for (var charSet : block.getCharSets()) {
			w.write("\tCharSet %s = %s \n".formatted(charSet.getName(), SetsNexusOutput.getRange(charSet, true)));
		}
		w.flush();
	}
}
