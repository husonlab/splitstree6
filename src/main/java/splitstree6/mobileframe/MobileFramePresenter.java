/*
 *  MobileFramePresenter.java Copyright (C) 2023 Daniel H. Huson
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
import javafx.collections.ListChangeListener;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import jloda.fx.util.RecentFilesManager;
import jloda.fx.util.RunAfterAWhile;
import jloda.fx.window.IMainWindow;
import jloda.fx.window.MainWindowManager;
import jloda.util.FileUtils;
import splitstree6.dialog.SaveDialog;
import splitstree6.window.MainWindow;

import java.io.File;
import java.io.IOException;
import java.util.Objects;

/**
 * top-level mobile frame presenter
 * Daniel Huson, 12.2023
 */
public class MobileFramePresenter {
	private final MobileFrame view;

	public MobileFramePresenter(MobileFrame view) {
		this.view = view;
		var controller = view.getController();
		var filesTab = view.getFilesTab();

		controller.getTabPane().setTabClosingPolicy(TabPane.TabClosingPolicy.ALL_TABS);
		controller.getTabPane().setFocusTraversable(false);
		controller.getTabPane().setOnSwipeLeft(e -> {
		});
		controller.getTabPane().setOnSwipeUp(e -> {
		});
		controller.getTabPane().setOnSwipeRight(e -> {
		});
		controller.getTabPane().setOnSwipeDown(e -> {
		});

		controller.getTabPane().getTabs().add(filesTab);

		MainWindowManager.getInstance().getMainWindows().addListener((ListChangeListener<? super IMainWindow>) e -> {
			while (e.next()) {
				for (var w : e.getRemoved()) {
					if (w instanceof MainWindow mainWindow) {
						var tab = view.getWindowTabMap().get(mainWindow);
						controller.getTabPane().getTabs().remove(tab);
					}
				}
				for (var w : e.getAddedSubList()) {
					if (w instanceof MainWindow mainWindow) {
						var tab = new Tab();
						view.getWindowTabMap().put(mainWindow, tab);
						tab.setClosable(true);
						if (false) {
							var closeMenuItem = new MenuItem("Close");
							closeMenuItem.setOnAction(a -> filesTab.getTabPane().getTabs().remove(tab));
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
								RunAfterAWhile.applyInFXThread(MobileFramePresenter.this, () -> {
									if (!n && !mainWindow.isEmpty() && mainWindow.isDirty()) {
										try {
											var fileInfo = view.findFileInfo(mainWindow.getFileName());
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

			controller.getTabPane().getSelectionModel().selectedItemProperty().addListener((v, o, n) -> {
				if (o != null && o.getUserData() instanceof MainWindow prevMainWindow
					&& prevMainWindow.getTaxonSelectionModel().size() > 0
					&& n != null && n.getUserData() instanceof MainWindow curMainWindow && curMainWindow.getWorkflow().getWorkingTaxaBlock() != null) {
					var taxonBlock = curMainWindow.getWorkflow().getWorkingTaxaBlock();
					if (taxonBlock != null) {
						prevMainWindow.getTaxonSelectionModel().getSelectedItems().stream()
								.map(t -> curMainWindow.getWorkflow().getWorkingTaxaBlock().get(t.getName()))
								.filter(Objects::nonNull).forEach(t -> curMainWindow.getTaxonSelectionModel().select(t));
					}
				}
			});
		});


		controller.getTopToolBar().setOnMousePressed(e -> {
			view.setHideTabs(!view.isHideTabs());
			if (view.isHideTabs()) {
				controller.getTabPane().setStyle("-fx-tab-min-height: 0; -fx-tab-max-height: 0; -fx-padding: -4 0 0 0;");
			} else {
				controller.getTabPane().setStyle("-fx-tab-min-height: 20; -fx-tab-max-height: 30; -fx-padding: 0 0 0 0;");
			}

		});
	}

}
