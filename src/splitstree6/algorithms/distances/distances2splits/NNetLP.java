/*
 * NNetLP.java Copyright (C) 2022 Daniel H. Huson
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

package splitstree6.algorithms.distances.distances2splits;

import jloda.util.progress.ProgressListener;
import splitstree6.algorithms.distances.distances2splits.neighbornet.NeighborNetCycle;
import splitstree6.algorithms.splits.IToCircularSplits;
import splitstree6.data.DistancesBlock;
import splitstree6.data.SplitsBlock;
import splitstree6.data.TaxaBlock;
import splitstree6.data.parts.ASplit;

import java.io.IOException;
import java.util.BitSet;

public class NNetLP extends Distances2Splits implements IToCircularSplits {

	/**
	 * run linear-programming-based neighbor-net algorithm
	 *
	 * @param progress          progress listener, can be ignored
	 * @param taxaBlock         the taxa, numbered 1-n
	 * @param distancesBlock    the input distance matrices, d(i,j) is distance for taxa i and j
	 * @param outputSplitsBlock here we return the list of splits that have been computed by the algorithm
	 * @throws IOException
	 */
	@Override
	public void compute(ProgressListener progress, TaxaBlock taxaBlock, DistancesBlock distancesBlock, SplitsBlock outputSplitsBlock) throws IOException {
		// step 1, compute cycle:
		var cycle = NeighborNetCycle.compute(taxaBlock.getNtax(), distancesBlock.getDistances());
		outputSplitsBlock.setCycle(cycle);

		// cycle[0]=0, cycle[1] is first taxon in ordering, cycle[2] is second taxon in ordering


		// step 2: compute the splits using LP, BSc

		// setup the LP...
		// run the LP...
		// parse the LP output and convert to splits

		// e.g. to print all distances:
		for (var s = 1; s <= taxaBlock.getNtax(); s++) {
			for (var t = 1; t <= taxaBlock.getNtax(); t++) {
				System.err.print(" " + distancesBlock.get(s, t));
			}
			System.err.println();
		}


		// finally add splits like this:
		var A = new BitSet(); // put taxa of side A in here
		var B = new BitSet(); // but taxa of side B in here
		var weight = 1.0; // set this to the weight of the computed split
		outputSplitsBlock.getSplits().add(new ASplit(A, B, weight));

	}
}
