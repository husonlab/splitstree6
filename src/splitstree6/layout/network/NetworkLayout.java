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

import javafx.collections.ObservableMap;
import javafx.geometry.Point2D;
import javafx.scene.Group;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import jloda.fx.control.RichTextLabel;
import jloda.fx.util.GeometryUtilsFX;
import jloda.graph.Edge;
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
		nodeShapeMap.clear();
		edgeShapeMap.clear();

		var graph = networkBlock.getGraph();

		progress.setTasks("Network", "computing layout");
		progress.setMaximum(graph.getNumberOfNodes() + graph.getNumberOfEdges());
		progress.setProgress(0);

		try (NodeArray<Point2D> nodePointMap = graph.newNodeArray()) {
			Function<Edge, Double> edgeWeightFunction;
			if (diagram == DiagramType.Network) {
				edgeWeightFunction = e -> Math.max(0.00001, graph.getWeight(e));
			} else {
				edgeWeightFunction = e -> 1.0;
			}

			FastMultiLayerMethodLayout.apply(options, graph, edgeWeightFunction, (v, p) -> nodePointMap.put(v, new Point2D(p.getX(), p.getY())));

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

				var group = new Group();
				group.setId("graph-node"); // the is used to rotate graph
				{
					var shape = new Circle(v.getDegree() == 1 ? 3 : 2);
					shape.getStyleClass().add("graph-node");
					group.getChildren().add(shape);
				}
				group.setTranslateX(point.getX());
				group.setTranslateY(point.getY());

				nodesGroup.getChildren().add(group);

				var label = LayoutUtils.getLabel(t -> taxaBlock.get(t).displayLabelProperty(), graph, v);

				if (graph.getNumberOfTaxa(v) == 1) {
					group.setUserData(taxaBlock.get(graph.getTaxon(v)));
				}
				nodeShapeMap.put(v, group);

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

					labelLayout.addItem(translateXProperty, translateYProperty, computeLabelAngle(v, nodePointMap), label.widthProperty(), label.heightProperty(),
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

				line.startXProperty().bind(nodeShapeMap.get(e.getSource()).translateXProperty());
				line.startYProperty().bind(nodeShapeMap.get(e.getSource()).translateYProperty());
				line.endXProperty().bind(nodeShapeMap.get(e.getTarget()).translateXProperty());
				line.endYProperty().bind(nodeShapeMap.get(e.getTarget()).translateYProperty());

				var group = new Group(line);
				edgesGroup.getChildren().add(group);

				edgeShapeMap.put(e, group);

				progress.incrementProgress();
			}

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

	public RadialLabelLayout getLabelLayout() {
		return labelLayout;
	}
}
