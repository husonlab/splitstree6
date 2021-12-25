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
 *  ComputeSplitNetworkLayout.java Copyright (C) 2021 Daniel H. Huson
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
import javafx.geometry.Point2D;
import javafx.scene.Group;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import javafx.scene.shape.Shape;
import jloda.fx.control.RichTextLabel;
import jloda.fx.selection.SelectionModel;
import jloda.fx.util.TriConsumer;
import jloda.fx.window.MainWindowManager;
import jloda.fx.window.NotificationManager;
import jloda.graph.Edge;
import jloda.graph.Node;
import jloda.graph.NodeArray;
import jloda.phylo.PhyloSplitsGraph;
import jloda.util.BitSetUtils;
import jloda.util.CanceledException;
import jloda.util.IteratorUtils;
import jloda.util.Triplet;
import jloda.util.progress.ProgressListener;
import splitstree6.algorithms.utils.SplitsUtilities;
import splitstree6.data.SplitsBlock;
import splitstree6.data.TaxaBlock;
import splitstree6.data.parts.ASplit;
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
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

import static splitstree6.view.trees.layout.LayoutUtils.computeFontHeightGraphWidthHeight;
import static splitstree6.view.trees.layout.LayoutUtils.normalize;

/**
 * computes the splits network layout
 * Daniel Huson, 12.2021
 */
public class ComputeSplitNetworkLayout {

