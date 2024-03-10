/*
 *  Model.java Copyright (C) 2024 Daniel H. Huson
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

package splitstree6.xtra.mapview;

import javafx.beans.property.LongProperty;
import javafx.beans.property.SimpleLongProperty;
import jloda.util.progress.ProgressPercentage;
import splitstree6.data.CharactersBlock;
import splitstree6.data.TaxaBlock;
import splitstree6.io.readers.ImportManager;
import splitstree6.io.readers.characters.CharactersReader;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

/**
 * simple data model
 */
public class Model {
	private final TaxaBlock taxaBlock = new TaxaBlock();
	private final CharactersBlock charactersBlock = new CharactersBlock();
	private final LongProperty lastUpdate = new SimpleLongProperty(this, "lastUpdate", 0L);

	public TaxaBlock getTaxaBlock() {
		return taxaBlock;
	}

	public CharactersBlock getCharactersBlock() {
		return charactersBlock;
	}

	public void load(File file) throws IOException {
		charactersBlock.clear();
		taxaBlock.clear();

		var importManager = ImportManager.getInstance();
		var dataType = importManager.getDataType(file.getPath());
		if (dataType.equals(CharactersBlock.class)) {
			var fileFormat = importManager.getFileFormat(file.getPath());
			var importer = (CharactersReader) importManager.getImporterByDataTypeAndFileFormat(dataType, fileFormat);
			importer.read(new ProgressPercentage(), file.getPath(), taxaBlock, charactersBlock);
			incrementLastUpdate();
		} else throw new IOException("File does not contain characters");
	}

	public long getLastUpdate() {
		return lastUpdate.get();
	}

	public LongProperty lastUpdateProperty() {
		return lastUpdate;
	}

	public void incrementLastUpdate() {
		lastUpdate.set(System.currentTimeMillis());
	}
}
