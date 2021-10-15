/*
 *  DataLoader.java Copyright (C) 2021 Daniel H. Huson
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

package splitstree6.workflow;

import jloda.util.ProgressListener;
import splitstree6.data.SourceBlock;
import splitstree6.data.TaxaBlock;
import splitstree6.io.readers.ImportManager;
import splitstree6.io.utils.DataReaderBase;

import java.io.IOException;
import java.util.ArrayList;

/**
 * data load algorithm
 *
 * @param <S>
 * @param <T>
 */
abstract public class DataLoader<S extends DataBlock, T extends DataBlock> extends Algorithm<S, T> {
	protected final ArrayList<DataReaderBase<T>> readers = new ArrayList<>();

	public DataLoader(Class<S> fromClass, Class<T> toClass) {
		super(fromClass, toClass);
		if (!fromClass.equals(SourceBlock.class))
			throw new RuntimeException("DataLoader: fromClass!=SourceBlock");
		readers.addAll(ImportManager.getInstance().getReaders(toClass));
	}

	public ArrayList<DataReaderBase<T>> getReaders() {
		return readers;
	}

	@Override
	final public void compute(ProgressListener progress, TaxaBlock taxaBlock, S inputData, T outputData) {
	}

	public abstract void load(ProgressListener progress, S sourceBlock, TaxaBlock outputTaxa, T outputData) throws IOException;
}
