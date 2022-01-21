/*
 *  DataReaderBase.java Copyright (C) 2022 Daniel H. Huson
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

package splitstree6.io.utils;

import jloda.util.progress.ProgressListener;
import splitstree6.data.TaxaBlock;
import splitstree6.workflow.DataBlock;

import java.io.IOException;

public abstract class DataReaderBase<T extends DataBlock> extends ReaderWriterBase {
	protected Class<T> toClass;

	public DataReaderBase(Class<T> toClass) {
		super(toClass.getSimpleName());
		this.toClass = toClass;
	}

	public abstract void read(ProgressListener progress, String fileName, TaxaBlock taxaBlock, T dataBlock) throws IOException;

	public Class<T> getToClass() {
		return toClass;
	}
}
