/*
 *  ImportTaxonDisplayLabels.java Copyright (C) 2023 Daniel H. Huson
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
import jloda.util.*;
import splitstree6.data.TraitsBlock;
import splitstree6.window.MainWindow;
import splitstree6.workflow.Workflow;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * allows user to import traits
 * Daniel Huson, 4.2022
 */
public class ImportTaxonTraits {
	/**
	 * show the import traits dialog and update
	 */
	public static void apply(MainWindow mainWindow) {
		final var fileChooser = new FileChooser();
		fileChooser.setTitle("Import Taxon Traits");

		final var previousFile = new File(ProgramProperties.get("TaxonTraitsFile", ""));
		if (previousFile.isFile()) {
			fileChooser.setInitialDirectory(previousFile.getParentFile());
			fileChooser.setInitialFileName(previousFile.getName());
		}
		fileChooser.setSelectedExtensionFilter(TextFileFilter.getInstance());
		fileChooser.getExtensionFilters().addAll(TextFileFilter.getInstance(), new FileChooser.ExtensionFilter("TSV", "*.tsv", "*.tsv.gz"));
		var file = fileChooser.showOpenDialog(mainWindow.getStage());
		if (file != null) {
			try {
				if (!isTraitsFile(file))
					throw new IOException("Traits import file must start with 'traits' keyword");
				try (var it = new FileLineIterator(file)) {
					String[] traitsLine = null;
					var taxonValuesMap = new HashMap<String, String[]>();

					for (var line : it.lines()) {
						line = line.trim();
						if (!line.startsWith("#")) {
							var tokens = StringUtils.split(line, '\t');
							if (tokens.length > 0) {
								if (traitsLine == null) {
									traitsLine = tokens;
									System.err.println("Traits: " + StringUtils.toString(traitsLine, 1, traitsLine.length, ", "));
								} else {
									if (tokens.length != traitsLine.length)
										throw new IOExceptionWithLineNumber(it.getLineNumber(), "Expected %,d values, got: %,d".formatted(traitsLine.length, tokens.length));
									for (var t = 1; t < tokens.length; t++) {
										taxonValuesMap.put(tokens[0], tokens);
									}
								}
							}
						}
					}
					ProgramProperties.put("TaxonTraitsFile", file.getPath());

					if (traitsLine != null && traitsLine.length > 1) {
						var inputTaxa = mainWindow.getWorkflow().getInputTaxaBlock();
						if (!taxonValuesMap.keySet().containsAll(inputTaxa.getLabels())) {
							throw new IOException("Imported taxon labels do not include all existing labels");
						}
						var traitsBlock = new TraitsBlock();
						traitsBlock.setDimensions(inputTaxa.getNtax(), traitsLine.length - 1);
						for (var tr = 1; tr < traitsLine.length; tr++) {
							traitsBlock.setTraitLabel(tr, traitsLine[tr]);
						}
						var traitCount = new int[traitsLine.length];
						for (var t = 1; t <= inputTaxa.getNtax(); t++) {
							var valuesLine = taxonValuesMap.get(inputTaxa.getLabel(t));
							for (var tr = 1; tr < valuesLine.length; tr++) {
								var label = valuesLine[tr];
								if (NumberUtils.isDouble(label))
									traitsBlock.setTraitValue(t, tr, NumberUtils.parseDouble(label));
								else {
									traitsBlock.setTraitValueLabel(t, tr, label);
									traitsBlock.setTraitValue(t, tr, ++traitCount[tr]);
								}
							}
						}
						inputTaxa.setTraitsBlock(traitsBlock);
						mainWindow.getWorkflow().restart(mainWindow.getWorkflow().getInputTaxaFilterNode());
					}
				}
			} catch (IOException ex) {
				NotificationManager.showError("Import failed: " + ex);
			}
		}
	}

	/**
	 * update the given display labels
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

	public static boolean isTraitsFile(File file) {
		var first = FileUtils.getFirstLineFromFile(file, "#", 100);
		return first != null && first.toLowerCase().startsWith("traits");
	}
}
