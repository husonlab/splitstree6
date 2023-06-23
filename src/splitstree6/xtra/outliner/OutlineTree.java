/*
 *  OutlineTree.java Copyright (C) 2023 Daniel H. Huson
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

package splitstree6.xtra.outliner;

import javafx.application.Platform;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.ObservableMap;
import javafx.scene.Group;
import javafx.scene.effect.BlendMode;
import javafx.scene.effect.DropShadow;
import javafx.scene.effect.Glow;
import javafx.scene.paint.Color;
import javafx.scene.shape.Line;
import javafx.scene.shape.Shape;
import javafx.scene.shape.StrokeLineCap;
import jloda.fx.control.RichTextLabel;
import jloda.graph.Node;
import jloda.util.progress.ProgressSilent;
import splitstree6.algorithms.splits.splits2trees.GreedyTree;
import splitstree6.algorithms.trees.trees2splits.ConsensusOutline;
import splitstree6.algorithms.trees.trees2splits.ConsensusSplits;
import splitstree6.algorithms.utils.TreesUtilities;
import splitstree6.data.SplitsBlock;
import splitstree6.data.parts.ASplit;
import splitstree6.data.parts.Compatibility;
import splitstree6.layout.splits.LoopView;
import splitstree6.layout.splits.SplitNetworkLayout;
import splitstree6.layout.tree.LabeledNodeShape;
import splitstree6.layout.tree.LayoutTreeRadial;
import splitstree6.layout.tree.RadialLabelLayout;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Map;
import java.util.function.Consumer;

import static splitstree6.xtra.outliner.ComputeOutlineAndReferenceTree.asGroup;

public class OutlineTree {
	public static Group apply(Model model, double width, double height) throws IOException {
		var taxa = model.getTaxaBlock().getTaxaSet();

		var consensusSplits = new SplitsBlock();
		var consensusOutline = new ConsensusOutline();
		consensusOutline.setOptionEdgeWeights(ConsensusSplits.EdgeWeights.Count);
		consensusOutline.compute(new ProgressSilent(), model.getTaxaBlock(), model.getTreesBlock(), consensusSplits);
		var cycle = consensusSplits.getCycle();

		var referenceSplits = new SplitsBlock();
		var consensusMethod = new ConsensusSplits();
		consensusMethod.setOptionConsensus("Greedy");
		consensusMethod.setOptionEdgeWeights(ConsensusSplits.EdgeWeights.Count);
		consensusMethod.compute(new ProgressSilent(), model.getTaxaBlock(), model.getTreesBlock(), referenceSplits);
		referenceSplits.setCycle(cycle);

		var nodeLabelsGroup = new Group();
		var lines = computeTree(model, referenceSplits, width, height, nodeLabelsGroup);

		for (var r = 1; r <= referenceSplits.getNsplits(); r++) {
			var count = 0;
			for (var s = 1; s <= consensusSplits.getNsplits(); s++) {
				if (!Compatibility.isCompatible(referenceSplits.get(r), consensusSplits.get(s))) {
					count++;
				}
			}
			lines[r].setStrokeWidth(1 + 400 * (double) count / consensusSplits.size());
		}

		var outlineEdges = new Group();
		var treeEdges = new Group();

		for (var line : lines) {
			if (line != null) {
				line.setStroke(Color.BLACK);
				line.setStrokeLineCap(StrokeLineCap.ROUND);
				line.setStroke(Color.WHITE);
				outlineEdges.getChildren().add(line);

				var other = new Line(line.getStartX(), line.getStartY(), line.getEndX(), line.getEndY());
				other.setStrokeWidth(1.0);
				other.setStrokeLineCap(StrokeLineCap.ROUND);

				treeEdges.getChildren().add(other);
			}
		}

		var dropShadow = new DropShadow();
		dropShadow.setColor(Color.BLACK); // Set the shadow color
		dropShadow.setRadius(1); // Set the shadow radius
		dropShadow.setSpread(0); // Set the spread to create a solid outline

		outlineEdges.setEffect(dropShadow);
		outlineEdges.setBlendMode(BlendMode.SRC_ATOP);
		return new Group(outlineEdges, treeEdges, nodeLabelsGroup);
	}

	private static Line[] computeTree(Model model, SplitsBlock splits, double width, double height, Group nodesAndLabels) throws IOException {

		SplitNetworkLayout splitNetworkLayout = new SplitNetworkLayout();

		var unitLength = new SimpleDoubleProperty(1.0);

		ObservableMap<Integer, RichTextLabel> taxonLabelMap = FXCollections.observableHashMap();
		ObservableMap<Node, LabeledNodeShape> nodeShapeMap = FXCollections.observableHashMap();
		ObservableMap<Integer, ArrayList<Shape>> splitShapeMap = FXCollections.observableHashMap();
		ObservableList<LoopView> loopViews = FXCollections.observableArrayList();
		splitNetworkLayout.apply(new ProgressSilent(), model.getTaxaBlock(), splits, unitLength, width, height, taxonLabelMap, nodeShapeMap, splitShapeMap, loopViews);
		Platform.runLater(() -> splitNetworkLayout.getLabelLayout().layoutLabels());

		nodesAndLabels.getChildren().addAll(nodeShapeMap.values());
		nodesAndLabels.getChildren().addAll(taxonLabelMap.values());

		var lines = new Line[splits.getNsplits() + 1];

		for (var s : splitShapeMap.keySet()) {
			var splitLines = splitShapeMap.get(s);
			for (var splitLine : splitLines) {
				if (splitLine instanceof Line line) {
					line.setStrokeWidth(2);
					line.setStroke(Color.BLACK);
					lines[s] = line; // should only be one line per split
					break;
				}
			}
		}

		return lines;
	}
}
