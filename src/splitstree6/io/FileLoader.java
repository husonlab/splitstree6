/*
 *  FileLoader.java Copyright (C) 2021 Daniel H. Huson
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

import jloda.fx.util.RecentFilesManager;
import jloda.fx.window.MainWindowManager;
import jloda.fx.window.NotificationManager;
import splitstree6.io.nexus.workflow.WorkflowNexusInput;
import splitstree6.io.readers.ImportManager;
import splitstree6.tabs.inputeditor.InputEditorTab;
import splitstree6.window.MainWindow;
import splitstree6.workflow.WorkflowSetup;

import java.io.File;
import java.util.function.Consumer;

/**
 * opens a file by importing it without showing the import dialog
 * Daniel Huson, 10.2021
 */
public class FileLoader {
	/**
	 * open the named file
	 */
	public static void apply(boolean reload, MainWindow mainWindow, String fileName, Consumer<Throwable> exceptionHandler) {
		var editorTab = (InputEditorTab) mainWindow.getTabByClass(InputEditorTab.class);

		if (!(new File(fileName)).canRead())
			NotificationManager.showError("File not found or unreadable: " + fileName);
		else if (editorTab != null && editorTab.getText().isBlank()) {
			editorTab.importFromFile(fileName);
			mainWindow.setFileName(fileName);
			RecentFilesManager.getInstance().insertRecentFile(fileName);
			/* todo: should use source node
			var sourceNode = mainWindow.getWorkflow().getSourceNode();
			if (sourceNode != null) {
				var sourceDataBlock = sourceNode.getDataBlock();
				if (sourceDataBlock.isUsingInputEditor()) {
					inputEditor.importFromFile(fileName);
				}
			}
			 */
		} else {
			if (editorTab != null) {
				NotificationManager.showWarning("Input editor is not empty, will open in new Window");
			}

			var newWindow = (MainWindow) MainWindowManager.getInstance().createAndShowWindow(mainWindow);
			if (WorkflowNexusInput.isApplicable(fileName)) {
				WorkflowNexusInput.open(newWindow, fileName);
				RecentFilesManager.getInstance().insertRecentFile(fileName);
			} else {
				var importManager = ImportManager.getInstance();
				if (importManager.getReaders(fileName).size() == 1) { // unique input format
					newWindow.getPresenter().getSplitPanePresenter().ensureTreeViewIsOpen(false);
					WorkflowSetup.apply(fileName, newWindow.getWorkflow(), exceptionHandler);
					newWindow.setFileName(fileName);
					RecentFilesManager.getInstance().insertRecentFile(fileName);
					newWindow.setDirty(true);
				} else {
					// ImportDialog.show(mainWindow, fileName);
					System.err.println("Import dialog: not implemented");
				}
			}
		}
	}
}


