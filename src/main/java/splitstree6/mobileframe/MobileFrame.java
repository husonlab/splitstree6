/*
 *  MobileFrame.java Copyright (C) 2023 Daniel H. Huson
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
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableMap;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.stage.Stage;
import jloda.fx.util.RecentFilesManager;
import jloda.fx.util.RunAfterAWhile;
import jloda.fx.window.IMainWindow;
import jloda.fx.window.MainWindowManager;
import jloda.fx.window.NotificationManager;
import jloda.util.FileUtils;
import splitstree6.dialog.SaveDialog;
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

public class MobileFrame {
	private final MainFrameController controller;

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

		controller.getTabPane().setTabClosingPolicy(TabPane.TabClosingPolicy.ALL_TABS);

		filesTab = new FilesTab(new File(SplitsTree6.getUserDirectory()), stage, this::openFile, this::closeFile);
		filesTab.setClosable(false);
		controller.getTabPane().getTabs().add(filesTab);

		MainWindowManager.getInstance().getMainWindows().addListener((ListChangeListener<? super IMainWindow>) e -> {
			while (e.next()) {
				for (var w : e.getRemoved()) {
					if (w instanceof MainWindow mainWindow) {
						var tab = windowTabMap.get(mainWindow);
						controller.getTabPane().getTabs().remove(tab);
					}
				}
				for (var w : e.getAddedSubList()) {
					if (w instanceof MainWindow mainWindow) {
						var tab = new Tab();
						windowTabMap.put(mainWindow, tab);
						tab.setClosable(true);
						if (false) {
							var closeMenuItem = new MenuItem("Close");
							closeMenuItem.setOnAction(a -> getRecentFilesTab().getTabPane().getTabs().remove(tab));
							tab.setContextMenu(new ContextMenu(closeMenuItem));
						}

						tab.setOnCloseRequest(a -> MainWindowManager.getInstance().getMainWindows().remove(mainWindow));

						Platform.runLater(() -> {
							mainWindow.getController().getTopVBox().getChildren().remove(mainWindow.getController().getToolBarBorderPane());
							mainWindow.getController().getOutsideBorderPane().setTop(mainWindow.getController().getToolBarBorderPane());
							mainWindow.getController().getRootPane().requestLayout();
							mainWindow.getController().getFileMenuButton().setVisible(false);

							if (mainWindow.getStage() != null) {
								mainWindow.getStage().hide();
							}
						});
						Platform.runLater(() -> {
							tab.setContent(mainWindow.getController().getRootPane());
							tab.textProperty().bind(mainWindow.nameProperty());
							controller.getTabPane().getTabs().add(tab);
							filesTab.getTabPane().getSelectionModel().select(tab);
						});
						// setup auto-save:
						Platform.runLater(() -> {
							mainWindow.getWorkflow().runningProperty().addListener((v, o, n) -> {
								RunAfterAWhile.applyInFXThread(MobileFrame.this, () -> {
									if (!n && !mainWindow.isEmpty() && mainWindow.isDirty()) {
										try {
											var fileInfo = findFileInfo(mainWindow.getFileName());
											var suffix = FileUtils.getFileSuffix(mainWindow.getFileName());
											if (!suffix.equals(".stree6"))
												mainWindow.setFileName(FileUtils.replaceFileSuffix(mainWindow.getFileName(), ".stree6"));
											FileUtils.checkFileWritable(mainWindow.getFileName(), true);
											SaveDialog.save(mainWindow, false, new File(mainWindow.getFileName()));
											if (false)
												RecentFilesManager.getInstance().insertRecentFile(mainWindow.getFileName());
											if (fileInfo != null) {
												fileInfo.setName(mainWindow.getName());
												fileInfo.updateInfo();
												var pos = filesTab.getController().getTableView().getItems().indexOf(fileInfo);
												filesTab.getController().getTableView().getItems().remove(pos);
												Platform.runLater(() -> {
													filesTab.getController().getTableView().getItems().add(pos, fileInfo);
													filesTab.getController().getTableView().sort();
												});
											}
										} catch (IOException ignored) {
										}
									}
								});
							});
						});
					}
				}
			}
		});
	}

	public FilesTab getRecentFilesTab() {
		return filesTab;
	}

	private void openFile(String fileName) {
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

	private void closeFile(String fileName) {
		for (var window : windowTabMap.keySet()) {
			if (FileUtils.equals(fileName, window.getFileName())) {
				controller.getTabPane().getTabs().remove(windowTabMap.get(window));
				windowTabMap.remove(window);
				return;
			}
		}
	}

	public FileItem findFileInfo(String fileName) {
		var file = new File(fileName);
		for (var fileInfo : getRecentFilesTab().getController().getTableView().getItems()) {
			if (file.equals(new File(fileInfo.getPath())))
				return fileInfo;
		}
		return null;
	}
}

