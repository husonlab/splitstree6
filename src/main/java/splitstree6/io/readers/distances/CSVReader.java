/*
 *  PhylipReader.java Copyright (C) 2024 Daniel H. Huson
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

package splitstree6.io.readers.distances;

import jloda.util.FileLineIterator;
import jloda.util.IOExceptionWithLineNumber;
import jloda.util.NumberUtils;
import jloda.util.StringUtils;
import jloda.util.progress.ProgressListener;
import splitstree6.data.DistancesBlock;
import splitstree6.data.TaxaBlock;

import java.io.IOException;
import java.util.HashSet;

/**
 * CVS format
 * Daniel Huson, 4.2024
 */
public class CSVReader extends DistancesReader {
	public CSVReader() {
		setFileExtensions("csv");
	}

	@Override
	public void read(ProgressListener progressListener, String inputFile, TaxaBlock taxa, DistancesBlock distances) throws IOException {

		try (var it = new FileLineIterator(inputFile)) {
			var lineNumber = 0;
			while (it.hasNext()) {
				var line = it.next().trim();
				if (lineNumber++ == 0) {
					var seen = new HashSet<String>();
					var labels = StringUtils.split(line, ',');
					for (var label : labels) {
						label = StringUtils.stripSurroundingQuotesIfAny(label);
						var taxonLabel = label;
						var count = 0;
						while (seen.contains(taxonLabel)) {
							taxonLabel = label + "_" + (++count);
						}
						seen.add(taxonLabel);
						taxa.addTaxonByName(taxonLabel);
					}
					if (taxa.getNtax() == 0)
						throw new IOExceptionWithLineNumber(it.getLineNumber(), "No taxa found");
					distances.setNtax(taxa.getNtax());
				} else {
					var tokens = StringUtils.split(line, ',');
					if (tokens.length != taxa.getNtax()) {
						throw new IOExceptionWithLineNumber(it.getLineNumber(), "Wrong number of entries");
					}
					for (var i = 0; i < tokens.length; i++) {
						if (NumberUtils.isDouble(tokens[i]))
							distances.set(lineNumber - 1, i + 1, NumberUtils.parseDouble(tokens[i]));
						else
							throw new IOExceptionWithLineNumber(it.getLineNumber(), "Expected number, got: " + tokens[i]);
					}
				}
			}
		}
	}

	@Override
	public boolean accepts(String fileName) {
		if (!super.accepts(fileName))
			return false;
		else {
			return acceptsFile(fileName);
		}
	}

	public boolean acceptsFirstLine(String text) {
		var line = StringUtils.getFirstLine(text);
		return !line.contains("\t") && StringUtils.countOccurrences(line, ',') >= 4 && !line.contains("(");
	}
}
