/*
 *  Try.java Copyright (C) 2024 Daniel H. Huson
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

package splitstree6.view.worldmap;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToolBar;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;
import jloda.fx.control.ZoomableScrollPane;

public class Try extends Application {
	@Override
	public void start(Stage stage) throws Exception {

		var worldMap1 = new WorldMap();
		var worldMap2 = new WorldMap();

		var hbox = new HBox(worldMap1, worldMap2);
		hbox.setSpacing(-20);

		var scrollPane = new ZoomableScrollPane(hbox);

		var update1 = worldMap1.createUpdateScaleMethod(scrollPane);
		var update2 = worldMap2.createUpdateScaleMethod(scrollPane);
		scrollPane.setUpdateScaleMethod(() -> {
			update1.run();
			update2.run();
		});

		var zoomIn = new Button("Zoom in");
		zoomIn.setOnAction(e -> {
			scrollPane.zoomBy(1.1, 1.1);
		});
		var zoomOut = new Button("Zoom out");
		zoomOut.setOnAction(e -> {
			scrollPane.zoomBy(1.0 / 1.1, 1.0 / 1.1);
		});

		var showCountries = new ToggleButton("Countries");
		showCountries.selectedProperty().bindBidirectional(worldMap1.getCountries().visibleProperty());
		showCountries.selectedProperty().bindBidirectional(worldMap2.getCountries().visibleProperty());
		var showContinents = new ToggleButton("Continents");
		showContinents.selectedProperty().bindBidirectional(worldMap1.getContinents().visibleProperty());
		showContinents.selectedProperty().bindBidirectional(worldMap2.getContinents().visibleProperty());

		var showGrid = new ToggleButton("Grid");
		showGrid.selectedProperty().bindBidirectional(worldMap1.getGrid().visibleProperty());
		showGrid.selectedProperty().bindBidirectional(worldMap2.getGrid().visibleProperty());

		var showWrapAround = new ToggleButton("Wrap around");
		showWrapAround.setSelected(true);
		showWrapAround.selectedProperty().addListener((v, o, n) -> {
			if (n) {
				if (!hbox.getChildren().contains(worldMap2))
					hbox.getChildren().add(worldMap2);
			} else {
				hbox.getChildren().remove(worldMap2);
			}
		});

		var root = new BorderPane();
		root.setTop(new ToolBar(zoomIn, zoomOut, showCountries, showContinents, showGrid, showWrapAround));
		root.setCenter(scrollPane);
		stage.setScene(new Scene(root, 900, 800));
		stage.show();
	}
}
