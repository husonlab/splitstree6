/*
 *  LocationsFormat.java Copyright (C) 2024 Daniel H. Huson
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

package splitstree6.view.format.locations;

import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.WeakChangeListener;
import javafx.collections.FXCollections;
import javafx.geometry.Orientation;
import javafx.scene.layout.Pane;
import jloda.fx.control.Legend;
import jloda.fx.undo.UndoManager;
import jloda.fx.util.ExtendedFXMLLoader;
import jloda.fx.util.FuzzyBoolean;
import jloda.fx.util.ProgramProperties;
import splitstree6.data.TaxaBlock;
import splitstree6.data.TraitsBlock;
import splitstree6.window.MainWindow;

public class LocationsFormat extends Pane {
	private final LocationsFormatController controller;
	private final LocationsFormatPresenter presenter;

	private final ObjectProperty<TaxaBlock> workingTaxa = new SimpleObjectProperty<>();
	private final ObjectProperty<TraitsBlock> traitsBlock = new SimpleObjectProperty<>();

	private final ChangeListener<Boolean> validListener;

	private final IntegerProperty optionLocationSize = new SimpleIntegerProperty(this, "optionLocationSize");

	private final ObjectProperty<FuzzyBoolean> optionLocationLegend = new SimpleObjectProperty<>(this, "optionLocationLegend", FuzzyBoolean.True);

	private final Legend legend;

	{
		ProgramProperties.track(optionLocationSize, 64);
		ProgramProperties.track(optionLocationLegend, FuzzyBoolean::valueOf, FuzzyBoolean.True);
	}

	public LocationsFormat(MainWindow mainWindow, UndoManager undoManager) {

		var loader = new ExtendedFXMLLoader<LocationsFormatController>(LocationsFormatController.class);
		controller = loader.getController();
		getChildren().add(loader.getRoot());

		validListener = (v, o, n) -> {
			workingTaxa.set(mainWindow.getWorkflow().getWorkingTaxaBlock());
			if (n)
				traitsBlock.set(mainWindow.getWorkflow().getWorkingTaxaBlock().getTraitsBlock());
		};
		workingTaxa.set(mainWindow.getWorkflow().getWorkingTaxaBlock());
		mainWindow.getWorkflow().validProperty().addListener(new WeakChangeListener<>(validListener));

		legend = new Legend(FXCollections.observableArrayList(), "Twenty", Orientation.VERTICAL);
		legend.setScalingType(Legend.ScalingType.sqrt);
		legend.circleMinSizeProperty().bind(optionLocationSizeProperty().multiply(0.5));

		legend.showProperty().bindBidirectional(optionLocationLegend);

		presenter = new LocationsFormatPresenter(this, undoManager);
	}

	public int getOptionLocationSize() {
		return optionLocationSize.get();
	}

	public IntegerProperty optionLocationSizeProperty() {
		return optionLocationSize;
	}

	public FuzzyBoolean getOptionLocationLegend() {
		return optionLocationLegend.get();
	}

	public ObjectProperty<FuzzyBoolean> optionLocationLegendProperty() {
		return optionLocationLegend;
	}

	public void setOptionLocationSize(int optionLocationSize) {
		this.optionLocationSize.set(optionLocationSize);
	}

	public void setOptionLocationLegend(FuzzyBoolean optionLocationLegend) {
		this.optionLocationLegend.set(optionLocationLegend);
	}

	public LocationsFormatController getController() {
		return controller;
	}


	public Legend getLegend() {
		return legend;
	}
}
