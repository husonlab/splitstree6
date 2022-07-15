/*
 * ImportManager.java Copyright (C) 2022 Daniel H. Huson
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
import jloda.util.PluginClassLoader;
import splitstree6.io.utils.DataReaderBase;
import splitstree6.workflow.DataBlock;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;

public class ImportManager {
	private static ImportManager instance;

	private final ArrayList<DataReaderBase> readers = new ArrayList<>();
	private final ArrayList<String> extensions = new ArrayList<>();

	public synchronized static ImportManager getInstance() {
		if (instance == null)
			instance = new ImportManager();
		return instance;
	}

	private ImportManager() {
		readers.addAll(PluginClassLoader.getInstances(DataReaderBase.class, "splitstree6.io.readers"));
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
}
