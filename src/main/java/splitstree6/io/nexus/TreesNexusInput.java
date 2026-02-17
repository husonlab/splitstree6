/*
 *  TreesNexusInput.java Copyright (C) 2024 Daniel H. Huson
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

package splitstree6.io.nexus;

import jloda.phylo.CommentData;
import jloda.phylo.NewickIO;
import jloda.phylo.PhyloTree;
import jloda.util.IOExceptionWithLineNumber;
import jloda.util.NumberUtils;
import jloda.util.parse.NexusStreamParser;
import splitstree6.data.TaxaBlock;
import splitstree6.data.TreesBlock;

import java.io.IOException;
import java.util.*;

/**
 * nexus input parser
 * Daniel Huson, 2.2018
 */
public class TreesNexusInput extends NexusIOBase implements INexusInput<TreesBlock> {
	public static final String SYNTAX = """
			BEGIN TREES;
			    [TITLE {title};]
			    [LINK {type} = {title};]
			[PROPERTIES [PartialTrees={YES|NO}] [Rooted={YES|NO}] [Reticulated={YES|NO}];]
			[TRANSLATE
			    nodeLabel1 taxon1,
			    nodeLabel2 taxon2,
			        ...
			    nodeLabelN taxonN
			;]
			[TREE name1 = tree1-in-Newick-format;]
			[TREE name2 = tree2-in-Newick-format;]
			    ...
			[TREE nameM = treeM-in-Newick-format;]
			END;
			""";

	public static final String DESCRIPTION = """
			This block maintains a list of trees. These can be rooted or unrooted
			phylogenetic trees, or rooted phylogenetic networks. Trees can
			partial in the sense that they need to contain all taxa.
			""";

	@Override
	public String getSyntax() {
		return SYNTAX;
	}

