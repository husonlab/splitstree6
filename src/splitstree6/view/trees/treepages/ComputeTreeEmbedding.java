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
import javafx.beans.binding.Bindings;
import javafx.beans.binding.DoubleBinding;
import javafx.geometry.Point2D;
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

	/**
	 * compute a tree embedding
	 *
	 * @param taxaBlock            set of working taxa
	 * @param tree                 tree
	 * @param diagram              diagram type
	 * @param width                target width
	 * @param height               target height
	 * @param nodeCallback         callback to set up additional node stuff
	 * @param edgeCallback         callback to set up additional edges stuff
	 * @param linkNodesEdgesLabels link coordinates nodes, edges and labels via listeners
	 * @param alignLabels          align labels in rectangular and circular phylograms
	 * @return group of all edges, nodes and node-labels
	 */
	public static Group apply(TaxaBlock taxaBlock, PhyloTree tree, Diagram diagram, double width, double height, TriConsumer<jloda.graph.Node, Shape, RichTextLabel> nodeCallback,
							  BiConsumer<Edge, Shape> edgeCallback, boolean linkNodesEdgesLabels, boolean alignLabels) {
		var parentPlacement = ParentPlacement.ChildrenAverage;

		if (alignLabels && diagram != Diagram.RectangularPhylogram && diagram != Diagram.CircularPhylogram)
			alignLabels = false; // can't or don't need to, or can't, align labels in all other cases

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
		final var labelGap = fontHeight;

		final double normalizeWidth;
		final double normalizeHeight;

		if (diagram.isRadial()) {
			var tmp = Math.min(width - 2 * (maxLabelWidth + labelGap), height - 2 * (maxLabelWidth + labelGap));
			if(tmp > 20)
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
			case RectangularPhylogram -> computeCoordinatesRectangular(tree, true, parentPlacement);
			case RectangularCladogram -> computeCoordinatesRectangular(tree, false, parentPlacement);
			case TriangularCladogram -> computeCoordinatesTriangularCladogram(tree);
			case RadialPhylogram -> computeCoordinatesRadialPhylogram(tree, parentPlacement);
			case RadialCladogram, CircularCladogram -> computeCoordinatesRadialCladogram(tree, nodeAngleMap, false);
			case CircularPhylogram -> computeCoordinatesRadialCladogram(tree, nodeAngleMap, true);
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
				circle.setUserData(taxaBlock.get(tree.getTaxa(v).iterator().next()));
			}
		}

		if (diagram == Diagram.CircularCladogram || diagram == Diagram.CircularPhylogram) {
			var origin = new Point2D(0, 0);

			Traversals.preOrderTreeTraversal(tree.getRoot(), v -> {
				for (var e : v.outEdges()) {
					var w = e.getTarget();

					// todo: need to implemented linked

					var vPt = nodePointMap.get(v);
					var wPt = nodePointMap.get(w);

					var line = new Path();
					line.setFill(Color.TRANSPARENT);
					line.setStroke(color);
					line.setStrokeLineCap(StrokeLineCap.ROUND);
					line.setStrokeWidth(1);

					line.getElements().add(new MoveTo(vPt.getX(), vPt.getY()));

					if (vPt.magnitude() > 0 && wPt.magnitude() > 0) {
						var corner = wPt.multiply(vPt.magnitude() / wPt.magnitude());

						var arcTo = new ArcTo();
						arcTo.setX(corner.getX());
						arcTo.setY(corner.getY());
						arcTo.setRadiusX(vPt.magnitude());
						arcTo.setRadiusY(vPt.magnitude());
						arcTo.setLargeArcFlag(GeometryUtilsFX.computeObservedAngle(origin, vPt, wPt) > 180);
						arcTo.setSweepFlag(GeometryUtilsFX.computeObservedAngle(origin, vPt, wPt) > 0);

						line.getElements().add(arcTo);
					}

					var lineTo2 = new LineTo();
					lineTo2.setX(wPt.getX());
					lineTo2.setY(wPt.getY());

					line.getElements().add(lineTo2);
					edgeGroup.getChildren().add(line);
					edgeCallback.accept(e, line);


					if (w.isLeaf() && diagram == Diagram.CircularPhylogram) {
						nodeAngleMap.put(w, GeometryUtilsFX.computeAngle(wPt));
					}
				}
			});
		} else if (diagram == Diagram.TriangularCladogram || diagram == Diagram.RadialPhylogram || diagram == Diagram.RadialCladogram) {
			for (var e : tree.edges()) {
				var sourceShape = nodeShapeMap.get(e.getSource());
				var targetShape = nodeShapeMap.get(e.getTarget());
				var moveTo = new MoveTo();
				if (linkNodesEdgesLabels) {
					moveTo.xProperty().bind(sourceShape.translateXProperty());
					moveTo.yProperty().bind(sourceShape.translateYProperty());
				} else {
					moveTo.setX(sourceShape.getTranslateX());
					moveTo.setY(sourceShape.getTranslateY());
				}

				var lineTo2 = new LineTo();
				if (linkNodesEdgesLabels) {
					lineTo2.xProperty().bind(targetShape.translateXProperty());
					lineTo2.yProperty().bind(targetShape.translateYProperty());
				} else {
					lineTo2.setX(targetShape.getTranslateX());
					lineTo2.setY(targetShape.getTranslateY());
				}

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
				if (linkNodesEdgesLabels) {
					moveTo.xProperty().bind(sourceShape.translateXProperty());
					moveTo.yProperty().bind(sourceShape.translateYProperty());
				} else {
					moveTo.setX(sourceShape.getTranslateX());
					moveTo.setY(sourceShape.getTranslateY());
				}

				var lineTo1 = new LineTo();

				if (linkNodesEdgesLabels) {
					lineTo1.xProperty().bind(sourceShape.translateXProperty());
					lineTo1.yProperty().bind(targetShape.translateYProperty());
				} else {
					lineTo1.setX(sourceShape.getTranslateX());
					lineTo1.setY(targetShape.getTranslateY());
				}

				var lineTo2 = new LineTo();

				if (linkNodesEdgesLabels) {
					lineTo2.xProperty().bind(targetShape.translateXProperty());
					lineTo2.yProperty().bind(targetShape.translateYProperty());
				} else {
					lineTo2.setX(targetShape.getTranslateX());
					lineTo2.setY(targetShape.getTranslateY());
				}

				var line = new Path(moveTo, lineTo1, lineTo2);

				line.setFill(Color.TRANSPARENT);
				line.setStroke(color);
				line.setStrokeLineCap(StrokeLineCap.ROUND);
				line.setStrokeWidth(1);

				edgeGroup.getChildren().add(line);
				edgeCallback.accept(e, line);
			}
		}

		Group labelConnectorGroup = alignLabels ? new Group() : null;

		if (diagram.isRadial())
			layoutNodeLabelsRadial(tree, nodeShapeMap, nodeLabelMap, nodeAngleMap, labelGap, linkNodesEdgesLabels, labelConnectorGroup);
		else
			layoutNodeLabelsRectangular(tree, nodeShapeMap, nodeLabelMap, labelGap, linkNodesEdgesLabels, labelConnectorGroup);

		if (labelConnectorGroup != null)
			return new Group(labelConnectorGroup, edgeGroup, nodeGroup, nodeLabelGroup);
		else
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
				if (v.isLeaf()) {
					firstLastLeafBelowMap.put(v, new Pair<>(v, v));
					nodeAngleMap.put(v, leafNumber.getAndIncrement() * delta);
				} else {
					var firstLeafBelow = firstLastLeafBelowMap.get(v.getFirstOutEdge().getTarget()).getFirst();
					var lastLeafBelow = firstLastLeafBelowMap.get(v.getLastOutEdge().getTarget()).getSecond();
					firstLastLeafBelowMap.put(v, new Pair<>(firstLeafBelow, lastLeafBelow));
					nodeAngleMap.put(v, 0.5 * (nodeAngleMap.get(firstLeafBelow) + nodeAngleMap.get(lastLeafBelow)));
				}
				if (toScale && v.getInDegree() > 0) {
					var e = v.getFirstInEdge();
					var parentRadius = nodeRadiusMap.get(e.getSource());
					nodeRadiusMap.put(v, parentRadius + tree.getWeight(e));
				}
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

	private static void layoutNodeLabelsRectangular(PhyloTree tree, NodeArray<Shape> nodeShapeMap, NodeArray<RichTextLabel> nodeLabelMap, double labelGap,
													boolean linkNodesEdgesLabels, Group labelConnectors) {
		var alignLabels = (labelConnectors != null);
		double max;
		if (alignLabels) {
			max = tree.nodeStream().mapToDouble(v -> nodeShapeMap.get(v).getTranslateX()).max().orElse(0);
		} else
			max = Double.MIN_VALUE;

		for (var v : tree.nodes()) {
			var shape = nodeShapeMap.get(v);
			var label = nodeLabelMap.get(v);
			if (label != null) {
				InvalidationListener changeListener = a -> {
					if (label.getWidth() > 0 && label.getHeight() > 0) {
						if (linkNodesEdgesLabels) {
							if (v.isLeaf()) {
								var add = (max > Double.MIN_VALUE ? max - shape.getTranslateX() : 0) + labelGap;
								label.translateXProperty().bind(shape.translateXProperty().add(add));
								if (alignLabels && add > 1.1 * labelGap) {
									// todo: this is untested
									labelConnectors.getChildren().add(new LabelConnector(
											Bindings.createDoubleBinding(() -> shape.getTranslateX() + 0.5 * labelGap, shape.translateXProperty()),
											Bindings.createDoubleBinding(shape::getTranslateY, shape.translateYProperty()),
											Bindings.createDoubleBinding(() -> shape.getTranslateX() + add - 0.5 * labelGap, shape.translateXProperty()),
											Bindings.createDoubleBinding(shape::getTranslateY, shape.translateYProperty())));
								}
							} else
								label.translateXProperty().bind(shape.translateXProperty().subtract(label.widthProperty()).subtract(0.5));
							label.translateYProperty().bind(shape.translateYProperty().subtract(label.heightProperty().multiply(0.5)));
						} else {
							if (v.isLeaf()) {
								var add = (max > Double.MIN_VALUE ? max - shape.getTranslateX() : 0) + labelGap;
								label.setTranslateX(shape.getTranslateX() + add);
								if (alignLabels && add > 1.1 * labelGap) {
									labelConnectors.getChildren().add(new LabelConnector(shape.getTranslateX() + 0.5 * labelGap, shape.getTranslateY(), shape.getTranslateX() + add - 0.5 * labelGap, shape.getTranslateY()));
								}
							} else
								label.setTranslateX(shape.getTranslateX() - label.getWidth() - 0.5);
							label.setTranslateY(shape.getTranslateY() - 0.5 * label.getHeight());
						}
					}
				};
				label.widthProperty().addListener(changeListener);
				label.heightProperty().addListener(changeListener);
			}
		}
	}

	private static void layoutNodeLabelsRadial(PhyloTree tree, NodeArray<Shape> nodeShapeMap, NodeArray<RichTextLabel> nodeLabelMap, NodeDoubleArray nodeAngleMap, double labelGap,
											   boolean linkNodesEdgesLabels, Group labelConnectors) {
		var alignLabels = (labelConnectors != null);
		final double maxRadius;
		if (alignLabels) {
			maxRadius = tree.nodeStream().map(nodeShapeMap::get).mapToDouble(s -> GeometryUtilsFX.magnitude(s.getTranslateX(), s.getTranslateY())).max().orElse(0);
		} else
			maxRadius = Double.MIN_VALUE;

		for (var v : tree.nodes()) {
			var shape = nodeShapeMap.get(v);
			var label = nodeLabelMap.get(v);
			if (label != null) {
				InvalidationListener changeListener = a -> {
					if (label.getWidth() > 0 && label.getHeight() > 0) {
						var angle = nodeAngleMap.get(v);
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
						var add = (maxRadius > Double.MIN_VALUE ? maxRadius - GeometryUtilsFX.magnitude(shape.getTranslateX(), shape.getTranslateY()) : 0);

						var offset = GeometryUtilsFX.translateByAngle(0, 0, angle, add + labelGap + 0.5 * label.getWidth());
						if (linkNodesEdgesLabels) {
							label.translateXProperty().bind(shape.translateXProperty().subtract(0.5 * label.getWidth()).add(offset.getX()));
							label.translateYProperty().bind(shape.translateYProperty().subtract(0.5 * label.getHeight()).add(offset.getY()));


							if (alignLabels && add > 1.1 * labelGap) {
								// todo: this is untested
								var offset1 = GeometryUtilsFX.translateByAngle(0, 0, angle, 0.5 * labelGap);
								var offset2 = GeometryUtilsFX.translateByAngle(0, 0, angle, add + 0.5 * labelGap);
								labelConnectors.getChildren().add(new LabelConnector(
										Bindings.createDoubleBinding(() -> shape.getTranslateX() + offset1.getX(), shape.translateXProperty()),
										Bindings.createDoubleBinding(() -> shape.getTranslateY() + offset1.getY(), shape.translateYProperty()),
										Bindings.createDoubleBinding(() -> shape.getTranslateX() + offset2.getX(), shape.translateXProperty()),
										Bindings.createDoubleBinding(() -> shape.getTranslateY() + offset2.getY(), shape.translateYProperty())));
							}
						} else {
							label.setTranslateX(shape.getTranslateX() - 0.5 * label.getWidth() + offset.getX());
							label.setTranslateY(shape.getTranslateY() - 0.5 * label.getHeight() + offset.getY());

							if (alignLabels && add > 1.1 * labelGap) {
								var offset1 = GeometryUtilsFX.translateByAngle(0, 0, angle, 0.5 * labelGap);
								var offset2 = GeometryUtilsFX.translateByAngle(0, 0, angle, add + 0.5 * labelGap);
								labelConnectors.getChildren().add(new LabelConnector(shape.getTranslateX() + offset1.getX(), shape.getTranslateY() + offset1.getY(), shape.getTranslateX()+offset2.getX(),shape.getTranslateY()+offset2.getY()));
							}
						}
						label.setRotate(angle);
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
			getStrokeDashArray().addAll(2.0,5.0);
		}
	}
}

