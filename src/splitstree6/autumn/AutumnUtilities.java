/*
 * AutumnUtilities.java Copyright (C) 2022 Daniel H. Huson
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

package splitstree6.autumn;

import jloda.graph.Node;
import jloda.phylo.PhyloTree;
import splitstree6.data.TaxaBlock;

import java.io.IOException;
import java.util.BitSet;

public class AutumnUtilities {
	/**
	 * extract all taxa from the given tree and add all new ones to the set of all taxa
	 *
	 * @param i
	 * @param tree
	 * @param allTaxa
	 * @return the bits of all taxa found in the tree
	 */
	public static BitSet extractTaxa(int i, PhyloTree tree, TaxaBlock allTaxa) throws IOException {
		BitSet taxaBits = new BitSet();
		for (Node v = tree.getFirstNode(); v != null; v = v.getNext()) {
			String name = tree.getLabel(v);
			if (name != null && name.length() > 0 && !PhyloTree.isBootstrapValue(name)) {
				int index = allTaxa.indexOf(name);
				if (index == -1) {
					allTaxa.addTaxonByName(name);
					index = allTaxa.indexOf(name);
				}
				if (taxaBits.get(index))
					throw new IOException("tree[" + i + "]: contains multiple copies of label: " + name);
				else
					taxaBits.set(index);
			} else if (v.getOutDegree() == 0)
				throw new IOException("tree [" + i + "]: leaf has invalid label: " + name);
		}
		return taxaBits;
	}
}
