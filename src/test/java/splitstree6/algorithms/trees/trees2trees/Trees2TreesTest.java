/*
 *  Trees2TreesTest.java Copyright (C) 2026 Daniel H. Huson
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

package splitstree6.algorithms.trees.trees2trees;

import jloda.graph.Node;
import jloda.phylo.PhyloTree;
import jloda.util.progress.ProgressSilent;
import org.junit.jupiter.api.Test;
import splitstree6.data.TaxaBlock;
import splitstree6.data.TreesBlock;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static splitstree6.testing.TestUtils.*;

/**
 * the methods that select, filter, reroot, reorder and summarize trees
 */
public class Trees2TreesTest {
	private static TreesBlock run(Trees2Trees algorithm, TreesBlock trees, int n) throws Exception {
		var child = new TreesBlock();
		algorithm.compute(new ProgressSilent(), taxa(n), trees, child);
		return child;
	}

	private static List<Integer> leafOrder(PhyloTree tree) {
		var result = new ArrayList<Integer>();
		leafOrder(tree, tree.getRoot(), result);
		return result;
	}

	private static void leafOrder(PhyloTree tree, Node v, List<Integer> result) {
		for (var t : tree.getTaxa(v)) result.add(t);
		for (var e : v.outEdges()) leafOrder(tree, e.getTarget(), result);
	}

	private static double height(PhyloTree tree, Node v) {
		var max = 0.0;
		for (var e : v.outEdges()) max = Math.max(max, tree.getWeight(e) + height(tree, e.getTarget()));
		return max;
	}

	// ---------- selecting and filtering ----------

	@Test
	void treeSelector() throws Exception {
		var trees = trees("((t1,t2),(t3,t4));", "((t1,t3),(t2,t4));", "((t1,t4),(t2,t3));");
		for (var which = 1; which <= 3; which++) {
			var selector = new TreeSelector();
			selector.setOptionWhich(which);
			var child = run(selector, trees, 4);
			assertEquals(1, child.getNTrees());
			assertEquals(clusters(trees.getTree(which)), clusters(child.getTree(1)));
		}
		var selector = new TreeSelector();
		selector.setOptionWhich(7);
		assertEquals(clusters(trees.getTree(3)), clusters(run(selector, trees, 4).getTree(1)));
	}

	@Test
	void treesFilter() throws Exception {
		var trees = trees("((t1,t2),(t3,t4));", "((t1,t3),(t2,t4));", "((t1,t4),(t2,t3));");
		var filter = new TreesFilter();
		assertEquals(3, run(filter, trees, 4).getNTrees());
		filter.setOptionDisabledTrees(new String[]{"tree-2"});
		var child = run(filter, trees, 4);
		assertEquals(2, child.getNTrees());
		assertEquals(List.of("tree-1", "tree-3"), child.getTrees().stream().map(PhyloTree::getName).toList());
		assertFalse(child.isPartial());
	}

	@Test
	void treesFilterMore() throws Exception {
		var trees = trees("((t1:1,t2:1):1,(t3:1,t4:1):1);", "((t1:1,t3:1):1,t2:1);", "((t1:5,t4:5):5,(t2:5,t3:5):5);");
		var filter = new TreesFilterMore();
		filter.setOptionRequireAllTaxa(true);
		assertEquals(List.of("tree-1", "tree-3"), run(filter, trees, 4).getTrees().stream().map(PhyloTree::getName).toList());
		filter = new TreesFilterMore();
		filter.setOptionMinNumberOfTaxa(4);
		assertEquals(2, run(filter, trees, 4).getNTrees());
		filter = new TreesFilterMore();
		filter.setOptionMinTotalTreeLength(10);
		var child = run(filter, trees, 4);
		assertEquals(List.of("tree-3"), child.getTrees().stream().map(PhyloTree::getName).toList());
		assertFalse(child.isPartial());
		filter = new TreesFilterMore();
		filter.setOptionMinNumberOfTaxa(2);
		assertTrue(run(filter, trees, 4).isPartial());
	}

