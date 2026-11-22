/*
 *  UtilsTest.java Copyright (C) 2026 Daniel H. Huson
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

package splitstree6.algorithms.utils;

import jloda.util.progress.ProgressSilent;
import org.junit.jupiter.api.Test;
import splitstree6.data.parts.CharactersType;
import splitstree6.splits.ASplit;
import splitstree6.splits.BiPartition;
import splitstree6.splits.Compatibility;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static splitstree6.testing.TestUtils.*;

/**
 * utilities that several algorithms share
 */
public class UtilsTest {
	/**
	 * the error function, against its series: erf(x) = 2/sqrt(pi) * sum of (-1)^k x^(2k+1) / (k! (2k+1))
	 */
	@Test
	void errorFunction() {
		for (var x = 0.0; x <= 3.0; x += 0.125) {
			double sum = 0, term = x;
			for (var k = 0; k < 200; k++) {
				sum += term / (2 * k + 1);
				term *= -x * x / (k + 1);
			}
			var expected = 2 / Math.sqrt(Math.PI) * sum;
			assertEquals(expected, ErrorFunction.derf(x), 1e-12, "erf(" + x + ")");
			assertEquals(-expected, ErrorFunction.derf(-x), 1e-12, "erf(-" + x + ")");
			assertEquals(1 - expected, ErrorFunction.derfc(x), 1e-12, "erfc(" + x + ")");
		}
		assertEquals(1.0, ErrorFunction.derf(10), 1e-15);
		assertEquals(0.0, ErrorFunction.derfc(30), 1e-15);
	}

	/**
	 * the heaviest set of pairwise compatible splits, against all subsets
	 */
	private static double heaviestCompatible(List<ASplit> splits) {
		var best = 0.0;
		for (var mask = 0; mask < (1 << splits.size()); mask++) {
			var weight = 0.0;
			var ok = true;
			for (var a = 0; ok && a < splits.size(); a++)
				if ((mask & (1 << a)) != 0) {
					weight += splits.get(a).getWeight();
					for (var b = a + 1; ok && b < splits.size(); b++)
						if ((mask & (1 << b)) != 0 && !BiPartition.areCompatible(splits.get(a), splits.get(b))) ok = false;
				}
			if (ok) best = Math.max(best, weight);
		}
		return best;
	}

	/**
	 * for circular splits. (For splits that are not circular the method uses MaxWeightClique, which fails with an
	 * index out of bounds; no algorithm calls it at present)
	 */
	@Test
	void closestTreeIsTheHeaviestSetOfCompatibleSplits() throws Exception {
		var random = new Random(101);
		for (var r = 0; r < 30; r++) {
			var n = 7;
			var splits = randomCircularSplits(n, random);
			var block = splitsBlock(splits, n);
			if (block.getNsplits() > 16) continue;
			var found = ClosestTree.apply(new ProgressSilent(), n, block.getSplits(), block.getCycle());
			assertTrue(Compatibility.isCompatible(found), "data set " + r + ": not compatible");
			assertEquals(heaviestCompatible(block.getSplits()), found.stream().mapToDouble(ASplit::getWeight).sum(), 1e-9, "data set " + r);
		}
	}

