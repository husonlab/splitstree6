/*
 *  BootstrapTest.java Copyright (C) 2026 Daniel H. Huson
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

import jloda.phylo.PhyloTree;
import jloda.util.ProgramExecutorService;
import jloda.util.progress.ProgressListener;
import jloda.util.progress.ProgressSilent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import splitstree6.algorithms.characters.characters2distances.HammingDistance;
import splitstree6.algorithms.characters.characters2splits.Characters2Splits;
import splitstree6.algorithms.characters.characters2splits.DnaToSplits;
import splitstree6.algorithms.distances.distances2trees.NeighborJoining;
import splitstree6.algorithms.splits.splits2splits.BootstrapSplits;
import splitstree6.algorithms.trees.trees2splits.BootstrapTreeSplits;
import splitstree6.algorithms.trees.trees2trees.BootstrapTree;
import splitstree6.data.*;
import splitstree6.data.parts.CharactersType;
import splitstree6.workflow.Algorithm;
import splitstree6.workflow.DataBlock;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static splitstree6.testing.TestUtils.*;

/**
 * bootstrapping of splits and of trees, on alignments for which the support of a split can be worked out
 */
public class BootstrapTest {
	private final int cores = ProgramExecutorService.getNumberOfCoresToUse();
	private final Locale locale = Locale.getDefault();

	private final boolean richNewick = PhyloTree.SUPPORT_RICH_NEWICK;

	@AfterEach
	void restore() {
		ProgramExecutorService.setNumberOfCoresToUse(cores);
		Locale.setDefault(locale);
		PhyloTree.SUPPORT_RICH_NEWICK = richNewick;
	}

	/**
	 * sites for the given splits of taxa 1..n: for each, the given number of sites with a on the side of taxon 1 and c
	 * on the other, and constant sites up to the given total
	 */
	private static CharactersBlock sites(int n, int total, Map<BitSet, Integer> splits) {
		var rows = new StringBuilder[n];
		for (var t = 0; t < n; t++) rows[t] = new StringBuilder();
		var sides = new ArrayList<>(splits.keySet());
		sides.sort(Comparator.comparing(BitSet::toString));
		for (var side : sides)
			for (var k = 0; k < splits.get(side); k++)
				for (var t = 1; t <= n; t++) rows[t - 1].append(side.get(t) ? 'a' : 'c');
		while (rows[0].length() < total) for (var t = 0; t < n; t++) rows[t].append('g');
		var text = new String[n];
		for (var t = 0; t < n; t++) text[t] = rows[t].toString();
		return characters(CharactersType.DNA, "acgt", text);
	}

	private static DnaToSplits dnaToSplits() {
		var algorithm = new DnaToSplits();
		algorithm.setOptionHighDimensionFilter(false);
		return algorithm;
	}

	private static SplitsBlock bootstrapSplits(CharactersBlock chars, int n, int replicates, int seed, double minPercent, boolean showAll, List<Algorithm<?, ?>> pipeline) throws Exception {
		var input = new SplitsBlock();
		var data = (DataBlock) chars;
		for (var algorithm : pipeline) {
			var out = DataBlock.newInstance(algorithm.getToClass());
			((Algorithm) algorithm).compute(new ProgressSilent(), taxa(n), data, out);
			data = out;
		}
		if (data instanceof SplitsBlock splits) input = splits;
		var bootstrap = new BootstrapSplits();
		bootstrap.setOptionReplicates(replicates);
		bootstrap.setOptionRandomSeed(seed);
		bootstrap.setOptionMinPercent(minPercent);
		bootstrap.setOptionShowAllSplits(showAll);
		bootstrap.setOptionHighDimensionFilter(false);
		var result = new SplitsBlock();
		bootstrap.compute(new ProgressSilent(), taxa(n), input, result, chars, BootstrappingUtils.newPathSupplier(pipeline, SplitsBlock.class));
		return result;
	}

	private static Map<BitSet, Double> confidences(SplitsBlock splits, int n) {
		var result = new HashMap<BitSet, Double>();
		for (var split : splits.getSplits()) result.put(canonical(split.getA(), n), split.getConfidence());
		return result;
	}

