/*
 *  ProgressiveSCS.java Copyright (C) 2024 Daniel H. Huson
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

package splitstree6.xtra.hyperstrings;

import jloda.graph.NodeArray;
import jloda.util.CanceledException;
import jloda.util.progress.ProgressSilent;
import splitstree6.algorithms.distances.distances2trees.UPGMA;
import splitstree6.data.DistancesBlock;
import splitstree6.data.TaxaBlock;

import java.util.ArrayList;

/**
 * pairwise shortest common hyper-sequence heuristic
 * Daniel Huson, 4.2024
 */
public class ProgressiveSCS {
	/**
	 * run the progressive SCS heuristic
	 *
	 * @param sequences input hyper sequences
	 * @return shortest common hyper sequence
	 */
	public static HyperSequence apply(ArrayList<HyperSequence> sequences) {
		if (sequences.size() == 1)
			return sequences.get(0);
		else if (sequences.size() == 2) {
			return ShortestCommonHyperSequence.align(sequences.get(0), sequences.get(1));
		} else if (sequences.size() == 3) {
			var one = ShortestCommonHyperSequence.align(sequences.get(0), sequences.get(1));
			return ShortestCommonHyperSequence.align(one, sequences.get(2));
		}
		// setup distances for UPGMA
		var taxa = new TaxaBlock();
		for (var t = 0; t < sequences.size(); t++) {
			taxa.addTaxonByName("s" + t);
		}
		var distancesBlock = new DistancesBlock();
		distancesBlock.setNtax(taxa.getNtax());

		for (var i = 1; i <= taxa.getNtax(); i++) {
			var si = sequences.get(i - 1);
			for (var j = i + 1; j <= taxa.getNtax(); j++) {
				var sj = sequences.get(j - 1);
				var aligned = ShortestCommonHyperSequence.align(si, sj);
				var minLength = Math.min(si.size(), sj.size());
				var d = (double) (aligned.size() - minLength) / (double) minLength;
				distancesBlock.set(i, j, d);
				distancesBlock.set(j, i, d);
			}
		}

		try {
			var tree = UPGMA.computeUPGMATree(new ProgressSilent(), taxa, distancesBlock);
			// progressive alignment up tree:
			try (NodeArray<HyperSequence> mhs = tree.newNodeArray()) {
				tree.postorderTraversal(u -> {
					if (u.isLeaf()) {
						var sequence = sequences.get(tree.getTaxon(u) - 1);
						mhs.put(u, sequence);
					} else {
						var v = u.getFirstOutEdge().getTarget();
						var w = u.getLastOutEdge().getTarget();
						mhs.put(u, ShortestCommonHyperSequence.align(mhs.get(v), mhs.get(w)));
					}
				});
				return mhs.get(tree.getRoot());
			}
		} catch (CanceledException ignored) {
			// can't happen
			return null;
		}
	}

	public static void main(String[] args) {
		var sequences = new ArrayList<HyperSequence>();

		sequences.add(HyperSequence.parse("1"));
		sequences.add(HyperSequence.parse("1 2"));
		sequences.add(HyperSequence.parse("2"));
		//sequences.add(HyperSequence.parse("1 : 2: 3 4 : 5 : 6 7"));
		//sequences.add(HyperSequence.parse("1 2 : 3 4 : 5 : 6:  7"));
		//sequences.add(HyperSequence.parse("1 : 2: 3 : 4 : 5 : 6 : 7"));

		var result = apply(sequences);
		System.err.println(result);
	}
}
