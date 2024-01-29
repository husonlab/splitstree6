/*
 *  MapViewPresenter.java Copyright (C) 2024 Daniel H. Huson
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
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Service;
import javafx.concurrent.Task;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.SnapshotParameters;
import javafx.scene.chart.PieChart;
import javafx.scene.image.Image;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.util.*;

public class MapViewPresenter {

	public MapViewPresenter(MapView mapView) {
		var controller = mapView.getController();
		var model = mapView.getModel();

		var emptyProperty = new SimpleBooleanProperty(true);
		model.lastUpdateProperty().addListener(e -> Platform.runLater(() -> emptyProperty.set(model.getCharactersBlock().getNtax() == 0)));
		model.lastUpdateProperty().addListener(e -> Platform.runLater(() -> redraw(mapView)));

		// MenuBar
		controller.getOpenMenuItem().setOnAction(e -> openFile(mapView.getStage(), controller, model));
		controller.getOpenMenuItem().disableProperty().bind(controller.getProgressBar().visibleProperty());

		controller.getCloseMenuItem().setOnAction(e -> Platform.exit());

		controller.getCopyMenuItem().setOnAction(e -> {
			var clipboardContent = new ClipboardContent();
			clipboardContent.putImage(createImage(controller.getStackPane()));
			Clipboard.getSystemClipboard().setContent(clipboardContent);
		});

		controller.getRedrawButton().setOnAction(e -> redraw(mapView));
		controller.getRedrawButton().disableProperty().bind(emptyProperty);



	}

	public MapPane createMap(MapViewController controller, Model model){
		var locationNameMap = new HashMap<Point2D, String>();
		var traitsBlock = model.getTaxaBlock().getTraitsBlock();
		for(int i = 1; i <= traitsBlock.getNTraits(); i++){
			locationNameMap.put(new Point2D(traitsBlock.getTraitLatitude(i), traitsBlock.getTraitLongitude(i)), "");
		}
		return SingleImageMap.createMapPane(locationNameMap.keySet(), controller.getStackPane().getWidth(), controller.getStackPane().getHeight());
	}






	public void redraw(MapView mapView) {
		var model = mapView.getModel();
		var controller = mapView.getController();

		try {
			controller.getStackPane().getChildren().clear();
			MapPane mapPane = createMap(controller, model);
			ArrayList<GeoTrait> traits = ComputeMap.apply(model);
			for(var trait : traits){
				PieChart pieChart = new PieChart();
				ObservableList obsList = FXCollections.observableList(getPieChartData(trait));
				pieChart.setData(obsList);
				pieChart.prefHeightProperty().bind(controller.getChartSizeSlider().valueProperty());
				pieChart.prefWidthProperty().bind(controller.getChartSizeSlider().valueProperty());
				pieChart.setMinWidth(80);
				pieChart.setMaxWidth(200);
				pieChart.setMinHeight(80);
				pieChart.setMaxHeight(200);
				pieChart.prefWidthProperty().bind(controller.getChartSizeSlider().valueProperty());
				pieChart.prefHeightProperty().bind(controller.getChartSizeSlider().valueProperty());
				mapPane.place(pieChart, trait.getLatitude(), trait.getLongtitude(), true);
			}
			controller.getStackPane().getChildren().add(mapPane);

		} catch (Exception ex) {
			controller.getLabel().setText("Error: " + ex.getMessage());
		}
	}

	public List<PieChart.Data> getPieChartData(GeoTrait trait){
		List<PieChart.Data> data = new ArrayList<>();
		for(String taxa : trait.getTaxa()){
			data.add(new PieChart.Data(taxa, trait.getCompostion().get(taxa)));
		}
		return data;
	}

	private void openFile(Stage stage, MapViewController controller, Model model) {
		final var fileChooser = new FileChooser();
		fileChooser.setTitle("Open file with traits");

		var file = fileChooser.showOpenDialog(stage);
		if (file != null) {
			var service = new Service<Integer>() {
				@Override
				protected Task<Integer> createTask() {
					return new Task<>() {
						@Override
						protected Integer call() throws Exception {
							model.load(file);
							return model.getCharactersBlock().getNtax();
						}
					};
				}
			};
			controller.getProgressBar().visibleProperty().bind(service.runningProperty());
			controller.getProgressBar().progressProperty().bind(service.progressProperty());
			service.setOnSucceeded(v -> {
				System.out.println("Loading succeeded");
				controller.getLabel().setText("Taxa: %,d, Characters: %,d".formatted(model.getTaxaBlock().getNtax(),
						model.getCharactersBlock().getNchar()));
			});
			service.setOnFailed(u -> {
				System.out.println("Loading characters failed");
				controller.getLabel().setText("Loading trees failed");
			});
			service.start();

		}
	}

	private Image createImage(Node node) {
		var parameters = new SnapshotParameters();
		parameters.setTransform(javafx.scene.transform.Transform.scale(2, 2));
		return node.snapshot(parameters, null);
	}
}
