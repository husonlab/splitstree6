/*
 * SaveDialog.java Copyright (C) 2023 Daniel H. Huson
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

package splitstree6.dialog;

import javafx.stage.FileChooser;
import jloda.fx.util.ProgramProperties;
import jloda.fx.util.RecentFilesManager;
import jloda.fx.window.NotificationManager;
import jloda.util.FileUtils;
import splitstree6.io.nexus.workflow.WorkflowNexusOutput;
import splitstree6.window.MainWindow;

import java.io.File;
import java.io.IOException;

import static splitstree6.io.nexus.workflow.WorkflowNexusInput.WORKFLOW_FILE_SUFFIX;

public class SaveDialog {
	/**
     * save dialog
     *
     * @param mainWindow the main window
     */
    public static void showSaveDialog(MainWindow mainWindow, boolean asWorkflowOnly) {
        final var fileChooser = new FileChooser();
        fileChooser.setTitle(asWorkflowOnly ? "Export SplitsTree6 Workflow" : "Save SplitsTree6 file");

		final var previousDir = new File(ProgramProperties.get("SaveDir", ""));
        if (previousDir.isDirectory()) {
            fileChooser.setInitialDirectory(previousDir);
        } else
            fileChooser.setInitialDirectory((new File(mainWindow.getFileName()).getParentFile()));

        if (!asWorkflowOnly) {
            fileChooser.getExtensionFilters().addAll(new FileChooser.ExtensionFilter("SplitsTree6 Files", "*.stree6", "*.nxs", "*.nex"));
            fileChooser.setInitialFileName(FileUtils.getFileNameWithoutPath(FileUtils.replaceFileSuffix(mainWindow.getFileName(), ".stree6")));
        } else {
			fileChooser.getExtensionFilters().addAll(new FileChooser.ExtensionFilter("SplitsTree6 Workflow Files", "*" + WORKFLOW_FILE_SUFFIX));
			fileChooser.setInitialFileName(FileUtils.getFileNameWithoutPath(FileUtils.replaceFileSuffix(mainWindow.getFileName(), WORKFLOW_FILE_SUFFIX)));
		}

		var selectedFile = fileChooser.showSaveDialog(mainWindow.getStage());
        if (selectedFile != null) {
            save(mainWindow, asWorkflowOnly, selectedFile);
        }
    }

	public static boolean save(MainWindow mainWindow, boolean asWorkflowOnly, File file) {
		var result = false;
		if (file.getParentFile().isDirectory())
			ProgramProperties.put("SaveDir", file.getParent());

		try {
			new WorkflowNexusOutput().save(mainWindow.getWorkflow(), file.getPath(), asWorkflowOnly);
			if (!asWorkflowOnly) {
				mainWindow.setFileName(file.getPath());
				mainWindow.setDirty(false);
				mainWindow.setHasSplitsTree6File(true);
			}
			if (!mainWindow.getFileName().endsWith(".tmp"))
				RecentFilesManager.getInstance().insertRecentFile(mainWindow.getFileName());
			result = true;
		} catch (IOException ex) {
			NotificationManager.showError("Save FAILED: " + ex);
		}
		return result;
	}
}
