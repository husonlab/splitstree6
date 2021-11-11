/*
 *  RectangularOrTriangularTreeEmbedding.java Copyright (C) 2021 Daniel H. Huson
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

package splitstree6.view.trees.multitree;

import javafx.beans.property.DoubleProperty;
import javafx.scene.Group;
import javafx.scene.paint.Color;
import javafx.scene.shape.*;
import javafx.scene.text.Font;
import jloda.fx.control.RichTextLabel;
import jloda.fx.window.MainWindowManager;
import jloda.graph.Node;
import jloda.graph.NodeArray;
import jloda.graph.algorithms.Traversals;
import jloda.phylo.PhyloTree;
import jloda.util.Counter;
import jloda.util.Pair;
import jloda.util.StringUtils;
import splitstree6.data.TaxaBlock;
import splitstree6.data.parts.Taxon;

import java.util.Map;

/**
 * computes a rectangular or triangular tree embedding
 * Daniel Huson, 10.2021
 */
public class RectangularOrTriangularTreeEmbedding {
	public enum ParentPlacement {LeafAverage, ChildrenAverage}

	public static double MAX_FONT_SIZE = 24;

	public static Group apply(TaxaBlock taxaBlock, PhyloTree tree, TreePane.Diagram diagram, boolean toScale, double width, double height,
							  Map<Taxon, javafx.scene.Node[]> taxonNodesMap) {
		var parentPlacement = ParentPlacement.ChildrenAverage;

		var color = (MainWindowManager.isUseDarkTheme() ? Color.WHITE : Color.BLACK);

		var numberOfLeaves = tree.nodeStream().filter(Node::isLeaf).count();
		var fontHeight = Math.min(MAX_FONT_SIZE, height / (numberOfLeaves + 1));

		var maxLabelWidth = 0.0;
		NodeArray<RichTextLabel> nodeLabelMap = tree.newNodeArray();
		for (var v : tree.nodes()) {
			var text = getLabelText(taxaBlock, tree, v);
			if (text != null) {
				var label = new RichTextLabel(text);
				label.setFont(new Font("Serif", fontHeight));
				label.setTextFill(color);
				nodeLabelMap.put(v, label);

				maxLabelWidth = Math.max(maxLabelWidth, label.getRawText().length() * 0.7 * fontHeight);
			}
		}
		if (maxLabelWidth > 0.25 * width) {
			fontHeight = Math.min(MAX_FONT_SIZE, fontHeight * 0.25 * width / maxLabelWidth);
			maxLabelWidth = 0;
			for (var label : nodeLabelMap.values()) {
				label.setFont(new Font("Serif", fontHeight));
				maxLabelWidth = Math.max(maxLabelWidth, label.getRawText().length() * 0.7 * fontHeight);
			}
		}

		var node2point = computeCoordinates(tree, toScale, parentPlacement);
		normalize(width - maxLabelWidth - 5, height - fontHeight, node2point);

		var nodeGroup = new Group();
		var nodeLabelGroup = new Group();
		var edgeGroup = new Group();

		NodeArray<DoubleProperty> nodeXMap = tree.newNodeArray();
		NodeArray<DoubleProperty> nodeYMap = tree.newNodeArray();

		for (var v : tree.nodes()) {
			var point = node2point.get(v);
			var circle = new Circle();
			circle.setStroke(color);
			nodeGroup.getChildren().add(circle);
			circle.setCenterX(point.getFirst());
			circle.setCenterY(point.getSecond());
			nodeXMap.put(v, circle.centerXProperty());
			nodeYMap.put(v, circle.centerYProperty());

			var label = nodeLabelMap.get(v);
			if (label != null) {
				if (v.isLeaf())
					label.translateXProperty().bind(nodeXMap.get(v).add(2));
				else
					label.translateXProperty().bind(nodeXMap.get(v).subtract(label.widthProperty()).subtract(0.5));
				label.translateYProperty().bind(nodeYMap.get(v).subtract(label.heightProperty().multiply(0.5)));
				nodeLabelGroup.getChildren().add(label);
				for (var t : tree.getTaxa(v)) {
					if (t <= taxaBlock.getNtax())
						taxonNodesMap.put(taxaBlock.get(t), new javafx.scene.Node[]{circle, label});
				}
			}
		}
		if (diagram == TreePane.Diagram.Triangular) {
			for (var e : tree.edges()) {
				var moveTo = new MoveTo();
				moveTo.xProperty().bind(nodeXMap.get(e.getSource()));
				moveTo.yProperty().bind(nodeYMap.get(e.getSource()));

				var lineTo2 = new LineTo();
				lineTo2.xProperty().bind(nodeXMap.get(e.getTarget()));
				lineTo2.yProperty().bind(nodeYMap.get(e.getTarget()));

				var line = new Path(moveTo, lineTo2);

				line.setFill(Color.TRANSPARENT);
				line.setStroke(color);
				line.setStrokeLineCap(StrokeLineCap.ROUND);
				line.setStrokeWidth(0.5);

				edgeGroup.getChildren().add(line);
			}
		} else if (diagram == TreePane.Diagram.Rectangular) {
			for (var e : tree.edges()) {
				var moveTo = new MoveTo();
				moveTo.xProperty().bind(nodeXMap.get(e.getSource()));
				moveTo.yProperty().bind(nodeYMap.get(e.getSource()));

				var lineTo1 = new LineTo();
				lineTo1.xProperty().bind(nodeXMap.get(e.getSource()));
				lineTo1.yProperty().bind(nodeYMap.get(e.getTarget()));

				var lineTo2 = new LineTo();
				lineTo2.xProperty().bind(nodeXMap.get(e.getTarget()));
				lineTo2.yProperty().bind(nodeYMap.get(e.getTarget()));

				var line = new Path(moveTo, lineTo1, lineTo2);

				line.setFill(Color.TRANSPARENT);
				line.setStroke(color);
				line.setStrokeLineCap(StrokeLineCap.ROUND);
				line.setStrokeWidth(0.5);

				edgeGroup.getChildren().add(line);
			}


		} else throw new RuntimeException("Diagram type not supported");

		return new Group(edgeGroup, nodeGroup, nodeLabelGroup);
	}

