/*
 *  Distances2SplitsTest.java Copyright (C) 2026 Daniel H. Huson
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

package splitstree6.algorithms.distances.distances2splits;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static splitstree6.testing.TestUtils.*;

/**
 * split decomposition and the Buneman tree, against their definitions
 */
public class Distances2SplitsTest {
	static final List<Supplier<Distances2Splits>> METHODS = List.of(BunemanTree::new, SplitDecomposition::new, NeighborNet::new);

	static double[][] randomDistances(int n, Random random) {
		var d = new double[n][n];
		for (var i = 0; i < n; i++) for (var j = i + 1; j < n; j++) d[i][j] = d[j][i] = 1 + random.nextDouble();
		return d;
	}

	/**
	 * eight taxa have 127 splits. The index of each is computed from its definition, as the minimum over all quartets
	 * that the split separates; the splits with a positive index are the result
	 */
	@Test
	void bunemanTreeAndSplitDecompositionAgreeWithEnumerationOfAllSplits() throws Exception {
		var random = new Random(15);
		var n = 8;
		for (var r = 0; r < 60; r++) {
			var d = (r < 30 ? noisy(metric(randomTree(n, random), n), 0.1, random) : randomDistances(n, random));
			var buneman = weightedSplits(splits(new BunemanTree(), d), n, 1e-6);
			var expectedBuneman = splitsByEnumeration(d, false);
			assertEquals(expectedBuneman.keySet(), buneman.keySet(), "BunemanTree, input " + r);
			assertTrue(maxDifference(expectedBuneman, buneman) < 1e-9, "BunemanTree, input " + r);
			var decomposition = weightedSplits(splits(new SplitDecomposition(), d), n, 1e-6);
			var expectedDecomposition = splitsByEnumeration(d, true);
			assertEquals(expectedDecomposition.keySet(), decomposition.keySet(), "SplitDecomposition, input " + r);
			assertTrue(maxDifference(expectedDecomposition, decomposition) < 1e-6, "SplitDecomposition, input " + r + ": " + maxDifference(expectedDecomposition, decomposition));
		}
	}

	@Test
	void theSplitsOfATreeMetricAreThoseOfTheTree() throws Exception {
		var random = new Random(50);
		var n = 50;
		for (var r = 0; r < 5; r++) {
			var truth = randomTree(n, random);
			for (var method : METHODS) {
				var name = method.get().getClass().getSimpleName() + ", tree " + r;
				var splits = splits(method.get(), metric(truth, n));
				var found = weightedSplits(splits, n, 1e-6);
				assertEquals(truth.keySet(), found.keySet(), name);
				assertTrue(maxDifference(truth, found) < 1e-6, name + ": weights differ by " + maxDifference(truth, found));
				assertEquals(100, splits.getFit(), 1e-3, name + ": fit");
			}
		}
	}

	/**
	 * multiplying all distances by a constant must multiply all weights by it and change nothing else. Split decomposition
	 * and the Buneman tree once dropped splits lighter than a fixed 1e-7 and 1e-8
	 */
	@Test
	void theSplitsDoNotDependOnTheUnitOfTheDistances() throws Exception {
		var random = new Random(16);
		var n = 8;
		for (var r = 0; r < 30; r++) {
			var d = noisy(metric(randomTree(n, random), n), 0.1, random);
			for (Supplier<Distances2Splits> method : List.<Supplier<Distances2Splits>>of(BunemanTree::new, SplitDecomposition::new))
				for (var scale : new double[]{1e-6, 1e6}) {
					var name = method.get().getClass().getSimpleName() + ", input " + r + ", scale " + scale;
					var plain = weightedSplits(splits(method.get(), d), n, 0);
					var scaled = weightedSplits(splits(method.get(), scaled(d, scale)), n, 0);
					assertEquals(plain.keySet(), scaled.keySet(), name);
					for (var entry : plain.entrySet())
						assertEquals(scale * entry.getValue(), scaled.get(entry.getKey()), 1e-5 * scale * entry.getValue() + 1e-9 * scale, name);
				}
		}
	}

	/**
	 * one, two and three taxa, all distances zero, two identical taxa. The Buneman tree and split decomposition once
	 * failed on one taxon and reported a fit of NaN for distances that are all zero
	 */
	@Test
	void smallAndDegenerateInput() throws Exception {
		for (var entry : degenerateDistances().entrySet())
			for (var method : METHODS) {
				var name = method.get().getClass().getSimpleName() + ", " + entry.getKey();
				var splits = splits(method.get(), entry.getValue());
				for (var s : splits.getSplits()) assertTrue(Double.isFinite(s.getWeight()) && s.getWeight() >= 0, name + ": weight " + s.getWeight());
				assertTrue(Double.isFinite(splits.getFit()), name + ": fit " + splits.getFit());
			}
	}
}
