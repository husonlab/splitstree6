/*
 *  ImportManager.java Copyright (C) 2021 Daniel H. Huson
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

package splitstree6.io.readers;

import jloda.util.PluginClassLoader;
import splitstree6.io.utils.DataReaderBase;
import splitstree6.workflow.DataBlock;

import java.util.ArrayList;

public class ImportManager {
	private static ImportManager instance;

	private final ArrayList<DataReaderBase> readers = new ArrayList<>();

	public synchronized static ImportManager getInstance() {
		if (instance == null)
			instance = new ImportManager();
		return instance;
	}

	private ImportManager() {
		readers.addAll(PluginClassLoader.getInstances(DataReaderBase.class,
				"splitstree6.io.readers.characters",
				"splitstree6.io.readers.distances",
				"splitstree6.io.readers.splits",
				"splitstree6.io.readers.trees"));
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

	public <S extends DataBlock> ArrayList<DataReaderBase<S>> getReaders(Class<S> clazz) {
		var list = new ArrayList<DataReaderBase<S>>();
		for (var reader : readers) {
			if (reader.getToClass().equals(clazz))
				list.add(reader);
		}
		return list;
	}
}
