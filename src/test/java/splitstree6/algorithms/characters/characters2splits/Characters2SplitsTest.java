/*
 *  Characters2SplitsTest.java Copyright (C) 2026 Daniel H. Huson
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

package splitstree6.algorithms.characters.characters2splits;

import jloda.util.progress.ProgressSilent;
import org.junit.jupiter.api.Test;
import splitstree6.data.CharactersBlock;
import splitstree6.data.SplitsBlock;
import splitstree6.data.parts.CharactersType;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static splitstree6.testing.TestUtils.*;

/**
 * the methods that compute splits directly from characters
 */
public class Characters2SplitsTest {
	private static Map<BitSet, Double> run(Characters2Splits algorithm, CharactersBlock chars) throws Exception {
		var splits = new SplitsBlock();
		algorithm.compute(new ProgressSilent(), taxa(chars.getNtax()), chars, splits);
		assertEquals(chars.getNtax() + 1, splits.getCycle().length, "cycle");
		return weightedSplits(splits, chars.getNtax(), 0);
	}

	/**
	 * each binary site that is not constant is a split, and the weight of a split is the number of its sites
	 */
	@Test
	void binaryToSplits() throws Exception {
		var chars = characters(CharactersType.Standard, "01",
				"00000",
				"00100",
				"11001",
				"11001",
				"11000");
		var expected = new HashMap<BitSet, Double>();
		expected.put(set(1, 2), 2.0);
		expected.put(set(1, 3, 4, 5), 1.0);
		expected.put(set(1, 2, 5), 1.0);
		var algorithm = new BinaryToSplits();
		assertEquals(expected, run(algorithm, chars));
		algorithm.setOptionMinSplitWeight(2);
		assertEquals(Map.of(set(1, 2), 2.0), run(algorithm, chars));
	}

	@Test
	void binaryToSplitsWithAllTrivialSplits() throws Exception {
		var chars = characters(CharactersType.Standard, "01", "000", "001", "110", "110");
		var algorithm = new BinaryToSplits();
		algorithm.setOptionAddAllTrivial(true);
		var expected = new HashMap<BitSet, Double>();
		expected.put(set(1, 2), 2.0);
		expected.put(set(1, 3, 4), 1.0);
		expected.put(set(1), 1.0);
		expected.put(set(1, 2, 4), 1.0);
		expected.put(set(1, 2, 3), 1.0);
		assertEquals(expected, run(algorithm, chars));
	}

	/**
	 * characters without homoplasy give back the splits of the tree they were generated on
	 */
	@Test
	void sitesOfATreeGiveTheTree() throws Exception {
		var random = new Random(11);
		for (var r = 0; r < 10; r++) {
			var n = 15;
			var truth = new HashMap<BitSet, Double>();
			for (var entry : randomTree(n, random).entrySet()) truth.put(entry.getKey(), (double) (1 + random.nextInt(4)));
			var rows = perfectCharacters(truth, n);
			var binary = new String[n];
			for (var t = 0; t < n; t++) binary[t] = rows[t].replace('a', '0').replace('c', '1');
			assertEquals(truth, run(new BinaryToSplits(), characters(CharactersType.Standard, "01", binary)), "binary, tree " + r);
			for (var method : DnaToSplits.Method.values()) {
				var algorithm = new DnaToSplits();
				algorithm.setOptionMethod(method);
				assertEquals(truth, run(algorithm, characters(CharactersType.DNA, "acgt", rows)), method + ", tree " + r);
			}
			assertEquals(truth, run(new ParsimonySplits(), characters(CharactersType.DNA, "acgt", rows)), "parsimony splits, tree " + r);
		}
	}

	/**
	 * majority state: the sequences with the most frequent state against the others
	 */
	@Test
	void dnaToSplitsByMajorityState() throws Exception {
		var chars = characters(CharactersType.DNA, "acgt",
				"aaa",
				"aca",
				"agc",
				"cgc",
				"cta");
		var expected = new HashMap<BitSet, Double>();
		expected.put(set(1, 2, 3), 1.0);
		expected.put(set(1, 2, 5), 1.0);
		// site 2: g is the most frequent state, in sequences 3 and 4
		expected.put(set(1, 2, 5), 2.0);
		assertEquals(expected, run(new DnaToSplits(), chars));
	}