	/**
	 * splits taken directly from the sites: a split is in a replicate exactly if one of its k sites is drawn, so its
	 * support is 1 - (1 - k/N)^N, and its mean weight in the replicates that have it is k divided by that
	 */
	@Test
	void supportOfASplitWithKnownNumberOfSites() throws Exception {
		var n = 8;
		var total = 200;
		var splits = new HashMap<BitSet, Integer>();
		splits.put(set(1, 2), 1);
		splits.put(set(1, 2, 3), 2);
		splits.put(set(1, 2, 3, 4), 4);
		splits.put(set(1, 2, 3, 4, 5), 40);
		splits.put(set(1, 3), 1); // not compatible with the first
		var replicates = 2000;
		var result = bootstrapSplits(sites(n, total, splits), n, replicates, 42, 0, false, List.of(dnaToSplits()));
		var found = confidences(result, n);
		var weights = weightedSplits(result, n, 0);
		for (var side : splits.keySet()) {
			var k = splits.get(side);
			var p = 1 - Math.pow(1 - k / (double) total, total);
			var tolerance = 100 * 4.5 * Math.sqrt(p * (1 - p) / replicates) + 0.01;
			assertEquals(100 * p, found.get(side), tolerance, "support of the split with " + k + " sites");
			assertEquals(k / p, weights.get(side), 0.08 * k / p + 0.05, "mean weight of the split with " + k + " sites");
		}
		assertEquals(n + 1, result.getCycle().length);
	}

	@Test
	void theSameSeedGivesTheSameSupportAndAnotherSeedDoesNot() throws Exception {
		var random = new Random(301);
		var n = 12;
		var chars = characters(CharactersType.DNA, "acgt", simulateSequences(n, 300, 0.05, 0, random));
		List<Algorithm<?, ?>> pipeline = List.of(new HammingDistance(), new NeighborJoining());
		var first = confidences(bootstrapSplits(chars, n, 200, 42, 0, true, pipeline), n);
		assertEquals(first, confidences(bootstrapSplits(chars, n, 200, 42, 0, true, pipeline), n));
		assertNotEquals(first, confidences(bootstrapSplits(chars, n, 200, 43, 0, true, pipeline), n));
		// the number of threads does not matter
		ProgramExecutorService.setNumberOfCoresToUse(1);
		assertEquals(first, confidences(bootstrapSplits(chars, n, 200, 42, 0, true, pipeline), n), "one thread");
		ProgramExecutorService.setNumberOfCoresToUse(7);
		assertEquals(first, confidences(bootstrapSplits(chars, n, 200, 42, 0, true, pipeline), n), "seven threads");
	}

	@Test
	void minimumPercentAndAllSplits() throws Exception {
		var n = 8;
		var splits = new HashMap<BitSet, Integer>();
		splits.put(set(1, 2), 1);
		splits.put(set(1, 2, 3, 4, 5), 40);
		var chars = sites(n, 200, splits);
		var all = confidences(bootstrapSplits(chars, n, 400, 42, 0, false, List.of(dnaToSplits())), n);
		assertTrue(all.get(set(1, 2)) > 50 && all.get(set(1, 2)) < 75);
		var filtered = confidences(bootstrapSplits(chars, n, 400, 42, 80, false, List.of(dnaToSplits())), n);
		assertFalse(filtered.containsKey(set(1, 2)));
		assertEquals(100.0, filtered.get(set(1, 2, 3, 4, 5)), 1e-9);
		for (var value : all.values()) assertTrue(value >= 0 && value <= 100);
	}

