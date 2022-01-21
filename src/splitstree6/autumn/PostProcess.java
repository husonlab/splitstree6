/*
 *  PostProcess.java Copyright (C) 2022 Daniel H. Huson
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

import jloda.graph.Edge;
import jloda.graph.Node;
import jloda.phylo.PhyloTree;
import jloda.util.BitSetUtils;
import splitstree6.data.TaxaBlock;

import java.util.*;

/**
 * post process the rooted trees to get full trees
 * Daniel Huson, 4.2011
 */
public class PostProcess {

	/**
	 * returns full trees or networks for the given rooted trees or networks
	 *
	 * @param roots
	 * @param allTaxa
	 * @param showTaxonIds
	 * @return
	 */
	public static List<PhyloTree> apply(Root[] roots, TaxaBlock allTaxa, boolean showTaxonIds) {
		var list = new LinkedList<PhyloTree>();

		// showTaxonIds=false;

		for (var original : roots) {
			var tree = new PhyloTree();
			var treeRoot = tree.newNode();
			tree.setRoot(treeRoot);
			copyRec(original, tree.getRoot(), new HashMap<>(), allTaxa, showTaxonIds, tree);
			for (var e : tree.edges()) {
				if (e.getTarget().getInDegree() > 1) {
					tree.setReticulated(e, true);
					tree.setWeight(e, 0);
				}
			}
			if (treeRoot.getOutDegree() > 1) {
				Node newRoot = tree.newNode();
				tree.newEdge(newRoot, treeRoot);
				tree.setRoot(newRoot);
			}
			list.add(new PhyloTree(tree));
			// System.err.println("Tree: " + tree.toString());
		}
		return list;
	}

	/**
	 * copies the rooted tree datastructure to the phylotree datastructure
	 *
	 * @param vSrc
	 * @param vTar
	 * @param src2tar
	 * @param allTaxa
	 * @param showTaxonIds
	 * @param tree
	 */
	private static void copyRec(Root vSrc, Node vTar, Map<Node, Node> src2tar, TaxaBlock allTaxa, boolean showTaxonIds, PhyloTree tree) {
		if (vSrc.getOutDegree() == 0) { // is at a leaf, grab the taxon name
			var id = vSrc.getTaxa().nextSetBit(0);
			if (vSrc.getInDegree() > 1 && vTar.getOutDegree() == 0) {
				var tmp = vTar.getOwner().newNode();
				vTar.getOwner().newEdge(vTar, tmp);
				vTar = tmp;
			}
			tree.setLabel(vTar, allTaxa.getLabel(id));
		} else {
			for (var e1 : vSrc.outEdges()) {
				var wSrc = (Root) e1.getTarget();
				var wTar = src2tar.get(wSrc);
				Edge e2;
				if (wTar == null) {
					wTar = vTar.getOwner().newNode();
					e2 = vTar.getOwner().newEdge(vTar, wTar);
					src2tar.put(wSrc, wTar);
					copyRec(wSrc, wTar, src2tar, allTaxa, showTaxonIds, tree);
				} else {
					e2 = vTar.getOwner().newEdge(vTar, wTar);
				}
				if (wSrc.getInDegree() > 1) {
					if (e1.getInfo() == null)
						throw new RuntimeException("Unlabeled reticulate edge: " + e1);
					else {
						e2.getOwner().setLabel(e2, e1.getInfo().toString());
					}
				}
			}
		}
		if (showTaxonIds) {
			var buf = new StringBuilder();
			var all = new BitSet();
			all.or(vSrc.getTaxa());
			all.or(vSrc.getRemovedTaxa());
			for (var t : BitSetUtils.members(all)) {
				if (vSrc.getTaxa().get(t))
					buf.append("+").append(t);
				if (vSrc.getRemovedTaxa().get(t))
					buf.append("-").append(t);
			}
			if (tree.getLabel(vTar) == null)
				tree.setLabel(vTar, "{" + buf + "}");
			else
				tree.setLabel(vTar, tree.getLabel(vTar) + "{" + buf + "}");
		}
	}
}
