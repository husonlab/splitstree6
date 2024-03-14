/*
 *  MapView.java Copyright (C) 2024 Daniel H. Huson
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

import javafx.application.Application;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Bounds;
import javafx.geometry.Point2D;
import javafx.geometry.Rectangle2D;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.chart.PieChart;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.stage.Screen;
import javafx.stage.Stage;

import java.io.IOException;
import java.util.*;

public class MapView extends Application {
	private final Model model = new Model();
	private MapViewController controller;
	private MapViewPresenter presenter;
	private Parent root;
	private Stage stage;

	public static void main(String[] args) {
		launch(args);
	}

	@Override
	public void start(Stage primaryStage) {
		this.stage = primaryStage;
		var fxmlLoader = new FXMLLoader();
		try (var ins = Objects.requireNonNull(MapViewController.class.getResource("MapView.fxml")).openStream()) {
			fxmlLoader.load(ins);
		} catch (IOException ex) {
			throw new RuntimeException(ex);
		}
		controller = fxmlLoader.getController();
		root = fxmlLoader.getRoot();

		presenter = new MapViewPresenter(this);
		stage.setScene(new Scene(root));
		stage.sizeToScene();
		stage.setTitle("MapView");
		stage.show();
		var locationNameMap = new HashMap<Point2D, String>();
	}

	public MapViewController getController() {
		return controller;
	}

	public MapViewPresenter getPresenter() {
		return presenter;
	}

	public Parent getRoot() {
		return root;
	}

	public Stage getStage() {
		return stage;
	}

	public Model getModel() {
		return model;
	}
}