	/**
	 * an algorithm of the pipeline that fails in some replicates: the bootstrap must fail, not report the support of
	 * the other replicates as if it were that of all. Only an IOException was once passed on
	 */
	@Test
	void aFailingReplicateIsNotSwallowed() {
		var n = 6;
		var splits = Map.of(set(1, 2), 5, set(1, 2, 3), 5);
		var chars = sites(n, 60, new HashMap<>(splits));
		var failing = new Characters2Splits() {
			private final java.util.concurrent.atomic.AtomicInteger calls = new java.util.concurrent.atomic.AtomicInteger();

			@Override
			public void compute(ProgressListener progress, TaxaBlock taxaBlock, CharactersBlock characters, SplitsBlock splitsBlock) throws java.io.IOException {
				if (calls.incrementAndGet() % 3 == 0) throw new IllegalStateException("replicate failed");
				dnaToSplits().compute(progress, taxaBlock, characters, splitsBlock);
			}
		};
		assertThrows(Exception.class, () -> bootstrapSplits(chars, n, 60, 42, 0, false, List.of(failing)));
	}

	// ---------- trees ----------

	private static double support(PhyloTree tree, BitSet cluster) {
		for (var e : tree.edges()) {
			var below = new BitSet();
			collect(tree, e.getTarget(), below);
			if (below.equals(cluster)) {
				if (tree.hasEdgeConfidences() && tree.getEdgeConfidences().containsKey(e)) return tree.getConfidence(e);
				return Double.parseDouble(tree.getLabel(e.getTarget()));
			}
		}
		return Double.NaN;
	}

	private static void collect(PhyloTree tree, jloda.graph.Node v, BitSet set) {
		for (var t : tree.getTaxa(v)) set.set(t);
		for (var e : v.outEdges()) collect(tree, e.getTarget(), set);
	}

	private static PhyloTree bootstrapTree(CharactersBlock chars, int n, int replicates, int seed, double minPercent, boolean transfer) throws Exception {
		List<Algorithm<?, ?>> pipeline = List.of(new HammingDistance(), new NeighborJoining());
		var distances = new DistancesBlock();
		new HammingDistance().compute(new ProgressSilent(), taxa(n), chars, distances);
		var trees = new TreesBlock();
		new NeighborJoining().compute(new ProgressSilent(), taxa(n), distances, trees);
		var bootstrap = new BootstrapTree();
		bootstrap.setOptionReplicates(replicates);
		bootstrap.setOptionRandomSeed(seed);
		bootstrap.setOptionMinPercent(minPercent);
		bootstrap.setOptionTransferBootstrap(transfer);
		var result = new TreesBlock();
		bootstrap.compute(new ProgressSilent(), taxa(n), trees, result, chars, BootstrappingUtils.newPathSupplier(pipeline, TreesBlock.class));
		assertEquals(clusters(trees.getTree(1)).size() >= clusters(result.getTree(1)).size(), true);
		return result.getTree(1);
	}

	/**
	 * the sides of the inner edges of a tree that do not contain taxon 1, with their support
	 */
	private static Map<BitSet, Double> supports(PhyloTree tree, int n) {
		var result = new HashMap<BitSet, Double>();
		for (var e : tree.edges()) {
			if (e.getTarget().isLeaf()) continue;
			var below = new BitSet();
			collect(tree, e.getTarget(), below);
			var side = canonical(below, n);
			var other = new BitSet();
			other.set(1, n + 1);
			other.andNot(side);
			if (other.cardinality() < 2 || side.cardinality() < 2) continue;
			result.put(side, support(tree, below));
		}
		return result;
	}

	/**
	 * four taxa, a sites for 12|34 and b for 13|24: the tree of a replicate has 12|34 if more of the first kind are
	 * drawn, and may have it if equally many are. So the support lies between the probabilities of "more" and of
	 * "at least as many", which are those of a multinomial distribution
	 */
	@Test
	void supportOfAnEdgeBetweenTwoConflictingSplits() throws Exception {
		int a = 6, b = 3, total = 60, replicates = 2000;
		var splits = new HashMap<BitSet, Integer>();
		splits.put(set(1, 2), a);
		splits.put(set(1, 3), b);
		var tree = bootstrapTree(sites(4, total, splits), 4, replicates, 42, 0, false);
		var found = supports(tree, 4);
		assertEquals(Set.of(set(1, 2)), found.keySet());
		// the numbers drawn of the two kinds: sum over all pairs
		double more = 0, equal = 0;
		var logFactorial = new double[total + 1];
		for (var i = 1; i <= total; i++) logFactorial[i] = logFactorial[i - 1] + Math.log(i);
		for (var x = 0; x <= total; x++)
			for (var y = 0; x + y <= total; y++) {
				var p = Math.exp(logFactorial[total] - logFactorial[x] - logFactorial[y] - logFactorial[total - x - y]
								 + x * Math.log(a / (double) total) + y * Math.log(b / (double) total) + (total - x - y) * Math.log((total - a - b) / (double) total));
				if (x > y) more += p;
				else if (x == y) equal += p;
			}
		var tolerance = 100 * 4.5 * Math.sqrt(0.25 / replicates);
		var support = found.get(set(1, 2));
		assertTrue(support >= 100 * more - tolerance && support <= 100 * (more + equal) + tolerance, "support " + support + ", expected between " + 100 * more + " and " + 100 * (more + equal));
	}

