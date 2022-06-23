/*
 *  NetworkLayout.java Copyright (C) 2022 Daniel H. Huson
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

package splitstree6.layout.network;

import javafx.application.Platform;
import javafx.collections.ObservableMap;
import javafx.geometry.Point2D;
import javafx.scene.Group;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import javafx.scene.shape.Shape;
import jloda.fx.control.RichTextLabel;
import jloda.fx.util.GeometryUtilsFX;
import jloda.graph.Edge;
import jloda.graph.EdgeArray;
import jloda.graph.Node;
import jloda.graph.NodeArray;
import jloda.graph.fmm.FastMultiLayerMethodLayout;
import jloda.graph.fmm.FastMultiLayerMethodOptions;
import jloda.util.CanceledException;
import jloda.util.progress.ProgressListener;
import splitstree6.data.NetworkBlock;
import splitstree6.data.TaxaBlock;
import splitstree6.layout.tree.LayoutUtils;
import splitstree6.layout.tree.RadialLabelLayout;

import java.util.Arrays;
import java.util.Collection;
import java.util.function.Function;

import static splitstree6.layout.tree.LayoutUtils.computeFontHeightGraphWidthHeight;
import static splitstree6.layout.tree.LayoutUtils.normalize;

/**
 * network layout
 * Daniel Huson, 4.2022
 */
public class NetworkLayout {
	private final RadialLabelLayout labelLayout = new RadialLabelLayout();
	private final FastMultiLayerMethodOptions options;

	public NetworkLayout() {
		options = new FastMultiLayerMethodOptions();
	}

	public Group apply(ProgressListener progress, TaxaBlock taxaBlock, NetworkBlock networkBlock, DiagramType diagram, double width, double height, ObservableMap<Integer, RichTextLabel> taxonLabelMap, ObservableMap<Node, Group> nodeShapeMap, ObservableMap<Edge, Group> edgeShapeMap) throws CanceledException {
		labelLayout.clear();
		Platform.runLater(nodeShapeMap::clear);
		Platform.runLater(edgeShapeMap::clear);

		var graph = networkBlock.getGraph();

		progress.setTasks("Network", "computing layout");
		progress.setMaximum(graph.getNumberOfNodes() + graph.getNumberOfEdges());
		progress.setProgress(0);

		try (NodeArray<Point2D> nodePointMap = graph.newNodeArray();
			 var nodeAngleMap = graph.newNodeDoubleArray();
			 NodeArray<Group> newNodeShapeMap = graph.newNodeArray();
			 EdgeArray<Group> newEdgeShapeMap = graph.newEdgeArray()) {
			Function<Edge, Double> edgeWeightFunction;
			if (diagram == DiagramType.Network) {
				edgeWeightFunction = e -> Math.max(0.00001, graph.getWeight(e));
			} else {
				edgeWeightFunction = e -> 1.0;
			}

			if (networkBlock.getNetworkType().equals(NetworkBlock.Type.Points)) {
				for (int t = 1; t <= taxaBlock.getNtax(); t++) {
					var v = networkBlock.getGraph().getTaxon2Node(t);
					var x = Double.parseDouble(networkBlock.getNodeData(v).get(NetworkBlock.NodeData.BasicKey.x.name()));
					var y = Double.parseDouble(networkBlock.getNodeData(v).get(NetworkBlock.NodeData.BasicKey.y.name()));
					nodePointMap.put(v, new Point2D(x, y));
				}
			} else {
				FastMultiLayerMethodLayout.apply(options, graph, edgeWeightFunction, (v, p) -> nodePointMap.put(v, new Point2D(p.getX(), p.getY())));
			}

			if (graph.getNumberOfEdges() == 0) {
				var center = computeCenter(nodePointMap.values());
				for (var v : graph.nodes()) {
					nodeAngleMap.put(v, GeometryUtilsFX.computeAngle(nodePointMap.get(v).subtract(center)));
				}

			} else {
				for (var v : graph.nodes())
					if (graph.getNumberOfTaxa(v) > 0)
						nodeAngleMap.put(v, computeLabelAngle(v, nodePointMap));
			}

			var dimensions = computeFontHeightGraphWidthHeight(taxaBlock.getNtax(), t -> taxaBlock.get(t).displayLabelProperty(), graph, true, width, height);
			var fontHeight = dimensions.fontHeight();
			width = dimensions.width();
			height = dimensions.height();

			normalize(width, height, nodePointMap, true);

			// nodes:
			var nodesGroup = new Group();
			var nodeLabelsGroup = new Group();

			for (var v : graph.nodes()) {
				var point = nodePointMap.get(v);

				var label = LayoutUtils.getLabel(t -> taxaBlock.get(t).displayLabelProperty(), graph, v);

				var group = new Group();
				group.setId("graph-node"); // the is used to rotate graph
				if (false && graph.getNumberOfEdges() == 0) { // todo: allow user to use marks for nodes
					Shape shape = null;
					if (label != null) {
						shape = RichTextLabel.getMark(label.getText());
						if (shape != null)
							label.setShowMarks(false);
					}
					if (shape == null) {
						shape = new Circle(2);
						shape.getStyleClass().add("graph-node");
					}
					group.getChildren().add(shape);
				} else {
					var shape = new Circle(v.getDegree() == 1 ? 3 : 2);
					shape.getStyleClass().add("graph-node");
					group.getChildren().add(shape);
				}
				group.setTranslateX(point.getX());
				group.setTranslateY(point.getY());

				nodesGroup.getChildren().add(group);


				if (graph.getNumberOfTaxa(v) == 1) {
					group.setUserData(taxaBlock.get(graph.getTaxon(v)));
				}
				newNodeShapeMap.put(v, group);

				if (label != null) {
					if (graph.getNumberOfTaxa(v) == 1) {
						taxonLabelMap.put(graph.getTaxon(v), label);
					}

					label.getStyleClass().add("graph-label");
					label.setScale(fontHeight / RichTextLabel.DEFAULT_FONT.getSize());
					label.setTranslateX(group.getTranslateX() + 10);
					label.setTranslateY(group.getTranslateY() + 10);
					label.setUserData(group);
					nodeLabelsGroup.getChildren().add(label);

					label.applyCss();

					var translateXProperty = group.translateXProperty();
					var translateYProperty = group.translateYProperty();

					labelLayout.addItem(translateXProperty, translateYProperty, nodeAngleMap.get(v), label.widthProperty(), label.heightProperty(),
							xOffset -> {
								label.setLayoutX(0);
								label.translateXProperty().bind(translateXProperty.add(xOffset));
							},
							yOffset -> {
								label.setLayoutY(0);
								label.translateYProperty().bind(translateYProperty.add(yOffset));
							});

					labelLayout.addAvoidable(() -> group.getTranslateX() - 0.5 * group.prefWidth(0), () -> group.getTranslateY() - 0.5 * group.prefHeight(0), () -> group.prefWidth(0), () -> group.prefHeight(0));
				}
				progress.incrementProgress();
			}

			var edgesGroup = new Group();

			for (var e : graph.edges()) {
				var line = new Line();
				line.getStyleClass().add("graph-edge");

				line.startXProperty().bind(newNodeShapeMap.get(e.getSource()).translateXProperty());
				line.startYProperty().bind(newNodeShapeMap.get(e.getSource()).translateYProperty());
				line.endXProperty().bind(newNodeShapeMap.get(e.getTarget()).translateXProperty());
				line.endYProperty().bind(newNodeShapeMap.get(e.getTarget()).translateYProperty());

				var group = new Group(line);
				edgesGroup.getChildren().add(group);

				newEdgeShapeMap.put(e, group);

				progress.incrementProgress();
			}
			Platform.runLater(() -> nodeShapeMap.putAll(newNodeShapeMap));
			Platform.runLater(() -> edgeShapeMap.putAll(newEdgeShapeMap));

			return new Group(edgesGroup, nodesGroup, nodeLabelsGroup);
		}
	}

