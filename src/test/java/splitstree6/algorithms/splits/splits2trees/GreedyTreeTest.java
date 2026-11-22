/*
 *  GreedyTreeTest.java Copyright (C) 2026 Daniel H. Huson
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

package splitstree6.algorithms.splits.splits2trees;

import jloda.graph.Node;
import jloda.phylo.PhyloTree;
import jloda.util.progress.ProgressSilent;
import org.junit.jupiter.api.Test;
import splitstree6.data.TreesBlock;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static splitstree6.testing.TestUtils.*;

/**
 * the tree of greedily chosen compatible splits
 */
public class GreedyTreeTest {
	private static PhyloTree run(Map<BitSet, Double> splits, int n) throws Exception {
		var trees = new TreesBlock();
		new GreedyTree().compute(new ProgressSilent(), taxa(n), splitsBlock(splits, n), trees);
		assertEquals(1, trees.getNTrees());
		return trees.getTree(1);
	}

	private static double height(PhyloTree tree, Node v) {
		var max = 0.0;
		for (var e : v.outEdges()) max = Math.max(max, tree.getWeight(e) + height(tree, e.getTarget()));
		return max;
	}

	/**
	 * compatible splits give the tree that has exactly these splits and weights, rooted at the midpoint of a longest
	 * path: the two deepest subtrees of the root are equally deep
	 */
	@Test
	void compatibleSplitsGiveTheirTree() throws Exception {
		var random = new Random(41);
		for (var r = 0; r < 20; r++) {
			var n = 5 + random.nextInt(30);
			var splits = randomTree(n, random);
			var tree = run(splits, n);
			var found = weightedSplits(tree, n);
			assertEquals(splits.keySet(), found.keySet(), "tree " + r);
			assertTrue(maxDifference(splits, found) < 1e-9, "tree " + r);
			var depths = new ArrayList<Double>();
			for (var e : tree.getRoot().outEdges()) depths.add(tree.getWeight(e) + height(tree, e.getTarget()));
			depths.sort(Comparator.reverseOrder());
			assertTrue(depths.size() >= 2, "tree " + r + ": the root has one child");
			assertEquals(depths.get(0), depths.get(1), 1e-9, "tree " + r + ": not rooted at the midpoint");
			for (var v : tree.leaves()) assertEquals("t" + tree.getTaxon(v), tree.getLabel(v));
		}
	}

	@Test
	void theHeavierOfTwoIncompatibleSplitsIsTaken() throws Exception {
		var splits = new HashMap<BitSet, Double>();
		for (var t = 1; t <= 5; t++) splits.put(canonical(set(t), 5), 1.0);
		splits.put(set(1, 2), 2.0);
		splits.put(set(1, 3), 3.0);
		splits.put(set(1, 2, 3), 1.5);
		var expected = new HashMap<>(splits);
		expected.remove(set(1, 2));
		assertEquals(expected, weightedSplits(run(splits, 5), 5));
	}

	@Test
	void degenerateInput() throws Exception {
		for (var n = 2; n <= 3; n++) {
			var splits = new HashMap<BitSet, Double>();
			for (var t = 1; t <= n; t++) splits.merge(canonical(set(t), n), 1.0, Double::sum);
			var tree = run(splits, n);
			var taxa = new BitSet();
			for (var v : tree.nodes()) for (var t : tree.getTaxa(v)) taxa.set(t);
			assertEquals(n, taxa.cardinality(), n + " taxa");
		}
	}
}
