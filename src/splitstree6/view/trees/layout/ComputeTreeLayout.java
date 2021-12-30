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

import javafx.geometry.Point2D;
import javafx.scene.Group;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Shape;
import jloda.fx.control.RichTextLabel;
import jloda.fx.util.TriConsumer;
import jloda.fx.window.MainWindowManager;
import jloda.graph.Edge;
import jloda.graph.NodeArray;
import jloda.graph.NodeDoubleArray;
import jloda.phylo.PhyloTree;
import jloda.util.IteratorUtils;
import splitstree6.data.TaxaBlock;
import splitstree6.view.trees.ordering.CircularOrdering;

import java.util.function.BiConsumer;

import static splitstree6.view.trees.layout.LayoutUtils.getLabelText;

/**
 * computes an embedding of a tree
 * Daniel Huson, 10.2021
 */
public class ComputeTreeLayout {

	public enum ParentPlacement {LeafAverage, ChildrenAverage}

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

		if (alignLabels && diagram != TreeDiagramType.RectangularPhylogram && diagram != TreeDiagramType.CircularPhylogram)
			alignLabels = false; // can't or don't need to, or can't, align labels in all other cases

		//parentPlacement = ParentPlacement.LeafAverage;

		final var color = (MainWindowManager.isUseDarkTheme() ? Color.WHITE : Color.BLACK);

		if (taxonOrdering == null || taxonOrdering.length == 0) {
			taxonOrdering = CircularOrdering.computeRealizableCycle(tree, CircularOrdering.apply(taxaBlock, tree));
		}

		final var taxon2pos = new int[taxaBlock.getNtax() + 1];
		for (int pos = 1; pos < taxonOrdering.length; pos++)
			taxon2pos[taxonOrdering[pos]] = pos;

		var triplet = LayoutUtils.computeFontHeightGraphWidthHeight(taxaBlock, tree, diagram.isRadial(), width, height);
		var fontHeight = triplet.getFirst();
		width = triplet.getSecond();
		height = triplet.getThird();

		final NodeArray<RichTextLabel> nodeLabelMap = tree.newNodeArray();
		for (var v : tree.nodes()) {
			var text = getLabelText(taxaBlock, tree, v);
			if (text != null) {
				var label = new RichTextLabel(text);
				label.setScale(fontHeight / RichTextLabel.DEFAULT_FONT.getSize());
				label.setTextFill(color);
				nodeLabelMap.put(v, label);
			}
		}

		var labelGap = fontHeight + 1;

		final NodeDoubleArray nodeAngleMap = tree.newNodeDoubleArray();

		final NodeArray<Point2D> nodePointMap = switch (diagram) {
			case RectangularPhylogram -> LayoutTreeRectangular.apply(tree, taxon2pos, true);
			case RectangularCladogram -> LayoutTreeRectangular.apply(tree, taxon2pos, false);
			case TriangularCladogram -> LayoutTreeTriangular.apply(tree, taxon2pos);
			case RadialPhylogram -> LayoutTreeRadial.apply(tree, taxon2pos);
			case RadialCladogram, CircularCladogram -> LayoutTreeCircular.apply(tree, taxon2pos, nodeAngleMap, false);
			case CircularPhylogram -> LayoutTreeCircular.apply(tree, taxon2pos, nodeAngleMap, true);
		};

		LayoutUtils.normalize(width, height, nodePointMap, diagram.isRadial());

		assert (Math.abs(nodePointMap.get(tree.getRoot()).getX()) < 0.000001);
		assert (Math.abs(nodePointMap.get(tree.getRoot()).getY()) < 0.000001);

		var nodeGroup = new Group();
		var nodeLabelGroup = new Group();
		var edgeGroup = new Group();

		NodeArray<Shape> nodeShapeMap = tree.newNodeArray();

		for (var v : tree.nodes()) {
			var point = nodePointMap.get(v);
			var circle = new Circle(tree.isLsaLeaf(v) || tree.getRoot() == v ? 1 : 0.5);
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
				var taxonId = IteratorUtils.getFirst(tree.getTaxa(v));
				if (taxonId != null)
					circle.setUserData(taxaBlock.get(taxonId));
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

}

