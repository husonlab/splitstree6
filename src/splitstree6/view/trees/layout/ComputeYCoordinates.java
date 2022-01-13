/*
 *  ComputeYCoordinates.java Copyright (C) 2022 Daniel H. Huson
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
import jloda.graph.NodeDoubleArray;
import jloda.phylo.PhyloTree;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

/**
 * computes the y-coordinates for the rectangular layout
 */
public class ComputeYCoordinates {

	/**
	 * compute the y-coordinates for the parallel view
	 *
	 * @param tree
	 * @return y-coordinates
	 */
	public static void apply(PhyloTree tree, NodeDoubleArray nodeYCoordinateMap) {
		apply(tree, tree.getRoot(), nodeYCoordinateMap);
	}

	/**
	 * compute the y-coordinates for the parallel view
	 */
	public static void apply(PhyloTree tree, Node root, NodeDoubleArray nodeYCoordinateMap) {
		var leafOrder = new LinkedList<Node>();
		computeYCoordinateOfLeavesRec(tree, root, 0, nodeYCoordinateMap, leafOrder);
		if (tree.getNumberReticulateEdges() > 0)
			fixSpacing(leafOrder, nodeYCoordinateMap);
		computeYCoordinateOfInternalRec(tree, root, nodeYCoordinateMap);
	}

	public static void computeAngles(PhyloTree tree, NodeDoubleArray nodeAngleMap) {
		ComputeYCoordinates.apply(tree, nodeAngleMap);
		var max = tree.nodeStream().filter(tree::isLsaLeaf).mapToDouble(nodeAngleMap::get).max().orElse(0);
		var factor = 360.0 / max;
		for (var v : nodeAngleMap.keySet()) {
			nodeAngleMap.put(v, nodeAngleMap.get(v) * factor);
		}
	}

	/**
	 * recursively compute the y coordinate for a parallel or triangular diagram
	 *
	 * @param v
	 * @return index of last leaf
	 */
	private static int computeYCoordinateOfLeavesRec(PhyloTree tree, Node v, int leafNumber, NodeDoubleArray yCoord, List<Node> nodeOrder) {
		if (v.isLeaf() || tree.isLsaLeaf(v)) {
			// String taxonName = tree.getLabel(v);
			yCoord.put(v, (double) ++leafNumber);
			nodeOrder.add(v);
		} else {
			for (Node w : tree.lsaChildren(v)) {
				leafNumber = computeYCoordinateOfLeavesRec(tree, w, leafNumber, yCoord, nodeOrder);
			}
		}
		return leafNumber;
	}


	/**
	 * recursively compute the y coordinate for the internal nodes of a parallel diagram
	 *
	 * @param v
	 * @param yCoord
	 */
	private static void computeYCoordinateOfInternalRec(PhyloTree tree, Node v, NodeDoubleArray yCoord) {
		if (v.getOutDegree() > 0) {
			double first = Double.NEGATIVE_INFINITY;
			double last = Double.NEGATIVE_INFINITY;

			for (Node w : tree.lsaChildren(v)) {
				Double y = yCoord.get(w);
				if (y == null) {
					computeYCoordinateOfInternalRec(tree, w, yCoord);
					y = yCoord.get(w);
				}
				last = y;
				if (first == Double.NEGATIVE_INFINITY)
					first = last;
			}
			yCoord.put(v, 0.5 * (last + first));
		}
	}

	/**
	 * fix spacing so that space between any two true leaves is 1
	 *
	 * @param leafOrder
	 */
	private static void fixSpacing(Collection<Node> leafOrder, NodeDoubleArray yCoord) {
		var nodes = leafOrder.toArray(new Node[0]);
		double leafPos = 0;
		for (int lastLeaf = -1; lastLeaf < nodes.length; ) {
			int nextLeaf = lastLeaf + 1;
			while (nextLeaf < nodes.length && nodes[nextLeaf].getOutDegree() != 0)
				nextLeaf++;
			// assign fractional positions to intermediate nodes
			int count = (nextLeaf - lastLeaf) - 1;
			if (count > 0) {
				double add = 1.0 / (count + 1); // if odd, use +2 to avoid the middle
				double value = leafPos;
				for (int i = lastLeaf + 1; i < nextLeaf; i++) {
					value += add;
					yCoord.put(nodes[i], value);
				}
			}
			// assign whole positions to actual leaves:
			if (nextLeaf < nodes.length) {
				yCoord.put(nodes[nextLeaf], ++leafPos);
			}
			lastLeaf = nextLeaf;
		}
	}
}
