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
import java.util.Collections;
import java.util.StringTokenizer;

/**
 * Phylip matrix input
 * <p>
 * Accepts the labeled square, upper- and lower-triangular layouts, and additionally a <i>label-less</i>
 * square matrix (rows of numbers with no taxon name), for which taxa are named {@code t1...tn}.
 * Comment lines starting with '#' and empty lines are ignored.
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
		var unlabeled = false; // label-less square matrix, taxa are named t1...tn
		int row = 0;
		int numberOfTaxa = 0;

		try (var it = new FileLineIterator(inputFile)) {
			while (it.hasNext()) {
				final var line = it.next().trim();

				if (line.startsWith("#") || line.isEmpty())
					continue;
				if (row == 0) {
					numberOfTaxa = Integer.parseInt(line);
					distances.setNtax(numberOfTaxa);
				} else {
					var tokens = line.split("\\s+");

					// decide this on the raw tokens of the first row, before the old-Phylip fixup below, which
					// would otherwise split a long leading number (say 0.12345678901) into a label plus a value
					if (row == 1)
						unlabeled = isUnlabeledSquareRow(tokens, numberOfTaxa);

					// does this look like old Phylip in which positions 0-10 are the label?
					if (!unlabeled && (row == 1 || triangle == Triangle.Both) && tokens[0].length() > 10 && NumberUtils.isDouble(tokens[0].substring(10))) {
						var tmp = new String[tokens.length + 1];
						tmp[0] = tokens[0].substring(0, 10);
						tmp[1] = tokens[0].substring(10);
						System.arraycopy(tokens, 1, tmp, 2, tokens.length - 1);
						tokens = tmp;
					}

					if (row == 1) {
						if (unlabeled)
							triangle = Triangle.Both;
						else if (tokens.length == 1)
							triangle = Triangle.Lower;
						else if (tokens.length == numberOfTaxa) {
							triangle = Triangle.Upper;
						}
						else if (tokens.length == numberOfTaxa + 1)
							triangle = Triangle.Both;
						else
							throw new IOExceptionWithLineNumber(it.getLineNumber(), "Matrix has wrong shape");
					}

					if (row > numberOfTaxa)
						throw new IOExceptionWithLineNumber(it.getLineNumber(), "Matrix has wrong shape");

					if (unlabeled) { // label-less square matrix: every token is a value, so the taxa are named here
						if (tokens.length != numberOfTaxa)
							throw new IOExceptionWithLineNumber(it.getLineNumber(), "Matrix has wrong shape");
						taxa.addTaxaByNames(Collections.singleton("t" + row));
						for (int col = 1; col <= numberOfTaxa; col++) {
							final double value = NumberUtils.parseDouble(tokens[col - 1]);
							distances.set(row, col, value);
						}
					} else if (triangle == Triangle.Both) {
						if (tokens.length != numberOfTaxa + 1)
							throw new IOExceptionWithLineNumber(it.getLineNumber(), "Matrix has wrong shape");
						taxa.addTaxaByNames(Collections.singleton(tokens[0]));
						for (int col = 1; col < tokens.length; col++) {
							final double value = NumberUtils.parseDouble(tokens[col]);
							distances.set(row, col, value);
						}
					} else if (triangle == Triangle.Upper) {
						if (tokens.length != numberOfTaxa + 1 - row)
							throw new IOExceptionWithLineNumber(it.getLineNumber(), "Matrix has wrong shape");
						taxa.addTaxaByNames(Collections.singleton(tokens[0]));
						for (int i = 1; i < tokens.length; i++) {
							final int col = row + i;
							final double value = NumberUtils.parseDouble(tokens[i]);
							distances.set(row, col, value);
							distances.set(col, row, value);
						}
					} else if (triangle == Triangle.Lower) {
						if (tokens.length != row)
							throw new IOExceptionWithLineNumber(it.getLineNumber(), "Matrix has wrong shape");
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

	/**
	 * Is this the first row of a label-less square matrix, rather than a labeled upper-triangular one?
	 * Both have exactly n tokens, so they must be told apart: an upper-triangular first row starts with
	 * the taxon label and its first value is d(1,2) &gt; 0, whereas a label-less square row starts with
	 * the diagonal entry d(1,1) = 0. We therefore require all n tokens to be numbers and the first to be
	 * zero, which only ever catches input that used to fail with "Matrix has wrong shape" at row 2 --
	 * no file that parsed before changes meaning.
	 * <p>
	 * The one input this cannot distinguish is an upper-triangular matrix whose first taxon is literally
	 * named "0".
	 */
	private static boolean isUnlabeledSquareRow(String[] tokens, int numberOfTaxa) {
		if (tokens.length != numberOfTaxa)
			return false;
		for (var token : tokens) {
			if (!NumberUtils.isDouble(token))
				return false;
		}
		return NumberUtils.parseDouble(tokens[0]) == 0;
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
		final StringTokenizer tokens = new StringTokenizer(line);
		return tokens.countTokens() == 1 && NumberUtils.isInteger(tokens.nextToken());
	}

}
