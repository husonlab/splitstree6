/*
 * RootingUtils.java Copyright (C) 2022 Daniel H. Huson
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

package splitstree6.view.splits.layout;

import jloda.util.BitSetUtils;
import jloda.util.Pair;
import jloda.util.Triplet;
import splitstree6.algorithms.utils.SplitsUtilities;
import splitstree6.data.SplitsBlock;
import splitstree6.data.TaxaBlock;
import splitstree6.data.parts.ASplit;
import splitstree6.data.parts.Taxon;

import java.io.IOException;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.Set;

/**
 * rooting utilities
 * Daniel Huson, 12.2021
 */
public class RootingUtils {
	/**
	 * computes the midpoint root location
	 *
	 * @param alt         use alternative side
	 * @param nTax        number of taxa
	 * @param outGroup    out group taxa or empty, if performing simple midpoint rooting
	 * @param useWeights  use split weights or otherwise give all splits weight 1
	 * @return rooting split and both distances
	 */
	public static Triplet<Integer, Double, Double> computeRootLocation(boolean alt, int nTax, Set<Integer> outGroup,
																	   int[] cycle, SplitsBlock splitsBlock, boolean useWeights) {
		if (outGroup.size() > 0) {
			final var outGroupBits = BitSetUtils.asBitSet(outGroup);
			var split = SplitsUtilities.getTighestSplit(splitsBlock, outGroupBits);
			if (split > 0) {
				double factor1;
				double factor2;
				switch (SplitsUtilities.compareMaxDistanceInSplitParts(nTax, splitsBlock, split, useWeights)) {
					case 1 -> {
						factor1 = 0.9;
						factor2 = 0.1;
					}
					case -1 -> {
						factor1 = 0.1;
						factor2 = 0.9;
					}
					default -> {
						factor1 = 0.5;
						factor2 = 0.5;
					}
				}
				return new Triplet<>(split, factor1 * splitsBlock.get(split).getWeight(), factor2 * splitsBlock.get(split).getWeight());
			}
		} else {
			final var splitDistances = new double[nTax + 1][nTax + 1];
			for (var split : splitsBlock.getSplits()) {
				for (var a : BitSetUtils.members(split.getA())) {
					for (var b : BitSetUtils.members(split.getB())) {
						var diff = useWeights ? split.getWeight() : 1;
						splitDistances[a][b] += diff;
						splitDistances[b][a] += diff;
					}
				}
			}
			var maxDistance = 0.0;
			final var furthestPair = new Pair<>(0, 0);

			for (var a = 1; a <= nTax; a++) {
				for (var b = a + 1; b <= nTax; b++) {
					if (splitDistances[a][b] > maxDistance) {
						maxDistance = splitDistances[a][b];
						furthestPair.set(a, b);
					}
				}
			}

			final var split2id = new HashMap<ASplit, Integer>();

			final var splits = new ArrayList<ASplit>();
			for (var s = 1; s <= splitsBlock.getNsplits(); s++) {
				final var split = splitsBlock.get(s);
				if (split.separates(furthestPair.getFirst(), furthestPair.getSecond())) {
					splits.add(split);
					split2id.put(split, s);
				}
			}

			final var interval = computeInterval(furthestPair.getFirst(), furthestPair.getSecond(), cycle, alt);

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

			var total = 0.0;
			for (var split : splits) {
				final var weight = (useWeights ? split.getWeight() : 1);
				final var delta = total + weight - 0.5 * maxDistance;
				if (delta > 0) {
					return new Triplet<>(split2id.get(split), delta, weight - delta);
				}
				total += weight;
			}
		}
		return new Triplet<>(1, 0.0, useWeights ? splitsBlock.get(1).getWeight() : 1);
	}

