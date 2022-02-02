/*
 * QuasiMedianBase.java Copyright (C) 2022 Daniel H. Huson
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

package splitstree6.algorithms.characters.characters2network;

import jloda.graph.Edge;
import jloda.graph.Node;
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
	public static final String NODE_STATES_KEY = "states";
	public static final String EDGE_SITES_KEY = "sites";

	/**
	 * Applies the method to the given data
	 *
	 * @param taxa            the taxa
	 * @param charactersBlock the characters
	 */
	public void apply(ProgressListener progress, TaxaBlock taxa, CharactersBlock charactersBlock, NetworkBlock networkBlock) throws CanceledException {
		progress.setSubtask(Basic.getShortName(getClass()));
		progress.setProgress(0);
		progress.setMaximum(100);    //initialize maximum progress

		final int ntax = taxa.getNtax();
		final int nchar = charactersBlock.getNchar();

		final char[][] characters = getCharacters(charactersBlock);
		final String[] characterLabels = getCharacterLabels(charactersBlock);

		int[] orig2CondensedPos = new int[nchar + 1];
		int[] orig2CondensedTaxa = new int[ntax + 1];

		final Translator translator = new Translator(); // translates between original character states and condensed ones

		// NOTE: in condensedCharacters we count taxa and positions starting from 0 (not 1, as otherwise in SplitsTree)
		String[] condensedCharacters = condenseCharacters(ntax, nchar, characters, orig2CondensedPos, orig2CondensedTaxa, translator);

		BitSet[] condensed2OrigPos = invert(orig2CondensedPos);

		double[] weights = computeWeights(condensedCharacters[1].length(), orig2CondensedPos);

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

		final Set<String> condensedInputSet = new TreeSet<>(Arrays.asList(condensedCharacters));

		final PhyloGraph graph = networkBlock.getGraph();

		computeGraph(progress, condensedInputSet, weights, graph);

		for (Node v : graph.nodes()) {
			String condensed = (String) v.getInfo();
			graph.setLabel(v, null);
			if (condensedInputSet.contains(condensed)) {
				for (int t = 1; t <= taxa.getNtax(); t++) {
					int o = orig2CondensedTaxa[t];
					if (condensedCharacters[o].equals(condensed)) {
						graph.addTaxon(v, t);
					}
				}

				if (graph.hasTaxa(v)) {
					final StringBuilder buf = new StringBuilder();
					int count = 0;
					for (Integer t : graph.getTaxa(v)) {
						if (buf.length() > 0)
							buf.append(", ");
						buf.append(taxa.getLabel(t));
						count++;
					}
					if (count == 1)
						graph.setLabel(v, taxa.get(graph.getTaxa(v).iterator().next()).getDisplayLabelOrName());
					else if (count > 1)
						graph.setLabel(v, "{" + buf + "}");
				}
			}
			String full = expandCondensed(condensed, orig2CondensedPos, translator);
			networkBlock.getNodeData(v).put(NODE_STATES_KEY, full);
		}

		for (Edge e : graph.edges()) {
			String label = computeEdgeLabel(characterLabels, (String) e.getSource().getInfo(), (String) e.getTarget().getInfo(), orig2CondensedPos, translator);
			networkBlock.getEdgeData(e).put(EDGE_SITES_KEY, label);
		}
		networkBlock.setNetworkType(NetworkBlock.Type.HaplotypeNetwork);
	}

	/**
	 * compute the matrix of unmasked characters
	 *
	 * @return unmasked characters
	 */
	private char[][] getCharacters(CharactersBlock chars) {
		List[] list = new LinkedList[chars.getNtax() + 1];
		for (int c = 1; c <= chars.getNchar(); c++) {
			char majorityState = 0;
			for (int t = 1; t <= chars.getNtax(); t++) {
				char ch = chars.get(t, c);
				if (list[t] == null)
					list[t] = new LinkedList();
				if (ch == chars.getGapCharacter() || ch == chars.getMissingCharacter()) {
					if (majorityState == 0)
						majorityState = determineMajorityState(chars, c);
					ch = majorityState;
				}
				list[t].add(ch);
			}
		}
		char[][] characters = new char[chars.getNtax() + 1][list[1].size() + 1];
		for (int t = 1; t <= chars.getNtax(); t++) {
			int count = 0;
			for (Object o : list[t]) {
				characters[t][++count] = (Character) o;
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
	 *
	 */
	public abstract void computeGraph(ProgressListener progressListener, Set<String> inputSequences, double[] weights, PhyloGraph graph) throws CanceledException;

	/**
	 * computes all original positions at which the two sequences differ in display coordinates 1--length
	 *
	 * @return positions at which orig sequences differ
	 */
	private String computeEdgeLabel(String[] labels, String conA, String conB, int[] orig2CondensedPos, Translator translator) {
		StringBuilder buf = new StringBuilder();

		String seqA = expandCondensed(conA, orig2CondensedPos, translator);
		String seqB = expandCondensed(conB, orig2CondensedPos, translator);

		boolean first = true;
		for (int i = 0; i < seqA.length(); i++) {
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
		String seqA = expandCondensed(conA, orig2CondensedPos, translator);
		String seqB = expandCondensed(conB, orig2CondensedPos, translator);
		List list = new LinkedList();
		for (int i = 0; i < seqA.length(); i++) {
			if (seqA.charAt(i) != seqB.charAt(i)) {
				list.add(i + 1);
			}
		}
		int[] result = new int[list.size()];
		int count = 0;
		for (Object aList : list) {
			result[count++] = (Integer) aList;
		}
		return result;
	}

	/**
	 * invert the orig 2 new mapping
	 *
	 * @return new 2 orig mapping
	 */
	private BitSet[] invert(int[] orig2new) {
		int maxValue = 0;
		for (int i = 1; i < orig2new.length; i++)
			maxValue = Math.max(orig2new[i], maxValue);
		BitSet[] new2orig = new BitSet[maxValue + 1];

		for (int i = 1; i < orig2new.length; i++) {
			int value = orig2new[i];
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
		int[] counts = new int[origPos2CondensedPos.length];

		for (int origPos2CondensedPo : origPos2CondensedPos) counts[origPos2CondensedPo]++;

		double[] weights = new double[numChars];
		int pos = 0;
		for (int count : counts) {
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
		StringBuilder buf = new StringBuilder();

		for (int origPos = 1; origPos < orig2CondensedPos.length; origPos++) {
			int conPos = orig2CondensedPos[origPos];
			char conChar = condensed.charAt(conPos);
			char origChar = translator.get(origPos, conPos, conChar);
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
		int[] samePosAs = new int[nchar + 1];
		for (int i = 1; i <= nchar; i++) {
			samePosAs[i] = i;
		}

		for (int i = 1; i <= nchar; i++) {
			for (int j = i + 1; j <= nchar; j++) {
				boolean same = true;
				char[] i2j = new char[256];
				char[] j2i = new char[256];

				for (int t = 1; same && t <= ntax; t++) {
					char chari = chars[t][i];
					char charj = chars[t][j];

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

		StringBuffer[] buffers = new StringBuffer[ntax + 1];
		for (int t = 1; t <= ntax; t++)
			buffers[t] = new StringBuffer();

		int newPos = 0;
		for (int i = 1; i < samePosAs.length; i++) {
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

		int[] sameTaxonAs = new int[ntax + 1];
		for (int s = 1; s <= ntax; s++) {
			sameTaxonAs[s] = s;
		}

		for (int s = 1; s <= ntax; s++) {
			String seqS = buffers[s].toString();
			for (int t = s + 1; t <= ntax; t++) {
				if (seqS.equals(buffers[t].toString()))
					sameTaxonAs[t] = sameTaxonAs[s];
			}
		}

		int count = 0;
		List list = new LinkedList();
		for (int t = 1; t <= ntax; t++) {
			if (sameTaxonAs[t] < t)
				origTaxa2CondensedTaxa[t] = origTaxa2CondensedTaxa[sameTaxonAs[t]];
			else // sameTaxonAs[t]==t
			{
				origTaxa2CondensedTaxa[t] = (++count);
				list.add(buffers[t].toString());
			}
		}

		// condensed taxa start at 0
		for (int t = 1; t <= ntax; t++) {
			origTaxa2CondensedTaxa[t]--;
        }

        String[] result = new String[list.size()];
        int which = 0;
        for (Object aList : list) {
            result[which++] = (String) aList;
        }
        return result;
    }

    static class Translator {
        final Map mapOrigPosCondensedPosCondensedCharToOrigChar = new HashMap();
        int maxOrigPos = 0;
        int maxOrigChar = 0;
        int maxCondensedPos = 0;

        public void put(int origPos, char origChar, int condensedPos, char condensedChar) {
            maxOrigPos = Math.max(maxOrigPos, origPos);
            maxOrigChar = Math.max(maxOrigChar, origChar);
            maxCondensedPos = Math.max(maxCondensedPos, condensedPos);
            Triple triple = new Triple(origPos, condensedPos, condensedChar);
            Character ch = origChar;
			mapOrigPosCondensedPosCondensedCharToOrigChar.put(triple, ch);
		}

		public char get(int origPos, int condensedPos, char condensedChar) {
			Triple triple = new Triple(origPos, condensedPos, condensedChar);
			Character ch = (Character) mapOrigPosCondensedPosCondensedCharToOrigChar.get(triple);

			if (ch != null)
				return ch;
			else
				return (char) 0;
		}

		public String toString() {
			StringBuilder buf = new StringBuilder();

			for (int i = 0; i <= maxCondensedPos; i++) {
				for (int j = 1; j <= maxOrigPos; j++) {
					for (int k = 0; k <= maxOrigChar; k++) {
						Character z = (Character) mapOrigPosCondensedPosCondensedCharToOrigChar.get(new Triple(j, i, (char) k));
						if (z != null) {
							buf.append("condensed[").append(i).append("]=").append((char) k).append(" -> original[").append(j).append("]=").append(z).append("\n");
						}
					}
				}
			}
			return buf.toString();
		}

        static class Triple {
			final int first;
			final int second;
			final char third;

			Triple(int first, int second, char third) {
				this.first = first;
				this.second = second;
				this.third = third;
			}

            public int hashCode() {
                return first + 17 * second + 37 * third;
            }

			public boolean equals(Object other) {
                if (other instanceof Triple t) {
                    return first == t.first && second == t.second && third == t.third;
                } else
                    return false;
			}
		}
	}
}