/*
 * SplitDecomposition.java Copyright (C) 2021. Daniel H. Huson
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

package splitstree6.algorithms.distances.distances2splits;

import jloda.util.progress.ProgressListener;
import splitstree6.data.DistancesBlock;
import splitstree6.data.SplitsBlock;
import splitstree6.data.TaxaBlock;
import splitstree6.data.parts.ASplit;
import splitstree6.data.parts.Compatibility;
import splitstree6.utils.SplitsUtilities;

import java.io.IOException;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

/**
 * Split decomposition
 * Created on 12/30/16.
 *
 * @author Daniel Huson and Daria Evseeva
 */
public class SplitDecomposition extends Distances2Splits {

	@Override
	public String getCitation() {
		return "Bandelt and Dress 1992; H.-J.Bandelt and A.W.M.Dress. A canonical decomposition theory for metrics on a finite set. Advances in Mathematics, 92:47–105, 1992.";
	}

	@Override
	public void compute(ProgressListener progress, TaxaBlock taxaBlock, DistancesBlock distancesBlock, SplitsBlock splitsBlock) throws IOException {
		if (SplitsUtilities.computeSplitsForLessThan4Taxa(taxaBlock, distancesBlock, splitsBlock))
			return;

		ArrayList<ASplit> previousSplits = new ArrayList<>(); // list of previously computed splits
		ArrayList<ASplit> nextSplits; // current list of splits

		// ProgressDialog pd = new ProgressDialog("Split Decomposition...",""); //Set new progress bar.
		// doc.setProgressListener(pd);
		progress.setMaximum(taxaBlock.getNtax());    //initialize maximum progress
		progress.setProgress(0);

		final BitSet previousTaxa = new BitSet(); // taxa already processed
		final int ntax = taxaBlock.getNtax();

		previousTaxa.set(1);

		for (int t = 2; t <= ntax; t++) {
			nextSplits = new ArrayList<>(t); // restart current list of splits

			// Does t vs previous set of taxa form a split?
			{
				final BitSet At = new BitSet();
				At.set(t);
				final float wgt = getIsolationIndex(t, At, previousTaxa, distancesBlock);
				if (wgt > 0) {
					nextSplits.add(new ASplit((BitSet) At.clone(), t, wgt));
				}
			}

			// consider all previously computed splits:
			for (final ASplit previousSplit : previousSplits) {
				final BitSet A = previousSplit.getA();
				final BitSet B = getComplement(previousSplit.getA(), t - 1);

				// is Au{t} vs B a split?
				{
					A.set(t);
					final double wgt = Math.min(previousSplit.getWeight(), getIsolationIndex(t, A, B, distancesBlock));
					if (wgt > 0) {
						nextSplits.add(new ASplit((BitSet) A.clone(), t, wgt));

					}
					A.set(t, false);
				}

				// is A vs Bu{t} a split?
				{
					B.set(t);
					final double wgt = Math.min(previousSplit.getWeight(), getIsolationIndex(t, B, A, distancesBlock));
					if (wgt > 0) {
						nextSplits.add(new ASplit((BitSet) B.clone(), t, wgt));
					}
				}
			}
			previousSplits = nextSplits;

			previousTaxa.set(t);

			progress.setProgress(t);
		}


		// copy splits to splits
		splitsBlock.setFit(computeFit(distancesBlock, previousSplits));
		splitsBlock.setCompatibility(Compatibility.compute(taxaBlock.getNtax(), splitsBlock.getSplits()));
		splitsBlock.getSplits().addAll(previousSplits);
		splitsBlock.setCycle(SplitsUtilities.computeCycle(taxaBlock.getNtax(), previousSplits));

		progress.setProgress(ntax);   //set progress to 100%
		progress.close();
	}

	/**
	 * Returns the isolation index for Au{x} vs B
	 *
	 * @param t maximal taxon index, assumed to be contained in set A
	 * @param A set A
	 * @param B set B
	 * @param d Distance matrix
	 * @return the isolation index
	 */
	public static float getIsolationIndex(int t, BitSet A, BitSet B, DistancesBlock d) {
		float min_val = Float.MAX_VALUE;

		for (int i = 1; i <= t; i++) {
			if (A.get(i)) {
				for (int j = 1; j <= t; j++) {
					if (B.get(j)) {
						for (int k = j; k <= t; k++) {
							if (B.get(k)) {
								float val = getIsolationIndex(t, i, j, k, d);
								if (val < min_val) {
									if (val <= 0.0000001)
										return 0;
									min_val = val;
								}
							}
						}
					}
				}
			}
		}
		return min_val;
	}

	/**
	 * Returns the isolation index of i,j vs k,l
	 *
	 * @param i a taxon
	 * @param j a taxon
	 * @param k a taxon
	 * @param m a taxon
	 * @param d Distance matrix
	 * @return the isolation index
	 */
	public static float getIsolationIndex(int i, int j, int k, int m, DistancesBlock d) {
		return (float) (0.5 * (Math.max(d.get(i, k) + d.get(j, m), d.get(i, m) + d.get(j, k)) - d.get(i, j) - d.get(k, m)));
	}

	/**
	 * computes the fit
	 *
	 * @param distancesBlock
	 * @param splits
	 * @return fit, ls fit and stress
	 */
	public static float computeFit(DistancesBlock distancesBlock, List<ASplit> splits) {
		final int ntax = distancesBlock.getNtax();

		double dsum = 0;
		for (int i = 1; i <= ntax; i++) {
			for (int j = i + 1; j <= ntax; j++) {
				dsum += distancesBlock.get(i, j);
			}
		}

		double ssum = 0;
		{
			for (int i = 1; i <= ntax; i++) {
				for (int j = i + 1; j <= ntax; j++) {
					double dij = 0.0;
					for (ASplit split : splits) {
						if (split.isContainedInA(i) != split.isContainedInA(j))
							dij += split.getWeight();
					}
					ssum += dij;
				}
			}
		}

		return (float) Math.max(100 * (1.0 - ssum / dsum), 0.0);
	}

	private static BitSet getComplement(BitSet A, int ntax) {
		BitSet result = new BitSet();
		for (int t = A.nextClearBit(1); t != -1 && t <= ntax; t = A.nextClearBit(t + 1))
			result.set(t);
		return result;
	}

	@Override
	public boolean isApplicable(TaxaBlock taxaBlock, DistancesBlock parent) {
		return parent.getNtax() > 0;
	}
}
