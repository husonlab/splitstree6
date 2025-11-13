/*
 *  QuasiMedianBase.java Copyright (C) 2024 Daniel H. Huson
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

package splitstree6.algorithms.characters.characters2network;

import jloda.phylo.PhyloGraph;
import jloda.util.Basic;
import jloda.util.CanceledException;
import jloda.util.StringUtils;
import jloda.util.progress.ProgressListener;
import splitstree6.data.CharactersBlock;
import splitstree6.data.NetworkBlock;
import splitstree6.data.TaxaBlock;

import java.util.*;

/**
 * base class for algorithms that produce quasi-median-type networks
 * <p/>
 * huson 10.2009
 */
public abstract class QuasiMedianBase {

	/**
	 * Applies the method to the given data
	 *
	 * @param taxaBlock            the taxa
	 * @param charactersBlock the characters
	 */
	public void apply(ProgressListener progress, TaxaBlock taxaBlock, CharactersBlock charactersBlock, NetworkBlock networkBlock) throws CanceledException {
		progress.setSubtask(Basic.getShortName(getClass()));
		progress.setProgress(0);
		progress.setMaximum(100);    //initialize maximum progress

		final var ntax = taxaBlock.getNtax();
		final var nchar = charactersBlock.getNchar();

		final var characters = getCharacters(charactersBlock);
		final var characterLabels = getCharacterLabels(charactersBlock);

		final var orig2CondensedPos = new int[nchar + 1];
		final var orig2CondensedTaxa = new int[ntax + 1];

		final Translator translator = new Translator(); // translates between original character states and condensed ones

		// NOTE: in condensedCharacters we count taxa and positions starting from 0 (not 1, as otherwise in SplitsTree)
		final var condensedCharacters = condenseCharacters(ntax, nchar, characters, orig2CondensedPos, orig2CondensedTaxa, translator);

		final var condensed2OrigPos = invert(orig2CondensedPos);

		final var weights = computeWeights(condensedCharacters[1].length(), orig2CondensedPos);

		if (false) {
			System.err.println("Translator:\n" + translator);

			System.err.println("Condensed characters:");
			for (String condensedCharacter1 : condensedCharacters) {
				System.err.println(condensedCharacter1);
			}

			System.err.println("uncondensed characters:");
			for (String condensedCharacter : condensedCharacters) {
				System.err.println(expandCondensed(condensedCharacter, orig2CondensedPos, translator));
			}

			System.err.println("Weights:");
			for (double weight : weights) {
				System.err.print(" " + weight);
			}
			System.err.println();
			System.err.println("Condensed to orig:");
			for (int t = 0; t < condensed2OrigPos.length; t++) {
				System.err.println(t + ": " + StringUtils.toString(condensed2OrigPos[t]));
			}
		}

		final var condensedInputSet = new TreeSet<>(Arrays.asList(condensedCharacters));

		final var graph = networkBlock.getGraph();
		graph.clear();

		computeGraph(progress, condensedInputSet, weights, graph);


		final var originalNodes = graph.getNodesAsList();
		for (var v : originalNodes) {
			graph.setLabel(v, null);
			var condensed = (String) v.getInfo();
			var full = expandCondensed(condensed, orig2CondensedPos, translator);
			networkBlock.getNodeData(v).put(NetworkBlock.NODE_STATES_KEY, full);

			if (condensedInputSet.contains(condensed)) {
				var first = true;
				for (var t = 1; t <= taxaBlock.getNtax(); t++) {
					var o = orig2CondensedTaxa[t];
					if (condensedCharacters[o].equals(condensed)) {
						if (first) {
							graph.addTaxon(v, t);
							graph.setLabel(v, taxaBlock.getLabel(t));
							first = false;
						} else {
							var w = graph.newNode();
							graph.addTaxon(w, t);
							graph.setLabel(w, taxaBlock.getLabel(t));
							graph.newEdge(v, w);
							networkBlock.getNodeData(w).put(NetworkBlock.NODE_STATES_KEY, full);
						}
					}
				}
			}
		}

		for (var e : graph.edges()) {
			if (e.getSource().getInfo() instanceof String conA && e.getTarget().getInfo() instanceof String conB) {
				var label = computeEdgeLabel(characterLabels, conA, conB, orig2CondensedPos, translator);
				networkBlock.getEdgeData(e).put(NetworkBlock.EDGE_SITES_KEY, label);
			}
		}
		networkBlock.setNetworkType(NetworkBlock.Type.HaplotypeNetwork);
	}

