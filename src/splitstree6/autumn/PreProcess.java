/*
 *  PreProcess.java Copyright (C) 2022 Daniel H. Huson
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
package splitstree6.autumn;


import jloda.graph.Graph;
import jloda.phylo.PhyloTree;
import jloda.util.Pair;
import splitstree6.data.TaxaBlock;

import java.io.IOException;
import java.util.BitSet;

/**
 * pre process input trees
 * Daniel Huson, 4.2011
 */
public class PreProcess {
	/**
	 * apply preprocessing to two trees
	 *
	 * @param tree1
	 * @param tree2
	 * @param allTaxa
	 * @return
	 * @throws IOException
	 */
	static public Pair<Root, Root> apply(PhyloTree tree1, PhyloTree tree2, TaxaBlock allTaxa) throws IOException {

		if (tree1.getRoot() == null || tree2.getRoot() == null) {
			throw new IOException("Pre-processing failed, at least one of the trees is empty or unrooted");
		}

		AutumnUtilities.extractTaxa(1, tree1, allTaxa);
		AutumnUtilities.extractTaxa(2, tree2, allTaxa);

		var root1 = Root.createACopy(new Graph(), tree1, allTaxa);
		root1.reorderSubTree();
		var root2 = Root.createACopy(new Graph(), tree2, allTaxa);
		root2.reorderSubTree();

		return new Pair<>(root1, root2);
	}

	/**
	 * apply preprocessing to one tree
	 *
	 * @param tree1
	 * @param allTaxa
	 * @param mustHaveSameTaxa
	 * @return
	 * @throws IOException
	 */
	static public Root apply(PhyloTree tree1, TaxaBlock allTaxa, boolean mustHaveSameTaxa) throws IOException {

		if (tree1.getRoot() == null) {
			throw new IOException("Pre-processing failed, tree is empty or unrooted");
		}

		BitSet taxa = allTaxa.getTaxaSet();

		BitSet taxa1 = AutumnUtilities.extractTaxa(1, tree1, allTaxa);
		if (mustHaveSameTaxa && taxa.cardinality() > 0 && !Cluster.contains(taxa, taxa1))
			throw new IOException("Pre-processing failed, trees has additional taxa");

		Root root1 = Root.createACopy(new Graph(), tree1, allTaxa);
		root1.reorderSubTree();
		return root1;
	}

}
