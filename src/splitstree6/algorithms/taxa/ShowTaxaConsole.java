/*
 *  ShowTaxaConsole.java Copyright (C) 2021 Daniel H. Huson
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

package splitstree6.algorithms.taxa;

import jloda.util.progress.ProgressListener;
import splitstree6.data.SinkBlock;
import splitstree6.data.TaxaBlock;
import splitstree6.io.writers.taxa.TabbedTextWriter;
import splitstree6.methods.IgnoredInMethodsText;
import splitstree6.workflow.Algorithm;
import splitstree6.workflow.DataBlock;

import java.io.IOException;
import java.io.StringWriter;

public class ShowTaxaConsole extends Algorithm<DataBlock, SinkBlock> implements IgnoredInMethodsText {
	public ShowTaxaConsole() {
		super(DataBlock.class, SinkBlock.class);
	}

	@Override
	public void compute(ProgressListener progress, TaxaBlock taxaBlock, DataBlock inputData, SinkBlock outputData) throws IOException {
		try (var w = new StringWriter()) {
			w.write(taxaBlock.getName() + ":\n");
			var writer = new TabbedTextWriter();
			writer.write(w, taxaBlock, taxaBlock);
			System.out.println(w);
		}
	}


	@Override
	public String getCitation() {
		return null;
	}
}