	/**
	 * rooted: trees with the same clusters are the same. Unrooted: trees with the same splits are the same, wherever the
	 * root is. Two trees that differed only in a part that does not contain the first taxon once counted as the same
	 */
	@Test
	void uniqueTopologies() throws Exception {
		var trees = trees(
				"(((t1,t2),t3),((t4,t5),t6));",
				"(((t2,t1),t3),(t6,(t5,t4)));", // the same
				"((t1,t2),(t3,((t4,t5),t6)));", // the same when unrooted
				"(((t1,t2),t3),((t4,t6),t5));", // differs in a part without t1
				"(((t1,t3),t2),((t4,t5),t6));");
		var rooted = run(new UniqueTopologies(), trees, 6);
		assertEquals(4, rooted.getNTrees());
		assertEquals(clusters(trees.getTree(1)), clusters(rooted.getTree(1)));
		assertEquals(clusters(trees.getTree(3)), clusters(rooted.getTree(2)));
		for (var v : rooted.getTree(1).leaves()) assertEquals("t" + rooted.getTree(1).getTaxon(v), rooted.getTree(1).getLabel(v));
		var filter = new UniqueTopologies();
		filter.setOptionUnrooted(true);
		var unrooted = run(filter, trees, 6);
		assertEquals(3, unrooted.getNTrees());
		var found = new ArrayList<Set<BitSet>>();
		for (var tree : unrooted.getTrees()) found.add(weightedSplits(tree, 6).keySet());
		assertEquals(List.of(weightedSplits(trees.getTree(1), 6).keySet(), weightedSplits(trees.getTree(4), 6).keySet(), weightedSplits(trees.getTree(5), 6).keySet()), found);
	}

	// ---------- edges ----------

	@Test
	void shortEdgesAreContracted() throws Exception {
		var trees = trees("(((t1:1,t2:1):0.1,t3:1):2,((t4:1,t5:0.01):3,t6:1):0.2);");
		var filter = new TreesEdgesFilter();
		filter.setOptionMinEdgeLength(0.5);
		var tree = run(filter, trees, 6).getTree(1);
		// the edges above {1,2} and above {4,5,6} are contracted; the short edge to t5 is not, it leads to a leaf
		assertEquals(Set.of(set(1), set(2), set(3), set(4), set(5), set(6), set(1, 2, 3), set(4, 5)), clusters(tree));
		assertEquals(3, trees.getTree(1).getRoot().getOutDegree() + 1, "the input tree is not changed");
		assertEquals(clusters(tree("(((t1,t2),t3),((t4,t5),t6));")), clusters(trees.getTree(1)), "the input tree is not changed");
	}

	@Test
	void uniformLengthsAndRescaling() throws Exception {
		var random = new Random(81);
		var trees = trees(randomNewick(10, random, true), randomNewick(10, random, true));
		var filter = new TreesEdgesFilter();
		filter.setOptionUniformEdgeLengths(true);
		for (var tree : run(filter, trees, 10).getTrees()) for (var e : tree.edges()) assertEquals(1.0, tree.getWeight(e));
		filter = new TreesEdgesFilter();
		filter.setOptionRescale(true);
		var child = run(filter, trees, 10);
		for (var t = 1; t <= 2; t++) {
			var tree = child.getTree(t);
			assertEquals(100.0, tree.edgeStream().mapToDouble(tree::getWeight).sum(), 1e-9);
			var before = weightedSplits(trees.getTree(t), 10);
			var after = weightedSplits(tree, 10);
			var factor = 100.0 / before.values().stream().mapToDouble(w -> w).sum();
			before.replaceAll((side, w) -> factor * w);
			assertTrue(maxDifference(before, after) < 1e-9);
		}
	}

	/**
	 * an edge is kept if at least the given percentage of the trees is compatible with it. At 100 percent every edge
	 * was once contracted, also in trees that all agree
	 */
	@Test
	void concordanceFilter() throws Exception {
		var same = trees("(((t1,t2),t3),((t4,t5),t6));", "(((t1,t2),t3),((t4,t5),t6));", "(((t1,t2),t3),((t4,t5),t6));");
		for (var percent : new double[]{50, 100}) {
			var filter = new TreesConcordanceFilter();
			filter.setOptionMinConcordance(percent);
			for (var tree : run(filter, same, 6).getTrees()) assertEquals(clusters(same.getTree(1)), clusters(tree), "identical trees, " + percent + " percent");
		}
		// {1,2} is in all four trees, {1,2,3} and {4,5,6} conflict with the last tree, {4,5} conflicts with two trees
		var trees = trees("(((t1,t2),t3),((t4,t5),t6));", "(((t1,t2),t3),((t4,t5),t6));", "(((t1,t2),t3),((t4,t6),t5));", "(((t1,t2),(t3,t4)),(t5,t6));");
		var leaves = Set.of(set(1), set(2), set(3), set(4), set(5), set(6));
		var expected = new HashMap<Double, Set<BitSet>>();
		expected.put(100.0, Set.of(set(1, 2)));
		expected.put(75.0, Set.of(set(1, 2), set(4, 5, 6), set(1, 2, 3)));
		expected.put(50.0, Set.of(set(1, 2), set(4, 5, 6), set(1, 2, 3), set(4, 5)));
		for (var percent : expected.keySet()) {
			var filter = new TreesConcordanceFilter();
			filter.setOptionMinConcordance(percent);
			var found = clusters(run(filter, trees, 6).getTree(1));
			found.removeAll(leaves);
			assertEquals(expected.get(percent), found, percent + " percent");
		}
	}

