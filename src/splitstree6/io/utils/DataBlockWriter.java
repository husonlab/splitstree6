/*
 *  DataBlockWriter.java Copyright (C) 2022 Daniel H. Huson
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

import jloda.util.FileUtils;
import splitstree6.data.TaxaBlock;
import splitstree6.workflow.DataBlock;
import splitstree6.workflow.interfaces.HasFromClass;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;

public abstract class DataBlockWriter<T extends DataBlock> extends ReaderWriterBase implements HasFromClass<T> {
	private final Class<T> fromClass;

	public DataBlockWriter(Class<T> fromClass) {
		super(fromClass.getSimpleName());
		this.fromClass = fromClass;
	}

	public abstract void write(Writer w, TaxaBlock taxaBlock, T dataBlock) throws IOException;

	public void write(String fileName, TaxaBlock taxaBlock, T dataBlock) throws IOException {
		try (var w = new OutputStreamWriter(FileUtils.getOutputStreamPossiblyZIPorGZIP(fileName))) {
			write(w, taxaBlock, dataBlock);
		}
	}

	public Class<T> getFromClass() {
		return fromClass;
	}
}
