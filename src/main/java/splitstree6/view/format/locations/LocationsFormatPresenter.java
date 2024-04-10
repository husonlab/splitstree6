/*
 *  LocationsFormatPresenter.java Copyright (C) 2024 Daniel H. Huson
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

import jloda.fx.undo.UndoManager;
import jloda.fx.util.FuzzyBoolean;
import jloda.util.NumberUtils;

public class LocationsFormatPresenter {
	public LocationsFormatPresenter(LocationsFormat format, UndoManager undoManager) {
		var controller = format.getController();
		controller.getMaxSizeField().setText(String.valueOf(format.getOptionLocationSize()));

		format.optionLocationSizeProperty().addListener((v, o, n) -> controller.getMaxSizeField().setText(String.valueOf(n.intValue())));
		controller.getMaxSizeField().textProperty().addListener((v, o, n) -> format.setOptionLocationSize(NumberUtils.parseInt(n)));

		FuzzyBoolean.setupCheckBox(controller.getLegendCBox(), format.getLegend().showProperty());

		format.optionLocationSizeProperty().addListener((v, o, n) -> undoManager.add("traits node size", format.optionLocationSizeProperty(), o, n));
		format.optionLocationLegendProperty().addListener((v, o, n) -> undoManager.add("show legend", format.optionLocationLegendProperty(), o, n));
	}
}
