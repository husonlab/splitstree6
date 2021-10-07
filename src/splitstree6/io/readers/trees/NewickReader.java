package splitstree6.io.readers.trees;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import jloda.phylo.PhyloTree;
import jloda.util.*;
import splitstree6.data.TaxaBlock;
import splitstree6.data.TreesBlock;
import splitstree6.io.utils.SimpleNewickParser;
import splitstree6.utils.TreesUtilities;

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
		setFileExtensions("tree", "tre", "new", "nwk", "treefile");
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

			var newickParser = new SimpleNewickParser();
			newickParser.setEnforceLabelDoesNotStartWithADigit(true);
			var partial = false;
			final var parts = new ArrayList<String>();

			// read in the trees
			while (it.hasNext()) {
				lineno++;
				final var line = Basic.removeComments(it.next(), '[', ']');
				if (line.endsWith(";")) {
					final String treeLine;
					if (parts.size() > 0) {
						parts.add(line);
						treeLine = Basic.toString(parts, "");
						parts.clear();
					} else
						treeLine = line;
					final PhyloTree tree;
					try {
						tree = newickParser.parse(treeLine);
					} catch (IOException ex) {
						throw new IOExceptionWithLineNumber(lineno, ex);
					}
					if (TreesUtilities.hasNumbersOnLeafNodes(tree)) {
						throw new IOExceptionWithLineNumber(lineno, "Leaf labels must not be numbers");
					}
					if (TreesUtilities.hasNumbersOnInternalNodes(tree)) {
						TreesUtilities.changeNumbersOnInternalNodesToEdgeConfidencies(tree);
					}
					final var leafLabelList = IteratorUtils.asList(newickParser.labels());
					final var leafLabelSet = new HashSet<String>(leafLabelList);
					final var multiLabeled = (leafLabelSet.size() < leafLabelList.size());

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
							for (var z : leafLabelSet) {
								leafLabelList.remove(z);
							}
							throw new IOExceptionWithLineNumber(lineno, "Name appears multiple times in tree:" + leafLabelList.get(0));
						}
					}

					if (taxonNamesFound.size() == 0) {
						for (var name : newickParser.labels()) {
							taxonNamesFound.add(name);
							orderedTaxonNames.add(name);
							taxName2Id.put(name, orderedTaxonNames.size());
						}
					} else {
						if (!taxonNamesFound.equals(IteratorUtils.asSet(newickParser.labels()))) {
							partial = true;
							for (var name : newickParser.labels()) {
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
				System.err.println("Ignoring trailing lines at end of file:\n" + Basic.abbreviateDotDotDot(Basic.toString(parts, "\n"), 400));
			taxa.addTaxaByNames(orderedTaxonNames);
			trees.setPartial(partial);
			trees.setRooted(true);
		}
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
}