	/**
	 * compute the matrix of unmasked characters
	 *
	 * @return unmasked characters
	 */
	private char[][] getCharacters(CharactersBlock chars) {
		List<Character>[] list = (List<Character>[]) new List<?>[chars.getNtax() + 1];
		for (int c = 1; c <= chars.getNchar(); c++) {
			char majorityState = 0;
			for (int t = 1; t <= chars.getNtax(); t++) {
				char ch = chars.get(t, c);
				if (list[t] == null)
					list[t] = new ArrayList<Character>();
				if (ch == chars.getGapCharacter() || ch == chars.getMissingCharacter()) {
					if (majorityState == 0)
						majorityState = determineMajorityState(chars, c);
					ch = majorityState;
				}
				list[t].add(ch);
			}
		}
		var characters = new char[chars.getNtax() + 1][list[1].size() + 1];
		for (var t = 1; t <= chars.getNtax(); t++) {
			int count = 0;
			for (var ch : list[t]) {
				characters[t][++count] = ch;
			}
		}
		return characters;
	}

	/**
	 * determines the majority state for the given position
	 *
	 * @return majority state
	 */
	private char determineMajorityState(CharactersBlock chars, int c) {
		BitSet states = new BitSet();
		int[] count = new int[256];

		for (int t = 1; t <= chars.getNtax(); t++) {
			char ch = chars.get(t, c);
			states.set(ch);
			count[ch]++;
		}

		int best = 0;
		for (int ch = states.nextSetBit(0); ch != -1; ch = states.nextSetBit(ch + 1)) {
			if (count[ch] > count[best])
				best = ch;
		}
		return (char) best;
	}

	/**
	 * all character labels
	 *
	 * @return character labels
	 */
	private String[] getCharacterLabels(CharactersBlock chars) {
		final ArrayList<String> list = new ArrayList<>(chars.getNchar());
		for (int c = 1; c <= chars.getNchar(); c++) {
			String label = null;
			if (chars.getCharLabeler() != null)
				label = chars.getCharLabeler().get(c);
			if (label == null)
				label = "" + c;
			list.add(label);
		}
		String[] labels = new String[list.size() + 1];
		int count = 0;
		for (String a : list) {
			labels[++count] = a;
		}
		return labels;
	}


	/**
	 * computes the actual graph
	 */
	public abstract void computeGraph(ProgressListener progressListener, Set<String> inputSequences, double[] weights, PhyloGraph graph) throws CanceledException;

	/**
	 * computes all original positions at which the two sequences differ in display coordinates 1--length
	 *
	 * @return positions at which orig sequences differ
	 */
	private String computeEdgeLabel(String[] labels, String conA, String conB, int[] orig2CondensedPos, Translator translator) {
		var buf = new StringBuilder();

		var seqA = expandCondensed(conA, orig2CondensedPos, translator);
		var seqB = expandCondensed(conB, orig2CondensedPos, translator);

		var first = true;
		for (var i = 0; i < seqA.length(); i++) {
			if (seqA.charAt(i) != seqB.charAt(i)) {
				if (first)
					first = false;
				else
					buf.append(",");
				String label = labels[i + 1];
				if (label == null)
					label = "" + (i + 1);
				buf.append(label);
			}
		}
		return buf.toString();
	}

	/**
	 * gets the differences in display coordinates 1--length
	 *
	 * @return length
	 */
	private int[] getDifferences(String conA, String conB, int[] orig2CondensedPos, Translator translator) {
		var seqA = expandCondensed(conA, orig2CondensedPos, translator);
		var seqB = expandCondensed(conB, orig2CondensedPos, translator);
		var list = new ArrayList<Integer>();
		for (var i = 0; i < seqA.length(); i++) {
			if (seqA.charAt(i) != seqB.charAt(i)) {
				list.add(i + 1);
			}
		}
		var result = new int[list.size()];
		var count = 0;
		for (var value : list) {
			result[count++] = value;
		}
		return result;
	}

	/**
	 * invert the orig 2 new mapping
	 *
	 * @return new 2 orig mapping
	 */
	private BitSet[] invert(int[] orig2new) {
		var maxValue = 0;
		for (var i = 1; i < orig2new.length; i++)
			maxValue = Math.max(orig2new[i], maxValue);
		var new2orig = new BitSet[maxValue + 1];

		for (var i = 1; i < orig2new.length; i++) {
			var value = orig2new[i];
			if (new2orig[value] == null)
				new2orig[value] = new BitSet();
			new2orig[value].set(i);
		}
		return new2orig;
	}

	/**
	 * computes the weights associated with the condensed characters
	 *
	 * @return weights
	 */
	private double[] computeWeights(int numChars, int[] origPos2CondensedPos) {
		var counts = new int[origPos2CondensedPos.length];

		for (var origPos2CondensedPo : origPos2CondensedPos)
			counts[origPos2CondensedPo]++;

		var weights = new double[numChars];
		var pos = 0;
		for (var count : counts) {
			if (count > 0)
				weights[pos++] = count;
		}
		return weights;
	}

	/**
	 * expand a condensed sequence
	 *
	 * @return expanded sequence
	 */
	private String expandCondensed(String condensed, int[] orig2CondensedPos, Translator translator) {
		var buf = new StringBuilder();

		for (var origPos = 1; origPos < orig2CondensedPos.length; origPos++) {
			var conPos = orig2CondensedPos[origPos];
			var conChar = condensed.charAt(conPos);
			var origChar = translator.get(origPos, conPos, conChar);
			buf.append(origChar);


		}
		return buf.toString();
	}


