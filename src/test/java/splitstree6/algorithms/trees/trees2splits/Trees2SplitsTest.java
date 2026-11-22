/*
 *  Trees2SplitsTest.java Copyright (C) 2026 Daniel H. Huson
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

package splitstree6.algorithms.trees.trees2splits;

import jloda.util.progress.ProgressSilent;
import org.junit.jupiter.api.Test;
import splitstree6.data.SplitsBlock;
import splitstree6.data.TreesBlock;
import splitstree6.splits.ASplit;
import splitstree6.splits.BiPartition;
import splitstree6.splits.Compatibility;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static splitstree6.testing.TestUtils.*;

/**
 * the methods that compute splits from trees: the consensus methods, the selection of one tree and the super network
 */
public class Trees2SplitsTest {
	private static SplitsBlock run(Trees2Splits algorithm, TreesBlock trees, int n) throws Exception {
		var splits = new SplitsBlock();
		algorithm.compute(new ProgressSilent(), taxa(n), trees, splits);
		return splits;
	}

	/**
	 * five trees on six taxa. The split 12|3456 is in all of them with lengths 1, 2, 3, 4, 10; 123|456 in the first three
	 * with lengths 1, 2, 6; 56|1234 in the first two with lengths 2, 4; 34|1256 in the last two; 125|346 in the last only
	 */
	private static TreesBlock example() {
		return trees(
				"(((t1:1,t2:1):1,t3:1):1,t4:1,(t5:1,t6:1):2);",
				"(((t1:1,t2:1):2,t3:1):2,t4:1,(t5:1,t6:1):4);",
				"(((t1:1,t2:1):3,t3:1):6,t4:1,t5:1,t6:1);",
				"((t1:1,t2:1):4,(t3:1,t4:1):7,t5:1,t6:1);",
				"(((t1:1,t2:1):10,t5:1):1,(t3:1,t4:1):9,t6:1);");
	}

	private static ConsensusSplits consensus(ConsensusSplits.Consensus consensus, ConsensusSplits.EdgeWeights weights) {
		var algorithm = new ConsensusSplits();
		algorithm.setOptionConsensus(consensus);
		algorithm.setOptionEdgeWeights(weights);
		return algorithm;
	}

	private static Map<BitSet, Double> nonTrivial(SplitsBlock splits, int n) {
		var result = new HashMap<BitSet, Double>();
		for (var split : splits.getSplits()) if (!split.isTrivial()) result.put(canonical(split.getA(), n), split.getWeight());
		return result;
	}

	@Test
	void strictMajorityAndGreedyConsensus() throws Exception {
		var mean = ConsensusSplits.EdgeWeights.Mean;
		var strict = run(consensus(ConsensusSplits.Consensus.Strict, mean), example(), 6);
		assertEquals(Map.of(set(1, 2), 4.0), nonTrivial(strict, 6));
		assertEquals(7, strict.getNsplits());
		assertEquals(Compatibility.compatible, strict.getCompatibility());

		var majority = run(consensus(ConsensusSplits.Consensus.Majority, mean), example(), 6);
		assertEquals(Map.of(set(1, 2), 4.0, set(1, 2, 3), 3.0), nonTrivial(majority, 6));

		// greedy: by decreasing number of trees, 12 (5), 123 (3), then 56 (2), which is compatible with both; 34 (2) is not
		var greedy = run(consensus(ConsensusSplits.Consensus.GreedyCompatible, mean), example(), 6);
		assertEquals(Map.of(set(1, 2), 4.0, set(1, 2, 3), 3.0, set(1, 2, 3, 4), 3.0), nonTrivial(greedy, 6));
		for (var split : greedy.getSplits()) if (split.isTrivial()) assertEquals(1.0, split.getWeight(), 1e-6);
	}

	/**
	 * the consensus network has the splits that occur in more than the given percentage of the trees
	 */
	@Test
	void consensusNetworkByThreshold() throws Exception {
		var algorithm = new ConsensusNetwork();
		algorithm.setOptionEdgeWeights(ConsensusSplits.EdgeWeights.Count);
		algorithm.setOptionHighDimensionFilter(false);
		algorithm.setOptionThresholdPercent(0);
		assertEquals(Map.of(set(1, 2), 5.0, set(1, 2, 3), 3.0, set(1, 2, 3, 4), 2.0, set(1, 2, 5, 6), 2.0, set(1, 2, 5), 1.0), nonTrivial(run(algorithm, example(), 6), 6));
		algorithm.setOptionThresholdPercent(30);
		assertEquals(Map.of(set(1, 2), 5.0, set(1, 2, 3), 3.0, set(1, 2, 3, 4), 2.0, set(1, 2, 5, 6), 2.0), nonTrivial(run(algorithm, example(), 6), 6));
		algorithm.setOptionThresholdPercent(40);
		assertEquals(Map.of(set(1, 2), 5.0, set(1, 2, 3), 3.0), nonTrivial(run(algorithm, example(), 6), 6));
		algorithm.setOptionThresholdPercent(100);
		assertEquals(Map.of(set(1, 2), 5.0), nonTrivial(run(algorithm, example(), 6), 6));
	}

