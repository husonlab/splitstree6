/*
 *  TreeUnrootedLayout.java Copyright (C) 2021 Daniel H. Huson
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

package splitstree6.view.trees.multitree.tree_try;

import javafx.geometry.Point2D;
import javafx.scene.Group;
import javafx.scene.text.Font;
import jloda.fx.util.GeometryUtilsFX;
import jloda.graph.*;
import jloda.phylo.PhyloTree;
import jloda.util.StringUtils;
import splitstree6.data.TaxaBlock;
import splitstree6.view.trees.multitree.TreePane;

public class TreeUnrootedLayout {
	public enum ParentPlacement {LeafAverage, ChildrenAverage}

	public static Group compute(TreePane.Diagram diagram, TaxaBlock taxaBlock, PhyloTree tree, boolean toScale, Font font) {
		final var PARENT_PLACEMENT_DEFAULT = ParentPlacement.LeafAverage;
		final var LEAF_GROUP_GAP_DEFAULT = 20;
		final var CUBIC_CURVE_PARENT_CONTROL_DEFAULT = 20;
		final var CUBIC_CURVE_CHILD_CONTROL_DEFAULT = 50;


		if (tree.getNumberOfNodes() > 0) {
			final EdgeFloatArray edge2Angle = new EdgeFloatArray(tree); // angle of edge
			setAnglesForCircularLayoutRec(tree.getRoot(), null, 0, tree.countLeaves(), edge2Angle, LEAF_GROUP_GAP_DEFAULT, PARENT_PLACEMENT_DEFAULT);
			for (Edge e : tree.edges()) {
				edge2Angle.put(e, 360f - edge2Angle.getFloat(e) + 90f);
			}

			final NodeArray<Point2D> node2point = new NodeArray<>(tree);
			final EdgeArray<EdgeControlPoints> edge2controlPoints = new EdgeArray<>(tree);
			final EdgeFloatArray edgeLengths = EdgeLengthsCalculation.computeEdgeLengths(tree, toScale ? EdgeLengthsCalculation.EdgeLengths.Weights : EdgeLengthsCalculation.EdgeLengths.Uniform);


			if (diagram == TreePane.Diagram.Unrooted)
				computeNodeLocationsForRadialRec(tree.getRoot(), new Point2D(0, 0), edgeLengths, edge2Angle, node2point);
			else
				computeNodeLocationsForCircular(tree.getRoot(), edgeLengths, edge2Angle, node2point);
			computeEdgePointsForCircularRec(tree.getRoot(), 0, edge2Angle, node2point, edge2controlPoints, CUBIC_CURVE_PARENT_CONTROL_DEFAULT, CUBIC_CURVE_CHILD_CONTROL_DEFAULT);
		}


		var nodesGroup = new Group();
		var nodeLabelsGroup = new Group();
		var edgesGroup = new Group();
		var edgesLabelsGroup = new Group();

		for (Node v : tree.nodes()) {
			var labelText = getLabelText(taxaBlock, tree, v);

		}

		return new Group(edgesGroup, nodesGroup, edgesLabelsGroup, nodeLabelsGroup);
	}

	private static String getLabelText(TaxaBlock taxaBlock, PhyloTree tree, Node v) {
		final int taxonId;
		{
			final var it = tree.getTaxa(v).iterator();
			taxonId = (it.hasNext() ? it.next() : 0);
		}
		if (v.getLabel() != null && tree.getLabel(v).length() > 0) {
			if (TaxaBlock.hasDisplayLabels(taxaBlock) && taxonId > 0)
				return taxaBlock.get(taxonId).getDisplayLabelOrName();
			else
				return tree.getLabel(v);
		} else if (tree.getNumberOfTaxa(v) > 0)
			return StringUtils.toString(taxaBlock.getLabels(tree.getTaxa(v)), ",");
		else
			return null;
	}

	/**
	 * Recursively determines the angle of every edge in a circular layout
	 *
	 * @return number of leaves visited
	 */
	static int setAnglesForCircularLayoutRec(final Node v, final Edge f, int nextLeafNum, final int angleParts, final EdgeFloatArray edgeAngles, float leafGroupGap, ParentPlacement parentPlacement) {
		if (v.getOutDegree() == 0) {
			if (f != null)
				edgeAngles.put(f, 360f / angleParts * nextLeafNum);
			return nextLeafNum + 1;
		} else if (v.getDegree() >= 2 && isAllChildrenAreLeaves(v)) { // treat these separately because we want to place them all slightly closer together
			final int numberOfChildren = v.getOutDegree();
			final float firstAngle = (360f / angleParts) * (nextLeafNum + leafGroupGap / 200f);
			final float lastAngle = (360f / angleParts) * (nextLeafNum + numberOfChildren - 1 - leafGroupGap / 200f);
			final float deltaAngle = (lastAngle - firstAngle) / (numberOfChildren - 1);
			float angle = firstAngle;
			for (Edge e : v.outEdges()) {
				edgeAngles.put(e, angle);
				angle += deltaAngle;
			}
			if (f != null)
				edgeAngles.put(f, (360f / angleParts) * (nextLeafNum + 0.5f * (numberOfChildren - 1)));
			nextLeafNum += numberOfChildren;
			//edgeAngles.set(f, 0.5f * (firstAngle + lastAngle));
			return nextLeafNum;
		} else {
			final float firstLeaf = nextLeafNum;
			float firstAngle = Float.MIN_VALUE;
			float lastAngle = Float.MIN_VALUE;

			for (Edge e : v.outEdges()) {
				nextLeafNum = setAnglesForCircularLayoutRec(e.getTarget(), e, nextLeafNum, angleParts, edgeAngles, leafGroupGap, parentPlacement);
				final float angle = edgeAngles.get(e);
				if (firstAngle == Float.MIN_VALUE)
					firstAngle = angle;
				lastAngle = angle;
			}

			if (f != null) {
				if (parentPlacement == ParentPlacement.ChildrenAverage)
					edgeAngles.put(f, 0.5f * (firstAngle + lastAngle));
				else {
					edgeAngles.put(f, 180f / angleParts * (firstLeaf + nextLeafNum - 1));
				}
			}
			return nextLeafNum;
		}
	}

	/**
	 * set the locations of all nodes in a radial tree layout
	 */
	static void computeNodeLocationsForRadialRec(Node v, Point2D vPoint, EdgeFloatArray edgeLengths, EdgeFloatArray edgeAngles, NodeArray<Point2D> node2point) {
		node2point.put(v, vPoint);
		for (Edge e : v.outEdges()) {
			final Node w = e.getTarget();
			final Point2D wLocation = GeometryUtilsFX.translateByAngle(vPoint, edgeAngles.get(e), edgeLengths.get(e));
			node2point.put(w, wLocation);
			computeNodeLocationsForRadialRec(w, wLocation, edgeLengths, edgeAngles, node2point);
		}
	}

	/**
	 * set the coordinates for all nodes and interior edge points
	 */
	static void computeNodeLocationsForCircular(Node root, EdgeFloatArray edgeLengths, EdgeFloatArray edgeAngles, NodeArray<Point2D> node2point) {
		Point2D rootLocation = new Point2D(0, 0); // has to be 0,0
		node2point.put(root, rootLocation);
		for (Edge e : root.outEdges()) {
			final Node w = e.getTarget();
			final Point2D wLocation = GeometryUtilsFX.translateByAngle(rootLocation, edgeAngles.get(e), edgeLengths.get(e));
			node2point.put(w, wLocation);
			computeNodeLocationAndViewForCicularRec(rootLocation, w, wLocation, e, edgeLengths, edgeAngles, node2point);
		}
	}

	/**
	 * recursively compute node coordinates and view from edge angles:
	 */
	static void computeNodeLocationAndViewForCicularRec(Point2D origin, Node v, Point2D vLocation, Edge e, EdgeFloatArray edgeLengths,
														EdgeFloatArray edgeAngles, NodeArray<Point2D> node2point) {
		for (Edge f : v.outEdges()) {
			final Node w = f.getTarget();
			final Point2D b = GeometryUtilsFX.rotateAbout(vLocation, edgeAngles.get(f) - edgeAngles.get(e), origin);
			final Point2D c = GeometryUtilsFX.translateByAngle(b, edgeAngles.get(f), edgeLengths.get(f));
			node2point.put(w, c);
			computeNodeLocationAndViewForCicularRec(origin, w, c, f, edgeLengths, edgeAngles, node2point);
		}
	}

	/**
	 * compute all edge points and setup edge views
	 */
	static void computeEdgePointsForCircularRec(Node v, float vAngle, EdgeFloatArray angles, NodeArray<Point2D> node2points, EdgeArray<EdgeControlPoints> edge2controlPoints, int cubicCurveParentControl, int cubicCurveChildControl) {
		Point2D start = node2points.get(v);
		for (Edge e : v.outEdges()) {
			final Node w = e.getTarget();
			final Point2D end = node2points.get(w);

			float wAngle = angles.get(e);
			double distance = Math.max(1, end.magnitude() - start.magnitude());
			final Point2D control1 = start.multiply(1 + cubicCurveParentControl * distance / (100 * start.magnitude()));
			final Point2D control2 = end.multiply(1 - cubicCurveChildControl * distance / (100 * end.magnitude()));
			final Point2D mid = GeometryUtilsFX.rotate(start, wAngle - vAngle);
			final EdgeControlPoints edgeControlPoints = new EdgeControlPoints(control1, mid, control2);
			edge2controlPoints.put(e, edgeControlPoints);

			computeEdgePointsForCircularRec(w, wAngle, angles, node2points, edge2controlPoints, cubicCurveParentControl, cubicCurveChildControl);
		}
	}

	static boolean isAllChildrenAreLeaves(Node v) {
		return v.outEdgesStream(false).allMatch(e -> e.getTarget().getOutDegree() == 0);
	}

}
