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

import jloda.util.FileUtils;
import jloda.util.NumberUtils;
import jloda.util.StringUtils;
import jloda.util.progress.ProgressListener;
import splitstree6.data.DistancesBlock;
import splitstree6.data.TaxaBlock;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * CVS format
 * Daniel Huson, 4.2024
 */
public class CSVReader extends DistancesReader {
	public CSVReader() {
		setFileExtensions("csv");
	}

	/**
	 * Read a distance matrix from a CSV file in one of three variants:
	 * (a) First non-empty line is a single integer n; then n lines: label, n values
	 * (b) n lines: label, n values (n inferred from line count)
	 * (c) n lines: n numeric values only; labels auto-assigned as t1..tn
	 * <p>>
	 * - Separators: commas (CSV). Whitespace around fields is trimmed.
	 * - Blank lines and lines starting with '#' or '//' are ignored.
	 * - Returns labels and matrix as doubles.
	 */
	@Override
	public void read(ProgressListener progress, String inputFile, TaxaBlock taxaBlock, DistancesBlock distancesBlock) throws IOException {
		try (var br = new BufferedReader(FileUtils.getReaderPossiblyZIPorGZIP(inputFile))) {
			// 1) Read all meaningful lines upfront (skip blanks/comments)
			List<String> lines = new ArrayList<>();
			for (String raw; (raw = br.readLine()) != null; ) {
				String line = raw.trim();
				if (line.isEmpty()) continue;
				if (line.startsWith("#") || line.startsWith("//")) continue;
				lines.add(line);
			}
			if (lines.isEmpty()) {
				throw new IOException("Empty distance file.");
			}

			// 2) Decide variant
			// Try variant (a): first line is a single integer token
			String first = lines.get(0);
			String[] firstTokens = splitCsv(first);
			boolean variantA = (firstTokens.length == 1 && NumberUtils.isInteger(firstTokens[0]));

			if (variantA) {
				int n = Integer.parseInt(firstTokens[0]);
				if (lines.size() - 1 < n) {
					throw new IOException("Expected " + n + " data lines after header, found " + (lines.size() - 1));
				}
				List<String> labels = new ArrayList<>(n);
				double[][] d = new double[n][n];

				for (int i = 0; i < n; i++) {
					String[] tok = splitCsv(lines.get(i + 1));
					if (tok.length != n + 1) {
						throw new IOException("Line " + (i + 2) + ": expected " + (n + 1) +
											  " CSV fields (label + " + n + " values), found " + tok.length);
					}
					String label = tok[0].trim();
					if (label.isEmpty()) {
						throw new IOException("Line " + (i + 2) + ": empty label.");
					}
					labels.add(label);
					for (int j = 0; j < n; j++) {
						d[i][j] = parseDouble(tok[j + 1], i, j);
					}
				}
				validateSquare(d);

				taxaBlock.setNtax(labels.size());
				taxaBlock.addTaxaByNames(labels);
				distancesBlock.setNtax(taxaBlock.getNtax());
				for (var i = 0; i < d.length; i++) {
					for (var j = 0; j < d.length; j++) {
						distancesBlock.getDistances()[i][j] = d[i][j];
					}
				}
				return;
			}

			// For variants (b) and (c), all remaining lines are data lines
			List<String[]> rows = new ArrayList<>(lines.size());
			for (String line : lines) {
				rows.add(splitCsv(line));
			}
			int n = rows.size();

			// Heuristic: if the first row has all tokens numeric AND its length == n,
			// we consider it variant (c). Otherwise, variant (b).
			String[] firstRow = rows.get(0);
			boolean firstRowAllNumeric = allNumeric(firstRow);
			boolean variantC = firstRowAllNumeric && firstRow.length == n;

			if (variantC) {
				// (c) No labels in file; autogenerate t1..tn
				List<String> labels = new ArrayList<>(n);
				double[][] d = new double[n][n];
				for (int i = 0; i < n; i++) {
					String[] tok = rows.get(i);
					if (tok.length != n) {
						throw new IOException("Line " + (i + 1) + ": expected " + n +
											  " numeric values, found " + tok.length);
					}
					labels.add("t" + (i + 1));
					for (int j = 0; j < n; j++) {
						d[i][j] = parseDouble(tok[j], i, j);
					}
				}
				validateSquare(d);

				taxaBlock.setNtax(labels.size());
				taxaBlock.addTaxaByNames(labels);
				distancesBlock.setNtax(taxaBlock.getNtax());
				for (var i = 0; i < d.length; i++) {
					for (var j = 0; j < d.length; j++) {
						distancesBlock.getDistances()[i][j] = d[i][j];
					}
				}
			} else {
				// (b) Each line: label, n values. Here n is the number of lines.
				List<String> labels = new ArrayList<>(n);
				double[][] d = new double[n][n];

				for (int i = 0; i < n; i++) {
					String[] tok = rows.get(i);
					if (tok.length != n + 1) {
						throw new IOException("Line " + (i + 1) + ": expected " + (n + 1) +
											  " CSV fields (label + " + n + " values), found " + tok.length);
					}
					String label = tok[0].trim();
					if (label.isEmpty()) {
						throw new IOException("Line " + (i + 1) + ": empty label.");
					}
					labels.add(label);
					for (int j = 0; j < n; j++) {
						d[i][j] = parseDouble(tok[j + 1], i, j);
					}
				}
				validateSquare(d);
				taxaBlock.setNtax(labels.size());
				taxaBlock.addTaxaByNames(labels);
				distancesBlock.setNtax(taxaBlock.getNtax());
				for (var i = 0; i < d.length; i++) {
					for (var j = 0; j < d.length; j++) {
						distancesBlock.getDistances()[i][j] = d[i][j];
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

	/**
	 * Splits a CSV line by commas, trimming whitespace. (No embedded-quote handling.)
	 */
	private static String[] splitCsv(String line) {
		// Basic CSV: split on commas and trim; treat consecutive commas as empty fields
		String[] raw = line.split(",", -1);
		String[] out = new String[raw.length];
		for (int i = 0; i < raw.length; i++) out[i] = raw[i].trim();
		return out;
	}

	private static boolean allNumeric(String[] arr) {
		for (String s : arr) {
			if (!NumberUtils.isDouble(s)) return false;
		}
		return true;
	}

	/**
	 * Optional sanity: checks the matrix is square and diagonal is numeric (no NaNs).
	 */
	private static void validateSquare(double[][] d) throws IOException {
		int n = d.length;
		for (int i = 0; i < n; i++) {
			if (d[i] == null || d[i].length != n) {
				throw new IOException("Non-square matrix at row " + (i + 1));
			}
			if (Double.isNaN(d[i][i])) {
				throw new IOException("Diagonal contains NaN at (" + i + "," + i + ")");
			}
		}
	}

	private static double parseDouble(String s, int i, int j) throws IOException {
		try {
			return Double.parseDouble(s);
		} catch (NumberFormatException e) {
			throw new IOException("Invalid number at row " + (i + 1) + ", column " + (j + 1) + ": '" + s + "'");
		}
	}
}
