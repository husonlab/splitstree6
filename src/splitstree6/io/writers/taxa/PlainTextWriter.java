/*
 * PlainTextWriter.java Copyright (C) 2022 Daniel H. Huson
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

package splitstree6.io.writers.taxa;

import splitstree6.data.TaxaBlock;

import java.io.IOException;
import java.io.Writer;

/**
 * write as text
 * Daniel Huson, 11.2021
 */
public class PlainTextWriter extends TaxaWriterBase {
	public PlainTextWriter() {
		setFileExtensions("txt", "text");
	}

	@Override
	public void write(Writer w, TaxaBlock ignored, TaxaBlock taxa) throws IOException {
		w.write("Taxa\n");
		for (int i = 1; i <= taxa.getNtax(); i++) {
			w.write(i + "\t" + taxa.getLabel(i) + "\n");
		}
		w.write("\n");
		w.flush();
	}
}
