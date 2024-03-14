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
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import jloda.fx.util.ClipboardUtils;
import splitstree6.xtra.mapview.mapbuilder.ComputeMap;
import splitstree6.xtra.mapview.mapbuilder.MapPane;
import splitstree6.xtra.mapview.mapbuilder.SingleImageMap;
import splitstree6.xtra.mapview.nodes.*;

import java.util.*;

/**
 * Presenter class for the MapView.
 * Nikolas Kreisz 01.2024
 */
public class MapViewPresenter {
	ArrayList<DraggablePieChart> charts = new ArrayList<DraggablePieChart>();
	LegendView legendView;

	/**
	 * Constructs a MapViewPresenter with the given MapView.
	 *
	 * @param mapView The MapView instance to associate with the presenter.
	 */
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

		controller.getCopyMenuItem().setOnAction(e -> ClipboardUtils.putImage(createImage(controller.getStackPane())));

		controller.getRedrawButton().setOnAction(e -> redraw(mapView));
		controller.getRedrawButton().disableProperty().bind(emptyProperty);

		// Initialize UI elements
		initChoiceBoxColors(controller);
	}
	/**
	 * Opens a file with traits and loads it into the model.
	 *
	 * @param stage     The JavaFX stage to display the file chooser dialog.
	 * @param controller The controller associated with the map view.
	 * @param model      The model to load the file into.
	 */
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
							System.out.println("Loading new model at:" + String.valueOf(model.getLastUpdate()));
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
	/**
	 * Redraws the map view based on the current state of the MapView.
	 *
	 * @param mapView The MapView containing the model, controller, and UI elements.
	 */
	public void redraw(MapView mapView) {
		var model = mapView.getModel();
		var controller = mapView.getController();

		try {
			// Clear previous map
			controller.getStackPane().getChildren().clear();
			// Calculate the image shown in the map pane
			MapPane mapPane = createMap(controller, model);
			// Compile traits and labels from the model
			ArrayList<GeoTrait> traits = ComputeMap.apply(model);
			ArrayList<String> traitLabels = new ArrayList<String>();
			traitLabels.addAll(model.getTaxaBlock().getLabels());

			// Calculate, create and place the country labels
			CountryFinder countryFinder = new CountryFinder();
			var mapBounds = mapPane.getBounds();
			ArrayList<CountryFinder.Country> countryLabels = countryFinder.getCountriesForAlpha2Codes(mapBounds.minLatitude(), mapBounds.maxLatitude(), mapBounds.minLongitude(), mapBounds.maxLongitude());
			for(CountryFinder.Country label : countryLabels){
				DraggableLabel draggableLabel = new DraggableLabel(label.name(), controller);
				mapPane.place(draggableLabel, label.latitude(), label.longitude(), true);
			}

			// Create and place the pie charts
			for(var trait : traits){
				ObservableList obsList = FXCollections.observableList(getPieChartData(trait));
				DraggablePieChart pieChart = new DraggablePieChart(obsList, controller);
				mapPane.placeChart(pieChart, trait.getLatitude(), trait.getlongitude(), true);
				pieChart.saveColorIDs(traitLabels);
				pieChart.updateColors(controller.getChoiceBoxColorScheme().getValue());
				charts.add(pieChart);
			}

			// Calculate, Create and place the LegendView
			legendView = new LegendView(traitLabels, controller.getChoiceBoxColorScheme().getValue(), controller);
			legendView.setMouseTransparent(false);
			legendView.visibleProperty().bind(controller.getCheckBoxLegend().selectedProperty().not());
			mapPane.place(legendView);

			controller.getStackPane().getChildren().add(mapPane);
		} catch (Exception ex) {
			controller.getInfoLabel().setText("Error: " + ex.getMessage());
		}
	}
	/**
	 * Creates a map pane based on geographical data from the model and the controller's settings.
	 * This method populates the map with location points extracted from the model's taxa block.
	 *
	 * @param controller The map view controller providing settings and UI components.
	 * @param model      The model containing geographical data.
	 * @return A MapPane instance representing the populated map.
	 */
	public MapPane createMap(MapViewController controller, Model model){
		var locationNameMap = new HashMap<Point2D, String>();
		var traitsBlock = model.getTaxaBlock().getTraitsBlock();
		for(int i = 1; i <= traitsBlock.getNTraits(); i++){
			locationNameMap.put(new Point2D(traitsBlock.getTraitLatitude(i), traitsBlock.getTraitLongitude(i)), "");
		}
		return SingleImageMap.createMapPane(locationNameMap.keySet(), controller.getStackPane().getWidth(), controller.getStackPane().getHeight());
	}
	/**
	 * Retrieves pie chart data from a given geographical trait.
	 * Each data point in the pie chart represents a taxon along with its composition.
	 *
	 * @param trait The geographical trait containing taxonomic data.
	 * @return A list of PieChart.Data objects representing the taxonomic composition.
	 */
	public List<PieChart.Data> getPieChartData(GeoTrait trait){
		List<PieChart.Data> data = new ArrayList<>();
		for(int i = 0; i < trait.getnTaxa(); i++){
			PieChart.Data nData = new PieChart.Data(trait.getTaxa().get(i), trait.getCompostion().get(trait.getTaxa().get(i)));
			data.add(nData);
		}
		return data;
	}
	/**
	 * Creates an image snapshot of a JavaFX node with a 2x scale.
	 *
	 * @param node The JavaFX node to capture as an image.
	 * @return The generated image.
	 */
	private Image createImage(Node node) {
		var parameters = new SnapshotParameters();
		parameters.setTransform(javafx.scene.transform.Transform.scale(2, 2));
		return node.snapshot(parameters, null);
	}
	/**
	 * Updates the colors of all charts and their associated legend views
	 * based on the specified color scheme.
	 *
	 * @param scheme The name of the color scheme to apply.
	 */
	private void updateChartColors(String scheme){
		for(var chart : charts){
			chart.updateColors(scheme);
			legendView.updateColors(scheme);
		}
	}
	/**
	 * Initializes the choice box for selecting color schemes in the map view controller.
	 * Adds predefined color schemes to the choice box and sets a default value.
	 * Also attaches a listener to update chart colors when a new scheme is selected.
	 *
	 * @param controller The map view controller containing the choice box.
	 */
	private void initChoiceBoxColors(MapViewController controller){
		final ObservableList<String> list = FXCollections.observableArrayList("Scheme-1", "Scheme-2", "Scheme-3");
		controller.getChoiceBoxColorScheme().getItems().addAll(list);
		controller.getChoiceBoxColorScheme().setValue("Scheme-1");
		controller.getChoiceBoxColorScheme().valueProperty().addListener((observable, oldValue, newValue) -> {
			updateChartColors(newValue);
		});
	}
}
