/*
 *  NewickWriter.java Copyright (C) 2021 Daniel H. Huson
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

package splitstree6.io.writers.trees;

import splitstree6.data.TaxaBlock;
import splitstree6.data.TreesBlock;

import java.io.IOException;
import java.io.Writer;

public class NewickWriter extends TreesWriter {
	public NewickWriter() {
		setFileExtensions("tree", "tre", "trees", "new", "nwk", "treefile");
	}

	@Override
	public void write(Writer w, TaxaBlock taxaBlock, TreesBlock trees) throws IOException {
		if (trees != null) {
			for (int i = 0; i < trees.getNTrees(); i++) {
				w.write(trees.getTrees().get(i).toBracketString() + ";\n");
			}
		}
		w.flush();
	}
}
