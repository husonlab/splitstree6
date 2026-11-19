/*
 *  Distances2TreesTest.java Copyright (C) 2026 Daniel H. Huson
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

package splitstree6.algorithms.distances.distances2trees;

import jloda.graph.Node;
import jloda.phylo.PhyloTree;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static splitstree6.testing.TestUtils.*;

/**
 * the methods that compute a tree from distances, on distances whose tree is known
 */
public class Distances2TreesTest {
	/**
	 * the distances of a tree determine the tree: both methods must return its splits and its edge lengths
	 */
	@Test
	void neighborJoiningAndBioNJRecoverTheTreeOfATreeMetric() throws Exception {
		for (Supplier<Distances2Trees> method : List.<Supplier<Distances2Trees>>of(NeighborJoining::new, BioNJ::new)) {
			var name = method.get().getClass().getSimpleName();
			var random = new Random(50);
			for (var r = 0; r < 20; r++) {
				var n = (r == 0 ? 200 : 50);
				var truth = randomTree(n, random);
				var found = weightedSplits(tree(method.get(), metric(truth, n)), n);
				assertEquals(truth.keySet(), found.keySet(), name + ", tree " + r);
				assertTrue(maxDifference(truth, found) < 1e-9, name + ", tree " + r + ": edge lengths differ by " + maxDifference(truth, found));
			}
		}
	}

	/**
	 * four taxa with pendant edges 1, 2, 3, 4 and an inner edge of length 5 that separates {1,2} from {3,4}
	 */
	@Test
	void neighborJoiningOnFourTaxa() throws Exception {
		var d = new double[][]{{0, 3, 9, 10}, {3, 0, 10, 11}, {9, 10, 0, 7}, {10, 11, 7, 0}};
		var found = weightedSplits(tree(new NeighborJoining(), d), 4);
		var expected = new HashMap<BitSet, Double>();
		expected.put(side(1), 1.0);
		expected.put(side(1, 3, 4), 2.0);
		expected.put(side(1, 2, 4), 3.0);
		expected.put(side(1, 2, 3), 4.0);
		expected.put(side(1, 2), 5.0);
		assertEquals(expected.keySet(), found.keySet());
		assertTrue(maxDifference(expected, found) < 1e-12);
	}

	static BitSet side(int... taxa) {
		var set = new BitSet();
		for (var t : taxa) set.set(t);
		return set;
	}

	/**
	 * on distances that are not those of a tree, against neighbor joining and BioNJ written from the papers.
	 * BioNJ once returned the neighbor-joining tree on every input. Its edge lengths depend on which of two equally
	 * good pairs is joined when four clusters are left, so either choice is accepted
	 */
	@Test
	void neighborJoiningAndBioNJAgreeWithTheMethodsAsPublished() throws Exception {
		var random = new Random(1);
		var n = 12;
		var bioNJDiffersFromNJ = 0;
		for (var r = 0; r < 100; r++) {
			var d = noisy(metric(randomTree(n, random), n), 0.3, random);
			var nj = weightedSplits(tree(new NeighborJoining(), d), n);
			var bioNJ = weightedSplits(tree(new BioNJ(), d), n);
			var referenceNJ = referenceNeighborJoining(d, false, false);
			assertEquals(referenceNJ.keySet(), nj.keySet(), "NeighborJoining, input " + r);
			assertTrue(maxDifference(referenceNJ, nj) < 1e-9, "NeighborJoining, input " + r);
			var referenceBioNJ = referenceNeighborJoining(d, true, false);
			assertEquals(referenceBioNJ.keySet(), bioNJ.keySet(), "BioNJ, input " + r);
			assertTrue(Math.min(maxDifference(referenceBioNJ, bioNJ), maxDifference(referenceNeighborJoining(d, true, true), bioNJ)) < 1e-9, "BioNJ, input " + r);
			if (!bioNJ.keySet().equals(nj.keySet())) bioNJDiffersFromNJ++;
		}
		assertTrue(bioNJDiffersFromNJ > 0, "BioNJ returns the neighbor-joining tree on all 100 inputs");
	}

	/**
	 * an ultrametric determines its rooted tree: UPGMA must return it, with all leaves at the same distance from the root
	 */
	@Test
	void upgmaRecoversTheTreeOfAnUltrametric() throws Exception {
		var random = new Random(14);
		var n = 50;
		for (var r = 0; r < 50; r++) {
			var d = new double[n][n];
			var truth = randomUltrametric(n, random, d);
			var tree = tree(new UPGMA(), d);
			var found = weightedSplits(tree, n);
			assertEquals(truth.keySet(), found.keySet(), "ultrametric " + r);
			assertTrue(maxDifference(truth, found) < 1e-9, "ultrametric " + r);
			var depths = new ArrayList<Double>();
			depths(tree, tree.getRoot(), 0, depths);
			assertEquals(n, depths.size());
			assertTrue(Collections.max(depths) - Collections.min(depths) < 1e-9, "leaves at different depths");
		}
	}

	static void depths(PhyloTree tree, Node v, double depth, List<Double> result) {
		if (v.getOutDegree() == 0) result.add(depth);
		for (var e : v.outEdges()) depths(tree, e.getTarget(), depth + tree.getWeight(e), result);
	}

	/**
	 * the root of the neighbor-joining tree was once a leaf on some inputs
	 */
	@Test
	void theRootOfTheNeighborJoiningTreeIsAnInnerNode() throws Exception {
		var random = new Random(131);
		for (var r = 0; r < 100; r++) {
			var tree = tree(new NeighborJoining(), metric(randomTree(12, random), 12));
			assertFalse(tree.hasTaxa(tree.getRoot()), "input " + r);
		}
	}

	/**
	 * one, two and three taxa, all distances zero, two identical taxa. BioNJ once failed on two taxa, and neighbor joining
	 * returned a tree without a root for one
	 */
	@Test
	void smallAndDegenerateInput() throws Exception {
		for (var entry : degenerateDistances().entrySet())
			for (Supplier<Distances2Trees> method : List.<Supplier<Distances2Trees>>of(NeighborJoining::new, BioNJ::new, UPGMA::new, MinSpanningTree::new)) {
				var name = method.get().getClass().getSimpleName() + ", " + entry.getKey();
				var n = entry.getValue().length;
				var tree = tree(method.get(), entry.getValue());
				assertNotNull(tree.getRoot(), name + ": no root");
				for (var e : tree.edges()) assertTrue(Double.isFinite(tree.getWeight(e)), name + ": edge weight " + tree.getWeight(e));
				var taxa = new BitSet();
				for (var v : tree.nodes()) for (var t : tree.getTaxa(v)) taxa.set(t);
				assertEquals(n, taxa.cardinality(), name + ": taxa in the tree");
			}
	}
}
