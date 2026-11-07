/*
 *  BunemanTree.java Copyright (C) 2026 Daniel H. Huson
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

package splitstree6.algorithms.distances.distances2splits;

import jloda.util.BitSetUtils;
import jloda.util.progress.ProgressListener;
import splitstree6.algorithms.splits.IToCompatibleSplits;
import splitstree6.algorithms.utils.SplitsBlockUtilities;
import splitstree6.data.DistancesBlock;
import splitstree6.data.SplitsBlock;
import splitstree6.data.TaxaBlock;
import splitstree6.splits.ASplit;
import splitstree6.splits.Compatibility;

import java.io.IOException;
import java.util.ArrayList;
import java.util.BitSet;

/**
 * computes the Buneman tree (Buneman 1971, Bandelt and Dress 1992) of a distance matrix.
 * <p>
 * The Buneman index of a split A|B is the minimum, over all choices of a, a' in A and b, b' in B, of
 * 1/2 (min{d(a,b) + d(a',b'), d(a,b') + d(a',b)} - d(a,a') - d(b,b')), where a = a' and b = b' are allowed.
 * The splits with positive index are always pairwise compatible and so form a tree; each is weighted by its index.
 * <p>
 * Daniel Huson, 11.2026
 */
public class BunemanTree extends Distances2Splits implements IToCompatibleSplits {
	/**
	 * indices that are not larger than this are treated as zero, to absorb rounding error
	 */
	public static final double TOLERANCE = 1e-8;

	@Override
	public String getCitation() {
		return "Bandelt & Dress 1992; HJ Bandelt and AWM Dress. A canonical decomposition theory for metrics on a finite set. " +
			   "Advances in Mathematics, 92:47–105, 1992.";
	}

	@Override
	public String getShortDescription() {
		return "Computes a set of compatible splits using the Buneman tree method.";
	}

	@Override
	public void compute(ProgressListener progress, TaxaBlock taxaBlock, DistancesBlock distancesBlock, SplitsBlock splitsBlock) throws IOException {
		if (SplitsBlockUtilities.computeSplitsForLessThan4Taxa(taxaBlock, distancesBlock, splitsBlock))
			return;

		var ntax = taxaBlock.getNtax();
		progress.setMaximum(ntax);
		progress.setProgress(0);

		// Taxa are added one at a time. Restricting a split to fewer taxa removes quartets from the minimum and so
		// can only increase its index. Hence every split of the taxa 1..t with positive index either is {t} | {1..t-1},
		// or restricts to a split of the taxa 1..t-1 with positive index. So the splits for 1..t are obtained by
		// placing t on either side of each split for 1..t-1, keeping those whose index stays positive, together
		// with the split that separates t from all the others
		var splits = new ArrayList<ASplit>(); // the splits of taxa 1..t that have a positive index, weighted by index
		for (var t = 2; t <= ntax; t++) {
			var extended = new ArrayList<ASplit>();
			{
				var singleton = BitSetUtils.asBitSet(t);
				var others = new BitSet();
				others.set(1, t);
				var index = minIndexOverQuartetsContaining(t, singleton, others, distancesBlock);
				if (index > TOLERANCE)
					extended.add(new ASplit(singleton, t, index));
			}
			for (var split : splits) {
				var a = split.getA();
				var b = split.getB();
				// the quartets that do not involve t are already accounted for by the index of the split on 1..t-1
				{
					var aWithT = (BitSet) a.clone();
					aWithT.set(t);
					var index = Math.min(split.getWeight(), minIndexOverQuartetsContaining(t, aWithT, b, distancesBlock));
					if (index > TOLERANCE)
						extended.add(new ASplit(aWithT, t, index));
				}
				{
					var bWithT = (BitSet) b.clone();
					bWithT.set(t);
					var index = Math.min(split.getWeight(), minIndexOverQuartetsContaining(t, bWithT, a, distancesBlock));
					if (index > TOLERANCE)
						extended.add(new ASplit((BitSet) a.clone(), t, index));
				}
			}
			splits = extended;
			progress.setProgress(t);
		}

		splits.addAll(SplitsBlockUtilities.createAllMissingTrivial(splits, ntax, 0.0));
		splitsBlock.getSplits().addAll(splits);

		splitsBlock.setFit(SplitsBlockUtilities.computeSplitDecompositionFit(distancesBlock, splitsBlock.getSplits()));
		splitsBlock.setCycle(SplitsBlockUtilities.computeCycle(ntax, splitsBlock.getSplits()));
		splitsBlock.setCompatibility(Compatibility.compute(ntax, splitsBlock.getSplits(), splitsBlock.getCycle()));
		progress.setProgress(ntax);
	}

	/**
	 * computes the minimum Buneman index over all quartets t,a | b,b' with a in A (a = t is allowed) and
	 * b, b' in B (b = b' is allowed)
	 *
	 * @param t         taxon, must be contained in A
	 * @param A         the side containing t
	 * @param B         the other side
	 * @param distances distances
	 * @return the minimum index, returned early once it is known not to be positive
	 */
	public static double minIndexOverQuartetsContaining(int t, BitSet A, BitSet B, DistancesBlock distances) {
		var min = Double.POSITIVE_INFINITY;
		for (var a : BitSetUtils.members(A)) {
			for (var b : BitSetUtils.members(B)) {
				for (var b2 : BitSetUtils.members(B, b)) {
					min = Math.min(min, quartetIndex(t, a, b, b2, distances));
					if (min <= TOLERANCE)
						return min;
				}
			}
		}
		return min;
	}

	/**
	 * computes the Buneman index of the quartet a,a' | b,b', that is, half the amount by which the smaller of the
	 * two crossing sums d(a,b) + d(a',b') and d(a,b') + d(a',b) exceeds d(a,a') + d(b,b')
	 */
	public static double quartetIndex(int a, int a2, int b, int b2, DistancesBlock d) {
		var crossing = Math.min(d.get(a, b) + d.get(a2, b2), d.get(a, b2) + d.get(a2, b));
		return 0.5 * (crossing - d.get(a, a2) - d.get(b, b2));
	}

	@Override
	public boolean isApplicable(TaxaBlock taxaBlock, DistancesBlock parent) {
		return parent.getNtax() > 0;
	}
}
