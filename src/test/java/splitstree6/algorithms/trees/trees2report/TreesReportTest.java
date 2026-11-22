/*
 *  TreesReportTest.java Copyright (C) 2026 Daniel H. Huson
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

package splitstree6.algorithms.trees.trees2report;

import jloda.phylo.PhyloTree;
import jloda.util.progress.ProgressSilent;
import org.junit.jupiter.api.Test;
import splitstree6.data.ReportBlock;
import splitstree6.data.TreesBlock;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static splitstree6.testing.TestUtils.*;

/**
 * the reports on trees
 */
public class TreesReportTest {
	private static String report(Trees2ReportBase algorithm, TreesBlock trees, int n) throws Exception {
		var report = new ReportBlock();
		algorithm.compute(new ProgressSilent(), taxa(n), trees, report);
		return text(report);
	}

	// ---------- Robinson-Foulds ----------

	/**
	 * the number of splits that are in one of two trees and not in the other
	 */
	@Test
	void robinsonFouldsDistance() throws Exception {
		var trees = trees("((t1:1,t2:1):1,(t3:1,t4:1):1,(t5:1,t6:1):1);",
				"((t1:2,t2:3):4,(t3:5,t4:6):7,(t5:8,t6:9):1);", // the same tree with other lengths
				"(((t1,t2),t3),(t4,(t5,t6)));", // the same, rooted differently: t3 and t4 are not a pair
				"((t1,t3),(t2,t5),(t4,t6));");
		var text = report(new RobinsonFouldsDistances(), trees, 6);
		assertEquals(0, numberAfter(text, "D(tree-1,tree-2) ="), text);
		assertEquals(2, numberAfter(text, "D(tree-1,tree-3) ="), text);
		assertEquals(6, numberAfter(text, "D(tree-1,tree-4) ="), text);
		assertEquals(6, numberAfter(text, "D(tree-3,tree-4) ="), text);
	}

	@Test
	void robinsonFouldsDistanceAgainstSetsOfSplits() throws Exception {
		var random = new Random(61);
		var n = 10;
		var newick = new String[6];
		for (var i = 0; i < newick.length; i++) newick[i] = randomNewick(n, random, i % 2 == 0);
		var trees = trees(newick);
		var text = report(new RobinsonFouldsDistances(), trees, n);
		for (var i = 1; i <= newick.length; i++)
			for (var j = i + 1; j <= newick.length; j++) {
				var a = weightedSplits(trees.getTree(i), n).keySet();
				var b = weightedSplits(trees.getTree(j), n).keySet();
				var difference = new HashSet<>(a);
				difference.addAll(b);
				var both = new HashSet<>(a);
				both.retainAll(b);
				difference.removeAll(both);
				assertEquals(difference.size(), numberAfter(text, "D(tree-" + i + ",tree-" + j + ") ="), text);
			}
	}

	@Test
	void normalizedRobinsonFouldsDistance() throws Exception {
		var algorithm = new RobinsonFouldsDistances();
		algorithm.optionNormalizeProperty().set(true);
		var trees = trees("((t1,t2),(t3,t4),(t5,t6));", "((t1,t2),(t3,t4),(t5,t6));", "((t1,t3),(t2,t5),(t4,t6));", "(t1,t2,t3,t4,t5,t6);", "(t1,t2,t3,t4,t5,t6);");
		var text = report(algorithm, trees, 6);
		assertEquals(0, numberAfter(text, "D(tree-1,tree-2) ="), text);
		assertEquals(1, numberAfter(text, "D(tree-1,tree-3) ="), text);
		assertEquals(1, numberAfter(text, "D(tree-1,tree-4) ="), text);
		assertFalse(text.contains("NaN"), text);
	}

	// ---------- phylogenetic diversity ----------

	private static final String TREE = "(((t1:1,t2:2):3,t3:4):5,(t4:6,t5:7):8);";

	/**
	 * rooted: the edges on the paths from the root to the taxa (Faith 1992)
	 */
	@Test
	void rootedPhylogeneticDiversity() throws Exception {
		var trees = trees(TREE);
		assertEquals(1 + 3 + 5, numberAfter(PhylogeneticDiversity.report(new ProgressSilent(), taxa(5), trees, set(1), true), "Diversity ="));
		assertEquals(1 + 2 + 3 + 5, numberAfter(PhylogeneticDiversity.report(new ProgressSilent(), taxa(5), trees, set(1, 2), true), "Diversity ="));
		assertEquals(4 + 5 + 7 + 8, numberAfter(PhylogeneticDiversity.report(new ProgressSilent(), taxa(5), trees, set(3, 5), true), "Diversity ="));
		assertEquals(36, numberAfter(PhylogeneticDiversity.report(new ProgressSilent(), taxa(5), trees, set(1, 2, 3, 4, 5), true), "Diversity ="));
	}

