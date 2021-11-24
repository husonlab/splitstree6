/*
 *  NewickReader.java Copyright (C) 2021 Daniel H. Huson
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

package splitstree6.io.readers.trees;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import jloda.phylo.PhyloTree;
import jloda.util.*;
import jloda.util.progress.ProgressListener;
import splitstree6.algorithms.utils.TreesUtilities;
import splitstree6.data.TaxaBlock;
import splitstree6.data.TreesBlock;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

/**
 * reads trees in Newick format,
 * Daniel Huson, Daria Evseeva, 2017
 */
public class NewickReader extends TreesReader {
	private final BooleanProperty optionConvertMultiLabeledTree = new SimpleBooleanProperty(false);

	public NewickReader() {
		setFileExtensions("tree", "tre", "trees", "new", "nwk", "treefile");
	}

	@Override
	public void read(ProgressListener progress, String inputFile, TaxaBlock taxa, TreesBlock trees) throws IOException {
		var lineno = 0;
		try (var it = new FileLineIterator(inputFile)) {
			progress.setMaximum(it.getMaximumProgress());
			progress.setProgress(0);

			final var taxName2Id = new HashMap<String, Integer>(); // starts at 1
			final var taxonNamesFound = new HashSet<String>();
			final ArrayList<String> orderedTaxonNames = new ArrayList<>();

			var partial = false;
			final var parts = new ArrayList<String>();

			// read in the trees
			while (it.hasNext()) {
				lineno++;
				final var line = StringUtils.removeComments(it.next(), '[', ']');
				if (line.endsWith(";")) {
					final String treeLine;
					if (parts.size() > 0) {
						parts.add(line);
						treeLine = StringUtils.toString(parts, "");
						parts.clear();
					} else
						treeLine = line;
					final var tree = new PhyloTree();
					try {
						tree.parseBracketNotation(treeLine, true);
					} catch (IOException ex) {
						throw new IOExceptionWithLineNumber(lineno, ex);
					}

					if (TreesUtilities.hasNumbersOnLeafNodes(tree)) {
						throw new IOExceptionWithLineNumber(lineno, "Leaf labels must not be numbers");
					}
					if (TreesUtilities.hasNumbersOnInternalNodes(tree)) {
						TreesUtilities.changeNumbersOnInternalNodesToEdgeConfidencies(tree);
					}
					final var labelList = tree.listNodeLabels(true);
					final var labelSet = new HashSet<>(labelList);
					final var multiLabeled = (labelSet.size() < labelList.size());

					if (multiLabeled) {
						if (isOptionConvertMultiLabeledTree()) {
							final var seen = new HashSet<String>();
							for (var v : tree.nodes()) {
								var label = tree.getLabel(v);
								if (label != null) {
									var count = 1;
									while (seen.contains(label)) {
										label = tree.getLabel(v) + "-" + (++count);
									}
									if (count > 1)
										tree.setLabel(v, label);
									seen.add(label);
								}
							}
						} else {
							for (var z : labelSet) {
								labelSet.remove(z);
							}
							throw new IOExceptionWithLineNumber(lineno, "Name appears multiple times in tree:" + labelList.get(0));
						}
					}

					if (taxonNamesFound.size() == 0) {
						for (var name : labelList) {
							taxonNamesFound.add(name);
							orderedTaxonNames.add(name);
							taxName2Id.put(name, orderedTaxonNames.size());
						}
					} else {
						if (!taxonNamesFound.equals(IteratorUtils.asSet(labelList))) {
							partial = true;
							for (var name : labelList) {
								if (!taxonNamesFound.contains(name)) {
									System.err.println("Additional taxon name: " + name);
									taxonNamesFound.add(name);
									orderedTaxonNames.add(name);
									taxName2Id.put(name, orderedTaxonNames.size());
								}
							}
						}
					}
					for (var v : tree.nodes()) {
						final var label = tree.getLabel(v);
						if (label != null && label.length() > 0) {
							if (taxonNamesFound.contains(label)) { // need to check that this is a taxon name, could also be a number placed on the root...
								tree.addTaxon(v, taxName2Id.get(label));
							}
						}
					}

					trees.getTrees().add(tree);
					tree.setName("tree-" + trees.size());

					progress.setProgress(it.getProgress());
				} else
					parts.add(line);
			}
			if (parts.size() > 0)
				System.err.println("Ignoring trailing lines at end of file:\n" + StringUtils.abbreviateDotDotDot(StringUtils.toString(parts, "\n"), 400));
			taxa.addTaxaByNames(orderedTaxonNames);
			trees.setPartial(partial);
		}
		trees.setRooted(true);
	}

	public boolean isOptionConvertMultiLabeledTree() {
		return optionConvertMultiLabeledTree.get();
	}

	public BooleanProperty optionConvertMultiLabeledTreeProperty() {
		return optionConvertMultiLabeledTree;
	}

	public void setOptionConvertMultiLabeledTree(boolean optionConvertMultiLabeledTree) {
		this.optionConvertMultiLabeledTree.set(optionConvertMultiLabeledTree);
	}

	@Override
	public boolean accepts(String file) {
		if (!super.accepts(file))
			return false;
		else {
			String line = FileUtils.getFirstLineFromFileIgnoreEmptyLines(new File(file), "#", 20);
			return line != null && line.startsWith("(");
		}
	}
}