	/**
	 * purines against pyrimidines
	 */
	@Test
	void dnaToSplitsByRYAlphabet() throws Exception {
		var chars = characters(CharactersType.DNA, "acgt",
				"aag",
				"gca",
				"ctg",
				"tca",
				"gtg");
		var algorithm = new DnaToSplits();
		algorithm.setOptionMethod(DnaToSplits.Method.RYAlphabet);
		var expected = new HashMap<BitSet, Double>();
		expected.put(set(1, 2, 5), 1.0);
		expected.put(set(1), 1.0);
		assertEquals(expected, run(algorithm, chars));
	}

	/**
	 * the parsimony score of four sequences for the grouping a1,a2 against b1,b2: the number of sites that support it,
	 * minus the smaller number of sites that support one of the other two groupings, or 0 if that is negative
	 */
	private static int score(String[] rows, int a1, int a2, int b1, int b2) {
		int support = 0, other1 = 0, other2 = 0;
		for (var site = 0; site < rows[0].length(); site++) {
			char ca1 = rows[a1].charAt(site), ca2 = rows[a2].charAt(site), cb1 = rows[b1].charAt(site), cb2 = rows[b2].charAt(site);
			if (ca1 == ca2 && cb1 == cb2) support++;
			if (ca1 == cb1 && ca2 == cb2) other1++;
			if (ca1 == cb2 && ca2 == cb1) other2++;
		}
		return Math.max(0, support - Math.min(other1, other2));
	}

	/**
	 * parsimony splits (Bandelt and Dress 1992) against the definition: the index of a split is the smallest score over
	 * all choices of two sequences on each side, and the splits are those with positive index
	 */
	@Test
	void parsimonySplitsAgreeWithTheDefinition() throws Exception {
		var random = new Random(12);
		for (var r = 0; r < 20; r++) {
			var n = 7;
			var rows = simulateSequences(n, 80, 0.15, 0, random);
			var expected = new HashMap<BitSet, Double>();
			for (var mask = 1; mask < (1 << (n - 1)); mask++) {
				var a = new ArrayList<Integer>();
				var b = new ArrayList<Integer>();
				for (var i = 0; i < n; i++) (((mask & (1 << i)) != 0) ? a : b).add(i);
				var index = Integer.MAX_VALUE;
				for (var a1 : a) for (var a2 : a) for (var b1 : b) for (var b2 : b) index = Math.min(index, score(rows, a1, a2, b1, b2));
				if (index > 0) {
					var side = new BitSet();
					for (var i : a) side.set(i + 1);
					expected.put(canonical(side, n), (double) index);
				}
			}
			assertEquals(expected, run(new ParsimonySplits(), characters(CharactersType.DNA, "acgt", rows)), "data set " + r);
		}
	}

	@Test
	void degenerateInput() throws Exception {
		for (var rows : List.of(new String[]{"acgt"}, new String[]{"acgt", "acct"}, new String[]{"aaaa", "aaaa", "aaaa", "aaaa"}, new String[]{"acgt", "ccgt", "agga"})) {
			for (var method : DnaToSplits.Method.values()) {
				var algorithm = new DnaToSplits();
				algorithm.setOptionMethod(method);
				for (var w : run(algorithm, characters(CharactersType.DNA, "acgt", rows)).values()) assertTrue(Double.isFinite(w));
			}
			for (var w : run(new ParsimonySplits(), characters(CharactersType.DNA, "acgt", rows)).values()) assertTrue(Double.isFinite(w));
			var binary = rows.clone();
			for (var t = 0; t < binary.length; t++) binary[t] = binary[t].replace('a', '0').replace('c', '1').replace('g', '0').replace('t', '1');
			for (var w : run(new BinaryToSplits(), characters(CharactersType.Standard, "01", binary)).values()) assertTrue(Double.isFinite(w));
		}
	}
}
