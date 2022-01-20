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
 *  SplitNetworkLayout.java Copyright (C) 2021 Daniel H. Huson
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

package splitstree6.view.splits.layout;

import javafx.beans.property.DoubleProperty;
import javafx.collections.ObservableMap;
import javafx.geometry.Point2D;
import javafx.scene.Group;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import javafx.scene.shape.Shape;
import jloda.fx.control.RichTextLabel;
import jloda.fx.selection.SelectionModel;
import jloda.fx.util.GeometryUtilsFX;
import jloda.fx.window.MainWindowManager;
import jloda.fx.window.NotificationManager;
import jloda.graph.Node;
import jloda.graph.NodeArray;
import jloda.phylo.PhyloSplitsGraph;
import jloda.util.CanceledException;
import jloda.util.IteratorUtils;
import jloda.util.Triplet;
import jloda.util.progress.ProgressListener;
import splitstree6.algorithms.utils.SplitsUtilities;
import splitstree6.data.SplitsBlock;
import splitstree6.data.TaxaBlock;
import splitstree6.data.parts.Taxon;
import splitstree6.view.splits.layout.algorithms.ConvexHull;
import splitstree6.view.splits.layout.algorithms.EqualAngle;
import splitstree6.view.splits.layout.algorithms.Outline;
import splitstree6.view.splits.viewer.LoopView;
import splitstree6.view.splits.viewer.SplitsDiagramType;
import splitstree6.view.splits.viewer.SplitsRooting;
import splitstree6.view.trees.layout.LayoutUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashSet;
import java.util.stream.Collectors;

import static splitstree6.view.trees.layout.LayoutUtils.computeFontHeightGraphWidthHeight;
import static splitstree6.view.trees.layout.LayoutUtils.normalize;

/**
 * computes the splits network layout
 * Daniel Huson, 12.2021
 */
public class SplitNetworkLayout {
	private final RadialLabelLayout labelLayout;

	private final PhyloSplitsGraph graph = new PhyloSplitsGraph();
	private final NodeArray<Point2D> nodePointMap = graph.newNodeArray();
	private final ArrayList<ArrayList<Node>> loops = new ArrayList<>();
	private final NodeArray<DoubleProperty> nodeXMap = graph.newNodeArray();
	private final NodeArray<DoubleProperty> nodeYMap = graph.newNodeArray();

	public SplitNetworkLayout() {
		labelLayout = new RadialLabelLayout();
	}

