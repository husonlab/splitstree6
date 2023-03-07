/*
 * ShowSplits.java Copyright (C) 2023 Daniel H. Huson
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

package splitstree6.algorithms.trees.trees2report;

import jloda.phylo.PhyloTree;
import jloda.util.BitSetUtils;
import jloda.util.CanceledException;
import jloda.util.SetUtils;
import jloda.util.progress.ProgressListener;
import splitstree6.algorithms.utils.TreesUtilities;
import splitstree6.data.TaxaBlock;
import splitstree6.data.TreesBlock;
import splitstree6.data.parts.Taxon;

import java.util.*;

/**
 * determines which trees are 1 rSPR away from each other
 * Daniel Huson, 2.2023
 */
public class ListOneRSPRTrees extends Trees2ReportBase {
	public ListOneRSPRTrees() {
		setOptionApplyTo(ApplyTo.AllTrees);
	}

	@Override
	String runAnalysis(ProgressListener progress, TaxaBlock taxaBlock, TreesBlock treesBlock, Collection<Taxon> selectedTaxa) throws CanceledException {
		var taxa = BitSetUtils.asBitSet(selectedTaxa.stream().mapToInt(taxaBlock::indexOf).toArray());
		return report(progress, taxaBlock, treesBlock, taxa);
	}

	public static String report(ProgressListener progress, TaxaBlock taxaBlock, TreesBlock treesBlock, BitSet selectedTaxa) throws CanceledException {
		progress.setTasks("Computing", "one rSPR pairs");
		progress.setMaximum(treesBlock.getNTrees());
		progress.setProgress(0);

		var buf = new StringBuilder();
		var count = 0;
		for (var s = 1; s <= treesBlock.getNTrees(); s++) {
			var tree1 = treesBlock.getTree(s);
			for (var t = s + 1; t <= treesBlock.getNTrees(); t++) {
				var tree2 = treesBlock.getTree(t);
				if (tree1.getNumberOfTaxa() == tree2.getNumberOfTaxa() && haveRootedSprDistanceOne(tree1, tree2)) {
					buf.append("%s and %s%n".formatted(tree1.getName(), tree2.getName()));
					count++;
				}
			}
			progress.incrementProgress();
		}
		progress.reportTaskCompleted();
		return "Pairs of trees that differ by one rSPR: %d%n".formatted(count) + buf;
	}

	public static boolean haveRootedSprDistanceOne(PhyloTree tree1, PhyloTree tree2) {
		var clusters1 = TreesUtilities.extractClusters(tree1).values();
		var clusters2 = new HashSet<>(TreesUtilities.extractClusters(tree2).values());

		if (allCompatible(clusters1, clusters2))
			return false; // rSPR distance is 0

		var clusters1sorted = new ArrayList<>(clusters1);
		clusters1sorted.sort(Comparator.comparingInt(BitSet::cardinality));

		for (var cluster : SetUtils.intersection(clusters1sorted, clusters2)) {
			var inC1 = clusters1.stream().filter(c -> BitSetUtils.contains(cluster, c)).toList();
			var inC2 = clusters2.stream().filter(c -> BitSetUtils.contains(cluster, c)).toList();
			if (!allCompatible(inC1, inC2))
				return false; // found two incompatible subtrees that we can fix, because going through clusters in order of increasing size
			var withoutC1 = clusters1.stream().map(c -> BitSetUtils.minus(c, cluster)).filter(c -> c.cardinality() > 0).toList();
			var withoutC2 = clusters2.stream().map(c -> BitSetUtils.minus(c, cluster)).filter(c -> c.cardinality() > 0).toList();
			if (allCompatible(withoutC1, withoutC2))
				return true;
		}
		return false;
	}

	private static boolean allCompatible(Collection<BitSet> clusters1, Collection<BitSet> clusters2) {
		for (var c1 : clusters1) {
			for (var c2 : clusters2) {
				var intersectionSize = BitSetUtils.intersection(c1, c2).cardinality();
				if (intersectionSize != 0 && intersectionSize != c1.cardinality() && intersectionSize != c2.cardinality())
					return false;
			}
		}
		return true;
	}
}