	private double computeLabelAngle(Node v, NodeArray<Point2D> nodePointMap) {
		if (v.getDegree() == 0)
			return 0;
		else if (v.getDegree() == 1) {
			var vp = nodePointMap.get(v);
			var wp = nodePointMap.get(v.getFirstAdjacentEdge().getOpposite(v));
			return GeometryUtilsFX.computeAngle(vp.getX() - wp.getX(), vp.getY() - wp.getY());
		} else {
			var vPt = nodePointMap.get(v);
			var angles = v.adjacentEdgesStream(false).map(e -> e.getOpposite(v)).mapToDouble(w -> GeometryUtilsFX.modulo360(GeometryUtilsFX.computeAngle(nodePointMap.get(w).getX() - vPt.getX(), nodePointMap.get(w).getY() - vPt.getY()))).toArray();
			Arrays.sort(angles);
			var bestI = angles.length - 1;
			var bestD = (360 - angles[angles.length - 1]) + angles[0];
			for (var i = 0; i < angles.length - 1; i++) {
				var d = angles[i + 1] - angles[i];
				if (d > bestD) {
					bestI = i;
					bestD = d;
				}
			}
			return GeometryUtilsFX.modulo360(angles[bestI] + 0.5 * bestD);
		}
	}

	private Point2D computeCenter(Collection<Point2D> points) {
		if (points.size() > 0) {
			var x = points.stream().mapToDouble(Point2D::getX).sum();
			var y = points.stream().mapToDouble(Point2D::getY).sum();
			return new Point2D(x / points.size(), y / points.size());
		} else
			return new Point2D(0, 0);
	}

	public RadialLabelLayout getLabelLayout() {
		return labelLayout;
	}
}