	/**
	 * parse a trees block
	 */
	@Override
	public List<String> parse(NexusStreamParser np, TaxaBlock taxaBlock, TreesBlock treesBlock) throws IOException {
		treesBlock.clear();

		final var format = treesBlock.getFormat();

		var rootedExplicitySet = false;

		np.matchBeginBlock("TREES");
		parseTitleAndLink(np);

		if (np.peekMatchIgnoreCase("PROPERTIES")) {
			final var tokens = np.getTokensLowerCase("PROPERTIES", ";");
			treesBlock.setPartial(np.findIgnoreCase(tokens, "partialTrees=no", false, treesBlock.isPartial()));
			treesBlock.setPartial(np.findIgnoreCase(tokens, "partialTrees=yes", true, treesBlock.isPartial()));
			// legacy:
			treesBlock.setPartial(np.findIgnoreCase(tokens, "no partialTrees", false, treesBlock.isPartial()));
			treesBlock.setPartial(np.findIgnoreCase(tokens, "partialTrees", true, treesBlock.isPartial()));

			if (np.findIgnoreCase(tokens, "rooted=no", true, false)) {
				treesBlock.setRooted(false);
				rootedExplicitySet = true;
			}
			if (np.findIgnoreCase(tokens, "rooted=yes", true, false)) {
				treesBlock.setRooted(true);
				rootedExplicitySet = true;
			}

			treesBlock.setReticulated(np.findIgnoreCase(tokens, "reticulated=no", false, treesBlock.isReticulated()));
			treesBlock.setReticulated(np.findIgnoreCase(tokens, "reticulated=yes", true, treesBlock.isReticulated()));

			// legacy:
			if (np.findIgnoreCase(tokens, "no rooted", true, false)) {
				treesBlock.setRooted(false);
				rootedExplicitySet = true;
			}
			if (np.findIgnoreCase(tokens, "rooted", true, false)) {
				treesBlock.setRooted(true);
				rootedExplicitySet = true;
			}

			if (!tokens.isEmpty())
				throw new IOExceptionWithLineNumber(np.lineno(), "'" + tokens + "' unexpected in PROPERTIES");
		}

		final var taxName2Id = new HashMap<String, Integer>();
		final var taxonNamesFound = new ArrayList<String>();
		var haveSetKnownTaxonNames = false;

		// create translator:
		final Map<String, String> translator; // maps node labels to taxon labels

		if (np.peekMatchIgnoreCase("TRANSLATE")) {
			translator = new HashMap<>();
			format.setOptionTranslate(true);
			np.matchIgnoreCase("TRANSLATE");
			while (!np.peekMatchIgnoreCase(";")) {
				final var nodeLabel = np.getWordRespectCase();
				final var taxonLabel = np.getWordRespectCase();
				taxonNamesFound.add(taxonLabel);
				taxName2Id.put(taxonLabel, taxonNamesFound.size());
				translator.put(nodeLabel, taxonLabel);

				if (!np.peekMatchIgnoreCase(";"))
					np.matchIgnoreCase(",");
			}
			np.matchIgnoreCase(";");
			haveSetKnownTaxonNames = true;
		} else {
			translator = null;
			format.setOptionTranslate(false);
			if (!taxaBlock.getTaxa().isEmpty()) {
				for (var t = 1; t <= taxaBlock.getNtax(); t++) {
					final var taxonLabel = taxaBlock.get(t).getName();
					taxonNamesFound.add(taxonLabel);
					taxName2Id.put(taxonLabel, t);
				}
				haveSetKnownTaxonNames = true;
			}
		}

		final var knownTaxonNames = new HashSet<>(taxonNamesFound);

		var newickIO = new NewickIO();
		newickIO.allowMultiLabeledNodes = true;
		newickIO.setNewickNodeCommentConsumer(CommentData.createDataNodeConsumer());


		int treeNumber = 1;
		while (np.peekMatchIgnoreCase("tree")) {
			np.matchIgnoreCase("tree");
			if (np.peekMatchRespectCase("*"))
				np.matchRespectCase("*"); // don't know why PAUP puts this star in the file....

			var name = np.getWordRespectCase();
			name = name.replaceAll("\\s+", "_");
			name = name.replaceAll("[:;,]+", ".");
			name = name.replaceAll("\\[", "(");
			name = name.replaceAll("]", ")");
			name = name.trim();

			if (name.isEmpty())
				name = "t" + treeNumber;

			np.matchIgnoreCase("=");
			np.popComments(); // clears comments

			final StringBuilder buf = new StringBuilder();

			{
				var squareBracketsComments = np.isSquareBracketsSurroundComments();
				np.setSquareBracketsSurroundComments(false);
				try {
					final var tokensToCome = np.getTokensRespectCase(null, ";");
					for (var s : tokensToCome) {
						buf.append(s);
					}
				} finally {
					np.setSquareBracketsSurroundComments(squareBracketsComments);
				}
			}

			final boolean isRooted; // In SplitsTree6 we ignore this because trees are now always rooted
			if (rootedExplicitySet) {
			} else {
				String comment = np.popComments();
			}

			// final PhyloTree tree = PhyloTree.valueOf(buf.toString(), isRooted);
			final var tree = new PhyloTree();


			{
				var newickString = buf.toString();
				newickIO.parseBracketNotation(tree, newickString.endsWith(";") ? newickString : newickString + ";", true);
			}

			if (translator != null)
				tree.changeLabels(translator, true);

			var taxonCount = 0;
			for (var v : tree.nodes()) {
				final var label = tree.getLabel(v);
				if (label != null && !label.isBlank()) {
					if (NumberUtils.isDouble(label)) {
						if (v.isLeaf())
							throw new IOExceptionWithLineNumber(np.lineno(), "Leaf labels must not be numbers");
						else
							continue;
					}
					if (!knownTaxonNames.contains(label)) {
						if (haveSetKnownTaxonNames) {
							System.err.println("Tree '" + name + "' contains unknown taxon: " + label);
						} else {
							knownTaxonNames.add(label);
							taxonNamesFound.add(label);
							taxName2Id.put(label, taxonNamesFound.size());
							tree.addTaxon(v, taxName2Id.get(label));
							taxonCount++;
						}
					} else {
						tree.addTaxon(v, taxName2Id.get(label));
						taxonCount++;
					}
					//System.err.println(v+" -> "+label+" -> "+Basic.toString(tree.getTaxa(v)," "));
				}
			}
			tree.setName(name);
			treesBlock.getTrees().add(tree);
			treeNumber++;
			if (!treesBlock.isPartial() && (taxonCount < taxName2Id.size() || (taxaBlock.getNtax() > 0 && taxonCount < taxaBlock.getNtax()))) {
				treesBlock.setPartial(true);
			}
		}

		np.matchEndBlock();
		return taxonNamesFound;
	}
}
