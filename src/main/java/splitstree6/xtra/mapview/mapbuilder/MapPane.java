/*
 *  MapPane.java Copyright (C) 2024 Daniel H. Huson
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

package splitstree6.xtra.mapview.mapbuilder;

import javafx.application.Platform;
import javafx.geometry.Point2D;
import javafx.geometry.Rectangle2D;
import javafx.scene.Node;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import splitstree6.xtra.mapview.nodes.DraggableLine;
import splitstree6.xtra.mapview.nodes.DraggablePieChart;

import java.util.List;
import java.util.function.Function;

/**
 * A world map view consisting of a lower pane showing the map and an upper pane containing user items.
 * This class facilitates the visualization of geographic data by providing methods to place nodes and charts
 * on specific latitude and longitude coordinates within the map.
 *
 * Daniel Huson, December 2023
 */
public class MapPane extends StackPane {
	private final Pane mapPane;
	private final Pane userPane;

	LatLongRect bounds;

	private final Function<Double, Double> latitudeYFunction;
	private final Function<Double, Double> longitudeXFunction;

	/**
	 * Constructs a MapPane with the specified bounds, tiles, and mapping functions.
	 *
	 * @param bounds            The bounds (Rectangle2D) of the map.
	 * @param tiles             The list of ImageView tiles representing the map.
	 * @param latitudeYFunction The function to map latitude values to Y-coordinates on the map.
	 * @param longitudeXFunction The function to map longitude values to X-coordinates on the map.
	 */
	MapPane(Rectangle2D bounds, List<ImageView> tiles, Function<Double, Double> latitudeYFunction, Function<Double, Double> longitudeXFunction) {
		this.latitudeYFunction = latitudeYFunction;
		this.longitudeXFunction = longitudeXFunction;


		mapPane = new Pane();
		mapPane.setPrefWidth(bounds.getWidth());
		mapPane.setMinWidth(Pane.USE_PREF_SIZE);
		mapPane.setMaxWidth(Pane.USE_PREF_SIZE);
		mapPane.setPrefHeight(bounds.getHeight());
		mapPane.setMinHeight(Pane.USE_PREF_SIZE);
		mapPane.setMaxHeight(Pane.USE_PREF_SIZE);
		mapPane.getChildren().addAll(tiles);

		userPane = new Pane();
		userPane.setPrefWidth(bounds.getWidth());
		userPane.setMinWidth(Pane.USE_PREF_SIZE);
		userPane.setMaxWidth(Pane.USE_PREF_SIZE);
		userPane.setPrefHeight(bounds.getHeight());
		userPane.setMinHeight(Pane.USE_PREF_SIZE);
		userPane.setMaxHeight(Pane.USE_PREF_SIZE);
		userPane.setStyle("-fx-background-color: transparent;");


		getChildren().addAll(mapPane, userPane);
	}

	/**
	 * get the map containing the map
	 *
	 * @return map pane
	 */
	public Pane getMapPane() {
		return mapPane;
	}

	/**
	 * get the top pane
	 *
	 * @return top pane
	 */
	public Pane getUserPane() {
		return userPane;
	}

	/**
	 * place a node into the top pane at the location of the given latitude/longitude coordinates
	 *
	 * @param node      the node - if this is a label
	 * @param latitude  between -90 and 90
	 * @param longitude coordinate -180 and 180
	 * @param center    center on the location
	 */
	public void place(Node node, double latitude, double longitude, boolean center) {
		var location = getLocationOnMap(latitude, longitude);
		node.setLayoutX(location.getX());
		node.setLayoutY(location.getY());

		if (center && (Node) node instanceof Region region) {
			region.applyCss();
			Platform.runLater(() -> {
				region.setLayoutX(region.getLayoutX() - 0.5 * region.getWidth());
				region.setLayoutY(region.getLayoutY() - 0.5 * region.getHeight());
			});
		}
		getUserPane().getChildren().add(node);

	}
	/**
	 * Allows the user to place a node using POINT2D
	 *
	 * @param node      The node to be placed.
	 * @param latLong   The latitude and longitude coordinates representing the location where the node will be placed.
	 *                  The latitude should be between -90 and 90 degrees, and the longitude should be between -180 and 180 degrees.
	 * @param center    Indicates whether to center the place the center of the node at thge specified location.
	 */
	public void place(Node node, Point2D latLong, boolean center) {
		place(node, latLong.getX(), latLong.getY(), center);
	}

	/**
	 * Places a DraggablePieChart node at the specified latitude and longitude coordinates on the map (user-pane).
	 * This method also adds a DraggableLine representing the connection between the pie chart and its associated node.
	 *
	 * @param node      The DraggablePieChart node to be placed.
	 * @param latitude  The latitude value (-90 to 90).
	 * @param longitude The longitude value (-180 to 180).
	 * @param center    Indicates whether to center the node at the specified location.
	 */
	public void placeChart (DraggablePieChart node, double latitude, double longitude, boolean center){
		var location = getLocationOnMap(latitude, longitude);
		node.getPieChart().setLayoutX(location.getX());
		node.getPieChart().setLayoutY(location.getY());
		node.updateCenter();


		if (center && (Node) node.getPieChart() instanceof Region region) {
			region.applyCss();
			Platform.runLater(() -> {
				region.setLayoutX(region.getLayoutX() - 0.5 * region.getWidth());
				region.setLayoutY(region.getLayoutY() - 0.5 * region.getHeight());
			});
		}

		DraggableLine line = new DraggableLine(node);
		getUserPane().getChildren().add(line.getLine());
		getUserPane().getChildren().add(node.getPieChart());

	}
	/**
	 * Places a node at the default coordinates (20, 20) within the user pane.
	 *
	 * @param node The node to be placed.
	 */
	public void place(Node node){
		node.setLayoutX(20);
		node.setLayoutY(20);
		userPane.getChildren().add(node);
	}
	/**
	 * Retrieves the Point2D location on the map corresponding to the given latitude and longitude coordinates.
	 *
	 * @param latitude  The latitude value (-90 to 90).
	 * @param longitude The longitude value (-180 to 180).
	 * @return The Point2D location on the map.
	 */
	public Point2D getLocationOnMap(double latitude, double longitude) {
		return new Point2D(getMapX(longitude), getMapY(latitude));
	}
	/**
	 * Calculates and returns the X-coordinate on the map corresponding to the given longitude value.
	 *
	 * @param longitude The longitude value (-180 to 180).
	 * @return The X-coordinate on the map.
	 */
	public double getMapX(double longitude) {
		return longitudeXFunction.apply(longitude);
	}
	/**
	 * Calculates and returns the Y-coordinate on the map corresponding to the given latitude value.
	 *
	 * @param latitude The latitude value (-90 to 90).
	 * @return The Y-coordinate on the map.
	 */
	public double getMapY(double latitude) {
		return latitudeYFunction.apply(latitude);
	}
	/**
	 * Sets the bounds (LatLongRect) of the map.
	 *
	 * @param bounds The bounds of the map.
	 */
	public void setBounds(LatLongRect bounds) {
		this.bounds = bounds;
	}
	/**
	 * Retrieves the bounds (LatLongRect) of the map.
	 *
	 * @return The bounds of the map.
	 */
	public LatLongRect getBounds() {
		return bounds;
	}
}
