/*
 *  TreeEmbedding.java Copyright (C) 2021 Daniel H. Huson
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

import javafx.geometry.Point2D;
import javafx.scene.Group;
import javafx.scene.paint.Color;
import javafx.scene.shape.*;
import javafx.scene.text.Font;
import jloda.fx.control.RichTextLabel;
import jloda.fx.util.GeometryUtilsFX;
import jloda.fx.window.MainWindowManager;
import jloda.graph.Node;
import jloda.graph.NodeArray;
import jloda.graph.algorithms.Traversals;
import jloda.phylo.PhyloTree;
import jloda.util.Counter;
import jloda.util.Pair;
import jloda.util.ProgramProperties;
import jloda.util.StringUtils;
import splitstree6.data.TaxaBlock;
import splitstree6.data.parts.Taxon;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * computes a rectangular or triangular tree embedding
 * Daniel Huson, 10.2021
 */
public class TreeEmbedding {
	public enum TreeDiagram {
		RectangularPhylogram, RectangularCladogram, TriangularCladogram, RadialPhylogram, RadialCladogram, CircularPhylogram, CircularCladogram;

		public static TreeDiagram getDefault() {
			return TreeDiagram.valueOf(ProgramProperties.get("DefaultTreeDiagram", RectangularPhylogram.name()));
		}

		public static void setDefault(TreeDiagram diagram) {
			ProgramProperties.put("DefaultTreeDiagram", diagram.name());
		}

		public static boolean isRadial(TreeDiagram diagram) {
			return diagram == RadialPhylogram || diagram == RadialCladogram || diagram == CircularPhylogram || diagram == CircularCladogram;
		}
	}

	public enum ParentPlacement {LeafAverage, ChildrenAverage}

	public static double MAX_FONT_SIZE = 24;

	public static Group apply(TaxaBlock taxaBlock, PhyloTree tree, TreeDiagram diagram, double width, double height,
							  Map<Taxon, ShapeAndLabel> taxonNodesMap) {
		var parentPlacement = ParentPlacement.LeafAverage;
		// child-average broken for radial layout

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

		var normalizeWidth = width - maxLabelWidth - 5;
		var normalizeHeight = height - fontHeight;

		if (TreeDiagram.isRadial(diagram))
			normalizeHeight = normalizeWidth = Math.min(width - 2 * maxLabelWidth - 5, height - 2 * fontHeight - 5);


		final NodeArray<Point2D> nodePointMap = switch (diagram) {
			case RectangularPhylogram -> computeCoordinatesRectangular(tree, true, parentPlacement);
			case RectangularCladogram -> computeCoordinatesRectangular(tree, false, parentPlacement);
			case TriangularCladogram -> computeCoordinatesTriangular(tree);
			case RadialPhylogram -> computeCoordinatesRadial(tree, true, parentPlacement);
			case RadialCladogram -> computeCoordinatesRadial(tree, false, parentPlacement);

			default -> throw new RuntimeException("Diagram type not supported");
		};

		normalize(normalizeWidth, normalizeHeight, nodePointMap);

		var nodeGroup = new Group();
		var nodeLabelGroup = new Group();
		var edgeGroup = new Group();

		NodeArray<Shape> nodeShapeMap = tree.newNodeArray();

		for (var v : tree.nodes()) {
			var point = nodePointMap.get(v);
			var circle = new Circle(0.25);
			circle.setStroke(color);
			nodeGroup.getChildren().add(circle);
			circle.setTranslateX(point.getX());
			circle.setTranslateY(point.getY());
			nodeShapeMap.put(v, circle);

			var label = nodeLabelMap.get(v);
			if (label != null) {
				nodeLabelGroup.getChildren().add(label);
				for (var t : tree.getTaxa(v)) {
					if (t <= taxaBlock.getNtax())
						taxonNodesMap.put(taxaBlock.get(t), new ShapeAndLabel(circle, label));
				}
			}
		}
		if (diagram == TreeDiagram.TriangularCladogram || diagram == TreeDiagram.RadialPhylogram || diagram == TreeDiagram.RadialCladogram) {
			for (var e : tree.edges()) {
				var sourceShape = nodeShapeMap.get(e.getSource());
				var targetShape = nodeShapeMap.get(e.getTarget());
				var moveTo = new MoveTo();
				moveTo.xProperty().bind(sourceShape.translateXProperty());
				moveTo.yProperty().bind(sourceShape.translateYProperty());

				var lineTo2 = new LineTo();
				lineTo2.xProperty().bind(targetShape.translateXProperty());
				lineTo2.yProperty().bind(targetShape.translateYProperty());

				var line = new Path(moveTo, lineTo2);

				line.setFill(Color.TRANSPARENT);
				line.setStroke(color);
				line.setStrokeLineCap(StrokeLineCap.ROUND);
				line.setStrokeWidth(0.5);

				edgeGroup.getChildren().add(line);
			}
		} else { // if (diagram == TreePane.Diagram.Rectangular) {
			for (var e : tree.edges()) {
				var sourceShape = nodeShapeMap.get(e.getSource());
				var targetShape = nodeShapeMap.get(e.getTarget());

				var moveTo = new MoveTo();
				moveTo.xProperty().bind(sourceShape.translateXProperty());
				moveTo.yProperty().bind(sourceShape.translateYProperty());

				var lineTo1 = new LineTo();
				lineTo1.xProperty().bind(sourceShape.translateXProperty());
				lineTo1.yProperty().bind(targetShape.translateYProperty());

				var lineTo2 = new LineTo();
				lineTo2.xProperty().bind(targetShape.translateXProperty());
				lineTo2.yProperty().bind(targetShape.translateYProperty());

				var line = new Path(moveTo, lineTo1, lineTo2);

				line.setFill(Color.TRANSPARENT);
				line.setStroke(color);
				line.setStrokeLineCap(StrokeLineCap.ROUND);
				line.setStrokeWidth(0.5);

				edgeGroup.getChildren().add(line);
			}
		}

		if (diagram == TreeDiagram.RadialPhylogram || diagram == TreeDiagram.RadialCladogram)
			layoutNodeLabelsRadial(tree, nodeShapeMap, nodeLabelMap);
		else
			layoutNodeLabelsRectangular(tree, nodeShapeMap, nodeLabelMap);

		return new Group(edgeGroup, nodeGroup, nodeLabelGroup);
	}

