/*
 * PlainTextWriter.java Copyright (C) 2024 Daniel H. Huson
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

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
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
	public enum Format {Matrix, OrderedPairs, AllPairs}

	private final ObjectProperty<Format> optionFormat = new SimpleObjectProperty<>(this, "optionFormat", Format.Matrix);
	private final BooleanProperty optionLabels = new SimpleBooleanProperty(this, "optionLabels", false);

	public PlainTextWriter() {
		setFileExtensions("tab", "txt");
	}

	@Override
	public void write(Writer w, TaxaBlock taxa, DistancesBlock distances) throws IOException {
		final var ntax = distances.getNtax();
		switch (optionFormat.get()) {
			case Matrix -> {
				w.write("# Distance matrix:\n");
				if (optionLabels.get()) {
					for (var i = 1; i <= ntax; i++) {
						if (i > 1)
							w.write("\t");
						w.write(taxa.get(i).getName());
					}
				}
				for (var i = 1; i <= ntax; i++) {
					for (var j = 1; j <= ntax; j++) {
						if (j > 1)
							w.write("\t");
						w.write(StringUtils.removeTrailingZerosAfterDot(String.format("%.8f", distances.get(i, j))));
					}
					w.write("\n");
				}
				w.write("\n");
			}
			case OrderedPairs -> {
				w.write("# Ordered pairs of distances D(1,2), D(1,3), ..,D(1,n), D(2,3), ... :\n");
				for (var i = 1; i <= ntax; i++) {
					for (var j = i + 1; j <= ntax; j++) {
						if (optionLabels.get()) {
							w.write(taxa.get(i).getName() + "\t" + taxa.get(j) + "\t");
						}
						w.write(StringUtils.removeTrailingZerosAfterDot(String.format("%.8f", distances.get(i, j))) + "\n");
					}
				}
				w.write("\n");
				w.flush();
			}
			case AllPairs -> {
				w.write("# All pairs of distances D(1,1), D(1,2), ..., D(2,1), D(2,2), D(2,3), ...\n");
				for (var i = 1; i <= ntax; i++) {
					for (var j = 1; j <= ntax; j++) {
						if (optionLabels.get()) {
							w.write(taxa.get(i).getName() + "\t" + taxa.get(j) + "\t");
						}
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

	public BooleanProperty optionLabelsProperty() {
		return optionLabels;
	}
}