	/**
	 * greedy choices: every split that is taken fits with those taken before, and every split that is left out does not
	 */
	@Test
	void greedyChoices() throws Exception {
		var random = new Random(102);
		for (var r = 0; r < 20; r++) {
			var n = 8;
			var splits = new HashMap<BitSet, Double>();
			while (splits.size() < 25) {
				var side = new BitSet();
				for (var t = 1; t <= n; t++) if (random.nextBoolean()) side.set(t);
				if (side.cardinality() > 0 && side.cardinality() < n) splits.put(canonical(side, n), 0.1 + random.nextDouble());
			}
			var block = splitsBlock(splits, n);
			var compatible = GreedyCompatible.apply(block.getSplits(), ASplit::getWeight);
			assertTrue(Compatibility.isCompatible(compatible));
			for (var split : block.getSplits())
				if (!compatible.contains(split)) {
					var conflict = false;
					for (var taken : compatible) if (taken.getWeight() >= split.getWeight() && !BiPartition.areCompatible(split, taken)) conflict = true;
					assertTrue(conflict, "data set " + r + ": a split was left out without a conflict with a heavier one");
				}
			var circular = GreedyCircular.apply(new ProgressSilent(), taxa(n).getTaxaSet(), block.getSplits(), ASplit::getWeight);
			var cycle = new int[n + 1];
			for (var i = 0; i < n; i++) cycle[i + 1] = circular.getSecond().get(i);
			assertTrue(Compatibility.isCyclic(n, circular.getFirst(), cycle), "data set " + r + ": not circular for the ordering returned");
			assertTrue(circular.getFirst().size() >= compatible.size() - 5);
			var weakly = GreedyWeaklyCompatible.apply(new ProgressSilent(), block.getSplits(), ASplit::getWeight);
			for (var a = 0; a < weakly.size(); a++)
				for (var b = a + 1; b < weakly.size(); b++)
					for (var c = b + 1; c < weakly.size(); c++) assertTrue(BiPartition.areWeaklyCompatible(weakly.get(a), weakly.get(b), weakly.get(c)));
		}
	}

	/**
	 * a site is compatible with a split unless both sides of the split have more than one state
	 */
	@Test
	void sitesCompatibleWithASplit() {
		var chars = characters(CharactersType.DNA, "acgt",
				"aaaac",
				"aaccc",
				"acacg",
				"acgcg");
		chars.computeColors();
		var split = new ASplit(set(1, 2), 4);
		assertEquals(set(1, 2, 4, 5), CharactersUtilities.computeAllCompatible(chars, split));
	}

	private static List<Set<BitSet>> refine(List<String> newick) {
		var trees = new ArrayList<jloda.phylo.PhyloTree>();
		for (var line : newick) trees.add(tree(line));
		var result = new ArrayList<Set<BitSet>>();
		for (var refined : TreeMutualRefinement.apply(trees)) result.add(clusters(refined));
		for (var k = 0; k < trees.size(); k++) assertEquals(clusters(tree(newick.get(k))), clusters(trees.get(k)), "the input is changed");
		return result;
	}

	private static boolean fits(BitSet cluster, Set<BitSet> clusters) {
		for (var other : clusters) {
			var both = (BitSet) other.clone();
			both.and(cluster);
			if (!both.isEmpty() && !both.equals(other) && !both.equals(cluster)) return false;
		}
		return true;
	}

	/**
	 * the mutual refinement of two trees that do not conflict: both become the tree with the clusters of both, in
	 * either order of the trees. In the first order the first tree once did not get the cluster {t1,t2}
	 */
	@Test
	void mutualRefinement() {
		var expected = Set.of(set(1), set(2), set(3), set(4), set(5), set(6), set(1, 2), set(1, 2, 3), set(4, 5));
		for (var newick : List.of(List.of("((t1,t2,t3),(t4,t5),t6);", "((t1,t2),t3,t4,t5,t6);"), List.of("((t1,t2),t3,t4,t5,t6);", "((t1,t2,t3),(t4,t5),t6);"))) {
			var refined = refine(newick);
			assertEquals(expected, refined.get(0));
			assertEquals(expected, refined.get(1));
		}
		// trees that conflict keep their own clusters
		var conflicting = List.of("((t1,t2),t3,t4);", "((t1,t3),t2,t4);");
		var result = refine(conflicting);
		assertEquals(clusters(tree(conflicting.get(0))), result.get(0));
		assertEquals(clusters(tree(conflicting.get(1))), result.get(1));
	}

