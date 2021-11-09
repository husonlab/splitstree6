/*
 *  ShowTreesConsole.java Copyright (C) 2021 Daniel H. Huson
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

package splitstree6.algorithms.trees.trees2view;

import jloda.util.progress.ProgressListener;
import splitstree6.data.TaxaBlock;
import splitstree6.data.TreesBlock;
import splitstree6.data.ViewBlock;
import splitstree6.io.writers.trees.NewickWriter;
import splitstree6.methods.IgnoredInMethodsText;

import java.io.IOException;
import java.io.StringWriter;

public class ShowTreesConsole extends Trees2View implements IgnoredInMethodsText {
	@Override
	public void compute(ProgressListener progress, TaxaBlock taxaBlock, TreesBlock treeData, ViewBlock outputData) throws IOException {
		try (var w = new StringWriter()) {
			w.write(treeData.getName() + ":\n");
			var writer = new NewickWriter();
			writer.write(w, taxaBlock, treeData);
			System.out.println(w);
		}
	}

	@Override
	public String getCitation() {
		return null;
	}
}
