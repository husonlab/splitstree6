/*
 * NewickWriter.java Copyright (C) 2023 Daniel H. Huson
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

package splitstree6.io.writers.trees;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import jloda.phylo.NewickIO;
import splitstree6.data.TaxaBlock;
import splitstree6.data.TreesBlock;

import java.io.IOException;
import java.io.Writer;

/**
 * write trees in Newick format
 */
public class NewickWriter extends TreesWriterBase {
	private final BooleanProperty optionEdgeWeights = new SimpleBooleanProperty(this, "optionEdgeWeights", true);

	public NewickWriter() {
		setFileExtensions("tree", "tre", "trees", "new", "nwk", "treefile");
	}

	@Override
	public void write(Writer w, TaxaBlock taxaBlock, TreesBlock trees) throws IOException {
		var newickIO = new NewickIO();

		if (trees != null) {
			for (var i = 1; i <= trees.getNTrees(); i++) {
				var tree = trees.getTree(i);
				newickIO.setNewickNodeCommentSupplier(v -> (v == tree.getRoot() && tree.getName() != null && !tree.getName().startsWith("tree-") ? "&&NHX:GN=" + tree.getName() : null));
				w.write(newickIO.toBracketString(tree, isOptionEdgeWeights()) + ";\n");
			}
		}
		w.flush();
	}

	public boolean isOptionEdgeWeights() {
		return optionEdgeWeights.get();
	}

	public BooleanProperty optionEdgeWeightsProperty() {
		return optionEdgeWeights;
	}
}
