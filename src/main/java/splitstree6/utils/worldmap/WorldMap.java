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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import javafx.geometry.Point2D;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Line;
import javafx.scene.shape.Polygon;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Text;
import jloda.fx.util.BasicFX;
import jloda.fx.util.DraggableUtils;
import jloda.util.NumberUtils;
import jloda.util.StringUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.stream.Collectors;

public class WorldMap extends Pane {
	private final Group landMasses;

	private final Group countries;
	private final Group continents;
	private final Group oceans;

	private final Group grid;

	private final Rectangle dataRectangle;

	private final Group userItems = new Group();

	private double scale = 1.0;

	public WorldMap() {
		dataRectangle = new Rectangle(0, 0, 0, 0);
		dataRectangle.setFill(Color.TRANSPARENT);
		dataRectangle.setStroke(Color.TRANSPARENT);
		dataRectangle.setStrokeWidth(1);

		var bottomLeft = millerProjection(-180, 90);
		var topRight = millerProjection(180, -90);
		var worldRectangle = new Rectangle(bottomLeft.getX(), bottomLeft.getY(), topRight.getX() - bottomLeft.getX(), topRight.getY() - bottomLeft.getY());
		worldRectangle.setStroke(Color.TRANSPARENT);
		worldRectangle.setFill(Color.TRANSPARENT);
		worldRectangle.setStrokeWidth(0.25);

		landMasses = createLandMasses();
		grid = createGrid();

		continents = createGroup("continents.dat");
		countries = createGroup("countries.dat");
		countries.setVisible(false);
		oceans = createGroup("oceans.dat");
		getChildren().addAll(worldRectangle, grid, dataRectangle, landMasses, countries, continents, oceans, userItems);
	}

	public void clear() {
		userItems.getChildren().clear();
		dataRectangle.setX(0);
		dataRectangle.setY(0);
		dataRectangle.setWidth(0);
		dataRectangle.setHeight(0);
	}

	public void addUserItem(Node node, double longitude, double latitude) {
		var point = millerProjection(longitude, latitude);
		node.setTranslateX(scale * point.getX());
		node.setTranslateY(scale * point.getY());
		getUserItems().getChildren().add(node);
		growRect(dataRectangle, point);
	}

	public void changeScale(double oldFactor, double newFactor) {
		var factor = newFactor / oldFactor;
		scale *= factor;

		for (var node : BasicFX.getAllRecursively(this, Node.class)) {
			if (node instanceof Polygon polygon) {
				var points = new ArrayList<Double>();
				for (var p : polygon.getPoints()) {
					points.add(factor * p);
				}
				polygon.getPoints().setAll(points);
			} else if (node instanceof Rectangle rectangle) {
				if (!rectangle.translateXProperty().isBound())
					rectangle.setTranslateX(factor * rectangle.getTranslateX());
				if (!rectangle.translateYProperty().isBound())
					rectangle.setTranslateY(factor * rectangle.getTranslateY());
				if (!rectangle.widthProperty().isBound())
					rectangle.setWidth(rectangle.getWidth() * factor);
				if (!rectangle.heightProperty().isBound())
					rectangle.setHeight(rectangle.getHeight() * factor);
			} else if (node instanceof Line line) {
				if (!line.startXProperty().isBound())
					line.setStartX(factor * line.getStartX());
				if (!line.endXProperty().isBound())
					line.setEndX(factor * line.getEndX());
				if (!line.startYProperty().isBound())
					line.setStartY(factor * line.getStartY());
				if (!line.endYProperty().isBound())
					line.setEndY(factor * line.getEndY());
			} else {
				if (!node.translateXProperty().isBound())
					node.setTranslateX(factor * node.getTranslateX());
				if (!node.translateYProperty().isBound())
					node.setTranslateY(factor * node.getTranslateY());
			}
		}
	}

	public Group getLandMasses() {
		return landMasses;
	}

	public Group getUserItems() {
		return userItems;
	}

	public Group getGrid() {
		return grid;
	}

	public Group getCountries() {
		return countries;
	}

	public Group getContinents() {
		return continents;
	}

	public Group getOceans() {
		return oceans;
	}

	private static int calculateArrayDepth(JsonNode node) {
		// Base case: if the node is not an array, return 0
		if (!node.isArray()) {
			return 0;
		}

		// Initialize the maximum depth to 0
		int maxDepth = 0;

		for (var element : node) {
			int depth = calculateArrayDepth(element);
			if (depth > maxDepth) {
				maxDepth = depth;
			}
		}
		return maxDepth + 1;
	}

	private static Point2D millerProjection(double longitude, double latitude) {
		var dx = 180;
		var fx = 800 / 360;
		var dy = 2.3034125433763912;
		var fy = 594 / 4.606825086752782;

		return new Point2D(fx * (longitude + dx), 594 - fy * (((5.0 / 4.0 * Math.log(Math.tan(Math.PI / 4 + 2 * Math.toRadians(latitude) / 5)))) + dy));
	}

