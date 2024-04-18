/*
 *  Icebergs.java Copyright (C) 2024 Daniel H. Huson
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

package splitstree6.view.utils;

import javafx.application.Platform;
import javafx.beans.InvalidationListener;
import javafx.beans.binding.Bindings;
import javafx.event.EventHandler;
import javafx.scene.input.MouseEvent;
import javafx.scene.shape.*;
import splitstree6.main.SplitsTree6;

/**
 * icebergs
 * Daniel Huson, 1.2024
 */
public class Icebergs {
	private static boolean enabled = false;

	/**
	 * creates a shape that represents the "bottom of the iceberg", which is used in a mobile app to make it possible
	 * to click on the shape
	 *
	 * @param shape                     original shape
	 * @param delegateMouseInteractions if true, delegates mouse pressed, -clicked, -dragged and -released events to shape.
	 *                                  (Mouse entered and exited are always delegated and any attempt to set handlers for these
	 *                                  events on the eisberg are ignored)
	 * @return bottom of the iceberg or null, if shape not supported
	 */
	public static Shape create(Shape shape, boolean delegateMouseInteractions) {
		if ("eisberg".equals(shape.getId()))
			throw new RuntimeException("create(): not valid on eisberg");
		else {
			Shape iceberg;
			if (shape instanceof Line line) {
				var line1 = new Line();
				line1.startXProperty().bind(line.startXProperty());
				line1.startYProperty().bind(line.startYProperty());
				line1.endXProperty().bind(line.endXProperty());
				line1.endYProperty().bind(line.endYProperty());
				iceberg = line1;
			} else if (shape instanceof Path path) {
				var path1 = new Path();
				InvalidationListener updatePath = e -> {
					path1.getElements().clear();
					for (var element : path.getElements()) {
						if (element instanceof MoveTo moveTo) {
							var moveTo1 = new MoveTo();
							moveTo1.xProperty().bind(moveTo.xProperty());
							moveTo1.yProperty().bind(moveTo.yProperty());
							path1.getElements().add(moveTo1);
						} else if (element instanceof LineTo lineTo) {
							var lineTo1 = new LineTo();
							lineTo1.xProperty().bind(lineTo.xProperty());
							lineTo1.yProperty().bind(lineTo.yProperty());
							path1.getElements().add(lineTo1);
						} else if (element instanceof HLineTo hLineTo) {
							var hLineTo1 = new HLineTo();
							hLineTo1.xProperty().bind(hLineTo.xProperty());
							path1.getElements().add(hLineTo1);
						} else if (element instanceof VLineTo vLineTo) {
							var vLineTo1 = new VLineTo();
							vLineTo1.yProperty().bind(vLineTo.yProperty());
							path1.getElements().add(vLineTo1);
						} else if (element instanceof QuadCurveTo quadCurveTo) {
							var quadCurveTo1 = new QuadCurveTo();
							quadCurveTo1.xProperty().bind(quadCurveTo.xProperty());
							quadCurveTo1.yProperty().bind(quadCurveTo.yProperty());
							quadCurveTo1.controlXProperty().bind(quadCurveTo.controlXProperty());
							quadCurveTo1.controlYProperty().bind(quadCurveTo.controlYProperty());
							path1.getElements().add(quadCurveTo1);
						} else if (element instanceof CubicCurveTo cubicCurveTo) {
							var cubicCurveTo1 = new CubicCurveTo();
							cubicCurveTo1.xProperty().bind(cubicCurveTo.xProperty());
							cubicCurveTo1.yProperty().bind(cubicCurveTo.yProperty());
							cubicCurveTo1.controlX1Property().bind(cubicCurveTo.controlX1Property());
							cubicCurveTo1.controlY1Property().bind(cubicCurveTo.controlY1Property());
							cubicCurveTo1.controlX2Property().bind(cubicCurveTo.controlX2Property());
							cubicCurveTo1.controlY2Property().bind(cubicCurveTo.controlY2Property());
							path1.getElements().add(cubicCurveTo1);
						} else if (element instanceof ArcTo arcTo) {
							var arcTo1 = new ArcTo();
							arcTo1.xProperty().bind(arcTo.xProperty());
							arcTo1.yProperty().bind(arcTo.yProperty());
							arcTo1.largeArcFlagProperty().bind(arcTo.largeArcFlagProperty());
							arcTo1.sweepFlagProperty().bind(arcTo.sweepFlagProperty());
							arcTo1.radiusXProperty().bind(arcTo.radiusXProperty());
							arcTo1.radiusYProperty().bind(arcTo.radiusYProperty());
							path1.getElements().add(arcTo1);
						}
					}
				};
				updatePath.invalidated(null);
				path.getElements().addListener((updatePath));
				iceberg = path1;
			} else if (shape instanceof Circle circle) {
				var circle1 = new Circle();
				circle1.radiusProperty().bind(Bindings.createDoubleBinding(() -> Math.max(5, circle.getRadius()), circle.radiusProperty()));
				iceberg = circle1;
			} else if (shape instanceof Rectangle rectangle) {
				var rectangle1 = new Rectangle();
				rectangle1.widthProperty().bind(rectangle.widthProperty());
				rectangle1.heightProperty().bind(rectangle.heightProperty());
				iceberg = rectangle1;
			} else { // todo: add polylines and polygons
				var rectangle = new Rectangle();
				rectangle.widthProperty().bind(Bindings.createDoubleBinding(() -> 1.1 * shape.getBoundsInLocal().getWidth(), shape.boundsInLocalProperty()));
				rectangle.heightProperty().bind(Bindings.createDoubleBinding(() -> 1.1 * shape.getBoundsInLocal().getHeight(), shape.boundsInLocalProperty()));
				iceberg = rectangle;
			}

			iceberg.setUserData(shape.getUserData());
			iceberg.scaleXProperty().bind(shape.scaleXProperty());
			iceberg.scaleYProperty().bind(shape.scaleYProperty());
			iceberg.translateXProperty().bind(shape.translateXProperty());
			iceberg.translateYProperty().bind(shape.translateYProperty());
			iceberg.layoutXProperty().bind(shape.layoutXProperty());
			iceberg.layoutYProperty().bind(shape.layoutYProperty());

			if (delegateMouseInteractions) {
				iceberg.setOnMousePressed(e -> {
					shape.fireEvent(e);
					e.consume();
				});
				iceberg.setOnMouseDragged(e -> {
					shape.fireEvent(e);
					e.consume();
				});
				iceberg.setOnMouseReleased(e -> {
					shape.fireEvent(e);
					e.consume();
				});
				iceberg.setOnMouseClicked(e -> {
					shape.fireEvent(e);
					e.consume();
				});
			}
			EventHandler<MouseEvent> handler = shape::fireEvent;
			if (SplitsTree6.nodeZoomOnMouseOver) {
				iceberg.setOnMouseEntered(handler);
				iceberg.setOnMouseExited(handler);
			}
			if (SplitsTree6.nodeZoomOnMouseOver) {
				iceberg.onMouseEnteredProperty().addListener((v, o, n) -> {
					if (n != handler)
						Platform.runLater(() -> iceberg.setOnMouseEntered(handler));
				});
				iceberg.onMouseExitedProperty().addListener((v, o, n) -> {
					if (n != handler)
						Platform.runLater(() -> iceberg.setOnMouseExited(handler));
				});
			}

			iceberg.setId("iceberg");
			return iceberg;
		}
	}

	public static boolean enabled() {
		return enabled;
	}

	public static void setEnabled(boolean enabled) {
		Icebergs.enabled = enabled;
	}
}
