/*
 *  WorldMapPresenter.java Copyright (C) 2024 Daniel H. Huson
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

import javafx.application.Platform;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.scene.Node;
import javafx.scene.layout.HBox;
import jloda.fx.find.FindToolBar;
import splitstree6.tabs.IDisplayTabPresenter;
import splitstree6.utils.worldmap.WorldMap;
import splitstree6.view.utils.ExportUtils;
import splitstree6.window.MainWindow;

import java.util.List;
import java.util.function.Supplier;

public class WorldMapPresenter implements IDisplayTabPresenter {
	private final WorldMap worldMap1;
	private final WorldMap worldMap2;

	private final WorldMapView view;
	private final MainWindow mainWindow;

	public WorldMapPresenter(MainWindow mainWindow, WorldMapView view) {
		this.mainWindow = mainWindow;
		this.view = view;
		var controller = view.getController();

		worldMap1 = new WorldMap();
		worldMap2 = new WorldMap();

		var hbox = new HBox();
		hbox.setStyle("-fx-padding: 10 10 10 60;");
		hbox.getStyleClass().add("viewer-background");
		hbox.setSpacing(-20);

		var scrollPane = controller.getZoomableScrollPane();
		scrollPane.setContent(hbox);

		var update1 = worldMap1.createUpdateScaleMethod(scrollPane);
		var update2 = worldMap2.createUpdateScaleMethod(scrollPane);
		scrollPane.setUpdateScaleMethod(() -> {
			update1.run();
			update2.run();
		});

		controller.getZoomInButton().setOnAction(e -> {
			scrollPane.zoomBy(1.1, 1.1);
			hbox.setSpacing(1.1 * hbox.getSpacing());
		});
		// todo: add disable binding
		controller.getZoomOutButton().setOnAction(e ->
		{
			scrollPane.zoomBy(1.0 / 1.1, 1.0 / 1.1);
			hbox.setSpacing(1.0 / 1.1 * hbox.getSpacing());
		});

		controller.getZoomToFitButton().setOnAction(e -> {
			var paneRect = worldMap1.localToScreen(worldMap1.getBoundsInLocal());
			var dataRect = worldMap1.getDataRectangle().localToScreen(worldMap1.getDataRectangle().getBoundsInLocal());

			var scale = Math.min(paneRect.getWidth() / dataRect.getWidth(), paneRect.getHeight() / dataRect.getHeight());

			System.err.println("scale: " + scale);
			if (scale > 0) {
				scale *= 0.5;
				scrollPane.zoomBy(scale / scrollPane.getZoomX(), scale / scrollPane.getZoomY());
				Platform.runLater(() -> {
					var allRect = hbox.localToScreen(hbox.getBoundsInLocal());
					var dataRect2 = worldMap1.getDataRectangle().localToScreen(worldMap1.getDataRectangle().getBoundsInLocal());
					var scrollPaneRect = scrollPane.localToScreen(scrollPane.getBoundsInLocal());

					var hbar = scrollPane.getHorizontalScrollBar();
					System.err.println("hBar: " + hbar.getMin() + " - " + hbar.getMax() + ", " + hbar.getVisibleAmount() + " value: " + hbar.getValue());

					var vbar = scrollPane.getVerticalScrollBar();
					System.err.println("vbar: " + vbar.getMin() + " - " + vbar.getMax() + ", " + vbar.getVisibleAmount() + " value: " + vbar.getValue());

					{
						var center = scrollPane.screenToLocal(dataRect2.getCenterX(), dataRect2.getCenterY());

						var h = (hbar.getMax() - hbar.getMin() + hbar.getVisibleAmount()) / allRect.getWidth() * (center.getX() - allRect.getMinX());

						double hValue = (center.getX() - scrollPaneRect.getWidth()) / 2 / (allRect.getWidth() - scrollPaneRect.getWidth());
						double vValue = (center.getY() - scrollPaneRect.getHeight()) / 2 / (allRect.getHeight() - scrollPaneRect.getHeight());

						scrollPane.setHvalue(hValue);
						scrollPane.setHvalue(vValue);
					}

					if (false) {

						System.err.println("dataRect: " + dataRect);

						System.err.println("allRect: " + allRect);

						System.err.println("scrollPaneRect: " + scrollPaneRect);


						System.err.println("dataRect2: " + dataRect2);

						var hValue = Math.max(0, dataRect2.getCenterX() / (allRect.getWidth()) - 0.5 * hbar.getVisibleAmount());
						var vValue = 1.0 - Math.max(0, dataRect2.getCenterY() / (allRect.getHeight()) - 0.5 * vbar.getVisibleAmount());
						Platform.runLater(() -> {
							hbar.setValue(hValue);
							vbar.setValue(vValue);
						});
					}

				});
			}
		});

		for (var worldMap : List.of(worldMap1, worldMap2)) {
			worldMap.getContinents().visibleProperty().bindBidirectional(view.optionShowContinentNamesProperty());
			worldMap.getCountries().visibleProperty().bindBidirectional(view.optionShowCountryNamesProperty());
			worldMap.getOceans().visibleProperty().bindBidirectional(view.optionShowOceanNamesProperty());
			worldMap.getGrid().visibleProperty().bindBidirectional(view.optionShowGridProperty());
			worldMap.getUserItems().visibleProperty().bindBidirectional(view.optionShowDataProperty());
		}

		controller.getTwoCopiesToggleButton().selectedProperty().addListener((v, o, n) -> {
			if (n) {
				if (!hbox.getChildren().contains(worldMap2))
					hbox.getChildren().add(worldMap2);
			} else {
				hbox.getChildren().remove(worldMap2);
			}
		});

		controller.getContinentNamesCheckMenuItem().selectedProperty().bindBidirectional(view.optionShowContinentNamesProperty());
		controller.getCountryNamesCheckMenuItem().selectedProperty().bindBidirectional(view.optionShowCountryNamesProperty());
		controller.getOceansCheckMenuItem().selectedProperty().bindBidirectional(view.optionShowOceanNamesProperty());
		controller.getGridCheckMenuItem().selectedProperty().bindBidirectional(view.optionShowGridProperty());
		controller.getTwoCopiesToggleButton().selectedProperty().bindBidirectional(view.optionTwoCopiesProperty());
		controller.getShowDataButton().selectedProperty().bindBidirectional(view.optionShowDataProperty());

		hbox.getChildren().add(worldMap1);
		if (view.optionTwoCopiesProperty().get() && !hbox.getChildren().contains(worldMap2)) {
			hbox.getChildren().add(worldMap2);
		}

		Platform.runLater(this::setupMenuItems);
	}

	@Override
	public void setupMenuItems() {
		var controller = view.getController();
		var mainController = mainWindow.getController();
		mainController.getShowQRCodeMenuItem().disableProperty().bind(new SimpleBooleanProperty(true));

		mainController.getZoomInMenuItem().setOnAction(controller.getZoomInButton().getOnAction());
		mainController.getZoomInMenuItem().disableProperty().bind(controller.getZoomOutButton().disableProperty());

		mainController.getZoomOutMenuItem().setOnAction(controller.getZoomOutButton().getOnAction());
		mainController.getZoomOutMenuItem().disableProperty().bind(controller.getZoomOutButton().disableProperty());
		ExportUtils.setup(mainWindow, null, view.emptyProperty());
	}

	@Override
	public FindToolBar getFindToolBar() {
		return null;
	}

	@Override
	public boolean allowFindReplace() {
		return false;
	}

	public void addNode(Supplier<Node> supplier, double latitude, double longitude) {
		worldMap1.addUserItem(supplier.get(), latitude, longitude);
		worldMap2.addUserItem(supplier.get(), latitude, longitude);
	}

	public WorldMap getWorldMap1() {
		return worldMap1;
	}

	public WorldMap getWorldMap2() {
		return worldMap2;
	}
}
