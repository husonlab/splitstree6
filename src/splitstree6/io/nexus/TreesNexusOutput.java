/*
 * TreesNexusOutput.java Copyright (C) 2021. Daniel H. Huson
 *
 * (Some code written by other authors, as named in code.)
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
 *
 */

package splitstree6.io.nexus;

import jloda.graph.Node;
import jloda.phylo.PhyloTree;
import jloda.util.StringUtils;
import splitstree6.data.TaxaBlock;
import splitstree6.data.TreesBlock;

import java.io.IOException;
import java.io.Writer;
import java.util.function.Function;

/**
 * tree nexus output
 * Daniel Huson, 2.2018
 */
public class TreesNexusOutput extends NexusIOBase implements INexusOutput<TreesBlock> {
	/**
	 * write a block in nexus format
	 */
	@Override
	public void write(Writer w, TaxaBlock taxaBlock, TreesBlock treesBlock) throws IOException {
		final var format = treesBlock.getFormat();

		w.write("\nBEGIN TREES;\n");
		writeTitleAndLink(w);
		if (treesBlock.size() > 0)
			w.write(String.format("[Number of trees: %,d]\n", treesBlock.getNTrees()));
		if (treesBlock.isPartial() || treesBlock.isRooted()) {
			w.write("PROPERTIES");
			w.write(" partialTrees=" + (treesBlock.isPartial() ? "yes" : "no"));
			w.write(" rooted=" + (treesBlock.isRooted() ? "yes" : "no"));
			if (treesBlock.isReticulated())
				w.write(" reticulated=yes");
			w.write(";\n");
		}

		final Function<Node, String> labeler;
		if (format.isOptionTranslate()) {
			labeler = computeLabelByNumber();
			w.write("TRANSLATE\n");

			for (var t = 1; t <= taxaBlock.getNtax(); t++) {
				w.write("\t" + t + " '" + taxaBlock.getLabel(t) + "',\n");
			}
			w.write(";\n");
		} else
			labeler = computeLabelByName(taxaBlock);

		w.write("[TREES]\n");
		int t = 1;
		for (var tree : treesBlock.getTrees()) {
			final String name = (tree.getName() != null && tree.getName().length() > 0 ? tree.getName() : "t" + t);
			w.write("\t\t[" + (t++) + "] tree '" + name + "'=" + getFlags(tree) + " ");
			tree.write(w, format.isOptionWeights(), labeler);
			w.write(";\n");
		}
		w.write("END; [TREES]\n");
	}

	/**
	 * compute label-by-number labeler
	 */
	private static Function<Node, String> computeLabelByNumber() {
		return v -> {
			final var tree = (PhyloTree) v.getOwner();
			if (tree.getNumberOfTaxa(v) > 0)
				return StringUtils.toString(tree.getTaxa(v), ",");
			else
				return null;
		};
	}

	/**
	 * compute label-by-name labeler
	 */
	private static Function<Node, String> computeLabelByName(TaxaBlock taxonBlock) {
		return v -> {
			final var tree = (PhyloTree) v.getOwner();
			if (tree.getNumberOfTaxa(v) > 0) {
				final StringBuilder buf = new StringBuilder();
				for (int taxId : tree.getTaxa(v)) {
					if (buf.length() > 0)
						buf.append(",");
					else
						buf.append(taxonBlock.getLabel(taxId));
				}
				return buf.toString();
			} else
				return null;
		};
	}

	/**
	 * Returns the nexus flag [&R] indicating whether the tree should be considered
	 * as rooted
	 *
	 * @return String  Returns [&R] if rooted, and "" otherwise.
	 */
	private static String getFlags(PhyloTree tree) {
		if (tree.getRoot() != null)
			return "[&R]";
		else
			return "";
	}

}
