/*
 * PlainTextWriter.java Copyright (C) 2023 Daniel H. Huson
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

package splitstree6.io.writers.distances;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import jloda.util.StringUtils;
import splitstree6.data.DistancesBlock;
import splitstree6.data.TaxaBlock;

import java.io.IOException;
import java.io.Writer;

/**
 * writes distances in text format
 * Daniel Huson, 11.2021
 */
public class PlainTextWriter extends DistancesWriterBase {
	public enum Format {Matrix, Vector}

	private final ObjectProperty<Format> optionFormat = new SimpleObjectProperty<>(this, "optionFormat", Format.Matrix);

	public PlainTextWriter() {
		setFileExtensions("tab", "txt");
	}

	@Override
	public void write(Writer w, TaxaBlock taxa, DistancesBlock distances) throws IOException {
		final var ntax = distances.getNtax();
		switch (optionFormat.get()) {
			case Matrix -> {
				w.write("# Distance matrix:\n");
				for (var i = 1; i <= ntax; i++) {
					for (var j = 1; j <= ntax; j++) {
						w.write(StringUtils.removeTrailingZerosAfterDot(String.format("%.8f", distances.get(i, j))) + "\t");
					}
					w.write("\n");
				}
				w.write("\n");
			}
			case Vector -> {

				//Export the distances as a matrix then as a column vector.
				w.write("Distance matrix as column vector (1,2),(1,3),..,(1,n),(2,3),... :\n");
				for (var i = 1; i <= ntax; i++) {
					for (var j = i + 1; j <= ntax; j++) {
						w.write(StringUtils.removeTrailingZerosAfterDot(String.format("%.8f", distances.get(i, j))) + "\n");
					}
				}
				w.write("\n");
				w.flush();
			}
		}
	}

	public ObjectProperty<Format> optionFormatProperty() {
		return optionFormat;
	}
}
