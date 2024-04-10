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
import javafx.beans.binding.Bindings;
import javafx.beans.property.*;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.WeakChangeListener;
import javafx.scene.Node;
import javafx.scene.chart.PieChart;
import jloda.fx.icons.MaterialIcons;
import jloda.fx.undo.UndoManager;
import jloda.fx.util.BasicFX;
import jloda.fx.util.ExtendedFXMLLoader;
import jloda.fx.util.ProgramProperties;
import splitstree6.data.TaxaBlock;
import splitstree6.data.TraitsBlock;
import splitstree6.tabs.IDisplayTabPresenter;
import splitstree6.tabs.viewtab.ViewTab;
import splitstree6.view.format.locations.LocationsFormat;
import splitstree6.view.utils.IView;
import splitstree6.window.MainWindow;

import java.util.List;

public class WorldMapView implements IView {
	private final MainWindow mainWindow;
	private final UndoManager undoManager = new UndoManager();

	private final WorldMapController controller;
	private final WorldMapPresenter presenter;

	private final ObjectProperty<ViewTab> viewTab = new SimpleObjectProperty<>(this, "viewTab");

	private final StringProperty name = new SimpleStringProperty(this, "name");

	private final BooleanProperty empty = new SimpleBooleanProperty(this, "empty", true);

	private final BooleanProperty optionShowContinentNames = new SimpleBooleanProperty(this, "optionShowContinentNames");
	private final BooleanProperty optionShowCountryNames = new SimpleBooleanProperty(this, "optionShowContinentNames");
	private final BooleanProperty optionShowOceanNames = new SimpleBooleanProperty(this, "optionShowOceanNames");
	private final BooleanProperty optionShowGrid = new SimpleBooleanProperty(this, "optionShowGrid");

	private final BooleanProperty optionShowData = new SimpleBooleanProperty(this, "optionShowData");
	private final BooleanProperty optionTwoCopies = new SimpleBooleanProperty(this, "optionTwoCopies");

	private final ObjectProperty<TaxaBlock> workingTaxa = new SimpleObjectProperty<>();
	private final ObjectProperty<TraitsBlock> traitsBlock = new SimpleObjectProperty<>();

	private final ChangeListener<Boolean> validListener;

	{
		ProgramProperties.track(optionShowContinentNames, true);
		ProgramProperties.track(optionShowCountryNames, false);
		ProgramProperties.track(optionShowOceanNames, true);
		ProgramProperties.track(optionShowGrid, false);
		ProgramProperties.track(optionTwoCopies, false);
		ProgramProperties.track(optionShowData, true);
	}

	public List<String> listOptions() {
		return List.of(optionShowContinentNames.getName(), optionShowCountryNames.getName(), optionShowOceanNames.getName(),
				optionShowGrid.getName(), optionTwoCopies.getName());
	}

	public WorldMapView(MainWindow mainWindow, String name, ViewTab viewTab) {
		this.mainWindow = mainWindow;
		this.name.set(name);
		viewTab.setGraphic(MaterialIcons.graphic(MaterialIcons.map));
		var loader = new ExtendedFXMLLoader<WorldMapController>(WorldMapController.class);
		controller = loader.getController();

		// this is the target area for the tree page:
		presenter = new WorldMapPresenter(mainWindow, this);
		empty.bind(Bindings.isEmpty(presenter.getWorldMap1().getUserItems().getChildren()));

		var locationsFormatter = new LocationsFormat(mainWindow, undoManager);
		controller.getFormatVBox().getChildren().addAll(locationsFormatter);
		controller.getFormatVBox().setDisable(false);
		locationsFormatter.optionLocationSizeProperty().addListener((v, o, n) -> updatePies(o.doubleValue(), n.doubleValue()));
		traitsBlock.addListener(e -> {
			updateTraitsData(workingTaxa.get(), traitsBlock.get(), presenter, getMaxCount(), locationsFormatter.getOptionLocationSize());
			locationsFormatter.getLegend().setUnitRadius(getMaxCount());
		});

		validListener = (v, o, n) -> {
			workingTaxa.set(mainWindow.getWorkflow().getWorkingTaxaBlock());
			if (n)
				traitsBlock.set(mainWindow.getWorkflow().getWorkingTaxaBlock().getTraitsBlock());
		};
		workingTaxa.set(mainWindow.getWorkflow().getWorkingTaxaBlock());
		mainWindow.getWorkflow().validProperty().addListener(new WeakChangeListener<>(validListener));

		setViewTab(viewTab);

		Platform.runLater(() -> {
			updateTraitsData(mainWindow.getWorkingTaxa(), mainWindow.getWorkingTaxa().getTraitsBlock(), presenter,
					getMaxCount(), locationsFormatter.getOptionLocationSize());
			locationsFormatter.getLegend().setUnitRadius(getMaxCount());
		});
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
		presenter.getWorldMap1().getUserItems().getChildren().clear();
		presenter.getWorldMap2().getUserItems().getChildren().clear();
	}

