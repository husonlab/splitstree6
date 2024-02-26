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

package splitstree6.xtra.mapview;

import javafx.application.Platform;
import javafx.geometry.Point2D;
import javafx.geometry.Rectangle2D;
import javafx.scene.Node;
import javafx.scene.chart.PieChart;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import jloda.fx.shapes.CircleShape;

import java.util.List;
import java.util.function.Function;

/**
 * a world map view consisting of a lower pane showing the map and an upper pane containing user items
 * Daniel Huson, 12.2023
 */
public class MapPane extends StackPane {
	private final Pane mapPane;
	private final Pane userPane;

	LatLongRect bounds;

	private final Function<Double, Double> latitudeYFunction;
	private final Function<Double, Double> longitudeXFunction;

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
		System.out.println("label " + latitude + " " + longitude);
		var location = getLocationOnMap(latitude, longitude);
		node.setLayoutX(location.getX());
		node.setLayoutY(location.getY());



		if (center && (Node) node instanceof Region region) {
			//System.out.println("applying css");
			region.applyCss();
			Platform.runLater(() -> {
				region.setLayoutX(region.getLayoutX() - 0.5 * region.getWidth());
				region.setLayoutY(region.getLayoutY() - 0.5 * region.getHeight());
			});
		}
		//System.out.println("Placing " + node.getLayoutX() + " " + node.getLayoutY());
		getUserPane().getChildren().add(node);

	}

	public void place(Node node, Point2D latLong, boolean center) {
		place(node, latLong.getX(), latLong.getY(), center);
	}

	public void placeChart (DraggablePieChart node, double latitude, double longitude, boolean center){
		System.out.println("pie " + latitude + " " + longitude);
		var location = getLocationOnMap(latitude, longitude);
		node.getPieChart().setLayoutX(location.getX());
		node.getPieChart().setLayoutY(location.getY());
		node.updateCenter();


		if (center && (Node) node.getPieChart() instanceof Region region) {
			//System.out.println("applying css");
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


	public Point2D getLocationOnMap(double latitude, double longitude) {
		return new Point2D(getMapX(longitude), getMapY(latitude));
	}

	public double getMapX(double longitude) {
		return longitudeXFunction.apply(longitude);
	}

	public double getMapY(double latitude) {
		return latitudeYFunction.apply(latitude);
	}

	public void setBounds(LatLongRect bounds) {
		this.bounds = bounds;
	}

	public LatLongRect getBounds() {
		return bounds;
	}
}
