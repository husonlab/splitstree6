/*
 *  ComputeTreeLayout.java Copyright (C) 2024 Daniel H. Huson
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

import javafx.beans.property.StringProperty;
import javafx.geometry.Point2D;
import javafx.scene.Group;
import javafx.scene.shape.Circle;
import jloda.fx.control.RichTextLabel;
import jloda.fx.util.DraggableUtils;
import jloda.graph.Edge;
import jloda.graph.Node;
import jloda.graph.NodeArray;
import jloda.graph.NodeDoubleArray;
import jloda.phylo.LSAUtils;
import jloda.phylo.PhyloTree;
import jloda.phylogeny.dolayout.NetworkDisplacementOptimization;
import jloda.phylogeny.layout.Averaging;
import jloda.phylogeny.layout.LayoutRootedPhylogeny;
import jloda.util.IteratorUtils;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * computes an embedding of a tree
 * Daniel Huson, 10.2021
 */
public class ComputeTreeLayout {
	/**
	 * compute a tree embedding
	 *
	 * @param tree          tree
	 * @param nTaxa         number of taxa
	 * @param taxonLabelMap taxon-id to label map, 1-based
	 * @param diagram       diagram type
	 * @param width         target width
	 * @param height        target height
	 * @param alignLabels   align labels in rectangular and circular phylograms
	 * @return groups and layout consumer
	 */
	public static Result apply(PhyloTree tree, int nTaxa, Function<Integer, StringProperty> taxonLabelMap, TreeDiagramType diagram, Averaging averaging,
							   double width, double height, boolean alignLabels, Map<Node, LabeledNodeShape> nodeShapeMap, Map<Edge, LabeledEdgeShape> edgeShapeMap, boolean optimizeReticulationEdges) {
		if (tree.getNumberOfNodes() == 0)
			return new Result();

		if (nodeShapeMap != null)
			nodeShapeMap.clear();
		else
			nodeShapeMap = new HashMap<>();

		if (alignLabels && diagram != TreeDiagramType.RectangularPhylogram && diagram != TreeDiagramType.CircularPhylogram)
			alignLabels = false; // can't or don't need to, align labels in all other cases

		//parentPlacement = ParentPlacement.LeafAverage;

		var dimensions = LayoutUtils.computeFontHeightGraphWidthHeight(nTaxa, taxonLabelMap, tree, diagram.isRadialOrCircular(), width, height);

		var labelGap = dimensions.fontHeight() + 1;

		final NodeDoubleArray nodeAngleMap = tree.newNodeDoubleArray();
		final NodeArray<Point2D> nodePointMap = tree.newNodeArray();

		if (optimizeReticulationEdges) {
			var childrenMap = new HashMap<Node, List<Node>>();
			LSAUtils.computeLSAChildrenMap(tree, childrenMap);

			var reticulateMap = new HashMap<Node, List<Node>>();
			for (var e : tree.edges()) {
				if (tree.isReticulateEdge(e) && !tree.isTransferAcceptorEdge(e)) {
					reticulateMap.computeIfAbsent(e.getSource(), k -> new ArrayList<>()).add(e.getTarget());
					reticulateMap.computeIfAbsent(e.getTarget(), k -> new ArrayList<>()).add(e.getSource());
				}
			}
			var result = NetworkDisplacementOptimization.apply(tree.getRoot(), childrenMap::get, reticulateMap::get, diagram.isRadialOrCircular(), () -> false);
			tree.getLSAChildrenMap().clear();
			tree.getLSAChildrenMap().putAll(result);
			optimizeReticulationEdges = false;
		}


		LayoutRootedPhylogeny.Layout layout = null;
		LayoutRootedPhylogeny.Scaling scaling = null;
		boolean triangular = false;

		switch (diagram) {
			case RectangularPhylogram -> {
				layout = LayoutRootedPhylogeny.Layout.Rectangular;
				scaling = LayoutRootedPhylogeny.Scaling.ToScale;
			}
			case RectangularCladogram -> {
				layout = LayoutRootedPhylogeny.Layout.Rectangular;
				scaling = LayoutRootedPhylogeny.Scaling.LateBranching;

			}
			case TriangularCladogram -> {
				triangular = true;
				scaling = LayoutRootedPhylogeny.Scaling.LateBranching;
			}
			case RadialPhylogram -> {
				layout = LayoutRootedPhylogeny.Layout.Radial;
				scaling = LayoutRootedPhylogeny.Scaling.ToScale;
			}
			case RadialCladogram -> {
				layout = LayoutRootedPhylogeny.Layout.Radial;
				scaling = LayoutRootedPhylogeny.Scaling.LateBranching;
			}
			case CircularCladogram -> {
				layout = LayoutRootedPhylogeny.Layout.Circular;
				scaling = LayoutRootedPhylogeny.Scaling.LateBranching;
			}
			case CircularPhylogram -> {
				layout = LayoutRootedPhylogeny.Layout.Circular;
				scaling = LayoutRootedPhylogeny.Scaling.ToScale;
			}
		}
		if (triangular) {
			splitstree6.layout.tree.LayoutRootedPhylogeny.applyTriangular(tree, averaging, nodePointMap);
		} else {
			splitstree6.layout.tree.LayoutRootedPhylogeny.apply(tree, layout, scaling, averaging, optimizeReticulationEdges, new Random(666), nodeAngleMap, nodePointMap);
		}

		var unitLengthX = LayoutUtils.normalize(dimensions.width(), dimensions.height(), nodePointMap, diagram.isRadialOrCircular());

		assert (Math.abs(nodePointMap.get(tree.getRoot()).getX()) < 0.000001);
		assert (Math.abs(nodePointMap.get(tree.getRoot()).getY()) < 0.000001);

		var nodeGroup = new Group();
		var otherLabelsGroup = new Group();
		var taxonLabelsGroup = new Group();
		var edgeGroup = new Group();

		nodeShapeMap.clear();
		for (var v : tree.nodes()) {
			var shape = new Circle(tree.isLsaLeaf(v) || tree.getRoot() == v ? 1 : 0.5);
			var label = LayoutUtils.getLabel(taxonLabelMap, tree, v);

			var nodeShape = new LabeledNodeShape(label, shape);
			nodeGroup.getChildren().add(nodeShape);

			nodeShapeMap.put(v, nodeShape);

			var point = nodePointMap.get(v);

			if (point != null) {
				nodeShape.setTranslateX(point.getX());
				nodeShape.setTranslateY(point.getY());

				if (label != null) {
					label.setScale(dimensions.fontHeight() / RichTextLabel.getDefaultFont().getSize());
					label.applyCss();
					var taxonId = IteratorUtils.getFirst(tree.getTaxa(v));
					if (taxonId != null) {
						taxonLabelsGroup.getChildren().add(label);
						nodeShape.setUserData(taxonId);
					} else {
						otherLabelsGroup.getChildren().add(label);
						DraggableUtils.setupDragMouseLayout(label);
					}
				}
			}
		}

		if (diagram == TreeDiagramType.CircularCladogram || diagram == TreeDiagramType.CircularPhylogram) {
			CreateEdgesCircular.apply(diagram, tree, nodePointMap, nodeAngleMap, edgeShapeMap);
		} else if (diagram == TreeDiagramType.TriangularCladogram || diagram == TreeDiagramType.RadialPhylogram || diagram == TreeDiagramType.RadialCladogram) {
			CreateEdgesStraight.apply(tree, nodeShapeMap, diagram == TreeDiagramType.RadialPhylogram, edgeShapeMap);
		} else { // if (diagram == TreePane.TreeDiagramType.Rectangular) {
			CreateEdgesRectangular.apply(tree, nodeShapeMap, edgeShapeMap);
		}
		edgeGroup.getChildren().addAll(edgeShapeMap.values());

		otherLabelsGroup.getChildren().addAll(edgeShapeMap.values().stream().filter(LabeledEdgeShape::hasLabel).map(LabeledEdgeShape::getLabel).toList());

		var labelConnectorGroup = alignLabels ? new Group() : null;

		SetupRadialLabelLayout layoutLabelsRadialPhylogram = null;

		switch (diagram) {
			case CircularPhylogram, CircularCladogram ->
					LayoutLabelsCircular.apply(tree, nodeShapeMap, nodeAngleMap, labelGap, labelConnectorGroup);
			case RadialPhylogram, RadialCladogram ->
					layoutLabelsRadialPhylogram = new SetupRadialLabelLayout(tree, nodeShapeMap, nodeAngleMap, labelGap);
			default -> LayoutLabelsRectangular.apply(tree, nodeShapeMap, labelGap, labelConnectorGroup);
		}
		return new Result(labelConnectorGroup, edgeGroup, nodeGroup, otherLabelsGroup, taxonLabelsGroup, layoutLabelsRadialPhylogram, unitLengthX);
	}

	public record Result(Group labelConnectors, Group edges, Group nodes, Group otherLabels, Group taxonLabels,
						 Consumer<LayoutOrientation> layoutOrientationConsumer, double unitLengthX) {

		public Group getAllAsGroup() {
			var group = new Group();
			if (labelConnectors != null)
				group.getChildren().add(labelConnectors);
			if (edges != null)
				group.getChildren().add(edges);
			if (nodes != null)
				group.getChildren().add(nodes);
			if (otherLabels != null)
				group.getChildren().add(otherLabels);
			if (taxonLabels != null)
				group.getChildren().add(taxonLabels);
			return group;
		}

		public Result() {
			this(null, null, null, null, null, null, 1.0);
		}
	}

}

