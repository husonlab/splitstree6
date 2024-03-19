/*
 * PhylipWriter.java Copyright (C) 2024 Daniel H. Huson
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
import jloda.fx.util.ProgramProperties;
import jloda.util.StringUtils;
import splitstree6.data.DistancesBlock;
import splitstree6.data.TaxaBlock;

import java.io.IOException;
import java.io.Writer;

import static splitstree6.io.writers.characters.PhylipWriter.getPhylipTaxonLabel;

/**
 * writes distances in Phylip format
 * Daniel Huson, 11.2021
 */
public class PhylipWriter extends DistancesWriterBase {
	private final BooleanProperty optionTriangular = new SimpleBooleanProperty(this, "optionTriangular");

	private final BooleanProperty optionTruncateLabels = new SimpleBooleanProperty(this, "optionTruncateLabels");


	public PhylipWriter() {
		setFileExtensions("dist", "dst", "phylip", "phy");
		ProgramProperties.track(optionTriangular, false);
		ProgramProperties.track(optionTruncateLabels, true);
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

		if (!optionTriangular.get()) {
			// System.err.println("standard");
			for (var i = 1; i <= distances.getDistances().length; i++) {
				var buf = new StringBuilder();
				for (int j = 1; j <= distances.getDistances()[i - 1].length; j++) {
					buf.append(StringUtils.removeTrailingZerosAfterDot("%.5f ", distances.get(i, j)));
				}
				w.write(getPhylipTaxonLabel(taxa.getLabel(i), optionTruncateLabels.get()));
				w.write(buf + "\n");
			}
		} else {
			//System.err.println("triangular");
			for (var i = 1; i <= distances.getDistances().length; i++) {
				var buf = new StringBuilder();
				for (var j = 1; j <= i - 1; j++) {
					buf.append(StringUtils.removeTrailingZerosAfterDot("%.5f ", distances.get(i, j)));
				}
				w.write(getPhylipTaxonLabel(taxa.getLabel(i), optionTruncateLabels.get()));
				w.write(buf + "\n");
			}
		}
	}

	public BooleanProperty optionTriangularProperty() {
		return optionTriangular;
	}

	public BooleanProperty optionTruncateLabelsProperty() {
		return optionTruncateLabels;
	}
}
