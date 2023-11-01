/*
 * SplitsBlockUtilities.java Copyright (C) 2023 Daniel H. Huson
 *
 * (Some files contain contributions from other authors, who are then mentioned separately.)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package splitstree6.algorithms.utils;

import jloda.graph.algorithms.PQTree;
import jloda.util.Basic;
import jloda.util.BitSetUtils;
import jloda.util.CanceledException;
import jloda.util.Pair;
import jloda.util.progress.ProgressPercentage;
import jloda.util.progress.ProgressSilent;
import splitstree6.algorithms.distances.distances2splits.neighbornet.NeighborNetCycleSplitsTree4;
import splitstree6.algorithms.distances.distances2trees.NeighborJoining;
import splitstree6.data.DistancesBlock;
import splitstree6.data.SplitsBlock;
import splitstree6.data.TaxaBlock;
import splitstree6.data.TreesBlock;
import splitstree6.splits.ASplit;
import splitstree6.splits.Compatibility;
import splitstree6.splits.SplitUtils;

import java.util.*;
import java.util.stream.Collectors;

/**
 * utilities for splits
 * Daniel Huson, 12.2021
 */
public class SplitsBlockUtilities {
	/**
	 * computes the least squares fit
	 *
	 * @return squares fit
	 */
	public static float computeLeastSquaresFit(DistancesBlock distancesBlock, List<ASplit> splits) {
		final int nTax = distancesBlock.getNtax();
		final double[][] splitDist = new double[nTax + 1][nTax + 1];

		double sumDSquared = 0.0;
		double sumDiffSquared = 0.0;

		for (ASplit split : splits) {
			for (int i : BitSetUtils.members(split.getA())) {
				for (int j : BitSetUtils.members(split.getB())) {
					splitDist[i][j] += split.getWeight();
					splitDist[j][i] = splitDist[i][j];
				}
			}
		}

		for (int i = 1; i <= nTax; i++) {
			for (int j = i + 1; j <= nTax; j++) {
				double sij = splitDist[i][j];
				double dij = distancesBlock.get(i, j);
				sumDiffSquared += (sij - dij) * (sij - dij);
				sumDSquared += dij * dij;
			}
		}

		return (float) (sumDSquared > 0 ? (100.0 * (1.0 - sumDiffSquared / sumDSquared)) : 0);
	}

	/**
	 * Determines the decomposition-fit of a splits system
	 */
	static public float computeSplitDecompositionFit(DistancesBlock dist, List<ASplit> splits) {
		if (splits == null || dist == null)
			return 0f;

		final var ntax = dist.getNtax();

		var sdist = new double[ntax + 1][ntax + 1];

		for (var a : BitSetUtils.range(1, ntax + 1)) {
			for (var b : BitSetUtils.range(a + 1, ntax + 1)) {
				sdist[a][b] = sdist[b][a] = (float) splits.stream().filter(s -> s.separates(a, b)).mapToDouble(ASplit::getWeight).sum();
			}
		}

		float dsum = 0;
		float ssum = 0;

		for (var i = 1; i <= ntax; i++) {
			for (var j = i + 1; j <= ntax; j++) {
				double sij = sdist[i][j];
				double dij = dist.get(i, j);
				ssum += Math.abs(sij - dij);
				dsum += dij;
			}
		}
		return (float) Math.max(0, 100.0 * (1.0 - ssum / dsum));
	}


	/**
	 * Given splits, returns the matrix split distances, as the number of splits separating each pair of taxa
	 *
	 * @param splits with 1-based taxa
	 */
	public static void splitsToDistances(List<ASplit> splits, boolean useWeights, DistancesBlock distancesBlock) {
		for (var s : splits) {
			for (var i : BitSetUtils.members(s.getA())) {
				for (var j : BitSetUtils.members(s.getB())) {
					var dist = distancesBlock.get(i, j) + (useWeights ? s.getWeight() : 1);
					distancesBlock.set(i, j, dist);
					distancesBlock.set(j, i, dist);
				}
			}
		}
	}

	/**
	 * is split circular with respect to the given cycle?
	 *
	 * @param cycle uses indices 1 to number-of-taxa
	 * @return true if circular
	 */
	public static boolean isCircular(TaxaBlock taxa, int[] cycle, ASplit split) {
		final var part = (!split.getA().get(cycle[1]) ? split.getA() : split.getB()); // choose part that doesn't go around the horn
		var prev = 0;
		for (var t = 1; t <= taxa.getNtax(); t++) {
			if (part.get(cycle[t])) {
				if (prev != 0 && t != prev + 1)
					return false;
				prev = t;
			}
		}
		return true;
	}

