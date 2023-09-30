/*
 *  ImportMultipleTrees.java Copyright (C) 2023 Daniel H. Huson
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

import javafx.stage.FileChooser;
import jloda.fx.util.AllFileFilter;
import jloda.fx.util.ProgramProperties;
import jloda.fx.util.TextFileFilter;
import jloda.fx.window.NotificationManager;
import jloda.util.FileUtils;
import splitstree6.io.FileLoader;
import splitstree6.io.readers.trees.NewickReader;
import splitstree6.window.MainWindow;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.util.stream.Collectors;

/**
 * import multiple trees dialog
 * Daniel Huson, 4.2022
 */
public class ImportMultipleTrees {
	/**
	 * show open file dialog and load multiple tree files
	 *
	 * @param mainWindow the main window
	 */
	public static void apply(MainWindow mainWindow) {
		final var fileChooser = new FileChooser();
		fileChooser.setTitle("Import Multiple Trees in Newick Format");

		final var previousDir = new File(ProgramProperties.get("TreeImportDirectory", ""));
		if (previousDir.isDirectory()) {
			fileChooser.setInitialDirectory(previousDir.getParentFile());
		}
		var newickExtensionFilter = new NewickReader().getExtensionFilter();
		fileChooser.getExtensionFilters().addAll(TextFileFilter.getInstance(), newickExtensionFilter, AllFileFilter.getInstance());
		fileChooser.setSelectedExtensionFilter(newickExtensionFilter);
		var files = fileChooser.showOpenMultipleDialog(mainWindow.getStage());
		if (files != null && files.size() > 0) {
			try {
				var tmpFile = FileUtils.getUniqueFileName(System.getProperty("user.dir"), "Untitled", "tmp");
				tmpFile.deleteOnExit();
				var newickReader = new NewickReader();
				var first = true;
				try (var w = new BufferedWriter(new FileWriter(tmpFile))) {
					for (var file : files) {
						if (first) {
							ProgramProperties.put("TreeImportDirectory", file.getParent());
							first = false;
						}

						if (newickReader.accepts(file.getPath())) {
							for (var line : Files.lines(file.toPath()).collect(Collectors.toList())) {
								w.write(line);
								w.newLine();
							}
						} else
							throw new IOException("File not in Newick format: " + file);
					}
				}
				FileLoader.apply(false, mainWindow, tmpFile.getPath(), ex -> {
					NotificationManager.showError("Import trees failed: " + ex);
				});
			} catch (IOException ex) {
				NotificationManager.showError("Import trees failed: " + ex);
			}
		}
	}
}
