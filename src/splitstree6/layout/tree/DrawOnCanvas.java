/*
 * DrawOnCanvas.java Copyright (C) 2022 Daniel H. Huson
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

package splitstree6.layout.tree;

import javafx.beans.property.StringProperty;
import javafx.geometry.Point3D;
import javafx.scene.Scene;
import javafx.scene.SnapshotParameters;
import javafx.scene.canvas.Canvas;
import javafx.scene.image.WritableImage;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.stage.Stage;
import jloda.fx.util.AService;
import jloda.phylo.PhyloTree;
import jloda.util.Basic;

import java.util.function.Function;

/**
 * computes a tree visualization and draws it on a canvas
 * Daniel Huson, 3.2022
 */
public class DrawOnCanvas {
	private final AService<ComputeTreeLayout.Result> service = new AService<>();
	private final Canvas canvas;

	public DrawOnCanvas() {
		canvas = new Canvas(850, 850);
		var stackPane = new StackPane();
		var rectangle = new Rectangle(60, 60, 120, 120);
		rectangle.setFill(Color.LIGHTBLUE);
		stackPane.getChildren().addAll(rectangle, canvas);
		var scene = new Scene(stackPane, 850, 850);
		var stage = new Stage();
		stage.setScene(scene);
		stage.setTitle("Canvas");
		stage.sizeToScene();
		stage.show();


		if (true) { // DHH: this is to see what a canvas looks like when it is rotated in 3D
			scene.setOnKeyPressed(e -> {
				if (e.getCode() == KeyCode.L) {
					canvas.setRotationAxis(new Point3D(0, 1, 0));
					canvas.setRotate(canvas.getRotate() + 5);
				} else if (e.getCode() == KeyCode.R) {
					canvas.setRotationAxis(new Point3D(0, 1, 0));
					canvas.setRotate(canvas.getRotate() - 5);
				} else if (e.getCode() == KeyCode.EQUALS) {
					canvas.setRotationAxis(new Point3D(1, 0, 0));
					canvas.setRotate(0);
				}
			});
		}
	}

	/**
	 * draw on canvas
	 *
	 * @param progressPane
	 * @param tree
	 * @param nTaxa
	 * @param taxonLabelMap
	 * @param diagram
	 * @param averaging
	 * @param clear         if set, clears the canvas
	 */
	public void draw(Pane progressPane, PhyloTree tree, int nTaxa, Function<Integer, StringProperty> taxonLabelMap, TreeDiagramType diagram, HeightAndAngles.Averaging averaging, double width, double height, boolean clear) {

		service.setProgressParentPane(progressPane);
		service.setCallable(() -> ComputeTreeLayout.apply(tree, nTaxa, taxonLabelMap, diagram, averaging, width - 200, height - 200, (a, b, c) -> {
		}, (a, b) -> {
		}, false, false, null));

		service.setOnSucceeded(e -> {
			var result = service.getValue();

			var gc = canvas.getGraphicsContext2D();
			if (clear)
				gc.clearRect(0, 0, canvas.getWidth(), canvas.getHeight());

			var snapshotParams = new SnapshotParameters();
			snapshotParams.setFill(Color.TRANSPARENT);

			try {
				var node = result.getAllAsGroup();
				var offsetX = 50 - node.getLayoutBounds().getMinX();
				var offsetY = 50 - node.getLayoutBounds().getMinY();
				// to implement jittering, translate snapshot here

				var bounds = node.getLayoutBounds();
				var bWidth = (int) Math.ceil(bounds.getWidth());
				var bheight = (int) Math.ceil(bounds.getWidth());
				if (bWidth > 0 && bheight > 0) {
					var snapshot = new WritableImage(bWidth, bheight);
					snapshot = node.snapshot(snapshotParams, snapshot);
					gc.drawImage(snapshot, bounds.getMinX() + offsetX + node.getTranslateX(), bounds.getMinY() + offsetY + node.getTranslateY());
				}

			} catch (Exception ex) {
				Basic.caught(ex);
			}
		});

		service.restart();
	}
}
