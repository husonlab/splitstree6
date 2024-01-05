/*
 * ImportManager.java Copyright (C) 2024 Daniel H. Huson
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

package splitstree6.io.readers;

import javafx.stage.FileChooser;
import jloda.util.Basic;
import splitstree6.data.SplitsBlock;
import splitstree6.data.TreesBlock;
import splitstree6.io.utils.DataReaderBase;
import splitstree6.io.utils.IDataReaderNoAutoDetect;
import splitstree6.workflow.DataBlock;

import java.util.*;

public class ImportManager {
	public static final String UNKNOWN_FORMAT = "Unknown";
	private static ImportManager instance;

	private final ArrayList<DataReaderBase> readers = new ArrayList<>();
	private final ArrayList<String> extensions = new ArrayList<>();

	public synchronized static ImportManager getInstance() {
		if (instance == null)
			instance = new ImportManager();
		return instance;
	}

	private ImportManager() {
		readers.add(new splitstree6.io.readers.characters.FastAReader());
		readers.add(new splitstree6.io.readers.characters.MSFReader());
		readers.add(new splitstree6.io.readers.characters.NexusReader());
		readers.add(new splitstree6.io.readers.characters.PhylipReader());
		readers.add(new splitstree6.io.readers.distances.NexusReader());
		readers.add(new splitstree6.io.readers.distances.PhylipReader());
		readers.add(new splitstree6.io.readers.genomes.NexusReader());
		readers.add(new splitstree6.io.readers.network.NexusReader());
		readers.add(new splitstree6.io.readers.report.NexusReader());
		readers.add(new splitstree6.io.readers.splits.NewickReader());
		readers.add(new splitstree6.io.readers.splits.NexusReader());
		readers.add(new splitstree6.io.readers.trees.NewickReader());
		readers.add(new splitstree6.io.readers.trees.NexmlReader());
		readers.add(new splitstree6.io.readers.trees.NexusReader());
		readers.add(new splitstree6.io.readers.view.NexusReader());

		readers.sort((a, b) -> {
			if (a.getToClass().equals(TreesBlock.class) && b.getToClass().equals(SplitsBlock.class))
				return -1;
			else if (a.getToClass().equals(SplitsBlock.class) && b.getToClass().equals(TreesBlock.class))
				return 1;
			else
				return 0;
		});

		for (var reader : readers)
			extensions.addAll(reader.getFileExtensions());
	}

	public Class<DataBlock> determineInputType(String fileName) {
		for (var reader : readers) {
			if (reader.accepts(fileName))
				return reader.getToClass();
		}
		return null;
	}

	public ArrayList<DataReaderBase> getReaders(String fileName) {
		var list = new ArrayList<DataReaderBase>();
		for (var reader : readers) {
			if (reader.accepts(fileName))
				list.add(reader);
		}
		return list;
	}


	public DataReaderBase getReader(String fileName) {
		var readers = getReaders(fileName);
		return readers.size() > 0 ? readers.get(0) : null;
	}

	public Class<? extends DataBlock> getDataType(String fileName) {
		var reader = getReader(fileName);
		return reader == null ? null : reader.getToClass();
	}


	public ArrayList<Class<? extends DataBlock>> getAllDataTypes(String fileName) {
		var list = new HashSet<Class<? extends DataBlock>>();
		for (var reader : getReaders(fileName)) {
			list.add(reader.getToClass());
		}
		return new ArrayList<>(list);
	}

	public ArrayList<Class<? extends DataBlock>> getAllDataTypes() {
		var list = new HashSet<Class<? extends DataBlock>>();
		for (var reader : readers) {
			list.add(reader.getToClass());
		}
		return new ArrayList<>(list);
	}


	public Collection<? extends String> getAllFileFormats() {
		final Set<String> set = new TreeSet<>();
		for (var reader : readers) {
			set.add(getFileFormat(reader));
		}
		final ArrayList<String> result = new ArrayList<>();
		result.add(UNKNOWN_FORMAT);
		result.addAll(set);
		return result;
	}

	public Collection<? extends String> getAllFileFormats(String file) {
		final var set = new TreeSet<String>();
		for (var reader : getReaders(file)) {
			set.add(getFileFormat(reader));
		}
		final ArrayList<String> result = new ArrayList<>();
		result.add(UNKNOWN_FORMAT);
		result.addAll(set);
		return result;
	}

	private static String getFileFormat(DataReaderBase reader) {
		var name = Basic.getShortName(reader.getClass());
		if (name.endsWith("Reader"))
			return name.substring(0, name.length() - "Reader".length());
		else if (name.endsWith("Importer"))
			return name.substring(0, name.length() - 8);
		else
			return name;
	}

	public String getFileFormat(String fileName) {
		String fileFormat = null;

		for (var importer : readers) {
			if (!(importer instanceof IDataReaderNoAutoDetect) && importer.acceptsFile(fileName)) {
				String format = getFileFormat(importer);
				if (fileFormat == null)
					fileFormat = format;
				else if (!fileFormat.equals(format))
					return UNKNOWN_FORMAT;
			}
		}
		if (fileFormat == null)
			return UNKNOWN_FORMAT;
		else
			return fileFormat;
	}


	public ArrayList<DataReaderBase> getReadersByText(String text) {
		var list = new ArrayList<DataReaderBase>();
		for (var reader : readers) {
			if (reader.acceptsFirstLine(text))
				list.add(reader);
		}
		return list;
	}

	public <S extends DataBlock> ArrayList<DataReaderBase<S>> getReaders(Class<S> clazz) {
		var list = new ArrayList<DataReaderBase<S>>();
		for (var reader : readers) {
			if (reader.getToClass().equals(clazz))
				list.add(reader);
		}
		return list;
	}

	public Collection<String> getFileExtensions() {
		return extensions;
	}

	public Collection<FileChooser.ExtensionFilter> getExtensionFilters() {
		var filters = new HashSet<FileChooser.ExtensionFilter>();
		for (var reader : readers) {
			if (reader.getFileExtensions().size() > 0)
				filters.add(reader.getExtensionFilter());
		}
		var list = new ArrayList<>(filters);
		list.sort(Comparator.comparing(FileChooser.ExtensionFilter::getDescription));
		list.add(0, new FileChooser.ExtensionFilter("Text (*.txt, *.txt.gz)", "*.txt,*.txt.gz"));
		list.add(0, new FileChooser.ExtensionFilter("All (*.*)", "*.*"));
		return list;
	}

	/**
	 * gets the importer by type and file format
	 *
	 * @return importer or null
	 */
	public DataReaderBase getImporterByDataTypeAndFileFormat(Class<? extends DataBlock> dataType, String fileFormat) {
		for (var importer : readers) {
			if (importer.getToClass().equals(dataType) && getFileFormat(importer).equals(fileFormat))
				return importer;
		}
		return null;
	}

	/**
	 * merge extension filters, using the description of the first. All extensions are listed alphabetically
	 *
	 * @param filters filters
	 * @return single filter
	 */
	public static FileChooser.ExtensionFilter mergeExtensionFilters(Collection<FileChooser.ExtensionFilter> filters) {
		var filterDescription = "";
		var extensions = new TreeSet<String>();
		for (var filter : filters) {
			if (filterDescription.isBlank())
				filterDescription = filter.getDescription().replaceAll(" \\(.*?\\)", "");
			extensions.addAll(filter.getExtensions());
		}
		return new FileChooser.ExtensionFilter(filterDescription, extensions.toArray(new String[0]));
	}

}
