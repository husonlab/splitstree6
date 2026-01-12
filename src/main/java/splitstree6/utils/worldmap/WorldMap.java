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
import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.geometry.Point2D;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Line;
import javafx.scene.shape.Polygon;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import jloda.fx.util.BasicFX;
import jloda.fx.util.DraggableUtils;
import jloda.util.NumberUtils;
import jloda.util.StringUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Objects;
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

	private final ObjectProperty<Font> font = new SimpleObjectProperty(Font.getDefault());


	public WorldMap() {
		dataRectangle = new Rectangle(0, 0, 0, 0);
		dataRectangle.setFill(Color.TRANSPARENT);
		dataRectangle.setStroke(Color.TRANSPARENT);
		dataRectangle.setStrokeWidth(2);

		var bottomLeft = millerProjection(90, -180);
		var topRight = millerProjection(-90, 180);
		var worldRectangle = new Rectangle(bottomLeft.getX(), bottomLeft.getY(), topRight.getX() - bottomLeft.getX(), topRight.getY() - bottomLeft.getY());
		worldRectangle.setFill(Color.TRANSPARENT);
		worldRectangle.setStroke(Color.TRANSPARENT);
		worldRectangle.setStrokeWidth(0.25);

		prefWidthProperty().bind(worldRectangle.widthProperty());
		prefHeightProperty().bind(worldRectangle.heightProperty());

		landMasses = createLandMasses();
		grid = createGrid();

		continents = createGroup("continents.dat");
		countries = createGroup("countries.dat");
		countries.setVisible(false);
		oceans = createGroup("oceans.dat");
		getChildren().addAll(worldRectangle, grid, landMasses, countries, continents, oceans, dataRectangle, userItems);
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
				if (!rectangle.xProperty().isBound())
					rectangle.setX(factor * rectangle.getX());
				if (!rectangle.yProperty().isBound())
					rectangle.setY(factor * rectangle.getY());
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

	private static Point2D millerProjection(double latitude, double longitude) {
		var dx = 180;
		var fx = 800 / 360;
		var dy = 2.3034125433763912;
		var fy = 594 / 4.606825086752782;

		return new Point2D(fx * (longitude + dx), 594 - fy * (((5.0 / 4.0 * Math.log(Math.tan(Math.PI / 4 + 2 * Math.toRadians(latitude) / 5)))) + dy));
	}

	private Group createLandMasses() {
		var group = new Group();

		var verbose = false;
		try (var r = new BufferedReader(new InputStreamReader(Objects.requireNonNull(getClass().getResourceAsStream("world-outline-low-precision_759.dat"))))) {
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
								polygon.setStyle("-fx-fill: #d8d5cc;-fx-stroke: gray;");
								for (JsonNode level3 : level2) {
									// Note: the file provides the coordinates as longitude latitude
									var latitude = level3.get(1).asDouble();
									var longitude = level3.get(0).asDouble();
									var projection = millerProjection(latitude, longitude);
									polygon.getPoints().addAll(projection.getX(), projection.getY());
									if (verbose) {
										System.err.printf("Long/lat: %.2f %.2f proj: %.2f %.2f%n", longitude, latitude, projection.getX(), projection.getY());
									}
								}
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
										polygon.setStyle("-fx-fill: #d8d5cc;-fx-stroke: gray;");
										if (verbose)
											System.err.println("part " + (++parts));
										for (JsonNode level4 : level3) {
											var longitude = level4.get(0).asDouble();
											var latitude = level4.get(1).asDouble();
											var projection = millerProjection(latitude, longitude);
											polygon.getPoints().addAll(projection.getX(), projection.getY());
											if (verbose) {
												System.err.printf("Long/lat: %.2f %.2f proj: %.2f %.2f%n", longitude, latitude, projection.getX(), projection.getY());
											}
										}

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
			var start = millerProjection(-90, lon);
			var end = millerProjection(90, lon);
			var line = new Line(start.getX(), start.getY(), end.getX(), end.getY());
			line.setStrokeWidth(0.25);
			line.setStroke(Color.GRAY);
			group.getChildren().add(line);
		}
		for (var lat = -90; lat <= 90; lat += 30) {
			var start = millerProjection(lat, -180);
			var end = millerProjection(lat, 180);
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
		try (var reader = new BufferedReader(new InputStreamReader(Objects.requireNonNull(getClass().getResourceAsStream(resource))))) {
			while (reader.ready()) {
				var line = reader.readLine();
				var tokens = StringUtils.split(line, '\t');
				if (tokens.length == 3 && NumberUtils.isDouble(tokens[1]) && NumberUtils.isDouble(tokens[2])) {
					var label = new Text(tokens[0]);
					DraggableUtils.setupDragMouseTranslate(label);
					label.fontProperty().bind(font);
					label.getStyleClass().add("above-label");
					var point = millerProjection(NumberUtils.parseDouble(tokens[1]), NumberUtils.parseDouble(tokens[2]));
					label.setTranslateX(point.getX());
					label.setTranslateY(point.getY());
					label.layoutBoundsProperty().addListener((v, o, n) -> {
						label.setLayoutX(-0.5 * n.getWidth());
						label.setLayoutY(-0.5 * n.getHeight());
					});
					label.applyCss();
					//label.setBackgroundColor(Color.WHITE.deriveColor(1, 1, 1, 0.8));
					// label.setBackgroundColor(Color.WHITE);
					group.getChildren().add(label);
					Platform.runLater(() -> {
						label.setLayoutX(-0.5 * label.getLayoutBounds().getWidth());
						label.setLayoutY(-0.5 * label.getLayoutBounds().getHeight());
					});
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
		if (false) { // this doesn't seem to work well
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

	public Font getFont() {
		return font.get();
	}

	public ObjectProperty<Font> fontProperty() {
		return font;
	}

	public void setFont(Font font) {
		this.font.set(font);
	}
}
