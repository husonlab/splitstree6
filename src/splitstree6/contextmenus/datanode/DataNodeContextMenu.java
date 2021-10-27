/*
 *  AlgorithmNodeContextMenu.java Copyright (C) 2021 Daniel H. Huson
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

package splitstree6.contextmenus.datanode;

import javafx.geometry.Point2D;
import javafx.scene.control.ContextMenu;
import jloda.fx.util.ExtendedFXMLLoader;
import splitstree6.window.MainWindow;
import splitstree6.workflow.DataNode;

public class DataNodeContextMenu {
	private final ContextMenu contextMenu;

	public DataNodeContextMenu(MainWindow mainWindow, Point2D screenLocation, DataNode dataNode) {
		var loader = new ExtendedFXMLLoader<DataNodeContextMenuController>(DataNodeContextMenuController.class);

		var controller = loader.getController();

		new DataNodeContextMenuPresenter(mainWindow, screenLocation, controller, dataNode);

		contextMenu = controller.getContextMenu();
	}

	public ContextMenu getContextMenu() {
		return contextMenu;
	}

	public static ContextMenu create(MainWindow mainWindow, Point2D screenLocation, DataNode dataNode) {
		var menu = new DataNodeContextMenu(mainWindow, screenLocation, dataNode);
		return menu.getContextMenu();
	}
}