	private static NodeArray<Point2D> computeCoordinatesRectangular(PhyloTree tree, boolean toScale, ParentPlacement parentPlacement) {
		final NodeArray<Point2D> nodePointMap = tree.newNodeArray();

		// compute x-coordinates:
		if (toScale) {
			nodePointMap.put(tree.getRoot(), new Point2D(0.0, 0.0));
			Traversals.preOrderTreeTraversal(tree.getRoot(), v -> {
				if (v.getInDegree() > 0)
					nodePointMap.put(v, new Point2D(nodePointMap.get(v.getParent()).getX() + tree.getWeight(v.getFirstInEdge()), 0.0));
			});
		} else { // not to scale:
			Traversals.postOrderTreeTraversal(tree.getRoot(), v -> {
				if (v.isLeaf()) {
					nodePointMap.put(v, new Point2D(0.0, 0.0));
				} else {
					var min = v.childrenStream(true).mapToDouble(w -> nodePointMap.get(w).getX()).min().orElse(0);
					nodePointMap.put(v, new Point2D(min - 1, 0.0));
				}
			});
		}

		// compute y-coordinates:
		if (parentPlacement == ParentPlacement.LeafAverage) {
			final NodeArray<Pair<Integer, Integer>> nodeFirstLastLeafYMap = tree.newNodeArray();
			var leafNumber = new Counter();
			Traversals.postOrderTreeTraversal(tree.getRoot(), v -> {
				if (v.isLeaf()) {
					nodePointMap.put(v, new Point2D(nodePointMap.get(v).getX(), leafNumber.incrementAndGet()));
					nodeFirstLastLeafYMap.put(v, new Pair<>((int) leafNumber.get(), (int) leafNumber.get()));
				} else {
					var min = nodeFirstLastLeafYMap.get(v.getFirstOutEdge().getTarget()).getFirst();
					var max = nodeFirstLastLeafYMap.get(v.getLastOutEdge().getTarget()).getSecond();
					nodePointMap.put(v, new Point2D(nodePointMap.get(v).getX(), (0.5 * (min + max))));
					nodeFirstLastLeafYMap.put(v, new Pair<>(min, max));
				}
			});
		} else { // child average
			var leafNumber = new Counter();
			Traversals.postOrderTreeTraversal(tree.getRoot(), v -> {
				if (v.isLeaf()) {
					nodePointMap.put(v, new Point2D(nodePointMap.get(v).getX(), leafNumber.incrementAndGet()));
				} else {
					var min = nodePointMap.get(v.getFirstOutEdge().getTarget()).getY();
					var max = nodePointMap.get(v.getLastOutEdge().getTarget()).getY();
					nodePointMap.put(v, new Point2D(nodePointMap.get(v).getX(), (0.5 * (min + max))));
				}
			});
		}
		return nodePointMap;
	}

