/*
 *  SplitsFilterTest.java Copyright (C) 2026 Daniel H. Huson
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

package splitstree6.algorithms.splits.splits2splits;

import jloda.util.progress.ProgressSilent;
import org.junit.jupiter.api.Test;
import splitstree6.data.SplitsBlock;
import splitstree6.data.TaxaBlock;
import splitstree6.splits.ASplit;
import splitstree6.splits.BiPartition;
import splitstree6.splits.Compatibility;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static splitstree6.testing.TestUtils.*;

/**
 * the filters on splits
 */
public class SplitsFilterTest {
	private static SplitsBlock run(Splits2Splits filter, SplitsBlock parent, int n) throws Exception {
		var child = new SplitsBlock();
		filter.compute(new ProgressSilent(), taxa(n), parent, child);
		return child;
	}

	/**
	 * splits on 8 taxa: a tree, plus two splits that are not compatible with it
	 */
	private static Map<BitSet, Double> example() {
		var splits = new HashMap<BitSet, Double>();
		for (var t = 1; t <= 8; t++) splits.put(canonical(set(t), 8), 1.0);
		splits.put(set(1, 2), 5.0);
		splits.put(set(1, 2, 3), 4.0);
		splits.put(set(1, 2, 3, 4), 3.0);
		splits.put(set(1, 2, 3, 4, 5, 6), 6.0);
		splits.put(set(1, 8), 2.0); // not compatible with {1,2}, {1,2,3}, {1,2,3,4}, {1,..,6}; circular with them
		splits.put(set(1, 3, 5, 7), 0.5); // compatible with no other split that is not trivial
		return splits;
	}

	@Test
	void withoutOptionsAllSplitsAreKept() throws Exception {
		var parent = splitsBlock(example(), 8);
		var filter = new SplitsFilter();
		var child = run(filter, parent, 8);
		assertEquals(example(), weightedSplits(child, 8, 0));
		assertArrayEquals(parent.getCycle(), child.getCycle());
		assertEquals(parent.getCompatibility(), child.getCompatibility());
		assertFalse(filter.isActive());
	}

	@Test
	void weightThreshold() throws Exception {
		var filter = new SplitsFilter();
		filter.setOptionWeightThreshold(3);
		var expected = new HashMap<>(example());
		expected.values().removeIf(w -> w < 3);
		var child = run(filter, splitsBlock(example(), 8), 8);
		assertEquals(expected, weightedSplits(child, 8, 0));
		assertTrue(filter.isActive());
		assertEquals(Compatibility.compatible, child.getCompatibility());
	}

	@Test
	void confidenceThreshold() throws Exception {
		var parent = splitsBlock(example(), 8);
		for (var split : parent.getSplits()) split.setConfidence(split.getWeight() * 10);
		var filter = new SplitsFilter();
		filter.setOptionConfidenceThreshold(40);
		var expected = new HashMap<>(example());
		expected.values().removeIf(w -> w < 4);
		assertEquals(expected, weightedSplits(run(filter, parent, 8), 8, 0));
	}

	/**
	 * greedily compatible: by decreasing weight, each split that is compatible with those taken so far
	 */
	@Test
	void greedyCompatible() throws Exception {
		var filter = new SplitsFilter();
		filter.setOptionFilterAlgorithm(SplitsFilter.FilterAlgorithm.GreedyCompatible);
		var expected = new HashMap<>(example());
		expected.remove(set(1, 8));
		expected.remove(set(1, 3, 5, 7));
		var child = run(filter, splitsBlock(example(), 8), 8);
		assertEquals(expected, weightedSplits(child, 8, 0));
		assertEquals(Compatibility.compatible, child.getCompatibility());
	}

	@Test
	void greedyCircularAndWeaklyCompatible() throws Exception {
		var filter = new SplitsFilter();
		filter.setOptionFilterAlgorithm(SplitsFilter.FilterAlgorithm.GreedyCircular);
		var expected = new HashMap<>(example());
		expected.remove(set(1, 3, 5, 7));
		var child = run(filter, splitsBlock(example(), 8), 8);
		assertEquals(expected, weightedSplits(child, 8, 0));

		filter = new SplitsFilter();
		filter.setOptionFilterAlgorithm(SplitsFilter.FilterAlgorithm.GreedyWeaklyCompatible);
		child = run(filter, splitsBlock(example(), 8), 8);
		var splits = child.getSplits();
		for (var a = 0; a < splits.size(); a++)
			for (var b = a + 1; b < splits.size(); b++)
				for (var c = b + 1; c < splits.size(); c++) assertTrue(BiPartition.areWeaklyCompatible(splits.get(a), splits.get(b), splits.get(c)));
		assertTrue(weightedSplits(child, 8, 0).keySet().containsAll(expected.keySet()));
	}

