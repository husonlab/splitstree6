/*
 *  ComputeOutlineAndReferenceTree.java Copyright (C) 2023 Daniel H. Huson
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
import javafx.scene.paint.Color;
import javafx.scene.shape.Line;
import javafx.scene.shape.Shape;
import jloda.fx.control.RichTextLabel;
import jloda.graph.Node;
import jloda.util.progress.ProgressSilent;
import splitstree6.algorithms.trees.trees2splits.ConsensusOutline;
import splitstree6.algorithms.trees.trees2splits.ConsensusSplits;
import splitstree6.data.SplitsBlock;
import splitstree6.layout.splits.LoopView;
import splitstree6.layout.splits.SplitNetworkLayout;
import splitstree6.layout.tree.LabeledNodeShape;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.function.Consumer;

/**
 * computes an outline and a reference tree
 */
public class ComputeOutlineAndReferenceTree {

	public static Group apply(Model model, boolean showReference, boolean showOther, double width, double height) throws IOException {
		var taxa = model.getTaxaBlock().getTaxaSet();

		var group = new Group();

		var consensusSplits = new SplitsBlock();
		var consensusOutline = new ConsensusOutline();
		consensusOutline.setOptionEdgeWeights(ConsensusSplits.EdgeWeights.Count);
		consensusOutline.compute(new ProgressSilent(), model.getTaxaBlock(), model.getTreesBlock(), consensusSplits);
		var outlineGroup = new Group();
		computeOutline(model, consensusSplits, width, height, g -> outlineGroup.getChildren().add(g), false);
		if (showOther)
			group.getChildren().add(outlineGroup);

		var referenceSplits = new SplitsBlock();
		var consensusMethod = new ConsensusSplits();
		consensusMethod.setOptionConsensus("Greedy");
		consensusMethod.setOptionEdgeWeights(ConsensusSplits.EdgeWeights.Count);
		consensusMethod.compute(new ProgressSilent(), model.getTaxaBlock(), model.getTreesBlock(), referenceSplits);
		referenceSplits.setCycle(consensusSplits.getCycle());
		var referenceGroup = new Group();
		computeOutline(model, referenceSplits, width, height, g -> referenceGroup.getChildren().add(g), true);
		if (showReference)
			group.getChildren().addAll(referenceGroup);

		return group;
	}

	public static void computeOutline(Model model, SplitsBlock splits, double width, double height, Consumer<Group> addGroup, boolean saveLabels) throws IOException {

		SplitNetworkLayout splitNetworkLayout = new SplitNetworkLayout();

		var unitLength = new SimpleDoubleProperty(1.0);

		ObservableMap<Integer, RichTextLabel> taxonLabelMap = FXCollections.observableHashMap();
		ObservableMap<Node, LabeledNodeShape> nodeShapeMap = FXCollections.observableHashMap();
		ObservableMap<Integer, ArrayList<Shape>> splitShapeMap = FXCollections.observableHashMap();
		ObservableList<LoopView> loopViews = FXCollections.observableArrayList();
		splitNetworkLayout.apply(new ProgressSilent(), model.getTaxaBlock(), splits, unitLength, width, height, taxonLabelMap, nodeShapeMap, splitShapeMap, loopViews);
		Platform.runLater(() -> splitNetworkLayout.getLabelLayout().layoutLabels());

		addGroup.accept(asGroup(loopViews));

		var lines = new ArrayList<Shape>();
		for (var splitLines : splitShapeMap.values()) {
			for (var splitLine : splitLines) {
				if (splitLine instanceof Line line) {
					line.setStrokeWidth(2);
					line.setStroke(Color.BLACK);
					lines.add(line);
				}
			}
		}
		addGroup.accept(asGroup(lines));

		addGroup.accept(asGroup(nodeShapeMap.values()));

		if (saveLabels)
			addGroup.accept(asGroup(taxonLabelMap.values()));
	}

	public static Group asGroup(Collection<? extends javafx.scene.Node> nodes) {
		var group = new Group();
		group.getChildren().addAll(nodes);
		return group;
	}
}
