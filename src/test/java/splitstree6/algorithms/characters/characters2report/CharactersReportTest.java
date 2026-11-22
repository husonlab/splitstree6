/*
 *  CharactersReportTest.java Copyright (C) 2026 Daniel H. Huson
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

package splitstree6.algorithms.characters.characters2report;

import jloda.util.progress.ProgressSilent;
import org.junit.jupiter.api.Test;
import splitstree6.data.CharactersBlock;
import splitstree6.data.ReportBlock;
import splitstree6.data.parts.CharactersType;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static splitstree6.testing.TestUtils.*;

/**
 * the reports on characters: Tajima's D, the Phi test and the estimate of the proportion of invariable sites
 */
public class CharactersReportTest {
	private static String report(AnalyzeCharactersBase algorithm, CharactersBlock chars) throws Exception {
		var report = new ReportBlock();
		algorithm.compute(new ProgressSilent(), taxa(chars.getNtax()), chars, report);
		return text(report);
	}

	// ---------- Tajima's D ----------

	/**
	 * Tajima's D (Tajima 1989) from the definition, with the nucleotide diversity computed from the state frequencies
	 * of each site; sites listed in skip are left out
	 */
	private static double tajimaD(String[] rows, Set<Integer> skip) {
		var n = rows.length;
		var pi = 0.0;
		var segregating = 0;
		for (var site = 0; site < rows[0].length(); site++) {
			if (skip.contains(site)) continue;
			var count = new HashMap<Character, Integer>();
			for (var row : rows) count.merge(row.charAt(site), 1, Integer::sum);
			if (count.size() > 1) segregating++;
			var same = 0.0;
			for (var c : count.values()) same += c * (c - 1.0);
			pi += 1 - same / (n * (n - 1.0));
		}
		double a1 = 0, a2 = 0;
		for (var i = 1; i < n; i++) {
			a1 += 1.0 / i;
			a2 += 1.0 / (i * i);
		}
		var b1 = (n + 1.0) / (3.0 * (n - 1));
		var b2 = 2.0 * (n * n + n + 3.0) / (9.0 * n * (n - 1));
		var c1 = b1 - 1 / a1;
		var c2 = b2 - (n + 2.0) / (a1 * n) + a2 / (a1 * a1);
		var e1 = c1 / a1;
		var e2 = c2 / (a1 * a1 + a2);
		return (pi - segregating / a1) / Math.sqrt(e1 * segregating + e2 * segregating * (segregating - 1.0));
	}

	private static TajimaD tajima(boolean excludeGapSites) {
		var algorithm = new TajimaD();
		algorithm.optionExcludeGapSitesProperty().set(excludeGapSites);
		return algorithm;
	}

	@Test
	void tajimaDAgreesWithTheDefinition() throws Exception {
		var random = new Random(3);
		for (var r = 0; r < 20; r++) {
			var rows = simulateSequences(10, 300, 0.02, 0, random);
			var expected = tajimaD(rows, Set.of());
			var text = report(tajima(true), characters(CharactersType.DNA, "acgt", rows));
			assertEquals(expected, numberAfter(text, "D ="), 1e-4, text);
			assertEquals(300, numberAfter(text, "based on"), text);
		}
	}

	/**
	 * two segregating sites among four sequences, one with a singleton and one that splits the sequences two against
	 * two: pi = 3/6 + 4/6, S = 2, a1 = 11/6
	 */
	@Test
	void tajimaDOnAHandExample() throws Exception {
		var rows = new String[]{"aaaa", "aaac", "acaa", "acaa"};
		var text = report(tajima(true), characters(CharactersType.DNA, "acgt", rows));
		var a1 = 11.0 / 6.0;
		var a2 = 1 + 0.25 + 1.0 / 9.0;
		var e1 = (5.0 / 9.0 - 1 / a1) / a1;
		var e2 = (2.0 * 23.0 / 108.0 - 6.0 / (4 * a1) + a2 / (a1 * a1)) / (a1 * a1 + a2);
		var expected = (7.0 / 6.0 - 2 / a1) / Math.sqrt(2 * e1 + 2 * e2);
		assertEquals(expected, numberAfter(text, "D ="), 1e-4, text);
	}

	/**
	 * with sites that contain a gap excluded, both the number of segregating sites and the nucleotide diversity must be
	 * those of the remaining sites. The diversity was once computed from all sites
	 */
	@Test
	void tajimaDLeavesOutSitesWithGaps() throws Exception {
		var random = new Random(4);
		var rows = simulateSequences(10, 300, 0.02, 0, random);
		var skip = new HashSet<Integer>();
		for (var k = 0; k < 60; k++) {
			var t = random.nextInt(10);
			var site = random.nextInt(300);
			rows[t] = rows[t].substring(0, site) + (k % 2 == 0 ? '-' : '?') + rows[t].substring(site + 1);
			skip.add(site);
		}
		var text = report(tajima(true), characters(CharactersType.DNA, "acgt", rows));
		assertEquals(tajimaD(rows, skip), numberAfter(text, "D ="), 1e-4, text);
		assertEquals(300 - skip.size(), numberAfter(text, "based on"), text);
	}

