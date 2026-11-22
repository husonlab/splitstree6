/*
 *  RootedNetworksTest.java Copyright (C) 2026 Daniel H. Huson
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

import jloda.phylo.PhyloTree;
import jloda.util.progress.ProgressSilent;
import org.junit.jupiter.api.Test;
import splitstree6.data.TreesBlock;
import splitstree6.utils.TreesUtils;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static splitstree6.testing.TestUtils.*;

/**
 * the methods that compute rooted networks from trees, and trees from rooted networks
 */
public class RootedNetworksTest {
	private static TreesBlock run(Trees2Trees algorithm, TreesBlock trees, int n) throws Exception {
		var child = new TreesBlock();
		algorithm.compute(new ProgressSilent(), taxa(n), trees, child);
		return child;
	}

	private static long reticulations(PhyloTree network) {
		return network.nodeStream().filter(v -> v.getInDegree() > 1).mapToLong(v -> v.getInDegree() - 1).sum();
	}

	private static Set<BitSet> hardwired(PhyloTree network, int n) {
		var all = new BitSet();
		all.set(1, n + 1);
		var result = new HashSet<>(TreesUtils.collectAllHardwiredClusters(network));
		result.remove(all);
		result.removeIf(BitSet::isEmpty);
		return result;
	}

	private static Set<Set<BitSet>> containedTrees(PhyloTree network, int n) throws Exception {
		var block = new TreesBlock();
		block.getTrees().add(network);
		block.setReticulated(true);
		var result = new HashSet<Set<BitSet>>();
		for (var tree : run(new EnumerateContainedTrees(), block, n).getTrees()) {
			assertEquals(0, reticulations(tree));
			result.add(clusters(tree));
		}
		return result;
	}

	/**
	 * two trees that differ by moving one subtree, and are not the same
	 */
	private static String[] pairAtDistanceOne(int n, Random random) {
		while (true) {
			var a = randomNewick(n, random, false);
			var b = randomNewick(n, random, false);
			if (!clusters(tree(a)).equals(clusters(tree(b))) && splitstree6.algorithms.trees.trees2report.ListOneRSPRTrees.haveRootedSprDistanceOne(tree(a), tree(b)))
				return new String[]{a, b};
		}
	}

	// ---------- the cluster network ----------

	private static ClusterNetwork clusterNetwork(double threshold) {
		var algorithm = new ClusterNetwork();
		algorithm.setOptionThresholdPercent(threshold);
		algorithm.setOptionEdgeWeights(ClusterNetwork.EdgeWeights.Count);
		return algorithm;
	}

	/**
	 * the cluster network represents exactly the clusters of the trees (Huson and Rupp 2008); for one tree it is the tree
	 */
	@Test
	void clusterNetworkHasTheClustersOfTheTrees() throws Exception {
		var random = new Random(91);
		for (var r = 0; r < 20; r++) {
			var n = 8;
			var count = 1 + r % 4;
			var newick = new String[count];
			for (var i = 0; i < count; i++) newick[i] = randomNewick(n, random, true);
			var trees = trees(newick);
			var expected = new HashSet<BitSet>();
			for (var tree : trees.getTrees()) expected.addAll(clusters(tree));
			var child = run(clusterNetwork(0), trees, n);
			assertEquals(1, child.getNTrees());
			var network = child.getTree(1);
			assertEquals(expected, hardwired(network, n), "data set " + r);
			if (count == 1) assertEquals(0, reticulations(network));
			assertEquals(reticulations(network) > 0, child.isReticulated());
			var taxa = new BitSet();
			for (var v : network.nodes()) for (var t : network.getTaxa(v)) taxa.set(t);
			assertEquals(n, taxa.cardinality());
		}
	}

	@Test
	void clusterNetworkWithThreshold() throws Exception {
		var trees = trees("(((t1,t2),t3),(t4,t5));", "(((t1,t2),t3),(t4,t5));", "(((t1,t3),t2),(t4,t5));");
		var leaves = Set.of(set(1), set(2), set(3), set(4), set(5));
		var found = hardwired(run(clusterNetwork(50), trees, 5).getTree(1), 5);
		found.removeAll(leaves);
		assertEquals(Set.of(set(1, 2), set(1, 2, 3), set(4, 5)), found);
		found = hardwired(run(clusterNetwork(0), trees, 5).getTree(1), 5);
		found.removeAll(leaves);
		assertEquals(Set.of(set(1, 2), set(1, 3), set(1, 2, 3), set(4, 5)), found);
	}

	// ---------- hybridization networks ----------

