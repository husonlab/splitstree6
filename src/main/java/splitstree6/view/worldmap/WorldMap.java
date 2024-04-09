/*
 *  Try.java Copyright (C) 2024 Daniel H. Huson
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

package splitstree6.view.worldmap;

import javafx.geometry.Point2D;
import javafx.scene.Group;
import javafx.scene.control.Label;
import javafx.scene.paint.Color;
import javafx.scene.shape.*;
import javafx.scene.text.Text;
import jloda.fx.control.RichTextLabel;
import jloda.fx.control.ZoomableScrollPane;
import jloda.fx.util.BasicFX;
import jloda.util.NumberUtils;
import jloda.util.StringUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class WorldMap extends Group {
	// do not change these values, they crucial for correct mapping of lat-long coordinates
	public static final double X0 = 25;
	public static final double Y0 = 24;
	public static final double DX = 642;
	public static final double DY = 476;

	public static double WRAP_AROUND_LONGITUDE = -169.0;

	private final Group outlines;
	private final Group continents;
	private final Group countries;
	private final Group oceans;

	private final Group userItems;

	private final Group box;

	private final Group grid;

	public WorldMap() throws IOException {
		super(new Group());

		outlines = createOutlines();
		continents = createGroup("continents.txt");
		countries = createGroup("countries.txt");
		countries.setVisible(false);
		oceans = createGroup("oceans.txt");
		box = new Group();

		grid = createGrid();
		if (true) {
			var topLeft = millerProjection(90, -180, false);
			var bottomRight = millerProjection(-90, 180, false);
			var rect = new Rectangle(Math.min(topLeft.getX(), bottomRight.getX()),
					Math.min(topLeft.getY(), bottomRight.getY()),
					Math.abs(topLeft.getX() - bottomRight.getX()),
					Math.abs(topLeft.getY() - bottomRight.getY()));
			rect.setStroke(Color.GRAY);
			rect.setFill(Color.TRANSPARENT);
			rect.setStrokeWidth(0.25);
			box.getChildren().add(rect);
		}
		userItems = new Group();

		getChildren().addAll(box, grid, outlines, oceans, countries, continents, userItems);
	}

	public Runnable createUpdateScaleMethod(ZoomableScrollPane scrollPane) {
		return () -> {
			for (var node : BasicFX.getAllChildrenRecursively(getChildren())) {
				var factorX = scrollPane.getZoomFactorX();
				var factorY = scrollPane.getZoomFactorY();
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
					rectangle.setLayoutX(factorX * rectangle.getLayoutX());
					rectangle.setLayoutY(factorY * rectangle.getLayoutY());
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

		try (var r = new BufferedReader(new InputStreamReader(getClass().getResourceAsStream("lines.txt")))) {
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
		System.err.println("Lines:");

		System.err.println("x: " + minX + " - " + maxX);
		System.err.println("y: " + minY + " - " + maxY);

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

		System.err.println("grid:");
		System.err.println("x: " + minX + " - " + maxX);
		System.err.println("y: " + minY + " - " + maxY);

		return group;
	}


	private Group createGroup(String resource) throws IOException {
		var group = new Group();
		try (var reader = new BufferedReader(new InputStreamReader(getClass().getResourceAsStream(resource)))) {
			while (reader.ready()) {
				var line = reader.readLine();
				var tokens = StringUtils.split(line, '\t');
				if (tokens.length == 3 && NumberUtils.isDouble(tokens[1]) && NumberUtils.isDouble(tokens[2])) {
					var label = new RichTextLabel(tokens[0]);
					var point = millerProjection(NumberUtils.parseDouble(tokens[1]), NumberUtils.parseDouble(tokens[2]));
					label.setTranslateX(point.getX());
					label.setTranslateY(point.getY());
					label.layoutBoundsProperty().addListener((v, o, n) -> {
						label.setLayoutX(-0.5 * n.getWidth());
						label.setLayoutY(-0.5 * n.getHeight());
					});
					label.setBackgroundColor(Color.WHITE.deriveColor(1, 1, 1, 0.8));
					// label.setBackgroundColor(Color.WHITE);
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
}