	/**
	 * unrooted: the edges of the smallest subtree that connects the taxa. The edges whose taxa below are all selected,
	 * which include the edges to the selected taxa themselves, were once left out
	 */
	@Test
	void unrootedPhylogeneticDiversity() throws Exception {
		var trees = trees(TREE);
		assertEquals(0, numberAfter(PhylogeneticDiversity.report(new ProgressSilent(), taxa(5), trees, set(1), false), "Diversity ="));
		assertEquals(1 + 2, numberAfter(PhylogeneticDiversity.report(new ProgressSilent(), taxa(5), trees, set(1, 2), false), "Diversity ="));
		assertEquals(4 + 5 + 8 + 7, numberAfter(PhylogeneticDiversity.report(new ProgressSilent(), taxa(5), trees, set(3, 5), false), "Diversity ="));
		assertEquals(1 + 3 + 4, numberAfter(PhylogeneticDiversity.report(new ProgressSilent(), taxa(5), trees, set(1, 3), false), "Diversity ="));
		assertEquals(36, numberAfter(PhylogeneticDiversity.report(new ProgressSilent(), taxa(5), trees, set(1, 2, 3, 4, 5), false), "Diversity ="));
		var random = new Random(62);
		var n = 12;
		var tree = trees(randomNewick(n, random, true));
		var splits = weightedSplits(tree.getTree(1), n);
		for (var r = 0; r < 30; r++) {
			var taxa = new BitSet();
			for (var t = 1; t <= n; t++) if (random.nextBoolean()) taxa.set(t);
			var expected = 0.0;
			for (var entry : splits.entrySet()) {
				var inside = (BitSet) entry.getKey().clone();
				inside.and(taxa);
				if (inside.cardinality() > 0 && inside.cardinality() < taxa.cardinality()) expected += entry.getValue();
			}
			assertEquals(expected, numberAfter(PhylogeneticDiversity.report(new ProgressSilent(), taxa(n), tree, taxa, false), "Diversity ="), 1e-3 * Math.max(1, expected), "taxa " + taxa);
		}
	}

	// ---------- diversity indices ----------

	/**
	 * fair proportion: each edge is shared equally among the taxa below it. Equal splits: at each node on the way to the
	 * root the length above is shared equally among the children
	 */
	@Test
	void fairProportionsAndEqualSplits() throws Exception {
		var tree = tree(TREE);
		var fair = new HashMap<Integer, Double>();
		try (var map = TreeDiversityIndex.computeFairProportions(tree)) {
			for (var entry : map.entrySet()) fair.put(tree.getTaxon(entry.getKey()), entry.getValue());
		}
		assertEquals(1 + 3 / 2.0 + 5 / 3.0, fair.get(1), 1e-12);
		assertEquals(2 + 3 / 2.0 + 5 / 3.0, fair.get(2), 1e-12);
		assertEquals(4 + 5 / 3.0, fair.get(3), 1e-12);
		assertEquals(6 + 8 / 2.0, fair.get(4), 1e-12);
		assertEquals(7 + 8 / 2.0, fair.get(5), 1e-12);
		var equal = new HashMap<Integer, Double>();
		var map = TreeDiversityIndex.computeEqualSplits(tree);
		for (var entry : map.entrySet()) equal.put(tree.getTaxon(entry.getKey()), entry.getValue());
		assertEquals(1 + 3 / 2.0 + 5 / 4.0, equal.get(1), 1e-12);
		assertEquals(2 + 3 / 2.0 + 5 / 4.0, equal.get(2), 1e-12);
		assertEquals(4 + 5 / 2.0, equal.get(3), 1e-12);
		assertEquals(6 + 8 / 2.0, equal.get(4), 1e-12);
		assertEquals(7 + 8 / 2.0, equal.get(5), 1e-12);
	}