	/**
	 * verify that all splits are proper and are contained in the taxon set
	 */
	public static void verifySplits(Collection<ASplit> splits, TaxaBlock taxa) throws SplitsException {
		final var seen = new HashSet<BitSet>();

		for (var split : splits) {
			final var aSet = split.getA();
			if (seen.contains(aSet))
				throw new SplitsException("Split " + aSet + " occurs multiple times");
			if (aSet.cardinality() == 0)
				throw new SplitsException("Split " + aSet + " not proper, size is 0");
			if (aSet.cardinality() == taxa.getNtax())
				throw new SplitsException("Split " + aSet + " not proper, size is ntax");
			if (aSet.nextSetBit(0) == 0 || aSet.nextSetBit(taxa.getNtax() + 1) != -1)
				throw new SplitsException("Split " + aSet + " not contained in taxa set <" + taxa.getTaxaSet() + ">");
			seen.add(aSet);
		}
	}


	/**
	 * computes a tightest split around a subset of taxa
	 *
	 * @return a tightest split
	 */
	public static int getTighestSplit(SplitsBlock splitsBlock, BitSet subset) {
		var best = 0;
		var bestSideCardinality = Integer.MAX_VALUE;
		for (var s = 1; s <= splitsBlock.getNsplits(); s++) {
			final var split = splitsBlock.get(s);
			if (BitSetUtils.contains(split.getA(), subset) && split.getA().cardinality() < bestSideCardinality) {
				best = s;
				bestSideCardinality = split.getA().cardinality();
			}
			if (BitSetUtils.contains(split.getB(), subset) && (split.getB().cardinality() < bestSideCardinality)) {
				best = s;
				bestSideCardinality = split.getB().cardinality();
			}
		}
		return best;
	}

	public static boolean computeSplitsForLessThan4Taxa(TaxaBlock taxaBlock, DistancesBlock distancesBlock, SplitsBlock splitsBlock) throws CanceledException {
		//TODO: Check that all trivial splits are included.
		if (taxaBlock.getNtax() < 4) {
			final TreesBlock treesBlock = new TreesBlock();
			new NeighborJoining().compute(new ProgressSilent(), taxaBlock, distancesBlock, treesBlock);
			splitsBlock.clear();
			SplitUtils.computeSplits(taxaBlock.getTaxaSet(), treesBlock.getTree(1), splitsBlock.getSplits());
			splitsBlock.setCompatibility(Compatibility.compatible);
			splitsBlock.setCycle(computeCycle(taxaBlock.getNtax(), splitsBlock.getSplits()));
			splitsBlock.setFit(100);
			return true;
		}
		return false;
	}

	/**
	 * returns comparison of maximum distance between any two taxon on side A and on side B of the given split
	 *
	 * @param ntax        number of taxa
	 * @param splitsBlock splits
	 * @param split       split to investigate
	 * @param useWeights  use weighted splits
	 * @return comparison value
	 */
	public static int compareMaxDistanceInSplitParts(int ntax, SplitsBlock splitsBlock, int split, boolean useWeights) {
		var distances = splitsToDistances(ntax, splitsBlock.getSplits(), useWeights);

		var maxA = 0.0;
		for (var a : BitSetUtils.members(splitsBlock.get(split).getA())) {
			for (var b : BitSetUtils.members(splitsBlock.get(split).getA(), a + 1))
				maxA = Math.max(maxA, distances.get(a, b));
		}
		var maxB = 0.0;
		for (var a : BitSetUtils.members(splitsBlock.get(split).getB())) {
			for (var b : BitSetUtils.members(splitsBlock.get(split).getB(), a + 1))
				maxB = Math.max(maxB, distances.get(a, b));
		}
		return Double.compare(maxA, maxB);
	}

	public static List<ASplit> createAllMissingTrivial(Collection<ASplit> splits, int ntax, double weight) {
		var present = new BitSet();
		for (var split : splits) {
			if (split.getA().cardinality() == 1) {
				present.or(split.getA());
			}
			if (split.getB().cardinality() == 1) {
				present.or(split.getB());
			}
		}
		var result = new ArrayList<ASplit>(ntax - present.cardinality());
		for (var t = present.nextClearBit(1); t <= ntax && t != -1; t = present.nextClearBit(t + 1)) {
			var split = new ASplit(BitSetUtils.asBitSet(t), ntax);
			split.setWeight(weight);
			result.add(split);
		}
		return result;
	}