	private static AutumnAlgorithm autumn(boolean onlyOne) {
		var algorithm = new AutumnAlgorithm();
		algorithm.setOptionOnlyOneNetwork(onlyOne);
		algorithm.optionRerootToMinimizeProperty().set(false);
		algorithm.setOptionTreeChildOnly(false);
		return algorithm;
	}

	/**
	 * two trees that differ by moving one subtree: every minimum hybridization network has one reticulation and contains
	 * both trees. Two identical trees: the tree itself
	 */
	@Test
	void autumnAlgorithm() throws Exception {
		var random = new Random(92);
		for (var r = 0; r < 10; r++) {
			var n = 8;
			var pair = pairAtDistanceOne(n, random);
			var trees = trees(pair);
			for (var onlyOne : new boolean[]{true, false}) {
				var child = run(autumn(onlyOne), trees, n);
				assertTrue(child.getNTrees() >= 1);
				if (onlyOne) assertEquals(1, child.getNTrees());
				assertTrue(child.isReticulated());
				for (var network : child.getTrees()) {
					assertEquals(1, reticulations(network), "pair " + r);
					var contained = containedTrees(network, n);
					assertTrue(contained.contains(clusters(trees.getTree(1))), "pair " + r + ": first tree not contained");
					assertTrue(contained.contains(clusters(trees.getTree(2))), "pair " + r + ": second tree not contained");
				}
			}
			var same = run(autumn(true), trees(pair[0], pair[0]), n);
			assertEquals(1, same.getNTrees());
			assertEquals(0, reticulations(same.getTree(1)));
			assertEquals(clusters(trees.getTree(1)), clusters(same.getTree(1)));
			assertFalse(same.isReticulated());
		}
	}

	private static PhyloFusion newPhyloFusion() {
		var algorithm = new PhyloFusion();
		algorithm.setOptionMutualRefinement(true);
		algorithm.setOptionCladeReduction(true);
		algorithm.setOptionOnlyOneNetwork(true);
		algorithm.optionGroupNonSeparatedProperty().set(true);
		algorithm.optionMissingTaxaHeuristicProperty().set(true);
		algorithm.optionRefinementHeuristicProperty().set(false);
		algorithm.setOptionReticulatePlacement(splitstree6.compute.phylofusion.PhyloFusionAlgorithm.ReticulationPreference.Normal);
		algorithm.setOptionEdgeWeights(PhyloFusion.EdgeWeights.None);
		return algorithm;
	}

	/**
	 * PhyloFusion on the same input: one reticulation, both trees contained; on several copies of one tree, the tree
	 */
	@Test
	void phyloFusion() throws Exception {
		var random = new Random(93);
		for (var r = 0; r < 10; r++) {
			var n = 8;
			var pair = pairAtDistanceOne(n, random);
			var trees = trees(pair);
			var child = run(newPhyloFusion(), trees, n);
			assertEquals(1, child.getNTrees());
			var network = child.getTree(1);
			assertEquals(1, reticulations(network), "pair " + r);
			var contained = containedTrees(network, n);
			assertTrue(contained.contains(clusters(trees.getTree(1))), "pair " + r + ": first tree not contained");
			assertTrue(contained.contains(clusters(trees.getTree(2))), "pair " + r + ": second tree not contained");
			var same = run(newPhyloFusion(), trees(pair[0], pair[0], pair[0]), n);
			assertEquals(0, reticulations(same.getTree(1)));
			assertEquals(clusters(trees.getTree(1)), clusters(same.getTree(1)));
		}
	}

	/**
	 * three trees that are pairwise different: the network of PhyloFusion contains all of them
	 */
	@Test
	void phyloFusionContainsAllInputTrees() throws Exception {
		var random = new Random(94);
		for (var r = 0; r < 5; r++) {
			var n = 7;
			var pair = pairAtDistanceOne(n, random);
			String third;
			do third = randomNewick(n, random, false);
			while (!splitstree6.algorithms.trees.trees2report.ListOneRSPRTrees.haveRootedSprDistanceOne(tree(pair[0]), tree(third)) || clusters(tree(third)).equals(clusters(tree(pair[1]))));
			var trees = trees(pair[0], pair[1], third);
			var network = run(newPhyloFusion(), trees, n).getTree(1);
			var contained = containedTrees(network, n);
			for (var t = 1; t <= 3; t++) assertTrue(contained.contains(clusters(trees.getTree(t))), "data set " + r + ": tree " + t + " not contained");
			assertTrue(reticulations(network) >= 1 && reticulations(network) <= 2, "data set " + r + ": " + reticulations(network) + " reticulations");
		}
	}

	// ---------- trees from networks ----------