	@Test
	void edgeWeights() throws Exception {
		var network = ConsensusSplits.Consensus.ConsensusNetwork;
		var a = set(1, 2);
		var b = set(1, 2, 3);
		var c = set(1, 2, 3, 4);
		var found = nonTrivial(run(consensus(network, ConsensusSplits.EdgeWeights.Mean), example(), 6), 6);
		assertEquals(4.0, found.get(a), 1e-6);
		assertEquals(3.0, found.get(b), 1e-6);
		found = nonTrivial(run(consensus(network, ConsensusSplits.EdgeWeights.Sum), example(), 6), 6);
		assertEquals(20.0, found.get(a), 1e-6);
		assertEquals(9.0, found.get(b), 1e-6);
		found = nonTrivial(run(consensus(network, ConsensusSplits.EdgeWeights.Median), example(), 6), 6);
		assertEquals(3.0, found.get(a), 1e-6);
		assertEquals(2.0, found.get(b), 1e-6);
		found = nonTrivial(run(consensus(network, ConsensusSplits.EdgeWeights.Count), example(), 6), 6);
		assertEquals(5.0, found.get(a), 1e-6);
		assertEquals(2.0, found.get(c), 1e-6);
		found = nonTrivial(run(consensus(network, ConsensusSplits.EdgeWeights.Uniform), example(), 6), 6);
		assertEquals(1.0, found.get(a), 1e-6);
		// each length divided by the length of its tree, 10 and 14 for the two trees that have the split
		found = nonTrivial(run(consensus(network, ConsensusSplits.EdgeWeights.TreeSizeWeightedMean), example(), 6), 6);
		assertEquals(0.5 * (2 / 10.0 + 4 / 14.0), found.get(c), 1e-6);
		found = nonTrivial(run(consensus(network, ConsensusSplits.EdgeWeights.TreeNormalizedSum), example(), 6), 6);
		assertEquals(2 / 10.0 + 4 / 14.0, found.get(c), 1e-6);
	}

	/**
	 * the confidence of a split is the percentage of trees that have it
	 */
	@Test
	void confidenceIsThePercentageOfTrees() throws Exception {
		var splits = run(consensus(ConsensusSplits.Consensus.ConsensusNetwork, ConsensusSplits.EdgeWeights.Mean), example(), 6);
		for (var split : splits.getSplits()) {
			var side = canonical(split.getA(), 6);
			var expected = side.equals(set(1, 2)) ? 100 : side.equals(set(1, 2, 3)) ? 60 : side.equals(set(1, 2, 3, 4)) || side.equals(set(1, 2, 5, 6)) ? 40 : side.equals(set(1, 2, 5)) ? 20 : 100;
			assertEquals(expected, split.getConfidence(), 1e-4, side.toString());
		}
	}

	/**
	 * a rooted tree whose root has two children has two edges for one split; the split counts once per tree and its
	 * length is the sum of the two
	 */
	@Test
	void treesWithARootOfDegreeTwo() throws Exception {
		var trees = trees("((t1:1,t2:1):3,(t3:1,t4:1):5);", "((t1:1,t2:1):2,(t3:1,t4:1):2);", "((t1:1,t3:1):1,(t2:1,t4:1):1);");
		var splits = run(consensus(ConsensusSplits.Consensus.Majority, ConsensusSplits.EdgeWeights.Mean), trees, 4);
		assertEquals(Map.of(set(1, 2), 6.0), nonTrivial(splits, 4));
		for (var split : splits.getSplits()) assertTrue(split.getConfidence() <= 100.0 + 1e-6, "confidence " + split.getConfidence());
	}