	public static void addAllTrivial(int ntaxa, SplitsBlock splits) {
		if (ntaxa > 2) {
			final var taxaWithTrivialSplit = new BitSet();

			for (var s = 1; s <= splits.getNsplits(); s++) {
				final var split = splits.get(s);
				if (split.isTrivial())
					taxaWithTrivialSplit.set(split.getSmallerPart().nextSetBit(0));
			}
			for (var t = taxaWithTrivialSplit.nextClearBit(1); t != -1 && t <= ntaxa; t = taxaWithTrivialSplit.nextClearBit(t + 1)) {
				splits.getSplits().add(new ASplit(BitSetUtils.asBitSet(t), ntaxa, 0.00001, 0.00001));
			}
		}
	}

	/**
	 * returns the number of splits that are compatible with the given taxon ordering
	 *
	 * @param splits      splits
	 * @param cycle1based ordering, 1-based (i.e., ignore 0-th entry)
	 * @return true, if all splits are compatible with the ordering
	 */
	public static int countCompatibleWithOrdering(Collection<ASplit> splits, int[] cycle1based) {
		var first = cycle1based[1];
		var taxonRank = new int[cycle1based.length];
		for (var t = 1; t < cycle1based.length; t++) {
			taxonRank[cycle1based[t]] = t;
		}
		var count = 0;
		for (var split : splits) {
			var min = Integer.MAX_VALUE;
			var max = 0;
			var part = split.getPartNotContaining(first);
			for (var t : BitSetUtils.members(part)) {
				min = Math.min(min, taxonRank[t]);
				max = Math.max(min, taxonRank[t]);
				if (max - min + 1 == part.cardinality())
					count++;
			}
		}
		return count;
	}

	/**
	 * Computes a cycle for the given splits
	 *
	 * @param ntax   number of taxa
	 * @param splits the splits
	 */
	static public int[] computeCycle(int ntax, List<ASplit> splits) {
		if (false) { // this is too slow for large examples
			var clusters = splits.parallelStream().filter(s -> !s.isTrivial()).map(s -> new Pair<>(s.getWeight() * s.size(), s.getPartNotContaining(1))).collect(Collectors.toCollection(ArrayList::new));
			try (var progress = (clusters.size() > 2000 ? new ProgressPercentage("Computing cycle:", clusters.size()) : new ProgressSilent())) {
				clusters.sort(Comparator.comparingDouble(a -> -a.getFirst()));
				var pqTree = new PQTree(BitSetUtils.asBitSet(BitSetUtils.range(1, ntax + 1)));
				for (var pair : clusters) {
					pqTree.accept(pair.getSecond());
					try {
						progress.incrementProgress();
					} catch (CanceledException ignored) {
					}
				}
				var ordering = pqTree.extractAnOrdering();
				var array1based = new int[ordering.size() + 1];
				var index = 0;
				for (var value : ordering) {
					array1based[++index] = value;
				}
				return array1based;
			}
		} else {
			if (ntax <= 3) {
				var order = new int[ntax + 1];
				for (var t = 1; t <= ntax; t++) {
					order[t] = t;
				}
				return order;
			} else {
				try {
					final var pso = Basic.hideSystemOut();
					final var pse = Basic.hideSystemErr();
					try {
						return NeighborNetCycleSplitsTree4.compute(ntax, splitsToDistances(ntax, splits, true).getDistances());
					} finally {
						Basic.restoreSystemErr(pse);
						Basic.restoreSystemOut(pso);
					}
				} catch (Exception ex) {
					Basic.caught(ex);
					return new int[0];
				}
			}
		}
	}

	/**
	 * Given splits, returns the matrix split distances, as the number of splits separating each pair of taxa
	 *
	 * @param ntax   number of taxa
	 * @param splits with 1-based taxa
	 * @return distance matrix, 0-based
	 */
	public static DistancesBlock splitsToDistances(int ntax, List<ASplit> splits, boolean useWeights) {
		var distancesBlock = new DistancesBlock();
		distancesBlock.setNtax(ntax);
		splitsToDistances(splits, useWeights, distancesBlock);
		return distancesBlock;
	}
}