	/**
	 * two clusters fit into a star tree and not together: the one that is in more trees is taken
	 */
	@Test
	void mutualRefinementTakesTheClusterOfMoreTrees() {
		var leaves = Set.of(set(1), set(2), set(3), set(4));
		for (var newick : List.of(List.of("(t1,t2,t3,t4);", "((t1,t2),t3,t4);", "((t2,t3),t1,t4);", "((t2,t3),t1,t4);"),
				List.of("((t2,t3),t1,t4);", "((t1,t2),t3,t4);", "(t1,t2,t3,t4);", "((t2,t3),t1,t4);"))) {
			for (var k = 0; k < 4; k++)
				if (newick.get(k).equals("(t1,t2,t3,t4);")) {
					var found = new HashSet<>(refine(newick).get(k));
					found.removeAll(leaves);
					assertEquals(Set.of(set(2, 3)), found);
				}
		}
	}

	/**
	 * on random trees that are not fully resolved: no cluster is lost, every cluster added is one of another tree,
	 * no cluster of another tree that still fits is left out, a second application changes nothing, and the result
	 * does not depend on the order of the trees. It once did, for 197 of 300 such sets of trees
	 */
	@Test
	void mutualRefinementDoesNotDependOnTheOrderOfTheTrees() {
		var random = new Random(200);
		for (var r = 0; r < 300; r++) {
			var n = 10;
			var newick = relatedUnresolvedTrees(n, 3 + r % 3, 0.4, random);
			var refined = refine(newick);
			var all = new HashSet<BitSet>();
			for (var line : newick) all.addAll(clusters(tree(line)));
			for (var k = 0; k < newick.size(); k++) {
				assertTrue(refined.get(k).containsAll(clusters(tree(newick.get(k)))), "set " + r + ": a cluster is lost");
				assertTrue(all.containsAll(refined.get(k)), "set " + r + ": a cluster from nowhere");
				for (var c1 : refined.get(k)) assertTrue(fits(c1, refined.get(k)), "set " + r + ": not a tree");
				for (var cluster : all) assertTrue(refined.get(k).contains(cluster) || !fits(cluster, refined.get(k)), "set " + r + ", tree " + k + ": " + cluster + " fits and is left out");
			}
			// again, on the result
			var lines = new ArrayList<String>();
			for (var clusters : refined) lines.add(newickFromClusters(clusters, n));
			assertEquals(refined, refine(lines), "set " + r + ": a second application changes the trees");
			// in another order
			var order = new ArrayList<Integer>();
			for (var k = 0; k < newick.size(); k++) order.add(k);
			Collections.shuffle(order, random);
			var shuffled = new ArrayList<String>();
			for (var k : order) shuffled.add(newick.get(k));
			var result = refine(shuffled);
			for (var k = 0; k < order.size(); k++) assertEquals(refined.get(order.get(k)), result.get(k), "set " + r + ": the result depends on the order");
		}
	}

	/**
	 * trees on different taxa: a tree is refined only by a tree that has all taxa below the node concerned, and edge
	 * lengths are kept
	 */
	@Test
	void mutualRefinementOfTreesOnDifferentTaxa() {
		// the second tree lacks t3, so it does not say whether t1 and t2 are together apart from t3
		var refined = refine(List.of("((t1,t2,t3),t4,t5);", "((t1,t2),t4,t5);"));
		assertFalse(refined.get(0).contains(set(1, 2)));
		// here it has all of t1, t2, t3
		refined = refine(List.of("((t1,t2,t3),t4,t5);", "((t1,t2),t3);"));
		assertTrue(refined.get(0).contains(set(1, 2)));
		var withLengths = TreeMutualRefinement.apply(List.of(tree("((t1:1,t2:2,t3:3):4,t4:5,t5:6);"), tree("(((t1:1,t2:1):1,t3:1):1,t4:1,t5:1);")));
		assertEquals(pathDistances(tree("((t1:1,t2:2,t3:3):4,t4:5,t5:6);"), 5)[0][1], pathDistances(withLengths.get(0), 5)[0][1], 1e-12);
		assertEquals(21.0, withLengths.get(0).edgeStream().mapToDouble(withLengths.get(0)::getWeight).sum(), 1e-12);
	}
}
