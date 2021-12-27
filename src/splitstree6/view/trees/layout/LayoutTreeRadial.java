/*
 *  LayoutTreeCircular.java Copyright (C) 2021 Daniel H. Huson
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
import jloda.fx.util.GeometryUtilsFX;
import jloda.graph.Node;
import jloda.graph.NodeArray;
import jloda.phylo.LSAUtils;
import jloda.phylo.PhyloTree;
import jloda.util.IteratorUtils;
import jloda.util.Pair;
import jloda.util.Single;

import java.util.LinkedList;

import static splitstree6.view.trees.layout.LayoutTreeRectangular.computeAverageEdgeWeight;


/**
 * compute a radial layout
 * Daniel Huson, 12.2021
 */
public class LayoutTreeRadial {
	/**
	 * compute layout for a radial phylogram
	 */
	public static NodeArray<Point2D> apply(PhyloTree tree, int[] taxon2pos) {
		// compute angles:
		try (var nodeAngleMap = tree.newNodeDoubleArray()) {
			final var alpha = (tree.getNumberOfNodes() > 0 ? 360.0 / tree.nodeStream().filter(Node::isLeaf).count() : 0);

			nodeAngleMap.put(tree.getRoot(), 0.0);
			var lsaLeafAngleMap = splitstree6.view.trees.layout.LSAUtils.computeAngleForLSALeaves(tree, taxon2pos, alpha);

			LSAUtils.postorderTraversalLSA(tree, tree.getRoot(), v -> {
				if (tree.isLeaf(v)) {
					var pos = taxon2pos[tree.getTaxa(v).iterator().next()];
					nodeAngleMap.put(v, pos * alpha);
				} else if (tree.isLsaLeaf(v)) {
					nodeAngleMap.put(v, lsaLeafAngleMap.get(v));
				} else {
					var aMin = IteratorUtils.asStream(tree.lsaChildren(v)).filter(w -> v.getEdgeTo(w) != null)
							.mapToDouble(nodeAngleMap::get).min().orElse(0);
					var aMax = IteratorUtils.asStream(tree.lsaChildren(v)).filter(w -> v.getEdgeTo(w) != null)
							.mapToDouble(nodeAngleMap::get).max().orElse(0);
					nodeAngleMap.put(v, 0.5 * (aMin + aMax));
				}
			});

			// assign coordinates:
			var delta = tree.isReticulated() ? 0.25 * computeAverageEdgeWeight(tree) : 0.0;

			final NodeArray<Point2D> nodePointMap = tree.newNodeArray();

			// assign coordinates:
			{
				nodePointMap.put(tree.getRoot(), new Point2D(0, 0));
				final var queue = new LinkedList<Node>();
				queue.add(tree.getRoot());
				while (queue.size() > 0) { // breath-first assignment
					var w = queue.remove(0); // pop
					var ok = true;
					if (w.getInDegree() == 1) { // has regular in-edge
						var e = w.getFirstInEdge();
						var v = e.getSource();
						var vPt = nodePointMap.get(v);

						if (vPt == null) { // can't process yet
							ok = false;
						} else {
							var weight = tree.getWeight(e);
							var angle = nodeAngleMap.get(w);
							var wPt = GeometryUtilsFX.translateByAngle(vPt, angle, weight);
							nodePointMap.put(w, wPt);
						}
					} else if (w.getInDegree() > 1) { // all in edges are reticulate edges
						var rootPt = nodePointMap.get(tree.getRoot());
						var maxDistance = 0.0;
						var x = 0.0;
						var y = 0.0;
						var count = 0;
						for (var v : w.parents()) {
							var vPt = nodePointMap.get(v);
							if (vPt == null) {
								ok = false;
							} else {
								maxDistance = Math.max(maxDistance, rootPt.distance(vPt));
								x += vPt.getX();
								y += vPt.getY();
							}
							count++;
						}
						if (ok) {
							var wPt = new Point2D(x / count, y / count);
							var dist = maxDistance - wPt.distance(rootPt) + delta;
							var angle = w.getOutDegree() > 0 ? nodeAngleMap.get(w.getFirstOutEdge().getTarget()) : 0;
							wPt = GeometryUtilsFX.translateByAngle(wPt, angle, dist);
							nodePointMap.put(w, wPt);
						}
					}

					if (ok)   // add children to end of queue:
						queue.addAll(IteratorUtils.asList(w.children()));
					else  // process this node again later
						queue.add(w);
				}
			}
			return nodePointMap;
		}
	}


	/**
	 * compute layout for a radial phylogram
	 */
	public static NodeArray<Point2D> applyAlt(PhyloTree tree, int[] taxon2pos) {

		try (var nodeAngleMap = tree.newNodeDoubleArray()) {
			final var alpha = (tree.getNumberOfNodes() > 0 ? 360.0 / tree.nodeStream().filter(Node::isLeaf).count() : 0.0);

			if (false) {
				try (NodeArray<Pair<Node, Node>> firstLastLeafBelowMap = tree.newNodeArray()) {
					LSAUtils.postorderTraversalLSA(tree, tree.getRoot(), v -> {
						final double angle;
						if (tree.isLsaLeaf(v)) {
							firstLastLeafBelowMap.put(v, new Pair<>(v, v));
							var pos = taxon2pos[tree.getTaxa(v).iterator().next()];
							angle = pos * alpha;
						} else {
							var firstLeafBelow = firstLastLeafBelowMap.get(tree.getFirstChildLSA(v)).getFirst();
							var lastLeafBelow = firstLastLeafBelowMap.get(tree.getLastChildLSA(v)).getSecond();
							firstLastLeafBelowMap.put(v, new Pair<>(firstLeafBelow, lastLeafBelow));
							angle = 0.5 * (nodeAngleMap.get(firstLeafBelow) + nodeAngleMap.get(lastLeafBelow));
						}
						nodeAngleMap.put(v, angle);
					});
				}
			} else {
				LSAUtils.postorderTraversalLSA(tree, tree.getRoot(), v -> {
					final double angle;
					if (tree.isLsaLeaf(v)) {
						var pos = taxon2pos[tree.getTaxa(v).iterator().next()];
						angle = pos * alpha;
					} else
						angle = IteratorUtils.asStream(tree.lsaChildren(v)).mapToDouble(nodeAngleMap::get).sum() / v.getOutDegree();
					nodeAngleMap.put(v, angle);
				});
			}

			final NodeArray<Point2D> nodePointMap = tree.newNodeArray();
			nodePointMap.put(tree.getRoot(), new Point2D(0, 0));
			LSAUtils.preorderTraversalLSA(tree, tree.getRoot(), v -> {
				var p = nodePointMap.get(v);
				for (var w : tree.lsaChildren(v)) {

				}
				for (var e : v.outEdges()) {
					nodePointMap.put(e.getTarget(), GeometryUtilsFX.translateByAngle(p, nodeAngleMap.get(e.getTarget()), tree.getWeight(e)));
				}
			});
			return nodePointMap;
		}
	}

	/**
	 * compute the maximum number of edges from the root to a leaf
	 *
	 * @param tree the tree
	 * @return length of longest path
	 */
	public static int computeMaxDepth(PhyloTree tree) {
		var max = new Single<>(0);
		LSAUtils.breathFirstTraversalLSA(tree, tree.getRoot(), 0, (level, v) -> max.set(Math.max(max.get(), level)));
		return max.get();
	}
}