	@Test
	void diversityIndicesAddUpToTheLengthOfTheTree() throws Exception {
		var random = new Random(63);
		for (var r = 0; r < 10; r++) {
			var n = 20;
			var tree = tree(randomNewick(n, random, true));
			var total = tree.edgeStream().mapToDouble(tree::getWeight).sum();
			try (var map = TreeDiversityIndex.computeFairProportions(tree)) {
				assertEquals(n, map.size());
				assertEquals(total, map.values().stream().mapToDouble(v -> v).sum(), 1e-9);
			}
			var map = TreeDiversityIndex.computeEqualSplits(tree);
			assertEquals(n, map.size());
			assertEquals(total, map.values().stream().mapToDouble(v -> v).sum(), 1e-9);
		}
	}

	@Test
	void diversityIndexReport() throws Exception {
		for (var method : TreeDiversityIndex.Method.values()) {
			var algorithm = new TreeDiversityIndex();
			algorithm.setOptionMethod(method);
			var text = report(algorithm, trees(TREE), 5);
			assertEquals(36, numberAfter(text, "total:"), text);
			assertEquals(11, numberAfter(text, "t5:"), 1e-9, text);
			assertTrue(text.indexOf("t5:") < text.indexOf("t1:"), "by decreasing value: " + text);
		}
	}

	/**
	 * the Shapley values of a tree are those of its splits and add up to the length of the tree
	 */
	@Test
	void unrootedShapleyValues() throws Exception {
		var text = UnrootedShapleyValues.report(new ProgressSilent(), taxa(5), trees(TREE), new BitSet());
		var sum = 0.0;
		for (var t = 1; t <= 5; t++) sum += numberAfter(text, "t" + t + ":");
		assertEquals(36, sum, 1e-3, text);
		// t5: its own edge 7*4/5, the edge above t4 6/(5*4), the edge 13 between {t4,t5} and the rest 13*3/(5*2), ...
		assertEquals(7 * 4 / 5.0 + 6 / 20.0 + 13 * 3 / 10.0 + 4 / 20.0 + 3 * 2 / 15.0 + 1 / 20.0 + 2 / 20.0, numberAfter(text, "t5:"), 1e-3, text);
	}

	// ---------- rooted subtree prune and regraft ----------

	/**
	 * all trees that arise from a rooted binary tree, given by its clusters, by cutting off one subtree and attaching it
	 * elsewhere
	 */
	private static Set<Set<BitSet>> neighbors(Set<BitSet> clusters, int n) {
		var all = new BitSet();
		all.set(1, n + 1);
		var result = new HashSet<Set<BitSet>>();
		for (var pruned : clusters) {
			var inside = new HashSet<BitSet>();
			var rest = new HashSet<BitSet>();
			for (var c : clusters) {
				var without = (BitSet) c.clone();
				without.andNot(pruned);
				if (without.cardinality() == 0) inside.add(c);
				else rest.add(without);
			}
			var remaining = (BitSet) all.clone();
			remaining.andNot(pruned);
			rest.add(remaining);
			for (var target : rest) {
				var neighbor = new HashSet<>(inside);
				for (var c : rest) {
					var above = (BitSet) target.clone();
					above.andNot(c);
					var d = (BitSet) c.clone();
					if (above.cardinality() == 0) d.or(pruned); // c contains the target
					neighbor.add(d);
				}
				neighbor.add(target);
				neighbor.remove(all);
				result.add(neighbor);
			}
		}
		result.remove(clusters);
		return result;
	}

	@Test
	void treesAtRootedSprDistanceOne() throws Exception {
		var random = new Random(64);
		var n = 7;
		var one = 0;
		for (var r = 0; r < 300; r++) {
			var tree1 = tree(randomNewick(n, random, false));
			PhyloTree tree2;
			var neighbors = neighbors(clusters(tree1), n);
			if (r % 3 == 0) tree2 = tree(randomNewick(n, random, false));
			else { // a tree one or two steps away
				var list = new ArrayList<>(neighbors);
				list.sort(Comparator.comparing(Object::toString));
				var target = list.get(random.nextInt(list.size()));
				if (r % 3 == 2) {
					var list2 = new ArrayList<>(neighbors(target, n));
					list2.sort(Comparator.comparing(Object::toString));
					target = list2.get(random.nextInt(list2.size()));
				}
				tree2 = tree(newickFromClusters(target, n));
			}
			var expected = neighbors.contains(clusters(tree2));
			if (expected) one++;
			assertEquals(expected, ListOneRSPRTrees.haveRootedSprDistanceOne(tree1, tree2), "pair " + r + ": " + clusters(tree1) + " and " + clusters(tree2));
		}
		assertTrue(one >= 50, "only " + one + " pairs at distance one");
	}
}
