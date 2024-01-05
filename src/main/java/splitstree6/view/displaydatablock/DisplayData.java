/*
 * DisplayData.java Copyright (C) 2024 Daniel H. Huson
 *
 * (Some files contain contributions from other authors, who are then mentioned separately.)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package splitstree6.view.displaydatablock;

import jloda.fx.util.ExtendedFXMLLoader;
import splitstree6.view.displaytext.DisplayTextView;
import splitstree6.window.MainWindow;
import splitstree6.workflow.DataNode;

/**
 * display data as text
 * Daniel Huson, 11.21
 */
public class DisplayData extends DisplayTextView {
	private final DisplayDataController controller;

	public DisplayData(MainWindow mainWindow, DataNode dataNode, String name, boolean editable) {
		super(mainWindow, name, editable);

		var loader = new ExtendedFXMLLoader<DisplayDataController>(DisplayDataController.class);
		controller = loader.getController();

		new DisplayDataPresenter(mainWindow, this, controller, dataNode);
	}

	public DisplayDataController getDisplayDataController() {
		return controller;
	}
}
