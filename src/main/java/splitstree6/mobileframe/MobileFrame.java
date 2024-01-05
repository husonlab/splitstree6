/*
 *  MobileFrame.java Copyright (C) 2024 Daniel H. Huson
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

package splitstree6.mobileframe;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableMap;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Tab;
import javafx.stage.Stage;
import jloda.fx.util.RecentFilesManager;
import jloda.fx.window.MainWindowManager;
import jloda.fx.window.NotificationManager;
import jloda.util.FileUtils;
import splitstree6.dialog.importdialog.ImportDialog;
import splitstree6.io.nexus.workflow.WorkflowNexusInput;
import splitstree6.io.readers.ImportManager;
import splitstree6.main.SplitsTree6;
import splitstree6.mobileframe.filestab.FileItem;
import splitstree6.mobileframe.filestab.FilesTab;
import splitstree6.tabs.inputeditor.InputEditorTab;
import splitstree6.window.MainWindow;
import splitstree6.workflow.WorkflowSetup;

import java.io.File;
import java.io.IOException;
import java.util.Objects;

/**
 * the top-level stage used in the mobile version of the program
 * Daniel Huson, 12.23
 */
public class MobileFrame {
	private final MobileFrameController controller;

	private final MobileFramePresenter presenter;

	private final Stage stage;

	private final FilesTab filesTab;
	private final ObservableMap<MainWindow, Tab> windowTabMap = FXCollections.observableHashMap();

	public MobileFrame(Stage stage) {
		this.stage = stage;
		stage.setOnHidden(e -> System.exit(0));

		var fxmlLoader = new FXMLLoader();
		try (var ins = Objects.requireNonNull(getClass().getResource("MobileFrame.fxml")).openStream()) {
			fxmlLoader.load(ins);
		} catch (IOException ex) {
			throw new RuntimeException(ex);
		}
		controller = fxmlLoader.getController();
		stage.setScene(new Scene(controller.getRootPane()));
		filesTab = new FilesTab(new File(SplitsTree6.getUserDirectory()), stage, this::openFile, this::closeFile);
		filesTab.setClosable(false);

		presenter = new MobileFramePresenter(this);
	}

	public Stage getStage() {
		return stage;
	}

	public MobileFrameController getController() {
		return controller;
	}

	public MobileFramePresenter getPresenter() {
		return presenter;
	}

	public ObservableMap<MainWindow, Tab> getWindowTabMap() {
		return windowTabMap;
	}

	public FilesTab getFilesTab() {
		return filesTab;
	}

	public FileItem findFileInfo(String fileName) {
		var file = new File(fileName);
		for (var fileInfo : getFilesTab().getController().getTableView().getItems()) {
			if (file.equals(new File(fileInfo.getPath())))
				return fileInfo;
		}
		return null;
	}

	public void openFile(String fileName) {
		var newWindow = new MainWindow();
		newWindow.setFileName(fileName);
		newWindow.show(new Stage(), 0, 0, stage.getWidth(), stage.getHeight());

		MainWindowManager.getInstance().addMainWindow(newWindow);
		newWindow.fileNameProperty().addListener((v, o, n) -> System.err.println("Name changed: " + n));

		if (!FileUtils.fileExistsAndIsNonEmpty(fileName) || (new File(fileName)).length() < 10) {
			var tab = new InputEditorTab(newWindow);
			Platform.runLater(() -> newWindow.getController().getMainTabPane().getSelectionModel().select(tab));
		} else if (WorkflowNexusInput.isApplicable(fileName)) {
			WorkflowNexusInput.open(newWindow, fileName, ex -> NotificationManager.showError("Open file failed: " + ex), () -> RecentFilesManager.getInstance().insertRecentFile(fileName));
		} else {
			var importManager = ImportManager.getInstance();
			if (importManager.getReaders(fileName).size() == 1) { // unique input format
				newWindow.getPresenter().getSplitPanePresenter().ensureTreeViewIsOpen(false);
				WorkflowSetup.apply(fileName, newWindow.getWorkflow(), ex -> NotificationManager.showError("Open file failed: " + ex), () -> RecentFilesManager.getInstance().insertRecentFile(fileName), importManager.getReaders(fileName).get(0).getToClass());
				newWindow.setDirty(true);
			} else {
				// ImportDialog.show(mainWindow, fileName);
				newWindow.getPresenter().getSplitPanePresenter().ensureTreeViewIsOpen(false);
				ImportDialog.show(newWindow, fileName);
			}
		}
	}

	public void closeFile(String fileName) {
		for (var window : windowTabMap.keySet()) {
			if (FileUtils.equals(fileName, window.getFileName())) {
				controller.getTabPane().getTabs().remove(windowTabMap.get(window));
				windowTabMap.remove(window);
				return;
			}
		}
	}


}