	private static NodeArray<Point2D> computeCoordinatesTriangular(PhyloTree tree) {
		final NodeArray<Point2D> nodePointMap = tree.newNodeArray();
		final NodeArray<Pair<Node, Node>> firstLastLeafBelowMap = tree.newNodeArray();

		var root = tree.getRoot();
		if (root != null) {
			nodePointMap.put(root, new Point2D(0.0, 0.0));
			// compute all y-coordinates:
			{
				var leafNumber = new Counter(0);
				Traversals.postOrderTreeTraversal(root, v -> {
					if (v.isLeaf()) {
						var y = (double) leafNumber.incrementAndGet();
						nodePointMap.put(v, new Point2D(0.0, y));
						firstLastLeafBelowMap.put(v, new Pair<>(v, v));
					} else {
						var firstLeafBelow = firstLastLeafBelowMap.get(v.getFirstOutEdge().getTarget()).getFirst();
						var lastLeafBelow = firstLastLeafBelowMap.get(v.getLastOutEdge().getTarget()).getSecond();
						var y = 0.5 * (nodePointMap.get(firstLeafBelow).getY() + nodePointMap.get(lastLeafBelow).getY());
						var x = -(Math.abs(nodePointMap.get(lastLeafBelow).getY() - nodePointMap.get(firstLeafBelow).getY()));
						nodePointMap.put(v, new Point2D(x, y));
						firstLastLeafBelowMap.put(v, new Pair<>(firstLeafBelow, lastLeafBelow));
					}
				});
			}
		}
		return nodePointMap;
	}

	private static NodeArray<Point2D> computeCoordinatesRadial(PhyloTree tree, boolean toScale, ParentPlacement parentPlacement) {
		final NodeArray<Point2D> nodePointMap = tree.newNodeArray();

		var numberOfLeaves = tree.nodeStream().filter(Node::isLeaf).count();
		if (numberOfLeaves > 0) {
			NodeArray<Double> nodeAngleMap = tree.newNodeArray();

			final NodeArray<Pair<Double, Double>> firstLastAngleBelowMap = tree.newNodeArray();

			var leafNumber = new Counter();
			var delta = 360.0 / numberOfLeaves;

			Traversals.postOrderTreeTraversal(tree.getRoot(), v -> {
				if (v.isLeaf()) {
					var angle = leafNumber.getAndIncrement() * delta;
					nodeAngleMap.put(v, angle);
					firstLastAngleBelowMap.put(v, new Pair<>(angle, angle));
				} else {
					var firstAngleBelow = firstLastAngleBelowMap.get(v.getFirstOutEdge().getTarget()).getFirst();
					var lastAngleBelow = firstLastAngleBelowMap.get(v.getLastOutEdge().getTarget()).getSecond();

					var swap = firstAngleBelow < lastAngleBelow;

					if (swap)
						nodeAngleMap.put(v, 0.5 * (firstAngleBelow + lastAngleBelow));
					else
						nodeAngleMap.put(v, GeometryUtilsFX.modulo360(180 + 0.5 * (firstAngleBelow + lastAngleBelow)));

					if (parentPlacement == ParentPlacement.LeafAverage)
						firstLastAngleBelowMap.put(v, new Pair<>(firstAngleBelow, lastAngleBelow));
					else {
						if (!swap)
							firstLastAngleBelowMap.put(v, new Pair<>(nodeAngleMap.get(v.getFirstOutEdge().getTarget()),
									nodeAngleMap.get(v.getLastOutEdge().getTarget())));
						else
							firstLastAngleBelowMap.put(v, new Pair<>(nodeAngleMap.get(v.getLastOutEdge().getTarget()),
									nodeAngleMap.get(v.getFirstOutEdge().getTarget())));
					}
				}
			});

			nodePointMap.put(tree.getRoot(), new Point2D(0.0, 0.0));

			Traversals.preOrderTreeTraversal(tree.getRoot(), v -> {
				for (var e : v.outEdges()) {
					var w = e.getTarget();
					var vPt = nodePointMap.get(v);
					var wPt = GeometryUtilsFX.translateByAngle(vPt.getX(), vPt.getY(), nodeAngleMap.get(w), toScale ? tree.getWeight(e) : 1.0);
					nodePointMap.put(w, new Point2D(wPt.getX(), wPt.getY()));
				}
			});
		}
		return nodePointMap;
	}

