/*
 *  ComputeTreeEmbedding.java Copyright (C) 2021 Daniel H. Huson
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

package splitstree6.view.trees.treepages;

import javafx.beans.InvalidationListener;
import javafx.geometry.Point2D;
import javafx.geometry.Point3D;
import javafx.scene.Group;
import javafx.scene.paint.Color;
import javafx.scene.shape.*;
import jloda.fx.control.RichTextLabel;
import jloda.fx.util.GeometryUtilsFX;
import jloda.fx.util.TriConsumer;
import jloda.fx.window.MainWindowManager;
import jloda.graph.Edge;
import jloda.graph.Node;
import jloda.graph.NodeArray;
import jloda.graph.NodeDoubleArray;
import jloda.graph.algorithms.Traversals;
import jloda.phylo.PhyloTree;
import jloda.util.Counter;
import jloda.util.Pair;
import jloda.util.ProgramProperties;
import jloda.util.StringUtils;
import splitstree6.data.TaxaBlock;

import java.util.function.BiConsumer;

/**
 * computes an embedding of a tree
 * Daniel Huson, 10.2021
 */
public class ComputeTreeEmbedding {
	public enum Diagram {
		RectangularCladogram, RectangularPhylogram, TriangularCladogram, RadialCladogram, RadialPhylogram, CircularCladogram, CircularPhylogram;

		public static Diagram getDefault() {
			return Diagram.valueOf(ProgramProperties.get("DefaultTreeDiagram", RectangularPhylogram.name()));
		}

		public static void setDefault(Diagram diagram) {
			ProgramProperties.put("DefaultTreeDiagram", diagram.name());
		}

		public boolean isRadial() {
			return this == RadialPhylogram || this == RadialCladogram || this == CircularPhylogram || this == CircularCladogram;
		}

		public boolean isCladogram() {
			return this == RectangularCladogram || this == TriangularCladogram || this == RadialCladogram || this == CircularCladogram;
		}
	}

	public enum ParentPlacement {LeafAverage, ChildrenAverage}

	public static double MAX_FONT_SIZE = 24;

	private static final double LINEAR_LABEL_GAP = 5;
	private static final double RADIAL_LABEL_GAP = 15;