	public static Group apply(ProgressListener progress, TaxaBlock taxaBlock0, SplitsBlock splitsBlock0, SplitsDiagramType diagram,
							  SplitsRooting rooting, boolean useWeights, SelectionModel<Taxon> taxonSelectionModel,
							  double width, double height, TriConsumer<Node, Shape,
			RichTextLabel> nodeCallback, BiConsumer<Edge, Shape> edgeCallback) throws IOException {

		if (splitsBlock0.getNsplits() == 0)
			return new Group();

		if (splitsBlock0.getCycle() == null || splitsBlock0.getCycle().length == 0) {
			splitsBlock0.setCycle(SplitsUtilities.computeCycle(taxaBlock0.getNtax(), splitsBlock0.getSplits()));
		}

		// if rooting is desired, need to setup a modified set of taxa and splits
		final TaxaBlock taxaBlock;
		final SplitsBlock splitsBlock;
		final boolean rooted;
		switch (rooting) {
			default -> { // no rooting
				rooted = false;
				taxaBlock = taxaBlock0;
				splitsBlock = splitsBlock0;
			}
			case OutGroup -> {
				rooted = true;
				var selectedTaxa = taxonSelectionModel.getSelectedItems().stream().map(taxaBlock0::indexOf).collect(Collectors.toSet());
				taxaBlock = new TaxaBlock();
				splitsBlock = new SplitsBlock();
				final Triplet<Integer, Double, Double> rootingSplit = SplitsUtilities.computeRootLocation(false, taxaBlock0.getNtax(), selectedTaxa, splitsBlock0.getCycle(), splitsBlock0, useWeights);
				setupForRootedNetwork(false, rootingSplit, taxaBlock0, splitsBlock0, taxaBlock, splitsBlock);
			}
			case OutGroupAlt -> {
				rooted = true;
				var selectedTaxa = taxonSelectionModel.getSelectedItems().stream().map(taxaBlock0::indexOf).collect(Collectors.toSet());
				taxaBlock = new TaxaBlock();
				splitsBlock = new SplitsBlock();
				final Triplet<Integer, Double, Double> rootingSplit = SplitsUtilities.computeRootLocation(true, taxaBlock0.getNtax(), selectedTaxa, splitsBlock0.getCycle(), splitsBlock0, useWeights);
				setupForRootedNetwork(true, rootingSplit, taxaBlock0, splitsBlock0, taxaBlock, splitsBlock);
			}
			case MidPoint -> {
				rooted = true;
				taxaBlock = new TaxaBlock();
				splitsBlock = new SplitsBlock();
				final Triplet<Integer, Double, Double> rootingSplit = SplitsUtilities.computeRootLocation(false, taxaBlock0.getNtax(), new HashSet<>(), splitsBlock0.getCycle(), splitsBlock0, useWeights);
				setupForRootedNetwork(false, rootingSplit, taxaBlock0, splitsBlock0, taxaBlock, splitsBlock);
			}
			case MidPointAlt -> {
				rooted = true;
				taxaBlock = new TaxaBlock();
				splitsBlock = new SplitsBlock();
				final Triplet<Integer, Double, Double> rootingSplit = SplitsUtilities.computeRootLocation(true, taxaBlock0.getNtax(), new HashSet<>(), splitsBlock0.getCycle(), splitsBlock0, useWeights);
				setupForRootedNetwork(true, rootingSplit, taxaBlock0, splitsBlock0, taxaBlock, splitsBlock);
			}
		}

		// compute the network and assign coordinates to nodes, and compute loops for outline:
		final var graph = new PhyloSplitsGraph();
		final NodeArray<Point2D> nodePointMap = graph.newNodeArray();
		final var loops = new ArrayList<ArrayList<Node>>();

		if (diagram == SplitsDiagramType.Outline) {
			try {
				var usedSplits = new BitSet();
				Outline.apply(progress, useWeights, taxaBlock, splitsBlock, graph, nodePointMap, usedSplits, loops, rooted);
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
				EqualAngle.assignAnglesToEdges(taxaBlock.getNtax(), splitsBlock, splitsBlock.getCycle(), graph, new BitSet(), rooted ? 160 : 360);
				EqualAngle.assignCoordinatesToNodes(useWeights, graph, nodePointMap, splitsBlock.getCycle()[1]);

			} catch (CanceledException e) {
				NotificationManager.showWarning("User CANCELED 'splits network' computation");
			}
		}

		var triplet = computeFontHeightGraphWidthHeight(taxaBlock, graph, true, width, height);
		var fontHeight = triplet.getFirst();
		width = triplet.getSecond();
		height = triplet.getThird();

		normalize(width, height, nodePointMap);

		// compute the shapes:
		final var color = (MainWindowManager.isUseDarkTheme() ? Color.WHITE : Color.BLACK);

		final NodeArray<DoubleProperty> nodeXMap = graph.newNodeArray();
		final NodeArray<DoubleProperty> nodeYMap = graph.newNodeArray();

		// nodes:
		var nodesGroup = new Group();
		var nodeLabelsGroup = new Group();

		for (var v : graph.nodes()) {
			var point = nodePointMap.get(v);
			var shape = new Circle(v.getDegree() == 1 ? 2 : 0.5);
			shape.setTranslateX(point.getX());
			shape.setStroke(Color.TRANSPARENT);
			shape.setFill(color);

			nodeXMap.put(v, shape.translateXProperty());
			shape.setTranslateY(point.getY());
			nodeYMap.put(v, shape.translateYProperty());
			nodesGroup.getChildren().add(shape);

			var text = LayoutUtils.getLabelText(taxaBlock, graph, v);
			if (text != null && (!rooted || !text.equals("Root"))) {
				var label = new RichTextLabel(text);
				label.setTextFill(color);
				label.setScale(fontHeight / RichTextLabel.DEFAULT_FONT.getSize());
				label.translateXProperty().bind(nodeXMap.get(v).add(10));
				label.translateYProperty().bind(nodeYMap.get(v).add(10));
				nodeLabelsGroup.getChildren().add(label);
				nodeCallback.accept(v, shape, label);
				var taxonId = IteratorUtils.getFirst(graph.getTaxa(v));
				if (taxonId != null)
					shape.setUserData(taxaBlock.get(taxonId));
			}
		}

		var edgesGroup = new Group();
		for (var e : graph.edges()) {
			var line = new Line();
			line.startXProperty().bind(nodeXMap.get(e.getSource()));
			line.startYProperty().bind(nodeYMap.get(e.getSource()));
			line.endXProperty().bind(nodeXMap.get(e.getTarget()));
			line.endYProperty().bind(nodeYMap.get(e.getTarget()));
			line.setStroke(color);
			edgeCallback.accept(e, line);
			edgesGroup.getChildren().add(line);
		}

		var loopsGroup = new Group();
		for (var loop : loops) {
			loopsGroup.getChildren().add((new LoopView(loop, nodeXMap, nodeYMap).getShape()));
		}

		return new Group(loopsGroup, edgesGroup, nodesGroup, nodeLabelsGroup);
	}

