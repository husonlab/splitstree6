/*
 *  Copyright (C) 2018. Daniel H. Huson
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

/*
 *  TaxLabelFormatter.java Copyright (C) 2022 Daniel H. Huson
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

package splitstree6.view.format.taxlabels;

import javafx.collections.ObservableMap;
import javafx.scene.layout.Pane;
import jloda.fx.control.RichTextLabel;
import jloda.fx.util.ExtendedFXMLLoader;
import splitstree6.data.parts.Taxon;
import splitstree6.window.MainWindow;

/**
 * provides taxon label formatter
 * Daniel Huson, 1.2022
 */
public class TaxLabelFormatter extends Pane {
	private final TaxLabelFormatterController controller;
	private final TaxLabelFormatterPresenter presenter;

	public TaxLabelFormatter(MainWindow mainWindow, ObservableMap<Taxon, RichTextLabel> taxonLabelMap) {

		var loader = new ExtendedFXMLLoader<TaxLabelFormatterController>(TaxLabelFormatterController.class);
		controller = loader.getController();
		getChildren().add(loader.getRoot());

		presenter = new TaxLabelFormatterPresenter(mainWindow, controller, taxonLabelMap);
	}
}
