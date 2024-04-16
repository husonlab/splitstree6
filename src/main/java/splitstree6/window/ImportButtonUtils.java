/*
 *  ImportButtonUtils.java Copyright (C) 2024 Daniel H. Huson
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

package splitstree6.window;

import javafx.application.Platform;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.scene.control.Button;
import javafx.scene.input.TransferMode;
import jloda.fx.util.ClipboardUtils;
import jloda.fx.window.MainWindowManager;
import jloda.util.FileUtils;
import jloda.util.StringUtils;
import splitstree6.tabs.inputeditor.InputEditorTab;
import splitstree6.view.displaytext.DisplayTextView;
import splitstree6.view.inputeditor.InputEditorView;

import java.io.IOException;

import static jloda.fx.util.ClipboardUtils.isTextFile;

/**
 * setup the import button
 * Daniel Huson, 4.2024
 */
public class ImportButtonUtils {
	public static void setup(Button importButton) {
		var dragOver = new SimpleBooleanProperty(false);

		importButton.disableProperty().bind(dragOver.not().and(ClipboardUtils.hasStringProperty().not()).and(ClipboardUtils.hasFilesProperty().not()));
		importButton.setOnAction(e -> openString(ClipboardUtils.getTextFilesContentOrString()));

		importButton.setOnDragOver(e -> {
			var db = e.getDragboard();
			if (e.getGestureSource() != importButton && db.getString() != null || db.hasFiles()) {
				e.acceptTransferModes(TransferMode.COPY_OR_MOVE);
				dragOver.set(true);
			}
			e.consume();
		});

		// Set up event handler for when files are dropped onto the button
		importButton.setOnDragDropped(e -> {
			var db = e.getDragboard();
			boolean success = false;
			if (db.getString() != null) {
				openString(db.getString());
				success = true;
			} else if (db.hasFiles()) {
				var buf = new StringBuilder();
				for (var file : db.getFiles()) {
					if (FileUtils.fileExistsAndIsNonEmpty(file) && isTextFile(file)) {
						try {
							buf.append(StringUtils.toString(FileUtils.getLinesFromFile(file.getPath()), "\n"));
						} catch (IOException ignored) {
						}
					}
				}
				openString(buf.toString());
				success = true;
			}
			e.setDropCompleted(success);
			e.consume();
		});
		importButton.setOnDragExited(event -> {
			dragOver.set(false);
			event.consume();
		});
	}

	public static void openString(String string) {
		if (string != null && !string.isBlank()) {
			var mainWindow = (MainWindow) MainWindowManager.getInstance().createAndShowWindow(true);
			Platform.runLater(() -> {
				mainWindow.getController().getEditInputMenuItem().fire();
				Platform.runLater(() -> {
					var inputEditorTab = (InputEditorTab) mainWindow.getTabByClass(InputEditorTab.class);
					if (inputEditorTab != null) {
						((DisplayTextView) inputEditorTab.getView()).getController().getCodeArea().replaceText(string);
						Platform.runLater(() -> ((InputEditorView) inputEditorTab.getView()).parseAndLoad());
					}
				});
			});
		}
	}
}
