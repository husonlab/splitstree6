/*
 *  ViewNexusOutput.java Copyright (C) 2022 Daniel H. Huson
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

package splitstree6.io.nexus;

import splitstree6.data.TaxaBlock;
import splitstree6.data.ViewBlock;
import splitstree6.options.OptionIO;

import java.io.IOException;
import java.io.Writer;

/**
 * view nexus output
 * Daniel Huson, 10.2021
 */
public class ViewNexusOutput extends NexusIOBase {
	/**
	 * view nexus output
	 */
	public void write(Writer w, TaxaBlock taxaBlock, ViewBlock viewBlock) throws IOException {
		w.write("\nBEGIN VIEW;\n");
		writeTitleAndLink(w);
		w.write("NAME '" + viewBlock.getName() + "';\n");
		w.write("INPUT '" + viewBlock.getInputBlockName() + "';\n");
		OptionIO.writeOptions(w, viewBlock.getView());
		w.write("END; [VIEW]\n");
	}
}
