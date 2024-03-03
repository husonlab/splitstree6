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
import javafx.concurrent.Service;
import javafx.concurrent.Task;
import javafx.scene.Node;
import javafx.scene.SnapshotParameters;
import javafx.scene.image.Image;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import jloda.fx.util.ClipboardUtils;

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

		controller.getCopyMenuItem().setOnAction(e -> ClipboardUtils.putImage(createImage(controller.getStackPane())));

		controller.getRedrawButton().setOnAction(e -> redraw(mapView));
		controller.getRedrawButton().disableProperty().bind(emptyProperty);
	}

	public void redraw(MapView mapView) {
		var model = mapView.getModel();
		var controller = mapView.getController();

		try {
			var width = mapView.getStage().getWidth() - 10;
			var height = mapView.getStage().getHeight() - 80;
			controller.getStackPane().getChildren().clear();
			controller.getStackPane().getChildren().setAll(ComputeMap.apply(model, width, height));
		} catch (Exception ex) {
			controller.getLabel().setText("Error: " + ex.getMessage());
		}
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
