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

import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.scene.layout.Pane;
import jloda.fx.control.Legend;
import jloda.fx.undo.UndoManager;
import jloda.fx.util.ExtendedFXMLLoader;
import jloda.fx.util.FuzzyBoolean;
import jloda.fx.util.ProgramProperties;
import splitstree6.window.MainWindow;

public class LocationsFormat extends Pane {
	private final LocationsFormatController controller;
	private final LocationsFormatPresenter presenter;

	private final Legend legend;


	public LocationsFormat(MainWindow mainWindow, UndoManager undoManager) {

		var loader = new ExtendedFXMLLoader<LocationsFormatController>(LocationsFormatController.class);
		controller = loader.getController();
		getChildren().add(loader.getRoot());

		legend = new Legend(FXCollections.observableArrayList(), "Twenty", Orientation.VERTICAL);
		ProgramProperties.track(legend.maxCircleRadiusProperty(), 32.0);
		ProgramProperties.track(legend.showProperty(), FuzzyBoolean::valueOf, FuzzyBoolean.True);

		legend.setScalingType(Legend.ScalingType.sqrt);

		legend.getStyleClass().add("viewer-background");
		legend.setPadding(new Insets(3, 3, 3, 3));

		presenter = new LocationsFormatPresenter(this, undoManager);
	}

	public LocationsFormatController getController() {
		return controller;
	}


	public Legend getLegend() {
		return legend;
	}
}
