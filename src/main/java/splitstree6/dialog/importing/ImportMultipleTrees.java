/*
 *  ImportMultipleTrees.java Copyright (C) 2024 Daniel H. Huson
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
import jloda.phylo.NewickIO;
import jloda.util.FileUtils;
import jloda.util.progress.ProgressSilent;
import splitstree6.data.TaxaBlock;
import splitstree6.data.TreesBlock;
import splitstree6.io.FileLoader;
import splitstree6.io.readers.trees.NewickReader;
import splitstree6.io.readers.trees.NexusReader;
import splitstree6.window.MainWindow;

import java.io.*;
import java.nio.file.Files;

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
		fileChooser.setTitle("Import Multiple Trees in Newick/Nexus Format");

		final var previousDir = new File(ProgramProperties.get("TreeImportDirectory", ""));
		if (previousDir.isDirectory()) {
			fileChooser.setInitialDirectory(previousDir.getParentFile());
		}
		var newickReader = new NewickReader();
		var nexusReader = new NexusReader();

		var newickExtensionFilter = newickReader.getExtensionFilter();
		var nexusExtensionFilter = nexusReader.getExtensionFilter();
		fileChooser.getExtensionFilters().addAll(TextFileFilter.getInstance(), newickExtensionFilter, nexusExtensionFilter, AllFileFilter.getInstance());
		fileChooser.setSelectedExtensionFilter(newickExtensionFilter);
		var files = fileChooser.showOpenMultipleDialog(mainWindow.getStage());
		if (files != null && !files.isEmpty()) {
			try {
				var tmpFile = FileUtils.getUniqueFileName(System.getProperty("user.dir"), "Untitled", "tmp");
				tmpFile.deleteOnExit();
				var first = true;
				try (var w = new BufferedWriter(new FileWriter(tmpFile))) {
					for (var file : files) {
						System.err.println(file.getName());
						if (first) {
							ProgramProperties.put("TreeImportDirectory", file.getParent());
							first = false;
						}
						if (newickReader.accepts(file.getPath())) {
							for (var line : Files.lines(file.toPath()).toList()) {
								w.write(line);
								w.newLine();
							}
						} else if (nexusReader.accepts(file.getPath())) {
							var taxaBlock = new TaxaBlock();
							var treesBlock = new TreesBlock();
							var newickIO = new NewickIO();
							nexusReader.read(new ProgressSilent(), file.getPath(), taxaBlock, treesBlock);
							for (var tree : treesBlock.getTrees()) {
								var format = new NewickIO.OutputFormat(tree.hasEdgeWeights(), false, tree.hasEdgeConfidences(), false, false);
								newickIO.write(tree, w, format);
								w.write(";\n");
							}
						} else
							throw new IOException("File not in Newick or Nexus format: " + file.getName());
					}
				}
				if (false) {
					try (var r = new BufferedReader(new FileReader(tmpFile.getPath()))) {
						while (r.ready()) {
							System.err.println(r.readLine());
						}
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