	// ---------- rerooting and reordering ----------

	@Test
	void midpointRooting() throws Exception {
		var random = new Random(82);
		for (var r = 0; r < 20; r++) {
			var n = 12;
			var trees = trees(randomNewick(n, random, true));
			var algorithm = new RerootOrReorderTrees();
			algorithm.setOptionRootBy(RerootOrReorderTrees.RootBy.MidPoint);
			var child = run(algorithm, trees, n);
			var tree = child.getTree(1);
			assertTrue(child.isRooted());
			var before = weightedSplits(trees.getTree(1), n);
			var after = weightedSplits(tree, n);
			assertEquals(before.keySet(), after.keySet(), "tree " + r);
			assertTrue(maxDifference(before, after) < 1e-9, "tree " + r);
			var depths = new ArrayList<Double>();
			for (var e : tree.getRoot().outEdges()) depths.add(tree.getWeight(e) + height(tree, e.getTarget()));
			depths.sort(Comparator.reverseOrder());
			assertEquals(depths.get(0), depths.get(1), 1e-9, "tree " + r + ": not rooted at the midpoint");
			var d = pathDistances(tree, n);
			var diameter = 0.0;
			for (var row : d) for (var x : row) diameter = Math.max(diameter, x);
			assertEquals(diameter / 2, depths.get(0), 1e-9, "tree " + r + ": not on a longest path");
		}
	}

	/**
	 * after rooting by outgroup, the outgroup taxa are the taxa below one child of the root
	 */
	@Test
	void outgroupRooting() throws Exception {
		var random = new Random(83);
		for (var r = 0; r < 20; r++) {
			var n = 12;
			var trees = trees(randomNewick(n, random, true));
			var candidates = new ArrayList<>(clusters(trees.getTree(1)));
			candidates.sort(Comparator.comparing(BitSet::toString));
			var outgroup = candidates.get(random.nextInt(candidates.size()));
			if (r % 2 == 1) { // the other side of the edge
				var other = new BitSet();
				other.set(1, n + 1);
				other.andNot(outgroup);
				outgroup = other;
			}
			var algorithm = new RerootOrReorderTrees();
			algorithm.setOptionRootBy(RerootOrReorderTrees.RootBy.OutGroup);
			algorithm.setOptionOutGroupTaxa(outgroup.stream().mapToObj(t -> "t" + t).toArray(String[]::new));
			var tree = run(algorithm, trees, n).getTree(1);
			var before = weightedSplits(trees.getTree(1), n);
			var after = weightedSplits(tree, n);
			assertEquals(before.keySet(), after.keySet(), "tree " + r);
			assertTrue(maxDifference(before, after) < 1e-9, "tree " + r);
			assertEquals(2, tree.getRoot().getOutDegree(), "tree " + r);
			assertTrue(clusters(tree).contains(outgroup), "tree " + r + ": outgroup " + outgroup + " is not a clade of " + clusters(tree));
			var below = new HashSet<BitSet>();
			for (var e : tree.getRoot().outEdges()) {
				var set = new BitSet();
				var sub = new ArrayList<Integer>();
				leafOrder(tree, e.getTarget(), sub);
				for (var t : sub) set.set(t);
				below.add(set);
			}
			assertTrue(below.contains(outgroup), "tree " + r + ": the outgroup is not below a child of the root");
		}
	}

