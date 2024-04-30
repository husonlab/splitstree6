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
import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
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

	private final WorldMapController controller;

	public WorldMapPresenter(MainWindow mainWindow, WorldMapView view) {
		this.mainWindow = mainWindow;
		this.view = view;
		controller = view.getController();

		worldMap1 = new WorldMap();
		worldMap2 = new WorldMap();

		var hbox = new HBox();
		hbox.getStyleClass().add("viewer-background");
		hbox.setSpacing(0);
		HBox.setHgrow(worldMap1, Priority.NEVER);
		HBox.setHgrow(worldMap2, Priority.NEVER);

		var scrollPane = controller.getZoomableScrollPane();
		scrollPane.setContent(hbox);
		scrollPane.setFitToWidth(false);

		var zoom = new SimpleDoubleProperty(1.0);
		zoom.addListener((v, o, n) -> {
			worldMap1.changeScale(o.doubleValue(), n.doubleValue());
			worldMap2.changeScale(o.doubleValue(), n.doubleValue());
		});

		scrollPane.setUpdateScaleMethod(() -> zoom.set(zoom.get() * scrollPane.getZoomFactorY()));

		controller.getZoomInButton().setOnAction(e -> {
			zoom.set(zoom.get() * 1.1);
			Platform.runLater(this::centerOnData);
		});
		// todo: add disable binding
		controller.getZoomOutButton().setOnAction(e ->
		{
			zoom.set(zoom.get() / 1.1);
			Platform.runLater(this::centerOnData);
		});

		controller.getZoomToFitButton().setOnAction(e -> {
			var paneRect = worldMap1.localToScreen(worldMap1.getBoundsInLocal());
			var dataRect = worldMap1.getDataRectangle().localToScreen(worldMap1.getDataRectangle().getBoundsInLocal());

			if (!worldMap1.getUserItems().getChildren().isEmpty() && dataRect.getHeight() > 0 && dataRect.getWidth() > 0) {
				var scale = Math.min(paneRect.getWidth() / dataRect.getWidth(), paneRect.getHeight() / dataRect.getHeight());
				if (scale > 0) {
					zoom.set(scale);
					scrollPane.applyCss();
					Platform.runLater(this::centerOnData);
				}
			}
		});

		controller.getBoundingBoxMenuItem().selectedProperty().bindBidirectional(view.optionShowBoundingBoxProperty());

		for (var worldMap : List.of(worldMap1, worldMap2)) {
			worldMap.getContinents().visibleProperty().bindBidirectional(view.optionShowContinentNamesProperty());
			worldMap.getCountries().visibleProperty().bindBidirectional(view.optionShowCountryNamesProperty());
			worldMap.getOceans().visibleProperty().bindBidirectional(view.optionShowOceanNamesProperty());
			worldMap.getGrid().visibleProperty().bindBidirectional(view.optionShowGridProperty());
			worldMap.getUserItems().visibleProperty().bindBidirectional(view.optionShowDataProperty());
			worldMap.getDataRectangle().strokeProperty().bind(Bindings.createObjectBinding(() -> (view.optionShowBoundingBoxProperty().get() ? Color.LAVENDER : Color.TRANSPARENT), view.optionShowBoundingBoxProperty()));
		}

		hbox.getChildren().add(worldMap1);
		var pane = new Pane();
		HBox.setHgrow(pane, Priority.ALWAYS);
		hbox.getChildren().add(pane);

		if (view.optionTwoCopiesProperty().get() && !hbox.getChildren().contains(worldMap2)) {
			hbox.getChildren().add(worldMap2);
		}

		controller.getTwoCopiesToggleButton().selectedProperty().addListener((v, o, n) -> {
			if (n) {
				if (!hbox.getChildren().contains(worldMap2)) {
					hbox.getChildren().add(1, worldMap2);
				}
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

		Platform.runLater(this::setupMenuItems);
	}

	public void centerOnData() {
		var scrollPane = controller.getZoomableScrollPane();
		var contentPane = (Pane) scrollPane.getContent();

		Point2D previous;
		var delta = new Point2D(Double.MAX_VALUE, Double.MAX_VALUE);
		do {
			previous = delta;
			var scrollPaneRect = scrollPane.localToScreen(scrollPane.getBoundsInLocal());
			var targetOnScreen = new Point2D(scrollPaneRect.getCenterX(), scrollPaneRect.getCenterY());

			var dataRect = worldMap1.getDataRectangle().localToScreen(worldMap1.getDataRectangle().getBoundsInLocal());
			var dataCenter = new Point2D(dataRect.getCenterX(), dataRect.getCenterY());
			var allRect = contentPane.localToScreen(contentPane.getBoundsInLocal());

			delta = targetOnScreen.subtract(dataCenter);

			scrollPane.setHvalue(scrollPane.getHvalue() - delta.getX() / allRect.getWidth());
			scrollPane.setVvalue(scrollPane.getVvalue() - delta.getY() / allRect.getHeight());
			scrollPane.applyCss();

		}
		while ((Math.abs(delta.getX()) >= 1 || Math.abs(delta.getY()) >= 1) &&
			   (Math.abs(delta.getX()) < Math.abs(previous.getX()) || Math.abs(delta.getY()) < Math.abs(previous.getY())));

	}

	@Override
	public void setupMenuItems() {
		var controller = view.getController();
		var mainController = mainWindow.getController();

		mainController.getShowQRCodeMenuItem().setSelected(false);
		mainController.getShowQRCodeMenuItem().disableProperty().unbind();
		mainController.getShowQRCodeMenuItem().setDisable(true);

		mainController.getZoomInMenuItem().setOnAction(controller.getZoomInButton().getOnAction());
		mainController.getZoomInMenuItem().disableProperty().bind(controller.getZoomOutButton().disableProperty());

		var labelsVisible = new SimpleBooleanProperty(false);
		labelsVisible.bind(worldMap1.getContinents().visibleProperty().or(worldMap1.getCountries().visibleProperty()).or(worldMap1.getOceans().visibleProperty()));
		mainController.getIncreaseFontSizeMenuItem().setOnAction(e -> {
			worldMap1.setFont(Font.font(worldMap1.getFont().getName(), worldMap1.getFont().getSize() * 1.1));
			worldMap2.setFont(Font.font(worldMap2.getFont().getName(), worldMap2.getFont().getSize() * 1.1));
		});
		mainController.getIncreaseFontSizeMenuItem().disableProperty().bind(labelsVisible.not().and(Bindings.createBooleanBinding(() -> worldMap1.getFont().getSize() < 128, worldMap1.fontProperty())));
		mainController.getDecreaseFontSizeMenuItem().setOnAction(e -> {
			worldMap1.setFont(Font.font(worldMap1.getFont().getName(), worldMap1.getFont().getSize() / 1.1));
			worldMap2.setFont(Font.font(worldMap2.getFont().getName(), worldMap2.getFont().getSize() / 1.1));
		});
		mainController.getDecreaseFontSizeMenuItem().disableProperty().bind(labelsVisible.not().and(Bindings.createBooleanBinding(() -> worldMap1.getFont().getSize() > 6, worldMap1.fontProperty())));

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

	public void addNode(Supplier<Node> supplier, double longitude, double latitude) {
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
