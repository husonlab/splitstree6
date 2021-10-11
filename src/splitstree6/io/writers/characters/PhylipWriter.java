/*
 * PhylipCharactersExporter.java Copyright (C) 2021. Daniel H. Huson
 *
 * (Some code written by other authors, as named in code.)
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

package splitstree6.io.writers.characters;


import javafx.beans.property.BooleanProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleIntegerProperty;
import splitstree6.data.CharactersBlock;
import splitstree6.data.TaxaBlock;

import java.io.IOException;
import java.io.Writer;
import java.util.HashSet;
import java.util.Set;

public class PhylipWriter extends CharactersWriter {

	public PhylipWriter() {
		setFileExtensions("phylip", "phy");
	}

	private final BooleanProperty optionInterleaved = new SimpleBooleanProperty(false);
	private final BooleanProperty optionInterleavedMultiLabels = new SimpleBooleanProperty(false);
	private final IntegerProperty optionLineLength = new SimpleIntegerProperty(40);

	public void write(Writer w, TaxaBlock taxa, CharactersBlock characters) throws IOException {

		int ntax = taxa.getNtax();
		int nchar = characters.getNchar();
		w.write("\t" + ntax + "\t" + nchar + "\n");

		final String[] labels = computePhylipLabels(taxa);

		if (isOptionInterleaved()) {

			var lineLength = getOptionLineLength();

			int iterations;
			if (nchar % lineLength == 0)
				iterations = nchar / lineLength;
			else
				iterations = nchar / lineLength + 1;

			for (int i = 1; i <= iterations; i++) {
				int startIndex = lineLength * (i - 1) + 1;
				for (int t = 1; t <= ntax; t++) {
					StringBuilder sequence = new StringBuilder("");

					// set space after every 10 chars, but not in the beginning of line
					for (int j = startIndex; j <= lineLength * i && j <= nchar; j++) {
						if ((j - 1) % 10 == 0 && (j - 1) != 0 && j != startIndex)
							sequence.append(" ");
						sequence.append(characters.get(t, j));
					}

					if (i == 1 || isOptionInterleavedMultiLabels())
						w.write(get10charLabel(labels[t]) + sequence.toString().toUpperCase() + "\n");
					else
						w.write(sequence.toString().toUpperCase() + "\n");
				}
				w.write("\n");
			}
		} else {
			for (int t = 1; t <= ntax; t++) {
				StringBuilder sequence = new StringBuilder("");
				for (int j = 1; j <= nchar; j++) {
					if ((j - 1) % 10 == 0 && (j - 1) != 0) sequence.append(" "); // set space after every 10 chars
					sequence.append(characters.get(t, j));
				}
				w.write(get10charLabel(labels[t]) + sequence.toString().toUpperCase() + "\n");
			}
		}
	}

	public static String[] computePhylipLabels(TaxaBlock taxa) {
		final Set<String> set = new HashSet<>();
		String[] labels = new String[taxa.getNtax() + 1];
		for (int t = 1; t <= taxa.getNtax(); t++) {
			String label = taxa.getLabel(t);
			if (label.length() > 10)
				label = label.charAt(0) + "_" + label.substring(label.length() - 8);
			int count = 0;
			while (set.contains(label)) {
				label = String.format("%s:%02d", label.substring(0, 7), (++count));
			}
			set.add(label);
			labels[t] = label;
		}
		return labels;
	}

	private static String get10charLabel(String label) {
		if (label.length() >= 10)
			return label.substring(0, 10);
		else {
			return label + " ".repeat(10 - label.length());
		}
	}

	public boolean isOptionInterleaved() {
		return optionInterleaved.get();
	}

	public BooleanProperty optionInterleavedProperty() {
		return optionInterleaved;
	}

	public void setOptionInterleaved(boolean optionInterleaved) {
		this.optionInterleaved.set(optionInterleaved);
	}

	public boolean isOptionInterleavedMultiLabels() {
		return optionInterleavedMultiLabels.get();
	}

	public BooleanProperty optionInterleavedMultiLabelsProperty() {
		return optionInterleavedMultiLabels;
	}

	public void setOptionInterleavedMultiLabels(boolean optionInterleavedMultiLabels) {
		this.optionInterleavedMultiLabels.set(optionInterleavedMultiLabels);
	}

	public int getOptionLineLength() {
		return optionLineLength.get();
	}

	public IntegerProperty optionLineLengthProperty() {
		return optionLineLength;
	}

	public void setOptionLineLength(int optionLineLength) {
		this.optionLineLength.set(optionLineLength);
	}
}
