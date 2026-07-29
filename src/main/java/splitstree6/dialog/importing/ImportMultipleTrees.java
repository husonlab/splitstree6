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
import jloda.phylo.CommentData;
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
import java.util.HashSet;
import java.util.Set;

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
				// Write the concatenated trees into the system temp directory, NOT user.dir: when the app is
				// launched by double-clicking (macOS .app), the working directory is "/" (read-only), so creating
				// "/Untitled.tmp" fails with "Read-only file system". java.io.tmpdir is always writable.
				var tmpFile = FileUtils.getUniqueFileName(System.getProperty("java.io.tmpdir"), "Untitled", "tmp");
				tmpFile.deleteOnExit();
				// Read the trees from every selected file and give each a name derived from its source file, so it is
				// clear inside the app which file a tree came from: <basename>-<i>, with i restarting at 1 per file
				// (basename = file name without path or extension). Clashes (e.g. two files with the same base name
				// but different extensions) are disambiguated with a "(k)" suffix. The name travels into the merged
				// file as an [&&NHX:GN=...] comment (CommentData.createDataNodeSupplier), which NewickReader reads back.
				var newickIO = new NewickIO();
				newickIO.setNewickNodeCommentSupplier(CommentData.createDataNodeSupplier());
				var usedNames = new HashSet<String>();
				var first = true;
				try (var w = new BufferedWriter(new FileWriter(tmpFile))) {
					for (var file : files) {
						if (first) {
							ProgramProperties.put("TreeImportDirectory", file.getParent());
							first = false;
						}
						var taxaBlock = new TaxaBlock();
						var treesBlock = new TreesBlock();
						if (newickReader.accepts(file.getPath())) {
							newickReader.read(new ProgressSilent(), file.getPath(), taxaBlock, treesBlock);
						} else if (nexusReader.accepts(file.getPath())) {
							nexusReader.read(new ProgressSilent(), file.getPath(), taxaBlock, treesBlock);
						} else
							throw new IOException("File not in Newick or Nexus format: " + file.getName());

						var base = FileUtils.getFileNameWithoutPathOrSuffix(file.getName());
						var i = 0;
						for (var tree : treesBlock.getTrees()) {
							tree.setName(uniqueName(base + "-" + (++i), usedNames));
							var format = new NewickIO.OutputFormat(tree.hasEdgeWeights(), false, tree.hasEdgeConfidences(), tree.hasEdgeProbabilities(), false);
							newickIO.write(tree, w, format);
							w.write(";\n");
						}
					}
				}
				if (false) {
					try (var r = new BufferedReader(new FileReader(tmpFile.getPath()))) {
						while (r.ready()) {
							System.err.println(r.readLine());
						}
					}
				}
				FileLoader.apply(mainWindow, tmpFile.getPath(), ex -> {
					NotificationManager.showError("Import trees failed: " + ex);
				});
			} catch (IOException ex) {
				NotificationManager.showError("Import trees failed: " + ex);

			}
		}
	}

	/**
	 * returns name if it is not yet in used, otherwise appends "(2)", "(3)", ... until unique; records the result in used
	 */
	private static String uniqueName(String name, Set<String> used) {
		var unique = name;
		var k = 1;
		while (!used.add(unique))
			unique = name + "(" + (++k) + ")";
		return unique;
	}
}
