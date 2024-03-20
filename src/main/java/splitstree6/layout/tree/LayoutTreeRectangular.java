/*
 *  LayoutTreeRectangular.java Copyright (C) 2024 Daniel H. Huson
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

package splitstree6.layout.tree;

import javafx.geometry.Point2D;
import jloda.graph.Node;
import jloda.graph.NodeArray;
import jloda.graph.NodeDoubleArray;
import jloda.phylo.PhyloTree;
import jloda.util.IteratorUtils;
import jloda.util.ProgramProperties;

import java.util.LinkedList;

/**
 * computes the rectangular layout for a rooted tree or network
 * Daniel Huson, 12.2021
 */
public class LayoutTreeRectangular {
	/**
	 * compute rectangular tree or network layout
	 *
	 * @param tree tree
	 * @return node to point map
	 */
	public static NodeArray<Point2D> apply(PhyloTree tree, boolean toScale, HeightAndAngles.Averaging averaging) {
		final NodeArray<Point2D> nodePointMap = tree.newNodeArray();
		nodePointMap.put(tree.getRoot(), new Point2D(0, 0));
		try (var yCoord = tree.newNodeDoubleArray()) {
			HeightAndAngles.apply(tree, yCoord, averaging);
			if (toScale) {
				setCoordinatesPhylogram(tree, yCoord, nodePointMap);
			} else {
				try (var levels = tree.newNodeIntArray()) {
					// compute levels: max length of path from node to a leaf
					tree.postorderTraversal(v -> {
						if (v.isLeaf())
							levels.put(v, 0);
						else {
							var level = 0;
							if (true) {
								for (var e : v.outEdges()) {
									var w = e.getTarget();

									if (tree.isTransferEdge(e))
										level = Math.max(level, levels.get(w) - 1);
									else
										level = Math.max(level, levels.get(w));
								}
							} else {
								for (var w : v.children()) {
									level = Math.max(level, levels.get(w));
								}
							}
							var prev = (levels.get(v) != null ? levels.get(v) : 0);
							if (level + 1 > prev)
								levels.set(v, level + 1);
						}
					});
					if (false)
						System.err.println(tree.toBracketString(false));
					for (var v : tree.nodes()) {
						nodePointMap.put(v, new Point2D(-levels.get(v), yCoord.get(v)));
					}
				}
			}
		}
		return nodePointMap;
	}

	/**
	 * This code assumes that all edges are directed away from the root.
	 */
	public static void setCoordinatesPhylogram(PhyloTree tree, NodeDoubleArray yCoord, NodeArray<Point2D> nodePointMap) {
		var percentOffset = ProgramProperties.get("ReticulationOffsetPercent", 50.0);

		var averageWeight = tree.edgeStream().mapToDouble(tree::getWeight).average().orElse(1);
		var smallOffsetForReticulateEdge = (percentOffset / 100.0) * averageWeight;

		var rootHeight = yCoord.get(tree.getRoot());

		try (var assigned = tree.newNodeSet()) {
			// assign coordinates:
			var queue = new LinkedList<Node>();
			queue.add(tree.getRoot());
			while (!queue.isEmpty()) // breath-first assignment
			{
				var w = queue.remove(0); // pop
				var ok = true;
				if (w.getInDegree() == 1) // has regular in edge
				{
					var e = w.getFirstInEdge();
					var v = e.getSource();
					var location = nodePointMap.get(v);

					if (!assigned.contains(v)) // can't process yet
					{
						ok = false;
					} else {
						var height = yCoord.get(e.getTarget());
						var u = e.getTarget();
						nodePointMap.put(u, new Point2D(location.getX() + tree.getWeight(e), height));
						assigned.add(u);
					}
				} else if (w.getInDegree() > 1) // all in edges are 'blue' edges
				{
					var x = Double.NEGATIVE_INFINITY;
					for (var f : w.inEdges()) {
						var u = f.getSource();
						var location = nodePointMap.get(u);
						if (location == null) {
							ok = false;
						} else {
							x = Math.max(x, location.getX());
						}
					}
					if (ok && x > Double.NEGATIVE_INFINITY) {
						x += smallOffsetForReticulateEdge;
						nodePointMap.put(w, new Point2D(x, yCoord.get(w)));
						assigned.add(w);
					}
				} else  // is root node
				{
					nodePointMap.put(w, new Point2D(0, rootHeight));
					assigned.add(w);
				}

				if (ok)  // add children to end of queue:
					queue.addAll(IteratorUtils.asList(w.children()));
				else  // process this node again later
					queue.add(w);
			}
		}
	}
}

