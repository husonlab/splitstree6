/*
 *  FileLoader.java Copyright (C) 2024 Daniel H. Huson
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

package splitstree6.io;

import javafx.application.Platform;
import jloda.fx.util.RecentFilesManager;
import jloda.fx.window.MainWindowManager;
import jloda.fx.window.NotificationManager;
import jloda.util.FileUtils;
import jloda.util.ProgramExecutorService;
import splitstree6.dialog.importdialog.ImportDialog;
import splitstree6.io.nexus.workflow.WorkflowNexusInput;
import splitstree6.io.readers.ImportManager;
import splitstree6.tabs.inputeditor.InputEditorTab;
import splitstree6.window.MainWindow;
import splitstree6.workflow.WorkflowSetup;

import java.io.File;
import java.io.StringReader;
import java.util.function.Consumer;

/**
 * opens a file by importing it without showing the import dialog
 * Daniel Huson, 10.2021
 */
public class FileLoader {
	/**
	 * open the named file
	 */
	public static void apply(MainWindow mainWindow, String fileName, Consumer<Throwable> exceptionHandler) {
		var editorTab = (InputEditorTab) mainWindow.getTabByClass(InputEditorTab.class);

		if (!(new File(fileName)).canRead())
			NotificationManager.showError("File not found or unreadable: " + fileName);
		else if (editorTab != null && editorTab.isEmpty()) {
			editorTab.importFromFile(fileName);
			mainWindow.setFileName(fileName);
			RecentFilesManager.getInstance().insertRecentFile(fileName);
		} else {
			if (editorTab != null) {
				NotificationManager.showInformation("Input editor is not empty, will open in new Window");
			}

			var newWindow = (MainWindow) MainWindowManager.getInstance().createAndShowWindow(mainWindow);
			if (WorkflowNexusInput.isApplicable(fileName)) {
				WorkflowNexusInput.open(newWindow, fileName, exceptionHandler, () -> RecentFilesManager.getInstance().insertRecentFile(fileName));
			} else {
				var importManager = ImportManager.getInstance();
				if (importManager.getReaders(fileName).size() == 1) { // unique input format
					newWindow.getPresenter().getSplitPanePresenter().ensureTreeViewIsOpen(false);
					WorkflowSetup.apply(fileName, newWindow.getWorkflow(), exceptionHandler, () -> RecentFilesManager.getInstance().insertRecentFile(fileName), importManager.getReaders(fileName).get(0).getToClass());
					newWindow.setFileName(fileName);
					newWindow.setDirty(true);
				} else {
					newWindow.getPresenter().getSplitPanePresenter().ensureTreeViewIsOpen(false);
					ImportDialog.show(newWindow, fileName);
				}
			}
			ProgramExecutorService.submit(200, () -> Platform.runLater(() -> newWindow.getStage().toFront()));
		}
	}

	/**
	 * open a SplitsTree6 document that is held in memory rather than on disk, e.g. a bundled example.
	 * The document is named after the given file name, but is not written to disk, not added to the recent files
	 * and not associated with a file, so that saving it asks for a location rather than writing anywhere
	 *
	 * @param fileName the name for the document; only its last component is used, the file need not exist
	 * @param text     the complete SplitsTree6 nexus document
	 */
	public static void applyText(MainWindow mainWindow, String fileName, String text, Consumer<Throwable> exceptionHandler) {
		var name = FileUtils.getFileNameWithoutPath(fileName);
		if (!WorkflowNexusInput.isApplicable(new StringReader(text))) {
			NotificationManager.showError("Not a SplitsTree6 document: " + name);
			return;
		}
		var editorTab = (InputEditorTab) mainWindow.getTabByClass(InputEditorTab.class);
		if (editorTab != null && editorTab.isEmpty()) {
			editorTab.importText(name, text);
		} else {
			if (editorTab != null) {
				NotificationManager.showInformation("Input editor is not empty, will open in new Window");
			}
			var newWindow = (MainWindow) MainWindowManager.getInstance().createAndShowWindow(mainWindow);
			WorkflowNexusInput.open(newWindow, name, () -> new StringReader(text), exceptionHandler, null);
			// not backed by a file: Save must behave like Save As, and the save dialog must not look for the directory of a file that does not exist
			newWindow.setHasSplitsTree6File(false);
			ProgramExecutorService.submit(200, () -> Platform.runLater(() -> newWindow.getStage().toFront()));
		}
	}
}