	/**
	 * computes the condensed sequences
	 *
	 * @return array condensed sequences
	 */
	private String[] condenseCharacters(int ntax, int nchar, char[][] chars, int[] origPos2CondensedPos, int[] origTaxa2CondensedTaxa, Translator translator) {
		// check that all columns differ:
		var samePosAs = new int[nchar + 1];
		for (var i = 1; i <= nchar; i++) {
			samePosAs[i] = i;
		}

		for (var i = 1; i <= nchar; i++) {
			for (var j = i + 1; j <= nchar; j++) {
				var same = true;
				var i2j = new char[256];
				var j2i = new char[256];

				for (var t = 1; same && t <= ntax; t++) {
					var chari = chars[t][i];
					var charj = chars[t][j];

					if (i2j[chari] == (char) 0) {
						i2j[chari] = charj;
						if (j2i[charj] == (char) 0)
							j2i[charj] = chari;
						else if (j2i[charj] != chari)
							same = false; // differ
					} else if (i2j[chari] != charj)
						same = false; // differ
				}
				if (same) {
					samePosAs[j] = samePosAs[i];
					break;
				}
			}
		}

		var buffers = new StringBuffer[ntax + 1];
		for (var t = 1; t <= ntax; t++)
			buffers[t] = new StringBuffer();

		var newPos = 0;
		for (var i = 1; i < samePosAs.length; i++) {
			if (samePosAs[i] != 0) {
				if (samePosAs[i] < i) {
					origPos2CondensedPos[i] = origPos2CondensedPos[samePosAs[i]];
				} else // sameAs[i]==i
				{
					origPos2CondensedPos[i] = newPos++;
					for (int t = 1; t <= ntax; t++)
						buffers[t].append(chars[t][i]);
				}
				for (int t = 1; t <= ntax; t++) {
					int conPos = origPos2CondensedPos[samePosAs[i]];
					char chari = chars[t][i];
					char charj = chars[t][samePosAs[i]];
					translator.put(i, chari, conPos, charj);
				}
			}
		}
		// condensed positions start at 0
		//  for (int i = 1; i < origPos2CondensedPos.length; i++)
		//      origPos2CondensedPos[i]--;

		var sameTaxonAs = new int[ntax + 1];
		for (var s = 1; s <= ntax; s++) {
			sameTaxonAs[s] = s;
		}

		for (var s = 1; s <= ntax; s++) {
			var seqS = buffers[s].toString();
			for (var t = s + 1; t <= ntax; t++) {
				if (seqS.contentEquals(buffers[t]))
					sameTaxonAs[t] = sameTaxonAs[s];
			}
		}

		var count = 0;
		var list = new ArrayList<String>();
		for (var t = 1; t <= ntax; t++) {
			if (sameTaxonAs[t] < t)
				origTaxa2CondensedTaxa[t] = origTaxa2CondensedTaxa[sameTaxonAs[t]];
			else // sameTaxonAs[t]==t
			{
				origTaxa2CondensedTaxa[t] = (++count);
				list.add(buffers[t].toString());
			}
		}

		// condensed taxa start at 0
		for (var t = 1; t <= ntax; t++) {
			origTaxa2CondensedTaxa[t]--;
		}

		// System.err.println(StringUtils.toString(list," "));

		return list.toArray(new String[0]);
	}

	static class Translator {
		final Map<Triple, Character> mapOrigPosCondensedPosCondensedCharToOrigChar = new HashMap<>();
		int maxOrigPos = 0;
		int maxOrigChar = 0;
		int maxCondensedPos = 0;

		public void put(int origPos, char origChar, int condensedPos, char condensedChar) {
			maxOrigPos = Math.max(maxOrigPos, origPos);
			maxOrigChar = Math.max(maxOrigChar, origChar);
			maxCondensedPos = Math.max(maxCondensedPos, condensedPos);
			var triple = new Triple(origPos, condensedPos, condensedChar);
			mapOrigPosCondensedPosCondensedCharToOrigChar.put(triple, origChar);
		}

		public char get(int origPos, int condensedPos, char condensedChar) {
			var triple = new Triple(origPos, condensedPos, condensedChar);
			var ch = mapOrigPosCondensedPosCondensedCharToOrigChar.get(triple);
			return ch != null ? ch : (char) 0;
		}

		public String toString() {
			var buf = new StringBuilder();

			for (int i = 0; i <= maxCondensedPos; i++) {
				for (int j = 1; j <= maxOrigPos; j++) {
					for (int k = 0; k <= maxOrigChar; k++) {
						var z = mapOrigPosCondensedPosCondensedCharToOrigChar.get(new Triple(j, i, (char) k));
						if (z != null) {
							buf.append("condensed[").append(i).append("]=").append((char) k).append(" -> original[").append(j).append("]=").append(z).append("\n");
						}
					}
				}
			}
			return buf.toString();
		}

		record Triple(int origPos, int condensedPos, char condensedChar) {
		}
	}
}