	/**
	 * the splits that are compatible with all others
	 */
	@Test
	void blobTree() throws Exception {
		var filter = new SplitsFilter();
		filter.setOptionFilterAlgorithm(SplitsFilter.FilterAlgorithm.BlobTree);
		var input = new HashMap<>(example());
		input.remove(set(1, 3, 5, 7));
		var expected = new HashMap<BitSet, Double>();
		for (var t = 1; t <= 8; t++) expected.put(canonical(set(t), 8), 1.0);
		assertEquals(expected, weightedSplits(run(filter, splitsBlock(input, 8), 8), 8, 0));
	}

	@Test
	void splitLabelsFollowTheirSplits() throws Exception {
		var parent = splitsBlock(example(), 8);
		for (var s = 1; s <= parent.getNsplits(); s++) parent.getSplitLabels().put(s, "w" + parent.get(s).getWeight());
		var filter = new SplitsFilter();
		filter.setOptionWeightThreshold(3);
		var child = run(filter, parent, 8);
		for (var s = 1; s <= child.getNsplits(); s++) assertEquals("w" + child.get(s).getWeight(), child.getSplitLabels().get(s));
	}

	/**
	 * the weights slider keeps the splits from the threshold up, and adds the trivial splits that are then missing with
	 * weight 0
	 */
	@Test
	void weightsSlider() throws Exception {
		var filter = new WeightsSlider();
		filter.setOptionWeightThreshold(3);
		var child = run(filter, splitsBlock(example(), 8), 8);
		var expected = new HashMap<>(example());
		expected.values().removeIf(w -> w < 3);
		assertEquals(expected, weightedSplits(child, 8, 0));
		assertEquals(4 + 8, child.getNsplits());
		assertEquals(9, child.getCycle().length);
		filter.setOptionWeightThreshold(0);
		assertEquals(example(), weightedSplits(run(filter, splitsBlock(example(), 8), 8), 8, 0));
	}

	/**
	 * k splits that are pairwise incompatible give a k-dimensional cube in the network
	 */
	private static Map<BitSet, Double> cube(int k) {
		var n = 1 << k;
		var splits = new HashMap<BitSet, Double>();
		for (var bit = 0; bit < k; bit++) {
			var side = new BitSet();
			for (var t = 0; t < n; t++) if ((t & (1 << bit)) == 0) side.set(t + 1);
			splits.put(side, 1.0 + bit);
		}
		return splits;
	}

	private static int largestSetOfPairwiseIncompatibleSplits(List<ASplit> splits) {
		var best = 0;
		for (var mask = 1; mask < (1 << splits.size()); mask++) {
			if (Integer.bitCount(mask) <= best) continue;
			var ok = true;
			for (var a = 0; ok && a < splits.size(); a++)
				for (var b = a + 1; ok && b < splits.size(); b++)
					if ((mask & (1 << a)) != 0 && (mask & (1 << b)) != 0 && BiPartition.areCompatible(splits.get(a), splits.get(b))) ok = false;
			if (ok) best = Integer.bitCount(mask);
		}
		return best;
	}

	@Test
	void dimensionFilter() throws Exception {
		var n = 32;
		var parent = splitsBlock(cube(5), n);
		assertEquals(5, largestSetOfPairwiseIncompatibleSplits(parent.getSplits()));
		for (var max = 2; max <= 5; max++) {
			var filter = new DimensionFilter();
			filter.setOptionMaxDimension(max);
			var child = run(filter, parent, n);
			assertEquals(max, largestSetOfPairwiseIncompatibleSplits(child.getSplits()), "maximum dimension " + max);
			assertEquals(max, child.getNsplits());
			assertEquals(max < 5, filter.isActive());
			// the splits of smallest weight are removed
			for (var split : child.getSplits()) assertTrue(split.getWeight() > 5 - max);
		}
	}

	/**
	 * the maximum dimension of the splits filter, 4 unless set otherwise, applies unless the filter is greedily
	 * compatible. It once had no effect
	 */
	@Test
	void maximumDimensionOfTheSplitsFilter() throws Exception {
		var n = 32;
		var parent = splitsBlock(cube(5), n);
		var filter = new SplitsFilter();
		assertEquals(4, largestSetOfPairwiseIncompatibleSplits(run(filter, parent, n).getSplits()));
		assertTrue(filter.isActive());
		filter = new SplitsFilter();
		filter.setOptionMaximumDimension(2);
		assertEquals(2, run(filter, parent, n).getNsplits());
		filter = new SplitsFilter();
		filter.setOptionMaximumDimension(0);
		assertEquals(5, run(filter, parent, n).getNsplits());
	}

