/*
 * SplitNetworkLayout.java Copyright (C) 2024 Daniel H. Huson
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

package splitstree6.layout.splits;

import javafx.beans.InvalidationListener;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.ObservableList;
import javafx.collections.ObservableMap;
import javafx.geometry.Point2D;
import javafx.scene.Group;
import javafx.scene.control.Label;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import javafx.scene.shape.Shape;
import jloda.fx.control.RichTextLabel;
import jloda.fx.selection.SelectionModel;
import jloda.fx.selection.SetSelectionModel;
import jloda.fx.util.DraggableUtils;
import jloda.fx.util.GeometryUtilsFX;
import jloda.fx.util.Icebergs;
import jloda.fx.window.NotificationManager;
import jloda.graph.Node;
import jloda.graph.NodeArray;
import jloda.phylo.PhyloSplitsGraph;
import jloda.util.CanceledException;
import jloda.util.StringUtils;
import jloda.util.Triplet;
import jloda.util.progress.ProgressListener;
import splitstree6.algorithms.utils.SplitsBlockUtilities;
import splitstree6.data.SplitsBlock;
import splitstree6.data.TaxaBlock;
import splitstree6.data.parts.Taxon;
import splitstree6.layout.splits.algorithms.ConvexHull;
import splitstree6.layout.splits.algorithms.EqualAngle;
import splitstree6.layout.splits.algorithms.PhylogeneticOutline;
import splitstree6.layout.tree.LabeledNodeShape;
import splitstree6.layout.tree.LayoutUtils;
import splitstree6.layout.tree.RadialLabelLayout;
import splitstree6.splits.Compatibility;

import java.io.IOException;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashSet;
import java.util.stream.Collectors;

import static splitstree6.layout.tree.LayoutUtils.computeFontHeightGraphWidthHeight;
import static splitstree6.layout.tree.LayoutUtils.normalize;

/**
 * computes the splits network layout
 * Daniel Huson, 12.2021
 */
public class SplitNetworkLayout {
	private final RadialLabelLayout labelLayout;

	private final PhyloSplitsGraph graph = new PhyloSplitsGraph();
	private final NodeArray<Point2D> nodePointMap = graph.newNodeArray();
	private final ArrayList<ArrayList<Node>> loops = new ArrayList<>();

	public SplitNetworkLayout() {
		labelLayout = new RadialLabelLayout();
	}


	public Group apply(ProgressListener progress, TaxaBlock taxaBlock0, SplitsBlock splitsBlock0,
					   DoubleProperty unitLength, double width, double height,
					   ObservableMap<Integer, RichTextLabel> taxonLabelMap, // todo: this should be input
					   ObservableMap<Node, LabeledNodeShape> nodeShapeMap,
					   ObservableMap<Integer, ArrayList<Shape>> splitShapeMap,
					   ObservableList<LoopView> loopViews) throws IOException {
		return apply(progress, taxaBlock0, splitsBlock0, SplitsDiagramType.Outline,
				SplitsRooting.None, 0, new SetSelectionModel<Taxon>(), new SetSelectionModel<Integer>(),
				new SimpleBooleanProperty(false), unitLength, width, height,
				taxonLabelMap,
				nodeShapeMap,
				splitShapeMap,
				loopViews);
	}

