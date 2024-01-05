/*
 * InputEditorViewPresenter.java Copyright (C) 2024 Daniel H. Huson
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

package splitstree6.view.inputeditor;

import javafx.application.Platform;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.scene.input.Clipboard;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.stage.FileChooser;
import jloda.fx.util.ProgramProperties;
import jloda.fx.util.RunAfterAWhile;
import jloda.util.StringUtils;
import splitstree6.io.readers.ImportManager;
import splitstree6.view.displaytext.DisplayTextViewPresenter;
import splitstree6.window.MainWindow;

import java.io.File;

/**
 * input editor tab presenter
 * Daniel Huson, 10.2021
 */
public class InputEditorViewPresenter {
	private final MainWindow mainWindow;
	private final InputEditorView view;

	private final StringProperty firstLine = new SimpleStringProperty(this, "firstLine", "");

	private final ReadOnlyBooleanProperty TRUE = new SimpleBooleanProperty(true);

	private final DisplayTextViewPresenter displayTextViewPresenter;

	public InputEditorViewPresenter(MainWindow mainWindow, DisplayTextViewPresenter displayTextViewPresenter, InputEditorView view) {
		this.mainWindow = mainWindow;
		this.displayTextViewPresenter = displayTextViewPresenter;
		this.view = view;

		view.setShowLineNumbers(true);
		view.setWrapText(false);

		var tabController = view.getController();
		var toolBarController = view.getInputEditorViewController();

		var codeArea = tabController.getCodeArea();
		codeArea.setEditable(true);

		tabController.getToolBar().getItems().addAll(toolBarController.getToolBar().getItems());

		toolBarController.getParseAndLoadButton().setOnAction(e -> view.parseAndLoad());
		toolBarController.getParseAndLoadButton().disableProperty().bind(view.emptyProperty().or(toolBarController.getFormatLabel().textProperty().isEmpty()));

		// prevent double paste:
		codeArea.addEventHandler(KeyEvent.ANY, e -> {
			if (e.getCode() == KeyCode.V && e.isShortcutDown()) {
				e.consume();
			}
		});

		codeArea.focusedProperty().addListener((c, o, n) -> {
			if (n) {
				mainWindow.getController().getPasteMenuItem().disableProperty().unbind();
				mainWindow.getController().getPasteMenuItem().disableProperty().set(!Clipboard.getSystemClipboard().hasString());
			}
		});

		Platform.runLater(codeArea::requestFocus);

		codeArea.textProperty().addListener((v, o, n) -> {
			firstLine.set(StringUtils.getFirstLine(n));
		});

		firstLine.addListener((v, o, n) -> {
			RunAfterAWhile.apply(toolBarController.getFormatLabel(), () -> Platform.runLater(() -> {
				var readers = ImportManager.getInstance().getReadersByText(n);
				if (readers.isEmpty())
					toolBarController.getFormatLabel().setText(null);
				else if (readers.size() == 1) {
					toolBarController.getFormatLabel().setText(readers.get(0).getName());
				} else {
					toolBarController.getFormatLabel().setText(readers.get(0).getName() + " * ");
				}
			}));
		});
	}

	public void setupMenuItems() {

		var mainController = mainWindow.getController();

		mainController.getMenuBar().getMenus().stream().filter(m -> m.getText().equals("Edit"))
				.forEach(m -> m.disableProperty().bind(new SimpleBooleanProperty(false)));

		displayTextViewPresenter.setupMenuItems();

		mainController.getOpenMenuItem().setOnAction(e -> openDialog(mainWindow, view));
		mainController.getOpenMenuItem().disableProperty().bind(view.emptyProperty().not());

		mainController.getSaveAsMenuItem().setOnAction(e -> saveDialog(mainWindow, view));
		mainController.getSaveAsMenuItem().disableProperty().bind(view.emptyProperty());

		mainController.getReplaceDataMenuItem().disableProperty().bind(TRUE);
		mainController.getAnalyzeGenomesMenuItem().disableProperty().bind(TRUE);
		mainController.getEditInputMenuItem().disableProperty().bind(TRUE);

		mainController.getOpenRecentMenu().disableProperty().bind(TRUE);

		mainController.getImportMultipleTreeFilesMenuItem().disableProperty().bind(TRUE);
	}

	public static void openDialog(MainWindow mainWindow, InputEditorView view) {
		final var previousDir = new File(ProgramProperties.get("InputDir", ""));
		final var fileChooser = new FileChooser();
		if (previousDir.isDirectory())
			fileChooser.setInitialDirectory(previousDir);
		fileChooser.setTitle("Open input file");
		fileChooser.getExtensionFilters().addAll(ImportManager.getInstance().getExtensionFilters());
		final var selectedFile = fileChooser.showOpenDialog(mainWindow.getStage());
		if (selectedFile != null) {
			if (selectedFile.getParentFile().isDirectory())
				ProgramProperties.put("InputDir", selectedFile.getParent());
			view.importFromFile(selectedFile.getPath());
		}
	}

	public static void saveDialog(MainWindow mainWindow, InputEditorView view) {
		var fileChooser = new FileChooser();
		fileChooser.setTitle("Save input text");
		var previousDir = new File(ProgramProperties.get("InputDir", ""));
		if (previousDir.isDirectory()) {
			fileChooser.setInitialDirectory(previousDir);
		}
		fileChooser.setInitialFileName(mainWindow.getFileName());
		var selectedFile = fileChooser.showSaveDialog(mainWindow.getStage());
		if (selectedFile != null) {
			view.saveToFile(selectedFile);
			jloda.util.ProgramProperties.put("InputDir", selectedFile.getPath());
		}
	}
}
