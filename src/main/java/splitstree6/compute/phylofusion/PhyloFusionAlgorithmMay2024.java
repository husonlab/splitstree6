/*
 *  PhyloFusionAlgorithmMay2024.java Copyright (C) 2024 Daniel H. Huson
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

package splitstree6.compute.phylofusion;

import jloda.phylo.PhyloGraph;
import jloda.phylo.PhyloTree;
import jloda.util.*;
import jloda.util.progress.ProgressListener;
import jloda.util.progress.ProgressSilent;
import splitstree6.utils.PathMultiplicityDistance;
import splitstree6.utils.ProgressTimeOut;
import splitstree6.xtra.hyperstrings.HyperSequence;
import splitstree6.xtra.hyperstrings.ProgressiveSCS;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Supplier;

import static splitstree6.compute.phylofusion.PhyloFusionAlgorithmOct2024.*;

/**
 * the PhyloFusion algorithm
 * Daniel Huson, 5.2024
 */
public class PhyloFusionAlgorithmMay2024 {
	/**
	 * run the algorithm
	 *
	 * @param inputTrees input rooted trees
	 * @param progress   progress listener
	 * @return the computed networks
	 * @throws IOException user canceled
	 */
	public static List<PhyloTree> apply(long numberOfRandomOrderings, List<PhyloTree> inputTrees, boolean onlyOneNetwork, ProgressListener progress) throws IOException {
		if (inputTrees.size() == 1) {
			return List.of(new PhyloTree(inputTrees.get(0)));
		}

		var taxa = union(inputTrees.stream().map(PhyloGraph::getTaxa).toList());

		var trees = new ArrayList<PhyloTree>();
		for (var tree : inputTrees) {
			tree = new PhyloTree(tree);
			if (tree.getRoot().getOutDegree() > 1) {
				var root = tree.newNode();
				var e = tree.newEdge(root, tree.getRoot());
				tree.setWeight(e, 0);
				tree.setRoot(root);
			}
			trees.add(tree);
		}

		var rankings = computeTaxonRankings(progress, taxa, trees, numberOfRandomOrderings);

		var bestHybridizationNumber = new Single<>(Integer.MAX_VALUE);
		var best = new ArrayList<Pair<int[], Map<Integer, HyperSequence>>>();

		progress.setSubtask("calculating");
		progress.setMaximum(rankings.size());
		progress.setProgress(0);

		try {
			ExecuteInParallel.apply(rankings, taxonRank -> {
				var taxonHyperSequencesMap = computeHyperSequences(progress, taxa, taxonRank, trees);
				var taxonHyperSequenceMap = new HashMap<Integer, HyperSequence>();
				// todo: take different optimal SCS into account
				for (var t : taxonHyperSequencesMap.keySet()) {
					var hyperSequence = ProgressiveSCS.apply(new ArrayList<>(taxonHyperSequencesMap.get(t)));

					// simplification
					if (hyperSequence != null) {
						var prev = new BitSet();
						for (var i = 0; i + 1 < hyperSequence.size(); i++) {
							var item = hyperSequence.get(i);
							var next = hyperSequence.get(i + 1);
							prev = BitSetUtils.minus(BitSetUtils.intersection(item, next), prev);
							item.andNot(prev);
						}
						hyperSequence.removeEmptyElements();
					}
					taxonHyperSequenceMap.put(t, hyperSequence);

				}
				var hybridizationNumber = computeHybridizationNumber(taxa.cardinality(), taxonHyperSequenceMap);
				synchronized (bestHybridizationNumber) {
					if (hybridizationNumber < bestHybridizationNumber.get()) {
						bestHybridizationNumber.set(hybridizationNumber);
						best.clear();
					}
					if (hybridizationNumber == bestHybridizationNumber.get()) {
						best.add(new Pair<>(taxonRank, taxonHyperSequenceMap));
					}
					progress.incrementProgress();
				}
			}, ProgramExecutorService.getNumberOfCoresToUse(), new ProgressSilent());
		} catch (Exception e) {
			throw new IOException(e);
		}

		if (onlyOneNetwork && best.size() > 1) {
			var one = best.get(0);
			best.clear();
			best.add(one);
		}

		progress.setSubtask("creating networks");
		progress.setMaximum(best.size());
		progress.setProgress(0);

		var result = new ArrayList<PhyloTree>();
		for (var network : best.stream().map(pair -> computeNetwork(pair.getFirst(), pair.getSecond())).toList()) {
			if (result.stream().noneMatch(other -> PathMultiplicityDistance.compute(network, other) == 0)) {
				result.add(network);
			}
			progress.incrementProgress();
		}

		return result;
	}

