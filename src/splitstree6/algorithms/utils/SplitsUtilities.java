/*
 * SplitsUtilities.java Copyright (C) 2021. Daniel H. Huson
 *
 * (Some code written by other authors, as named in code.)
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
 *
 */

package splitstree6.algorithms.utils;

import jloda.util.*;
import jloda.util.progress.ProgressListener;
import jloda.util.progress.ProgressSilent;
import splitstree6.algorithms.distances.distances2splits.neighbornet.NeighborNetCycle;
import splitstree6.algorithms.distances.distances2trees.NeighborJoining;
import splitstree6.data.DistancesBlock;
import splitstree6.data.SplitsBlock;
import splitstree6.data.TaxaBlock;
import splitstree6.data.TreesBlock;
import splitstree6.data.parts.ASplit;
import splitstree6.data.parts.Compatibility;

import java.io.PrintStream;
import java.util.*;

/**
 * utilities for splits
 * Daniel Huson, 2005, 2016
 * Daria Evseeva,23.01.2017.
 */
public class SplitsUtilities {
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
	 * Computes a cycle for the given splits system
	 *
	 * @param ntax   number of taxa
	 * @param splits the splits
	 */
	static public int[] computeCycle(int ntax, List<ASplit> splits) {
		try {
			final PrintStream pso = Basic.hideSystemOut();
			final PrintStream pse = Basic.hideSystemErr();
			try {
				return NeighborNetCycle.computeNeighborNetCycle(ntax, splitsToDistances(ntax, splits));
			} finally {
				Basic.restoreSystemErr(pse);
				Basic.restoreSystemOut(pso);
			}
		} catch (Exception ex) {
			Basic.caught(ex);
			final int[] order = new int[ntax + 1];
			for (int t = 1; t <= ntax; t++) {
				order[t] = t;
			}
			return order;
		}
	}

	/**
	 * Given splits, returns the matrix split distances, as the number of splits separating each pair of taxa
	 *
	 * @param ntax   number of taxa
	 * @param splits with 1-based taxa
	 * @return distance matrix, 0-based
	 */
	public static double[][] splitsToDistances(int ntax, List<ASplit> splits) {
		return splitsToDistances(ntax, splits, null);
	}

	/**
	 * Given splits, returns the matrix split distances, as the number of splits separating each pair of taxa
	 *
	 * @param ntax   number of taxa
	 * @param splits with 1-based taxa
	 * @param dist   matrix, 0-based
	 * @return distance matrix, 0-based
	 */
	public static double[][] splitsToDistances(int ntax, List<ASplit> splits, double[][] dist) {
		if (dist == null)
			dist = new double[ntax][ntax];
		for (int i = 1; i <= ntax; i++) {
			for (int j = i + 1; j <= ntax; j++) {
				for (ASplit split : splits) {
					BitSet A = split.getA();

					if (A.get(i) != A.get(j)) {
						dist[i - 1][j - 1]++;
						dist[j - 1][i - 1]++;
					}
				}
			}
		}
		return dist;
	}


	/**
	 * normalize cycle so that it is lexicographically smallest
	 *
	 * @param cycle
	 * @return normalized cycle
	 */
	public static int[] normalizeCycle(int[] cycle) {
		int posOf1 = -1;
		for (int i = 1; i < cycle.length; i++) {
			if (cycle[i] == 1) {
				posOf1 = i;
				break;
			}
		}
		final int posPrev = (posOf1 == 1 ? cycle.length - 1 : posOf1 - 1);
		final int posNext = (posOf1 == cycle.length - 1 ? 1 : posOf1 + 1);
		if (cycle[posPrev] > cycle[posNext]) { // has correct orientation, ensure that taxon 1 is at first position
			if (posOf1 != 1) {
				int[] tmp = new int[cycle.length];
				int i = posOf1;
				for (int j = 1; j < tmp.length; j++) {
					tmp[j] = cycle[i];
					if (++i == cycle.length)
						i = 1;
				}
				return tmp;
			} else
				return cycle;
		} else // change orientation, as well
		{
			int[] tmp = new int[cycle.length];
			int i = posOf1;
			for (int j = 1; j < tmp.length; j++) {
				tmp[j] = cycle[i];
				if (--i == 0)
					i = cycle.length - 1;
			}
			return tmp;
		}
	}

	/**
	 * sort splits by decreasing weight
	 *
	 * @param splits
	 */
	public static ArrayList<ASplit> sortByDecreasingWeight(List<ASplit> splits) {
		final ASplit[] array = splits.toArray(new ASplit[splits.size()]);
		Arrays.sort(array, (a, b) -> {
			if (a.getWeight() > b.getWeight())
				return -1;
			else if (a.getWeight() < b.getWeight())
				return 1;
			return 0;
		});
		return new ArrayList<>(Arrays.asList(array)); // this construction ensures that the resulting list can grow
	}