	/**
	 * without a segregating site, and with fewer than four sequences, D is not defined; the report must say so
	 */
	@Test
	void tajimaDIsNotReportedWhereItIsNotDefined() throws Exception {
		for (var rows : List.of(new String[]{"acgt", "acgt", "acgt", "acgt", "acgt"}, new String[]{"acgt", "acct"}, new String[]{"acgt"},
				new String[]{"ac-t", "a-gt", "-cgt", "acg-", "acgt"})) {
			var text = report(tajima(true), characters(CharactersType.DNA, "acgt", rows));
			assertFalse(text.contains("NaN") || text.contains("Infinity"), text);
			assertFalse(text.contains("indicates"), text);
		}
	}

	// ---------- the Phi test ----------

	/**
	 * the incompatibility score of two sites (Bruen et al 2006): the number of edges, minus the number of vertices, plus
	 * the number of components, of the graph whose vertices are the states of the two sites and whose edges are the
	 * pairs of states that occur together in a sequence
	 */
	private static int incompatibility(String[] rows, int site1, int site2) {
		var parent = new HashMap<String, String>();
		var edges = new HashSet<String>();
		for (var row : rows) {
			var a = "1" + row.charAt(site1);
			var b = "2" + row.charAt(site2);
			parent.putIfAbsent(a, a);
			parent.putIfAbsent(b, b);
			if (edges.add(a + b)) parent.put(find(parent, a), find(parent, b));
		}
		var components = new HashSet<String>();
		for (var v : parent.keySet()) components.add(find(parent, v));
		return edges.size() - parent.size() + components.size();
	}

	private static String find(Map<String, String> parent, String v) {
		while (!parent.get(v).equals(v)) v = parent.get(v);
		return v;
	}

	private static double phi(int[][] score, List<Integer> order, int k) {
		var sum = 0.0;
		var pairs = 0;
		for (var i = 0; i < order.size(); i++)
			for (var j = i + 1; j <= i + k && j < order.size(); j++) {
				sum += score[order.get(i)][order.get(j)];
				pairs++;
			}
		return sum / pairs;
	}

	/**
	 * the p-value of the Phi test by permutation of the informative sites: the probability, under a normal distribution
	 * with the mean and variance of the permuted statistic, of a value below the observed one
	 */
	private static double phiByPermutation(String[] rows, int permutations, Random random) {
		var informative = new ArrayList<Integer>();
		for (var site = 0; site < rows[0].length(); site++) {
			var count = new HashMap<Character, Integer>();
			for (var row : rows) count.merge(row.charAt(site), 1, Integer::sum);
			if (count.values().stream().filter(c -> c >= 2).count() >= 2) informative.add(site);
		}
		var m = informative.size();
		var score = new int[m][m];
		for (var i = 0; i < m; i++)
			for (var j = i + 1; j < m; j++) score[i][j] = score[j][i] = incompatibility(rows, informative.get(i), informative.get(j));
		var k = Math.max(1, (int) Math.floor(m * 100.0 / rows[0].length() + 0.5));
		var order = new ArrayList<Integer>();
		for (var i = 0; i < m; i++) order.add(i);
		var observed = phi(score, order, k);
		double sum = 0, sumOfSquares = 0;
		for (var p = 0; p < permutations; p++) {
			Collections.shuffle(order, random);
			var value = phi(score, order, k);
			sum += value;
			sumOfSquares += value * value;
		}
		var mean = sum / permutations;
		var sd = Math.sqrt(sumOfSquares / permutations - mean * mean);
		return normalBelow((observed - mean) / sd);
	}

	/**
	 * the standard normal distribution function, by numerical integration
	 */
	private static double normalBelow(double z) {
		var steps = 200000;
		var from = -12.0;
		if (z <= from) return 0;
		var h = (z - from) / steps;
		var sum = 0.0;
		for (var i = 0; i < steps; i++) {
			var x = from + (i + 0.5) * h;
			sum += Math.exp(-0.5 * x * x);
		}
		return sum * h / Math.sqrt(2 * Math.PI);
	}

