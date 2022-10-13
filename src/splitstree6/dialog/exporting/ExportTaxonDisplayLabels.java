/*
 *  ExportTaxonDisplayLabels.java Copyright (C) 2022 Daniel H. Huson
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

package splitstree6.dialog.exporting;

import javafx.stage.FileChooser;
import jloda.fx.util.ProgramProperties;
import jloda.fx.util.TextFileFilter;
import jloda.fx.window.NotificationManager;
import jloda.util.FileUtils;
import splitstree6.window.MainWindow;

import java.io.File;
import java.io.IOException;

/**
 * export taxon display labels
 * Daniel Huson, 4.2022
 */
public class ExportTaxonDisplayLabels {
	public static void apply(MainWindow mainWindow) {
		final var fileChooser = new FileChooser();
		fileChooser.setTitle("Export Taxa Display Labels");

		final var previousFile = new File(ProgramProperties.get("TaxaDisplayLabelsFile", ""));
		if (previousFile.isFile()) {
			fileChooser.setInitialDirectory(previousFile.getParentFile());
			fileChooser.setInitialFileName(mainWindow.getName() + "-labels.txt");
		}
		fileChooser.setSelectedExtensionFilter(TextFileFilter.getInstance());
		fileChooser.getExtensionFilters().addAll(TextFileFilter.getInstance(), new FileChooser.ExtensionFilter("TSV", "*.tsv", "*.tsv.gz"));
		var file = fileChooser.showSaveDialog(mainWindow.getStage());
		if (file != null) {
			try (var w = FileUtils.getOutputWriterPossiblyZIPorGZIP(file.getPath())) {
				for (var taxon : mainWindow.getWorkflow().getWorkingTaxaBlock().getTaxa()) {
					w.write("%s\t%s%n".formatted(taxon.getName(), taxon.getDisplayLabelOrName().replaceAll("\t", " ")));
				}
			} catch (IOException ex) {
				NotificationManager.showError("Export failed: " + ex);
			}
		}
	}
}