	/**
	 * is split circular with respect to the given cycle?
	 *
	 * @param taxa
	 * @param cycle uses indices 1 to number-of-taxa
	 * @param split
	 * @return true if circular
	 */
	public static boolean isCircular(TaxaBlock taxa, int[] cycle, ASplit split) {
		final BitSet part = (!split.getA().get(cycle[1]) ? split.getA() : split.getB()); // choose part that doesn't go around the horn
		int prev = 0;
		for (int t = 1; t <= taxa.getNtax(); t++) {
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
	 *
	 * @param splits
	 * @param taxa
	 * @throws SplitsException
	 */
	public static void verifySplits(Collection<ASplit> splits, TaxaBlock taxa) throws SplitsException {
		final Set<BitSet> seen = new HashSet<>();

		for (ASplit split : splits) {
			final BitSet aSet = split.getA();
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
	 * Determines the fit of a splits system, ie how well it
	 * represents a given distance matrix, in percent. Computes two different values.
	 * //ToDo: Fix variances.
	 * // todo no lsfit?
	 *
	 * @param forceRecalculation always recompute the fit, even if there is a valid value stored.
	 * @param splits             the splits
	 * @param dist               the distances
	 */
	static public void computeFits(boolean forceRecalculation, SplitsBlock splits, DistancesBlock dist, ProgressListener pl) {
		if (splits == null || dist == null)
			return;

		final int ntax = dist.getNtax();


		if (!forceRecalculation && splits.getFit() >= 0)
			return; //No need to recalculate.

		splits.setFit(-1);
		//splits.getProperties().setLSFit(-1); //A fit of -1 means that we don't have a valid value.

		pl.setSubtask("Recomputing fit");

		double[][] sdist = new double[ntax + 1][ntax + 1];

		for (int i = 1; i <= ntax; i++) {
			sdist[i][i] = 0;
			for (int j = i + 1; j <= ntax; j++) {
				float dij = 0;
				for (int s = 1; s <= splits.getNsplits(); s++) {
					BitSet split = splits.getSplits().get(s - 1).getA();
					if (split.get(i) != split.get(j))
						dij += splits.getSplits().get(s - 1).getWeight();
				}
				sdist[i][j] = sdist[j][i] = dij;
			}
		}

		float dsum = 0;
		float ssum = 0;
		float dsumSquare = 0;
		float diffSumSquare = 0;
		float netsumSquare = 0;

		for (int i = 1; i <= ntax; i++) {
			for (int j = i + 1; j <= ntax; j++) {
				double sij = sdist[i][j];
				double dij = dist.get(i, j);
				ssum += Math.abs(sij - dij);
				diffSumSquare += (sij - dij) * (sij - dij);
				dsum += dij;
				dsumSquare += dij * dij;
				netsumSquare += sij * sij;
			}
		}
		final double fit = Math.max(0, 100.0 * (1.0 - ssum / dsum));
		splits.setFit((float) fit);

		final double lsFit = Math.max(0.0, 100.0 * (1.0 - diffSumSquare / dsumSquare));

		splits.setFit((float) lsFit);

		double stress = Math.sqrt(diffSumSquare / netsumSquare);

		System.err.println("\nRecomputed fit:\n\tfit = " + fit + "\n\tLS fit =" + lsFit + "\n\tstress =" + stress + "\n");
	}

	public static void rotateCycle(int[] cycle, int first) {
		final int[] tmp = new int[2 * cycle.length - 1];
		System.arraycopy(cycle, 0, tmp, 0, cycle.length);
		System.arraycopy(cycle, 1, tmp, cycle.length, cycle.length - 1);
		for (int i = 1; i < tmp.length; i++) {
			if (tmp[i] == first) {
				for (int j = 1; j < cycle.length; j++) {
					cycle[j] = tmp[i++];
				}
				return;
			}
		}
	}

	public static int getTighestSplit(BitSet taxa, SplitsBlock splitsBlock) {
		int best = 0;
		int bestSideCardinality = Integer.MAX_VALUE;
		for (int s = 1; s <= splitsBlock.getNsplits(); s++) {
			final ASplit split = splitsBlock.get(s);
			if (BitSetUtils.contains(split.getA(), taxa) && split.getA().cardinality() < bestSideCardinality) {
				best = s;
				bestSideCardinality = split.getA().cardinality();
			}
			if (BitSetUtils.contains(split.getB(), taxa) && (split.getB().cardinality() < bestSideCardinality)) {
				best = s;
				bestSideCardinality = split.getB().cardinality();
			}
		}
		return best;
	}

	/**
	 * computes the midpoint root location
	 *
	 * @param alt         use alternative side
	 * @param nTax        number of taxa
	 * @param outGroup    out group taxa or empty, if performing simple midpoint rooting
	 * @param cycle
	 * @param splitsBlock
	 * @param useWeights  use split weights or otherwise give all splits weight 1
	 * @param progress
	 * @return rooting split and both distances
	 * @throws CanceledException
	 */
	public static Triplet<Integer, Double, Double> computeRootLocation(boolean alt, int nTax, Set<Integer> outGroup,
																	   int[] cycle, SplitsBlock splitsBlock, boolean useWeights, ProgressListener progress) {
		progress.setSubtask("Computing root location");

		if (outGroup.size() > 0) {
			final BitSet outGroupSplits = new BitSet();
			final BitSet outGroupBits = BitSetUtils.asBitSet(outGroup);
			final int outGroupTaxon = outGroup.iterator().next();


			for (int p = 1; p <= splitsBlock.getNsplits(); p++) {
				final BitSet pa = splitsBlock.get(p).getPartContaining(outGroupTaxon);
				if (BitSetUtils.contains(pa, outGroupBits)) {
					boolean ok = true;
					for (int q : BitSetUtils.members(outGroupSplits)) {
						final BitSet qa = splitsBlock.get(q).getPartContaining(outGroupTaxon);
						if (BitSetUtils.contains(pa, qa)) {
							ok = false;
							break;
						} else if (BitSetUtils.contains(qa, pa)) {
							outGroupSplits.clear(q);
						}
					}
					if (ok)
						outGroupSplits.set(p);
				}
			}
			if (outGroupSplits.cardinality() > 0) {
				final int s = outGroupSplits.nextSetBit(0);
				return new Triplet<>(s, 0.9 * splitsBlock.get(s).getWeight(), 0.1 * splitsBlock.get(s).getWeight());
			}
		} else {
			final double[][] splitDistances = new double[nTax + 1][nTax + 1];
			for (ASplit split : splitsBlock.getSplits()) {
				for (int a : BitSetUtils.members(split.getA())) {
					for (int b : BitSetUtils.members(split.getB())) {
						double diff = useWeights ? split.getWeight() : 1;
						splitDistances[a][b] += diff;
						splitDistances[b][a] += diff;
					}
				}
			}
			double maxDistance = 0;
			final Pair<Integer, Integer> furthestPair = new Pair<>(0, 0);

			for (int a = 1; a <= nTax; a++) {
				for (int b = a + 1; b <= nTax; b++) {
					if (splitDistances[a][b] > maxDistance) {
						maxDistance = splitDistances[a][b];
						furthestPair.set(a, b);
					}
				}
			}

			final Map<ASplit, Integer> split2id = new HashMap<>();

			final ArrayList<ASplit> splits = new ArrayList<>();
			for (int s = 1; s <= splitsBlock.getNsplits(); s++) {
				final ASplit split = splitsBlock.get(s);
				if (split.separates(furthestPair.getFirst(), furthestPair.getSecond())) {
					splits.add(split);
					split2id.put(split, s);
				}
			}

			final BitSet interval = computeInterval(furthestPair.getFirst(), furthestPair.getSecond(), cycle, alt);

			splits.sort((s1, s2) -> {
				final BitSet a1 = s1.getPartContaining(furthestPair.getFirst());
				final BitSet a2 = s2.getPartContaining(furthestPair.getFirst());
				final int size1 = BitSetUtils.intersection(a1, interval).cardinality();
				final int size2 = BitSetUtils.intersection(a2, interval).cardinality();

				if (size1 < size2)
					return -1;
				else if (size1 > size2)
					return 1;
				else
					return Integer.compare(a1.cardinality(), a2.cardinality());
			});

			double total = 0;
			for (ASplit split : splits) {
				final double weight = (useWeights ? split.getWeight() : 1);
				final double delta = total + weight - 0.5 * maxDistance;
				if (delta > 0) {
					return new Triplet<>(split2id.get(split), delta, weight - delta);
				}
				total += weight;
			}
		}
		return new Triplet<>(1, 0.0, useWeights ? splitsBlock.get(1).getWeight() : 1);
	}

	private static BitSet computeInterval(int a, int b, int[] cycle, boolean alt) {
		final BitSet set = new BitSet();

		if (cycle.length > 0) {
			if (alt) {
				boolean in = false;
				int i = cycle.length - 1;
				while (true) {
					if (cycle[i] == a) {
						set.set(a);
						in = true;
					}
					if (in && cycle[i] == b) {
						break;
					}
					if (i == 1)
						i = cycle.length - 1;
					else
						i--;
				}
			} else {
				boolean in = false;
				int i = 1;
				while (true) {
					if (cycle[i] == a) {
						set.set(a);
						in = true;
					}
					if (in && cycle[i] == b) {
						break;
					}
					if (i >= cycle.length - 1)
						i = 1;
					else
						i++;
				}
			}
		}
		return set;
	}

	public static boolean computeSplitsForLessThan4Taxa(TaxaBlock taxaBlock, DistancesBlock distancesBlock, SplitsBlock splitsBlock) throws CanceledException {
		if (taxaBlock.getNtax() < 4) {
			final TreesBlock treesBlock = new TreesBlock();
			new NeighborJoining().compute(new ProgressSilent(), taxaBlock, distancesBlock, treesBlock);
			splitsBlock.clear();
			TreesUtilities.computeSplits(taxaBlock.getTaxaSet(), treesBlock.getTree(1), splitsBlock.getSplits());
			splitsBlock.setCompatibility(Compatibility.compatible);
			splitsBlock.setCycle(SplitsUtilities.computeCycle(taxaBlock.getNtax(), splitsBlock.getSplits()));
			splitsBlock.setFit(100);
			return true;
		}
		return false;
	}
}