	/**
	 * a network with one reticulation: t3 is below both the parent of t1,t2 and the parent of t4
	 */
	private static TreesBlock network() throws Exception {
		var trees = trees("(((t1,t2),t3),(t4,t5));", "((t1,t2),((t3,t4),t5));");
		var child = run(autumn(true), trees, 5);
		assertEquals(1, reticulations(child.getTree(1)));
		return child;
	}

	@Test
	void containedTreesOfANetwork() throws Exception {
		var network = network();
		assertEquals(Set.of(clusters(tree("(((t1,t2),t3),(t4,t5));")), clusters(tree("((t1,t2),((t3,t4),t5));"))), containedTrees(network.getTree(1), 5));
		var algorithm = new EnumerateContainedTrees();
		algorithm.setOptionRemoveDuplicates(false);
		assertEquals(2, run(algorithm, network, 5).getNTrees());
		// a tree contains itself only
		var tree = trees("(((t1,t2),t3),(t4,t5));");
		var contained = run(new EnumerateContainedTrees(), tree, 5);
		assertEquals(1, contained.getNTrees());
		assertEquals(clusters(tree.getTree(1)), clusters(contained.getTree(1)));
	}

	/**
	 * the LSA tree and the blob tree of a tree are the tree; those of the network are trees with all taxa in which the
	 * parts that the two contained trees share are kept
	 */
	@Test
	void lsaTreeAndBlobTree() throws Exception {
		var tree = trees("(((t1,t2),t3),(t4,t5));");
		assertEquals(clusters(tree.getTree(1)), clusters(run(new LSATree(), tree, 5).getTree(1)));
		assertEquals(clusters(tree.getTree(1)), clusters(run(new BlobTree(), tree, 5).getTree(1)));
		var network = network();
		for (Trees2Trees algorithm : List.of(new LSATree(), new BlobTree())) {
			var name = algorithm.getClass().getSimpleName();
			var child = run(algorithm, network, 5);
			assertEquals(1, child.getNTrees());
			assertFalse(child.isReticulated(), name);
			var result = child.getTree(1);
			assertEquals(0, reticulations(result), name);
			var found = clusters(result);
			assertTrue(found.containsAll(Set.of(set(1), set(2), set(3), set(4), set(5))), name + ": " + found);
			assertTrue(found.contains(set(1, 2)), name + ": the clade of t1 and t2 is in both trees: " + found);
			for (var c1 : found)
				for (var c2 : found) {
					var both = (BitSet) c1.clone();
					both.and(c2);
					assertTrue(both.isEmpty() || both.equals(c1) || both.equals(c2), name + ": clusters not nested");
				}
		}
	}

	@Test
	void networkFilter() throws Exception {
		var block = new TreesBlock();
		block.getTrees().add(tree("(((t1,t2),t3),(t4,t5));"));
		block.getTrees().add(network().getTree(1));
		block.setReticulated(true);
		var filter = new NetworkFilter();
		assertEquals(2, run(filter, block, 5).getNTrees());
		filter.setOptionFilterBy(NetworkFilter.FilterBy.Tree);
		var child = run(filter, block, 5);
		assertEquals(1, child.getNTrees());
		assertFalse(child.isReticulated());
		filter.setOptionFilterBy(NetworkFilter.FilterBy.TreeChildNetwork);
		assertEquals(2, run(filter, block, 5).getNTrees());
	}

	@Test
	void normalizeRootedNetworks() throws Exception {
		var tree = trees("(((t1,t2),t3),(t4,t5));");
		var normalized = run(new NormalizeRootedNetworks(), tree, 5);
		assertEquals(1, normalized.getNTrees());
		assertEquals(clusters(tree.getTree(1)), clusters(normalized.getTree(1)));
		var network = network();
		var child = run(new NormalizeRootedNetworks(), network, 5);
		assertEquals(1, child.getNTrees());
		var taxa = new BitSet();
		for (var v : child.getTree(1).nodes()) for (var t : child.getTree(1).getTaxa(v)) taxa.set(t);
		assertEquals(set(1, 2, 3, 4, 5), taxa);
		assertTrue(reticulations(child.getTree(1)) <= 1);
	}

	// ---------- incomplete lineage sorting ----------

	/**
	 * trees that all agree have no conflict to explain: nothing is contracted
	 */
	@Test
	void ilsContractorLeavesAgreeingTreesAlone() throws Exception {
		var random = new Random(95);
		var n = 8;
		var newick = randomNewick(n, random, true);
		var copies = new String[20];
		Arrays.fill(copies, newick);
		var trees = trees(copies);
		var child = run(new ILSContractor(), trees, n);
		assertEquals(20, child.getNTrees());
		for (var tree : child.getTrees()) assertEquals(clusters(trees.getTree(1)), clusters(tree));
	}
}