	private Group createLandMasses() {
		var group = new Group();

		var verbose = false;
		try (var r = new BufferedReader(new InputStreamReader(getClass().getResourceAsStream("world-outline-low-precision_759.dat")))) {
			var json = r.lines().collect(Collectors.joining("\n"));

			var objectMapper = new ObjectMapper();
			var rootNode = objectMapper.readTree(json);

			// Accessing properties of the root node
			var type = rootNode.get("type").asText();
			if (verbose)
				System.err.println("Type: " + type);

			// Accessing properties of features array
			var featuresNode = rootNode.get("features");
			for (var featureNode : featuresNode) {

				var featureType = featureNode.get("type").asText();
				var id = featureNode.get("id").asText();

				// Accessing properties object
				var propertiesNode = featureNode.get("properties");
				var name = propertiesNode.get("name").asText();


				// Accessing geometry object
				var geometryNode = featureNode.get("geometry");
				var geometryType = geometryNode.get("type").asText();

				var coordinatesNode = geometryNode.get("coordinates");

				if (verbose) {
					System.err.println("Feature Type: " + featureType);
					System.err.println("ID: " + id);
					System.err.println("Name: " + name);
					System.err.println("Geometry Type: " + geometryType);
				}

				// Assuming coordinates are always a nested array of arrays

				var parts = 0;
				if (coordinatesNode.isArray()) {
					var depth = calculateArrayDepth(coordinatesNode);
					if (depth == 3) {
						for (JsonNode level2 : coordinatesNode) {
							if (level2.isArray()) {
								var polygon = new Polygon();
								for (JsonNode level3 : level2) {
									var longitude = level3.get(0).asDouble();
									var latitude = level3.get(1).asDouble();
									var projection = millerProjection(longitude, latitude);
									polygon.getPoints().addAll(projection.getX(), projection.getY());
									if (verbose) {
										System.err.printf("Long/lat: %.2f %.2f proj: %.2f %.2f%n", longitude, latitude, projection.getX(), projection.getY());
									}
								}
								polygon.setFill(Color.WHITESMOKE);
								polygon.setStroke(Color.BLACK);
								Tooltip.install(polygon, new Tooltip(name));
								polygon.setId(name);
								group.getChildren().add(polygon);
							}
						}
					} else if (depth == 4) {
						for (JsonNode level2 : coordinatesNode) {
							if (level2.isArray()) {
								for (JsonNode level3 : level2) {
									if (level3.isArray()) {
										var polygon = new Polygon();
										if (verbose)
											System.err.println("part " + (++parts));
										for (JsonNode level4 : level3) {
											var longitude = level4.get(0).asDouble();
											var latitude = level4.get(1).asDouble();
											var projection = millerProjection(longitude, latitude);
											polygon.getPoints().addAll(projection.getX(), projection.getY());
											if (verbose) {
												System.err.printf("Long/lat: %.2f %.2f proj: %.2f %.2f%n", longitude, latitude, projection.getX(), projection.getY());
											}
										}

										polygon.setFill(Color.WHITESMOKE);
										polygon.setStroke(Color.BLACK);
										Tooltip.install(polygon, new Tooltip(name));
										polygon.setId(name);
										group.getChildren().add(polygon);
									}
								}
							}
						}
					}
				}
			}
			if (verbose)
				System.err.println();
		} catch (IOException ex) {
			throw new RuntimeException(ex);
		}
		return group;
	}

	private Group createGrid() {
		var group = new Group();

			for (var lon = -180; lon <= 180; lon += 30) {
				var start = millerProjection(lon, -90);
				var end = millerProjection(lon, 90);
				var line = new Line(start.getX(), start.getY(), end.getX(), end.getY());
				line.setStrokeWidth(0.25);
				line.setStroke(Color.GRAY);
				group.getChildren().add(line);
			}
			for (var lat = -90; lat <= 90; lat += 30) {
				var start = millerProjection(-180, lat);
				var end = millerProjection(180, lat);
				var line = new Line(start.getX(), start.getY(), end.getX(), end.getY());
				line.setStrokeWidth(0.25);
				line.setStroke(Color.GRAY);
				group.getChildren().add(line);
			}
		return group;
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

	private Group createGroup(String resource) {
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
		} catch (IOException ex) {
			throw new RuntimeException(ex);
		}
		return group;
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

		topLeft = new Point2D((1 - marginProportion) * topLeft.getX(), (1 - marginProportion) * topLeft.getY());
		bottomRight = new Point2D((1 + marginProportion) * bottomRight.getX(), (1 + marginProportion) * bottomRight.getY());
		rect.setX(topLeft.getX());
		rect.setY(topLeft.getY());
		rect.setWidth(Math.abs(bottomRight.getX() - topLeft.getX()));
		rect.setHeight(Math.abs(bottomRight.getY() - topLeft.getY()));
	}
}
