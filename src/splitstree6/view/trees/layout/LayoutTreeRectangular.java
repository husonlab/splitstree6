/*
 *  Copyright (C) 2018. Daniel H. Huson
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

/*
 *  LayoutTreeRectangular.java Copyright (C) 2021 Daniel H. Huson
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

import javafx.geometry.Point2D;
import jloda.graph.*;
import jloda.phylo.LSAUtils;
import jloda.phylo.PhyloTree;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.Set;

/**
 * computes the rectangular layout for a rooted tree or network
 * Daniel Huson, 12.2021
 */
public class LayoutTreeRectangular {
	/**
	 * compute rectangular tree or network layout
	 *
	 * @param tree    tree
	 * @param toScale
	 * @return node to point map
	 */
	public static NodeArray<Point2D> apply(PhyloTree tree, boolean toScale) {
		final NodeArray<Point2D> nodePointMap = tree.newNodeArray();
		nodePointMap.put(tree.getRoot(), new Point2D(0, 0));
		try (var yCoord = tree.newNodeDoubleArray()) {
			ComputeYCoordinates.apply(tree, yCoord);
			if (toScale) {
				setCoordinatesPhylogram(tree, yCoord, nodePointMap);
			} else {
				try (var levels = computeLevels(tree)) {
					LSAUtils.preorderTraversalLSA(tree, tree.getRoot(), v -> {
						nodePointMap.put(v, new Point2D(-levels.get(v), yCoord.get(v)));
					});
				}
			}
		}
		return nodePointMap;
	}

	public static double computeAverageEdgeWeight(PhyloTree tree) {
		var weight = 0.0;
		var count = 0;
		for (var e : tree.edges()) {
			if (!tree.isReticulatedEdge(e)) {
				weight += Math.max(0, tree.getWeight(e));
				count++;
			}
		}
		return count == 0 ? 0 : weight / count;
	}

	/**
	 * assign rectangular phylogram coordinates. First must use cladogram code to set y coordinates!
	 * This code assumes that all edges are directed away from the root.
	 *
	 * @param tree
	 */
	public static void setCoordinatesPhylogram(PhyloTree tree, NodeDoubleArray yCoord, NodeArray<Point2D> nodePointMap) {
		// todo: these things need to be made user options
		var useWeights = true;
		var percentOffset = 0;

		var smallDistance = 5.0 / 100.0;
		if (useWeights) {
			var largestDistance = 0.0;
			for (Edge e = tree.getFirstEdge(); e != null; e = e.getNext())
				if (!tree.isReticulatedEdge(e))
					largestDistance = Math.max(largestDistance, tree.getWeight(e));
			smallDistance = (percentOffset / 100.0) * largestDistance;
		}

		double rootHeight = yCoord.get(tree.getRoot());

		NodeSet assigned = new NodeSet(tree);

		// assign coordinates:
		var queue = new LinkedList<Node>();
		queue.add(tree.getRoot());
		while (queue.size() > 0) // breath-first assignment
		{
			Node w = queue.remove(0); // pop

			boolean ok = true;
			if (w.getInDegree() == 1) // has regular in edge
			{
				Edge e = w.getFirstInEdge();
				Node v = e.getSource();
				var location = nodePointMap.get(v);

				if (!assigned.contains(v)) // can't process yet
				{
					ok = false;
				} else {
					double weight = (useWeights ? tree.getWeight(e) : 1);
					double height = yCoord.get(e.getTarget());
					Node u = e.getTarget();
					nodePointMap.put(u, new Point2D(location.getX() + weight, height));
					assigned.add(u);
				}
			} else if (w.getInDegree() > 1) // all in edges are 'blue' edges
			{
				double x = Double.NEGATIVE_INFINITY;
				for (Edge f = w.getFirstInEdge(); f != null; f = w.getNextInEdge(f)) {
					Node u = f.getSource();
					var location = nodePointMap.get(u);
					if (location == null) {
						ok = false;
					} else {
						x = Math.max(x, location.getX());
					}
				}
				if (ok && x > Double.NEGATIVE_INFINITY) {
					x += smallDistance;
					nodePointMap.put(w, new Point2D(x, yCoord.get(w)));
					assigned.add(w);
				}
			} else  // is root node
			{
				nodePointMap.put(w, new Point2D(0, rootHeight));
				assigned.add(w);
			}

			if (ok)  // add children to end of queue:
			{
				for (Edge f = w.getFirstOutEdge(); f != null; f = w.getNextOutEdge(f)) {
					queue.add(f.getTarget());
				}
			} else  // process this node again later
				queue.add(w);
		}
	}

	/**
	 * compute the levels in the tree or network (max number of edges from node to a leaf)
	 */
	public static NodeIntArray computeLevels(PhyloTree tree) {
		var levels = tree.newNodeIntArray();
		computeLevelsRec(tree, tree.getRoot(), levels, new HashSet<>());
		return levels;
	}

	/**
	 * compute node levels
	 *
	 * @param v
	 * @param levels
	 */
	public static void computeLevelsRec(PhyloTree tree, Node v, NodeIntArray levels, Set<Node> path) {
		path.add(v);
		var level = 0;
		var below = new HashSet<>();
		for (var f : v.outEdges()) {
			var w = f.getTarget();
			below.add(w);
			if (levels.get(w) == null)
				computeLevelsRec(tree, w, levels, path);
			level = Math.max(level, levels.get(w) + (tree.isTransferEdge(f) ? 0 : 1));
		}
		var lsaChildren = tree.getLSAChildrenMap().get(v);
		if (lsaChildren != null) {
			for (var w : lsaChildren) {
				if (!below.contains(w) && !path.contains(w)) {
					if (levels.get(w) == null)
						computeLevelsRec(tree, w, levels, path);
					level = Math.max(level, levels.get(w) + 1);
				}
			}
		}
		levels.put(v, level);
		path.remove(v);
	}
}