	@Test
	void treeBootstrapIsReproducibleAndContractsWeakEdges() throws Exception {
		var random = new Random(302);
		var n = 12;
		var chars = characters(CharactersType.DNA, "acgt", simulateSequences(n, 200, 0.03, 0, random));
		var first = supports(bootstrapTree(chars, n, 200, 42, 0, false), n);
		assertEquals(n - 3, first.size(), "with no minimum, every inner edge is kept");
		assertEquals(first, supports(bootstrapTree(chars, n, 200, 42, 0, false), n));
		ProgramExecutorService.setNumberOfCoresToUse(1);
		assertEquals(first, supports(bootstrapTree(chars, n, 200, 42, 0, false), n), "one thread");
		ProgramExecutorService.setNumberOfCoresToUse(cores);
		assertNotEquals(first, supports(bootstrapTree(chars, n, 200, 7, 0, false), n));
		for (var value : first.values()) assertTrue(value >= 0 && value <= 100);
		var threshold = 0.5 * (Collections.min(first.values()) + Collections.max(first.values()));
		var strong = new HashMap<>(first);
		strong.values().removeIf(value -> value < threshold);
		assertTrue(strong.size() < first.size() && !strong.isEmpty(), "the example needs edges of different support: " + first.values());
		assertEquals(strong, supports(bootstrapTree(chars, n, 200, 42, threshold, false), n));
	}

	/**
	 * transfer bootstrap (Lemoine et al 2018). For a pair of taxa it is the usual support. For 123|456, when a
	 * replicate lacks it and has the pair 1,2, one taxon has to move, which counts 1 - 1/(3-1) = 1/2; here every
	 * replicate has the pair, so the transfer support is the usual support plus half of the rest. A pair that is
	 * missing from a replicate once counted as present
	 */
	@Test
	void transferBootstrap() throws Exception {
		var n = 6;
		var splits = new HashMap<BitSet, Integer>();
		splits.put(set(1, 2), 30);
		splits.put(set(1, 2, 3, 4), 30); // 56
		splits.put(set(1, 2, 3), 5);
		splits.put(set(1, 2, 4), 3);
		splits.put(set(1, 2, 3, 4, 6), 1); // weakens the pair 5,6: sites that put 5 apart
		var chars = sites(n, 120, splits);
		var usual = supports(bootstrapTree(chars, n, 500, 42, 0, false), n);
		var transfer = supports(bootstrapTree(chars, n, 500, 42, 0, true), n);
		assertEquals(usual.keySet(), transfer.keySet());
		assertEquals(100.0, usual.get(set(1, 2)), 1e-9);
		assertEquals(usual.get(set(1, 2)), transfer.get(set(1, 2)), 1e-9, "a pair");
		assertEquals(usual.get(set(1, 2, 3, 4)), transfer.get(set(1, 2, 3, 4)), 1e-9, "the pair 5,6");
		var middle = usual.get(set(1, 2, 3));
		assertTrue(middle > 40 && middle < 95, "the example needs a middle edge that is sometimes missing: " + middle);
		assertEquals(middle + 0.5 * (100 - middle), transfer.get(set(1, 2, 3)), 0.11, "123|456");

		// a pair that is not in every replicate
		var weak = new HashMap<BitSet, Integer>();
		weak.put(set(1, 2), 2);
		weak.put(set(1, 3), 1);
		weak.put(set(1, 2, 3, 4), 30);
		weak.put(set(1, 2, 3), 30);
		var weakChars = sites(n, 120, weak);
		var usualWeak = supports(bootstrapTree(weakChars, n, 500, 42, 0, false), n);
		var transferWeak = supports(bootstrapTree(weakChars, n, 500, 42, 0, true), n);
		assertTrue(usualWeak.get(set(1, 2)) < 95, "the example needs a pair that is sometimes missing: " + usualWeak);
		assertEquals(usualWeak.get(set(1, 2)), transferWeak.get(set(1, 2)), 1e-9, "a pair has no transfer support beyond the usual one");
	}