	/**
	 * compute a tree embedding
	 *
	 * @param taxaBlock    set of working taxa
	 * @param tree         tree
	 * @param diagram      diagram type
	 * @param width        target width
	 * @param height       target height
	 * @param nodeCallback callback to set up additional node stuff
	 * @param edgeCallback callback to set up additional edges stuff
	 * @return group of all edges, nodes and node-labels
	 */
	public static Group apply(TaxaBlock taxaBlock, PhyloTree tree, Diagram diagram, double width, double height, TriConsumer<jloda.graph.Node, Shape, RichTextLabel> nodeCallback, BiConsumer<Edge, Shape> edgeCallback) {
		var parentPlacement = ParentPlacement.ChildrenAverage;

		parentPlacement = ParentPlacement.LeafAverage;

		final var color = (MainWindowManager.isUseDarkTheme() ? Color.WHITE : Color.BLACK);

		final var numberOfLeaves = tree.nodeStream().filter(Node::isLeaf).count();

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

				maxLabelWidth = Math.max(maxLabelWidth, label.getEstimatedWidth());
			}
		}
		if (maxLabelWidth > 0.25 * width) {
			fontHeight = Math.min(MAX_FONT_SIZE, fontHeight * 0.25 * width / maxLabelWidth);
			maxLabelWidth = 0;
			for (var label : nodeLabelMap.values()) {
				label.setScale(fontHeight / RichTextLabel.DEFAULT_FONT.getSize());
				maxLabelWidth = Math.max(maxLabelWidth, label.getRawText().length() * 0.7 * fontHeight);
			}
		}

		final double normalizeWidth;
		final double normalizeHeight;

		if (diagram.isRadial()) {
			normalizeHeight = normalizeWidth = Math.min(width - 2 * (maxLabelWidth + RADIAL_LABEL_GAP) - 5, height - 2 * (maxLabelWidth + RADIAL_LABEL_GAP) - 5);
		} else {
			normalizeWidth = width - maxLabelWidth - LINEAR_LABEL_GAP;
			normalizeHeight = height - fontHeight;
		}

		NodeDoubleArray nodeAngleMap = tree.newNodeDoubleArray();

		final NodeArray<Point2D> nodePointMap = switch (diagram) {
			case RectangularPhylogram -> computeCoordinatesRectangular(tree, true, parentPlacement);
			case RectangularCladogram -> computeCoordinatesRectangular(tree, false, parentPlacement);
			case TriangularCladogram -> computeCoordinatesTriangularCladogram(tree);
			case RadialPhylogram -> computeCoordinatesRadialPhylogram(tree, parentPlacement);
			case RadialCladogram, CircularCladogram -> computeCoordinatesRadialCladogram(tree, nodeAngleMap, false);
			case CircularPhylogram -> computeCoordinatesRadialCladogram(tree, nodeAngleMap, true);
		};

		normalize(normalizeWidth, normalizeHeight, nodePointMap);

		var nodeGroup = new Group();
		var nodeLabelGroup = new Group();
		var edgeGroup = new Group();

		NodeArray<Shape> nodeShapeMap = tree.newNodeArray();

		for (var v : tree.nodes()) {
			var point = nodePointMap.get(v);
			var circle = new Circle(v.isLeaf() || tree.getRoot() == v ? 1 : 0.5);
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
			}
		}

		if (diagram == Diagram.CircularCladogram || diagram == Diagram.CircularPhylogram) {
			var rootPt = nodePointMap.get(tree.getRoot());
			Traversals.preOrderTreeTraversal(tree.getRoot(), v -> {
				for (var e : v.outEdges()) {
					var w = e.getTarget();
					var vPt = nodePointMap.get(v);
					var wPt = nodePointMap.get(w);

					var line = new Path();
					line.setFill(Color.TRANSPARENT);
					line.setStroke(color);
					line.setStrokeLineCap(StrokeLineCap.ROUND);
					line.setStrokeWidth(1);

					line.getElements().add(new MoveTo(vPt.getX(), vPt.getY()));

					var vPt0 = vPt.subtract(rootPt);
					var wPt0 = wPt.subtract(rootPt);
					if (vPt0.magnitude() > 0 && wPt0.magnitude() > 0) {
						var corner = rootPt.add(wPt0.multiply(vPt0.magnitude() / wPt0.magnitude()));

						var arcTo = new ArcTo();
						arcTo.setX(corner.getX());
						arcTo.setY(corner.getY());
						arcTo.setRadiusX(vPt0.magnitude());
						arcTo.setRadiusY(vPt0.magnitude());
						arcTo.setLargeArcFlag(GeometryUtilsFX.computeObservedAngle(rootPt, vPt, wPt) > 180);
						arcTo.setSweepFlag(GeometryUtilsFX.computeObservedAngle(rootPt, vPt, wPt) > 0);

						line.getElements().add(arcTo);
					}

					var lineTo2 = new LineTo();
					lineTo2.setX(wPt.getX());
					lineTo2.setY(wPt.getY());

					line.getElements().add(lineTo2);
					edgeGroup.getChildren().add(line);
					edgeCallback.accept(e, line);
				}
			});
		} else if (diagram == Diagram.TriangularCladogram || diagram == Diagram.RadialPhylogram || diagram == Diagram.RadialCladogram) {
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
				line.setStrokeWidth(1);

				edgeGroup.getChildren().add(line);
				edgeCallback.accept(e, line);
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
				line.setStrokeWidth(1);

				edgeGroup.getChildren().add(line);
				edgeCallback.accept(e, line);
			}
		}

		if (diagram.isRadial())
			layoutNodeLabelsRadial(tree, nodeShapeMap, nodeLabelMap, nodeAngleMap);
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

	private static NodeArray<Point2D> computeCoordinatesTriangularCladogram(PhyloTree tree) {
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

	private static NodeArray<Point2D> computeCoordinatesRadialCladogram(PhyloTree tree, NodeDoubleArray nodeAngleMap, boolean toScale) {
		final NodeArray<Point2D> nodePointMap = tree.newNodeArray();

		final var numberOfLeaves = tree.nodeStream().filter(Node::isLeaf).count();
		if (numberOfLeaves > 0) {
			final var leafNumber = new Counter();
			final var delta = 360.0 / numberOfLeaves;

			final NodeDoubleArray nodeRadiusMap = tree.newNodeDoubleArray();

			final var maxDepth = tree.computeMaxDepth();
			Traversals.postOrderTreeTraversal(tree.getRoot(), v -> {
				if (v.isLeaf()) {
					nodeRadiusMap.put(v, (double) maxDepth);
				} else {
					nodeRadiusMap.put(v, v.childrenStream().mapToDouble(nodeRadiusMap::get).min().orElse(maxDepth) - 1);
				}
			});
			nodeRadiusMap.put(tree.getRoot(), 0.0);

			nodeAngleMap.put(tree.getRoot(), 0.0);
			final NodeArray<Pair<Node, Node>> firstLastLeafBelowMap = tree.newNodeArray();
			Traversals.postOrderTreeTraversal(tree.getRoot(), v -> {
				final double angle;
				if (v.isLeaf()) {
					firstLastLeafBelowMap.put(v, new Pair<>(v, v));
					angle = leafNumber.getAndIncrement() * delta;
				} else {
					var firstLeafBelow = firstLastLeafBelowMap.get(v.getFirstOutEdge().getTarget()).getFirst();
					var lastLeafBelow = firstLastLeafBelowMap.get(v.getLastOutEdge().getTarget()).getSecond();
					firstLastLeafBelowMap.put(v, new Pair<>(firstLeafBelow, lastLeafBelow));
					angle = 0.5 * (nodeAngleMap.get(firstLeafBelow) + nodeAngleMap.get(lastLeafBelow));
				}
				if (toScale && v.getInDegree() > 0) {
					var e = v.getFirstInEdge();
					var parentRadius = nodeRadiusMap.get(e.getSource());
					nodeRadiusMap.put(v, parentRadius + tree.getWeight(e));
				}
				nodeAngleMap.put(v, angle);
			});

			tree.nodeStream().forEach(v -> nodePointMap.put(v, GeometryUtilsFX.computeCartesian(nodeRadiusMap.get(v), nodeAngleMap.get(v))));
		}
		return nodePointMap;
	}

	private static NodeArray<Point2D> computeCoordinatesRadialPhylogram(PhyloTree tree, ParentPlacement parentPlacement) {
		final NodeArray<Point2D> nodePointMap = tree.newNodeArray();

		final var numberOfLeaves = tree.nodeStream().filter(Node::isLeaf).count();
		if (numberOfLeaves > 0) {
			final var leafNumber = new Counter();
			final var delta = 360.0 / numberOfLeaves;

			final NodeDoubleArray nodeAngleMap = tree.newNodeDoubleArray();
			if (parentPlacement == ParentPlacement.LeafAverage) {
				final NodeArray<Pair<Node, Node>> firstLastLeafBelowMap = tree.newNodeArray();
				Traversals.postOrderTreeTraversal(tree.getRoot(), v -> {
					final double angle;
					if (v.isLeaf()) {
						firstLastLeafBelowMap.put(v, new Pair<>(v, v));
						angle = leafNumber.getAndIncrement() * delta;
					} else {
						var firstLeafBelow = firstLastLeafBelowMap.get(v.getFirstOutEdge().getTarget()).getFirst();
						var lastLeafBelow = firstLastLeafBelowMap.get(v.getLastOutEdge().getTarget()).getSecond();
						firstLastLeafBelowMap.put(v, new Pair<>(firstLeafBelow, lastLeafBelow));
						angle = 0.5 * (nodeAngleMap.get(firstLeafBelow) + nodeAngleMap.get(lastLeafBelow));
					}
					nodeAngleMap.put(v, angle);
				});
			} else {
				Traversals.postOrderTreeTraversal(tree.getRoot(), v -> {
					final double angle;
					if (v.isLeaf())
						angle = leafNumber.getAndIncrement() * delta;
					else
						angle = v.childrenStream().mapToDouble(nodeAngleMap::get).sum() / v.getOutDegree();
					nodeAngleMap.put(v, angle);
				});
			}
			nodePointMap.put(tree.getRoot(), new Point2D(0, 0));
			Traversals.preOrderTreeTraversal(tree.getRoot(), v -> {
				var p = nodePointMap.get(v);
				for (var e : v.outEdges()) {
					nodePointMap.put(e.getTarget(), GeometryUtilsFX.translateByAngle(p, nodeAngleMap.get(e.getTarget()), tree.getWeight(e)));
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
				InvalidationListener changeListener = a -> {
					if (label.getWidth() > 0 && label.getHeight() > 0) {
						if (v.isLeaf())
							label.translateXProperty().bind(shape.translateXProperty().add(LINEAR_LABEL_GAP));
						else
							label.translateXProperty().bind(shape.translateXProperty().subtract(label.widthProperty()).subtract(0.5));
						label.translateYProperty().bind(shape.translateYProperty().subtract(label.heightProperty().multiply(0.5)));
					}
				};
				label.widthProperty().addListener(changeListener);
				label.heightProperty().addListener(changeListener);
			}
		}
	}

	private static void layoutNodeLabelsRadial(PhyloTree tree, NodeArray<Shape> nodeShapeMap, NodeArray<RichTextLabel> nodeLabelMap, NodeDoubleArray nodeAngleMap) {
		for (var v : tree.nodes()) {
			var shape = nodeShapeMap.get(v);
			var label = nodeLabelMap.get(v);
			if (label != null) {
				InvalidationListener changeListener = a -> {
					if (label.getWidth() > 0 && label.getHeight() > 0) {
						Double angle = nodeAngleMap.get(v);
						if (angle == null) {
							if (v.getParent() != null) {
								var vPoint = new Point2D(shape.getTranslateX(), shape.getTranslateY());
								var w = v.getParent();
								var wPoint = new Point2D(nodeShapeMap.get(w).getTranslateX(), nodeShapeMap.get(w).getTranslateY());
								while (vPoint.distance(wPoint) == 0 && w.getParent() != null) {
									w = w.getParent();
									wPoint = new Point2D(nodeShapeMap.get(w).getTranslateX(), nodeShapeMap.get(w).getTranslateY());
								}
								angle = GeometryUtilsFX.computeAngle(shape.getTranslateX() - wPoint.getX(), shape.getTranslateY() - wPoint.getY());
							} else
								angle = 0.0;
						}
						var offset = GeometryUtilsFX.translateByAngle(0, 0, angle, RADIAL_LABEL_GAP + 0.5 * label.getWidth());
						label.translateXProperty().bind(shape.translateXProperty().subtract(0.5 * label.getWidth()).add(offset.getX()));
						label.translateYProperty().bind(shape.translateYProperty().subtract(0.5 * label.getHeight()).add(offset.getY()));
						label.setRotationAxis(new Point3D(0, 0, 1));
						label.setRotate(angle > 90 && angle < 270 ? angle + 180 : angle);
					}
				};
				label.widthProperty().addListener(changeListener);
				label.heightProperty().addListener(changeListener);
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

		var scaleX = (maxX > minX ? width / (maxX - minX) : 1);
		var scaleY = (maxY > minY ? height / (maxY - minY) : 1);
		if (minX != 0 || scaleX != 1 || minY != 0 || scaleY != 1) {
			for (var v : nodePointMap.keySet()) {
				var point = nodePointMap.get(v);
				nodePointMap.put(v, new Point2D(point.getX() * scaleX, point.getY() * scaleY));
			}
		}
	}
}

