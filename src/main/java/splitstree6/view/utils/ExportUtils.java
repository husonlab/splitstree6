/*
 *  ExportUtils.java Copyright (C) 2023 Daniel H. Huson
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

package splitstree6.view.utils;

import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.scene.control.MenuButton;
import javafx.scene.control.SeparatorMenuItem;
import jloda.fx.util.BasicFX;
import splitstree6.data.ViewBlock;
import splitstree6.dialog.exporting.data.ExportDialog;
import splitstree6.window.MainWindow;
import splitstree6.workflow.DataNode;

import java.util.List;

/**
 * setup the export menu button using existing main menu items
 * Daniel Huson, 11.2023
 */
public class ExportUtils {
	public static void setup(MenuButton menuButton, MainWindow mainWindow, DataNode dataNode, ReadOnlyBooleanProperty emptyProperty) {
		if (dataNode.getDataBlock() instanceof ViewBlock)
			dataNode = dataNode.getPreferredParent().getPreferredParent();

		var mainController = mainWindow.getController();
		menuButton.getItems().setAll(BasicFX.copyMenu(List.of(mainController.getCopyMenuItem(),
				mainController.getCopyImageMenuItem(), new SeparatorMenuItem(), mainController.getPrintMenuItem(),
				new SeparatorMenuItem(), mainController.getExportImageMenuItem(),
				ExportDialog.createMenuItem(mainWindow, dataNode, emptyProperty))));
	}
}