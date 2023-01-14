/*
 * PhylipWriter.java Copyright (C) 2023 Daniel H. Huson
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
import javafx.beans.property.SimpleBooleanProperty;
import jloda.util.StringUtils;
import splitstree6.data.DistancesBlock;
import splitstree6.data.TaxaBlock;

import java.io.IOException;
import java.io.Writer;

/**
 * writes distances in Phylip format
 * Daniel Huson, 11.2021
 */
public class PhylipWriter extends DistancesWriterBase {
	private final BooleanProperty optionTriangular = new SimpleBooleanProperty(this, "optionTriangular", false);

	public PhylipWriter() {
		setFileExtensions("dist", "dst", "phylip", "phy");
	}

	@Override
	public void write(Writer w, TaxaBlock taxa, DistancesBlock distances) throws IOException {
		var ntax = taxa.getNtax();

		var maxLabelLength = taxa.getLabel(1).length();
		for (var i = 2; i <= ntax; i++) {
			if (taxa.getLabel(i).length() > maxLabelLength)
				maxLabelLength = taxa.getLabel(i).length();
		}

		w.write("\t" + ntax + "\n");

		if (!isOptionTriangular()) {
			System.err.println("standard");
			for (var i = 1; i <= distances.getDistances().length; i++) {
				var buf = new StringBuilder();
				for (int j = 1; j <= distances.getDistances()[i - 1].length; j++) {
					buf.append(StringUtils.removeTrailingZerosAfterDot("%.5f ", distances.get(i, j)));
				}
				if (taxa.getLabel(i).length() >= 10)
					w.write(taxa.getLabel(i).substring(0, 10));
				else {
					w.write(taxa.getLabel(i));
					for (var k = 0; k < 10 - taxa.getLabel(i).length(); k++) {
						w.write(" ");
					}
				}
				w.write("\t" + buf + "\n");
			}
		} else {
			System.err.println("triangular");
			w.write(taxa.getLabel(1) + "\n");
			for (var i = 2; i <= distances.getDistances().length; i++) {
				var buf = new StringBuilder();
				for (var j = 1; j <= i - 1; j++) {
					buf.append(StringUtils.removeTrailingZerosAfterDot("%.5f ", distances.get(i, j)));
				}
				if (taxa.getLabel(i).length() >= 10)
					w.write(taxa.getLabel(i).substring(0, 10));
				else {
					w.write(taxa.getLabel(i));
					for (int k = 0; k < 10 - taxa.getLabel(i).length(); k++) {
						w.write(" ");
					}
				}
				w.write("\t" + buf + "\n");
			}
		}
	}

	public boolean isOptionTriangular() {
		return optionTriangular.get();
	}

	public BooleanProperty optionTriangularProperty() {
		return optionTriangular;
	}

	public void setOptionTriangular(boolean optionTriangular) {
		this.optionTriangular.set(optionTriangular);
	}
}
