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
 *  ComputeTreeLayout.java Copyright (C) 2021 Daniel H. Huson
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

import javafx.beans.binding.DoubleBinding;
import javafx.geometry.Point2D;
import javafx.scene.Group;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import javafx.scene.shape.Shape;
import jloda.fx.control.RichTextLabel;
import jloda.fx.util.TriConsumer;
import jloda.fx.window.MainWindowManager;
import jloda.graph.Edge;
import jloda.graph.Node;
import jloda.graph.NodeArray;
import jloda.graph.NodeDoubleArray;
import jloda.phylo.PhyloTree;
import jloda.util.StringUtils;
import splitstree6.data.TaxaBlock;
import splitstree6.view.trees.ordering.CircularOrdering;

import java.util.function.BiConsumer;

/**
 * computes an embedding of a tree
 * Daniel Huson, 10.2021
 */
public class ComputeTreeLayout {

	public enum ParentPlacement {LeafAverage, ChildrenAverage}

	public static double MAX_FONT_SIZE = 24;

	/**
	 * compute a tree embedding
	 *
	 * @param taxaBlock            set of working taxa
	 * @param tree                 tree
	 * @param taxonOrdering        if non-null, maps the i-th taxon to its position in the leaf ordering
	 * @param diagram              diagram type
	 * @param width                target width
	 * @param height               target height
	 * @param nodeCallback         callback to set up additional node stuff
	 * @param edgeCallback         callback to set up additional edges stuff
	 * @param linkNodesEdgesLabels link coordinates nodes, edges and labels via listeners
	 * @param alignLabels          align labels in rectangular and circular phylograms
	 * @return group of all edges, nodes and node-labels
	 */
	public static Group apply(TaxaBlock taxaBlock, PhyloTree tree, int[] taxonOrdering, TreeDiagramType diagram, double width, double height, TriConsumer<jloda.graph.Node, Shape, RichTextLabel> nodeCallback,
							  BiConsumer<Edge, Shape> edgeCallback, boolean linkNodesEdgesLabels, boolean alignLabels) {
		if (tree.getNumberOfNodes() == 0)
			return new Group();

		var parentPlacement = ParentPlacement.ChildrenAverage;

		if (alignLabels && diagram != TreeDiagramType.RectangularPhylogram && diagram != TreeDiagramType.CircularPhylogram)
			alignLabels = false; // can't or don't need to, or can't, align labels in all other cases

		//parentPlacement = ParentPlacement.LeafAverage;

		final var color = (MainWindowManager.isUseDarkTheme() ? Color.WHITE : Color.BLACK);

		final var numberOfLeaves = tree.nodeStream().filter(tree::isLsaLeaf).count();

		if (taxonOrdering == null || taxonOrdering.length == 0) {
			taxonOrdering = CircularOrdering.computeRealizableCycle(tree, CircularOrdering.apply(taxaBlock, tree));
		}

		final var taxon2pos = new int[taxaBlock.getNtax() + 1];
		for (int pos = 1; pos < taxonOrdering.length; pos++)
			taxon2pos[taxonOrdering[pos]] = pos;

		double fontHeight;
		if (diagram.isRadial())
			fontHeight = Math.min(MAX_FONT_SIZE, 0.5 * Math.min(width, height) * Math.PI / (numberOfLeaves + 1));
		else
			fontHeight = Math.min(MAX_FONT_SIZE, height / (numberOfLeaves + 1));

		var maxLabelWidth = 0.0;
		NodeArray<RichTextLabel> nodeLabelMap = tree.newNodeArray();
		for (var v : tree.nodes()) {
			var text = getLabelText(taxaBlock, tree, v);
			if (text != null) {
				var label = new RichTextLabel(text);
				label.setScale(fontHeight / RichTextLabel.DEFAULT_FONT.getSize());
				label.setTextFill(color);
				nodeLabelMap.put(v, label);

				// BasicFX.reportChanges(label.getRawText(),label.translateXProperty());
				// BasicFX.reportChanges(label.getRawText(),label.translateYProperty());

				maxLabelWidth = Math.max(maxLabelWidth, label.getEstimatedWidth());
			}
		}

		if (maxLabelWidth + fontHeight > 0.25 * width) {
			fontHeight = Math.min(MAX_FONT_SIZE, fontHeight * 0.25 * width / (maxLabelWidth + fontHeight));
			maxLabelWidth = 0;
			for (var label : nodeLabelMap.values()) {
				label.setScale(fontHeight / RichTextLabel.DEFAULT_FONT.getSize());
				maxLabelWidth = Math.max(maxLabelWidth, label.getRawText().length() * 0.7 * fontHeight);
			}
		}
		var labelGap = fontHeight;

		final double normalizeWidth;
		final double normalizeHeight;

		if (diagram.isRadial()) {
			if (maxLabelWidth > 100) {
				fontHeight *= 100 / maxLabelWidth;
				labelGap = fontHeight;
				maxLabelWidth = 100;
			}

			var tmp = Math.min(width - 2 * (maxLabelWidth + labelGap), height - 2 * (maxLabelWidth + labelGap));
			if (tmp > 20)
				tmp -= 10;
			else if (tmp < 0)
				tmp = 20;
			normalizeWidth = normalizeHeight = tmp;
		} else {
			normalizeWidth = width - maxLabelWidth - labelGap;
			normalizeHeight = height - fontHeight;
		}

		NodeDoubleArray nodeAngleMap = tree.newNodeDoubleArray();

		final NodeArray<Point2D> nodePointMap = switch (diagram) {
			case RectangularPhylogram -> LayoutTreeRectangular.apply(tree, taxon2pos, true);
			case RectangularCladogram -> LayoutTreeRectangular.apply(tree, taxon2pos, false);
			case TriangularCladogram -> LayoutTreeTriangular.apply(tree, taxon2pos);
			case RadialPhylogram -> LayoutTreeRadial.apply(tree, taxon2pos);
			case RadialCladogram, CircularCladogram -> LayoutTreeCircular.apply(tree, taxon2pos, nodeAngleMap, false);
			case CircularPhylogram -> LayoutTreeCircular.apply(tree, taxon2pos, nodeAngleMap, true);
		};

		normalize(normalizeWidth, normalizeHeight, nodePointMap);

		assert (Math.abs(nodePointMap.get(tree.getRoot()).getX()) < 0.000001);
		assert (Math.abs(nodePointMap.get(tree.getRoot()).getY()) < 0.000001);

		var nodeGroup = new Group();
		var nodeLabelGroup = new Group();
		var edgeGroup = new Group();

		NodeArray<Shape> nodeShapeMap = tree.newNodeArray();

		for (var v : tree.nodes()) {
			var point = nodePointMap.get(v);
			var circle = new Circle(tree.isLsaLeaf(v) || tree.getRoot() == v ? 2 : 0.5);
			circle.setFill(color);
			circle.setStroke(Color.TRANSPARENT);
			nodeGroup.getChildren().add(circle);
			circle.setTranslateX(point.getX());
			circle.setTranslateY(point.getY());
			nodeShapeMap.put(v, circle);

			var label = nodeLabelMap.get(v);
			if (label != null) {
				nodeLabelGroup.getChildren().add(label);
				nodeCallback.accept(v, circle, label);
				circle.setUserData(taxaBlock.get(tree.getTaxa(v).iterator().next()));
			}
		}

		if (diagram == TreeDiagramType.CircularCladogram || diagram == TreeDiagramType.CircularPhylogram) {
			edgeGroup.getChildren().addAll(CreateEdgesCircular.apply(diagram, tree, nodePointMap, nodeAngleMap, color, linkNodesEdgesLabels, edgeCallback));
		} else if (diagram == TreeDiagramType.TriangularCladogram || diagram == TreeDiagramType.RadialPhylogram || diagram == TreeDiagramType.RadialCladogram) {
			edgeGroup.getChildren().addAll(CreateEdgesStraight.apply(diagram, tree, nodeShapeMap, color, linkNodesEdgesLabels, edgeCallback));
		} else { // if (diagram == TreePane.TreeDiagramType.Rectangular) {
			edgeGroup.getChildren().addAll(CreateEdgesRectangular.apply(diagram, tree, nodeShapeMap, color, linkNodesEdgesLabels, edgeCallback));
		}

		Group labelConnectorGroup = alignLabels ? new Group() : null;

		if (diagram.isRadial())
			LayoutLabelsRadial.apply(tree, nodeShapeMap, nodeLabelMap, nodeAngleMap, labelGap, linkNodesEdgesLabels, labelConnectorGroup);
		else
			LayoutLabelsRectangular.apply(tree, nodeShapeMap, nodeLabelMap, labelGap, linkNodesEdgesLabels, labelConnectorGroup);

		if (labelConnectorGroup != null)
			return new Group(labelConnectorGroup, edgeGroup, nodeGroup, nodeLabelGroup);
		else
			return new Group(edgeGroup, nodeGroup, nodeLabelGroup);
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

	private static void normalize(double width, double height, NodeArray<Point2D> nodePointMap) {
		var minX = nodePointMap.values().parallelStream().mapToDouble(Point2D::getX).min().orElse(0);
		var maxX = nodePointMap.values().parallelStream().mapToDouble(Point2D::getX).max().orElse(0);
		var minY = nodePointMap.values().parallelStream().mapToDouble(Point2D::getY).min().orElse(0);
		var maxY = nodePointMap.values().parallelStream().mapToDouble(Point2D::getY).max().orElse(0);

		var scaleX = (maxX > minX ? width / (maxX - minX) : 1);
		var scaleY = (maxY > minY ? height / (maxY - minY) : 1);
		if (minX != 0 || scaleX != 1 || minY != 0 || scaleY != 1) {
			for (var v : nodePointMap.keySet()) {
				var point = nodePointMap.get(v);
				nodePointMap.put(v, new Point2D(point.getX() * scaleX, point.getY() * scaleY));
			}
		}
	}

	public static class LabelConnector extends Line {
		public LabelConnector(double x1, double y1, double x2, double y2) {
			setStartX(x1);
			setStartY(y1);
			setEndX(x2);
			setEndY(y2);
			setStroke(Color.DARKGRAY);
			getStrokeDashArray().addAll(2.0, 5.0);
		}

		public LabelConnector(DoubleBinding x1, DoubleBinding y1, DoubleBinding x2, DoubleBinding y2) {
			startXProperty().bind(x1);
			startYProperty().bind(y1);
			endXProperty().bind(x2);
			endYProperty().bind(y2);
			setStroke(Color.LIGHTGRAY);
			getStrokeDashArray().addAll(2.0, 5.0);
		}
	}
}

