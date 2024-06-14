/*
 *  RenameTaxa.java Copyright (C) 2024 Daniel H. Huson
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

package splitstree6.xtra.more;

import jloda.phylo.NewickIO;
import jloda.phylo.PhyloTree;
import jloda.util.FileUtils;

import java.io.BufferedReader;
import java.io.IOException;

public class RenameTaxa {
	public static void main(String[] args) throws IOException {
		try (var reader = new BufferedReader(FileUtils.getReaderPossiblyZIPorGZIP(args.length < 1 ? "stdin" : args[0]));
			 var writer = FileUtils.getOutputWriterPossiblyZIPorGZIP(args.length < 2 ? "stdout" : args[1])) {
			var newickIO = new NewickIO();

			while (reader.ready()) {
				var line = reader.readLine();
				if (line.startsWith("#") || line.isBlank()) continue;

				var tree = new PhyloTree();
				newickIO.parseBracketNotation(tree, line, true);
				var count = 0;
				for (var v : tree.nodeStream().filter(v -> tree.getLabel(v) != null && !tree.getLabel(v).isBlank()).toList()) {
					tree.setLabel(v, "t" + (++count));
				}
				newickIO.write(tree, writer, new NewickIO.OutputFormat(tree.hasEdgeWeights(), tree.hasEdgeConfidences(), false, false, false));
				writer.write(";\n");
			}

		}
	}
}
