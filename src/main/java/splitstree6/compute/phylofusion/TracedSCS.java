/*
 *  TracedSCS.java Copyright (C) 2024 Daniel H. Huson
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

import jloda.graph.NodeArray;
import jloda.util.*;
import jloda.util.progress.ProgressSilent;
import splitstree6.algorithms.distances.distances2trees.UPGMA;
import splitstree6.data.DistancesBlock;
import splitstree6.data.TaxaBlock;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashSet;
import java.util.function.BiConsumer;

/**
 * shortest common hyper-sequence for {@link TracedHyperSequence}: the taxa-only dynamic program of
 * ShortestCommonHyperSequence/ProgressiveSCS, extended to carry per-taxon tree-id metadata through the alignment.
 * <p>
 * Banu Cetinkaya, 2026
 */
public class TracedSCS {
	private static final byte TRACEBACK_INSERT_A = 1;
	private static final byte TRACEBACK_INSERT_B = 2;
	private static final byte TRACEBACK_MATCH = 4;

	/**
	 * progressive shortest common hyper-sequence over a collection of sequences
	 */
	public static TracedHyperSequence progressive(ArrayList<TracedHyperSequence> hyperSequences) {
		if (hyperSequences.size() == 1)
			return hyperSequences.get(0);
		else if (hyperSequences.size() == 2) {
			var expanded = preProcessExpansion(hyperSequences.get(0), hyperSequences.get(1));
			return align(expanded.getFirst(), expanded.getSecond());
		} else if (hyperSequences.size() == 3) {
			var one = align(hyperSequences.get(0), hyperSequences.get(1));
			return align(one, hyperSequences.get(2));
		}

		// set up distances for UPGMA and align progressively up the resulting tree
		var taxa = new TaxaBlock();
		for (var t = 0; t < hyperSequences.size(); t++)
			taxa.addTaxonByName("s" + t);
		var distancesBlock = new DistancesBlock();
		distancesBlock.setNtax(taxa.getNtax());
		for (var i = 1; i <= taxa.getNtax(); i++) {
			var si = hyperSequences.get(i - 1);
			for (var j = i + 1; j <= taxa.getNtax(); j++) {
				var sj = hyperSequences.get(j - 1);
				var aligned = align(si, sj);
				var minLength = Math.min(si.size(), sj.size());
				var d = (double) (aligned.size() - minLength) / (double) minLength;
				distancesBlock.set(i, j, d);
				distancesBlock.set(j, i, d);
			}
		}
		try {
			var tree = UPGMA.computeUPGMATree(new ProgressSilent(), taxa, distancesBlock);
			try (NodeArray<TracedHyperSequence> mhs = tree.newNodeArray()) {
				tree.postorderTraversal(u -> {
					if (u.isLeaf()) {
						mhs.put(u, hyperSequences.get(tree.getTaxon(u) - 1));
					} else {
						var v = u.getFirstOutEdge().getTarget();
						var w = u.getLastOutEdge().getTarget();
						mhs.put(u, align(mhs.get(v), mhs.get(w)));
					}
				});
				return mhs.get(tree.getRoot());
			}
		} catch (CanceledException ignored) {
			return null; // can't happen
		}
	}

