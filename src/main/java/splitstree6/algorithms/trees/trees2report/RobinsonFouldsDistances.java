/*
 * RobinsonFouldsDistances.java Copyright (C) 2024 Daniel H. Huson
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
 *
 */

package splitstree6.algorithms.trees.trees2report;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import jloda.util.CanceledException;
import jloda.util.IteratorUtils;
import jloda.util.SetUtils;
import jloda.util.StringUtils;
import jloda.util.progress.ProgressListener;
import splitstree6.data.TaxaBlock;
import splitstree6.data.TreesBlock;
import splitstree6.data.parts.Taxon;
import splitstree6.splits.ASplit;
import splitstree6.splits.SplitUtils;

import java.util.*;

/**
 * calculate the Robinson-Foulds distances between trees
 * Daniel Huson, 11.2024
 */
public class RobinsonFouldsDistances extends Trees2ReportBase {
	private final BooleanProperty optionNormalize = new SimpleBooleanProperty(this, "optionNormalize", false);

	@Override
	public List<String> listOptions() {
		return List.of(optionNormalize.getName());
	}

	@Override
	public String getToolTip(String optionName) {
		if (!optionName.startsWith("option"))
			optionName = "option" + optionName;
		if (optionName.equals(optionNormalize.getName()))
			return "normalized distances";
		else return "";
	}

	public RobinsonFouldsDistances() {
		setOptionApplyTo(ApplyTo.AllTrees);
	}

	@Override
	String runAnalysis(ProgressListener progress, TaxaBlock taxaBlock, TreesBlock treesBlock, Collection<Taxon> selectedTaxa) throws CanceledException {
		var splitsList = new ArrayList<Set<ASplit>>();
		for (var tree : treesBlock.getTrees()) {
			var splits = new HashSet<ASplit>();
			SplitUtils.computeSplits(taxaBlock.getTaxaSet(), tree, splits);
			splitsList.add(splits);
		}
		var buf = new StringBuilder();
		for (var i = 0; i < splitsList.size(); i++) {
			for (var j = i + 1; j < splitsList.size(); j++) {
				var symm = (double) IteratorUtils.size(SetUtils.symmetricDifference(splitsList.get(i), splitsList.get(j)));
				if (isOptionNormalize()) {
					symm /= (IteratorUtils.size(SetUtils.union(splitsList.get(i), splitsList.get(j))) - taxaBlock.getNtax());
				}
				buf.append("D(%s,%s) = %s%n".formatted(treesBlock.getTree(i + 1).getName(), treesBlock.getTree(j + 1).getName(),
						StringUtils.removeTrailingZerosAfterDot(symm)));
			}
		}
		return buf.toString();
	}

	@Override
	public String getShortDescription() {
		return "Calculates the Robinson-Foulds distance between each pair of trees";
	}

	@Override
	public String getCitation() {
		return "Robinson & Foulds (1981);DF Robinson and LR Foulds. Comparison of phylogenetic trees. Mathematical Biosciences. 53(1–2):131–147, 1981.";
	}

	@Override
	public boolean isApplicable(TaxaBlock taxa, TreesBlock treesBlock) {
		return !treesBlock.isPartial() && !treesBlock.isReticulated();
	}

	public boolean isOptionNormalize() {
		return optionNormalize.get();
	}

	public BooleanProperty optionNormalizeProperty() {
		return optionNormalize;
	}
}
