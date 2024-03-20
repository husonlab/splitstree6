/*
 *  ExportDialog.java Copyright (C) 2024 Daniel H. Huson
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

package splitstree6.dialog.exporting.data;

import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.scene.Scene;
import javafx.scene.control.MenuItem;
import javafx.stage.Modality;
import javafx.stage.Stage;
import jloda.fx.util.ExtendedFXMLLoader;
import splitstree6.main.SplitsTree6;
import splitstree6.main.Version;
import splitstree6.window.MainWindow;
import splitstree6.workflow.DataNode;

/**
 * export dialog
 * Daniel Huson, 11.21
 */
public class ExportDialog {
	/**
	 * show the export dialog
	 *
	 * @param mainWindow
	 * @param dataNode
	 */
	public static void show(MainWindow mainWindow, DataNode dataNode) {
		var loader = new ExtendedFXMLLoader<ExportDialogController>(ExportDialogController.class);
		ExportDialogController controller = loader.getController();

		var scene = new Scene(loader.getRoot());

		var stage = new Stage();
		stage.setTitle("Export data - " + dataNode.getTitle() + " - " + Version.NAME);
		//stage.setAlwaysOnTop(true);
		stage.setScene(scene);
		stage.sizeToScene();
		stage.setX(SplitsTree6.isDesktop() ? mainWindow.getStage().getX() + 100 : 100);
		stage.setY(SplitsTree6.isDesktop() ? mainWindow.getStage().getY() + 100 : 100);

		stage.initModality(Modality.APPLICATION_MODAL);
		stage.initOwner(mainWindow.getStage());

		new ExportDialogPresenter(mainWindow, controller, stage, dataNode);
		stage.show();
	}

	/**
	 * create a menu item for this dialog
	 */
	public static MenuItem createMenuItem(MainWindow mainWindow, DataNode dataNode, ReadOnlyBooleanProperty empty) {
		var menuItem = new MenuItem("Export data...");
		menuItem.setOnAction(e -> ExportDialog.show(mainWindow, dataNode));
		if (dataNode == null || empty == null)
			menuItem.disableProperty().bind(new SimpleBooleanProperty(true));
		else
			menuItem.disableProperty().bind(empty);
		return menuItem;
	}
}