	/**
	 * shortest common hyper-sequence of two sequences via dynamic programming (taxa drive the DP; metadata is merged)
	 */
	public static TracedHyperSequence align(TracedHyperSequence a, TracedHyperSequence b) {
		var m = a.size();
		var n = b.size();

		var matrix = new int[m + 1][n + 1];
		var traceback = new byte[m + 1][n + 1];

		for (var i = 1; i <= m; i++) {
			matrix[i][0] = i;
			traceback[i][0] = TRACEBACK_INSERT_A;
		}
		for (var j = 1; j <= n; j++) {
			matrix[0][j] = j;
			traceback[0][j] = TRACEBACK_INSERT_B;
		}

		for (var i = 1; i <= m; i++) {
			var i1 = i - 1;
			for (var j = 1; j <= n; j++) {
				var j1 = j - 1;
				var insertionInA = matrix[i1][j] + 1;
				var insertionInB = matrix[i][j1] + 1;
				if (BitSetUtils.contains(a.get(i1), b.get(j1)) || BitSetUtils.contains(b.get(j1), a.get(i1))) {
					var match = matrix[i1][j1];
					var best = NumberUtils.min(insertionInA, insertionInB, match);
					if (insertionInA == best) traceback[i][j] |= TRACEBACK_INSERT_A;
					if (insertionInB == best) traceback[i][j] |= TRACEBACK_INSERT_B;
					if (match == best) traceback[i][j] |= TRACEBACK_MATCH;
					matrix[i][j] = best;
				} else {
					var best = Math.min(insertionInA, insertionInB);
					if (insertionInA == best) traceback[i][j] |= TRACEBACK_INSERT_A;
					if (insertionInB == best) traceback[i][j] |= TRACEBACK_INSERT_B;
					matrix[i][j] = best;
				}
			}
		}

		var best = new Single<>(Integer.MAX_VALUE);
		var result = new Single<TracedHyperSequence>();
		var seen = new HashSet<Pair<ArrayList<Integer>, ArrayList<Integer>>>();

		traceback(m, n, matrix, traceback, 100, (aTrace, bTrace) -> {
			var pair = new Pair<>(aTrace, bTrace);
			if (!seen.add(pair))
				return;

			aTrace = CollectionUtils.reverse(aTrace);
			bTrace = CollectionUtils.reverse(bTrace);

			var superSequence = new TracedHyperSequence();
			for (var p = 0; p < aTrace.size(); p++) {
				TracedHyperSequence.Element merged;
				if (aTrace.get(p) != -1 && bTrace.get(p) != -1) {
					merged = mergeElements(a.getElement(aTrace.get(p)), b.getElement(bTrace.get(p)));
				} else if (aTrace.get(p) != -1) {
					merged = a.getElement(aTrace.get(p)).copy();
				} else if (bTrace.get(p) != -1) {
					merged = b.getElement(bTrace.get(p)).copy();
				} else {
					merged = new TracedHyperSequence.Element(new BitSet());
				}
				superSequence.add(merged);
			}

			// simplification: drop from each element the taxa carried by the next, transferring their metadata forward
			var simplified = new TracedHyperSequence();
			var count = 0;
			var working = new ArrayList<TracedHyperSequence.Element>();
			for (var element : superSequence.elements())
				working.add(element.copy());
			for (var i = 0; i < working.size() - 1; i++) {
				var current = working.get(i);
				var next = working.get(i + 1);
				transferOverlapTreeInfo(current, next);
				var keep = BitSetUtils.minus(current.taxa(), next.taxa());
				if (keep.cardinality() > 0) {
					count += keep.cardinality();
					simplified.add(restrictElement(current, keep));
				}
			}
			if (!working.isEmpty()) {
				var last = working.get(working.size() - 1);
				count += last.taxa().cardinality();
				simplified.add(last.copy());
			}

			if (count < best.get()) {
				best.set(count);
				result.set(simplified);
			}
		});

		return result.get();
	}

	private static void traceback(int m, int n, int[][] matrix, byte[][] traceback, int maxResults, BiConsumer<ArrayList<Integer>, ArrayList<Integer>> tracebackConsumer) {
		traceBackRec(m, n, matrix[m][n], matrix, traceback, new ArrayList<>(), new ArrayList<>(), new Counter(maxResults), tracebackConsumer);
	}