	public static void setupForRootedNetwork(boolean altLayout, Triplet<Integer, Double, Double> triplet, TaxaBlock taxaBlockSrc, SplitsBlock splitsBlockSrc, TaxaBlock taxaBlockTarget, SplitsBlock splitsBlockTarget) throws IOException {
		//final Triplet<Integer,Double,Double> triplet= SplitsUtilities.getMidpointSplit(taxaBlockSrc.getNtax(), splitsBlockSrc);
		final int mid = triplet.getFirst();
		final double weightWith1 = triplet.getSecond();
		final double weightOpposite1 = triplet.getThird();

		// modify taxa:
		taxaBlockTarget.clear();
		taxaBlockTarget.setNtax(taxaBlockSrc.getNtax() + 1);
		for (Taxon taxon : taxaBlockSrc.getTaxa())
			taxaBlockTarget.add(taxon);
		final Taxon root = new Taxon("Root");
		taxaBlockTarget.add(root);
		final int rootTaxonId = taxaBlockTarget.indexOf(root);

		// modify cycle:
		final int[] cycle0 = splitsBlockSrc.getCycle();
		final int[] cycle = new int[cycle0.length + 1];
		int first = 0; // first taxon on other side of mid split
		if (!altLayout) {
			final BitSet part = splitsBlockSrc.get(mid).getPartNotContaining(1);
			int t = 1;
			for (int value : cycle0) {
				if (value > 0) {
					if (first == 0 && part.get(value)) {
						first = value;
						cycle[t++] = rootTaxonId;
					}
					cycle[t++] = value;
				}
			}
		} else { // altLayout
			final BitSet part = splitsBlockSrc.get(mid).getPartNotContaining(1);
			int seen = 0;
			int t = 1;
			for (int value : cycle0) {
				if (value > 0) {
					cycle[t++] = value;
					if (part.get(value)) {
						seen++;
						if (seen == part.cardinality()) {
							first = value;
							cycle[t++] = rootTaxonId;
						}
					}
				}
			}
		}
		SplitsUtilities.rotateCycle(cycle, rootTaxonId);

		// setup splits:
		splitsBlockTarget.clear();
		double totalWeight = 0;

		final ASplit mid1 = splitsBlockSrc.get(mid).clone();
		mid1.getPartContaining(1).set(rootTaxonId);
		mid1.setWeight(weightWith1);
		final ASplit mid2 = splitsBlockSrc.get(mid).clone();
		mid2.getPartNotContaining(1).set(rootTaxonId);
		mid2.setWeight(weightOpposite1);

		for (int s = 1; s <= splitsBlockSrc.getNsplits(); s++) {
			if (s == mid) {
				totalWeight += mid1.getWeight();
				splitsBlockTarget.getSplits().add(mid1);
				//splitsBlockTarget.getSplitLabels().put(mid,"BOLD");
			} else {
				final ASplit aSplit = splitsBlockSrc.get(s).clone();

				if (BitSetUtils.contains(mid1.getPartNotContaining(rootTaxonId), aSplit.getA())) {
					aSplit.getB().set(rootTaxonId);
				} else if (BitSetUtils.contains(mid1.getPartNotContaining(rootTaxonId), aSplit.getB())) {
					aSplit.getA().set(rootTaxonId);
				} else if (aSplit.getPartContaining(first).cardinality() > 1)
					aSplit.getPartContaining(first).set(rootTaxonId);
				else
					aSplit.getPartNotContaining(first).set(rootTaxonId);

				splitsBlockTarget.getSplits().add(aSplit);
				totalWeight += aSplit.getWeight();
			}
		}
		// add  new separator split
		{
			totalWeight += mid2.getWeight();
			splitsBlockTarget.getSplits().add(mid2);
			//splitsBlockTarget.getSplitLabels().put(splitsBlockTarget.getNsplits(),"BOLD");
		}
		// add root split:
		{
			final ASplit aSplit = new ASplit(BitSetUtils.asBitSet(rootTaxonId), taxaBlockTarget.getNtax(), totalWeight > 0 ? totalWeight / splitsBlockTarget.getNsplits() : 1);
			splitsBlockTarget.getSplits().add(aSplit);

		}
		splitsBlockTarget.setCycle(cycle, false);
	}

}
