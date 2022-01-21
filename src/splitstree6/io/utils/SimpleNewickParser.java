/*
 *  SimpleNewickParser.java Copyright (C) 2022 Daniel H. Huson
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

package splitstree6.io.utils;

import jloda.graph.Edge;
import jloda.graph.Node;
import jloda.graph.NotOwnerException;
import jloda.phylo.PhyloTree;
import jloda.util.NumberUtils;
import jloda.util.StringUtils;

import java.io.IOException;
import java.util.Iterator;


/**
 * simple Newick parser that can't handle rooted networks or edge labels
 * Daniel Huson, 1.2018
 */
public class SimpleNewickParser {
	private static final String punctuationCharacters = "),;:";
	private boolean enforceLabelDoesNotStartWithADigit = false;

	private PhyloTree tree;
	private boolean hasWeights;

	/**
	 * parse a tree
	 */
	public PhyloTree parse(String line) throws IOException {
		this.tree = new PhyloTree();
		hasWeights = false;

		parseBracketNotationRecursively(0, null, 0, line);
		if (tree.getNumberOfNodes() > 0)
			tree.setRoot(tree.getFirstNode());

		if (!isHasWeights()) { // set all weights to 1
			for (Edge e : tree.edges()) {
				if (e.getSource().getInDegree() == 0 && e.getSource().getOutDegree() == 2 && !tree.getTaxa(e.getSource()).iterator().hasNext()) {
					tree.setWeight(e, 0.5);
				} else
					tree.setWeight(e, 1.0);
			}
		}

		return tree;
	}

	public PhyloTree getTree() {
		return tree;
	}

	public boolean isHasWeights() {
		return hasWeights;
	}

	/**
	 * recursively do the work
	 *
	 * @param depth distance from root
	 * @param v     parent node
	 * @param pos   current position in string
	 * @param str   string
	 * @return new current position
	 * @throws IOException
	 */
	private int parseBracketNotationRecursively(int depth, Node v, int pos, String str) throws IOException {
		try {
			for (pos = StringUtils.skipSpaces(str, pos); pos < str.length(); pos = StringUtils.skipSpaces(str, pos + 1)) {
				final Node w = tree.newNode();

				if (str.charAt(pos) == '(') {
					pos = parseBracketNotationRecursively(depth + 1, w, pos + 1, str);
					if (str.charAt(pos) != ')')
						throw new IOException("Expected ')' at position " + pos);
					pos = StringUtils.skipSpaces(str, pos + 1);
					while (pos < str.length() && punctuationCharacters.indexOf(str.charAt(pos)) == -1) {
						int i0 = pos;
						StringBuilder buf = new StringBuilder();
						boolean inQuotes = false;
						while (pos < str.length() && (inQuotes || punctuationCharacters.indexOf(str.charAt(pos)) == -1)) {
							if (str.charAt(pos) == '\'')
								inQuotes = !inQuotes;
							else
								buf.append(str.charAt(pos));
							pos++;
						}

						String label = buf.toString().trim();

						if (label.length() == 0)
							throw new IOException("Expected label at position " + i0);

						if (enforceLabelDoesNotStartWithADigit && w.getOutDegree() == 0 && !Character.isLetter(label.charAt(0))) {
							label = "T" + label;
						}
						tree.setLabel(w, label);
					}
				} else // everything to next to close-bracket, : or , is considered a label:
				{
					if (tree.getNumberOfNodes() == 1)
						throw new IOException("Expected '(' at position " + pos);
					int i0 = pos;
					final StringBuilder buf = new StringBuilder();
					boolean inQuotes = false;
					while (pos < str.length() && (inQuotes || punctuationCharacters.indexOf(str.charAt(pos)) == -1)) {
						if (str.charAt(pos) == '\'')
							inQuotes = !inQuotes;
						else
							buf.append(str.charAt(pos));
						pos++;
					}

					String label = buf.toString().trim();
					if (label.startsWith("'") && label.endsWith("'") && label.length() > 1)
						label = label.substring(1, label.length() - 1).trim(); // strip quotes

					if (label.length() == 0)
						throw new IOException("Expected label at position " + i0);

					if (enforceLabelDoesNotStartWithADigit && w.getOutDegree() == 0 && Character.isDigit(label.charAt(0))) {
						label = "T" + label;
					}

					tree.setLabel(w, label);
				}
				Edge e = null;
				if (v != null) {
					e = tree.newEdge(v, w);
				}

				// detect and read embedded bootstrap values:
				pos = StringUtils.skipSpaces(str, pos);

				// read edge weights

				if (pos < str.length() && str.charAt(pos) == ':') // edge weight is following
				{
					pos = StringUtils.skipSpaces(str, pos + 1);
					int i0 = pos;
					final StringBuilder buf = new StringBuilder();
					while (pos < str.length() && (punctuationCharacters.indexOf(str.charAt(pos)) == -1 && str.charAt(pos) != '['))
						buf.append(str.charAt(pos++));
					String number = buf.toString().trim();
					try {
						double weight = Math.max(0, Double.parseDouble(number));
						if (e != null)
							tree.setWeight(e, weight);
						if (!hasWeights)
							hasWeights = true;
					} catch (Exception ex) {
						throw new IOException("Expected number at position " + i0 + " (got: '" + number + "')");
					}
				}

				// now pos should be pointing to a ',' or a ')'
				if (pos >= str.length()) {
					if (depth == 0)
						return pos; // finished parsing tree
					else
						throw new IOException("Unexpected end of line");
				}
				if (str.charAt(pos) == ';' && depth == 0)
					return pos; // finished parsing tree
				else if (str.charAt(pos) == ')')
					return pos;
				else if (str.charAt(pos) != ',')
					throw new IOException("Unexpected '" + str.charAt(pos) + "' at position " + pos);
			}
		} catch (NotOwnerException ex) {
			throw new IOException(ex);
		}
		return -1;
	}

	public Iterable<String> labels() {
		return () -> new Iterator<>() {
			private Node v = tree.getFirstNode();

			{
				while (v != null && v.getOutDegree() > 0 && (tree.getLabel(v) == null || NumberUtils.isDouble(tree.getLabel(v))))
					v = v.getNext();
			}

			@Override
			public boolean hasNext() {
				return v != null;
			}

			@Override
			public String next() {
				final String result = (v != null ? tree.getLabel(v) : null);
				if (v != null)
					v = v.getNext();
				while (v != null && v.getOutDegree() > 0 && (tree.getLabel(v) == null || NumberUtils.isDouble(tree.getLabel(v))))
					v = v.getNext();
				return result;
			}
		};
	}


	public boolean isEnforceLabelDoesNotStartWithADigit() {
		return enforceLabelDoesNotStartWithADigit;
	}

	public void setEnforceLabelDoesNotStartWithADigit(boolean enforceLabelDoesNotStartWithADigit) {
		this.enforceLabelDoesNotStartWithADigit = enforceLabelDoesNotStartWithADigit;
	}
}