	@Test
	void consensusOfRandomTreesAgainstCounting() throws Exception {
		var random = new Random(71);
		var n = 9;
		var count = 25;
		var newick = new String[count];
		for (var i = 0; i < count; i++) newick[i] = randomNewick(n, random, true);
		var trees = trees(newick);
		var counts = new HashMap<BitSet, Integer>();
		var sums = new HashMap<BitSet, Double>();
		for (var tree : trees.getTrees())
			for (var entry : weightedSplits(tree, n).entrySet()) {
				counts.merge(entry.getKey(), 1, Integer::sum);
				sums.merge(entry.getKey(), entry.getValue(), Double::sum);
			}
		for (var percent : new double[]{0, 10, 33, 50}) {
			var algorithm = new ConsensusNetwork();
			algorithm.setOptionEdgeWeights(ConsensusSplits.EdgeWeights.Mean);
			algorithm.setOptionHighDimensionFilter(false);
			algorithm.setOptionThresholdPercent(percent);
			var expected = new HashMap<BitSet, Double>();
			for (var side : counts.keySet()) if (counts.get(side) > percent / 100.0 * count) expected.put(side, sums.get(side) / counts.get(side));
			var found = weightedSplits(run(algorithm, trees, n), n, 0);
			assertEquals(expected.keySet(), found.keySet(), "threshold " + percent);
			assertTrue(maxDifference(expected, found) < 1e-5, "threshold " + percent + ": weights differ by " + maxDifference(expected, found));
		}
	}

	/**
	 * the consensus outline is circular, and for trees that all fit one circular ordering it has all their splits
	 */
	@Test
	void consensusOutline() throws Exception {
		var algorithm = new ConsensusOutline();
		algorithm.setOptionEdgeWeights(ConsensusSplits.EdgeWeights.Count);
		var splits = run(algorithm, example(), 6);
		// all five splits fit the circular ordering 6,5,1,2,3,4
		assertEquals(Map.of(set(1, 2), 5.0, set(1, 2, 3), 3.0, set(1, 2, 3, 4), 2.0, set(1, 2, 5, 6), 2.0, set(1, 2, 5), 1.0), nonTrivial(splits, 6));
		assertEquals(7, splits.getCycle().length);
		assertTrue(Compatibility.isCyclic(6, splits.getSplits(), splits.getCycle()));
	}

	@Test
	void weaklyCompatibleConsensus() throws Exception {
		var random = new Random(72);
		var n = 8;
		var newick = new String[30];
		for (var i = 0; i < newick.length; i++) newick[i] = randomNewick(n, random, true);
		var splits = run(consensus(ConsensusSplits.Consensus.GreedyWeaklyCompatible, ConsensusSplits.EdgeWeights.Count), trees(newick), n).getSplits();
		for (var a = 0; a < splits.size(); a++)
			for (var b = a + 1; b < splits.size(); b++)
				for (var c = b + 1; c < splits.size(); c++) assertTrue(BiPartition.areWeaklyCompatible(splits.get(a), splits.get(b), splits.get(c)));
	}

	@Test
	void oneTreeAndItsSplits() throws Exception {
		var random = new Random(73);
		var n = 15;
		var trees = trees(randomNewick(n, random, true), randomNewick(n, random, true), randomNewick(n, random, true));
		for (var which = 1; which <= 3; which++) {
			var selector = new TreeSelectorSplits();
			selector.setOptionWhich(which);
			var splits = run(selector, trees, n);
			var expected = weightedSplits(trees.getTree(which), n);
			var found = weightedSplits(splits, n, 0);
			assertEquals(expected.keySet(), found.keySet());
			assertTrue(maxDifference(expected, found) < 1e-9);
			assertEquals(Compatibility.compatible, splits.getCompatibility());
			assertEquals(n + 1, splits.getCycle().length);
		}
		for (Trees2Splits algorithm : List.of(new ConsensusSplits(), new ConsensusNetwork(), new ConsensusOutline(), new SuperNetwork())) {
			var one = trees(randomNewick(n, random, true));
			var expected = weightedSplits(one.getTree(1), n);
			var found = weightedSplits(run(algorithm, one, n), n, 0);
			assertEquals(expected.keySet(), found.keySet(), algorithm.getClass().getSimpleName() + " on one tree");
		}
	}

	/**
	 * the average consensus of copies of one tree is that tree
	 */
	@Test
	void averageConsensusOfIdenticalTrees() throws Exception {
		var random = new Random(74);
		var n = 12;
		var newick = randomNewick(n, random, true);
		var trees = trees(newick, newick, newick);
		var expected = weightedSplits(trees.getTree(1), n);
		var found = weightedSplits(run(new AverageConsensus(), trees, n), n, 1e-6);
		assertEquals(expected.keySet(), found.keySet());
		assertTrue(maxDifference(expected, found) < 1e-4, "weights differ by " + maxDifference(expected, found));
	}

