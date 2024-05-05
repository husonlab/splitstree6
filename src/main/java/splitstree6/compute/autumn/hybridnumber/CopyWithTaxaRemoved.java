/*
 *  CopyWithTaxaRemoved.java Copyright (C) 2024 Daniel H. Huson
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
package splitstree6.compute.autumn.hybridnumber;

import jloda.graph.Edge;
import jloda.graph.Graph;
import jloda.util.StringUtils;
import splitstree6.compute.autumn.Cluster;
import splitstree6.compute.autumn.Root;

import java.util.BitSet;

/**
 * copy a tree with given taxa removed
 * Daniel Huson, 5.2011
 */
public class CopyWithTaxaRemoved {
	/**
	 * creates a copy of the subtree below this node, with all given taxa removed
	 *
	 * @return copy with named taxa
	 */
	public static Root apply(Root v, BitSet taxa2remove) {
		if (v.getTaxa().equals(taxa2remove))
			return null; // removal of all taxa produces empty tree

		Root newRoot = new Root(new Graph());

		applyRec(v, newRoot, taxa2remove);
		newRoot.reorderSubTree();
		if (false) {
			try {
				newRoot.checkTree();
			} catch (RuntimeException ex) {
				System.err.println("Orig: " + v.toStringFullTreeX());
				System.err.println("To remove: " + StringUtils.toString(taxa2remove));
				System.err.println("New: " + newRoot.toStringFullTreeX());
				throw ex;
			}
		}
		return newRoot;
	}

	/**
	 * recursively makes a copy
	 */
	private static void applyRec(Root v1, Root v2, BitSet taxa2remove) {
		BitSet taxa = new BitSet();
		taxa.or(v1.getTaxa());
		taxa = Cluster.setminus(taxa, taxa2remove);
		v2.setTaxa(taxa);
		for (Edge e1 = v1.getFirstOutEdge(); e1 != null; e1 = v1.getNextOutEdge(e1)) {
			Root w1 = (Root) e1.getTarget();
			if (!taxa2remove.equals(w1.getTaxa())) {
				Root w2 = v2.newNode();
				v2.newEdge(v2, w2);
				applyRec(w1, w2, taxa2remove);
			}
		}
		// found leaf, if it was one of a pair, delete its sibling
		if (v2.getOutDegree() == 1) {
			if (v2.getInDegree() == 1) {
				v2.newEdge(v2.getFirstInEdge().getSource(), v2.getFirstOutEdge().getTarget());
				v2.deleteNode();
			} else // v2.getInDegree()==0
			{
				Root w2 = (Root) v2.getFirstOutEdge().getTarget();
				for (Edge e = w2.getFirstOutEdge(); e != null; e = w2.getNextOutEdge(e)) {
					Root u2 = (Root) e.getTarget();
					v2.newEdge(v2, u2);
				}
				w2.deleteNode();
			}
		}
	}

}