	private static BitSet computeInterval(int a, int b, int[] cycle, boolean alt) {
		final var set = new BitSet();

		if (cycle.length > 0) {
			if (alt) {
				var in = false;
				var i = cycle.length - 1;
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
				var in = false;
				var i = 1;
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

	/**
	 * setup target taxa block and splits block for computing rooted network
	 *
	 * @return root split
	 */
	public static int setupForRootedNetwork(boolean altLayout, Triplet<Integer, Double, Double> triplet, TaxaBlock taxaBlockSrc, SplitsBlock splitsBlockSrc, TaxaBlock taxaBlockTarget, SplitsBlock splitsBlockTarget) throws IOException {
		//final Triplet<Integer,Double,Double> triplet= SplitsUtilities.getMidpointSplit(taxaBlockSrc.getNtax(), splitsBlockSrc);
		final var mid = triplet.getFirst();
		final var weightWith1 = triplet.getSecond();
		final var weightOpposite1 = triplet.getThird();

		// modify taxa:
		taxaBlockTarget.clear();
		taxaBlockTarget.setNtax(taxaBlockSrc.getNtax() + 1);
		for (var taxon : taxaBlockSrc.getTaxa())
			taxaBlockTarget.add(taxon);
		final var rootTaxon = new Taxon("Root");
		taxaBlockTarget.add(rootTaxon);
		final int rootTaxonId = taxaBlockTarget.indexOf(rootTaxon);

		// modify cycle:
		final var cycle0 = splitsBlockSrc.getCycle();
		final var cycle = new int[cycle0.length + 1];
		var first = 0; // first taxon on other side of mid split
		if (!altLayout) {
			final var part = splitsBlockSrc.get(mid).getPartNotContaining(1);
			var t = 1;
			for (var value : cycle0) {
				if (value > 0) {
					if (first == 0 && part.get(value)) {
						first = value;
						cycle[t++] = rootTaxonId;
					}
					cycle[t++] = value;
				}
			}
		} else { // altLayout
			final var part = splitsBlockSrc.get(mid).getPartNotContaining(1);
			var seen = 0;
			var t = 1;
			for (var value : cycle0) {
				if (value > 0) {
					cycle[t++] = value;
					if (part.get(value)) {
						seen++;
						if (seen == part.cardinality()) {
							first = value;
							cycle[t++] = rootTaxonId;
						}
					}
				}
			}
		}
		SplitsUtilities.rotateCycle(cycle, rootTaxonId);

		// setup splits:
		splitsBlockTarget.clear();
		var totalWeight = 0.0;

		final var mid1 = splitsBlockSrc.get(mid).clone();
		mid1.getPartContaining(1).set(rootTaxonId);
		mid1.setWeight(weightWith1);
		final var mid2 = splitsBlockSrc.get(mid).clone();
		mid2.getPartNotContaining(1).set(rootTaxonId);
		mid2.setWeight(weightOpposite1);

		for (var s = 1; s <= splitsBlockSrc.getNsplits(); s++) {
			if (s == mid) {
				totalWeight += mid1.getWeight();
				splitsBlockTarget.getSplits().add(mid1);
				//splitsBlockTarget.getSplitLabels().put(mid,"BOLD");
			} else {
				final var aSplit = splitsBlockSrc.get(s).clone();

				if (BitSetUtils.contains(mid1.getPartNotContaining(rootTaxonId), aSplit.getA())) {
					aSplit.getB().set(rootTaxonId);
				} else if (BitSetUtils.contains(mid1.getPartNotContaining(rootTaxonId), aSplit.getB())) {
					aSplit.getA().set(rootTaxonId);
				} else if (aSplit.getPartContaining(first).cardinality() > 1)
					aSplit.getPartContaining(first).set(rootTaxonId);
				else
					aSplit.getPartNotContaining(first).set(rootTaxonId);

				splitsBlockTarget.getSplits().add(aSplit);
				totalWeight += aSplit.getWeight();
			}
		}
		// add  new separator split
		{
			totalWeight += mid2.getWeight();
			splitsBlockTarget.getSplits().add(mid2);
			//splitsBlockTarget.getSplitLabels().put(splitsBlockTarget.getNsplits(),"BOLD");
		}
		// add root split:
		{
			final var split = new ASplit(BitSetUtils.asBitSet(rootTaxonId), taxaBlockTarget.getNtax(), totalWeight > 0 ? 0.1 * totalWeight / splitsBlockTarget.getNsplits() : 0.1);
			splitsBlockTarget.getSplits().add(split);

		}
		splitsBlockTarget.setCycle(cycle, false);
		return splitsBlockTarget.getNsplits(); // last split is root split
	}
}
