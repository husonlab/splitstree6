/*
 * ComputeTreeLayout.java Copyright (C) 2022 Daniel H. Huson
 *
 * (Some files contain contributions from other authors, who are then mentioned separately.)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package splitstree6.layout.tree;

import javafx.beans.property.StringProperty;
import javafx.geometry.Point2D;
import javafx.scene.Group;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Shape;
import jloda.fx.control.RichTextLabel;
import jloda.fx.util.TriConsumer;
import jloda.graph.Edge;
import jloda.graph.Node;
import jloda.graph.NodeArray;
import jloda.graph.NodeDoubleArray;
import jloda.phylo.PhyloTree;
import jloda.util.IteratorUtils;

import java.util.function.BiConsumer;
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
	 * @param tree                 tree
	 * @param nTaxa                number of taxa
	 * @param taxonLabelMap        taxon-id to label map, 1-based
	 * @param diagram              diagram type
	 * @param width                target width
	 * @param height               target height
	 * @param nodeCallback         callback to set up additional node stuff
	 * @param edgeCallback         callback to set up additional edges stuff
	 * @param linkNodesEdgesLabels link coordinates nodes, edges and labels via listeners
	 * @param alignLabels          align labels in rectangular and circular phylograms
	 * @return groups and layout consumer
	 */
	public static Result apply(PhyloTree tree, int nTaxa, Function<Integer, StringProperty> taxonLabelMap, TreeDiagramType diagram, HeightAndAngles.Averaging averaging,
							   double width, double height, TriConsumer<Node, Shape, RichTextLabel> nodeCallback,
							   BiConsumer<Edge, Shape> edgeCallback, boolean linkNodesEdgesLabels, boolean alignLabels) {
		if (tree.getNumberOfNodes() == 0)
			return new Result();

		if (alignLabels && diagram != TreeDiagramType.RectangularPhylogram && diagram != TreeDiagramType.CircularPhylogram)
			alignLabels = false; // can't or don't need to, or can't, align labels in all other cases

		//parentPlacement = ParentPlacement.LeafAverage;

		var dimensions = LayoutUtils.computeFontHeightGraphWidthHeight(nTaxa, taxonLabelMap, tree, diagram.isRadialOrCircular(), width, height);

		final NodeArray<RichTextLabel> nodeLabelMap = tree.newNodeArray();

		for (var v : tree.nodes()) {
			var label = LayoutUtils.getLabel(taxonLabelMap, tree, v);
			if (label != null) {
				label.setScale(dimensions.fontHeight() / RichTextLabel.DEFAULT_FONT.getSize());
				label.applyCss();
				nodeLabelMap.put(v, label);
			}
		}

		var labelGap = dimensions.fontHeight()  + 1;

		final NodeDoubleArray nodeAngleMap = tree.newNodeDoubleArray();

		final NodeArray<Point2D> nodePointMap = switch (diagram) {
			case RectangularPhylogram -> LayoutTreeRectangular.apply(tree, true, averaging);
			case RectangularCladogram -> LayoutTreeRectangular.apply(tree, false, averaging);
			case TriangularCladogram -> LayoutTreeTriangular.apply(tree);
			case RadialPhylogram -> LayoutTreeRadial.apply(tree);
			case RadialCladogram, CircularCladogram -> LayoutTreeCircular.apply(tree, nodeAngleMap, false, averaging);
			case CircularPhylogram -> LayoutTreeCircular.apply(tree, nodeAngleMap, true, averaging);
		};

		var unitLengthX = LayoutUtils.normalize(dimensions.width(), dimensions.height(), nodePointMap, diagram.isRadialOrCircular());

		assert (Math.abs(nodePointMap.get(tree.getRoot()).getX()) < 0.000001);
		assert (Math.abs(nodePointMap.get(tree.getRoot()).getY()) < 0.000001);

		var nodeGroup = new Group();
		var internalLabelsGroup = new Group();
		var taxonLabelsGroup = new Group();
		var edgeGroup = new Group();

		NodeArray<Shape> nodeShapeMap = tree.newNodeArray();

		for (var v : tree.nodes()) {
			var point = nodePointMap.get(v);
			var shape = new Circle(tree.isLsaLeaf(v) || tree.getRoot() == v ? 1 : 0.5);
			shape.getStyleClass().add("graph-node");
			nodeGroup.getChildren().add(shape);
			shape.setTranslateX(point.getX());
			shape.setTranslateY(point.getY());
			nodeShapeMap.put(v, shape);

			var label = nodeLabelMap.get(v);
			if (label != null) {
				nodeCallback.accept(v, shape, label);
				var taxonId = IteratorUtils.getFirst(tree.getTaxa(v));
				if (taxonId != null) {
					taxonLabelsGroup.getChildren().add(label);
					shape.setUserData(taxonLabelMap.apply(taxonId));
				} else {
					internalLabelsGroup.getChildren().add(label);
					splitstree6.layout.splits.LayoutUtils.installTranslateUsingLayout(label, () -> {
					});
				}
			}
		}

		if (diagram == TreeDiagramType.CircularCladogram || diagram == TreeDiagramType.CircularPhylogram) {
			edgeGroup.getChildren().addAll(CreateEdgesCircular.apply(diagram, tree, nodePointMap, nodeAngleMap, linkNodesEdgesLabels, edgeCallback));
		} else if (diagram == TreeDiagramType.TriangularCladogram || diagram == TreeDiagramType.RadialPhylogram || diagram == TreeDiagramType.RadialCladogram) {
			edgeGroup.getChildren().addAll(CreateEdgesStraight.apply(diagram, tree, nodeShapeMap, linkNodesEdgesLabels || diagram == TreeDiagramType.RadialPhylogram, edgeCallback));
		} else { // if (diagram == TreePane.TreeDiagramType.Rectangular) {
			edgeGroup.getChildren().addAll(CreateEdgesRectangular.apply(diagram, tree, nodeShapeMap, linkNodesEdgesLabels, edgeCallback));
		}

		Group labelConnectorGroup = alignLabels ? new Group() : null;

		LayoutLabelsRadialPhylogram layoutLabelsRadialPhylogram = null;

		switch (diagram) {
			case CircularPhylogram, CircularCladogram, RadialCladogram -> LayoutLabelsCircular.apply(tree, nodeShapeMap, nodeLabelMap, nodeAngleMap, labelGap, linkNodesEdgesLabels, labelConnectorGroup);
			case RadialPhylogram -> layoutLabelsRadialPhylogram = new LayoutLabelsRadialPhylogram(tree, nodeShapeMap, nodeLabelMap, nodeAngleMap, labelGap);
			default -> LayoutLabelsRectangular.apply(tree, nodeShapeMap, nodeLabelMap, labelGap, linkNodesEdgesLabels, labelConnectorGroup);

		}
		return new Result(labelConnectorGroup, edgeGroup, nodeGroup, internalLabelsGroup, taxonLabelsGroup, layoutLabelsRadialPhylogram, unitLengthX);
	}

	public record Result(Group labelConnectors, Group edges, Group nodes, Group internalLabels, Group taxonLabels,
						 Consumer<LayoutOrientation> layoutOrientationConsumer, double unitLengthX) {
		public Group getAllAsGroup() {
			var group = new Group();
			if (labelConnectors != null)
				group.getChildren().add(labelConnectors);
			if (edges != null)
				group.getChildren().add(edges);
			if (nodes != null)
				group.getChildren().add(nodes);
			if (internalLabels != null)
				group.getChildren().add(internalLabels);
			if (taxonLabels != null)
				group.getChildren().add(taxonLabels);
			return group;
		}

		public Result() {
			this(null, null, null, null, null, null, 1.0);
		}
	}
}

