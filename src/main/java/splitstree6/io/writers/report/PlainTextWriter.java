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

package splitstree6.io.writers.report;

import splitstree6.data.ReportBlock;
import splitstree6.data.TaxaBlock;

import java.io.IOException;
import java.io.Writer;

/**
 * write as text
 * Daniel Huson, 4.2022
 */
public class PlainTextWriter extends TextWriterBase {
	public PlainTextWriter() {
		setFileExtensions("txt", "text");
	}

	@Override
	public void write(Writer w, TaxaBlock taxa, ReportBlock block) throws IOException {
		for (var line : block.getLines()) {
			w.write(line + "\n");
		}
		w.flush();
	}
}