	/**
	 * the rule of the Z-closure (Huson, Dezulian, Kloepper and Steel 2004): the partial splits A1|B1 and A2|B2, where A1 and
	 * A2, A2 and B1, B1 and B2 overlap and A1 and B2 do not, give A1|B1+B2 and A1+A2|B2
	 */
	@Test
	void superNetworkOfTwoPartialTrees() throws Exception {
		var trees = trees("((t1:1,t2:1):1,(t3:1,t4:1):1);", "((t2:1,t3:1):1,(t4:1,t5:1):1);");
		trees.setPartial(true);
		var algorithm = new SuperNetwork();
		algorithm.setOptionEdgeWeights(SuperNetwork.EdgeWeights.None);
		var splits = run(algorithm, trees, 5);
		assertEquals(Set.of(set(1, 2), set(1, 2, 3)), nonTrivial(splits, 5).keySet());
		assertEquals(6, splits.getCycle().length);
	}

	/**
	 * partial trees that are all restrictions of one tree: every split of the super network is a split of that tree
	 */
	@Test
	void superNetworkOfRestrictionsOfOneTree() throws Exception {
		var random = new Random(75);
		var n = 12;
		for (var r = 0; r < 5; r++) {
			var full = tree(randomNewick(n, random, true));
			var truth = weightedSplits(full, n).keySet();
			var trees = new TreesBlock();
			for (var k = 0; k < 6; k++) {
				var keep = new BitSet();
				while (keep.cardinality() < 8) keep.set(1 + random.nextInt(n));
				var induced = splitstree6.utils.TreesUtils.computeInducedTree(keep, full);
				trees.getTrees().add(induced);
			}
			trees.setPartial(true);
			for (Trees2Splits algorithm : List.of(new SuperNetwork(), new FilteredSuperNetwork())) {
				var splits = run(algorithm, trees, n);
				for (var split : splits.getSplits()) {
					if (!split.isTrivial()) assertTrue(truth.contains(canonical(split.getA(), n)), algorithm.getClass().getSimpleName() + ", data set " + r + ": " + split.getA());
					assertTrue(Double.isFinite(split.getWeight()));
				}
				assertNotNull(splits.getCycle(), algorithm.getClass().getSimpleName() + ": no cycle");
				assertEquals(n + 1, splits.getCycle().length, algorithm.getClass().getSimpleName() + ": cycle");
			}
		}
	}

	/**
	 * the confidence network of trees that are all the same is that tree, with its edge lengths
	 */
	@Test
	void confidenceNetworkOfIdenticalTrees() throws Exception {
		var random = new Random(76);
		var n = 10;
		var newick = randomNewick(n, random, true);
		var copies = new String[12];
		Arrays.fill(copies, newick);
		var trees = trees(copies);
		var expected = weightedSplits(trees.getTree(1), n);
		for (var filter : new boolean[]{true, false}) {
			var algorithm = new ConfidenceNetwork();
			algorithm.setOptionHighDimensionFilter(filter);
			var splits = run(algorithm, trees, n);
			var found = weightedSplits(splits, n, 0);
			assertEquals(expected.keySet(), found.keySet());
			assertTrue(maxDifference(expected, found) < 1e-5, "weights differ by " + maxDifference(expected, found));
			assertEquals(n + 1, splits.getCycle().length);
		}
	}

	/**
	 * the confidence network of different trees: every split of it is a split of one of the trees, with a weight that
	 * is a number
	 */
	@Test
	void confidenceNetworkOfDifferentTrees() throws Exception {
		var random = new Random(77);
		var n = 8;
		var newick = new String[20];
		for (var i = 0; i < newick.length; i++) newick[i] = randomNewick(n, random, true);
		var trees = trees(newick);
		var all = new HashSet<BitSet>();
		for (var tree : trees.getTrees()) all.addAll(weightedSplits(tree, n).keySet());
		for (var level : new double[]{0.5, 0.95}) {
			var algorithm = new ConfidenceNetwork();
			algorithm.setOptionLevel(level);
			algorithm.setOptionHighDimensionFilter(false);
			var splits = run(algorithm, trees, n);
			for (var split : splits.getSplits()) {
				assertTrue(all.contains(canonical(split.getA(), n)));
				assertTrue(Double.isFinite(split.getWeight()) && split.getWeight() >= 0);
			}
			// the trivial splits are in every tree
			for (var t = 1; t <= n; t++) assertTrue(weightedSplits(splits, n, -1).containsKey(canonical(set(t), n)), "level " + level + ": trivial split of taxon " + t);
		}
	}

