/*
 * Connectors.java Copyright (C) 2022 Daniel H. Huson
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

package splitstree6.view.trees.tanglegram;

import javafx.application.Platform;
import javafx.beans.InvalidationListener;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ObjectProperty;
import javafx.collections.SetChangeListener;
import javafx.collections.WeakSetChangeListener;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.*;
import jloda.fx.util.BasicFX;
import jloda.fx.util.SelectionEffectBlue;
import splitstree6.data.parts.Taxon;
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

	private final Map<Taxon, Shape> taxonShapeMap = new HashMap<>();

	private final SetChangeListener<Taxon> selectionListener;

	public Connectors(MainWindow mainWindow, Pane drawPane, Pane tree1Pane, Pane tree2Pane, ObjectProperty<Color> strokeColor, DoubleProperty strokeWidth) {
		this.mainWindow = mainWindow;
		this.drawPane = drawPane;
		this.tree1Pane = tree1Pane;
		this.tree2Pane = tree2Pane;

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
				var shape = taxonShapeMap.get(e.getElementAdded());
				if (shape != null)
					shape.setEffect(SelectionEffectBlue.getInstance());
			}
			if (e.wasRemoved()) {
				var shape = taxonShapeMap.get(e.getElementRemoved());
				if (shape != null)
					shape.setEffect(null);

			}
		};
		mainWindow.getTaxonSelectionModel().getSelectedItems().addListener(new WeakSetChangeListener<>(selectionListener));

		update();
	}

	public void update() {
		var taxonBlock = mainWindow.getWorkflow().getWorkingTaxaBlock();

		taxonShapeMap.clear();
		var map1 = new HashMap<Taxon, Node>();
		for (var node : BasicFX.getAllRecursively(tree1Pane, Shape.class)) {
			if (node.getUserData() instanceof Integer taxon) {
				map1.put(taxonBlock.get(taxon), node);
			}
		}
		var map2 = new HashMap<Taxon, Node>();
		for (var node : BasicFX.getAllRecursively(tree2Pane, Shape.class)) {
			if (node.getUserData() instanceof Integer taxon) {
				map2.put(taxonBlock.get(taxon), node);
			}
		}

		group.getChildren().clear();

		if (map1.size() > 0 && map2.size() > 0) {
			for (var taxon : map1.keySet()) {
				var node1 = map1.get(taxon);
				var node2 = map2.get(taxon);
				if (node1 != null && node2 != null) {
					var line = new Path();
					group.getChildren().add(line);
					taxonShapeMap.put(taxon, line);

					line.setStrokeWidth(strokeWidth);
					line.setStroke(strokeColor);
					line.setOnMouseClicked(e -> {
						if (!e.isShiftDown())
							mainWindow.getTaxonSelectionModel().clearSelection();
						mainWindow.getTaxonSelectionModel().toggleSelection(taxon);
					});

					if (mainWindow.getTaxonSelectionModel().isSelected(taxon))
						line.setEffect(SelectionEffectBlue.getInstance());

					InvalidationListener invalidationListener = e -> Platform.runLater(() -> {
						line.getElements().clear();
						var screenStartPoint = node1.getParent().localToScreen(node1.getTranslateX(), node1.getTranslateY());
						var screenEndPoint = node2.getParent().localToScreen(node2.getTranslateX(), node2.getTranslateY());
						if (screenStartPoint != null && screenEndPoint != null) {
							var localStartPoint = line.screenToLocal(screenStartPoint);
							var localEndPoint = line.screenToLocal(screenEndPoint);
							if (localStartPoint != null && localEndPoint != null) {
								line.getElements().add(new MoveTo(0, localStartPoint.getY()));
								line.getElements().add(new CubicCurveTo(0.3 * drawPane.getWidth(), localStartPoint.getY(),
										0.7 * drawPane.getWidth(), localEndPoint.getY(),
										drawPane.getWidth(), localEndPoint.getY()));
							}
						} else
							group.getChildren().remove(line);
					});
					tree1Pane.boundsInParentProperty().addListener(invalidationListener);
					tree2Pane.boundsInParentProperty().addListener(invalidationListener);
					invalidationListener.invalidated(null);
				}
			}
		}
	}
}