	/**
	 * compute an outline or network
	 *
	 * @param progress
	 * @param taxaBlock0
	 * @param splitsBlock0
	 * @param diagram
	 * @param rooting
	 * @param useWeights
	 * @param taxonSelectionModel
	 * @param splitSelectionModel
	 * @param unitLength
	 * @param width
	 * @param height
	 * @return group of groups, namely loops, nodes, edges and node labels
	 * @throws IOException
	 */
	public Group apply(ProgressListener progress, TaxaBlock taxaBlock0, SplitsBlock splitsBlock0, SplitsDiagramType diagram,
					   SplitsRooting rooting, double rootAngle, boolean useWeights, SelectionModel<Taxon> taxonSelectionModel, SelectionModel<Integer> splitSelectionModel,
					   ObservableMap<Taxon, RichTextLabel> taxonLabelMap, ObservableMap<Integer, ArrayList<Shape>> splitShapeMap, DoubleProperty unitLength,
					   double width, double height) throws IOException {

		labelLayout.clear();

		if (splitsBlock0.getNsplits() == 0)
			return new Group();

		if (splitsBlock0.getCycle() == null || splitsBlock0.getCycle().length == 0) {
			splitsBlock0.setCycle(SplitsUtilities.computeCycle(taxaBlock0.getNtax(), splitsBlock0.getSplits()));
		}

		// if rooting is desired, need to setup a modified set of taxa and splits
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
				final Triplet<Integer, Double, Double> rootLocation = RootingUtils.computeRootLocation(false, taxaBlock0.getNtax(), selectedTaxa, splitsBlock0.getCycle(), splitsBlock0, useWeights);
				rootSplit = RootingUtils.setupForRootedNetwork(false, rootLocation, taxaBlock0, splitsBlock0, taxaBlock, splitsBlock);
			}
			case OutGroupAlt -> {
				var selectedTaxa = taxonSelectionModel.getSelectedItems().stream().map(taxaBlock0::indexOf).collect(Collectors.toSet());
				taxaBlock = new TaxaBlock();
				splitsBlock = new SplitsBlock();
				final Triplet<Integer, Double, Double> rootLocation = RootingUtils.computeRootLocation(true, taxaBlock0.getNtax(), selectedTaxa, splitsBlock0.getCycle(), splitsBlock0, useWeights);
				rootSplit = RootingUtils.setupForRootedNetwork(true, rootLocation, taxaBlock0, splitsBlock0, taxaBlock, splitsBlock);
			}
			case MidPoint -> {
				taxaBlock = new TaxaBlock();
				splitsBlock = new SplitsBlock();
				final Triplet<Integer, Double, Double> rootLocation = RootingUtils.computeRootLocation(false, taxaBlock0.getNtax(), new HashSet<>(), splitsBlock0.getCycle(), splitsBlock0, useWeights);
				rootSplit = RootingUtils.setupForRootedNetwork(false, rootLocation, taxaBlock0, splitsBlock0, taxaBlock, splitsBlock);
			}
			case MidPointAlt -> {
				taxaBlock = new TaxaBlock();
				splitsBlock = new SplitsBlock();
				final Triplet<Integer, Double, Double> rootLocation = RootingUtils.computeRootLocation(true, taxaBlock0.getNtax(), new HashSet<>(), splitsBlock0.getCycle(), splitsBlock0, useWeights);
				rootSplit = RootingUtils.setupForRootedNetwork(true, rootLocation, taxaBlock0, splitsBlock0, taxaBlock, splitsBlock);
			}
		}

		// interaction support:
		var interactionSetup = new InteractionSetup(taxaBlock, splitsBlock, taxonSelectionModel, splitSelectionModel);
		var nodeCallback = interactionSetup.createNodeCallback();
		var edgeCallback = interactionSetup.createEdgeCallback();

		// compute the network and assign coordinates to nodes, and compute loops for outline:

		graph.clear();

		if (diagram == SplitsDiagramType.Outline) {
			try {
				var usedSplits = new BitSet();
				Outline.apply(progress, useWeights, taxaBlock, splitsBlock, graph, nodePointMap, usedSplits, loops, rootSplit, rootAngle);
				if (usedSplits.cardinality() < splitsBlock.getNsplits())
					NotificationManager.showWarning(String.format("Outline algorithm: Showing only %d of %d splits", usedSplits.cardinality(), splitsBlock.getNsplits()));
			} catch (CanceledException e) {
				NotificationManager.showWarning("User CANCELED 'outline' computation");
			}
		} else { // splits
			var usedSplits = new BitSet();
			try {
				EqualAngle.apply(progress, useWeights, taxaBlock, splitsBlock, graph, new BitSet(), usedSplits);
				if (usedSplits.cardinality() < splitsBlock.getNsplits()) {
					ConvexHull.apply(progress, taxaBlock, splitsBlock, graph, usedSplits);
				}
				EqualAngle.assignAnglesToEdges(taxaBlock.getNtax(), splitsBlock, splitsBlock.getCycle(), graph, new BitSet(), rootSplit == 0 ? 360 : rootAngle);
				EqualAngle.assignCoordinatesToNodes(useWeights, graph, nodePointMap, splitsBlock.getCycle()[1], rootSplit);

			} catch (CanceledException e) {
				NotificationManager.showWarning("User CANCELED 'splits network' computation");
			}
		}

		if (rootSplit > 0) // want the root to be placed on the left by default
			rotate90(graph, nodePointMap);

		var triplet = computeFontHeightGraphWidthHeight(taxaBlock, graph, true, width, height);
		var fontHeight = triplet.getFirst();
		width = triplet.getSecond();
		height = triplet.getThird();

		unitLength.set(normalize(width, height, nodePointMap, true));

		// compute the shapes:
		final var color = (MainWindowManager.isUseDarkTheme() ? Color.WHITE : Color.BLACK);

		nodeXMap.clear();
		nodeYMap.clear();

		// nodes:
		var nodesGroup = new Group();
		var nodeLabelsGroup = new Group();

		taxonLabelMap.clear();

		for (var v : graph.nodes()) {
			var isRootNode = (rootSplit > 0 && v.getDegree() == 1 && graph.getSplit(v.getFirstAdjacentEdge()) == rootSplit);
			var point = nodePointMap.get(v);
			var shape = new Circle(v.getDegree() == 1 && !isRootNode ? 2 : 0.5);
			shape.setTranslateX(point.getX());
			shape.setStroke(Color.TRANSPARENT);
			shape.setFill(color);

			nodeXMap.put(v, shape.translateXProperty());
			shape.setTranslateY(point.getY());
			nodeYMap.put(v, shape.translateYProperty());
			nodesGroup.getChildren().add(shape);

			var text = LayoutUtils.getLabelText(taxaBlock, graph, v);
			if (text != null && !isRootNode) {
				var label = new RichTextLabel(text);
				// todo: need to change stuff so that each node has at most one taxon
				taxonLabelMap.put(taxaBlock.get(graph.getTaxa(v).iterator().next()), label);

				label.setTextFill(color);
				label.setScale(fontHeight / RichTextLabel.DEFAULT_FONT.getSize());
				label.setTranslateX(nodeXMap.get(v).doubleValue() + 10);
				label.setTranslateY(nodeYMap.get(v).doubleValue() + 10);
				nodeLabelsGroup.getChildren().add(label);
				nodeCallback.accept(v, shape, label);
				var taxonId = IteratorUtils.getFirst(graph.getTaxa(v));
				if (taxonId != null)
					shape.setUserData(taxaBlock.get(taxonId));

				double angle = v.adjacentEdgesStream(false).mapToDouble(graph::getAngle).average().orElse(0);
				if (rootSplit == 0 && v == graph.getTaxon2Node(1)) {
					angle += 180;
				}
				var translateXProperty = shape.translateXProperty();
				var translateYProperty = shape.translateYProperty();
				labelLayout.addItem(translateXProperty, translateYProperty, angle, label.widthProperty(), label.heightProperty(),
						xOffset -> label.translateXProperty().bind(translateXProperty.add(xOffset)), yOffset -> label.translateYProperty().bind(translateYProperty.add(yOffset)));
				labelLayout.addAvoidable(translateXProperty, translateYProperty, 2 * shape.getRadius(), 2 * shape.getRadius());
			}
		}

		var edgesGroup = new Group();
		for (var e : graph.edges()) {
			var line = new Line();
			line.startXProperty().bind(nodeXMap.get(e.getSource()));
			line.startYProperty().bind(nodeYMap.get(e.getSource()));
			line.endXProperty().bind(nodeXMap.get(e.getTarget()));
			line.endYProperty().bind(nodeYMap.get(e.getTarget()));
			if (graph.getSplit(e) == rootSplit) // is added  split
				line.setStroke(Color.GRAY);
			else
				line.setStroke(color);
			edgeCallback.accept(e, line);
			edgesGroup.getChildren().add(line);

			var split = graph.getSplit(e);
			splitShapeMap.computeIfAbsent(split,s->new ArrayList<>()).add(line);
		}

		var loopsGroup = new Group();
		for (var loop : loops) {
			loopsGroup.getChildren().add(new LoopView(loop, nodeXMap, nodeYMap).getShape());
		}

		return new Group(loopsGroup, edgesGroup, nodesGroup, nodeLabelsGroup);
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
}