	/**
	 * compute taxon orderings to consider, using greedy heuristic
	 *
	 * @param trees input trees
	 * @return taxon rankings
	 */
	private static Collection<int[]> computeTaxonRankings(ProgressListener progress, BitSet taxa, ArrayList<PhyloTree> trees, long numberOfRandomOrderings) throws CanceledException {

		var rankings = new ArrayList<int[]>();

		if (false && taxa.cardinality() < 9) {
			computeAllRankingsRec(new BitSet(), taxa, new int[taxa.cardinality()], rankings);
			System.err.printf("Using all %,d taxon rankings for n=%,d%n", rankings.size(), taxa.cardinality());
			return rankings;
		}


		var nThreads = ProgramExecutorService.getNumberOfCoresToUse();
		var executor = Executors.newFixedThreadPool(nThreads);

		try {
			var globalScore = new Single<>(Integer.MAX_VALUE);

			progress.setSubtask("Searching");
			progress.setMaximum(numberOfRandomOrderings);
			progress.setProgress(0);

			var queue = new LinkedBlockingQueue<ArrayList<Integer>>(2 * nThreads);
			var sentinel = new ArrayList<Integer>();

			var countdownLatch = new CountDownLatch(nThreads);

			var total = new LongAdder();

			var mainThread = Thread.currentThread();

			for (var t = 0; t < nThreads; t++) {
				executor.submit(() -> {
					try {
						var silent = new ProgressSilent();
						while (true) {
							var ordering = queue.take();
							if (ordering == sentinel) {
								break;
							}
							try {
								computeTaxonRankingsRec(silent, 0, new int[taxa.cardinality()], taxa, ordering, trees, globalScore, rankings);
							} catch (CanceledException ignored) {
							}
							progress.checkForCancel();
						}
					} catch (InterruptedException ignored) {
					} catch (CanceledException ignored) {
						executor.shutdownNow();
						mainThread.interrupt();
					} finally {
						countdownLatch.countDown();
					}
				});
			}

			var randomOrderSupplier = randomTaxonOrderingsSupplier(taxa);
			do {
				queue.put(randomOrderSupplier.get());
				total.increment();
				progress.incrementProgress();
			}
			while (total.longValue() < numberOfRandomOrderings);

			for (var t = 0; t < nThreads; t++) {
				queue.put(sentinel);
			}

			rankings.sort((a, b) -> {
				for (int i = 0; i < a.length; i++) {
					if (a[i] < b[i])
						return -1;
					else if (a[i] > b[i])
						return 1;
				}
				return 0;
			});

			countdownLatch.await();

		} catch (InterruptedException e) {
			throw new CanceledException(ProgressTimeOut.MESSAGE);
		} finally {
			executor.shutdownNow();
		}
		progress.checkForCancel();

		return rankings;
	}

	private static void computeAllRankingsRec(BitSet used, BitSet taxa, int[] ordering, ArrayList<int[]> rankings) {
		if (used.cardinality() == taxa.cardinality()) {
			rankings.add(ranking(ordering));
		} else {
			for (var t : BitSetUtils.members(taxa)) {
				if (!used.get(t)) {
					ordering[used.cardinality()] = t;
					used.set(t, true);
					computeAllRankingsRec(used, taxa, ordering, rankings);
					used.set(t, false);
				}
			}
		}

	}

	/**
	 * recursively use greedy heuristic to compute best order
	 *
	 * @param pos           position in orde
	 * @param order         current order
	 * @param remainingTaxa remaining taxa
	 * @param trees         all trees
	 * @param globalScore   best hybridization number so far
	 * @param rankings      best orderings
	 */
	private static void computeTaxonRankingsRec(ProgressListener progress, int pos, int[] order, BitSet allTaxa, ArrayList<Integer> remainingTaxa, ArrayList<PhyloTree> trees,
												Single<Integer> globalScore, ArrayList<int[]> rankings) throws CanceledException {
		var bestScore = Integer.MAX_VALUE;
		Integer bestTaxon = 0; // needs to be Integer not int, otherwise wrong entry is removed from remaining taxa

		for (var t : remainingTaxa) {
			order[pos] = t;
			// fill up with remaining:
			var nextPos = pos + 1;
			for (var s : remainingTaxa) {
				if (!s.equals(t)) {
					order[nextPos++] = s;
				}
			}
			if (true) {
				var seen = new BitSet();
				for (var taxon : order) {
					if (seen.get(taxon))
						System.err.println("ERROR: Already seen: " + taxon);
					else seen.set(taxon);
				}
				if (BitSetUtils.compare(allTaxa, seen) != 0) {
					System.err.println("ERROR: taxa!=seen: " + SetUtils.symmetricDifference(BitSetUtils.asSet(allTaxa), BitSetUtils.asSet(seen)));
				}
			}

			var taxonHyperSequencesMap = computeHyperSequences(progress, allTaxa, ranking(order), trees);
			var taxonHyperSequenceMap = new HashMap<Integer, HyperSequence>();
			// todo: take different optimal SCS into account
			for (var t1 : taxonHyperSequencesMap.keySet()) {
				taxonHyperSequenceMap.put(t1, ProgressiveSCS.apply(new ArrayList<>(taxonHyperSequencesMap.get(t1))));
			}
			var h = computeHybridizationNumber(allTaxa.cardinality(), taxonHyperSequenceMap);

			// compute score
			if (h < bestScore) {
				bestTaxon = t;
				bestScore = h;
			}
		}

		remainingTaxa.remove(bestTaxon); // remove best and continue

		order[pos] = bestTaxon;
		for (var p = pos + 1; p < order.length; p++) {
			order[p] = 0;
		}

		if (!remainingTaxa.isEmpty()) {
			if (bestScore <= globalScore.get()) {
				computeTaxonRankingsRec(progress, pos + 1, order, allTaxa, remainingTaxa, trees, globalScore, rankings);
			}
		} else { // completed ordering,
			if (bestScore <= globalScore.get()) {
				var ranking = ranking(order);
				synchronized (PhyloFusionAlgorithmMay2024.class) {
					if (bestScore < globalScore.get()) {
						globalScore.set(bestScore);
						rankings.clear();
					}
					if (bestScore == globalScore.get()) {
						rankings.add(ranking);
					}
				}
			}
		}
	}

	public static Supplier<ArrayList<Integer>> randomTaxonOrderingsSupplier(BitSet taxa) {
		return new Supplier<>() {
			private final Random random = new Random(42L);

			@Override
			public ArrayList<Integer> get() {
				return CollectionUtils.randomize(BitSetUtils.asList(taxa), random.nextLong());
			}
		};
	}
}