	private static void traceBackRec(final int i, final int j, final int value, int[][] matrix, byte[][] traceback, ArrayList<Integer> aTrace, ArrayList<Integer> bTrace, Counter resultsToConsume, BiConsumer<ArrayList<Integer>, ArrayList<Integer>> tracebackConsumer) {
		if ((traceback[i][j] & TRACEBACK_INSERT_A) != 0) {
			aTrace.add(i - 1);
			bTrace.add(-1);
			traceBackRec(i - 1, j, matrix[i - 1][j], matrix, traceback, aTrace, bTrace, resultsToConsume, tracebackConsumer);
			aTrace.remove(aTrace.size() - 1);
			bTrace.remove(bTrace.size() - 1);
			if (resultsToConsume.get() == 0)
				return;
		}
		if ((traceback[i][j] & TRACEBACK_INSERT_B) != 0) {
			aTrace.add(-1);
			bTrace.add(j - 1);
			traceBackRec(i, j - 1, matrix[i][j - 1], matrix, traceback, aTrace, bTrace, resultsToConsume, tracebackConsumer);
			aTrace.remove(aTrace.size() - 1);
			bTrace.remove(bTrace.size() - 1);
			if (resultsToConsume.get() == 0)
				return;
		}
		if ((traceback[i][j] & TRACEBACK_MATCH) != 0) {
			aTrace.add(i - 1);
			bTrace.add(j - 1);
			traceBackRec(i - 1, j - 1, matrix[i - 1][j - 1], matrix, traceback, aTrace, bTrace, resultsToConsume, tracebackConsumer);
			aTrace.remove(aTrace.size() - 1);
			bTrace.remove(bTrace.size() - 1);
			if (resultsToConsume.get() == 0)
				return;
		}
		if (i == 0 && j == 0) {
			tracebackConsumer.accept(aTrace, bTrace);
			resultsToConsume.decrement();
		}
	}

	private static Pair<TracedHyperSequence, TracedHyperSequence> preProcessExpansion(TracedHyperSequence a, TracedHyperSequence b) {
		var aExpanded = new TracedHyperSequence();
		var bExpanded = new TracedHyperSequence();
		for (var i = 0; i < 2; i++) {
			var first = (i == 0 ? a : b);
			var second = (i == 0 ? b : a);
			var expanded = (i == 0 ? aExpanded : bExpanded);
			for (var element : first.elements()) {
				var set = element.taxa();
				if (set.cardinality() == 1)
					expanded.add(element.copy());
				else {
					var remaining = BitSetUtils.copy(set);
					var list = new ArrayList<TracedHyperSequence.Element>();
					for (var other : second.elements()) {
						if (set.intersects(other.taxa())) {
							var intersection = BitSetUtils.intersection(set, other.taxa());
							remaining.andNot(intersection);
							list.add(restrictElement(element, intersection));
						}
					}
					if (remaining.cardinality() > 0)
						expanded.add(restrictElement(element, remaining));
					list.forEach(expanded::add);
				}
			}
		}
		return new Pair<>(aExpanded, bExpanded);
	}

	private static TracedHyperSequence.Element mergeElements(TracedHyperSequence.Element a, TracedHyperSequence.Element b) {
		var taxa = BitSetUtils.copy(a.taxa());
		taxa.or(b.taxa());
		var merged = new TracedHyperSequence.Element(taxa);
		merged.mergeMetadata(a);
		merged.mergeMetadata(b);
		merged.restrictToTaxa(taxa);
		return merged;
	}

	private static TracedHyperSequence.Element restrictElement(TracedHyperSequence.Element element, BitSet taxa) {
		var restricted = element.copy();
		restricted.taxa().clear();
		restricted.taxa().or(taxa);
		restricted.restrictToTaxa(taxa);
		return restricted;
	}

	private static void transferOverlapTreeInfo(TracedHyperSequence.Element current, TracedHyperSequence.Element next) {
		var overlap = (BitSet) current.taxa().clone();
		overlap.and(next.taxa());
		for (int taxon = overlap.nextSetBit(0); taxon >= 0; taxon = overlap.nextSetBit(taxon + 1)) {
			var currentTrees = current.treeIdsPerTaxon().get(taxon);
			if (currentTrees != null)
				next.treeIdsPerTaxon().computeIfAbsent(taxon, k -> new BitSet()).or((BitSet) currentTrees.clone());
		}
	}
}
