/*
 *  ImportDialog.java Copyright (C) 2023 Daniel H. Huson
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

package splitstree6.dialog.importdialog;

import javafx.scene.Scene;
import javafx.stage.Modality;
import javafx.stage.Stage;
import jloda.fx.util.ExtendedFXMLLoader;
import jloda.fx.util.ProgramProperties;
import splitstree6.window.MainWindow;

public class ImportDialog {
	private final MainWindow mainWindow;
	private final ImportDialogController controller;
	private final ImportDialogPresenter presenter;

	private final Stage stage;

	/**
	 * constructor
	 */
	public ImportDialog(MainWindow mainWindow) {
		this.mainWindow = mainWindow;
		final ExtendedFXMLLoader<ImportDialogController> extendedFXMLLoader = new ExtendedFXMLLoader<>(this.getClass());
		controller = extendedFXMLLoader.getController();

		stage = new Stage();
		stage.initModality(Modality.APPLICATION_MODAL);
		stage.initOwner(mainWindow.getStage());
		stage.getIcons().setAll(ProgramProperties.getProgramIconsFX());

		stage.setScene(new Scene(extendedFXMLLoader.getRoot()));
		stage.sizeToScene();

		stage.setTitle("Import - " + ProgramProperties.getProgramName());

		presenter = new ImportDialogPresenter(this);
	}

	public static void show(MainWindow mainWindow, String fileName) {
		var dialog = new ImportDialog(mainWindow);
		dialog.getStage().show();
		dialog.getController().getFileTextField().setText(fileName);
	}

	public ImportDialogController getController() {
		return controller;
	}

	public ImportDialogPresenter getPresenter() {
		return presenter;
	}

	public Stage getStage() {
		return stage;
	}

	public MainWindow getMainWindow() {
		return mainWindow;
	}
}
