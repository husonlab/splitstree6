/*
 *  SplitsReportTest.java Copyright (C) 2026 Daniel H. Huson
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

package splitstree6.algorithms.splits.splits2report;

import org.junit.jupiter.api.Test;
import splitstree6.data.SplitsBlock;
import splitstree6.splits.ASplit;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static splitstree6.testing.TestUtils.*;

/**
 * the reports on splits: phylogenetic diversity, Shapley values and the incompatibility score
 */
public class SplitsReportTest {
	/**
	 * the diversity of a set of taxa is the total weight of the splits that separate two of them
	 */
	private static double diversity(Map<BitSet, Double> splits, BitSet taxa) {
		var sum = 0.0;
		for (var entry : splits.entrySet()) {
			var inside = (BitSet) entry.getKey().clone();
			inside.and(taxa);
			if (inside.cardinality() > 0 && inside.cardinality() < taxa.cardinality()) sum += entry.getValue();
		}
		return sum;
	}

	@Test
	void phylogeneticDiversity() {
		var random = new Random(21);
		var n = 12;
		var splits = randomCircularSplits(n, random);
		var block = splitsBlock(splits, n);
		for (var r = 0; r < 50; r++) {
			var taxa = new BitSet();
			for (var t = 1; t <= n; t++) if (random.nextBoolean()) taxa.set(t);
			assertEquals(diversity(splits, taxa), PhylogeneticDiversity.compute(block, taxa), 1e-9);
		}
		assertEquals(0, PhylogeneticDiversity.compute(block, new BitSet()));
		assertEquals(0, PhylogeneticDiversity.compute(block, set(3)));
		var all = new BitSet();
		all.set(1, n + 1);
		assertEquals(splits.values().stream().mapToDouble(w -> w).sum(), PhylogeneticDiversity.compute(block, all), 1e-9);
	}

	@Test
	void phylogeneticDiversityReport() {
		var block = splitsBlock(Map.of(set(1), 1.0, set(1, 2), 2.0, set(1, 2, 3), 4.0), 4);
		var text = PhylogeneticDiversity.report(taxa(4), block, set(1, 2));
		assertEquals(1.0, numberAfter(text, "Diversity ="), 1e-9, text);
		assertTrue(text.contains("t1") && text.contains("t2") && !text.contains("t3"), text);
		assertFalse(PhylogeneticDiversity.report(taxa(4), new SplitsBlock(), set(1, 2)).contains("NaN"));
	}

	/**
	 * the Shapley value of a taxon by its definition: the mean, over all orders in which the taxa can be added, of the
	 * diversity that the taxon adds
	 */
	private static double[] shapleyByDefinition(Map<BitSet, Double> splits, int n) {
		var factorial = new double[n + 1];
		factorial[0] = 1;
		for (var k = 1; k <= n; k++) factorial[k] = k * factorial[k - 1];
		var result = new double[n + 1];
		for (var mask = 0; mask < (1 << n); mask++) {
			var subset = new BitSet();
			for (var t = 1; t <= n; t++) if ((mask & (1 << (t - 1))) != 0) subset.set(t);
			var size = subset.cardinality();
			var value = diversity(splits, subset);
			for (var t = 1; t <= n; t++)
				if (!subset.get(t)) {
					subset.set(t);
					result[t] += factorial[size] * factorial[n - size - 1] / factorial[n] * (diversity(splits, subset) - value);
					subset.clear(t);
				}
		}
		return result;
	}

	@Test
	void shapleyValuesAgreeWithTheDefinition() {
		var random = new Random(22);
		for (var r = 0; r < 5; r++) {
			var n = 9;
			var splits = randomCircularSplits(n, random);
			var block = splitsBlock(splits, n);
			var expected = shapleyByDefinition(splits, n);
			var found = ShapleyValues.compute(taxa(n).getTaxaSet(), block.getSplits());
			assertEquals(n, found.size());
			for (var t = 1; t <= n; t++) assertEquals(expected[t], found.get(t), 1e-9, "taxon " + t);
			assertEquals(splits.values().stream().mapToDouble(w -> w).sum(), found.values().stream().mapToDouble(w -> w).sum(), 1e-9);
		}
	}

	/**
	 * the values are computed in parallel; every taxon must have its value every time. They were once collected in a
	 * map that must not be written to by several threads
	 */
	@Test
	void shapleyValuesAreCompleteEveryTime() {
		var n = 300;
		var splits = randomTree(n, new Random(23));
		var block = splitsBlock(splits, n);
		var taxa = taxa(n).getTaxaSet();
		var first = ShapleyValues.compute(taxa, block.getSplits());
		for (var r = 0; r < 300; r++) {
			var found = ShapleyValues.compute(taxa, block.getSplits());
			assertEquals(n, found.size(), "run " + r);
			assertEquals(first, found, "run " + r);
		}
	}

	@Test
	void shapleyValuesReport() {
		var block = splitsBlock(Map.of(set(1), 3.0, set(1, 3), 6.0, set(1, 2), 9.0), 3);
		// each split separates one taxon from two: the one gets 2/3 of the weight, the others 1/6 each
		var text = ShapleyValues.report(taxa(3), block.getSplits());
		assertEquals(2 + 1 + 1.5, numberAfter(text, "t1:"), 1e-9, text);
		assertEquals(0.5 + 4 + 1.5, numberAfter(text, "t2:"), 1e-9, text);
		assertEquals(0.5 + 1 + 6, numberAfter(text, "t3:"), 1e-9, text);
		assertTrue(text.indexOf("t3:") < text.indexOf("t2:") && text.indexOf("t2:") < text.indexOf("t1:"), "by decreasing value: " + text);
	}

	/**
	 * the weight of the splits that are not in the greedily chosen tree, among the splits that are not trivial
	 */
	@Test
	void incompatibilityScore() {
		var splits = new HashMap<BitSet, Double>();
		splits.put(set(1), 10.0);
		splits.put(set(1, 2), 3.0);
		splits.put(set(1, 3), 1.0);
		splits.put(set(1, 2, 3), 4.0);
		var text = IncompatibilityScore.report(taxa(6), splitsBlock(splits, 6).getSplits());
		text = text.substring(text.indexOf('\n')); // the line after the heading
		assertEquals(1.0, numberAfter(text, "\n"), 1e-9, text);
		assertEquals(8.0, numberAfter(text, " of "), 1e-9, text);
		assertEquals(12.5, numberAfter(text, "("), 0.05, text);
	}

	@Test
	void incompatibilityScoreOfCompatibleSplits() {
		var n = 10;
		var text = IncompatibilityScore.report(taxa(n), splitsBlock(randomTree(n, new Random(24)), n).getSplits());
		assertEquals(0.0, numberAfter(text, "\n"), 1e-9, text);
		assertEquals(0.0, numberAfter(text, "("), 1e-9, text);
		var trivial = new ArrayList<ASplit>();
		for (var t = 1; t <= 4; t++) trivial.add(new ASplit(set(t), 4, 1.0));
		assertFalse(IncompatibilityScore.report(taxa(4), trivial).contains("NaN"));
	}
}
