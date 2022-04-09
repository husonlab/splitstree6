/*
 *  ImportTaxonDisplayLabels.java Copyright (C) 2022 Daniel H. Huson
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
import jloda.fx.util.TextFileFilter;
import jloda.fx.window.NotificationManager;
import jloda.util.FileUtils;
import jloda.util.ProgramProperties;
import jloda.util.StringUtils;
import splitstree6.window.MainWindow;
import splitstree6.workflow.Workflow;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static splitstree6.dialog.importing.ImportTraits.isTraitsFile;

/**
 * allows user to import taxon display labels from a file
 * Daniel Huson, 4.2022
 */
public class ImportTaxonDisplayLabels {
	/**
	 * show the import taxon display labels dialog and apply
	 */
	public static void apply(MainWindow mainWindow) {
		final var fileChooser = new FileChooser();
		fileChooser.setTitle("Import Taxa Display Labels");

		final var previousFile = new File(ProgramProperties.get("TaxaDisplayLabelsFile", ""));
		if (previousFile.isFile()) {
			fileChooser.setInitialDirectory(previousFile.getParentFile());
			fileChooser.setInitialFileName(previousFile.getName());
		}
		fileChooser.setSelectedExtensionFilter(TextFileFilter.getInstance());
		fileChooser.getExtensionFilters().addAll(TextFileFilter.getInstance(), new FileChooser.ExtensionFilter("TSV", "*.tsv", "*.tsv.gz"));
		var file = fileChooser.showOpenDialog(mainWindow.getStage());
		if (file != null) {
			try {
				if (isTraitsFile(file))
					throw new IOException("This looks like a traits import file, starts with 'traits' keyword");

				try (var reader = new BufferedReader(FileUtils.getReaderPossiblyZIPorGZIP(file.getPath()))) {
					var nameDisplayLabelMap = new HashMap<String, String>();
					reader.lines().map(s -> StringUtils.split(s, '\t'))
							.forEach(s -> {
								if (s.length == 1)
									nameDisplayLabelMap.put(s[0], s[0]);
								else if (s.length >= 2)
									nameDisplayLabelMap.put(s[0], s[1]);
							});
					apply(mainWindow.getWorkflow(), nameDisplayLabelMap);
					ProgramProperties.put("TaxaDisplayLabelsFile", file.getPath());
				}
			} catch (IOException ex) {
				NotificationManager.showError("Import failed: " + ex);
			}
		}
	}

	/**
	 * apply the given display labels
	 *
	 * @param workflow            the workflow
	 * @param nameDisplayLabelMap mapping of taxon names to the desired display labels
	 */
	public static void apply(Workflow workflow, Map<String, String> nameDisplayLabelMap) {
		var taxonBlock = workflow.getWorkingTaxaBlock();
		if (taxonBlock != null) {
			var count = 0;
			for (var name : nameDisplayLabelMap.keySet()) {
				var taxon = taxonBlock.get(name);
				if (taxon != null) {
					var displayLabel = nameDisplayLabelMap.get(name);
					if (!displayLabel.equals(taxon.getDisplayLabelOrName())) {
						taxon.setDisplayLabel(nameDisplayLabelMap.get(name));
						count++;
					}
				}
			}
			NotificationManager.showInformation("Applied %,d display labels".formatted(count));
		}
	}
}
