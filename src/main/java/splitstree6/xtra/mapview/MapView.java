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
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.chart.PieChart;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.BorderPane;
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


		var locationNameMap = new HashMap<Point2D, String>();

		locationNameMap.put(new Point2D(40.7128, -74.0060), "New York");
		//locationNameMap.put(new Point2D(51.5074, -0.1278), "London");
		locationNameMap.put(new Point2D(48.52, 9.05), "Tuebingen");
		//locationNameMap.put(new Point2D(48.4914, 9.2043), "Reutlingen");
		//locationNameMap.put(new Point2D(48.5363, 9.2846), "Metzingen");
		locationNameMap.put(new Point2D(1.3521, 103.8198),"Singapore");
		locationNameMap.put(new Point2D(-33.9249, 18.4241), "Cape Town");
		locationNameMap.put(new Point2D(-36.88, 174.786991), "Auckland");


		MapPane mapPane;

		mapPane = SingleImageMap.createMapPane(locationNameMap.keySet(), 1200, 800);

		for (var entry : locationNameMap.entrySet()) {
			mapPane.place(new Label(entry.getValue()), entry.getKey(), true);
		}

		ArrayList<PieChart> diagramms = createTestDiagramms(controller, locationNameMap);
		ArrayList<Point2D> locs = new ArrayList<>();
		for(var all : locationNameMap.entrySet()){
			locs.add(all.getKey());
		}

		for(int i = 0; i <diagramms.size() ; i++){
			diagramms.get(i).setMaxSize(20, 20);

			//diagramms.get(i).maxWidthProperty().bind(controller.getChartSizeSlider().valueProperty());
			//diagramms.get(i).maxHeightProperty().bind(controller.getChartSizeSlider().valueProperty());
			//diagramms.get(i).setMaxWidth(10);
			//diagramms.get(i).setMaxHeight(10);
			mapPane.place(diagramms.get(i), locs.get(i).getX(), locs.get(i).getY(), true);
		}
		controller.getStackPane().getChildren().add(mapPane);




		stage.setScene(new Scene(root));
		stage.sizeToScene();
		stage.setTitle("MapView");
		stage.show();
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

	private ArrayList<PieChart> createTestDiagramms(MapViewController controller, HashMap<Point2D, String> locs){
		ArrayList<PieChart> diagramms = new ArrayList<>();
		for(var point : locs.keySet()){
			PieChart chart = new PieChart();
			List data = generateRandomData();

			ObservableList obsList = FXCollections.observableList(data);
			chart.setData(obsList);
			diagramms.add(chart);
		}
		return diagramms;
	}

	private List<PieChart.Data> generateRandomData() {
		List<PieChart.Data> data = new ArrayList<>();
		Random random = new Random();

		// Generate random data for the pie chart
		for (int i = 0; i < 5; i++) {
			String category = "Category " + (i + 1);
			double value = random.nextDouble() * 100; // Random value between 0 and 100
			data.add(new PieChart.Data(category, value));
		}

		return data;
	}

}