	/**
	 * splits that are compatible, circular or weakly compatible are passed on unchanged; the filter once returned no
	 * splits at all for them
	 */
	@Test
	void dimensionFilterPassesOnSplitsThatNeedNoFiltering() throws Exception {
		var n = 10;
		var splits = randomTree(n, new Random(31));
		var parent = splitsBlock(splits, n);
		assertEquals(Compatibility.compatible, parent.getCompatibility());
		var filter = new DimensionFilter();
		var child = run(filter, parent, n);
		assertEquals(splits, weightedSplits(child, n, 0));
		assertArrayEquals(parent.getCycle(), child.getCycle());
		assertEquals(Compatibility.compatible, child.getCompatibility());
		assertFalse(filter.isActive());
	}

	/**
	 * restricting splits to a subset of the taxa: the induced splits, with two splits that become the same added up,
	 * and none with an empty side
	 */
	@Test
	void taxaFilter() throws Exception {
		var n = 8;
		var parent = splitsBlock(example(), n);
		var modified = new TaxaBlock();
		modified.addTaxaByNames(List.of("t1", "t2", "t5", "t6", "t7"));
		var child = new SplitsBlock();
		new SplitsTaxaFilter().filter(new ProgressSilent(), taxa(n), modified, parent, child);
		// in the new numbering t1=1, t2=2, t5=3, t6=4, t7=5
		var expected = new HashMap<BitSet, Double>();
		expected.put(canonical(set(1), 5), 1.0 + 2.0); // {1} and {1,8}
		expected.put(canonical(set(2), 5), 1.0);
		expected.put(canonical(set(3), 5), 1.0);
		expected.put(canonical(set(4), 5), 1.0);
		expected.put(canonical(set(5), 5), 1.0 + 6.0); // {7} and {1,..,6}
		expected.put(set(1, 2), 5.0 + 4.0 + 3.0); // {1,2}, {1,2,3}, {1,2,3,4}
		expected.put(set(1, 3, 5), 0.5);
		var found = weightedSplits(child, 5, 0);
		assertEquals(expected.keySet(), found.keySet());
		assertTrue(maxDifference(expected, found) < 1e-12, "found " + found);
		for (var split : child.getSplits()) {
			assertTrue(split.getA().cardinality() > 0 && split.getB().cardinality() > 0, "split with an empty side");
			assertEquals(5, split.ntax());
		}
		assertEquals(expected.size(), child.getNsplits(), "each split once");
		assertEquals(6, child.getCycle().length);
		var cycle = new BitSet();
		for (var i = 1; i <= 5; i++) cycle.set(child.getCycle()[i]);
		assertEquals(set(1, 2, 3, 4, 5), cycle);
	}

	/**
	 * without the first taxon, whose side of each split is the one that is stored
	 */
	@Test
	void taxaFilterWithoutTheFirstTaxon() throws Exception {
		var modified = new TaxaBlock();
		modified.addTaxaByNames(List.of("t2", "t3", "t8"));
		var child = new SplitsBlock();
		new SplitsTaxaFilter().filter(new ProgressSilent(), taxa(8), modified, splitsBlock(example(), 8), child);
		// in the new numbering t2=1, t3=2, t8=3
		var expected = new HashMap<BitSet, Double>();
		expected.put(set(1), 1.0 + 5.0); // {2} and {1,2}
		expected.put(set(1, 3), 1.0 + 0.5); // {3} and {1,3,5,7}
		expected.put(set(1, 2), 1.0 + 4.0 + 3.0 + 6.0 + 2.0); // {8}, {1,2,3}, {1,2,3,4}, {1,..,6} and {1,8}
		var found = weightedSplits(child, 3, 0);
		assertEquals(expected.keySet(), found.keySet());
		assertTrue(maxDifference(expected, found) < 1e-12, "found " + found);
		assertEquals(3, child.getNsplits());
		for (var split : child.getSplits()) assertTrue(split.getA().cardinality() > 0 && split.getB().cardinality() > 0, "split with an empty side");
	}

	@Test
	void taxaFilterWithAllTaxa() throws Exception {
		var parent = splitsBlock(example(), 8);
		var child = new SplitsBlock();
		new SplitsTaxaFilter().filter(new ProgressSilent(), taxa(8), taxa(8), parent, child);
		assertEquals(example(), weightedSplits(child, 8, 0));
		assertArrayEquals(parent.getCycle(), child.getCycle());
	}
}
