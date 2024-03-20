/*
 *  UnrootedShapleyValues.java Copyright (C) 2024 Daniel H. Huson
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

package splitstree6.algorithms.trees.trees2report;

import jloda.util.BitSetUtils;
import jloda.util.CanceledException;
import jloda.util.progress.ProgressListener;
import splitstree6.algorithms.splits.splits2report.ShapleyValues;
import splitstree6.data.TaxaBlock;
import splitstree6.data.TreesBlock;
import splitstree6.data.parts.Taxon;
import splitstree6.splits.ASplit;
import splitstree6.splits.SplitUtils;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collection;

/**
 * compute fair proportion values on trees
 * Daniel Huson, 2.2023
 */
public class UnrootedShapleyValues extends Trees2ReportBase {
	@Override
	String runAnalysis(ProgressListener progress, TaxaBlock taxaBlock, TreesBlock treesBlock, Collection<Taxon> selectedTaxa) throws CanceledException {
		var taxa = BitSetUtils.asBitSet(selectedTaxa.stream().mapToInt(taxaBlock::indexOf).toArray());
		return report(progress, taxaBlock, treesBlock, taxa);
	}

	@Override
	public String getCitation() {
		return "Haake et al 2008;CJ Haake, A. Kashiwada and FE Su. The Shapley value of phylogenetic trees. J Math Biol 56:479–497, 2008.";
	}

	@Override
	public String getShortDescription() {
		return "Calculates unrooted Shapley values on trees.";
	}

	public static String report(ProgressListener progress, TaxaBlock taxaBlock, TreesBlock treesBlock, BitSet selectedTaxa) throws CanceledException {
		var buf = new StringBuilder();
		progress.setTasks("Computing", "unrooted Shapley values");
		progress.setMaximum(treesBlock.getNTrees());
		progress.setProgress(0);

		for (var tree : treesBlock.getTrees()) {
			buf.append("%nTree %s:%n".formatted(tree.getName()));
			var splits = new ArrayList<ASplit>();
			SplitUtils.computeSplits(null, tree, splits);
			buf.append(ShapleyValues.report(taxaBlock, splits));
			progress.incrementProgress();
		}
		progress.reportTaskCompleted();
		return buf.toString();
	}
}
