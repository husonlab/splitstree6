/*
 * NewickReader.java Copyright (C) 2023 Daniel H. Huson
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

package splitstree6.io.readers.splits;

import jloda.fx.window.NotificationManager;
import jloda.phylo.PhyloTree;
import jloda.util.*;
import jloda.util.progress.ProgressListener;
import splitstree6.algorithms.utils.SplitsUtilities;
import splitstree6.data.SplitsBlock;
import splitstree6.data.TaxaBlock;
import splitstree6.io.utils.SplitNewick;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.TreeMap;

/**
 * reads splits in Newick format,
 * Daniel Huson, 3.2023
 */
public class NewickReader extends SplitsReader {
	public static final String[] extensions = {"tree", "tre", "trees", "new", "nwk", "treefile"};

	public NewickReader() {
		setFileExtensions(extensions);
	}

	@Override
	public void read(ProgressListener progress, String inputFile, TaxaBlock taxa, SplitsBlock splitsBlock) throws IOException {
		try (var it = new FileLineIterator(inputFile)) {
			read(progress, it, taxa, splitsBlock);
		}
	}

	public void read(ProgressListener progress, ICloseableIterator<String> it, TaxaBlock taxa, SplitsBlock splitsBlock) throws IOException {
		var lineno = 0;
		progress.setMaximum(it.getMaximumProgress());
		progress.setProgress(0);

		final var parts = new ArrayList<String>();

		// read lines for first Newick expression
		while (it.hasNext()) {
			lineno++;
			final var line = StringUtils.removeComments(it.next(), '[', ']');
			parts.add(line);
			if (line.endsWith(";"))
				break;
		}
		var newick = StringUtils.toString(parts, "");
		if (newick.endsWith(";")) {
			final var tree = new PhyloTree();
			tree.allowMultiLabeledNodes = false;
			try {
				var taxonLabelMap = new TreeMap<Integer, String>();
				splitsBlock.getSplits().addAll(SplitNewick.parse(newick, null, taxonLabelMap));
				for (var name : taxonLabelMap.values()) {
					taxa.addTaxonByName(name);
				}
				SplitsUtilities.computeCycle(taxa.getNtax(), splitsBlock.getSplits());
			} catch (IOException ex) {
				throw new IOExceptionWithLineNumber(lineno, ex);
			}
		} else
			NotificationManager.showWarning("No Newick string found");
	}

	@Override
	public boolean accepts(String filename) {
		if (!super.accepts(filename))
			return false;
		else {
			var line = FileUtils.getFirstLineFromFileIgnoreEmptyLines(new File(filename), "#", 20);
			return line != null && line.startsWith("(");
		}
	}

	public boolean acceptsFirstLine(String text) {
		var line = StringUtils.getFirstLine(text);
		return line.startsWith("(");
	}
}
