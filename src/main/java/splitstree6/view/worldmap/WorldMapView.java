/*
 *  WorldMapView.java Copyright (C) 2024 Daniel H. Huson
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
import javafx.beans.InvalidationListener;
import javafx.beans.WeakInvalidationListener;
import javafx.beans.binding.Bindings;
import javafx.beans.property.*;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.Pane;
import javafx.scene.shape.Shape;
import javafx.util.Pair;
import jloda.fx.control.Legend;
import jloda.fx.icons.MaterialIcons;
import jloda.fx.undo.UndoManager;
import jloda.fx.util.*;
import splitstree6.data.TaxaBlock;
import splitstree6.data.TraitsBlock;
import splitstree6.main.SplitsTree6;
import splitstree6.tabs.viewtab.ViewTab;
import splitstree6.view.format.locations.LocationsFormat;
import splitstree6.view.utils.IView;
import splitstree6.window.MainWindow;

import java.util.List;
import java.util.function.BiConsumer;

public class WorldMapView implements IView {
	private final MainWindow mainWindow;
	private final UndoManager undoManager = new UndoManager();

	private final WorldMapController controller;
	private final WorldMapPresenter presenter;

	private final ObjectProperty<ViewTab> viewTab = new SimpleObjectProperty<>(this, "viewTab");

	private final StringProperty name = new SimpleStringProperty(this, "name");

	private final BooleanProperty empty = new SimpleBooleanProperty(this, "empty", false);

	private final BooleanProperty optionShowContinentNames = new SimpleBooleanProperty(this, "optionShowContinentNames");
	private final BooleanProperty optionShowCountryNames = new SimpleBooleanProperty(this, "optionShowContinentNames");
	private final BooleanProperty optionShowOceanNames = new SimpleBooleanProperty(this, "optionShowOceanNames");

	private final DoubleProperty optionMaxCircleRadius = new SimpleDoubleProperty(this, "optionMaxCircleRadius", 16.0);

	private final BooleanProperty optionShowBoundingBox = new SimpleBooleanProperty(this, "optionShowBoundingBox");

	private final BooleanProperty optionShowGrid = new SimpleBooleanProperty(this, "optionShowGrid");

	private final BooleanProperty optionShowData = new SimpleBooleanProperty(this, "optionShowData", true);
	private final BooleanProperty optionTwoCopies = new SimpleBooleanProperty(this, "optionTwoCopies");

	private final ObjectProperty<TaxaBlock> workingTaxa = new SimpleObjectProperty<>();
	private final ObjectProperty<TraitsBlock> traitsBlock = new SimpleObjectProperty<>();

	private final InvalidationListener validListener;

	private final Legend legend;

	private final InvalidationListener selectionListener;

	private final BiConsumer<MouseEvent, String> clickOnLabel;

	{
		ProgramProperties.track(optionShowContinentNames, true);
		ProgramProperties.track(optionShowCountryNames, false);
		ProgramProperties.track(optionShowOceanNames, true);
		ProgramProperties.track(optionShowGrid, false);
		ProgramProperties.track(optionShowBoundingBox, true);
		ProgramProperties.track(optionTwoCopies, false);
	}

	public List<String> listOptions() {
		return List.of(optionShowContinentNames.getName(), optionShowCountryNames.getName(), optionShowOceanNames.getName(),
				optionShowBoundingBox.getName(), optionShowGrid.getName(), optionTwoCopies.getName(), optionMaxCircleRadius.getName());
	}

	public WorldMapView(MainWindow mainWindow, String name, ViewTab viewTab) {
		this.mainWindow = mainWindow;
		this.name.set(name);
		viewTab.setGraphic(MaterialIcons.graphic(MaterialIcons.map));

		var formatter = new LocationsFormat(mainWindow, undoManager);
		legend = formatter.getLegend();
		legend.maxCircleRadiusProperty().bindBidirectional(optionMaxCircleRadius);
		legend.setMaxCircleRadius(optionMaxCircleRadius.get());
		legend.maxCircleRadiusProperty().addListener((v, o, n) -> updatePies(o.doubleValue(), n.doubleValue()));
		legend.setEditable(true);
		legend.getStyleClass().add("viewer-background");
		legend.setPadding(new Insets(3, 3, 3, 3));

		var loader = new ExtendedFXMLLoader<WorldMapController>(WorldMapController.class);
		controller = loader.getController();

		presenter = new WorldMapPresenter(mainWindow, this);
		empty.bind(Bindings.isEmpty(presenter.getWorldMap1().getUserItems().getChildren()));
		viewTab.emptyProperty().bind(empty);

		controller.getFormatVBox().getChildren().addAll(formatter);
		controller.getFormatVBox().setDisable(false);

		Platform.runLater(() -> legend.setMaxCircleRadius(optionMaxCircleRadius.get()));


		legend.setClickOnLabel((e, label) -> {
			if (label == null || !e.isShiftDown() || !SplitsTree6.isDesktop())
				mainWindow.getTaxonSelectionModel().clearSelection();
			var taxon = mainWindow.getWorkingTaxa().get(label);
			if (taxon != null)
				Platform.runLater(() -> mainWindow.getTaxonSelectionModel().toggleSelection(taxon));
		});

		traitsBlock.addListener(e -> {
			legend.getLabels().setAll(workingTaxa.get().getLabels());
			updateNodes();
			Platform.runLater(() -> controller.getZoomToFitButton().getOnAction().handle(null));
		});

		AnchorPane.setLeftAnchor(legend, 5.0);
		AnchorPane.setTopAnchor(legend, 30.0);
		DraggableLabel.makeDraggable(legend);
		controller.getInnerAnchorPane().getChildren().add(legend);

		validListener = e -> {
			if (mainWindow.getWorkflow().isValid()) {
				workingTaxa.set(mainWindow.getWorkflow().getWorkingTaxaBlock());
				if (workingTaxa.get() != null) {
					traitsBlock.set(workingTaxa.get().getTraitsBlock());
					legend.getLabels().setAll(workingTaxa.get().getLabels());
					legend.getActive().addAll(workingTaxa.get().getLabels());
				} else {
					traitsBlock.set(null);
				}
			}
		};
		workingTaxa.set(mainWindow.getWorkflow().getWorkingTaxaBlock());
		mainWindow.getWorkflow().validProperty().addListener(new WeakInvalidationListener(validListener));

		setViewTab(viewTab);

		Platform.runLater(() -> validListener.invalidated(null));

		selectionListener = e -> {
			for (var shape : BasicFX.getAllRecursively(getPresenter().getWorldMap1().getUserItems(), Shape.class)) {
				shape.setEffect(null);
				if (shape.getUserData() instanceof String string) {
					if (mainWindow.getWorkingTaxa() != null) {
						var taxon = mainWindow.getWorkingTaxa().get(string);
						if (taxon != null && mainWindow.getTaxonSelectionModel().isSelected(taxon)) {
							shape.setEffect(SelectionEffectBlue.getInstance());
							if (shape.getParent() instanceof Pane pane) {
								pane.getChildren().remove(shape);
								pane.getChildren().add(shape);

							}
						}
					}
				}
			}
		};
		mainWindow.getTaxonSelectionModel().getSelectedItems().addListener(selectionListener);
		selectionListener.invalidated(null);

		clickOnLabel = (e, label) -> {
			if (label == null || !e.isShiftDown() || !SplitsTree6.isDesktop())
				mainWindow.getTaxonSelectionModel().clearSelection();
			if (label != null) {
				var taxon = mainWindow.getWorkingTaxa().get(label);
				if (taxon != null)
					mainWindow.getTaxonSelectionModel().select(taxon);
			}
		};
	}

	@Override
	public String getName() {
		return name.get();
	}

	@Override
	public Node getRoot() {
		return controller.getAnchorPane();
	}

	@Override
	public void setupMenuItems() {
		presenter.setupMenuItems();
	}

	@Override
	public void setViewTab(ViewTab viewTab) {
		this.viewTab.set(viewTab);
	}

	@Override
	public int size() {
		return presenter.getWorldMap1().getUserItems().getChildren().size();
	}

	@Override
	public UndoManager getUndoManager() {
		return undoManager;
	}

	@Override
	public ReadOnlyBooleanProperty emptyProperty() {
		return empty;
	}

	@Override
	public Node getMainNode() {
		return controller.getInnerAnchorPane();
	}

	@Override
	public void clear() {
		presenter.getWorldMap1().clear();
		presenter.getWorldMap2().clear();
	}

	@Override
	public WorldMapPresenter getPresenter() {
		return presenter;
	}

	public WorldMapController getController() {
		return controller;
	}


	@Override
	public String getCitation() {
		return null;
	}

	public BooleanProperty optionShowContinentNamesProperty() {
		return optionShowContinentNames;
	}

	public BooleanProperty optionShowCountryNamesProperty() {
		return optionShowCountryNames;
	}

	public BooleanProperty optionShowOceanNamesProperty() {
		return optionShowOceanNames;
	}

	public BooleanProperty optionShowGridProperty() {
		return optionShowGrid;
	}

	public BooleanProperty optionShowBoundingBoxProperty() {
		return optionShowBoundingBox;
	}

	public BooleanProperty optionTwoCopiesProperty() {
		return optionTwoCopies;
	}

	public BooleanProperty optionShowDataProperty() {
		return optionShowData;
	}

	private void updatePies(double oldSize, double newSize) {
		if (oldSize > 1 && newSize > 1) {
			for (var worldMap : List.of(presenter.getWorldMap1(), presenter.getWorldMap2())) {
				for (var pie : BasicFX.getAllRecursively(worldMap, BasicPieChart.class)) {
					pie.setScaleX(pie.getScaleX() / oldSize * newSize);
					pie.setScaleY(pie.getScaleY() / oldSize * newSize);
				}
			}
		}
	}

	public void updateNodes() {
		var taxaBlock = workingTaxa.get();
		var traitsBlock = getTraitsBlock();
		var maxRadius = legend.getMaxCircleRadius();
		if (taxaBlock != null && traitsBlock != null) {
			var maxCount = computeMaxCount(taxaBlock, traitsBlock);
			presenter.getWorldMap1().clear();
			presenter.getWorldMap2().clear();

			for (var traitId = 1; traitId < traitsBlock.getNTraits(); traitId++) {
				var lat = traitsBlock.getTraitLatitude(traitId);
				var lon = traitsBlock.getTraitLongitude(traitId);
				if (lat != 0 || lon != 0) {
					presenter.getWorldMap1().addUserItem(setupChart(taxaBlock, traitsBlock, legend, traitId, maxCount, maxRadius, clickOnLabel), lat, lon);
					presenter.getWorldMap2().addUserItem(setupChart(taxaBlock, traitsBlock, legend, traitId, maxCount, maxRadius, clickOnLabel), lat, lon);
				}
			}

			presenter.getWorldMap1().expandDataRectangle(0.2);
			presenter.getWorldMap2().expandDataRectangle(0.2);

			legend.setMaxCount(maxCount);
		} else legend.setMaxCount(0);
	}

	private static Node setupChart(TaxaBlock taxaBlock, TraitsBlock traitsBlock, Legend legend, int traitId, double maxCount, double maxRadius, BiConsumer<MouseEvent, String> clickOnLabel) {
		var chart = new BasicPieChart(traitsBlock.getTraitLabel(traitId));
		chart.setClickOnLabel(clickOnLabel);
		chart.getColorMap().clear();
		var total = 0.0;
		for (var t = 1; t <= taxaBlock.getNtax(); t++) {
			var name = taxaBlock.getLabel(t);
			chart.getColorMap().put(name, legend.getColorForName(name));
			var value = traitsBlock.getTraitValue(t, traitId);
			chart.getData().add(new Pair<>(name, value));
			total += value;
		}
		if (maxCount > 0)
			chart.setRadius(maxRadius / Math.sqrt(maxCount) * Math.sqrt(total));
		else chart.setRadius(0);
		return chart;
	}

	private static double computeMaxCount(TaxaBlock taxaBlock, TraitsBlock traitsBlock) {
		var max = 0.0;
		for (var traitId = 1; traitId < traitsBlock.getNTraits(); traitId++) {
			var count = 0.0;
			var lat = traitsBlock.getTraitLatitude(traitId);
			var lon = traitsBlock.getTraitLongitude(traitId);
			if (lat != 0 || lon != 0) {
				for (var t = 1; t <= taxaBlock.getNtax(); t++) {
					var value = traitsBlock.getTraitValue(t, traitId);
					count += value;
				}
			}
			max = Math.max(max, count);
		}
		return max;
	}

	public Legend getLegend() {
		return legend;
	}

	public TraitsBlock getTraitsBlock() {
		return traitsBlock.get();
	}

	public ObjectProperty<TraitsBlock> traitsBlockProperty() {
		return traitsBlock;
	}
}
