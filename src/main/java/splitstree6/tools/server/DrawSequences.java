/*
 * DrawNewick.java Copyright (C) 2024 Daniel H. Huson
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

package splitstree6.tools.server;

import jloda.util.progress.ProgressSilent;
import splitstree6.algorithms.characters.characters2distances.HammingDistance;
import splitstree6.algorithms.characters.characters2distances.LogDet;
import splitstree6.data.CharactersBlock;
import splitstree6.data.DistancesBlock;
import splitstree6.data.TaxaBlock;
import splitstree6.io.readers.characters.FastAReader;
import splitstree6.io.readers.characters.NexusReader;
import splitstree6.io.readers.characters.PhylipReader;
import splitstree6.io.writers.distances.PhylipWriter;

import java.io.IOException;
import java.io.StringWriter;
import java.util.List;

import static jloda.util.FileLineIterator.PREFIX_TO_INDICATE_TO_PARSE_FILENAME_STRING;

/**
 * handles draw_sequences requests
 * Daniel Huson, 12/2024
 */
public class DrawSequences {
	public static String apply(String sequences, String output, String transform, String algorithm, String layout, double width, double height) throws IOException {
		var input = PREFIX_TO_INDICATE_TO_PARSE_FILENAME_STRING + sequences;

		Utilities.checkValue("output", output, List.of("coordinates", "newick"));

		var taxaBlock = new TaxaBlock();
		var charactersBlock = new CharactersBlock();

		for (var reader : List.of(new FastAReader(), new PhylipReader(), new NexusReader())) {
			if (reader.acceptsFirstLine(sequences)) {
				reader.read(new ProgressSilent(), input, taxaBlock, charactersBlock);
				break;
			}
		}
		if (taxaBlock.size() == 0)
			throw new IOException("Failed to read sequences");

		return switch (transform) {
			case "hamming" -> {
				var distances = new DistancesBlock();
				(new HammingDistance()).compute(new ProgressSilent(), taxaBlock, charactersBlock, distances);
				var phylipWriter = new PhylipWriter();
				phylipWriter.optionTruncateLabelsProperty().set(false);
				var matrix = new StringWriter();
				phylipWriter.write(matrix, taxaBlock, distances);
				yield DrawDistances.apply(matrix.toString(), output, algorithm, layout, width, height);
			}
			case "logdet" -> {
				var distances = new DistancesBlock();
				(new LogDet()).compute(new ProgressSilent(), taxaBlock, charactersBlock, distances);
				var phylipWriter = new PhylipWriter();
				phylipWriter.optionTruncateLabelsProperty().set(false);
				var matrix = new StringWriter();
				phylipWriter.write(matrix, taxaBlock, distances);
				yield DrawDistances.apply(matrix.toString(), output, algorithm, layout, width, height);
			}
			default -> throw new IOException("Unsupported transform: " + transform);
		};
	}
}