	@Override
	public IDisplayTabPresenter getPresenter() {
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

	public BooleanProperty optionTwoCopiesProperty() {
		return optionTwoCopies;
	}

	public BooleanProperty optionShowDataProperty() {
		return optionShowData;
	}

	private void updatePies(double oldSize, double newSize) {
		if (oldSize > 1 && newSize > 1) {
			for (var worldMap : List.of(presenter.getWorldMap1(), presenter.getWorldMap2())) {
				for (var pie : BasicFX.getAllRecursively(worldMap, PieChart.class)) {
					pie.setScaleX(pie.getScaleX() / oldSize * newSize);
					pie.setScaleY(pie.getScaleY() / oldSize * newSize);
				}
			}
		}
	}

	private double getMaxCount() {
		var max = 0.0;
		if (workingTaxa.get() != null && traitsBlock.get() != null) {
			var taxa = workingTaxa.get();
			var traits = traitsBlock.get();
			for (var traitId = 1; traitId < traits.getNTraits(); traitId++) {
				var count = 0.0;
				var lat = traits.getTraitLatitude(traitId);
				var lon = traits.getTraitLongitude(traitId);
				if (lat != 0 || lon != 0) {
					for (var t = 1; t <= taxa.getNtax(); t++) {
						var value = traits.getTraitValue(t, traitId);
						count += value;
					}
				}
				max = Math.max(max, count);
			}
		}
		return max;
	}

	private static void updateTraitsData(TaxaBlock taxaBlock, TraitsBlock traitsBlock, WorldMapPresenter presenter, double maxCount, double maxSize) {
		presenter.getWorldMap1().getUserItems().getChildren().clear();
		presenter.getWorldMap2().getUserItems().getChildren().clear();

		if (taxaBlock != null && traitsBlock != null && traitsBlock.size() > 0) {
			for (var traitId = 1; traitId < traitsBlock.getNTraits(); traitId++) {
				var lat = traitsBlock.getTraitLatitude(traitId);
				var lon = traitsBlock.getTraitLongitude(traitId);
				if (lat != 0 || lon != 0) {
					presenter.getWorldMap1().addUserItem(setupChart(taxaBlock, traitsBlock, traitId, maxCount, maxSize), lat, lon);
					presenter.getWorldMap2().addUserItem(setupChart(taxaBlock, traitsBlock, traitId, maxCount, maxSize), lat, lon);
				}
			}
		}
	}

	private static Node setupChart(TaxaBlock taxaBlock, TraitsBlock traitsBlock, int traitId, double maxCount, double maxSize) {
		var chart = new PieChart();
		chart.setLabelsVisible(false);
		chart.setLegendVisible(false);
		var count = 0.0;
		for (var t = 1; t <= taxaBlock.getNtax(); t++) {
			var value = traitsBlock.getTraitValue(t, traitId);
			chart.getData().add(new PieChart.Data(taxaBlock.getLabel(t), value));
			count += value;
		}
		chart.widthProperty().addListener((v, o, n) -> chart.setLayoutX(-0.5 * n.doubleValue()));
		chart.heightProperty().addListener((v, o, n) -> chart.setLayoutY(-0.5 * n.doubleValue()));


		chart.setMinWidth(maxSize);
		chart.setMinHeight(maxSize);
		chart.setPrefWidth(maxSize);
		chart.setPrefHeight(maxSize);
		if (count < maxCount) {
			chart.setScaleX(count / maxCount);
			chart.setScaleY(count / maxCount);
		}
		return chart;
	}
}