	/**
	 * compute an outline or network
	 *
	 * @return group of groups, namely loops, nodes, edges and node labels
	 */
	public Group apply(ProgressListener progress, TaxaBlock taxaBlock0, SplitsBlock splitsBlock0, SplitsDiagramType diagram,
					   SplitsRooting rooting, double rootAngle,
					   SelectionModel<Taxon> taxonSelectionModel, SelectionModel<Integer> splitSelectionModel,
					   ReadOnlyBooleanProperty showConfidence, DoubleProperty unitLength, double width, double height,
					   ObservableMap<Integer, RichTextLabel> taxonLabelMap, // todo: this should be input
					   ObservableMap<Node, LabeledNodeShape> nodeShapeMap,
					   ObservableMap<Integer, ArrayList<Shape>> splitShapeMap,
					   ObservableList<LoopView> loopViews) throws IOException {
		labelLayout.clear();

		if (splitsBlock0.getNsplits() == 0)
			return new Group();

		if (splitsBlock0.getCycle() == null || splitsBlock0.getCycle().length == 0) {
			splitsBlock0.setCycle(SplitsBlockUtilities.computeCycle(taxaBlock0.getNtax(), splitsBlock0.getSplits()));
		}

		// if rooting is desired, need to create a modified set of taxa and splits
		final TaxaBlock taxaBlock;
		final SplitsBlock splitsBlock;
		final int rootSplit;
		switch (rooting) {
			default -> { // no rooting
				taxaBlock = taxaBlock0;
				splitsBlock = splitsBlock0;
				rootSplit = 0;
			}
			case OutGroup -> {
				var selectedTaxa = taxonSelectionModel.getSelectedItems().stream().map(taxaBlock0::indexOf).collect(Collectors.toSet());
				taxaBlock = new TaxaBlock();
				splitsBlock = new SplitsBlock();
				final Triplet<Integer, Double, Double> rootLocation = RootingUtils.computeRootLocation(false, taxaBlock0.getNtax(), selectedTaxa, splitsBlock0.getCycle(), splitsBlock0, diagram.isUsingWeights());
				rootSplit = RootingUtils.setupForRootedNetwork(false, rootLocation, taxaBlock0, splitsBlock0, taxaBlock, splitsBlock);
			}
			case OutGroupAlt -> {
				var selectedTaxa = taxonSelectionModel.getSelectedItems().stream().map(taxaBlock0::indexOf).collect(Collectors.toSet());
				taxaBlock = new TaxaBlock();
				splitsBlock = new SplitsBlock();
				final Triplet<Integer, Double, Double> rootLocation = RootingUtils.computeRootLocation(true, taxaBlock0.getNtax(), selectedTaxa, splitsBlock0.getCycle(), splitsBlock0, diagram.isUsingWeights());
				rootSplit = RootingUtils.setupForRootedNetwork(true, rootLocation, taxaBlock0, splitsBlock0, taxaBlock, splitsBlock);
			}
			case MidPoint -> {
				taxaBlock = new TaxaBlock();
				splitsBlock = new SplitsBlock();
				final Triplet<Integer, Double, Double> rootLocation = RootingUtils.computeRootLocation(false, taxaBlock0.getNtax(), new HashSet<>(), splitsBlock0.getCycle(), splitsBlock0, diagram.isUsingWeights());
				rootSplit = RootingUtils.setupForRootedNetwork(false, rootLocation, taxaBlock0, splitsBlock0, taxaBlock, splitsBlock);
			}
			case MidPointAlt -> {
				taxaBlock = new TaxaBlock();
				splitsBlock = new SplitsBlock();
				final Triplet<Integer, Double, Double> rootLocation = RootingUtils.computeRootLocation(true, taxaBlock0.getNtax(), new HashSet<>(), splitsBlock0.getCycle(), splitsBlock0, diagram.isUsingWeights());
				rootSplit = RootingUtils.setupForRootedNetwork(true, rootLocation, taxaBlock0, splitsBlock0, taxaBlock, splitsBlock);
			}
		}

		// compute the network and assign coordinates to nodes, and compute loops for outline:

		graph.clear();
		nodeShapeMap.clear();
		loopViews.clear();
		loops.clear();

		if (diagram.isOutline()) {
			try {
				var usedSplits = new BitSet();
				PhylogeneticOutline.apply(progress, diagram.isUsingWeights(), taxaBlock, splitsBlock, graph, nodePointMap, usedSplits, loops, rootSplit, rootAngle);
				if (splitsBlock.getCompatibility() != Compatibility.compatible && splitsBlock.getCompatibility() != Compatibility.cyclic && usedSplits.cardinality() < splitsBlock.getNsplits())
					NotificationManager.showWarning(String.format("Outline algorithm: Showing only %d of %d splits", usedSplits.cardinality(), splitsBlock.getNsplits()));
			} catch (CanceledException e) {
				NotificationManager.showWarning("User CANCELED 'outline' computation");
			}
		} else { // splits
			var usedSplits = new BitSet();
			try {
				if (!EqualAngle.apply(progress, diagram.isUsingWeights(), taxaBlock, splitsBlock, graph, new BitSet(), usedSplits)) {
					ConvexHull.apply(progress, taxaBlock, splitsBlock, graph, usedSplits);
				}
				EqualAngle.assignAnglesToEdges(taxaBlock.getNtax(), splitsBlock, splitsBlock.getCycle(), graph, new BitSet(), rootSplit == 0 ? 360 : rootAngle);
				EqualAngle.assignCoordinatesToNodes(diagram.isUsingWeights(), graph, nodePointMap, splitsBlock.getCycle()[1], rootSplit);

			} catch (CanceledException ignored) {
			}
		}

		progress.setTasks("Drawing", "network");
		progress.setMaximum(graph.getNumberOfNodes() + graph.getNumberOfEdges());
		progress.setProgress(0);

		if (rootSplit > 0) // want the root to be placed on the left by default
			rotate90(graph, nodePointMap);

		var dimensions = computeFontHeightGraphWidthHeight(taxaBlock.getNtax(), t -> taxaBlock.get(t).displayLabelProperty(), graph, false, width, height);
		var fontHeight = dimensions.fontHeight();
		width = dimensions.width();
		height = dimensions.height();

		unitLength.set(normalize(width, height, nodePointMap, true));

		// compute the shapes:

		// nodes:
		var nodesGroup = new Group();
		var nodeLabelsGroup = new Group();

		for (var v : graph.nodes()) {
			var isRootNode = (rootSplit > 0 && v.getDegree() == 1 && graph.getSplit(v.getFirstAdjacentEdge()) == rootSplit);
			var point = nodePointMap.get(v);

			var labeledNode = new LabeledNodeShape(new Circle(v.getDegree() == 1 && !isRootNode ? 1 : 0.5));
			labeledNode.setTranslateX(point.getX());
			labeledNode.setTranslateY(point.getY());

			nodesGroup.getChildren().add(labeledNode);

			var label = LayoutUtils.getLabel(t -> taxaBlock.get(t).displayLabelProperty(), graph, v);

			if (graph.getNumberOfTaxa(v) == 1) {
				labeledNode.setUserData(taxaBlock.get(graph.getTaxon(v)));
			}

			nodeShapeMap.put(v, labeledNode);

			if (label != null && !isRootNode) {
				if (graph.getNumberOfTaxa(v) == 1) {
					taxonLabelMap.put(graph.getTaxon(v), label);
				}

				labeledNode.setLabel(label);

				label.setScale(fontHeight / RichTextLabel.getDefaultFont().getSize());
				label.setTranslateX(labeledNode.getTranslateX() + 10);
				label.setTranslateY(labeledNode.getTranslateY() + 10);
				label.setUserData(labeledNode);
				nodeLabelsGroup.getChildren().add(label);

				label.applyCss();

				double angle = v.adjacentEdgesStream(false).mapToDouble(graph::getAngle).average().orElse(0);
				if (rootSplit == 0 && v == graph.getTaxon2Node(1)) {
					angle += 180;
				}
				var translateXProperty = labeledNode.translateXProperty();
				var translateYProperty = labeledNode.translateYProperty();
				labelLayout.addItem(translateXProperty, translateYProperty, angle, label.widthProperty(), label.heightProperty(),
						xOffset -> {
							label.setLayoutX(0);
							label.translateXProperty().bind(translateXProperty.add(xOffset));
						},
						yOffset -> {
							label.setLayoutY(0);
							label.translateYProperty().bind(translateYProperty.add(yOffset));
						});

				labelLayout.addAvoidable(() -> labeledNode.getTranslateX() - 0.5 * labeledNode.prefWidth(0), () -> labeledNode.getTranslateY() - 0.5 * labeledNode.prefHeight(0), () -> labeledNode.prefWidth(0), () -> labeledNode.prefHeight(0));
			}
			progress.incrementProgress();
		}

		var edgesGroup = new Group();

		var confidenceLabels = new Group();
		var splitsWithConfidenceLabels = new BitSet();

		for (var e : graph.edges()) {
			var line = new Line();
			line.getStyleClass().add("graph-edge");

			line.startXProperty().bind(nodeShapeMap.get(e.getSource()).translateXProperty());
			line.startYProperty().bind(nodeShapeMap.get(e.getSource()).translateYProperty());
			line.endXProperty().bind(nodeShapeMap.get(e.getTarget()).translateXProperty());
			line.endYProperty().bind(nodeShapeMap.get(e.getTarget()).translateYProperty());

			if (graph.getSplit(e) == rootSplit) // is added  split
				line.setStroke(Color.GRAY);
			else
				line.setStroke(null);

			if (Icebergs.enabled()) {
				edgesGroup.getChildren().add(Icebergs.create(line, true));
			}

			edgesGroup.getChildren().add(line);

			var split = graph.getSplit(e);
			splitShapeMap.computeIfAbsent(split, s -> new ArrayList<>()).add(line);

			if (split <= splitsBlock.getNsplits() && splitsBlock.get(split).getConfidence() > 0.05 && !splitsWithConfidenceLabels.get(split)) {
				splitsWithConfidenceLabels.set(split);
				var label = new Label(StringUtils.removeTrailingZerosAfterDot("%.1f", splitsBlock.get(split).getConfidence()));
				if (false)
					label.setStyle("-fx-background-color: rgba(128,128,128,0.2)");
				placeLabel(line, label);
				label.effectProperty().bind(line.effectProperty());
				DraggableUtils.setupDragMouseLayout(label, () -> splitSelectionModel.select(split));
				confidenceLabels.getChildren().add(label);
			}
			progress.incrementProgress();
		}

		var loopsGroup = new Group();
		for (var loop : loops) {
			var loopView = new LoopView(loop, v -> nodeShapeMap.get(v).translateXProperty(), v -> nodeShapeMap.get(v).translateYProperty());
			loopsGroup.getChildren().add(loopView);
			loopViews.add(loopView);
		}
		progress.reportTaskCompleted();

		confidenceLabels.visibleProperty().bind(showConfidence);

		return new Group(loopsGroup, edgesGroup, confidenceLabels, nodesGroup, nodeLabelsGroup);
	}

