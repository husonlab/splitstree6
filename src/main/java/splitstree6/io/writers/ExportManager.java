/*
 * ExportManager.java Copyright (C) 2023 Daniel H. Huson
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

package splitstree6.io.writers;

import jloda.fx.util.RecentFilesManager;
import jloda.fx.window.NotificationManager;
import jloda.util.FileUtils;
import splitstree6.data.TaxaBlock;
import splitstree6.io.utils.DataBlockWriter;
import splitstree6.io.utils.ReaderWriterBase;
import splitstree6.workflow.DataBlock;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

/**
 * manages export of data in different formats
 * Daniel Huson, 11.21
 */
public class ExportManager {
	private final ArrayList<DataBlockWriter> exporters = new ArrayList<>();

	private static ExportManager instance;

	private ExportManager() {
		exporters.add(new splitstree6.io.writers.characters.ClustalWriter());
		exporters.add(new splitstree6.io.writers.characters.FastAWriter());
		exporters.add(new splitstree6.io.writers.characters.NexusWriter());
		exporters.add(new splitstree6.io.writers.characters.PhylipWriter());
		exporters.add(new splitstree6.io.writers.characters.PlainTextWriter());
		exporters.add(new splitstree6.io.writers.distances.NexusWriter());
		exporters.add(new splitstree6.io.writers.distances.PhylipWriter());
		exporters.add(new splitstree6.io.writers.distances.PlainTextWriter());
		exporters.add(new splitstree6.io.writers.genomes.NexusWriter());
		exporters.add(new splitstree6.io.writers.network.NexusWriter());
		exporters.add(new splitstree6.io.writers.network.PlainTextWriter());
		exporters.add(new splitstree6.io.writers.report.NexusWriter());
		exporters.add(new splitstree6.io.writers.report.PlainTextWriter());
		exporters.add(new splitstree6.io.writers.sets.NexusWriter());
		exporters.add(new splitstree6.io.writers.sets.PlainTextWriter());
		exporters.add(new splitstree6.io.writers.splits.FastAWriter());
		exporters.add(new splitstree6.io.writers.splits.NewickWriter());
		exporters.add(new splitstree6.io.writers.splits.NexusWriter());
		exporters.add(new splitstree6.io.writers.splits.PlainTextWriter());
		exporters.add(new splitstree6.io.writers.taxa.NexusWriter());
		exporters.add(new splitstree6.io.writers.taxa.PlainTextWriter());
		exporters.add(new splitstree6.io.writers.traits.NexusWriter());
		exporters.add(new splitstree6.io.writers.traits.PlainTextWriter());
		exporters.add(new splitstree6.io.writers.trees.NeXMLWriter());
		exporters.add(new splitstree6.io.writers.trees.NewickWriter());
		exporters.add(new splitstree6.io.writers.trees.NexusWriter());
		exporters.add(new splitstree6.io.writers.trees.PlainTextWriter());
		exporters.add(new splitstree6.io.writers.view.GMLWriter());
		exporters.add(new splitstree6.io.writers.view.NexusWriter());
		exporters.add(new splitstree6.io.writers.view.PlainTextWriter());
	}

	public static ExportManager getInstance() {
		if (instance == null)
			instance = new ExportManager();

		return instance;
	}

	/**
	 * gets the list of names of all exporters suitable for this data
	 */
	public List<String> getExporterNames(DataBlock dataBlock) {
		return exporters.stream().filter(e -> e.getFromClass() == dataBlock.getClass()).map(ReaderWriterBase::getName).sorted().toList();
	}

	/**
	 * get all exporter names
	 *
	 * @return exporter names
	 */
	public Collection<String> getExporterNames() {
		return exporters.stream().map(ReaderWriterBase::getName).collect(Collectors.toSet()).stream().sorted().toList();
	}

	/**
	 * add a file suffix, if missing
	 *
	 * @return file with suffix added, if necessary
	 */
	public File ensureFileSuffix(File selectedFile, Class<? extends DataBlock> clazz, String exporterName) {
		var suffix = FileUtils.getFileSuffix(selectedFile.getName());
		if (suffix == null) {
			var exporter = getExporterByName(clazz, exporterName);
			if (exporter != null && exporter.getFileExtensions().size() > 0) {
				return FileUtils.replaceFileSuffix(selectedFile, "." + exporter.getFileExtensions().get(0));
			}
		}
		return selectedFile;
	}

	public DataBlockWriter getExporterByName(Class<? extends DataBlock> clazz, String exporterName) {
		for (var exporter : exporters) {
			if (exporter.getFromClass() == clazz && exporterName.equals(exporter.getName()))
				return exporter;
		}
		return null;
	}

	public DataBlockWriter getExporterByName(String exporterName) {
		for (var exporter : exporters) {
			if (exporterName.equals(exporter.getName()))
				return exporter;
		}
		return null;
	}

	/**
	 * write a datablock using the named exporter
	 */
	public void exportFile(String fileName, TaxaBlock taxaBlock, DataBlock dataBlock, String exporterName) throws IOException {
		try (var w = new BufferedWriter(FileUtils.getOutputWriterPossiblyZIPorGZIP(fileName))) {
			write(taxaBlock, dataBlock, exporterName, w);
		}
		RecentFilesManager.getInstance().insertRecentFile(fileName);
		NotificationManager.showInformation(String.format("Wrote %,d bytes to file: %s", (new File(fileName)).length(), fileName));
	}

	/**
	 * write a datablock using the named exporter
	 */
	public void write(TaxaBlock taxaBlock, DataBlock dataBlock, String exporterName0, Writer w) throws IOException {
		var prependTaxa = exporterName0.equals("NexusWithTaxa");
		var exporterName = (exporterName0.equals("NexusWithTaxa") ? "Nexus" : exporterName0);
		var exporter = getExporterByName(dataBlock.getClass(), exporterName);
		if (exporter != null) {
			if (prependTaxa && exporter instanceof IHasPrependTaxa nexusWriter)
				nexusWriter.optionPrependTaxaProperty().set(true);
			if (dataBlock.getClass() == exporter.getFromClass())
				exporter.write(w, taxaBlock, dataBlock);
			else
				throw new IOException("Invalid combination of writer and data");
		}
	}
}
