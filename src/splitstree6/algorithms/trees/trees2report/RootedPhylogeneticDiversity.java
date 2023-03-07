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

import jloda.util.BitSetUtils;
import jloda.util.CanceledException;
import jloda.util.progress.ProgressListener;
import splitstree6.algorithms.utils.TreesUtilities;
import splitstree6.data.TaxaBlock;
import splitstree6.data.TreesBlock;
import splitstree6.data.parts.Taxon;

import java.util.BitSet;
import java.util.Collection;

/**
 * compute phylogenetic diversity
 * Daniel Huson, 2.2023
 */
public class RootedPhylogeneticDiversity extends Trees2ReportBase {
	@Override
	String runAnalysis(ProgressListener progress, TaxaBlock taxaBlock, TreesBlock treesBlock, Collection<Taxon> selectedTaxa) throws CanceledException {
		var taxa = BitSetUtils.asBitSet(selectedTaxa.stream().mapToInt(taxaBlock::indexOf).toArray());
		return report(progress, taxaBlock, treesBlock, taxa);
	}

	@Override
	public String getCitation() {
		return "Faith 1992;Faith, D.P. Conservation evaluation and phylogenetic diversity. Biological Conservation 61, 1–10 (1992)";
	}

	public static String report(ProgressListener progress, TaxaBlock taxaBlock, TreesBlock treesBlock, BitSet selectedTaxa) throws CanceledException {
		var buf = new StringBuilder();
		progress.setTasks("Computing", "fair proporitions");
		progress.setMaximum(treesBlock.getNTrees());
		progress.setProgress(0);

		for (var tree : treesBlock.getTrees()) {
			buf.append("%nTree %s:%n".formatted(tree.getName()));
			var total = tree.edgeStream().mapToDouble(tree::getWeight).sum();
			double diversity;
			try (var nodeClusterMap = TreesUtilities.extractClusters(tree)) {
				diversity = tree.edgeStream().filter(e -> nodeClusterMap.get(e.getTarget()).intersects(selectedTaxa)).mapToDouble(tree::getWeight).sum();
			}
			buf.append("Phylogenetic Diversity = %.8f (%.1f%%)%n".formatted(diversity, 100.0 * (diversity / total)));
			progress.incrementProgress();
		}
		buf.append("Computed on %d (of %d) selected taxa:%n".formatted(selectedTaxa.cardinality(), taxaBlock.getNtax()));
		if (selectedTaxa.cardinality() > 0) {
			for (var t = 1; t <= taxaBlock.getNtax(); t++) {
				if (selectedTaxa.get(t)) {
					buf.append(taxaBlock.get(t).getName()).append("\n");
				}
			}
		}
		progress.reportTaskCompleted();
		return buf.toString();
	}
}
