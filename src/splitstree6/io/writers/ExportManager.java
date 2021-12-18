/*
 * ExportManager.java Copyright (C) 2021. Daniel H. Huson
 *
 * (Some code written by other authors, as named in code.)
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
 *
 */

package splitstree6.io.writers;

import jloda.fx.util.RecentFilesManager;
import jloda.fx.window.NotificationManager;
import jloda.util.FileUtils;
import jloda.util.PluginClassLoader;
import splitstree6.data.TaxaBlock;
import splitstree6.io.utils.DataBlockWriter;
import splitstree6.io.utils.ReaderWriterBase;
import splitstree6.workflow.DataBlock;

import java.io.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

/**
 * manages export of data in different formats
 * Daniel Huson, 11.21
 */
public class ExportManager {
	private final ArrayList<DataBlockWriter> exporters;

	private static ExportManager instance;

	private ExportManager() {
		exporters = new ArrayList<>(PluginClassLoader.getInstances(DataBlockWriter.class, "splitstree6.io.writers"));
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

	/**
	 * write a datablock using the named exporter
	 */
	public void exportFile(String fileName, TaxaBlock taxaBlock, DataBlock dataBlock, String exporterName) throws IOException {
		try (BufferedWriter w = new BufferedWriter(fileName.equals("stdout") ? new OutputStreamWriter(System.out) : new FileWriter(fileName))) {
			write(taxaBlock, dataBlock, exporterName, w);
		}
		RecentFilesManager.getInstance().insertRecentFile(fileName);
		NotificationManager.showInformation(String.format("Wrote %,d bytes to file: %s", (new File(fileName)).length(), fileName));
	}

	/**
	 * write a datablock using the named exporter
	 */
	public void write(TaxaBlock taxaBlock, DataBlock dataBlock, String exporterName, Writer w) throws IOException {
		var exporter = getExporterByName(dataBlock.getClass(), exporterName);
		if (exporter != null) {
			if (dataBlock.getClass() == exporter.getFromClass())
				exporter.write(w, taxaBlock, dataBlock);
			else
				throw new IOException("Invalid combination of writer and data");
		}
	}
}