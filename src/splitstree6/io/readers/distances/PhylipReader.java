/*
 * PhylipReader.java Copyright (C) 2022 Daniel H. Huson
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

package splitstree6.io.readers.distances;

import jloda.util.FileLineIterator;
import jloda.util.FileUtils;
import jloda.util.IOExceptionWithLineNumber;
import jloda.util.NumberUtils;
import jloda.util.progress.ProgressListener;
import splitstree6.data.DistancesBlock;
import splitstree6.data.TaxaBlock;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.StringTokenizer;

/**
 * Phylip matrix input
 * Daria Evseeva, 02.10.2017, Daniel Huson, 3.2020
 */
public class PhylipReader extends DistancesReader {
	public PhylipReader() {
		setFileExtensions("dist", "dst", "matrix", "mat", "phylip", "phy");
	}

	public enum Triangle {Both, Lower, Upper}

	@Override
	public void read(ProgressListener progressListener, String inputFile, TaxaBlock taxa, DistancesBlock distances) throws IOException {
		Triangle triangle = null;
		int row = 0;
		int numberOfTaxa = 0;

		try (var it = new FileLineIterator(inputFile)) {
			while (it.hasNext()) {
				final var line = it.next().trim();

				if (line.startsWith("#") || line.length() == 0)
					continue;
				if (row == 0) {
					numberOfTaxa = Integer.parseInt(line);
					distances.setNtax(numberOfTaxa);
				} else {
					final var tokens = line.split("\\s+");
					if (row == 1) {
						if (tokens.length == 1)
							triangle = Triangle.Lower;
						else if (tokens.length == numberOfTaxa)
							triangle = Triangle.Upper;
						else if (tokens.length == numberOfTaxa + 1)
							triangle = Triangle.Both;
						else
							throw new IOExceptionWithLineNumber(it.getLineNumber(), "Matrix has wrong getShape");
					}

					if (row > numberOfTaxa)
						throw new IOExceptionWithLineNumber(it.getLineNumber(), "Matrix has wrong getShape");

					if (triangle == Triangle.Both) {
						if (tokens.length != numberOfTaxa + 1)
							throw new IOExceptionWithLineNumber(it.getLineNumber(), "Matrix has wrong getShape");
						taxa.addTaxaByNames(Collections.singleton(tokens[0]));
						for (int col = 1; col < tokens.length; col++) {
							final double value = NumberUtils.parseDouble(tokens[col]);
							distances.set(row, col, value);
						}
					} else if (triangle == Triangle.Upper) {
						if (tokens.length != numberOfTaxa + 1 - row)
							throw new IOExceptionWithLineNumber(it.getLineNumber(), "Matrix has wrong getShape");
						taxa.addTaxaByNames(Collections.singleton(tokens[0]));
						for (int i = 1; i < tokens.length; i++) {
							final int col = row + i;
							final double value = NumberUtils.parseDouble(tokens[i]);
							distances.set(row, col, value);
							distances.set(col, row, value);
						}
					} else if (triangle == Triangle.Lower) {
						if (tokens.length != row)
							throw new IOExceptionWithLineNumber(it.getLineNumber(), "Matrix has wrong getShape");
						taxa.addTaxaByNames(Collections.singleton(tokens[0]));
						for (int col = 1; col < tokens.length; col++) {
							final double value = NumberUtils.parseDouble(tokens[col]);
							distances.set(row, col, value);
							distances.set(col, row, value);
						}
					}
				}
				row++;
			}
		}
		if (triangle == Triangle.Both) {
			ensureSymmetric(taxa, distances);
		}
	}

	@Override
	public boolean accepts(String fileName) {
		if (!super.accepts(fileName))
			return false;
		else {
			String line = FileUtils.getFirstLineFromFile(new File(fileName));
			if (line == null) return false;

			final StringTokenizer tokens = new StringTokenizer(line);
			return tokens.countTokens() == 1 && NumberUtils.isInteger(tokens.nextToken());
		}
	}
}
