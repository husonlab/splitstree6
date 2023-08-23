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

package splitstree6.io.readers.trees;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import jloda.fx.window.NotificationManager;
import jloda.graph.Node;
import jloda.graph.NodeArray;
import jloda.phylo.NewickIO;
import jloda.phylo.PhyloTree;
import jloda.util.*;
import jloda.util.progress.ProgressListener;
import splitstree6.splits.TreesUtils;
import splitstree6.data.TaxaBlock;
import splitstree6.data.TreesBlock;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * reads trees in Newick format,
 * Daniel Huson, Daria Evseeva, 2017
 */
public class NewickReader extends TreesReader {
	public static final String[] extensions = {"tree", "tre", "trees", "new", "nwk", "treefile"};
	private final BooleanProperty optionConvertMultiLabeledTree = new SimpleBooleanProperty(false);

	public NewickReader() {
		setFileExtensions(extensions);
	}

	@Override
	public void read(ProgressListener progress, String inputFile, TaxaBlock taxa, TreesBlock treesBlock) throws IOException {
		try (var it = new FileLineIterator(inputFile)) {
			read(progress, it, taxa, treesBlock);
		}
	}

	public void read(ProgressListener progress, ICloseableIterator<String> it, TaxaBlock taxa, TreesBlock treesBlock) throws IOException {
		var lineno = 0;
		progress.setMaximum(it.getMaximumProgress());
		progress.setProgress(0);

		final var taxName2Id = new HashMap<String, Integer>(); // starts at 1
		final var taxonNamesFound = new HashSet<String>();
		final var orderedTaxonNames = new ArrayList<String>();

		final var parts = new ArrayList<String>();

		final var GENE_NAME_TAG = "&&NHX:GN=";

		treesBlock.clear();
		treesBlock.setReticulated(false);
		treesBlock.setPartial(false);
		treesBlock.setRooted(true);

		// read in the trees
		var newickIO = new NewickIO();
		newickIO.allowMultiLabeledNodes = false;

		while (it.hasNext()) {
			lineno++;
			// var line = StringUtils.removeComments(it.next(), '[', ']');
			var line = it.next();
			if (line.endsWith(";")) {
				final String treeLine;
				if (!parts.isEmpty()) {
					parts.add(line);
					treeLine = StringUtils.toString(parts, "");
					parts.clear();
				} else
					treeLine = line;
				final var tree = new PhyloTree();
				try {
					try (NodeArray<String> nodeCommentMap = tree.newNodeArray()) {
						newickIO.parseBracketNotation(tree, treeLine, true,
								s -> {
									if (s.startsWith(GENE_NAME_TAG))
										tree.setName(s.substring(GENE_NAME_TAG.length() + 1).trim());
								}, (v, s) -> {
									if (v != null && s != null) {
								if (s.startsWith(GENE_NAME_TAG))
									tree.setName(s.substring(GENE_NAME_TAG.length()).trim());
								else
									nodeCommentMap.put(v, s);
									}
								});
						if (newickIO.isInputHasMultiLabels())
							throw new IOException("Tree contains multiple copies of the same label");
						setupEdgeConfidenceFromComments(tree, nodeCommentMap);
					}
					//System.err.println(tree.toBracketString(false));
				} catch (IOException ex) {
					throw new IOExceptionWithLineNumber(lineno, ex);
				}

				if (TreesUtils.hasNumbersOnLeafNodes(tree)) {
					NotificationManager.showWarning("Leaf nodes have integer labels 'i', converting to t'i'");
					for (var v : tree.leaves()) {
						if (NumberUtils.isInteger(tree.getLabel(v))) {
								tree.setLabel(v, "t" + tree.getLabel(v));
							}
						}
					}

					final var labelList = getNodeLabels(tree, true);
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
								labelList.remove(z);
							}
							throw new IOExceptionWithLineNumber(lineno, "Name appears multiple times in tree: " + labelList.get(0));
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
							treesBlock.setPartial(true);
							for (var name : labelList) {
								if (!taxonNamesFound.contains(name)) {
									if (false)
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

				if (!treesBlock.isReticulated() && tree.edgeStream().anyMatch(tree::isReticulateEdge)) {
					treesBlock.setReticulated(true);
				}

				treesBlock.getTrees().add(tree);
				if (tree.getName() == null || tree.getName().isBlank())
					tree.setName("tree-" + treesBlock.size());

				progress.setProgress(it.getProgress());
			} else
					parts.add(line);
			}
		if (!parts.isEmpty())
				System.err.println("Ignoring trailing lines at end of file:\n" + StringUtils.abbreviateDotDotDot(StringUtils.toString(parts, "\n"), 400));
			taxa.addTaxaByNames(orderedTaxonNames);
	}

	private void setupEdgeConfidenceFromComments(PhyloTree tree, NodeArray<String> nodeCommentMap) {
		var hasNonNumericalComment = tree.nodeStream().filter(v -> v.getInDegree() == 1).map(nodeCommentMap::get).filter(Objects::nonNull).anyMatch(c -> !NumberUtils.isDouble(c));
		var hasNumericalComment = tree.nodeStream().filter(v -> v.getInDegree() == 1).map(nodeCommentMap::get).filter(Objects::nonNull).anyMatch(NumberUtils::isDouble);
		if (hasNumericalComment && !hasNonNumericalComment) {
			for (var v : tree.nodes()) {
				if (v.getInDegree() == 1) {
					var comment = nodeCommentMap.get(v);
					if (comment != null && NumberUtils.isDouble(comment)) {
						tree.setConfidence(v.getFirstInEdge(), NumberUtils.parseDouble(comment));
					}
				}
			}
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

	/**
	 * list node labels in pre-order
	 *
	 * @param ignoreInternalNumericalLabels if set, will ignore number labels on internal nodes
	 * @return list
	 */
	public static List<String> getNodeLabels(PhyloTree tree, boolean ignoreInternalNumericalLabels) {
		final var list = new ArrayList<String>();
		var queue = new LinkedList<Node>();
		queue.add(tree.getRoot());
		while (!queue.isEmpty()) {
			var w = queue.pop();
			var label = tree.getLabel(w);
			if (label != null && (w.isLeaf() || !(ignoreInternalNumericalLabels && NumberUtils.isDouble(label))))
				list.add(label);
			for (var e : w.outEdges()) {
				if (tree.okToDescendDownThisEdgeInTraversal(e))
					queue.add(e.getTarget());
			}
		}
		return list;
	}

	@Override
	public boolean accepts(String file) {
		if (!super.accepts(file))
			return false;
		else {
			String line = FileUtils.getFirstLineFromFileIgnoreEmptyLines(new File(file), "#", 20);
			return acceptsFirstLine(line);
		}
	}

	public boolean acceptsFirstLine(String text) {
		var line = StringUtils.getFirstLine(text);
		return line.startsWith("(") && !(line.contains("<") && line.contains(">"));
	}
}