	private void placeLabel(Line line, Label label) {
		InvalidationListener listener = e -> {
			var dir = new Point2D(line.getStartX() - line.getEndX(), line.getStartY() - line.getEndY()).normalize().multiply(12);
			label.setTranslateX(0.5 * (line.getStartX() + line.getEndX()) - dir.getY() - 12);
			label.setTranslateY(0.5 * (line.getStartY() + line.getEndY()) + dir.getX() - 12);
		};
		line.startXProperty().addListener(listener);
		line.startYProperty().addListener(listener);
		line.endXProperty().addListener(listener);
		line.endYProperty().addListener(listener);
		listener.invalidated(null);
	}

	private void rotate90(PhyloSplitsGraph graph, NodeArray<Point2D> nodePointMap) {
		for (var v : graph.nodes()) {
			nodePointMap.put(v, GeometryUtilsFX.rotate(nodePointMap.get(v), 90));
		}
		for (var e : graph.edges()) {
			graph.setAngle(e, graph.getAngle(e) + 90);
		}
	}

	public RadialLabelLayout getLabelLayout() {
		return labelLayout;
	}

	public PhyloSplitsGraph getGraph() {
		return graph;
	}

	public NodeArray<Point2D> getNodePointMap() {
		return nodePointMap;
	}
}
