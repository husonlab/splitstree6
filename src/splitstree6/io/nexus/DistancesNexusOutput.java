/*
 *  DistancesNexusOutput.java Copyright (C) 2022 Daniel H. Huson
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

package splitstree6.io.nexus;

import jloda.util.StringUtils;
import splitstree6.data.DistancesBlock;
import splitstree6.data.TaxaBlock;

import java.io.IOException;
import java.io.Writer;

/**
 * distances nexus output
 * Daniel Huson, 2.2018
 */
public class DistancesNexusOutput extends NexusIOBase implements INexusOutput<DistancesBlock> {
	/**
	 * write a block in nexus format
	 */
	@Override
	public void write(Writer w, TaxaBlock taxaBlock, DistancesBlock distancesBlock) throws IOException {
		final var format = distancesBlock.getFormat();

		w.write("\nBEGIN DISTANCES;\n");
		writeTitleAndLink(w);
		w.write("DIMENSIONS ntax=" + distancesBlock.getNtax() + ";\n");
		w.write("FORMAT");
		if (format.isOptionLabels())
			w.write(" labels=left");
		else
			w.write(" labels=no");
		if (format.isOptionDiagonal())
			w.write(" diagonal");
		else
			w.write(" no diagonal");

		w.write(" triangle=" + format.getOptionTriangle());
		w.write(";\n");

		final var diag = format.isOptionDiagonal() ? 0 : 1;

		// write matrix:
		{
			w.write("MATRIX\n");

			for (var s = 1; s <= distancesBlock.getNtax(); s++) {
				if (format.isOptionLabels()) {
					w.write("[" + s + "]");
					w.write(" '" + taxaBlock.get(s).getName() + "'");
					pad(w, taxaBlock, s);
				}

				final var buf = new StringBuilder();
				InnerLoop:
				for (var t = 1; t <= distancesBlock.getNtax(); t++) {
					switch (format.getOptionTriangle()) {
						case Lower:
							if (t > s || !format.isOptionDiagonal() && t == s)
								break InnerLoop; // don't write the upper triangle
							break;
						case Upper:
							if (s == distancesBlock.getNtax() && !format.isOptionDiagonal())
								break InnerLoop;
							if ((!format.isOptionDiagonal() && t == s + 1) || (format.isOptionDiagonal() && t == s)) {
								// replace lower dialog by spaces
								buf.replace(0, buf.length(), " ".repeat(buf.length()));
							}
						default:
						case Both:
					}
					buf.append(StringUtils.removeTrailingZerosAfterDot(String.format(" %.6f", distancesBlock.get(s, t))));
				}
				w.write(buf.toString() + "\n");
			}
			w.write(";\n");
		}

		// write variances, if present
		if (format.isOptionVariancesIO() && distancesBlock.isVariances()) {
			w.write("VARMATRIX\n");

			for (var t = 1; t <= distancesBlock.getNtax(); t++) {
				if (format.isOptionLabels()) {
					w.write("[" + t + "]");
					w.write(" '" + taxaBlock.get(t).getName() + "'");
					pad(w, taxaBlock, t);

				}
				int left;
				int right;

				// both
				switch (format.getOptionTriangle()) {
					case Lower -> {
						left = 1;//1;
						right = t - diag;//t-1+diag;
					}
					case Upper -> {
						left = t + diag;//t-1+diag;
						right = distancesBlock.getNtax();
						for (int i = 1; i < t; i++)
							w.write("      ");
					}
					default -> {
						left = 1;
						right = distancesBlock.getNtax();
					}
				}

				for (var q = left; q <= right; q++) {
					w.write(StringUtils.removeTrailingZerosAfterDot(String.format(" %.6f", distancesBlock.getVariance(t, q))));
				}
				w.write("\n");
			}
			w.write(";\n");
		}
		w.write("END; [DISTANCES]\n");
	}

	/**
	 * pad with white space
	 *
	 * @param w         the writer
	 * @param taxaBlock the Taxa
	 * @param index     the index of label
	 */
	public static void pad(Writer w, TaxaBlock taxaBlock, int index) {
		try {
			var len = taxaBlock.getLabel(index).length();
			var max = maxLabelLength(taxaBlock);

			for (var i = 1; i <= (max - len + 2); i++) {
				w.write(" ");
			}
		} catch (Exception ignored) {
		}
	}

	/**
	 * Get the max length of all the labels.
	 *
	 * @return longer the max length.
	 */
	private static int maxLabelLength(TaxaBlock taxaBlock) {
		var len = 0;
		var longer = 0;

		for (var i = 1; i <= taxaBlock.getNtax(); i++) {
			len = taxaBlock.getLabel(i).length();
			if (longer < len) {
				longer = len;
			}
		}
		return longer;
	}
}
