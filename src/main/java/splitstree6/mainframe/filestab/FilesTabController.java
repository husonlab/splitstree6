/*
 *  FilesTabController.java Copyright (C) 2023 Daniel H. Huson
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

package splitstree6.mainframe.filestab;

import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.AnchorPane;
import jloda.fx.icons.MaterialIcons;
import jloda.util.Basic;
import splitstree6.utils.Platform;

import java.nio.file.attribute.FileTime;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public class FilesTabController {
	@FXML
	private AnchorPane rootPane;

	@FXML
	private TableView<FileItem> tableView;

	@FXML
	private TableColumn<FileItem, String> nameColumn;

	@FXML
	private TableColumn<FileItem, FileTime> dateColumn;

	@FXML
	private TableColumn<FileItem, Long> sizeColumn;

	@FXML
	private TableColumn<FileItem, String> kindColumn;

	@FXML
	private Button deleteButton;

	@FXML
	private Button duplicateButton;

	@FXML
	private Button newButton;

	@FXML
	private Button openButton;

	@FXML
	private void initialize() {
		MaterialIcons.setIcon(newButton, "add_box");
		MaterialIcons.setIcon(deleteButton, "delete");
		MaterialIcons.setIcon(duplicateButton, "content_copy");
		MaterialIcons.setIcon(openButton, "open_in_new");

		tableView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
		tableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

		nameColumn.setCellValueFactory(new PropertyValueFactory<>("name"));
		nameColumn.setCellFactory(column -> new TableCell<>() {
			@Override
			protected void updateItem(String name, boolean empty) {
				super.updateItem(name, empty);
				if (empty || name == null) {
					setText(null);
					setTooltip(null);
				} else {
					setText(name);
					if (Platform.isDesktop()) {
						var fileInfo = getTableView().getItems().get(getIndex());
						setTooltip(new Tooltip(fileInfo.getPath())); // todo: place the path here
					}
				}
			}
		});

		dateColumn.setCellValueFactory(new PropertyValueFactory<>("date"));
		var formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
		dateColumn.setCellFactory(column -> new TableCell<>() {
			@Override
			protected void updateItem(FileTime date, boolean empty) {
				super.updateItem(date, empty);
				if (empty || date == null) {
					setText(null);
				} else {
					var localDateTime = LocalDateTime.ofInstant(date.toInstant(), ZoneId.systemDefault());
					setText(formatter.format(localDateTime));
					setAlignment(Pos.CENTER_RIGHT);
				}
			}
		});

		sizeColumn.setCellValueFactory(new PropertyValueFactory<>("size"));
		sizeColumn.setCellFactory(column -> new TableCell<>() {
			@Override
			protected void updateItem(Long size, boolean empty) {
				super.updateItem(size, empty);
				if (empty || size == null) {
					setText(null);
				} else {
					setText(Basic.getMemorySizeString(size));
					setAlignment(Pos.CENTER_RIGHT);
				}
			}
		});
		kindColumn.setCellValueFactory(new PropertyValueFactory<>("kind"));
	}

	public TableView<FileItem> getTableView() {
		return tableView;
	}

	public TableColumn<FileItem, String> getNameColumn() {
		return nameColumn;
	}

	public TableColumn<FileItem, FileTime> getDateColumn() {
		return dateColumn;
	}

	public TableColumn<FileItem, Long> getSizeColumn() {
		return sizeColumn;
	}

	public TableColumn<FileItem, String> getKindColumn() {
		return kindColumn;
	}

	public Button getDeleteButton() {
		return deleteButton;
	}

	public Button getDuplicateButton() {
		return duplicateButton;
	}

	public Button getNewButton() {
		return newButton;
	}

	public Button getOpenButton() {
		return openButton;
	}

	public AnchorPane getRootPane() {
		return rootPane;
	}
}