	private static void layoutNodeLabelsRectangular(PhyloTree tree, NodeArray<Shape> nodeShapeMap, NodeArray<RichTextLabel> nodeLabelMap) {
		for (var v : tree.nodes()) {
			var shape = nodeShapeMap.get(v);
			var label = nodeLabelMap.get(v);
			if (label != null) {
				if (v.isLeaf())
					label.translateXProperty().bind(shape.translateXProperty().add(2));
				else
					label.translateXProperty().bind(shape.translateXProperty().subtract(label.widthProperty()).subtract(0.5));
				label.translateYProperty().bind(shape.translateYProperty().subtract(label.heightProperty().multiply(0.5)));
			}
		}
	}

	private static void layoutNodeLabelsRadial(PhyloTree tree, NodeArray<Shape> nodeShapeMap, NodeArray<RichTextLabel> nodeLabelMap) {
		for (var v : tree.nodes()) {
			var shape = nodeShapeMap.get(v);
			var label = nodeLabelMap.get(v);
			if (label != null) {
				var angle = GeometryUtilsFX.computeAngle(shape.getTranslateX(), shape.getTranslateY());
				if (angle <= 45 || angle >= 315) { // right
					label.translateXProperty().bind(shape.translateXProperty().add(2));
					label.translateYProperty().bind(shape.translateYProperty().subtract(label.heightProperty().multiply(0.5)));
				} else if (angle <= 135) { // botto,
					label.translateXProperty().bind(shape.translateXProperty().subtract(label.widthProperty().multiply(0.5)));
					label.translateYProperty().bind(shape.translateYProperty().add(2));
				} else if (angle <= 225) { // left
					label.translateXProperty().bind(shape.translateXProperty().subtract(label.widthProperty()).subtract(2));
					label.translateYProperty().bind(shape.translateYProperty().subtract(label.heightProperty().multiply(0.5)));
				} else if (angle <= 315) { // top
					label.translateXProperty().bind(shape.translateXProperty().subtract(label.widthProperty().multiply(0.5)));
					label.translateYProperty().bind(shape.translateYProperty().subtract(label.heightProperty()));
				}
			}
		}
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

		var centerX = 0.5 * (minX + maxX);
		var centerY = 0.5 * (minY + maxY);

		var scaleX = (maxX > minX ? width / (maxX - minX) : 1);
		var scaleY = (maxY > minY ? height / (maxY - minY) : 1);
		if (minX != 0 || scaleX != 1 || minY != 0 || scaleY != 1) {
			for (var v : nodePointMap.keySet()) {
				var point = nodePointMap.get(v);
				nodePointMap.put(v, new Point2D((point.getX() - centerX) * scaleX, (point.getY() - centerY) * scaleY));
			}
		}
	}

	public static record ShapeAndLabel(Shape shape, RichTextLabel label) implements Iterable<javafx.scene.Node> {
		@Override
		public Iterator<javafx.scene.Node> iterator() {
			return List.of(shape, label).iterator();
		}
	}
}