	@Test
	void reordering() throws Exception {
		var random = new Random(84);
		var n = 15;
		var trees = trees(randomNewick(n, random, true), randomNewick(n, random, true));
		for (var reorder : RerootOrReorderTrees.Reorder.values()) {
			var algorithm = new RerootOrReorderTrees();
			algorithm.setOptionReorder(reorder);
			var child = run(algorithm, trees, n);
			for (var t = 1; t <= 2; t++) {
				var tree = child.getTree(t);
				assertEquals(clusters(trees.getTree(t)), clusters(tree), reorder + ": the tree is changed");
				assertEquals(n, new HashSet<>(leafOrder(tree)).size());
				if (reorder == RerootOrReorderTrees.Reorder.ByTaxa) {
					// at every node the children are in the order of their smallest taxon
					for (var v : tree.nodes()) {
						var previous = 0;
						for (var e : v.outEdges()) {
							var sub = new ArrayList<Integer>();
							leafOrder(tree, e.getTarget(), sub);
							var smallest = Collections.min(sub);
							assertTrue(smallest > previous, "children not ordered by taxa");
							previous = smallest;
						}
					}
					assertEquals(1, leafOrder(tree).get(0));
				}
				if (reorder == RerootOrReorderTrees.Reorder.LadderizedUp || reorder == RerootOrReorderTrees.Reorder.LadderizedDown) {
					for (var v : tree.nodes()) {
						var previous = (reorder == RerootOrReorderTrees.Reorder.LadderizedUp ? Integer.MAX_VALUE : 0);
						for (var e : v.outEdges()) {
							var levels = levels(e.getTarget());
							assertTrue(reorder == RerootOrReorderTrees.Reorder.LadderizedUp ? levels <= previous : levels >= previous, reorder + ": children not ordered by height");
							previous = levels;
						}
					}
				}
			}
		}
	}

	private static int levels(Node v) {
		var max = 0;
		for (var e : v.outEdges()) max = Math.max(max, levels(e.getTarget()));
		return max + 1;
	}

	// ---------- consensus trees ----------

	private static Map<BitSet, Integer> clusterCounts(TreesBlock trees) {
		var counts = new HashMap<BitSet, Integer>();
		for (var tree : trees.getTrees()) for (var cluster : clusters(tree)) counts.merge(cluster, 1, Integer::sum);
		return counts;
	}

	@Test
	void rootedConsensusTree() throws Exception {
		var random = new Random(85);
		var n = 8;
		for (var r = 0; r < 10; r++) {
			// trees that share much: a random tree, and trees in which some taxa are placed elsewhere
			var newick = new String[9];
			var base = randomNewick(n, random, true);
			for (var i = 0; i < newick.length; i++) {
				newick[i] = base;
				if (i % 3 != 0) {
					int a = 1 + random.nextInt(n), b = 1 + random.nextInt(n);
					newick[i] = base.replace("t" + a + ":", "x:").replace("t" + b + ":", "t" + a + ":").replace("x:", "t" + b + ":");
				}
			}
			var trees = trees(newick);
			var counts = clusterCounts(trees);
			for (var consensus : RootedConsensusTree.Consensus.values()) {
				var algorithm = new RootedConsensusTree();
				algorithm.setOptionConsensus(consensus);
				var child = run(algorithm, trees, n);
				assertEquals(1, child.getNTrees());
				var tree = child.getTree(1);
				var found = clusters(tree);
				var expected = new HashSet<BitSet>();
				for (var cluster : counts.keySet()) {
					if (consensus == RootedConsensusTree.Consensus.Strict && counts.get(cluster) == newick.length) expected.add(cluster);
					if (consensus != RootedConsensusTree.Consensus.Strict && 2 * counts.get(cluster) > newick.length) expected.add(cluster);
				}
				if (consensus == RootedConsensusTree.Consensus.Greedy) {
					assertTrue(found.containsAll(expected), "greedy lacks a majority cluster");
					for (var c1 : found)
						for (var c2 : found) {
							var both = (BitSet) c1.clone();
							both.and(c2);
							assertTrue(both.isEmpty() || both.equals(c1) || both.equals(c2), "greedy: clusters not nested");
						}
					// no cluster that was left out fits in and occurs in more trees than one that was taken
				} else assertEquals(expected, found, consensus + ", data set " + r);
				var taxa = new HashSet<>(leafOrder(tree));
				assertEquals(n, taxa.size());
				for (var v : tree.leaves()) assertEquals("t" + tree.getTaxon(v), tree.getLabel(v), consensus + ": leaf without its label");
			}
		}
	}

