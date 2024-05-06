/*
 *  AutumnUtilities.java Copyright (C) 2024 Daniel H. Huson
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

package splitstree6.compute.autumn;

import jloda.phylo.PhyloTree;
import splitstree6.data.TaxaBlock;

import java.io.IOException;
import java.util.BitSet;

public class AutumnUtilities {
	/**
	 * extract all taxa from the given tree and add all new ones to the set of all taxa
	 *
	 * @return the bits of all taxa found in the tree
	 */
	public static BitSet extractTaxa(int i, PhyloTree tree, TaxaBlock allTaxa) throws IOException {
		var taxaBits = new BitSet();
		for (var v : tree.nodes()) {
			var name = tree.getLabel(v);
			if (name != null && name.length() > 0 && !PhyloTree.isBootstrapValue(name)) {
				var index = allTaxa.indexOf(name);
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