	/**
	 * the support values are written with a decimal point whatever the language of the computer; with a decimal
	 * comma they could not be read back
	 */
	@Test
	void supportValuesDoNotDependOnTheLocale() throws Exception {
		var random = new Random(303);
		var n = 10;
		var chars = characters(CharactersType.DNA, "acgt", simulateSequences(n, 150, 0.03, 0, random));
		var english = supports(bootstrapTree(chars, n, 150, 42, 0, false), n);
		Locale.setDefault(Locale.GERMANY);
		assertEquals(english, supports(bootstrapTree(chars, n, 150, 42, 0, false), n));
	}

	/**
	 * the program window stores the support values as confidences of the edges, the command-line tools as labels of
	 * the nodes; both must give the same values, with or without contracted edges, in any language.
	 * In the window, with a language that writes a decimal comma, the tree bootstrap once failed with a
	 * NumberFormatException; as labels, the value above a contracted edge was lost
	 */
	@Test
	void supportAsEdgeConfidenceAndAsNodeLabelAgree() throws Exception {
		var random = new Random(302);
		var n = 12;
		var chars = characters(CharactersType.DNA, "acgt", simulateSequences(n, 200, 0.03, 0, random));
		for (var minPercent : new double[]{0, 95, 99}) {
			PhyloTree.SUPPORT_RICH_NEWICK = false;
			var labels = supports(bootstrapTree(chars, n, 200, 42, minPercent, false), n);
			for (var language : new Locale[]{Locale.US, Locale.GERMANY}) {
				Locale.setDefault(language);
				PhyloTree.SUPPORT_RICH_NEWICK = true;
				assertEquals(labels, supports(bootstrapTree(chars, n, 200, 42, minPercent, false), n), "minimum " + minPercent + ", " + language);
			}
			Locale.setDefault(locale);
		}
	}

	/**
	 * bootstrapping the splits of a tree gives the support of the tree bootstrap
	 */
	@Test
	void bootstrapTreeSplitsAgreesWithBootstrapTree() throws Exception {
		var random = new Random(304);
		var n = 10;
		var chars = characters(CharactersType.DNA, "acgt", simulateSequences(n, 200, 0.03, 0, random));
		var tree = supports(bootstrapTree(chars, n, 200, 42, 0, false), n);
		List<Algorithm<?, ?>> pipeline = List.of(new HammingDistance(), new NeighborJoining());
		var distances = new DistancesBlock();
		new HammingDistance().compute(new ProgressSilent(), taxa(n), chars, distances);
		var trees = new TreesBlock();
		new NeighborJoining().compute(new ProgressSilent(), taxa(n), distances, trees);
		var bootstrap = new BootstrapTreeSplits();
		bootstrap.setOptionReplicates(200);
		bootstrap.setOptionRandomSeed(42);
		bootstrap.setOptionMinPercent(0);
		bootstrap.setOptionHighDimensionFilter(false);
		var result = new SplitsBlock();
		bootstrap.compute(new ProgressSilent(), taxa(n), trees, result, chars, BootstrappingUtils.newPathSupplier(pipeline, SplitsBlock.class));
		var splits = confidences(result, n);
		for (var side : tree.keySet()) {
			if (tree.get(side) == 0) assertFalse(splits.containsKey(side) && splits.get(side) > 0);
			else assertEquals(tree.get(side), splits.get(side), 0.051, side.toString());
		}
	}
}
