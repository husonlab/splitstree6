/*
 *  DrawNetwork.java Copyright (C) 2024 Daniel H. Huson
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

package splitstree6.xtra.draw;

import javafx.application.Application;
import javafx.beans.InvalidationListener;
import javafx.beans.property.DoubleProperty;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Slider;
import javafx.scene.control.ToolBar;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.*;
import javafx.stage.Stage;
import jloda.fx.util.BasicFX;
import jloda.fx.util.RunAfterAWhile;
import jloda.util.TriConsumer;

import java.util.ArrayList;

public class DrawNetwork extends Application {

	@Override
	public void start(Stage stage) throws Exception {
		var pane = new Pane();
		pane.setStyle("-fx-background-color: yellow;");
		pane.setPrefWidth(800);
		pane.setPrefHeight(800);

		var edges = new ArrayList<Path>();

		var slider = new Slider(0.1, 20, 4);
		var clearButton = new Button("Clear");
		clearButton.setOnAction(e -> pane.getChildren().clear());

		setupDraw(pane, (p, s, t) -> {
			edges.add(p);
			p.setUserData(edges.size());
			p.setStroke(Color.BLACK);
			p.setFill(Color.TRANSPARENT);
			pane.getChildren().add(p);
			s.setStroke(Color.BLACK);
			s.setFill(Color.WHITE);
			pane.getChildren().add(s);
			t.setStroke(Color.BLACK);
			t.setFill(Color.BLACK);
			pane.getChildren().add(t);
		}, slider.valueProperty());

		var rect = new Rectangle(50, 50, 700, 700);
		rect.setFill(Color.TRANSPARENT);
		rect.setStroke(Color.BLUE);
		pane.getChildren().add(rect);

		var root = new BorderPane();
		root.setTop(new ToolBar(slider, clearButton));
		root.setCenter(pane);
		var scene = new Scene(root, 800, 800);
		stage.setScene(scene);
		stage.sizeToScene();
		stage.show();
	}

	private static double mouseX;
	private static double mouseY;

	private static Path path;

	private static final ArrayList<PathElement> internalElements = new ArrayList<>();

	public static void setupDraw(Pane pane, TriConsumer<Path, Shape, Shape> pathConsumer, DoubleProperty minDistance) {

		pane.setOnMousePressed(e -> {
			mouseX = e.getScreenX();
			mouseY = e.getScreenY();
			internalElements.clear();
			path = null;
		});

		pane.setOnMouseDragged(e -> {
			var previous = pane.screenToLocal(mouseX, mouseY);

			if (path == null) {
				path = new Path();
				previous = snapToExisting(previous, pane, path, minDistance.doubleValue());
				path.getElements().add(new MoveTo(previous.getX(), previous.getY()));

				var pointOnPath = PathUtils.pointOnPath(new Point2D(previous.getX(), previous.getY()),
						BasicFX.getAllRecursively(pane, Path.class), minDistance.doubleValue());
				if (pointOnPath != null) {
					var circle = new Circle(3);
					circle.setFill(Color.GREEN);
					circle.setCenterX(pointOnPath.getSecond().getX());
					circle.setCenterY(pointOnPath.getSecond().getY());
					pane.getChildren().add(circle);
				}

			}
			var point = pane.screenToLocal(e.getScreenX(), e.getScreenY());
			//point=snapToExisting(point,pane,path,minDistance.doubleValue());

			if (point.distance(previous) > minDistance.get()) {
				path.getElements().add(new LineTo(point.getX(), point.getY()));
				if (path.getElements().size() == 2) {
					previous = snapToExisting(previous, pane, path, minDistance.doubleValue());
					var start = new Circle(previous.getX(), previous.getY(), 3);
					var end = new Circle(3);
					path.getElements().addListener((InvalidationListener) a -> {
						if (!path.getElements().isEmpty() && path.getElements().get(path.getElements().size() - 1) instanceof LineTo lineTo) {
							end.setCenterX(lineTo.getX());
							end.setCenterY(lineTo.getY());
						}
					});
					pathConsumer.accept(path, start, end);
				}
				mouseX = e.getScreenX();
				mouseY = e.getScreenY();
			}

			RunAfterAWhile.applyInFXThread(path, () -> {
				var first = (MoveTo) path.getElements().get(0);
				var last = (LineTo) path.getElements().get(path.getElements().size() - 1);
				var middle = asRectangular(path);
				internalElements.clear();
				for (var element : path.getElements()) {
					if (element != first && element != last) {
						internalElements.add(element);
					}
				}
				if (middle != null) {
					path.getElements().setAll(first, middle, last);
				} else {
					path.getElements().setAll(first, last);
				}
			});

			if (!internalElements.isEmpty()) {
				var first = (MoveTo) path.getElements().get(0);
				var last = (LineTo) path.getElements().get(path.getElements().size() - 1);
				path.getElements().setAll(first);
				path.getElements().addAll(internalElements);
				path.getElements().add(last);
				internalElements.clear();
			}
		});

		pane.setOnMouseReleased(e -> {
			RunAfterAWhile.applyInFXThread(path, () -> {
			});

			{
				var intersections = PathUtils.allIntersections(path, BasicFX.getAllRecursively(pane, Path.class), false);
				for (var intersection : intersections) {
					var circle = new Circle(intersection.getSecond().getX(), intersection.getSecond().getY(), 3);
					circle.setFill(Color.TRANSPARENT);
					circle.setStroke(Color.RED);
					pane.getChildren().add(circle);
				}
			}

			path = null;
		});
	}

	private static LineTo asRectangular(Path path) {
		var first = (MoveTo) path.getElements().get(0);
		var last = (LineTo) path.getElements().get(path.getElements().size() - 1);

		var points = new ArrayList<Point2D>();
		for (var element : path.getElements()) {
			if ((element instanceof LineTo lineTo) && lineTo != last) {
				points.add(new Point2D(lineTo.getX(), lineTo.getY()));
			}
		}

		var x0 = first.getX();
		var x1 = last.getX();
		var y0 = first.getY();
		var y1 = last.getY();

		var p = new Point2D(x0 <= x1 ? x0 : x1, x0 <= x1 ? y0 : y1);
		var q = new Point2D(x0 <= x1 ? x1 : x0, x0 <= x1 ? y1 : y0);


		var dx = Math.abs(x0 - x1);
		var dy = Math.abs(y0 - y1);

		var factor = 0.3;

		if (p.getY() + 2 * factor * dy < q.getY()) {
			for (var a : points) {
				if (a.getX() >= q.getX() - factor * dx && a.getY() <= p.getY() + factor * dy) {
					return new LineTo(q.getX(), p.getY());
				} else if (a.getX() <= p.getX() + factor * dx && a.getY() >= p.getY() + (1 - factor) * dy) {
					return new LineTo(p.getX(), q.getY());
				}
			}
		} else if (p.getY() - 2 * factor * dy > q.getY()) {
			for (var a : points) {
				if (a.getX() <= p.getX() + factor * dx && a.getY() <= q.getY() + factor * dy) {
					return new LineTo(p.getX(), q.getY());
				} else if (a.getX() >= p.getX() + (1 - factor) * dx && a.getY() >= q.getY() + (1 - factor) * dy) {
					return new LineTo(q.getX(), p.getY());
				}
			}
		}
		return null;
	}

	private static Point2D snapToExisting(Point2D point, Node parent, Path ignore, double tolerance) {
		for (var path : BasicFX.getAllRecursively(parent, Path.class)) {
			if (path != ignore) {
				if (path.getElements().get(0) instanceof MoveTo moveTo) {
					var other = new Point2D(moveTo.getX(), moveTo.getY());
					if (other.distance(point) < tolerance)
						return other;
				}
				if (path.getElements().get(path.getElements().size() - 1) instanceof LineTo lineTo) {
					var other = new Point2D(lineTo.getX(), lineTo.getY());
					if (other.distance(point) < tolerance)
						return other;
				}
			}
		}
		return point;
	}
}
