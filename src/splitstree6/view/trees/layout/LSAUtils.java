/*
 *  LSAUtils.java Copyright (C) 2021 Daniel H. Huson
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

package splitstree6.view.trees.layout;

import jloda.graph.Node;
import jloda.graph.NodeArray;
import jloda.phylo.PhyloTree;
import jloda.util.IteratorUtils;
import jloda.util.Pair;

import java.util.*;

/**
 * utilities for laying out a rooted network based on its LSA tree
 * Daniel Huson, 12.2021
 */
public class LSAUtils {
	/**
	 * computes the height for any node that is a leaf only in the LSA tree, but not in the network
	 *
	 * @param tree
	 * @param tax2pos
	 * @return height
	 */
	public static Map<Node, Double> computeHeightForLSALeaves(PhyloTree tree, int[] tax2pos) {
		if (!tree.isReticulated()) {
			return Collections.emptyMap();
		} else {
			var leafOrder = new ArrayList<Pair<Node, Integer>>();
			try (NodeArray<Integer> nodeHighPosMap = tree.newNodeArray()) {
				jloda.phylo.LSAUtils.postorderTraversalLSA(tree, tree.getRoot(), v -> {
					if (v.isLeaf()) {
						var pos = IteratorUtils.asStream(tree.getTaxa(v)).mapToInt(t -> tax2pos[t]).max().orElse(0);
						nodeHighPosMap.put(v, pos);
					} else if (!tree.isLsaLeaf(v)) {
						var pos = IteratorUtils.asStream(tree.lsaChildren(v)).filter(nodeHighPosMap::containsKey).mapToInt(nodeHighPosMap::get).max().orElse(0);
						nodeHighPosMap.put(v, pos);
					}
				});
				visitRec(tree, tree.getRoot(), nodeHighPosMap, leafOrder);
			}

			var lsaLeafHeightMap = new HashMap<Node, Double>();

			var a = 0;
			while (true) {
				// skip all proper leaves:
				while (a < leafOrder.size() && leafOrder.get(a).getSecond() != null) {
					a++;
				}
				if (a == leafOrder.size())
					break;
				// now a points to an LSA leaf
				// find end of run of LSA leaves
				var b = a;
				while (b < leafOrder.size() && leafOrder.get(b).getSecond() == null) {
					b++;
				}
				var numberConsecutiveLSALeaves = (b - a);
				var height = (a == 0 ? 0.0 : leafOrder.get(a - 1).getSecond());
				for (var c = a; c < b; c++) {
					height += 1.0 / (numberConsecutiveLSALeaves + 1.0);
					lsaLeafHeightMap.put(leafOrder.get(c).getFirst(), height);
				}
				a = b;
			}
			return lsaLeafHeightMap;
		}
	}

	/**
	 * computes the angle for any node that is a leaf only in the LSA tree, but not in the network
	 *
	 * @param tree
	 * @param tax2pos
	 * @return height
	 */
	public static Map<Node, Double> computeAngleForLSALeaves(PhyloTree tree, int[] tax2pos, double delta) {
		var lsaValueMap = computeHeightForLSALeaves(tree, tax2pos);
		lsaValueMap.replaceAll((v, value) -> delta * value);
		return lsaValueMap;
	}


	private static void visitRec(PhyloTree tree, Node v, NodeArray<Integer> nodeHighPosMap, ArrayList<Pair<Node, Integer>> leafOrder) {
		if (v.isLeaf()) {
			leafOrder.add(new Pair<>(v, nodeHighPosMap.get(v)));
		} else if (tree.isLsaLeaf(v)) {
			leafOrder.add(new Pair<>(v, null));
		} else {
			var lsaLeaves = new ArrayList<Node>();
			var otherChildren = new ArrayList<Node>();
			for (var w : tree.lsaChildren(v)) {
				if (!w.isLeaf() && (tree.isLsaLeaf(w) || IteratorUtils.asStream(tree.lsaChildren(w)).allMatch(tree::isLsaLeaf)))
					lsaLeaves.add(w);
				else
					otherChildren.add(w);
			}
			otherChildren.sort(Comparator.comparing(nodeHighPosMap::get));

			for (var w : otherChildren) {
				visitRec(tree, w, nodeHighPosMap, leafOrder);
				if (lsaLeaves.size() > 0) {
					for (var u : lsaLeaves) {
						visitRec(tree, u, nodeHighPosMap, leafOrder);
					}
					lsaLeaves.clear();
				}
			}
			if (lsaLeaves.size() > 0) {
				for (var u : lsaLeaves) {
					visitRec(tree, u, nodeHighPosMap, leafOrder);
				}
				lsaLeaves.clear();
			}
		}
	}
}
