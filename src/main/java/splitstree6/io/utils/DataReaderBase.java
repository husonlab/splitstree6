/*
 *  DataReaderBase.java Copyright (C) 2024 Daniel H. Huson
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
import jloda.util.progress.ProgressListener;
import splitstree6.data.TaxaBlock;
import splitstree6.workflow.DataBlock;

import java.io.File;
import java.io.IOException;

public abstract class DataReaderBase<T extends DataBlock> extends ReaderWriterBase {
	protected final Class<T> toClass;

	public DataReaderBase(Class<T> toClass) {
		super(toClass.getSimpleName());
		this.toClass = toClass;
	}

	public abstract void read(ProgressListener progress, String fileName, TaxaBlock taxaBlock, T dataBlock) throws IOException;

	public Class<T> getToClass() {
		return toClass;
	}

	abstract public boolean acceptsFirstLine(String text);

	public boolean acceptsFile(String fileName) {
		var file = new File(fileName);
		var firstLine = FileUtils.getFirstLineFromFile(file);
		if (firstLine != null && acceptsFirstLine(firstLine))
			return true;
		// Fall back to the first non-empty, non-comment ('#') line, so a data file preceded by a leading comment
		// block (e.g. a Phylip matrix with '# ...' header lines) is still recognized. Formats whose own marker is
		// itself a '#' line - Nexus '#nexus', Stockholm '# STOCKHOLM' - are already matched above on the literal
		// first line, so skipping '#' lines here does not affect them.
		var contentLine = FileUtils.getFirstLineFromFileIgnoreEmptyLines(file, "#", 1000);
		return contentLine != null && !contentLine.equals(firstLine) && acceptsFirstLine(contentLine);
	}

}
