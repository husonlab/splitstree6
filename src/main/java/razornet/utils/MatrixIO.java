/*
 * MatrixIO.java Copyright (C) 2025 Daniel H. Huson
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
 *
 */

package razornet.utils;

/*
 * MatrixIO.java Copyright (C) 2025
 *
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
 *
 */

import java.io.*;
import java.util.Objects;

/**
 * Utility class for reading and writing square double matrices.
 * <p>
 * Format:
 * <pre>
 * n
 * a11 a12 a13 ...
 * a21,a22,a23,...
 * a31;a32;a33;...
 * </pre>
 * - The first line contains a single integer {@code n} (number of rows = cols).
 * - Each subsequent line contains n floating-point numbers separated by spaces,
 * commas, or semicolons.
 * - Blank lines and lines starting with '#' are ignored.
 */
public final class MatrixIO {
	/**
	 * Reads a matrix from the given filename.
	 */
	public static double[][] read(String filename) throws IOException {
		Objects.requireNonNull(filename, "filename");
		try (var fr = new FileReader(filename)) {
			return read(fr);
		}
	}

	/**
	 * Writes a matrix to the given filename.
	 */
	public static void write(String filename, double[][] matrix) throws IOException {
		Objects.requireNonNull(filename, "filename");
		try (FileWriter fw = new FileWriter(filename)) {
			write((Writer) fw, matrix);
		}
	}


	/**
	 * Reads a square matrix of doubles from the given reader.
	 *
	 * @param r the reader to read from
	 * @return the matrix as double[n][n]
	 * @throws IOException if malformed or inconsistent data is found
	 */
	public static double[][] read(Reader r) throws IOException {
		Objects.requireNonNull(r, "reader");

		BufferedReader br = new BufferedReader(r);
		String line;
		int n = -1;

		// Read first non-empty, non-comment line for size
		while ((line = br.readLine()) != null) {
			line = line.trim();
			if (line.isEmpty() || line.startsWith("#")) continue;
			try {
				n = Integer.parseInt(line);
			} catch (NumberFormatException e) {
				throw new IOException("First non-comment line must be an integer matrix size");
			}
			break;
		}

		if (n <= 0) {
			throw new IOException("Matrix dimension must be positive");
		}

		double[][] M = new double[n][n];
		int row = 0;
		var hasLabels = false;
		var noLabels = false;
		while ((line = br.readLine()) != null && row < n) {
			line = line.trim();
			if (line.isEmpty() || line.startsWith("#")) continue;
			if (line.contains("#"))
				line = line.substring(0, line.indexOf('#'));
			// Split by whitespace, commas, or semicolons
			String[] tokens = line.split("[\\s,;]+");
			if (tokens.length == n + 1) {
				var tmp = new String[n];
				System.arraycopy(tokens, 1, tmp, 0, tokens.length - 1);
				tokens = tmp;
				hasLabels = true;
			} else if (tokens.length == n) {
				noLabels = true;
			}
			if (hasLabels && noLabels)
				throw new IOException("lines contain different numbers of tokens");

			if (tokens.length != n) {
				throw new IOException("Line " + (row + 2) + ": expected " + n + " values, found " + tokens.length);
			}
			for (int j = 0; j < n; j++) {
				try {
					M[row][j] = Double.parseDouble(tokens[j]);
				} catch (NumberFormatException e) {
					throw new IOException("Invalid numeric value at row " + row + ", column " + j);
				}
			}
			row++;
		}

		if (row < n) {
			throw new IOException("Expected " + n + " data lines but found only " + row);
		}

		return M;
	}

	/**
	 * Writes the given square matrix to the writer.
	 *
	 * @param w the writer to write to
	 * @param M the matrix to write
	 * @throws IOException if an I/O error occurs
	 */
	public static void write(Writer w, double[][] M) throws IOException {
		Objects.requireNonNull(w, "writer");
		Objects.requireNonNull(M, "matrix");
		int n = M.length;
		for (double[] row : M) {
			if (row.length != n)
				throw new IOException("Matrix must be square: row length " + row.length + " != " + n);
		}

		w.write(Integer.toString(n));
		w.write(System.lineSeparator());
		for (double[] doubles : M) {
			for (int j = 0; j < n; j++) {
				if (j > 0) w.write(' ');
				w.write(Double.toString(doubles[j]));
			}
			w.write(System.lineSeparator());
		}
		w.flush();
	}
}