	private static NodeArray<Pair<Double, Double>> computeCoordinates(PhyloTree tree, boolean toScale, ParentPlacement parentPlacement) {
		NodeArray<Pair<Double, Double>> nodePointMap = tree.newNodeArray();


		// compute x-coordinates:
		if (toScale) {
			nodePointMap.put(tree.getRoot(), new Pair<>(0.0, 0.0));
			Traversals.preOrderTreeTraversal(tree.getRoot(), v -> {
				if (v.getInDegree() > 0)
					nodePointMap.put(v, new Pair<>(nodePointMap.get(v.getParent()).getFirst() + tree.getWeight(v.getFirstInEdge()), 0.0));
			});
		} else { // not to scale:
			Traversals.postOrderTreeTraversal(tree.getRoot(), v -> {
				if (v.isLeaf()) {
					nodePointMap.put(v, new Pair<>(0.0, 0.0));
				} else {
					var min = v.childrenStream(true).mapToDouble(w -> nodePointMap.get(w).getFirst()).min().orElse(0);
					nodePointMap.put(v, new Pair<>(min - 1, 0.0));
				}
			});
		}
		// compute y-coordinates:

		NodeArray<Pair<Integer, Integer>> nodeFirstLastLeafMap = (parentPlacement == ParentPlacement.LeafAverage) ? tree.newNodeArray() : null;

		var leafNumber = new Counter();
		Traversals.postOrderTreeTraversal(tree.getRoot(), v -> {
			if (v.isLeaf()) {
				nodePointMap.get(v).setSecond((double) leafNumber.incrementAndGet());
				if (nodeFirstLastLeafMap != null)
					nodeFirstLastLeafMap.put(v, new Pair<>((int) leafNumber.get(), (int) leafNumber.get()));
			} else if (parentPlacement == ParentPlacement.ChildrenAverage) {
				var min = v.childrenStream(true).mapToDouble(w -> nodePointMap.get(w).getSecond()).min().orElse(0);
				var max = v.childrenStream(true).mapToDouble(w -> nodePointMap.get(w).getSecond()).max().orElse(0);
				nodePointMap.get(v).setSecond(0.5 * (min + max));
			} else if (nodeFirstLastLeafMap != null) {
				var min = v.childrenStream(true).mapToDouble(w -> nodeFirstLastLeafMap.get(w).getFirst()).min().orElse(0);
				var max = v.childrenStream(true).mapToDouble(w -> nodeFirstLastLeafMap.get(w).getSecond()).max().orElse(0);
				nodePointMap.get(v).setSecond(0.5 * (min + max));
			}
		});

		return nodePointMap;
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

	private static void normalize(double width, double height, NodeArray<Pair<Double, Double>> nodePointMap) {
		var minX = nodePointMap.values().parallelStream().mapToDouble(Pair::getFirst).min().orElse(0);
		var maxX = nodePointMap.values().parallelStream().mapToDouble(Pair::getFirst).max().orElse(0);
		var minY = nodePointMap.values().parallelStream().mapToDouble(Pair::getSecond).min().orElse(0);
		var maxY = nodePointMap.values().parallelStream().mapToDouble(Pair::getSecond).max().orElse(0);

		var scaleX = (maxX > minX ? width / (maxX - minX) : 1);
		var scaleY = (maxY > minY ? height / (maxY - minY) : 1);
		if (minX != 0 || scaleX != 1 || minY != 0 || scaleY != 1) {
			for (var point : nodePointMap.values()) {
				point.set((point.getFirst() - minX) * scaleX, (point.getSecond() - minY) * scaleY);
			}
		}
	}
}

