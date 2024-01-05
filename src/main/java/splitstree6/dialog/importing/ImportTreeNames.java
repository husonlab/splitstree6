/*
 *  ImportTaxonDisplayLabels.java Copyright (C) 2024 Daniel H. Huson
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

package splitstree6.dialog.importing;

import javafx.application.Platform;
import javafx.stage.FileChooser;
import jloda.fx.util.ProgramProperties;
import jloda.fx.util.TextFileFilter;
import jloda.fx.window.NotificationManager;
import jloda.util.FileUtils;
import splitstree6.data.TreesBlock;
import splitstree6.window.MainWindow;
import splitstree6.workflow.AlgorithmNode;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;

/**
 * allows user to import names for trees from a file
 * Daniel Huson, 1.2023
 */
public class ImportTreeNames {
	/**
	 * show the import taxon display labels dialog and update
	 */
	public static void apply(MainWindow mainWindow) {
		if (!(mainWindow.getWorkflow().getInputDataBlock() instanceof TreesBlock inputTreesBlock)) {
			NotificationManager.showError("ImportTreeNames: Input data must be trees");
			return;
		}

		final var fileChooser = new FileChooser();
		fileChooser.setTitle("Import Tree Names");

		final var previousFile = new File(ProgramProperties.get("TreeNamesFile", ""));
		if (previousFile.isFile()) {
			fileChooser.setInitialDirectory(previousFile.getParentFile());
			fileChooser.setInitialFileName(previousFile.getName());
		}
		fileChooser.setSelectedExtensionFilter(TextFileFilter.getInstance());
		fileChooser.getExtensionFilters().addAll(TextFileFilter.getInstance(), new FileChooser.ExtensionFilter("TXT", "*.txt", "*.txt.gz"));
		var file = fileChooser.showOpenDialog(mainWindow.getStage());
		if (file != null) {
			try {
				try (var reader = new BufferedReader(FileUtils.getReaderPossiblyZIPorGZIP(file.getPath()))) {
					var treeNames = reader.lines().map(String::trim).filter(s -> !s.isBlank() && !s.startsWith("#")).toList();
					if (treeNames.size() != inputTreesBlock.size()) {
						NotificationManager.showError("Number of tree names in file (%,d) must match number of trees (%,d)".formatted(treeNames.size(), inputTreesBlock.size()));
						return;
					}
					var t = 1;
					for (var name : treeNames) {
						inputTreesBlock.getTree(t++).setName(name);
					}
					Platform.runLater(() ->
					{
						mainWindow.getWorkflow().getInputDataNode().getChildren().forEach(v -> {
							if (v instanceof AlgorithmNode a)
								a.restart();
						});
						mainWindow.getTextTabsManager().updateDataNodeTabIfShowing(mainWindow.getWorkflow().getInputDataNode());
					});
					ProgramProperties.put("TreeNamesFile", file.getPath());
				}
			} catch (IOException ex) {
				NotificationManager.showError("Import failed: " + ex);
			}
		}
	}
}
