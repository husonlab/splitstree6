/*
 * LayoutTester.java Copyright (C) 2022 Daniel H. Huson
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

package splitstree6.densitree;

import javafx.application.Application;
import javafx.beans.InvalidationListener;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.value.ChangeListener;
import javafx.geometry.Orientation;
import javafx.geometry.Point2D;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Pane;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import javafx.stage.Stage;
import jloda.fx.control.RichTextLabel;
import jloda.fx.util.GeometryUtilsFX;
import jloda.graph.NodeArray;
import jloda.util.progress.ProgressSilent;
import splitstree6.data.TreesBlock;
import splitstree6.io.readers.trees.NewickReader;

public class LayoutTester extends Application {
	@Override
	public void start(Stage primaryStage) throws Exception {
		// var file = "examples/trees/trees-10000x100.tre";
		//var file = "examples/trees/bees.tre";
		var file = "examples/beast2/flu.new";
		//var file = "examples/beast2/primates-mtDNA.new";
		//var file="examples/trees49.tre";
		//var file="examples/trees/full-1001.tree";

		var treeNumber = new SimpleIntegerProperty(0);

		var model = new Model();
		var newickReader = new NewickReader();
		newickReader.read(new ProgressSilent(), file, model.getTaxaBlock(), model.getTreesBlock());

		model.setCircularOrdering(Utilities.computeCycle(model.getTaxaBlock().getTaxaSet(), model.getTreesBlock()));

		var consensusTree = MajorityConsensus.apply(model);
		System.err.printf("Majority consensus: %,d nodes and %,d edges%n", consensusTree.getNumberOfNodes(), consensusTree.getNumberOfEdges());

		var pane = new Pane();
		pane.setPrefWidth(800);
		pane.setPrefHeight(800);

		var borderPane = new BorderPane();

		var prev = new Button("<");
		prev.setOnAction(e -> treeNumber.set(treeNumber.get() - 1));
		prev.disableProperty().bind(treeNumber.lessThanOrEqualTo(1));
		var next = new Button(">");
		next.setOnAction(e -> treeNumber.set(treeNumber.get() + 1));
		next.disableProperty().bind(treeNumber.greaterThanOrEqualTo(model.getTreesBlock().getNTrees()));
		var number = new Label();
		number.textProperty().bind(treeNumber.asString());

		var toScale = new CheckBox("To ScalingType");
		var useOwnCycle = new CheckBox("Use Own Cycle");

		borderPane.setCenter(pane);
		borderPane.setTop(new ToolBar(prev, next, number, new Separator(Orientation.VERTICAL), toScale, useOwnCycle));

		final InvalidationListener invalidationListener = o -> {
			var tree = model.getTreesBlock().getTree(treeNumber.get());

			var cycle = model.getCircularOrdering();
			if (useOwnCycle.isSelected()) {
				var treesBlock = new TreesBlock();
				treesBlock.getTrees().add(tree);
				cycle = Utilities.computeCycle(model.getTaxaBlock().getTaxaSet(), treesBlock);
			}

			try (NodeArray<Point2D> nodePointMap = tree.newNodeArray(); var nodeAngleMap = tree.newNodeDoubleArray()) {
				//LayoutAlgorithm.update(tree, toScale.isSelected(), cycle, nodePointMap, nodeAngleMap);
				adjustCoordinatesToBox(nodePointMap, 100, 100, 800, 800);

				pane.getChildren().clear();
				for (var e : tree.edges()) {
					var v = e.getSource();
					var w = e.getTarget();
					var vPt = nodePointMap.get(v);
					var wPt = nodePointMap.get(w);
					var line = new Line(vPt.getX(), vPt.getY(), wPt.getX(), wPt.getY());
					pane.getChildren().add(line);
					if (e.getTarget().isLeaf()) {
						var label = new RichTextLabel(tree.getLabel(w));
						ChangeListener<Number> listener = (observableValue, oldValue, newValue) -> { // use a listener because we have to wait until both width and height have been set
							if (oldValue.doubleValue() == 0 && newValue.doubleValue() > 0 && label.getWidth() > 0 && label.getHeight() > 0) {
								var angle = nodeAngleMap.get(w);
								var delta = GeometryUtilsFX.translateByAngle(-0.5 * label.getWidth(), -0.5 * label.getHeight(), angle, 0.5 * label.getWidth() + 5);
								label.setLayoutX(wPt.getX() + delta.getX());
								label.setLayoutY(wPt.getY() + delta.getY());
								label.setRotate(angle);
								label.ensureUpright();
							}
						};
						label.widthProperty().addListener(listener);
						label.heightProperty().addListener(listener);
						pane.getChildren().add(label);
					}
					if (v == tree.getRoot()) {
						var circle = new Circle(2);
						circle.setTranslateX(vPt.getX());
						circle.setTranslateY(vPt.getY());
						pane.getChildren().add(circle);
					}
					if (w.isLeaf()) {
						var circle = new Circle(2);
						circle.setTranslateX(wPt.getX());
						circle.setTranslateY(wPt.getY());
						pane.getChildren().add(circle);
					}
				}
			}
		};
		treeNumber.addListener(invalidationListener);
		toScale.selectedProperty().addListener(invalidationListener);
		useOwnCycle.selectedProperty().addListener(invalidationListener);

		treeNumber.set(1);

		var scene = new Scene(borderPane);
		primaryStage.setTitle("test layout");
		primaryStage.setScene(scene);
		primaryStage.sizeToScene();
		primaryStage.show();
	}

	private void adjustCoordinatesToBox(NodeArray<Point2D> nodePointMap, double xMinTarget, int yMinTarget, double xMaxTarget, double yMaxTarget) {

		var xMin = nodePointMap.values().stream().mapToDouble(Point2D::getX).min().orElse(0);
		var xMax = nodePointMap.values().stream().mapToDouble(Point2D::getX).max().orElse(0);
		var yMin = nodePointMap.values().stream().mapToDouble(Point2D::getY).min().orElse(0);
		var yMax = nodePointMap.values().stream().mapToDouble(Point2D::getY).max().orElse(0);

		var scaleX = (xMaxTarget - xMinTarget) / (xMax - xMin);
		var scaleY = (yMaxTarget - yMinTarget) / (yMax - yMin);
		var scale = Math.min(scaleX, scaleY);

		for (var v : nodePointMap.keySet()) {
			var point = nodePointMap.get(v);
			nodePointMap.put(v, point.multiply(scale).add(0.5 * (xMinTarget + xMaxTarget), 0.5 * (yMinTarget + yMaxTarget)));
		}
	}
}
