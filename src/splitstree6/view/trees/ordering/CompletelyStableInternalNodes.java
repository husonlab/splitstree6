/*
 *  CompletelyStableInternalNodes.java Copyright (C) 2021 Daniel H. Huson
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

package splitstree6.view.trees.ordering;

import jloda.graph.Node;
import jloda.graph.NodeSet;
import jloda.phylo.PhyloTree;
import jloda.util.IteratorUtils;

import java.util.HashSet;
import java.util.Set;

/**
 * determines all completely stable nodes, which are nodes that lie on all paths to all of their children
 * Daniel Huson, 12.2021
 */
public class CompletelyStableInternalNodes {

	public static NodeSet apply(PhyloTree tree) {
		var result = tree.newNodeSet();
		computeAllCompletelyStableInternalRec(tree.getRoot(), new HashSet<>(), new HashSet<>(), result);
		return result;
	}

	private static void computeAllCompletelyStableInternalRec(Node v, Set<Node> below, Set<Node> parentsOfBelow, NodeSet result) {
		if (v.getOutDegree() == 0) {
			below.add(v);
			parentsOfBelow.addAll(IteratorUtils.asList(v.parents()));
		} else {
			var belowV = new HashSet<Node>();
			var parentsOfBelowV = new HashSet<Node>();

			for (var w : v.children()) {
				computeAllCompletelyStableInternalRec(w, belowV, parentsOfBelowV, result);
			}
			belowV.forEach(u -> parentsOfBelowV.addAll(IteratorUtils.asList(u.parents())));
			belowV.add(v);

			if (belowV.containsAll(parentsOfBelowV)) {
				result.add(v);
			}
			below.addAll(belowV);
			parentsOfBelow.addAll(parentsOfBelowV);
		}
	}
}