	/**
	 * the two trees of the Z-rule, the second with a sixth taxon. The split 12|3456 is the edge of length 4 in the
	 * first tree (4 taxa) and the edge to t2, of length 1, in the second (5 taxa); 123|456 is the edge to t4, of
	 * length 1, in the first and the edge of length 2 in the second. Mean and TreeSizeWeightedMean were once
	 * exchanged, and the second divided the sum of the lengths by the sum of the numbers of taxa
	 */
	@Test
	void superNetworkEdgeWeights() throws Exception {
		var expected = new HashMap<SuperNetwork.EdgeWeights, double[]>();
		expected.put(SuperNetwork.EdgeWeights.Mean, new double[]{2.5, 1.5});
		expected.put(SuperNetwork.EdgeWeights.TreeSizeWeightedMean, new double[]{(4 * 4 + 5 * 1) / 9.0, (4 * 1 + 5 * 2) / 9.0});
		expected.put(SuperNetwork.EdgeWeights.Sum, new double[]{5, 3});
		expected.put(SuperNetwork.EdgeWeights.Min, new double[]{1, 1});
		expected.put(SuperNetwork.EdgeWeights.None, new double[]{1, 1});
		for (var weights : expected.keySet()) {
			var trees = trees("((t1:1,t2:1):4,t3:1,t4:1);", "((t2:1,t3:1):2,t4:1,t5:1,t6:1);");
			trees.setPartial(true);
			var algorithm = new SuperNetwork();
			algorithm.setOptionEdgeWeights(weights);
			var found = nonTrivial(run(algorithm, trees, 6), 6);
			assertEquals(Set.of(set(1, 2), set(1, 2, 3)), found.keySet(), weights.toString());
			assertEquals(expected.get(weights)[0], found.get(set(1, 2)), 1e-12, weights + ", 12|3456");
			assertEquals(expected.get(weights)[1], found.get(set(1, 2, 3)), 1e-12, weights + ", 123|456");
		}
	}

	/**
	 * the confidence of a split is the percentage of the trees that contain it, among the trees whose taxa it divides.
	 * The two trees of the Z-rule and a third on t1..t4 that has 13|24: 12|3456 is in the first, is the edge to t2 in
	 * the second and is not in the third; 13|2456 likewise; 123|456 is in all three, in two of them as the edge to t4.
	 * The second tree does not have t1 and so does not count for the edge to t1
	 */
	@Test
	void superNetworkConfidence() throws Exception {
		var trees = trees("((t1:1,t2:1):4,t3:1,t4:1);", "((t2:1,t3:1):2,t4:1,t5:1,t6:1);", "((t1:1,t3:1):1,t2:1,t4:1);");
		trees.setPartial(true);
		var algorithm = new SuperNetwork();
		algorithm.setOptionHighDimensionFilter(false);
		var splits = run(algorithm, trees, 6);
		var found = new HashMap<BitSet, Double>();
		for (var split : splits.getSplits()) found.put(canonical(split.getA(), 6), split.getConfidence());
		assertEquals(200.0 / 3.0, found.get(set(1, 2)), 1e-9, found.toString());
		assertEquals(200.0 / 3.0, found.get(set(1, 3)), 1e-9, found.toString());
		assertEquals(100.0, found.get(set(1, 2, 3)), 1e-9, found.toString());
		assertEquals(100.0, found.get(set(1)), 1e-9, found.toString());
		assertEquals(100.0, found.get(set(1, 2, 3, 5, 6)), 1e-9, found.toString());
	}

	/**
	 * a tree whose root has two children: the two edges at the root are one split, with the sum of their lengths
	 */
	@Test
	void superNetworkAndARootOfDegreeTwo() throws Exception {
		var trees = trees("((t1:1,t2:1):3,(t3:1,t4:1):1);", "((t2:1,t3:1):2,t4:1,t5:1,t6:1);");
		trees.setPartial(true);
		var algorithm = new SuperNetwork();
		algorithm.setOptionEdgeWeights(SuperNetwork.EdgeWeights.Sum);
		assertEquals(4.0 + 1.0, nonTrivial(run(algorithm, trees, 6), 6).get(set(1, 2)), 1e-12);
	}
}
