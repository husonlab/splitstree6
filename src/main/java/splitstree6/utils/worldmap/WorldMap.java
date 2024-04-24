/*
 *  WorldMap.java Copyright (C) 2024 Daniel H. Huson
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

package splitstree6.utils.worldmap;

import javafx.beans.InvalidationListener;
import javafx.beans.WeakInvalidationListener;
import javafx.geometry.Point2D;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.*;
import javafx.scene.text.Text;
import jloda.fx.control.RichTextLabel;
import jloda.fx.control.ZoomableScrollPane;
import jloda.fx.util.BasicFX;
import jloda.fx.util.DraggableUtils;
import jloda.fx.window.MainWindowManager;
import jloda.util.NumberUtils;
import jloda.util.StringUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class WorldMap extends Pane {
	// do not change these values, they crucial for correct mapping of lat-long coordinates
	public static final double X0 = 19;
	public static final double Y0 = 24;
	public static final double DX = 642;
	public static final double DY = 476;

	private static boolean verbose = false;

	public static double WRAP_AROUND_LONGITUDE = -169.0;

	private final Group outlines;
	private final Group continents;
	private final Group countries;
	private final Group oceans;

	private final Rectangle dataRectangle;

	private final Group userItems = new Group();

	private final Group box;

	private final Group grid;

	private final Group notUserItems = new Group();

	private final InvalidationListener darkModeInvalidationListener;

	public WorldMap() {
		dataRectangle = new Rectangle(0, 0, 0, 0);
		dataRectangle.setFill(Color.TRANSPARENT);
		dataRectangle.setStroke(Color.TRANSPARENT);
		dataRectangle.setStrokeWidth(1);

		try {
			outlines = createOutlines();
			continents = createGroup("continents.dat");
			countries = createGroup("countries.dat");
			countries.setVisible(false);
			oceans = createGroup("oceans.dat");
		} catch (IOException ex) {
			throw new RuntimeException(ex);
		}
		box = new Group();

		grid = createGrid();
		{
			var topLeft = millerProjection(90, -180, false);
			var bottomRight = millerProjection(-90, 180, false);
			var worldRectangle = new Rectangle(Math.min(topLeft.getX(), bottomRight.getX()),
					Math.min(topLeft.getY(), bottomRight.getY()),
					Math.abs(topLeft.getX() - bottomRight.getX()),
					Math.abs(topLeft.getY() - bottomRight.getY()));
			worldRectangle.setStroke(Color.TRANSPARENT);
			worldRectangle.setFill(Color.TRANSPARENT);
			worldRectangle.setStrokeWidth(0.25);
			box.getChildren().addAll(worldRectangle, dataRectangle);
		}
		notUserItems.getChildren().addAll(box, grid, outlines, oceans, countries, continents);
		getChildren().addAll(notUserItems, userItems);

		darkModeInvalidationListener = e -> {
			var dark = MainWindowManager.isUseDarkTheme();
			for (var shape : BasicFX.getAllRecursively(this, Shape.class)) {
				if (dark && shape.getStroke() != null && shape.getStroke().equals(Color.BLACK))
					shape.setStroke(Color.WHITE);
				if (!dark && shape.getStroke() != null && shape.getStroke().equals(Color.WHITE))
					shape.setStroke(Color.BLACK);
			}
		};
		MainWindowManager.useDarkThemeProperty().addListener(new WeakInvalidationListener(darkModeInvalidationListener));
		if (MainWindowManager.isUseDarkTheme())
			darkModeInvalidationListener.invalidated(null);
	}

	public void clear() {
		userItems.getChildren().clear();
		dataRectangle.setX(0);
		dataRectangle.setY(0);
		dataRectangle.setWidth(0);
		dataRectangle.setHeight(0);
	}

	public Runnable createUpdateScaleMethod(ZoomableScrollPane scrollPane) {
		return () -> {
			var factorX = scrollPane.getZoomFactorX();
			var factorY = scrollPane.getZoomFactorY();
			for (var node : BasicFX.getAllChildrenRecursively(notUserItems.getChildren())) {
				if (node instanceof Path path) {
					for (var element : path.getElements()) {
						if (element instanceof MoveTo moveTo) {
							moveTo.setX(factorX * moveTo.getX());
							moveTo.setY(factorY * moveTo.getY());
						} else if (element instanceof LineTo lineTo) {
							lineTo.setX(factorX * lineTo.getX());
							lineTo.setY(factorY * lineTo.getY());
						}
					}
				} else if (node instanceof RichTextLabel label) {
					label.setTranslateX(factorX * label.getTranslateX());
					label.setTranslateY(factorY * label.getTranslateY());
				} else if (node instanceof Label label) {
					label.setTranslateX(factorX * label.getTranslateX());
					label.setTranslateY(factorY * label.getTranslateY());
				} else if (node instanceof Text label) {
					label.setTranslateX(factorX * label.getTranslateX());
					label.setTranslateY(factorY * label.getTranslateY());
				} else if (node instanceof Rectangle rectangle) {
					rectangle.setX(factorX * rectangle.getX());
					rectangle.setY(factorY * rectangle.getY());
					rectangle.setWidth(factorX * rectangle.getWidth());
					rectangle.setHeight(factorY * rectangle.getHeight());
				} else if (node instanceof Line line) {
					line.setStartX(factorX * line.getStartX());
					line.setEndX(factorX * line.getEndX());
					line.setStartY(factorY * line.getStartY());
					line.setEndY(factorY * line.getEndY());
				} else if (node instanceof Shape) {
					node.setScaleX(factorX * node.getScaleX());
					node.setScaleY(factorY * node.getScaleY());
				}
				countries.setVisible(scrollPane.getZoomX() > 1.8);
				continents.setVisible(scrollPane.getZoomX() <= 1.8);
			}
			for (var node : userItems.getChildren()) {
				node.setTranslateX(factorX * node.getTranslateX());
				node.setTranslateY(factorY * node.getTranslateY());

			}
		};
	}

	private Group createOutlines() throws IOException {

		var group = new Group();
		Path path = new Path();

		var scale = 1.0;

		var minX = Double.MAX_VALUE;
		var maxX = Double.MIN_VALUE;
		var minY = Double.MAX_VALUE;
		var maxY = Double.MIN_VALUE;

		try (var r = new BufferedReader(new InputStreamReader(getClass().getResourceAsStream("lines.dat")))) {
			while (r.ready()) {
				var line = r.readLine();
				if (!line.startsWith("#")) {
					var tokens = line.split("\\s");
					var x = NumberUtils.parseDouble(tokens[0]);
					var y = NumberUtils.parseDouble(tokens[1]);

					minX = Math.min(minX, x);
					maxX = Math.max(maxX, x);
					minY = Math.min(minY, y);
					maxY = Math.max(maxY, y);

					if (tokens.length == 3) {
						if (tokens[2].equals("m")) {
							if (false) {
								if (!path.getElements().isEmpty())
									group.getChildren().add(path);
								path = new Path();
								path.setStrokeWidth(0.5);
								path.getStyleClass().add("graph-edge");
							}
							path.getElements().add(new MoveTo(scale * x, scale * y));
						} else if (tokens[2].equals("l")) {
							path.getElements().add(new LineTo(scale * x, scale * y));
						}
					}
				}
			}
			if (!path.getElements().isEmpty())
				group.getChildren().add(path);
		}
		if (verbose) {
			System.err.println("Lines:");
			System.err.println("x: " + minX + " - " + maxX);
			System.err.println("y: " + minY + " - " + maxY);
		}

		return group;
	}

	private static Group createGrid() {
		var group = new Group();
		var minX = Double.MAX_VALUE;
		var maxX = Double.MIN_VALUE;
		var minY = Double.MAX_VALUE;
		var maxY = Double.MIN_VALUE;

		if (true) {
			for (var lon = -180; lon <= 180; lon += 30) {
				var start = millerProjection(-90, lon, false);
				var end = millerProjection(90, lon, false);
					var line = new Line(start.getX(), start.getY(), end.getX(), end.getY());
				line.setStrokeWidth(0.25);
					line.setStroke(Color.GRAY);
					group.getChildren().add(line);
			}
			for (var lat = -90; lat <= 90; lat += 30) {
				var start = millerProjection(lat, -180, false);
				var end = millerProjection(lat, 180, false);
					var line = new Line(start.getX(), start.getY(), end.getX(), end.getY());
				line.setStrokeWidth(0.25);
					line.setStroke(Color.GRAY);
					group.getChildren().add(line);
			}
		}

		if (verbose) {
			System.err.println("grid:");
			System.err.println("x: " + minX + " - " + maxX);
			System.err.println("y: " + minY + " - " + maxY);
		}

		return group;
	}

	private Group createGroup(String resource) throws IOException {
		var group = new Group();
		try (var reader = new BufferedReader(new InputStreamReader(getClass().getResourceAsStream(resource)))) {
			while (reader.ready()) {
				var line = reader.readLine();
				var tokens = StringUtils.split(line, '\t');
				if (tokens.length == 3 && NumberUtils.isDouble(tokens[1]) && NumberUtils.isDouble(tokens[2])) {
					var label = new Text(tokens[0]);
					label.getStyleClass().add("above-label");
					var point = millerProjection(NumberUtils.parseDouble(tokens[1]), NumberUtils.parseDouble(tokens[2]));
					label.setTranslateX(point.getX());
					label.setTranslateY(point.getY());
					label.layoutBoundsProperty().addListener((v, o, n) -> {
						label.setLayoutX(-0.5 * n.getWidth());
						label.setLayoutY(-0.5 * n.getHeight());
					});
					//label.setBackgroundColor(Color.WHITE.deriveColor(1, 1, 1, 0.8));
					// label.setBackgroundColor(Color.WHITE);
					DraggableUtils.setupDragMouseTranslate(label);
					group.getChildren().add(label);
				}
			}
		}
		return group;
	}

	public static Point2D millerProjection(double latitude, double longitude) {
		return millerProjection(latitude, longitude, true);
	}


	public static Point2D millerProjection(double latitude, double longitude, boolean wrapAround) {
		// this is wrap-around to the right
		if (wrapAround && longitude <= WRAP_AROUND_LONGITUDE) {
			System.err.println(longitude + " -> " + (180 + Math.abs(180 + longitude)));
			longitude = 180 + Math.abs(180 + longitude);
		}

		var x = (longitude + 180) / 360 * DX + X0;
		//var y = Math.log(Math.tan(Math.PI / 4.0 + Math.toRadians(latitude) / 2.0)) ;
		var y = 5.0 / 4.0 * Math.log(Math.tan(Math.PI / 4.0 + (2.0 / 5.0) * Math.toRadians(latitude)));

		y = Y0 + (2.3034125 - y) / (2 * 2.3034125) * DY;
		//y=RECT[3]+RECT[1]-(y-2.3034125)/(2*2.3034125)*RECT[3];
		//y=540-(y+230);
		//y=540*(1-(y+210)/360);
		return new Point2D(x, y);
	}

	public static double getX0() {
		return X0;
	}

	public static double getY0() {
		return Y0;
	}

	public static double getDX() {
		return DX;
	}

	public static double getDY() {
		return DY;
	}

	public static double getWrapAroundLongitude() {
		return WRAP_AROUND_LONGITUDE;
	}

	public Group getOutlines() {
		return outlines;
	}

	public Group getContinents() {
		return continents;
	}

	public Group getCountries() {
		return countries;
	}

	public Group getOceans() {
		return oceans;
	}

	public Group getUserItems() {
		return userItems;
	}

	public Group getBox() {
		return box;
	}

	public Group getGrid() {
		return grid;
	}

	public void addUserItem(Node node, double latitude, double longitude) {
		var point = millerProjection(latitude, longitude, true);
		node.setTranslateX(point.getX());
		node.setTranslateY(point.getY());
		getUserItems().getChildren().add(node);

		growRect(dataRectangle, point);
	}

	private static void growRect(Rectangle rect, Point2D point) {
		if (rect.getX() == 0 && rect.getX() == 0 && rect.getWidth() == 0 && rect.getHeight() == 0) {
			rect.setX(point.getX());
			rect.setY(point.getY());
		} else {
			var topLeft = new Point2D(rect.getX(), rect.getY());
			var bottomRight = new Point2D(rect.getX() + rect.getWidth(), rect.getY() + rect.getHeight());

			if (point.getX() < topLeft.getX()) {
				topLeft = new Point2D(point.getX(), topLeft.getY());
				rect.setX(topLeft.getX());
				rect.setWidth(bottomRight.getX() - topLeft.getX());
			} else if (point.getX() > bottomRight.getX()) {
				bottomRight = new Point2D(point.getX(), bottomRight.getY());
				rect.setWidth(bottomRight.getX() - topLeft.getX());
			}
			if (point.getY() < topLeft.getY()) {
				topLeft = new Point2D(topLeft.getX(), point.getY());
				rect.setY(topLeft.getY());
				rect.setHeight(bottomRight.getY() - topLeft.getY());
			} else if (point.getY() > bottomRight.getY()) {
				bottomRight = new Point2D(bottomRight.getX(), point.getY());
				rect.setHeight(bottomRight.getY() - topLeft.getY());
			}
		}
	}

	public Rectangle getDataRectangle() {
		return dataRectangle;
	}

	public void expandDataRectangle(double marginProportion) {
		expandRectangle(dataRectangle, marginProportion);
	}

	private static void expandRectangle(Rectangle rect, double marginProportion) {
		var topLeft = new Point2D(rect.getX(), rect.getY());
		var bottomRight = new Point2D(rect.getX() + rect.getWidth(), rect.getY() + rect.getHeight());

		topLeft = new Point2D(Math.max(X0, (1 - marginProportion) * topLeft.getX()), Math.max(Y0, (1 - marginProportion) * topLeft.getY()));
		bottomRight = new Point2D(Math.min(X0 + DX, (1 + marginProportion) * bottomRight.getX()), Math.min(Y0 + DY, (1 + marginProportion) * bottomRight.getY()));
		rect.setX(topLeft.getX());
		rect.setY(topLeft.getY());
		rect.setWidth(Math.abs(bottomRight.getX() - topLeft.getX()));
		rect.setHeight(Math.abs(bottomRight.getY() - topLeft.getY()));
	}
}
