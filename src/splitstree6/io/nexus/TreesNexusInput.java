/*
 * TreesNexusInput.java Copyright (C) 2022 Daniel H. Huson
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

package splitstree6.io.nexus;

import jloda.phylo.PhyloTree;
import jloda.util.IOExceptionWithLineNumber;
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
			[PROPERTIES [PARTIALTREES={YES|NO}] [ROOTED={YES|NO}] [RETICULATED={YES|NO}];]
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

			if (tokens.size() != 0)
				throw new IOExceptionWithLineNumber(np.lineno(), "'" + tokens + "' unexpected in PROPERTIES");
		}

		final var taxName2Id = new HashMap<String, Integer>();
		final var taxonNamesFound = new ArrayList<String>();
		var haveSetKnownTaxonNames = false;

		// setup translator:
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
			if (taxaBlock.getTaxa().size() > 0) {
				for (var t = 1; t <= taxaBlock.getNtax(); t++) {
					final var taxonLabel = taxaBlock.get(t).getName();
					taxonNamesFound.add(taxonLabel);
					taxName2Id.put(taxonLabel, t);
				}
				haveSetKnownTaxonNames = true;
			}
		}

		final var knownTaxonNames = new HashSet<>(taxonNamesFound);

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

			if (name.length() == 0)
				name = "t" + treeNumber;

			np.matchIgnoreCase("=");
			np.getComment(); // clears comments

			final StringBuilder buf = new StringBuilder();

			final var tokensToCome = np.getTokensRespectCase(null, ";");
			for (var s : tokensToCome) {
				buf.append(s);
			}

			final boolean isRooted; // In SplitsTree6 we ignore this because trees are now always rooted
			if (rootedExplicitySet) {
			} else {
				String comment = np.getComment();
			}

			// final PhyloTree tree = PhyloTree.valueOf(buf.toString(), isRooted);
			final var tree = new PhyloTree();
			tree.parseBracketNotation(buf.toString(), true);

			if (translator != null)
				tree.changeLabels(translator);

			for (var v : tree.nodes()) {
				final var label = tree.getLabel(v);
				if (label != null && label.length() > 0) {
					if (!knownTaxonNames.contains(label)) {
						if (haveSetKnownTaxonNames) {
							System.err.println("Tree '" + name + "' contains unknown taxon: " + label);
						} else {
							knownTaxonNames.add(label);
							taxonNamesFound.add(label);
							taxName2Id.put(label, taxonNamesFound.size());
							tree.addTaxon(v, taxName2Id.get(label));
						}
					} else
						tree.addTaxon(v, taxName2Id.get(label));
					//System.err.println(v+" -> "+label+" -> "+Basic.toString(tree.getTaxa(v)," "));
				}
			}
			tree.setName(name);
			treesBlock.getTrees().add(tree);
			treeNumber++;
		}

		np.matchEndBlock();
		return taxonNamesFound;
	}
}