	/**
	 * the consensus tree of unrooted trees has the splits that are in all trees, or in more than half of them, with the
	 * mean of their lengths
	 */
	@Test
	void consensusTree() throws Exception {
		var trees = trees(
				"(((t1:1,t2:1):1,t3:1):1,t4:1,(t5:1,t6:1):2);",
				"(((t1:1,t2:1):2,t3:1):2,t4:1,(t5:1,t6:1):4);",
				"(((t1:1,t2:1):3,t3:1):6,t4:1,t5:1,t6:1);",
				"((t1:1,t2:1):4,(t3:1,t4:1):7,t5:1,t6:1);",
				"(((t1:1,t2:1):10,t5:1):1,(t3:1,t4:1):9,t6:1);");
		var expected = new HashMap<ConsensusTree.Consensus, Map<BitSet, Double>>();
		expected.put(ConsensusTree.Consensus.Strict, Map.of(set(1, 2), 4.0));
		expected.put(ConsensusTree.Consensus.Majority, Map.of(set(1, 2), 4.0, set(1, 2, 3), 3.0));
		expected.put(ConsensusTree.Consensus.Greedy, Map.of(set(1, 2), 4.0, set(1, 2, 3), 3.0, set(1, 2, 3, 4), 3.0));
		for (var consensus : ConsensusTree.Consensus.values()) {
			var algorithm = new ConsensusTree();
			algorithm.setOptionConsensus(consensus);
			var child = run(algorithm, trees, 6);
			assertEquals(1, child.getNTrees());
			var found = weightedSplits(child.getTree(1), 6);
			found.keySet().removeIf(side -> isTrivial(side, 6));
			assertEquals(expected.get(consensus).keySet(), found.keySet(), consensus.toString());
			assertTrue(maxDifference(expected.get(consensus), found) < 1e-6, consensus + ": " + found);
		}
	}

	// ---------- taxa ----------

	@Test
	void taxaFilter() throws Exception {
		var random = new Random(86);
		var n = 12;
		var trees = trees(randomNewick(n, random, true), randomNewick(n, random, true));
		var modified = new TaxaBlock();
		modified.addTaxaByNames(List.of("t3", "t11", "t5", "t8", "t1", "t9"));
		var keep = set(3, 11, 5, 8, 1, 9);
		var child = new TreesBlock();
		new TreesTaxaFilter().filter(new ProgressSilent(), taxa(n), modified, trees, child);
		assertEquals(2, child.getNTrees());
		for (var t = 1; t <= 2; t++) {
			var expected = new HashSet<BitSet>();
			for (var cluster : clusters(trees.getTree(t))) {
				var induced = new BitSet();
				for (var x = cluster.nextSetBit(0); x >= 0; x = cluster.nextSetBit(x + 1)) if (keep.get(x)) induced.set(modified.indexOf("t" + x));
				if (induced.cardinality() > 0 && induced.cardinality() < 6) expected.add(induced);
			}
			assertEquals(expected, clusters(child.getTree(t)), "tree " + t);
			// the distances between the remaining taxa are unchanged
			var before = pathDistances(trees.getTree(t), n);
			var after = pathDistances(child.getTree(t), 6);
			for (var a : new int[]{3, 11, 5, 8, 1, 9})
				for (var b : new int[]{3, 11, 5, 8, 1, 9})
					assertEquals(before[a - 1][b - 1], after[modified.indexOf("t" + a) - 1][modified.indexOf("t" + b) - 1], 1e-9);
		}
	}

	// ---------- species from a trait ----------

	/**
	 * taxa t1, t2 have trait value 1, t3 and t4 have 2 and t5 has 3. Tight (lacy) species: the largest clades with one
	 * value. Loose species: the smallest clades that do not share a value with the rest
	 */
	@Test
	void looseAndLacySpecies() throws Exception {
		var taxa = taxa(5);
		var traits = new splitstree6.data.TraitsBlock();
		traits.setDimensions(5, 1);
		traits.setTraitLabel(1, "region");
		var values = new int[]{0, 1, 1, 2, 2, 3};
		for (var t = 1; t <= 5; t++) traits.setTraitValue(t, 1, values[t]);
		taxa.setTraitsBlock(traits);
		var algorithm = new LooseAndLacy();
		algorithm.setOptionSpeciesDefinition(LooseAndLacy.SpeciesDefinition.Both);
		var child = new TreesBlock();
		algorithm.compute(new ProgressSilent(), taxa, trees("((t1:1,t2:1):1,(t3:1,(t4:1,t5:1):1):1);"), child);
		assertEquals(2, child.getNTrees());
		var leaves = Set.of(set(1), set(2), set(3), set(4), set(5));
		var loose = clusters(child.getTree(1));
		loose.removeAll(leaves);
		assertEquals(Set.of(set(1, 2), set(3, 4, 5)), loose);
		var lacy = clusters(child.getTree(2));
		lacy.removeAll(leaves);
		assertEquals(Set.of(set(1, 2)), lacy);
	}
}
