/*
 * ShowSplits.java Copyright (C) 2024 Daniel H. Huson
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

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import jloda.util.BitSetUtils;
import jloda.util.CanceledException;
import jloda.util.NumberUtils;
import jloda.util.StringUtils;
import jloda.util.progress.ProgressListener;
import splitstree6.data.TaxaBlock;
import splitstree6.data.TreesBlock;
import splitstree6.data.parts.Taxon;
import splitstree6.splits.TreesUtils;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collection;
import java.util.List;

/**
 * compute phylogenetic diversity
 * Daniel Huson, 2.2023
 */
public class PhylogeneticDiversity extends Trees2ReportBase {
	private final BooleanProperty optionRooted = new SimpleBooleanProperty(this, "optionRooted", true);

	@Override
	public List<String> listOptions() {
		var list = new ArrayList<String>();
		list.add(optionRooted.getName());
		list.addAll(super.listOptions());
		return list;
	}

	@Override
	public String getToolTip(String optionName) {
		if (!optionName.startsWith("option")) {
			optionName = "option" + optionName;
		}
		if (optionRooted.getName().equals(optionName))
			return "interpret trees as rooted?";
		else
			return super.getToolTip(optionName);
	}

	@Override
	public String getCitation() {
		return "Faith 1992;DP Faith. Conservation evaluation and phylogenetic diversity. Biological Conservation 61, 1–10, 1992.";
	}

	@Override
	public String getShortDescription() {
		return "Calculates the phylogenetic diversity for selected taxa.";
	}

	@Override
	String runAnalysis(ProgressListener progress, TaxaBlock taxaBlock, TreesBlock treesBlock, Collection<Taxon> selectedTaxa) throws CanceledException {
		var taxa = BitSetUtils.asBitSet(selectedTaxa.stream().mapToInt(taxaBlock::indexOf).toArray());
		return report(progress, taxaBlock, treesBlock, taxa, getOptionRooted());
	}

	public static String report(ProgressListener progress, TaxaBlock taxaBlock, TreesBlock treesBlock, BitSet selectedTaxa, boolean rooted) throws CanceledException {
		var buf = new StringBuilder();
		progress.setTasks("Computing", "fair proportions");
		progress.setMaximum(treesBlock.getNTrees());
		progress.setProgress(0);

		for (var tree : treesBlock.getTrees()) {
			var total = tree.edgeStream().mapToDouble(tree::getWeight).sum();
			double diversity;
			try (var nodeClusterMap = TreesUtils.extractClusters(tree)) {
				diversity = tree.edgeStream()
						.filter(e -> nodeClusterMap.get(e.getTarget()).intersects(selectedTaxa) && (rooted || !BitSetUtils.contains(selectedTaxa, nodeClusterMap.get(e.getTarget()))))
						.mapToDouble(tree::getWeight).sum();
			}
			var totalRounded = NumberUtils.roundSigFig(total, 5);
			buf.append("%nTree %s (total: %s):%n".formatted(tree.getName(), StringUtils.removeTrailingZerosAfterDot(totalRounded)));
			var diversityRounded = NumberUtils.roundSigFig(diversity, 5);
			buf.append("%s Phylogenetic Diversity = %s (%.1f%%)%n".formatted(rooted ? "Rooted" : "Unrooted", StringUtils.removeTrailingZerosAfterDot(diversityRounded), 100.0 * (diversity / total)));
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

	public boolean getOptionRooted() {
		return optionRooted.get();
	}

	public BooleanProperty optionRootedProperty() {
		return optionRooted;
	}

	public void setOptionRooted(boolean optionRooted) {
		this.optionRooted.set(optionRooted);
	}
}
