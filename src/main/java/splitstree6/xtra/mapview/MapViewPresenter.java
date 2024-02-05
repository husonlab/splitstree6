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
import javafx.scene.paint.Color;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.util.*;

import static splitstree6.xtra.mapview.ColorSchemes.SCHEME1;

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

				ObservableList obsList = FXCollections.observableList(getPieChartData(trait));
				DraggablePieChart pieChart = new DraggablePieChart(obsList);
				pieChart.getPieChart().prefHeightProperty().bind(controller.getChartSizeSlider().valueProperty());
				pieChart.getPieChart().prefWidthProperty().bind(controller.getChartSizeSlider().valueProperty());
				pieChart.getPieChart().setMinWidth(80);
				pieChart.getPieChart().setMaxWidth(200);
				pieChart.getPieChart().setMinHeight(80);
				pieChart.getPieChart().setMaxHeight(200);
				pieChart.getPieChart().setCenterShape(true);
				pieChart.getPieChart().prefWidthProperty().bind(controller.getChartSizeSlider().valueProperty());
				pieChart.getPieChart().prefHeightProperty().bind(controller.getChartSizeSlider().valueProperty());
				mapPane.placeChart(pieChart, trait.getLatitude(), trait.getLongtitude(), true);
				for(int i = 0; i < pieChart.getPieChart().getData().size(); i++){
					// Convert Color to CSS representation
					// Use the cssColor in your style
					System.out.println("Color " + i);
					String style = "-fx-pie-color: " + SCHEME1.get(i) + ";";
					pieChart.getPieChart().getData().get(i).getNode().setStyle(style);
				}
			}
			//Legend
			Map<String, Integer> colorMap = new HashMap<>();
			colorMap.put("seq_1", 1);
			colorMap.put("seq_2", 2);
			colorMap.put("seq_3", 3);
			colorMap.put("seq_4", 4);
			colorMap.put("seq_5", 5);
			colorMap.put("seq_6", 6);
			colorMap.put("seq_7", 7);
			LegendView legendView = new LegendView(colorMap);
			legendView.setLayoutX(50);
			legendView.setLayoutY(50);
			controller.getStackPane().getChildren().add(mapPane);
			legendView.setMouseTransparent(false);

			controller.getStackPane().getChildren().add(legendView);

		} catch (Exception ex) {
			controller.getInfoLabel().setText("Error: " + ex.getMessage());
		}
	}

	public List<PieChart.Data> getPieChartData(GeoTrait trait){
		List<PieChart.Data> data = new ArrayList<>();
		for(int i = 0; i < trait.getnTaxa(); i++){
			PieChart.Data nData = new PieChart.Data(trait.getTaxa().get(i), trait.getCompostion().get(trait.getTaxa().get(i)));
			data.add(nData);
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
				controller.getInfoLabel().setText("Taxa: %,d, Characters: %,d".formatted(model.getTaxaBlock().getNtax(),
						model.getCharactersBlock().getNchar()));
			});
			service.setOnFailed(u -> {
				System.out.println("Loading characters failed");
				controller.getInfoLabel().setText("Loading trees failed");
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
