/*
 *  ImportTaxonTraits.java Copyright (C) 2024 Daniel H. Huson
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

import javafx.geometry.Point2D;
import javafx.stage.FileChooser;
import jloda.fx.util.TextFileFilter;
import jloda.fx.window.NotificationManager;
import jloda.util.*;
import splitstree6.data.TraitsBlock;
import splitstree6.window.MainWindow;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;

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
			fileChooser.setInitialFileName(FileUtils.getFileNameWithoutPathOrSuffix(previousFile.getName()));
		}
		fileChooser.setSelectedExtensionFilter(TextFileFilter.getInstance());
		fileChooser.getExtensionFilters().addAll(TextFileFilter.getInstance(), new FileChooser.ExtensionFilter("TSV", "*.tsv", "*.tsv.gz"));
		var file = fileChooser.showOpenDialog(mainWindow.getStage());
		if (file != null) {
			try {
				if (!isTraitsFile(file))
					throw new IOException("Traits import file must start with 'traits' keyword");
				ProgramProperties.put("TaxonTraitsFile", file.getPath());
				try (var it = new FileLineIterator(file)) {
					String[] traitNames = null;
					var taxonValuesMap = new HashMap<String, String[]>();
					var traitLocations = new HashMap<String, Point2D>();

					var seen = 0;

					for (var line : it.lines()) {
						line = line.trim().replaceAll("'", "_");
						if (!line.startsWith("#")) {
							var tokens = StringUtils.split(line, '\t');
							if (tokens.length > 0) {
								if (seen == 0) {
									traitNames = tokens;
									if (false)
										System.err.println("Traits: " + StringUtils.toString(traitNames, 1, traitNames.length, ", "));
									seen++;
								} else {
									if (seen == 1 && line.startsWith("Coordinates")) {
										if (tokens.length != traitNames.length) {
											throw new IOExceptionWithLineNumber(it.getLineNumber(), "Expected %,d pairs of coordinates 'lat,long', got: %,d".formatted(traitNames.length, tokens.length));
										}
										for (int t = 1; t < traitNames.length; t++) {
											var values = StringUtils.split(tokens[t], ',');
											if (values.length != 2 || !NumberUtils.isDouble(values[0]) || !NumberUtils.isDouble(values[1]))
												throw new IOExceptionWithLineNumber(it.getLineNumber(), "Expected pair of coordinates, got: " + tokens[t]);
											traitLocations.put(traitNames[t], new Point2D(NumberUtils.parseDouble(values[0]), NumberUtils.parseDouble(values[1])));
										}
										seen++;
									} else {
										if (tokens.length == 3 && Arrays.stream(traitNames).anyMatch(n -> n.equals(tokens[1])) && NumberUtils.isDouble(tokens[2])) {
											var length = traitNames.length;
											var values = taxonValuesMap.computeIfAbsent(tokens[0],
													k -> {
														var array = new String[length];
														Arrays.fill(array, "0");
														return array;
													});
											for (var t = 0; t < traitNames.length; t++) {
												if (traitNames[t].equals(tokens[1]))
													values[t] = String.valueOf(NumberUtils.parseDouble(values[t]) + NumberUtils.parseDouble(tokens[2]));
											}
										} else {
											if (tokens.length != traitNames.length)
												throw new IOExceptionWithLineNumber(it.getLineNumber(), "Expected %,d values, got: %,d".formatted(traitNames.length, tokens.length));
											taxonValuesMap.put(tokens[0], tokens);
										}
										seen++;
									}
								}
							}
						}
					}

					if (traitNames != null && traitNames.length > 1) {
						var inputTaxa = mainWindow.getWorkflow().getInputTaxaBlock();
						for (var label : inputTaxa.getLabels()) {
							if (!taxonValuesMap.containsKey(label))
								System.err.println("Taxon not found in file: " + label);
						}

						if (!taxonValuesMap.keySet().containsAll(inputTaxa.getLabels())) {
							throw new IOException("Imported taxon labels do not include all existing labels");
						}
						var traitsBlock = new TraitsBlock();
						traitsBlock.setDimensions(inputTaxa.getNtax(), traitNames.length - 1);
						for (var tr = 1; tr < traitNames.length; tr++) {
							var name = traitNames[tr];
							traitsBlock.setTraitLabel(tr, name);
							if (traitLocations.containsKey(name)) {
								var latlong = traitLocations.get(name);
								traitsBlock.setTraitLatitude(tr, (float) latlong.getX());
								traitsBlock.setTraitLongitude(tr, (float) latlong.getY());
							}
						}
						var traitCount = new int[traitNames.length];
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

	public static boolean isTraitsFile(File file) {
		var first = FileUtils.getFirstLineFromFile(file, "#", 100);
		return first != null && first.toLowerCase().startsWith("traits");
	}
}
