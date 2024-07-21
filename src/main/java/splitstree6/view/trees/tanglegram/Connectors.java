/*
 *  Connectors.java Copyright (C) 2024 Daniel H. Huson
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

package splitstree6.view.trees.tanglegram;

import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ObjectProperty;
import javafx.collections.SetChangeListener;
import javafx.collections.WeakSetChangeListener;
import javafx.scene.Group;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.*;
import javafx.scene.text.Text;
import jloda.fx.util.RunAfterAWhile;
import jloda.fx.util.SelectionEffectBlue;
import jloda.phylo.PhyloGraph;
import splitstree6.data.parts.Taxon;
import splitstree6.main.SplitsTree6;
import splitstree6.window.MainWindow;

import java.util.HashMap;
import java.util.Map;

/**
 * draws connectors between any nodes that have the same user string
 * Daniel Huson, 12.2021
 */
public class Connectors {
	private final MainWindow mainWindow;
	private final Pane drawPane;
	private final Pane tree1Pane;
	private final Pane tree2Pane;

	private Color strokeColor;
	private double strokeWidth;

	private final Group group = new Group();

	private final Map<jloda.graph.Node, ? extends javafx.scene.Node> nodeShapeMap1;
	private final Map<jloda.graph.Node, ? extends javafx.scene.Node> nodeShapeMap2;
	private final Map<Taxon, Shape> taxonConnectorMap = new HashMap<>();

	private final SetChangeListener<Taxon> selectionListener;

	public Connectors(MainWindow mainWindow, Pane drawPane, Pane tree1Pane, Map<jloda.graph.Node, ? extends javafx.scene.Node> nodeShapeMap1, Pane tree2Pane,
					  Map<jloda.graph.Node, ? extends javafx.scene.Node> nodeShapeMap2, ObjectProperty<Color> strokeColor, DoubleProperty strokeWidth) {
		this.mainWindow = mainWindow;
		this.drawPane = drawPane;
		this.tree1Pane = tree1Pane;
		this.nodeShapeMap1 = nodeShapeMap1;
		this.tree2Pane = tree2Pane;
		this.nodeShapeMap2 = nodeShapeMap2;

		this.strokeColor = strokeColor.get();
		strokeColor.addListener((v, o, n) -> {
			group.getChildren().stream().map(item -> (Line) item).forEach(line -> line.setStroke(n));
			this.strokeColor = n;
		});

		this.strokeWidth = strokeWidth.get();

		strokeWidth.addListener((v, o, n) -> {
			group.getChildren().stream().map(item -> (Line) item).forEach(line -> line.setStrokeWidth(n.doubleValue()));
			this.strokeWidth = n.doubleValue();
		});

		drawPane.getChildren().add(group);

		selectionListener = e -> {
			if (e.wasAdded()) {
				var shape = taxonConnectorMap.get(e.getElementAdded());
				if (shape != null)
					shape.setEffect(SelectionEffectBlue.getInstance());
			}
			if (e.wasRemoved()) {
				var shape = taxonConnectorMap.get(e.getElementRemoved());
				if (shape != null)
					shape.setEffect(null);

			}
		};
		mainWindow.getTaxonSelectionModel().getSelectedItems().addListener(new WeakSetChangeListener<>(selectionListener));

		tree1Pane.boundsInParentProperty().addListener(e -> update());
		tree2Pane.boundsInParentProperty().addListener(e -> update());

		update();
	}

	private int count = 0;

	public void update() {
		RunAfterAWhile.applyInFXThread(this, () -> {
			// System.err.println("update "+(++count));

			taxonConnectorMap.clear();

			group.getChildren().clear();

			if (false) {
				for (var y = 0; y < 1500; y += 50) {
					var text = new Text(10, y, "" + y);
					text.setFill(Color.BLUE);
					group.getChildren().add(text);
				}

				for (var y = 0; y < 1500; y += 50) {
					var text = new Text(50, y, "" + y);
					text.setFill(Color.RED);
					group.getChildren().add(text);
				}
			}


			if (!nodeShapeMap1.isEmpty() && !nodeShapeMap2.isEmpty()) {
				var taxaBlock = mainWindow.getWorkflow().getWorkingTaxaBlock();
				var taxonShapeMap1 = new HashMap<Taxon, javafx.scene.Node>();
				{
					var graph1 = (PhyloGraph) nodeShapeMap1.keySet().iterator().next().getOwner();
					for (var v : nodeShapeMap1.keySet()) {
						for (var t : graph1.getTaxa(v)) {
							var taxon = taxaBlock.get(t);
							taxonShapeMap1.put(taxon, nodeShapeMap1.get(v));
						}
					}
				}
				var taxonShapeMap2 = new HashMap<Taxon, javafx.scene.Node>();
				{
					var graph2 = (PhyloGraph) nodeShapeMap2.keySet().iterator().next().getOwner();
					for (var v : nodeShapeMap2.keySet()) {
						for (var t : graph2.getTaxa(v)) {
							var taxon = taxaBlock.get(t);
							taxonShapeMap2.put(taxon, nodeShapeMap2.get(v));
						}
					}
				}

				for (var taxon : taxonShapeMap1.keySet()) {
					var shape1 = taxonShapeMap1.get(taxon);
					var shape2 = taxonShapeMap2.get(taxon);
					if (shape1 != null && shape2 != null) {
						var line = new Path();
						line.setPickOnBounds(false);
						group.getChildren().add(line);
						taxonConnectorMap.put(taxon, line);

						line.getStyleClass().add("graph-special-edge");

						line.setOnMouseClicked(e -> {
							if (!e.isShiftDown() && SplitsTree6.isDesktop())
								mainWindow.getTaxonSelectionModel().clearSelection();
							mainWindow.getTaxonSelectionModel().toggleSelection(taxon);
						});

						if (mainWindow.getTaxonSelectionModel().isSelected(taxon))
							line.setEffect(SelectionEffectBlue.getInstance());

						var screenStartPoint = shape1.getParent().localToScreen(shape1.getTranslateX(), shape1.getTranslateY());
						var screenEndPoint = shape2.getParent().localToScreen(shape2.getTranslateX(), shape2.getTranslateY());

						if (screenStartPoint != null && screenEndPoint != null) {
							var localStartPoint = line.screenToLocal(screenStartPoint);
							var localEndPoint = line.screenToLocal(screenEndPoint);
							if (localStartPoint != null && localEndPoint != null) {
								var moveTo = new MoveTo(0, localStartPoint.getY());
								var cubicCurveTo = new CubicCurveTo(0.3 * drawPane.getWidth(), localStartPoint.getY(),
										0.7 * drawPane.getWidth(), localEndPoint.getY(),
										drawPane.getWidth(), localEndPoint.getY());
								line.getElements().add(moveTo);
								line.getElements().add(cubicCurveTo);
							}
						} else {
							group.getChildren().remove(line);
						}
					}
				}
			}
		});
	}
}