	/**
	 * the test uses formulas for the mean and variance of the statistic under permutation of the sites; the p-value must
	 * agree with the one obtained by carrying out the permutations
	 */
	@Test
	void phiTestAgreesWithPermutation() throws Exception {
		var random = new Random(5);
		for (var r = 0; r < 6; r++) {
			String[] rows;
			if (r < 3) rows = simulateSequences(12, 400, 0.15, 0, random);
			else { // two halves with different trees
				var left = simulateSequences(12, 200, 0.1, 0, random);
				var right = simulateSequences(12, 200, 0.1, 0, random);
				rows = new String[12];
				for (var t = 0; t < 12; t++) rows[t] = left[t] + right[t];
			}
			var expected = phiByPermutation(rows, 20000, random);
			var found = new PhiTest().approxPhi(new ProgressSilent(), characters(CharactersType.DNA, "acgt", rows));
			assertEquals(expected, found, 0.02 + 0.1 * expected, "data set " + r);
			if (r >= 3) assertTrue(found < 0.05, "recombinant data set " + r + ": p = " + found);
		}
	}

	@Test
	void phiTestReport() throws Exception {
		var random = new Random(6);
		var left = simulateSequences(12, 300, 0.1, 0, random);
		var right = simulateSequences(12, 300, 0.1, 0, random);
		var rows = new String[12];
		for (var t = 0; t < 12; t++) rows[t] = left[t] + right[t];
		assertTrue(report(new PhiTest(), characters(CharactersType.DNA, "acgt", rows)).contains("did find statistically significant evidence"));
		var few = report(new PhiTest(), characters(CharactersType.DNA, "acgt", "aaaa", "aaaa", "acca", "acca"));
		assertTrue(few.contains("too few informative"), few);
		var none = report(new PhiTest(), characters(CharactersType.DNA, "acgt", "aaaa", "aaaa", "aaaa"));
		assertTrue(none.contains("too few informative"), none);
	}

	/**
	 * sites that are all compatible with one tree: no incompatibility at all, so the statistic has no variance and the
	 * test cannot be carried out; no p-value that is not a number may be reported
	 */
	@Test
	void phiTestOnSitesWithoutAnyIncompatibility() throws Exception {
		var rows = perfectCharacters(randomTree(12, new Random(7)), 12);
		var twice = new String[12];
		for (var t = 0; t < 12; t++) twice[t] = rows[t].repeat(30);
		var text = report(new PhiTest(), characters(CharactersType.DNA, "acgt", twice));
		assertFalse(text.contains("NaN"), text);
		assertFalse(text.contains("did find"), text);
	}

	// ---------- invariable sites ----------

	private static EstimateInvariableSites estimator() {
		var algorithm = new EstimateInvariableSites();
		algorithm.optionFullTaxaCutoffProperty().set(20);
		return algorithm;
	}

	/**
	 * sequences that are identical have no variable site
	 */
	@Test
	void identicalSequencesAreAllInvariable() throws Exception {
		var rows = new String[6];
		Arrays.fill(rows, "acgtacgtacgtacgtacgt");
		var chars = characters(CharactersType.DNA, "acgt", rows);
		assertEquals(1.0, estimator().estimatePropInvariableSites(new ProgressSilent(), chars), 1e-12);
		assertEquals(100.0, numberAfter(report(estimator(), chars), "("), 1e-9);
	}

	/**
	 * sequences from the Jukes-Cantor model in which a known part of the sites cannot change (Steel, Huson and Lockhart
	 * 2000). The method once returned the proportion of variable sites
	 */
	@Test
	void theProportionOfInvariableSitesIsRecovered() throws Exception {
		var random = new Random(8);
		for (var proportion : new double[]{0.0, 0.3, 0.6}) {
			var sites = 30000;
			var rows = simulateSequences(8, sites, 0.3, (int) (proportion * sites), random);
			var found = estimator().estimatePropInvariableSites(new ProgressSilent(), characters(CharactersType.DNA, "acgt", rows));
			assertEquals(proportion, found, 0.05, "proportion " + proportion);
		}
	}

	/**
	 * above the cut-off the method samples quadruples of sequences; the number of quadruples once overflowed from about
	 * 215 sequences, and the estimate then came from a single quadruple
	 */
	@Test
	void manySequences() throws Exception {
		var random = new Random(9);
		var sites = 2000;
		var rows = simulateSequences(230, sites, 0.05, sites / 2, random);
		var chars = characters(CharactersType.DNA, "acgt", rows);
		var values = new TreeSet<Double>();
		for (var r = 0; r < 5; r++) values.add(estimator().estimatePropInvariableSites(new ProgressSilent(), chars));
		assertTrue(values.last() - values.first() < 0.05, "five runs gave " + values);
	}

	@Test
	void fewerThanFourSequences() throws Exception {
		var text = report(estimator(), characters(CharactersType.DNA, "acgt", "acgt", "acct", "acgt"));
		assertFalse(text.contains("NaN"), text);
	}
}
