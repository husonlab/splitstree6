/*
 *  FilesTabPresenter.java Copyright (C) 2023 Daniel H. Huson
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

package splitstree6.mobileframe.filestab;

import javafx.beans.binding.Bindings;
import javafx.scene.control.*;
import javafx.stage.Stage;
import jloda.fx.util.ProgramProperties;
import jloda.fx.window.NotificationManager;
import jloda.util.Basic;
import jloda.util.FileUtils;
import splitstree6.main.SplitsTree6;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class FilesTabPresenter {
	public FilesTabPresenter(FilesTab filesTab, Stage stage, Consumer<String> fileOpener, Consumer<String> fileCloser) {
		var controller = filesTab.getController();

		for (var file : filesTab.getDirectory().listFiles()) {
			controller.getTableView().getItems().add(new FileItem(file.getPath()));
		}

		controller.getTableView().getSortOrder().add(controller.getDateColumn());
		controller.getDateColumn().setSortType(TableColumn.SortType.DESCENDING);
		controller.getTableView().sort();

		controller.getOpenButton().setOnAction(e -> {
			controller.getTableView().requestFocus();
			for (var fileInfo : controller.getTableView().getSelectionModel().getSelectedItems()) {
				if (fileOpener != null) {
					javafx.application.Platform.runLater(() -> fileOpener.accept(fileInfo.getPath()));
					javafx.application.Platform.runLater(() -> selectFiles(controller, List.of(fileInfo.getPath())));
				}
			}
		});
		controller.getOpenButton().disableProperty().bind(Bindings.isEmpty(controller.getTableView().getSelectionModel().getSelectedItems()));

		controller.getDuplicateButton().setOnAction(e -> {
			controller.getTableView().requestFocus();
			var duplicates = new ArrayList<String>();
			for (var fileInfo : new ArrayList<>(controller.getTableView().getSelectionModel().getSelectedItems())) {
				var duplicate = createUniqueFile(fileInfo.getPath(), controller.getTableView().getItems().stream().map(FileItem::getPath).toList());
				if (FileUtils.fileExistsAndIsNonEmpty(fileInfo.getPath())) {
					var original = new File(fileInfo.getPath());
					try {
						Files.copy(original.toPath(), duplicate.toPath());
					} catch (IOException ex) {
						NotificationManager.showWarning("Duplicate failed: " + ex.getMessage());
					}
				}
				controller.getTableView().getItems().add(new FileItem(duplicate.getPath()));
				duplicates.add(duplicate.getPath());
			}
			javafx.application.Platform.runLater(() -> {
				controller.getTableView().sort();
				selectFiles(controller, duplicates);
			});
			if (duplicates.size() <= 5)
				javafx.application.Platform.runLater(() -> controller.getOpenButton().fire());
		});
		controller.getDuplicateButton().disableProperty().bind(Bindings.isEmpty(controller.getTableView().getSelectionModel().getSelectedItems()));

		controller.getDeleteButton().setOnAction(e -> {
			controller.getTableView().requestFocus();
			if (confirmDelete(stage, controller.getTableView().getSelectionModel().getSelectedItems().size())) {
				for (var fileInfo : new ArrayList<>(controller.getTableView().getSelectionModel().getSelectedItems())) {
					try {
						var file = new File(fileInfo.getPath());
						if (fileCloser != null)
							fileCloser.accept(file.getPath());
						if (file.exists() && !file.delete())
							System.err.println("FAILED to delete: " + file);
						controller.getTableView().getItems().remove(fileInfo);
						javafx.application.Platform.runLater(() -> controller.getTableView().sort());
					} catch (Exception ex) {
						Basic.caught(ex);
					}
				}
			}
		});
		controller.getDeleteButton().disableProperty().bind(Bindings.isEmpty(controller.getTableView().getSelectionModel().getSelectedItems()));

		controller.getNewButton().setOnAction(e -> {
			controller.getTableView().requestFocus();
			var name = requestName(stage, controller.getTableView().getItems().stream().map(FileItem::getPath).toList());
			name = FileUtils.replaceFileSuffix(name, ".stree6");
			if (name != null) {
				try {
					FileUtils.writeLinesToFile(List.of(""), name, false);
				} catch (IOException ex) {
					NotificationManager.showError("Failed to create file: " + name + ": " + ex.getMessage());
					return;
				}
				if (fileOpener != null)
					fileOpener.accept(name);
				controller.getTableView().getItems().add(new FileItem(name));
				javafx.application.Platform.runLater(() -> controller.getTableView().sort());
			}
		});

		controller.getTableView().setRowFactory(tv -> {
			var row = new TableRow<FileItem>();
			row.setOnMouseClicked(event -> {
				if (event.getClickCount() == 2 && !row.isEmpty()) {
					var fileInfo = row.getItem();
					if (fileOpener != null)
						javafx.application.Platform.runLater(() -> fileOpener.accept(fileInfo.getPath()));
				}
			});
			return row;
		});

		// reopen the most recent file:
		if (!controller.getTableView().getItems().isEmpty()) {
			var fileInfo = controller.getTableView().getItems().get(0);
			javafx.application.Platform.runLater(() -> fileOpener.accept(fileInfo.getPath()));
			javafx.application.Platform.runLater(() -> selectFiles(controller, List.of(fileInfo.getPath())));
		}
	}


	private void selectFiles(FilesTabController controller, Collection<String> files) {
		controller.getTableView().getSelectionModel().clearSelection();
		for (var fileInfo : controller.getTableView().getItems()) {
			if (files.contains(fileInfo.getPath()))
				controller.getTableView().getSelectionModel().select(fileInfo);
		}
		controller.getTableView().requestFocus();
	}

	private static boolean confirmDelete(Stage stage, int size) {
		var alert = new Alert(Alert.AlertType.CONFIRMATION);
		alert.initOwner(stage);
		alert.initModality(javafx.stage.Modality.APPLICATION_MODAL);
		alert.setTitle("Deleting files - " + ProgramProperties.getProgramName());
		alert.setHeaderText("Deleting files");
		alert.setContentText("Are you sure that you want to delete %,d file(s)?".formatted(size));
		var result = alert.showAndWait();
		return result.isPresent() && result.get() == ButtonType.OK;
	}

	private static String requestName(Stage stage, List<String> fileNames) {
		var file = createUniqueFile(SplitsTree6.getUserDirectory() + File.separator + "Untitled.stree6", fileNames);
		var dialog = new TextInputDialog(file.getName());
		dialog.initOwner(stage);
		dialog.initModality(javafx.stage.Modality.APPLICATION_MODAL);
		dialog.setTitle("New file - " + ProgramProperties.getProgramName());
		dialog.setHeaderText("Creating a new file");
		dialog.setContentText("Please enter a file name:");
		var result = dialog.showAndWait();
		if (result.isPresent())
			return createUniqueFile(SplitsTree6.getUserDirectory() + File.separator + result.get(), fileNames).getPath();
		else
			return null;
	}

	private static File createUniqueFile(String path, List<String> fileNames) {
		var uniqueFile = new File(path);
		var files = fileNames.stream().map(File::new).collect(Collectors.toSet());
		if (!FileUtils.fileExistsAndIsNonEmpty(uniqueFile) && !files.contains(uniqueFile))
			return uniqueFile;

		var suffix = FileUtils.getFileSuffix(path);
		var name = FileUtils.replaceFileSuffix(path, "");

		var count = 0;
		var matcher = Pattern.compile(".*-(\\d+)$").matcher(name);
		if (matcher.matches()) {
			count = Integer.parseInt(matcher.group(1));
			name = name.substring(0, name.lastIndexOf('-'));
		}
		do {
			uniqueFile = new File(name + "-%d".formatted(++count) + suffix);
		} while (FileUtils.fileExistsAndIsNonEmpty(uniqueFile) || files.contains(uniqueFile));

		return uniqueFile;
	}
}
