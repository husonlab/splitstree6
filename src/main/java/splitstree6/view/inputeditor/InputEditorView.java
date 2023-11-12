/*
 * InputEditorView.java Copyright (C) 2023 Daniel H. Huson
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

import jloda.fx.util.ExtendedFXMLLoader;
import jloda.fx.util.RecentFilesManager;
import jloda.fx.window.NotificationManager;
import jloda.util.FileLineIterator;
import jloda.util.FileUtils;
import jloda.util.IOExceptionWithLineNumber;
import jloda.util.StringUtils;
import splitstree6.io.nexus.workflow.WorkflowNexusInput;
import splitstree6.tabs.viewtab.ViewTab;
import splitstree6.view.displaytext.DisplayTextView;
import splitstree6.view.utils.IView;
import splitstree6.window.MainWindow;
import splitstree6.workflow.WorkflowSetup;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.util.function.Consumer;

/**
 * input editor view
 */
public class InputEditorView extends DisplayTextView implements IView {
	public static final String NAME = "Input Editor";
	private final MainWindow mainWindow;
	private final InputEditorViewController inputEditorViewController;
	private final InputEditorViewPresenter inputEditorViewPresenter;
	private File tmpFile;

	private final ViewTab viewTab;

	/**
	 * constructor
	 */
	public InputEditorView(MainWindow mainWindow, ViewTab viewTab) {
		super(mainWindow, NAME, true);
		this.mainWindow = mainWindow;
		this.viewTab = viewTab;

		var loader = new ExtendedFXMLLoader<InputEditorViewController>(this.getClass());
		inputEditorViewController = loader.getController();
		inputEditorViewPresenter = new InputEditorViewPresenter(mainWindow, super.getPresenter(), this);
	}

	public InputEditorViewController getInputEditorViewController() {
		return inputEditorViewController;
	}

	public InputEditorViewPresenter getInputEditorViewPresenter() {
		return inputEditorViewPresenter;
	}

	@Override
	public void setupMenuItems() {
		super.setupMenuItems();
	}

	/**
	 * go to given line and given col
	 *
	 * @param col if col<=1 or col>line length, will select the whole line, else selects line starting at given col
	 */
	public void gotoLine(long lineNumber, int col) {
		if (col < 0)
			col = 0;
		else if (col > 0)
			col--; // because col is 1-based

		lineNumber = Math.max(1, lineNumber);
		final var text = getController().getCodeArea().getText();
		var start = 0;
		for (var i = 1; i < lineNumber; i++) {
			start = text.indexOf('\n', start + 1);
			if (start == -1) {
				System.err.println("No such line number: " + lineNumber);
				return;
			}
		}
		start++;
		if (start < text.length()) {
			var end = text.indexOf('\n', start);
			if (end == -1)
				end = text.length();
			if (start + col < end)
				start = start + col;
			getController().getCodeArea().selectRange(start, end);
			getController().getCodeArea().requestFollowCaret();
			getController().getCodeArea().requestFocus();
		}
	}

	public void importFromFile(String fileName) {
		try (var it = new FileLineIterator(fileName)) {
			replaceText(StringUtils.toString(it.lines(), "\n"));
			var name = FileUtils.getFileBaseName(fileName);
			mainWindow.setFileName(name);
			RecentFilesManager.getInstance().insertRecentFile(fileName);
		} catch (IOException ex) {
			NotificationManager.showError("Import failed: " + ex.getMessage());
		}
	}

	public void saveToFile(File file) {
		try {
			Files.writeString(file.toPath(), getController().getCodeArea().getText());
			mainWindow.setFileName(file.getName());
		} catch (IOException ex) {
			NotificationManager.showError("Save failed: " + ex.getMessage());
		}
	}

	public void parseAndLoad() {
		try {
			var name = mainWindow.getFileName();

			if (tmpFile == null) {
				tmpFile = File.createTempFile("Untitled", ".tmp");
				tmpFile.deleteOnExit();
			}
			try (BufferedWriter w = new BufferedWriter(new FileWriter(tmpFile))) {
				w.write(getController().getCodeArea().getText());
			}

			final Consumer<Throwable> failedHandler = ex -> {
				mainWindow.getWorkflow().clear();
				if (ex instanceof IOExceptionWithLineNumber exceptionWithLineNumber) {
					viewTab.getTabPane().getSelectionModel().select(viewTab);
					getController().getCodeArea().requestFocus();
					gotoLine(exceptionWithLineNumber.getLineNumber(), 0);
				}
				//NotificationManager.showError("Parse failed: " + ex.getMessage());
				mainWindow.setFileName(name);

			};
			final Runnable runOnSuccess = () -> {
				mainWindow.getPresenter().getSplitPanePresenter().ensureTreeViewIsOpen(false);
				mainWindow.setFileName(name);
				mainWindow.setDirty(true);
			};
			if (WorkflowNexusInput.isApplicable(tmpFile.getPath())) {
				WorkflowNexusInput.open(mainWindow, tmpFile.getPath(), failedHandler, runOnSuccess);
			} else
				WorkflowSetup.apply(tmpFile.getPath(), mainWindow.getWorkflow(), failedHandler, runOnSuccess, null);

		} catch (Exception ex) {
			NotificationManager.showError("Enter data failed: " + ex.getClass().getSimpleName() + ": " + ex.getMessage());
		}
	}
}

